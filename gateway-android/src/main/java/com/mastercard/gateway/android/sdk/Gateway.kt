/*
 * Copyright (c) 2016 Mastercard
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.mastercard.gateway.android.sdk


import android.app.Activity
import android.content.Intent
import android.util.Base64
import com.google.android.gms.common.api.Status
import com.google.android.gms.wallet.AutoResolveHelper
import com.google.android.gms.wallet.PaymentData
import com.google.android.gms.wallet.PaymentDataRequest
import com.google.android.gms.wallet.PaymentsClient
import io.reactivex.Single
import org.json.JSONObject


/**
 * The public interface to the Gateway SDK.
 *
 * Example set up:
 *
 * `Gateway gateway = new Gateway();
 * gateway.setMerchantId("your-merchant-id");
 * gateway.setRegion(Gateway.Region.NORTH_AMERICA);`
 *
 */
class Gateway {

    @JvmField internal var comms = GatewayComms()

    var merchantId: String? = null
    var region: Region? = null


    /**
     * The available gateway regions
     */
    enum class Region constructor(internal var prefix: String) {
        ASIA_PACIFIC("ap-"),
        EUROPE("eu-"),
        NORTH_AMERICA("na-"),
        MTF("test-")
    }

    /**
     * Updates a Mastercard Gateway session with the provided information.<br></br>
     * The API version number provided MUST match the version used when the session was created.
     *
     * This will execute the necessary network request on a background thread
     * and return the response (or error) to the provided callback.
     *
     * @param sessionId  A session ID from the Mastercard Gateway
     * @param apiVersion The API version number used when the session was created
     * @param payload    A map of the request data
     * @param callback   A callback to handle success and error messages
     */
    fun updateSession(sessionId: String, apiVersion: String, payload: GatewayMap, callback: GatewayCallback) {
        val request = buildUpdateSessionRequest(sessionId, apiVersion, payload)

        comms.runGatewayRequest(request, callback)
    }

    /**
     * Updates a Mastercard Gateway session with the provided information.
     * The API version number provided MUST match the version used when the session was created.
     *
     * Does not adhere to any particular scheduler
     *
     * @param sessionId  A session ID from the Mastercard Gateway
     * @param apiVersion The API version number used when the session was created
     * @param payload    A map of the request data
     * @return A <tt>Single</tt> of the response map
     * @see [RxJava: Single](http://reactivex.io/RxJava/javadoc/io/reactivex/Single.html)
     */
    fun updateSession(sessionId: String, apiVersion: String, payload: GatewayMap): Single<GatewayMap> {
        val request = buildUpdateSessionRequest(sessionId, apiVersion, payload)

        return comms.runGatewayRequest(request)
    }

    private fun buildUpdateSessionRequest(sessionId: String, apiVersion: String, payload: GatewayMap): GatewayRequest {
        val request = GatewayRequest(
                url = getUpdateSessionUrl(sessionId, apiVersion),
                method = GatewayRequest.Method.PUT,
                payload = payload.apply {
                    this["device.browser"] = GatewayComms.USER_AGENT
                }
        )

        // version 50 of the API dropped the requirement for the apiOperation parameter
        // 50+ uses the standard Update Session API
        if (apiVersion.toInt() < 50) {
            request.payload["apiOperation"] = "UPDATE_PAYER_DATA"
        } else {
            // Auth header required for v50+
            request.headers["Authorization"] = createAuthHeader(sessionId)
        }

        return request
    }

    /**
     *
     */
    fun initiateAuthentication(sessionId: String, orderId: String, transactionId: String, apiVersion: String, payload: GatewayMap, callback: GatewayCallback) {
        val request = buildInitiateAuthenticationRequest(sessionId, orderId, transactionId, apiVersion, payload)
        comms.runGatewayRequest(request, callback)
    }

    /**
     *
     */
    fun initiateAuthentication(sessionId: String, orderId: String, transactionId: String, apiVersion: String, payload: GatewayMap): Single<GatewayMap> {
        val request = buildInitiateAuthenticationRequest(sessionId, orderId, transactionId, apiVersion, payload)
        return comms.runGatewayRequest(request)
    }

