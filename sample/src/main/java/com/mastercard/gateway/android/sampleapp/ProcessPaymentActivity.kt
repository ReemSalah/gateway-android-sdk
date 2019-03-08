package com.mastercard.gateway.android.sampleapp

import android.app.Activity
import android.content.Intent
import android.graphics.Paint
import android.os.Bundle
import android.util.Log
import android.view.View
import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.appcompat.app.AppCompatActivity
import androidx.databinding.DataBindingUtil
import com.mastercard.gateway.android.sampleapp.databinding.ActivityProcessPaymentBinding
import com.mastercard.gateway.android.sdk.Gateway
import com.mastercard.gateway.android.sdk.Gateway3DSecureCallback
import com.mastercard.gateway.android.sdk.GatewayCallback
import com.mastercard.gateway.android.sdk.GatewayMap
import com.mastercard.gateway.threeds2.*
import java.util.*


class ProcessPaymentActivity : AppCompatActivity() {

    internal lateinit var binding: ActivityProcessPaymentBinding
    internal lateinit var gateway: Gateway
    internal lateinit var sessionId: String
    internal lateinit var apiVersion: String
    internal lateinit var orderId: String
    internal lateinit var transactionId: String

    internal var threeDSecureTwoId: String? = null
    internal var threeDSecureTwoStatus: String? = null
    internal var isGooglePay = false
    internal var apiController = ApiController.instance

    internal lateinit var mock3DS2Service: Mock3DS2Service

    internal lateinit var threeDS2Transaction: Transaction

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = DataBindingUtil.setContentView(this, R.layout.activity_process_payment)

        // init api controller
        apiController.setMerchantServerUrl(Config.MERCHANT_URL.getValue(this))

        // init gateway
        gateway = Gateway()
        gateway.setMerchantId(Config.MERCHANT_ID.getValue(this))
        try {
            val region = Gateway.Region.valueOf(Config.REGION.getValue(this))
            gateway.setRegion(region)
        } catch (e: Exception) {
            Log.e(TAG, "Invalid Gateway region value provided", e)
        }

        // random order/txn IDs for example purposes
        orderId = UUID.randomUUID().toString()
        orderId = orderId.substring(0, orderId.indexOf('-'))
        transactionId = UUID.randomUUID().toString()
        transactionId = transactionId.substring(0, transactionId.indexOf('-'))

        // bind buttons
        binding.startButton.setOnClickListener { createSession() }
        binding.confirmButton.setOnClickListener {
            // 3DS is not applicable to Google Pay transactions
            when {
                isGooglePay -> processPayment()
                else -> check3dsEnrollment()
            }
        }
        binding.doneButton.setOnClickListener { finish() }

        initUI()
        init3DS2SDK()
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        // handle the 3DSecure lifecycle
        if (Gateway.handle3DSecureResult(requestCode, resultCode, data!!, ThreeDSecureCallback())) {
            return
        }

        if (requestCode == REQUEST_CARD_INFO) {
            binding.collectCardInfoProgress.visibility = View.GONE

            if (resultCode == Activity.RESULT_OK) {
                binding.collectCardInfoSuccess.visibility = View.VISIBLE

                val googlePayToken = data.getStringExtra(CollectCardInfoActivity.EXTRA_PAYMENT_TOKEN)

                val cardDescription = data.getStringExtra(CollectCardInfoActivity.EXTRA_CARD_DESCRIPTION)
                binding.confirmCardDescription.text = cardDescription

                if (googlePayToken != null) {
                    isGooglePay = true

                    binding.check3dsLabel.paintFlags = binding.check3dsLabel.paintFlags or Paint.STRIKE_THRU_TEXT_FLAG

                    val paymentToken = data.getStringExtra(CollectCardInfoActivity.EXTRA_PAYMENT_TOKEN)

                    updateSession(paymentToken)
                } else {
                    isGooglePay = false

                    val cardName = data.getStringExtra(CollectCardInfoActivity.EXTRA_CARD_NAME)
                    val cardNumber = data.getStringExtra(CollectCardInfoActivity.EXTRA_CARD_NUMBER)
                    val cardExpiryMonth = data.getStringExtra(CollectCardInfoActivity.EXTRA_CARD_EXPIRY_MONTH)
                    val cardExpiryYear = data.getStringExtra(CollectCardInfoActivity.EXTRA_CARD_EXPIRY_YEAR)
                    val cardCvv = data.getStringExtra(CollectCardInfoActivity.EXTRA_CARD_CVV)

                    updateSession(cardName, cardNumber, cardExpiryMonth, cardExpiryYear, cardCvv)
                }

            } else {
                binding.collectCardInfoError.visibility = View.VISIBLE

                showResult(R.drawable.failed, R.string.pay_error_card_info_not_collected)
            }

            return
        }

