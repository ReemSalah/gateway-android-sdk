package com.mastercard.gateway.android.sampleapp

import android.app.Activity
import android.content.Intent
import androidx.databinding.DataBindingUtil
import android.graphics.Paint
import android.os.Bundle
import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.appcompat.app.AppCompatActivity
import android.util.Log
import android.view.View

import com.mastercard.gateway.android.sampleapp.databinding.ActivityProcessPaymentBinding
import com.mastercard.gateway.android.sdk.Gateway
import com.mastercard.gateway.android.sdk.Gateway3DSecureCallback
import com.mastercard.gateway.android.sdk.GatewayCallback
import com.mastercard.gateway.android.sdk.GatewayMap

import java.util.UUID

class ProcessPaymentActivity : AppCompatActivity() {

    internal lateinit var binding: ActivityProcessPaymentBinding
    internal lateinit var gateway: Gateway
    internal lateinit var sessionId: String
    internal lateinit var apiVersion: String
    internal lateinit var orderId: String
    internal lateinit var transactionId: String

    internal var threeDSecureId: String? = null
    internal var isGooglePay = false
    internal var apiController = ApiController.instance

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = DataBindingUtil.setContentView(this, R.layout.activity_process_payment)

        // init api controller
        apiController.setMerchantServerUrl(Config.MERCHANT_URL.getValue(this))

        // init gateway
        gateway = Gateway()
        gateway.setMerchantId(Config.MERCHANT_ID.getValue(this)!!)
        try {
            val region = Gateway.Region.valueOf(Config.REGION.getValue(this))
            gateway.setRegion(region)
        } catch (e: Exception) {
            Log.e(ProcessPaymentActivity::class.java.simpleName, "Invalid Gateway region value provided", e)
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

        // generate a random 3DSecureId for testing
        var threeDSId = UUID.randomUUID().toString()
        threeDSId = threeDSId.substring(0, threeDSId.indexOf('-'))

        apiController.check3DSecureEnrollment(sessionId, AMOUNT, CURRENCY, threeDSId, Check3DSecureEnrollmentCallback())
    }

    internal fun processPayment() {
        binding.processPaymentProgress.visibility = View.VISIBLE

        apiController.completeSession(sessionId, orderId, transactionId, AMOUNT, CURRENCY, threeDSecureId, isGooglePay, CompleteSessionCallback())
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
            Log.e(ProcessPaymentActivity::class.java.simpleName, throwable.message, throwable)

            binding.createSessionProgress.visibility = View.GONE
            binding.createSessionError.visibility = View.VISIBLE

            showResult(R.drawable.failed, R.string.pay_error_unable_to_create_session)
        }
    }

    internal inner class UpdateSessionCallback : GatewayCallback {
        override fun onSuccess(response: GatewayMap) {
            Log.i(ProcessPaymentActivity::class.java.simpleName, "Successfully updated session")
            binding.updateSessionProgress.visibility = View.GONE
            binding.updateSessionSuccess.visibility = View.VISIBLE

            binding.startButton.visibility = View.GONE
            binding.groupConfirm.visibility = View.VISIBLE
        }

        override fun onError(throwable: Throwable) {
            Log.e(ProcessPaymentActivity::class.java.simpleName, throwable.message, throwable)

            binding.updateSessionProgress.visibility = View.GONE
            binding.updateSessionError.visibility = View.VISIBLE

            showResult(R.drawable.failed, R.string.pay_error_unable_to_update_session)
        }
    }

    internal inner class Check3DSecureEnrollmentCallback : ApiController.Check3DSecureEnrollmentCallback {
        override fun onSuccess(response: GatewayMap) {
            val apiVersionInt = Integer.valueOf(apiVersion)
            val threeDSecureId = response["gatewayResponse.3DSecureID"] as String?

            var html: String? = null
            if (response.containsKey("gatewayResponse.3DSecure.authenticationRedirect.simple.htmlBodyContent")) {
                html = response["gatewayResponse.3DSecure.authenticationRedirect.simple.htmlBodyContent"] as String?
            }

            // for API versions <= 46, you must use the summary status field to determine next steps for 3DS
            if (apiVersionInt <= 46) {
                val summaryStatus = response["gatewayResponse.3DSecure.summaryStatus"] as String?

                if ("CARD_ENROLLED".equals(summaryStatus!!, ignoreCase = true)) {
                    Gateway.start3DSecureActivity(this@ProcessPaymentActivity, html!!)
                    return
                }

                binding.check3dsProgress.visibility = View.GONE
                binding.check3dsSuccess.visibility = View.VISIBLE
                this@ProcessPaymentActivity.threeDSecureId = null

                // for these 2 cases, you still provide the 3DSecureId with the pay operation
                if ("CARD_NOT_ENROLLED".equals(summaryStatus, ignoreCase = true) || "AUTHENTICATION_NOT_AVAILABLE".equals(summaryStatus, ignoreCase = true)) {
                    this@ProcessPaymentActivity.threeDSecureId = threeDSecureId
                }

                processPayment()
            } else {
                val gatewayRecommendation = response["gatewayResponse.response.gatewayRecommendation"] as String?

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

                this@ProcessPaymentActivity.threeDSecureId = threeDSecureId

                processPayment()
            }// for API versions >= 47, you must look to the gateway recommendation and the presence of 3DS info in the payload
        }

        override fun onError(throwable: Throwable) {
            Log.e(ProcessPaymentActivity::class.java.simpleName, throwable.message, throwable)

            binding.check3dsProgress.visibility = View.GONE
            binding.check3dsError.visibility = View.VISIBLE

            showResult(R.drawable.failed, R.string.pay_error_3ds_authentication_failed)
        }
    }

    internal inner class ThreeDSecureCallback : Gateway3DSecureCallback {
        override fun on3DSecureCancel() {
            showError()
        }

        override fun on3DSecureComplete(result: GatewayMap) {
            val apiVersionInt = Integer.valueOf(apiVersion)

            if (apiVersionInt <= 46) {
                if ("AUTHENTICATION_FAILED".equals((result["3DSecure.summaryStatus"] as String?)!!, ignoreCase = true)) {
                    showError()
                    return
                }
            } else { // version >= 47
                if ("DO_NOT_PROCEED".equals((result["response.gatewayRecommendation"] as String?)!!, ignoreCase = true)) {
                    showError()
                    return
                }
            }

            binding.check3dsProgress.visibility = View.GONE
            binding.check3dsSuccess.visibility = View.VISIBLE

            this@ProcessPaymentActivity.threeDSecureId = threeDSecureId

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
            Log.e(ProcessPaymentActivity::class.java.simpleName, throwable.message, throwable)

            binding.processPaymentProgress.visibility = View.GONE
            binding.processPaymentError.visibility = View.VISIBLE

            showResult(R.drawable.failed, R.string.pay_error_processing_your_payment)
        }
    }

    companion object {

        internal const val REQUEST_CARD_INFO = 100

        // static for demo
        internal const val AMOUNT = "1.00"
        internal const val CURRENCY = "USD"
    }
}
