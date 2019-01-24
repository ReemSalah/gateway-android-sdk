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
import android.os.Handler
import android.util.Base64

import com.google.android.gms.common.api.Status
import com.google.android.gms.wallet.AutoResolveHelper

import com.google.android.gms.wallet.PaymentData
import com.google.android.gms.wallet.PaymentDataRequest
import com.google.android.gms.wallet.PaymentsClient
import com.google.gson.Gson

import org.json.JSONObject

import java.io.ByteArrayInputStream
import java.net.URL
import java.security.KeyStore
import java.security.cert.CertificateFactory
import java.security.cert.X509Certificate

import javax.net.ssl.HttpsURLConnection
import javax.net.ssl.SSLContext
import javax.net.ssl.TrustManagerFactory

import io.reactivex.Single


/**
 * The public interface to the Gateway SDK.
 *
 *
 * Example set up:
 *
 *
 * `
 * Gateway gateway = new Gateway();
 * gateway.setMerchantId("your-merchant-id");
 * gateway.setRegion(Gateway.Region.NORTH_AMERICA);
` *
 */
/**
 * Constructs a new instance.
 */
class Gateway {

    internal var logger: Logger = BaseLogger()
    internal var gson = Gson()
    internal var merchantId: String? = null
    internal var region: Region? = null

    /**
     * The available gateway regions
     */
    enum class Region private constructor(internal var prefix: String) {
        ASIA_PACIFIC("ap-"),
        EUROPE("eu-"),
        NORTH_AMERICA("na-"),
        MTF("test-")
    }

    /**
     * Gets the current Merchant ID
     *
     * @return The current Merchant ID
     */
    fun getMerchantId(): String? {
        return merchantId
    }

    /**
     * Sets the current Merchant ID
     *
     * @param merchantId A valid Merchant ID
     * @return The <tt>Gateway</tt> instance
     * @throws IllegalArgumentException If the provided Merchant ID is null
     */
    fun setMerchantId(merchantId: String): Gateway {
        this.merchantId = merchantId
        return this
    }

    /**
     * Gets the current [Region]
     *
     * @return The region
     */
    fun getRegion(): Region? {
        return region
    }

    /**
     * Sets the current [Region] to target
     *
     * @param region The region
     * @return The <tt>Gateway</tt> instance
     * @throws IllegalArgumentException If the provided Merchant ID is null
     */
    fun setRegion(region: Region): Gateway {
        this.region = region
        return this
    }

    /**
     * Updates a Mastercard Gateway session with the provided information.<br></br>
     * The API version number provided MUST match the version used when the session was created.
     *
     *
     * This will execute the necessary network request on a background thread
     * and return the response (or error) to the provided callback.
     *
     * @param sessionId  A session ID from the Mastercard Gateway
     * @param apiVersion The API version number used when the session was created
     * @param payload    A map of the request data
     * @param callback   A callback to handle success and error messages
     * @throws IllegalArgumentException If the provided session id is null
     */
    fun updateSession(sessionId: String, apiVersion: String, payload: GatewayMap, callback: GatewayCallback) {
        val request = buildUpdateSessionRequest(sessionId, apiVersion, payload)

        runGatewayRequest(request, callback)
    }

    /**
     * Updates a Mastercard Gateway session with the provided information.
     * The API version number provided MUST match the version used when the session was created.
     *
     *
     * Does not adhere to any particular scheduler
     *
     * @param sessionId  A session ID from the Mastercard Gateway
     * @param apiVersion The API version number used when the session was created
     * @param payload    A map of the request data
     * @return A <tt>Single</tt> of the response map
     * @throws IllegalArgumentException If the provided session id is null
     * @see [RxJava: Single](http://reactivex.io/RxJava/javadoc/io/reactivex/Single.html)
     */
    fun updateSession(sessionId: String, apiVersion: String, payload: GatewayMap): Single<GatewayMap> {
        val request = buildUpdateSessionRequest(sessionId, apiVersion, payload)

        return runGatewayRequest(request)
    }

    internal fun buildUpdateSessionRequest(sessionId: String, apiVersion: String, payload: GatewayMap): GatewayRequest {
        payload["device.browser"] = USER_AGENT

        val request = GatewayRequest(
                url = getUpdateSessionUrl(sessionId, apiVersion),
                method = GatewayRequest.Method.PUT,
                payload = payload
        )

        // version 50 of the API dropped the requirement for the apiOperation parameter
        // 50+ uses the standard Update Session API
        if (Integer.parseInt(apiVersion) < 50) {
            request.payload["apiOperation"] = API_OPERATION
        } else {
            // Auth header required for v50+
            request.headers["Authorization"] = createAuthHeader(sessionId)
        }

        return request
    }


    internal fun getApiUrl(apiVersion: String): String {
        if (Integer.valueOf(apiVersion) < MIN_API_VERSION) {
            throw IllegalArgumentException("API version must be >= $MIN_API_VERSION")
        }

        if (region == null) {
            throw IllegalStateException("You must initialize the the Gateway instance with a Region before use")
        }

        return "https://" + region!!.prefix + "gateway.mastercard.com/api/rest/version/" + apiVersion
    }