        super.onActivityResult(requestCode, resultCode, data)
    }

    internal fun initUI() {
        binding.createSessionProgress.visibility = View.GONE
        binding.createSessionSuccess.visibility = View.GONE
        binding.createSessionError.visibility = View.GONE

        binding.collectCardInfoProgress.visibility = View.GONE
        binding.collectCardInfoSuccess.visibility = View.GONE
        binding.collectCardInfoError.visibility = View.GONE

        binding.updateSessionProgress.visibility = View.GONE
        binding.updateSessionSuccess.visibility = View.GONE
        binding.updateSessionError.visibility = View.GONE

        binding.check3dsProgress.visibility = View.GONE
        binding.check3dsSuccess.visibility = View.GONE
        binding.check3dsError.visibility = View.GONE

        binding.processPaymentProgress.visibility = View.GONE
        binding.processPaymentSuccess.visibility = View.GONE
        binding.processPaymentError.visibility = View.GONE

        binding.startButton.isEnabled = true
        binding.confirmButton.isEnabled = true

        binding.startButton.visibility = View.VISIBLE
        binding.groupConfirm.visibility = View.GONE
        binding.groupResult.visibility = View.GONE
    }

    private fun init3DS2SDK() {
        mock3DS2Service = Mock3DS2Service()
        mock3DS2Service.initializeCallback = ThreeDS2InitializationCallback()

        val configParameters = ConfigParameters()

        val uiCustomization = UiCustomization()

        mock3DS2Service.initialize(applicationContext, configParameters, "en-US", uiCustomization)
    }

    internal fun createSession() {
        binding.startButton.isEnabled = false
        binding.createSessionProgress.visibility = View.VISIBLE

        apiController.createSession(CreateSessionCallback())
    }

    internal fun collectCardInfo() {
        binding.collectCardInfoProgress.visibility = View.VISIBLE

        val i = Intent(this, CollectCardInfoActivity::class.java)
        i.putExtra(CollectCardInfoActivity.EXTRA_GOOGLE_PAY_TXN_AMOUNT, AMOUNT)
        i.putExtra(CollectCardInfoActivity.EXTRA_GOOGLE_PAY_TXN_CURRENCY, CURRENCY)

        startActivityForResult(i, REQUEST_CARD_INFO)
    }

    internal fun updateSession(paymentToken: String) {
        binding.updateSessionProgress.visibility = View.VISIBLE

        val request = GatewayMap()
                .set("sourceOfFunds.provided.card.devicePayment.paymentToken", paymentToken)

        gateway.updateSession(sessionId, apiVersion, request, UpdateSessionCallback())
    }

    internal fun updateSession(name: String, number: String, expiryMonth: String, expiryYear: String, cvv: String) {
        binding.updateSessionProgress.visibility = View.VISIBLE

        // build the gateway request
        val request = GatewayMap()
                .set("sourceOfFunds.provided.card.nameOnCard", name)
                .set("sourceOfFunds.provided.card.number", number)
                .set("sourceOfFunds.provided.card.securityCode", cvv)
                .set("sourceOfFunds.provided.card.expiry.month", expiryMonth)
                .set("sourceOfFunds.provided.card.expiry.year", expiryYear)

        gateway.updateSession(sessionId, apiVersion, request, UpdateSessionCallback())
    }

    internal fun check3dsEnrollment() {
        binding.check3dsProgress.visibility = View.VISIBLE
        binding.confirmButton.isEnabled = false

        // build the gateway request
        val request = GatewayMap()
                .set("order.currency", CURRENCY)

        gateway.initiateAuthentication(sessionId, orderId, transactionId, apiVersion, request, InitiateAuthenticationCallback())
    }

    internal fun processPayment() {
        binding.processPaymentProgress.visibility = View.VISIBLE

        apiController.completeSession(sessionId, orderId, transactionId, AMOUNT, CURRENCY, threeDSecureTwoId, threeDSecureTwoStatus, isGooglePay, CompleteSessionCallback())
    }

    internal fun showResult(@DrawableRes iconId: Int, @StringRes messageId: Int) {
        binding.resultIcon.setImageResource(iconId)
        binding.resultText.setText(messageId)

        binding.groupConfirm.visibility = View.GONE
        binding.groupResult.visibility = View.VISIBLE
    }


    internal inner class CreateSessionCallback : ApiController.CreateSessionCallback {
        override fun onSuccess(sessionId: String, apiVersion: String) {
            Log.i("CreateSessionTask", "Session established")
            binding.createSessionProgress.visibility = View.GONE
            binding.createSessionSuccess.visibility = View.VISIBLE

            this@ProcessPaymentActivity.sessionId = sessionId
            this@ProcessPaymentActivity.apiVersion = apiVersion

            collectCardInfo()
        }

        override fun onError(throwable: Throwable) {
            Log.e(TAG, throwable.message, throwable)

            binding.createSessionProgress.visibility = View.GONE
            binding.createSessionError.visibility = View.VISIBLE

            showResult(R.drawable.failed, R.string.pay_error_unable_to_create_session)
        }
    }

    internal inner class UpdateSessionCallback : GatewayCallback {
        override fun onSuccess(response: GatewayMap) {
            Log.i(TAG, "Successfully updated session")

            binding.updateSessionProgress.visibility = View.GONE
            binding.updateSessionSuccess.visibility = View.VISIBLE

            binding.startButton.visibility = View.GONE
            binding.groupConfirm.visibility = View.VISIBLE
        }

        override fun onError(throwable: Throwable) {
            Log.e(TAG, throwable.message, throwable)

            binding.updateSessionProgress.visibility = View.GONE
            binding.updateSessionError.visibility = View.VISIBLE

            showResult(R.drawable.failed, R.string.pay_error_unable_to_update_session)
        }
    }

    internal inner class InitiateAuthenticationCallback : GatewayCallback {
        override fun onSuccess(response: GatewayMap) {
            Log.i(TAG, "Successfully initiated Authentication")

            val authVersion = response["authentication.version"]

            when (authVersion) {
                "3DS2" -> handle3DS2Response(response)
                "3DS1" -> handle3DS1Response(response)
                else -> processPayment()
            }
        }

        override fun onError(throwable: Throwable) {
            Log.e(TAG, throwable.message, throwable)

            binding.check3dsProgress.visibility = View.GONE
            binding.check3dsError.visibility = View.VISIBLE

            showResult(R.drawable.failed, R.string.pay_error_3ds_authentication_failed)
        }

    }


    internal inner class AuthenticatePayerCallback : GatewayCallback {
        override fun onSuccess(response: GatewayMap) {
            Log.i(TAG, "Successfully initiated Authentication")

            val acsTransactionId = response["authentication.3ds2.acsTransactionId"] as String
            val acsRefNumber = response["authentication.3ds2.acsRefNumber"] as String
            val acsSignedContent = response["authentication.3ds2.acsSignedContent"] as String

            val timeout = response["authentication.3ds2.sdk.timeout"] as Int

            val challengeParameters = ChallengeParameters(threeDS2Transaction.getAuthenticationRequestParameters().sdkTransactionID,
                    acsTransactionId,
                    acsRefNumber,
                    acsSignedContent,
                    "No idea" /*TODO fix this*/
            )

            threeDS2Transaction.doChallenge(this@ProcessPaymentActivity, challengeParameters, StatusReceiver(), timeout)
        }

        override fun onError(throwable: Throwable) {
            Log.e(TAG, throwable.message, throwable)

            binding.check3dsProgress.visibility = View.GONE
            binding.check3dsError.visibility = View.VISIBLE

            showResult(R.drawable.failed, R.string.pay_error_3ds_authentication_failed)
        }

    }

    internal inner class StatusReceiver : ChallengeStatusReceiver {
        override fun completed(completionEvent: CompletionEvent) {

            threeDSecureTwoId = completionEvent.sdkTransactionId
            threeDSecureTwoStatus = completionEvent.transactionStatus


            apiController.completeSession(sessionId, orderId, transactionId, AMOUNT, CURRENCY, threeDSecureTwoId, threeDSecureTwoStatus, isGooglePay, CompleteSessionCallback())
        }

        override fun cancelled() {
            Log.e(TAG, "3DS Canceled")

            binding.check3dsProgress.visibility = View.GONE
            binding.check3dsError.visibility = View.VISIBLE

            showResult(R.drawable.failed, R.string.pay_error_3ds_authentication_failed)
        }

        override fun timedout() {
            Log.e(TAG, "3DS Timed out")

            binding.check3dsProgress.visibility = View.GONE
            binding.check3dsError.visibility = View.VISIBLE

            showResult(R.drawable.failed, R.string.pay_error_3ds_authentication_failed)

        }

        override fun protocolError(protocolErrorEvent: ProtocolErrorEvent) {
            Log.e(TAG, protocolErrorEvent.errorMessage.errorDetails)

            binding.check3dsProgress.visibility = View.GONE
            binding.check3dsError.visibility = View.VISIBLE

            showResult(R.drawable.failed, R.string.pay_error_3ds_authentication_failed)
        }

        override fun runtimeError(runtimeErrorEvent: RuntimeErrorEvent) {
            Log.e(TAG, runtimeErrorEvent.errorMessage)

            binding.check3dsProgress.visibility = View.GONE
            binding.check3dsError.visibility = View.VISIBLE

            showResult(R.drawable.failed, R.string.pay_error_3ds_authentication_failed)
        }

    }

    private fun handle3DS1Response(response: GatewayMap) {
        val threeDSecureId = response["gatewayResponse.3DSecureID"] as String?

        var html: String? = null
        if (response.containsKey("authentication.redirectHtml")) {
            html = response["authentication.redirectHtml"] as String?
        }

        val gatewayRecommendation = response["response.gatewayRecommendation "] as String?

        // if DO_NOT_PROCEED returned in recommendation, should stop transaction
        if ("DO_NOT_PROCEED".equals(gatewayRecommendation!!, ignoreCase = true)) {
            binding.check3dsProgress.visibility = View.GONE
            binding.check3dsError.visibility = View.VISIBLE

            showResult(R.drawable.failed, R.string.pay_error_3ds_authentication_failed)
            return
        }

        // if PROCEED in recommendation, and we have HTML for 3ds, perform 3DS
        if (html != null) {
            Gateway.start3DSecureActivity(this@ProcessPaymentActivity, html)
            return
        }

        processPayment()
    }

    private fun handle3DS2Response(response: GatewayMap) {
        val directoryServerId = response["authentication.3ds2.directoryServerId"] as String
        val messageVersion = response["authentication.3ds2.messageVersion"] as String

        threeDS2Transaction = mock3DS2Service.createTransaction(directoryServerId, messageVersion)

        val requestParams = threeDS2Transaction.getAuthenticationRequestParameters()

        // build the gateway request
        val request = GatewayMap()
                .set("authentication.3ds2.sdk.appId", requestParams.sdkAppID)
                .set("authentication.3ds2.sdk.encryptedData", requestParams.deviceData)
                .set("authentication.3ds2.sdk.ephemeralPublicKey ", requestParams.sdkEphemeralPublicKey)
                .set("authentication.3ds2.sdk.referenceNumber", requestParams.sdkReferenceNumber)
                .set("authentication.3ds2.sdk.transactionId", requestParams.sdkTransactionID)


        gateway.authenticatePayer(sessionId, orderId, transactionId, apiVersion, request, AuthenticatePayerCallback())
    }

    internal inner class ThreeDSecureCallback : Gateway3DSecureCallback {
        override fun on3DSecureCancel() {
            showError()
        }

        override fun on3DSecureComplete(result: GatewayMap) {

            if ("DO_NOT_PROCEED".equals((result["response.gatewayRecommendation"] as String?)!!, ignoreCase = true)) {
                showError()
                return
            }

            binding.check3dsProgress.visibility = View.GONE
            binding.check3dsSuccess.visibility = View.VISIBLE

            processPayment()
        }

        fun showError() {
            binding.check3dsProgress.visibility = View.GONE
            binding.check3dsError.visibility = View.VISIBLE

            showResult(R.drawable.failed, R.string.pay_error_3ds_authentication_failed)
        }
    }

    internal inner class CompleteSessionCallback : ApiController.CompleteSessionCallback {
        override fun onSuccess(result: String) {
            binding.processPaymentProgress.visibility = View.GONE
            binding.processPaymentSuccess.visibility = View.VISIBLE

            showResult(R.drawable.success, R.string.pay_you_payment_was_successful)
        }

        override fun onError(throwable: Throwable) {
            Log.e(TAG, throwable.message, throwable)

            binding.processPaymentProgress.visibility = View.GONE
            binding.processPaymentError.visibility = View.VISIBLE

            showResult(R.drawable.failed, R.string.pay_error_processing_your_payment)
        }
    }

    internal inner class ThreeDS2InitializationCallback : Mock3DS2Service.InitializeCallback {
        override fun initializeComplete() {
            Log.i(TAG, "3DS2 Initialization Complete")
        }

        override fun initializeError(e: java.lang.Exception) {
            Log.e(TAG, "An error occured initializing 3DS2", e)
        }
    }

    companion object {

        internal val TAG = ProcessPaymentActivity::class.java.simpleName

        internal const val REQUEST_CARD_INFO = 100


        // static for demo
        internal const val AMOUNT = "1.00"
        internal const val CURRENCY = "USD"
    }
}
