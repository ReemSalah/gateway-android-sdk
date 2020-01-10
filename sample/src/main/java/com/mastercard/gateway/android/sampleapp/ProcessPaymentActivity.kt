package com.mastercard.gateway.android.sampleapp

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
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
import com.nds.threeds.core.*
import java.util.*
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import org.emvco.threeds.core.ChallengeParameters


class ProcessPaymentActivity : AppCompatActivity(), ActivityCompat.OnRequestPermissionsResultCallback {

    companion object {
        internal val TAG = ProcessPaymentActivity::class.java.simpleName
        internal const val REQUEST_CARD_INFO = 100
        internal const val REQUEST_READ_PHONE_STATE = 1000

        // static for demo
        internal const val AMOUNT = "1.00"
        internal const val CURRENCY = "AUD"
    }

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

    internal lateinit var threeDSSDK: ThreeDSSDK
    internal lateinit var threeDSTransaction: EMVTransaction

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_process_payment)

        binding = DataBindingUtil.setContentView(this, R.layout.activity_process_payment)

        // init api controller
        apiController.setMerchantServerUrl(Config.MERCHANT_URL.getValue(this))

        // init gateway
        gateway = Gateway()
        gateway.merchantId = Config.MERCHANT_ID.getValue(this)
        try {
            gateway.region = Gateway.Region.valueOf(Config.REGION.getValue(this))
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

        val permissionCheck = ContextCompat.checkSelfPermission(this, Manifest.permission.READ_PHONE_STATE)

        if (permissionCheck != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.READ_PHONE_STATE), REQUEST_READ_PHONE_STATE)
        } else {
            init3DS2SDK()
        }
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

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        when (requestCode) {
            REQUEST_READ_PHONE_STATE -> if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                init3DS2SDK()
            }
            else -> {
            }
        }

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

        threeDSSDK = ThreeDSSDK.Builder()
                .configParameters(EMVConfigParameters())
                .uiCustomization(EMVUiCustomization())
                .build(this) // this == activity


        threeDSSDK.initialize(object : ThreeDSInitializationCallback {
            // 3DS SDK successfully initialized
            override fun success() {
                Log.d(ProcessPaymentActivity::class.java.simpleName, "NuData initialization complete")
            }

            // 3DS SDK failed to initialize
            override fun error(e: Exception?) {
                Log.e(ProcessPaymentActivity::class.java.simpleName, "NuData initialization error", e)
            }
        })
    }

    internal fun createSession() {
        binding.startButton.isEnabled = false
        binding.createSessionProgress.visibility = View.VISIBLE

        apiController.createSession(CreateSessionCallback())
    }

    internal fun addCurrencyToSession() {
        apiController.updateSessionWithOrderDetails(sessionId, orderId, AMOUNT, CURRENCY, AddCurrencyCodeToSessionCallback())
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

    internal fun showResult(@DrawableRes iconId: Int, message: String) {
        binding.resultIcon.setImageResource(iconId)
        binding.resultText.text = message

        binding.groupConfirm.visibility = View.GONE
        binding.groupResult.visibility = View.VISIBLE
    }


    internal inner class CreateSessionCallback : ApiController.CreateSessionCallback {
        override fun onSuccess(sessionId: String, apiVersion: String) {
            Log.i("CreateSessionTask", "Session established")

            this@ProcessPaymentActivity.sessionId = sessionId
            this@ProcessPaymentActivity.apiVersion = apiVersion

            addCurrencyToSession()
        }

        override fun onError(throwable: Throwable) {
            Log.e(TAG, throwable.message, throwable)

            binding.createSessionProgress.visibility = View.GONE
            binding.createSessionError.visibility = View.VISIBLE

            showResult(R.drawable.failed, R.string.pay_error_unable_to_create_session)
        }
    }

    internal inner class AddCurrencyCodeToSessionCallback : ApiController.UpdateSessionCallback {
        override fun onSuccess(result: String) {
            Log.i("UpdateSessionTask", "Currency code added to session")
            binding.createSessionProgress.visibility = View.GONE
            binding.createSessionSuccess.visibility = View.VISIBLE

            collectCardInfo()
        }

        override fun onError(throwable: Throwable) {
            Log.e(TAG, throwable.message, throwable)

            binding.createSessionProgress.visibility = View.GONE
            binding.createSessionError.visibility = View.VISIBLE

            showResult(R.drawable.failed, R.string.pay_error_unable_to_add_currency_code_to_session)
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
            Log.i(TAG, "Initiated Authentication Complete")

            handle3DS2Response(response) // TEMP hotwire

//            when (response["authentication.version"]) {
//                "3DS2" -> handle3DS2Response(response)
//                "3DS1" -> handle3DS1Response(response)
//                else -> processPayment()
//            }
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
            Log.i(TAG, "Authenticate Payer Complete")

            val acsTransactionId = response["authentication.3ds2.acsTransactionId"] as String?
                    ?: "acTransactionID"
            val acsRefNumber = response["authentication.3ds2.acsRefNumber"] as String?
                    ?: "acsRefNumber"
            val acsSignedContent = response["authentication.3ds2.acsSignedContent"] as String?
                    ?: "acsSignedContent"

            val timeout = response["authentication.3ds2.sdk.timeout"] as Int? ?: 0




//            val challengeParameters = EMVChallengeParameters(threeDSTransaction.getAuthenticationRequestParameters().sdkTransactionID,
//                    acsTransactionId,
//                    acsRefNumber,
//                    acsSignedContent,
//                    "No idea" /*TODO fix this*/
//            )
//
//            threeDSTransaction.doChallenge(this@ProcessPaymentActivity, challengeParameters, StatusReceiver(), timeout)
        }

        override fun onError(throwable: Throwable) {
            Log.e(TAG, throwable.message, throwable)

            binding.check3dsProgress.visibility = View.GONE
            binding.check3dsError.visibility = View.VISIBLE

            showResult(R.drawable.failed, R.string.pay_error_3ds_authentication_failed)
        }

    }

    private fun handle3DS1Response(response: GatewayMap) {
        var html: String? = null
        if (response.containsKey("authentication.redirectHtml")) {
            html = response["authentication.redirectHtml"] as String?
        }

        val gatewayRecommendation = response["response.gatewayRecommendation"] as String?

        when (gatewayRecommendation) {
            "DO_NOT_PROCEED" -> {
                // if DO_NOT_PROCEED returned in recommendation, should stop transaction
                binding.check3dsProgress.visibility = View.GONE
                binding.check3dsError.visibility = View.VISIBLE

                showResult(R.drawable.failed, R.string.pay_error_3ds_authentication_failed)
                return
            }
            "PROCEED_WITH_AUTHENTICATION" -> {
                // if PROCEED returned in recommendation, start 3DS 1.0
                Gateway.start3DSecureActivity(this@ProcessPaymentActivity, html!!)
                return
            }
            else -> processPayment()
        }
    }

    private fun handle3DS2Response(response: GatewayMap) {
//        val directoryServerId = response["authentication.3ds2.directoryServerId"] as String
//        val messageVersion = response["authentication.3ds2.messageVersion"] as String? ?: "2.1"

        val directoryServerId = "A000000004"
        val messageVersion = "2.1.0"

//        threeDSTransaction = mock3DS2Service.createTransaction(directoryServerId, messageVersion)
        threeDSTransaction = threeDSSDK.threeDS2Service().createTransaction(directoryServerId, messageVersion)

        val requestParams = threeDSTransaction.authenticationRequestParameters

        // TODO this is just a test value
        val referenceNumber = "SDKREF00000000000000000000000001"

        // build the gateway request
        val request = GatewayMap()
                .set("authentication.3ds2.sdk.appId", requestParams.sdkAppID)
                .set("authentication.3ds2.sdk.encryptedData", requestParams.deviceData)
                .set("authentication.3ds2.sdk.ephemeralPublicKey", requestParams.sdkEphemeralPublicKey)
                .set("authentication.3ds2.sdk.referenceNumber", referenceNumber)
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


}