    private fun buildInitiateAuthenticationRequest(sessionId: String, orderId: String, transactionId: String, apiVersion: String, payload: GatewayMap): GatewayRequest {
        return buildAuthenticationRequest(sessionId, orderId, transactionId, apiVersion, payload).apply {
            payload["apiOperation"] = "INITIATE_AUTHENTICATION"
        }
    }

    /**
     *
     */
    fun authenticatePayer(sessionId: String, orderId: String, transactionId: String, apiVersion: String, payload: GatewayMap, callback: GatewayCallback) {
        val request = buildAuthenticatePayerRequest(sessionId, orderId, transactionId, apiVersion, payload)
        comms.runGatewayRequest(request, callback)
    }

    /**
     *
     */
    fun authenticatePayer(sessionId: String, orderId: String, transactionId: String, apiVersion: String, payload: GatewayMap): Single<GatewayMap> {
        val request = buildAuthenticatePayerRequest(sessionId, orderId, transactionId, apiVersion, payload)
        return comms.runGatewayRequest(request)
    }

    private fun buildAuthenticatePayerRequest(sessionId: String, orderId: String, transactionId: String, apiVersion: String, payload: GatewayMap): GatewayRequest {
        return buildAuthenticationRequest(sessionId, orderId, transactionId, apiVersion, payload).apply {
            payload["apiOperation"] = "AUTHENTICATE_PAYER"
        }
    }

    private fun buildAuthenticationRequest(sessionId: String, orderId: String, transactionId: String, apiVersion: String, payload: GatewayMap): GatewayRequest {
        return GatewayRequest(
                url = getAuthenticationUrl(orderId, transactionId, apiVersion),
                method = GatewayRequest.Method.PUT,
                payload = payload.apply {
                    this["session.id"] = sessionId
                },
                headers = mutableMapOf("Authorization" to createAuthHeader(sessionId))
        )
    }

    private fun getApiUrl(apiVersion: String): String {
        if (apiVersion.toInt() < GatewayComms.MIN_API_VERSION) {
            throw IllegalArgumentException("API version must be >= ${GatewayComms.MIN_API_VERSION}")
        }

        if (region == null) {
            throw IllegalStateException("You must initialize the the Gateway instance with a Region before use")
        }

        return "https://${region!!.prefix}gateway.mastercard.com/api/rest/version/$apiVersion"
    }

    private fun getUpdateSessionUrl(sessionId: String, apiVersion: String): String {
        if (merchantId == null) {
            throw IllegalStateException("You must initialize the the Gateway instance with a Merchant Id before use")
        }

        return getApiUrl(apiVersion) + "/merchant/$merchantId/session/$sessionId"
    }

    private fun getAuthenticationUrl(orderId: String, transactionId: String, apiVersion: String): String {
        if (merchantId == null) {
            throw IllegalStateException("You must initialize the the Gateway instance with a Merchant Id before use")
        }

        return getApiUrl(apiVersion) + "/merchant/$merchantId/order/$orderId/transaction/$transactionId"
    }

    private fun createAuthHeader(sessionId: String): String {
        val value = "merchant.$merchantId:$sessionId"
        return "Basic " + Base64.encodeToString(value.toByteArray(), Base64.NO_WRAP)
    }