    internal fun getUpdateSessionUrl(sessionId: String?, apiVersion: String): String {
        if (sessionId == null) {
            throw IllegalArgumentException("Session Id may not be null")
        }

        if (merchantId == null) {
            throw IllegalStateException("You must initialize the the Gateway instance with a Merchant Id before use")
        }

        return getApiUrl(apiVersion) + "/merchant/" + merchantId + "/session/" + sessionId
    }

    internal fun runGatewayRequest(request: GatewayRequest, callback: GatewayCallback) {
        // create handler on current thread
        val handler = Handler { msg -> handleCallbackMessage(callback, msg.obj) }

        Thread {
            val m = handler.obtainMessage()
            try {
                m.obj = executeGatewayRequest(request)
            } catch (e: Exception) {
                m.obj = e
            }

            handler.sendMessage(m)
        }.start()
    }

    internal fun runGatewayRequest(request: GatewayRequest): Single<GatewayMap> {
        return Single.fromCallable { executeGatewayRequest(request) }
    }

    // handler callback method when executing a request on a new thread
    internal fun handleCallbackMessage(callback: GatewayCallback?, arg: Any): Boolean {
        if (callback != null) {
            if (arg is Throwable) {
                callback.onError(arg)
            } else {
                callback.onSuccess(arg as GatewayMap)
            }
        }
        return true
    }

    internal fun executeGatewayRequest(request: GatewayRequest): GatewayMap {
        // init connection
        val c = createHttpsUrlConnection(request)

        // encode request data to json
        val requestData = gson.toJson(request.payload)

        // log request data
        logger.logRequest(c, requestData)

        // write request data
        if (requestData != null) {
            val os = c.outputStream
            os.write(requestData.toByteArray(charset("UTF-8")))
            os.close()
        }

        // initiate the connection
        c.connect()

        var responseData: String? = null
        val statusCode = c.responseCode
        val isStatusOk = statusCode in 200..299

        // if connection has output stream, get the data
        // socket time-out exceptions will be thrown here
        if (c.doInput) {
            val inputStream = if (isStatusOk) c.inputStream else c.errorStream
            responseData = inputStream.readTextAndClose()
        }

        c.disconnect()

        // log response
        logger.logResponse(c, responseData)

        // parse the response body
        val response = GatewayMap(responseData)

        // if response static is good, return response
        if (isStatusOk) {
            return response
        }

        // otherwise, create a gateway exception and throw it
        var message = response["error.explanation"] as String?
        if (message == null) {
            message = "An error occurred"
        }

        val exception = GatewayException(message)
        exception.setStatusCode(statusCode)
        exception.errorResponse = response

        throw exception
    }

    internal fun createSslContext(): SSLContext {
        // create and initialize a KeyStore
        val keyStore = createSslKeyStore()

        // create a TrustManager that trusts the INTERMEDIATE_CA in our KeyStore
        val tmf = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm())
        tmf.init(keyStore)

        val trustManagers = tmf.trustManagers

        val context = SSLContext.getInstance("TLS")
        context.init(null, trustManagers, null)