    companion object {
        const val REQUEST_3D_SECURE = 10000
        const val REQUEST_GOOGLE_PAY_LOAD_PAYMENT_DATA = 10001

        /**
         * Starts the [Gateway3DSecureActivity] for result, initializing it with the provided html
         *
         * @param activity The calling activity context
         * @param html     The initial HTML to render in the web view
         * @param title    An optional title to render in the toolbar
         */
        @JvmOverloads
        @JvmStatic
        fun start3DSecureActivity(activity: Activity, html: String, title: String? = null) {
            val intent = Intent(activity, Gateway3DSecureActivity::class.java)
            start3DSecureActivity(activity, html, title, intent)
        }

        // separated for testability
        @JvmStatic
        internal fun start3DSecureActivity(activity: Activity, html: String, title: String?, intent: Intent) {
            intent.putExtra(Gateway3DSecureActivity.EXTRA_HTML, html) // required

            title?.apply {
                intent.putExtra(Gateway3DSecureActivity.EXTRA_TITLE, this)
            }

            activity.startActivityForResult(intent, REQUEST_3D_SECURE)
        }

        /**
         * A convenience method for handling activity result messages returned from [Gateway3DSecureActivity].
         * This method should be called within the calling Activity's onActivityResult() lifecycle method.
         * This helper only works if the 3-D Secure Activity was launched using the
         * [Gateway.start3DSecureActivity] method.
         *
         * @param requestCode The request code returning from the activity result
         * @param resultCode The result code returning from the activity result
         * @param data The intent data returning from the activity result
         * @param callback An implementation of [Gateway3DSecureCallback]
         * @return True if handled, False otherwise
         * @see Gateway.start3DSecureActivity
         * @see Gateway.start3DSecureActivity
         */
        @JvmStatic
        fun handle3DSecureResult(requestCode: Int, resultCode: Int, data: Intent, callback: Gateway3DSecureCallback?): Boolean {
            if (callback == null) {
                return false
            }

            if (requestCode == REQUEST_3D_SECURE) {
                if (resultCode == Activity.RESULT_OK) {
                    val acsResultJson = data.getStringExtra(Gateway3DSecureActivity.EXTRA_ACS_RESULT)
                    val acsResult = GatewayMap(acsResultJson)

                    callback.on3DSecureComplete(acsResult)
                } else {
                    callback.on3DSecureCancel()
                }

                return true
            }

            return false
        }


        /**
         * A convenience method for initializing the request to get Google Pay card info
         *
         * @param paymentsClient An instance of the PaymentClient
         * @param request A properly formatted PaymentDataRequest
         * @param activity The calling activity
         * @see [Payments Client](https://developers.google.com/pay/api/android/guides/tutorial.paymentsclient)
         *
         * @see [Payment Data Request](https://developers.google.com/pay/api/android/guides/tutorial.paymentdatarequest)
         */
        @JvmStatic
        fun requestGooglePayData(paymentsClient: PaymentsClient, request: PaymentDataRequest, activity: Activity) {
            AutoResolveHelper.resolveTask(paymentsClient.loadPaymentData(request), activity, REQUEST_GOOGLE_PAY_LOAD_PAYMENT_DATA)
        }

        /**
         * A convenience method for handling activity result messages returned from Google Pay.
         * This method should be called withing the calling Activity's onActivityResult() lifecycle method.
         * This helper only works if the Google Pay dialog was launched using the
         * [Gateway.requestGooglePayData] method.
         *
         * @param requestCode The request code returning from the activity result
         * @param resultCode The result code returning from the activity result
         * @param data The intent data returning from the activity result
         * @param callback An implementation of [GatewayGooglePayCallback]
         * @return True if handled, False otherwise
         * @see Gateway.requestGooglePayData
         */
        @JvmStatic
        fun handleGooglePayResult(requestCode: Int, resultCode: Int, data: Intent, callback: GatewayGooglePayCallback?): Boolean {
            if (callback == null) {
                return false
            }

            if (requestCode == REQUEST_GOOGLE_PAY_LOAD_PAYMENT_DATA) {
                when (resultCode) {
                    Activity.RESULT_OK -> try {
                        val paymentData = PaymentData.getFromIntent(data)
                        val json = JSONObject(paymentData!!.toJson())
                        callback.onReceivedPaymentData(json)
                    } catch (e: Exception) {
                        callback.onGooglePayError(Status.RESULT_INTERNAL_ERROR)
                    }
                    Activity.RESULT_CANCELED -> callback.onGooglePayCancelled()
                    AutoResolveHelper.RESULT_ERROR -> {
                        val status = AutoResolveHelper.getStatusFromIntent(data)
                        callback.onGooglePayError(status!!)
                    }
                }

                return true
            }

            return false
        }
    }
}