        return context
    }

    internal fun createSslKeyStore(): KeyStore {
        val keyStore = KeyStore.getInstance(KeyStore.getDefaultType())
        keyStore.load(null, null)

        // add our trusted cert to the keystore
        keyStore.setCertificateEntry("gateway.mastercard.com", readCertificate(INTERMEDIATE_CA))

        return keyStore
    }

    internal fun readCertificate(cert: String): X509Certificate {
        val bytes = cert.toByteArray()
        val inputStream = ByteArrayInputStream(bytes)

        return CertificateFactory.getInstance("X.509").generateCertificate(inputStream) as X509Certificate
    }

    internal fun createHttpsUrlConnection(request: GatewayRequest): HttpsURLConnection {
        // parse url
        val url = URL(request.url)

        // init ssl context with limiting trust managers
        val context = createSslContext()

        val c = url.openConnection() as HttpsURLConnection
        c.sslSocketFactory = context.socketFactory
        c.connectTimeout = CONNECTION_TIMEOUT
        c.readTimeout = READ_TIMEOUT
        c.requestMethod = request.method!!.name
        c.doOutput = true

        c.setRequestProperty("User-Agent", USER_AGENT)
        c.setRequestProperty("Content-Type", "application/json")

        // add extra headers
        for (key in request.headers.keys) {
            c.setRequestProperty(key, request.headers[key])
        }

        return c
    }

    internal fun createAuthHeader(sessionId: String): String {
        val value = "merchant.$merchantId:$sessionId"
        return "Basic " + Base64.encodeToString(value.toByteArray(), Base64.NO_WRAP)
    }

    internal fun isStatusCodeOk(statusCode: Int): Boolean {
        return statusCode in 200..299
    }


    companion object {
        const val MIN_API_VERSION = 39
        const val CONNECTION_TIMEOUT = 15000
        const val READ_TIMEOUT = 60000
        const val REQUEST_3D_SECURE = 10000
        const val REQUEST_GOOGLE_PAY_LOAD_PAYMENT_DATA = 10001
        const val API_OPERATION = "UPDATE_PAYER_DATA"
        const val USER_AGENT = "Gateway-Android-SDK/" + BuildConfig.VERSION_NAME
        const val INTERMEDIATE_CA = "-----BEGIN CERTIFICATE-----\n" +
                "MIIFAzCCA+ugAwIBAgIEUdNg7jANBgkqhkiG9w0BAQsFADCBvjELMAkGA1UEBhMC\n" +
                "VVMxFjAUBgNVBAoTDUVudHJ1c3QsIEluYy4xKDAmBgNVBAsTH1NlZSB3d3cuZW50\n" +
                "cnVzdC5uZXQvbGVnYWwtdGVybXMxOTA3BgNVBAsTMChjKSAyMDA5IEVudHJ1c3Qs\n" +
                "IEluYy4gLSBmb3IgYXV0aG9yaXplZCB1c2Ugb25seTEyMDAGA1UEAxMpRW50cnVz\n" +
                "dCBSb290IENlcnRpZmljYXRpb24gQXV0aG9yaXR5IC0gRzIwHhcNMTQxMDIyMTcw\n" +
                "NTE0WhcNMjQxMDIzMDczMzIyWjCBujELMAkGA1UEBhMCVVMxFjAUBgNVBAoTDUVu\n" +
                "dHJ1c3QsIEluYy4xKDAmBgNVBAsTH1NlZSB3d3cuZW50cnVzdC5uZXQvbGVnYWwt\n" +
                "dGVybXMxOTA3BgNVBAsTMChjKSAyMDEyIEVudHJ1c3QsIEluYy4gLSBmb3IgYXV0\n" +
                "aG9yaXplZCB1c2Ugb25seTEuMCwGA1UEAxMlRW50cnVzdCBDZXJ0aWZpY2F0aW9u\n" +
                "IEF1dGhvcml0eSAtIEwxSzCCASIwDQYJKoZIhvcNAQEBBQADggEPADCCAQoCggEB\n" +
                "ANo/ltBNuS9E59s5XptQ7lylYdpBZ1MJqgCajld/KWvbx+EhJKo60I1HI9Ltchbw\n" +
                "kSHSXbe4S6iDj7eRMmjPziWTLLJ9l8j+wbQXugmeA5CTe3xJgyJoipveR8MxmHou\n" +
                "fUAL0u8+07KMqo9Iqf8A6ClYBve2k1qUcyYmrVgO5UK41epzeWRoUyW4hM+Ueq4G\n" +
                "RQyja03Qxr7qGKQ28JKyuhyIjzpSf/debYMcnfAf5cPW3aV4kj2wbSzqyc+UQRlx\n" +
                "RGi6RzwE6V26PvA19xW2nvIuFR4/R8jIOKdzRV1NsDuxjhcpN+rdBQEiu5Q2Ko1b\n" +
                "Nf5TGS8IRsEqsxpiHU4r2RsCAwEAAaOCAQkwggEFMA4GA1UdDwEB/wQEAwIBBjAP\n" +
                "BgNVHRMECDAGAQH/AgEAMDMGCCsGAQUFBwEBBCcwJTAjBggrBgEFBQcwAYYXaHR0\n" +
                "cDovL29jc3AuZW50cnVzdC5uZXQwMAYDVR0fBCkwJzAloCOgIYYfaHR0cDovL2Ny\n" +
                "bC5lbnRydXN0Lm5ldC9nMmNhLmNybDA7BgNVHSAENDAyMDAGBFUdIAAwKDAmBggr\n" +
                "BgEFBQcCARYaaHR0cDovL3d3dy5lbnRydXN0Lm5ldC9ycGEwHQYDVR0OBBYEFIKi\n" +
                "cHTdvFM/z3vU981/p2DGCky/MB8GA1UdIwQYMBaAFGpyJnrQHu995ztpUdRsjZ+Q\n" +
                "EmarMA0GCSqGSIb3DQEBCwUAA4IBAQA/HBpb/0AiHY81DC2qmSerwBEycNc2KGml\n" +
                "jbEnmUK+xJPrSFdDcSPE5U6trkNvknbFGe/KvG9CTBaahqkEOMdl8PUM4ErfovrO\n" +
                "GhGonGkvG9/q4jLzzky8RgzAiYDRh2uiz2vUf/31YFJnV6Bt0WRBFG00Yu0GbCTy\n" +
                "BrwoAq8DLcIzBfvLqhboZRBD9Wlc44FYmc1r07jHexlVyUDOeVW4c4npXEBmQxJ/\n" +
                "B7hlVtWNw6f1sbZlnsCDNn8WRTx0S5OKPPEr9TVwc3vnggSxGJgO1JxvGvz8pzOl\n" +
                "u7sY82t6XTKH920l5OJ2hiEeEUbNdg5vT6QhcQqEpy02qUgiUX6C\n" +
                "-----END CERTIFICATE-----\n"

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

            if (title != null) {
                intent.putExtra(Gateway3DSecureActivity.EXTRA_TITLE, title)
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
                        callback.onGooglePayError(status)
                    }
                }

                return true
            }

            return false
        }
    }
}
