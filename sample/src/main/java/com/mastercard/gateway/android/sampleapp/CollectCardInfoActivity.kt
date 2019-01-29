package com.mastercard.gateway.android.sampleapp

import android.app.Activity
import android.content.Intent
import androidx.databinding.DataBindingUtil
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import android.text.Editable
import android.text.TextWatcher
import android.util.Log
import android.view.View
import android.widget.Toast

import com.google.android.gms.common.api.ApiException
import com.google.android.gms.common.api.Status
import com.google.android.gms.wallet.IsReadyToPayRequest
import com.google.android.gms.wallet.PaymentDataRequest
import com.google.android.gms.wallet.PaymentsClient
import com.google.android.gms.wallet.Wallet
import com.google.android.gms.wallet.WalletConstants
import com.mastercard.gateway.android.sampleapp.databinding.ActivityCollectCardInfoBinding
import com.mastercard.gateway.android.sdk.Gateway
import com.mastercard.gateway.android.sdk.GatewayGooglePayCallback

import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject

import java.util.Arrays


class CollectCardInfoActivity : AppCompatActivity() {


    internal lateinit var binding: ActivityCollectCardInfoBinding
    internal lateinit var googlePayTxnAmount: String
    internal lateinit var googlePayTxnCurrency: String
    internal lateinit var paymentsClient: PaymentsClient
    internal var textChangeListener = TextChangeListener()
    internal var googlePayCallback = GooglePayCallback()

    internal val isReadyToPayRequest: JSONObject
        get() = baseRequest
                .put("allowedPaymentMethods", JSONArray()
                        .put(baseCardPaymentMethod))

    internal val cardPaymentMethod: JSONObject
        get() = baseCardPaymentMethod
                .put("tokenizationSpecification", tokenizationSpecification)

    internal val baseRequest: JSONObject
        get() = JSONObject()
                .put("apiVersion", 2)
                .put("apiVersionMinor", 0)

    internal val baseCardPaymentMethod: JSONObject
        get() = JSONObject()
                .put("type", "CARD")
                .put("parameters", JSONObject()
                        .put("allowedAuthMethods", allowedCardAuthMethods)
                        .put("allowedCardNetworks", allowedCardNetworks))

    internal val allowedCardNetworks: JSONArray
        get() = JSONArray()
                .put("AMEX")
                .put("DISCOVER")
                .put("MASTERCARD")
                .put("VISA")

    internal val allowedCardAuthMethods: JSONArray
        get() = JSONArray()
                .put("PAN_ONLY")
                .put("CRYPTOGRAM_3DS")

    internal val tokenizationSpecification: JSONObject
        get() = JSONObject()
                .put("type", "PAYMENT_GATEWAY")
                .put("parameters", JSONObject()
                        .put("gateway", "mpgs")
                        .put("gatewayMerchantId", Config.MERCHANT_ID.getValue(this)))

    internal val transactionInfo: JSONObject
        get() = JSONObject()
                .put("totalPrice", googlePayTxnAmount)
                .put("totalPriceStatus", "FINAL")
                .put("currencyCode", googlePayTxnCurrency)

    internal val merchantInfo: JSONObject
        get() = JSONObject()
                .put("merchantName", "Example Merchant")

    internal val paymentDataRequest: JSONObject
        get() = baseRequest
                .put("allowedPaymentMethods", JSONArray().put(cardPaymentMethod))
                .put("transactionInfo", transactionInfo)
                .put("merchantInfo", merchantInfo)


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        binding = DataBindingUtil.setContentView(this, R.layout.activity_collect_card_info)

        // get bundle extras and set txn amount and currency for google pay
        val i = intent
        googlePayTxnAmount = i.getStringExtra(EXTRA_GOOGLE_PAY_TXN_AMOUNT)
        googlePayTxnCurrency = i.getStringExtra(EXTRA_GOOGLE_PAY_TXN_CURRENCY)

        // init manual text field listeners
        binding.nameOnCard.requestFocus()
        binding.nameOnCard.addTextChangedListener(textChangeListener)
        binding.cardnumber.addTextChangedListener(textChangeListener)
        binding.expiryMonth.addTextChangedListener(textChangeListener)
        binding.expiryYear.addTextChangedListener(textChangeListener)
        binding.cvv.addTextChangedListener(textChangeListener)

        binding.submitButton.isEnabled = false
        binding.submitButton.setOnClickListener { v -> continueButtonClicked() }


        // init Google Pay client
        paymentsClient = Wallet.getPaymentsClient(this, Wallet.WalletOptions.Builder()
                .setEnvironment(WalletConstants.ENVIRONMENT_TEST)
                .build())

        // init google pay button
        binding.googlePayButton.setOnClickListener { v -> googlePayButtonClicked() }

        // check if Google Pay is available
        isReadyToPay()
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        // handle the Google Pay lifecycle
        if (Gateway.handleGooglePayResult(requestCode, resultCode, data!!, googlePayCallback)) {
            return
        }

        super.onActivityResult(requestCode, resultCode, data)
    }

    internal fun enableContinueButton() {
        binding.submitButton.isEnabled = binding.nameOnCard.isNotBlank() && binding.cardnumber.isNotBlank() &&
                binding.expiryMonth.isNotBlank() && binding.expiryYear.isNotBlank() &&
                binding.cvv.isNotBlank() && binding.cvv.text!!.toString().length >= 3
    }

    internal fun continueButtonClicked() {
        val nameOnCard = binding.nameOnCard.text!!.toString()
        val cardNumber = binding.cardnumber.text!!.toString()
        val expiryMM = binding.expiryMonth.text!!.toString()
        val expiryYY = binding.expiryYear.text!!.toString()
        val cvv = binding.cvv.text!!.toString()

        val i = Intent().apply {
            putExtra(EXTRA_CARD_DESCRIPTION, maskCardNumber(cardNumber))
            putExtra(EXTRA_CARD_NAME, nameOnCard)
            putExtra(EXTRA_CARD_NUMBER, cardNumber)
            putExtra(EXTRA_CARD_EXPIRY_MONTH, expiryMM)
            putExtra(EXTRA_CARD_EXPIRY_YEAR, expiryYY)
            putExtra(EXTRA_CARD_CVV, cvv)
        }

        setResult(Activity.RESULT_OK, i)
        finish()
    }

    internal fun googlePayButtonClicked() {
        try {
            val request = PaymentDataRequest.fromJson(paymentDataRequest.toString())

            // use the Gateway convenience handler for launching the Google Pay flow
            Gateway.requestGooglePayData(paymentsClient, request, this)

        } catch (e: JSONException) {
            Toast.makeText(this, "Could not request payment data", Toast.LENGTH_SHORT).show()
        }
    }

    internal fun returnCardInfo(paymentData: JSONObject) {
        val i = Intent()

        try {
            val paymentMethodData = paymentData.getJSONObject("paymentMethodData")
            val description = paymentMethodData.getString("description")
            val token = paymentMethodData.getJSONObject("tokenizationData")
                    .getString("token")

            i.putExtra(EXTRA_CARD_DESCRIPTION, description)
            i.putExtra(EXTRA_PAYMENT_TOKEN, token)

            setResult(Activity.RESULT_OK, i)
        } catch (e: Exception) {
            setResult(Activity.RESULT_CANCELED, i)
        }

        finish()
    }

    internal fun maskCardNumber(number: String): String {
        val maskLen = number.length - 4
        val mask = CharArray(maskLen)
        Arrays.fill(mask, '*')
        return String(mask) + number.substring(maskLen)
    }

    internal fun isReadyToPay() {
        try {
            val request = IsReadyToPayRequest.fromJson(isReadyToPayRequest.toString())

            val task = paymentsClient.isReadyToPay(request)
            task.addOnCompleteListener {
                try {
                    val result = it.getResult(ApiException::class.java)!!
                    if (result) {
                        // Show Google as payment option.
                        binding.orSeparator.visibility = View.VISIBLE
                        binding.googlePayButton.visibility = View.VISIBLE
                    } else {
                        // Hide Google as payment option.
                        binding.orSeparator.visibility = View.GONE
                        binding.googlePayButton.visibility = View.GONE
                    }
                } catch (e: ApiException) {
                }
            }
        } catch (e: JSONException) {
            // do nothing
        }

    }

    internal inner class TextChangeListener : TextWatcher {
        override fun beforeTextChanged(charSequence: CharSequence, i: Int, i1: Int, i2: Int) {

        }

        override fun onTextChanged(charSequence: CharSequence, i: Int, i1: Int, i2: Int) {
            enableContinueButton()
        }

        override fun afterTextChanged(editable: Editable) {

        }
    }

    internal inner class GooglePayCallback : GatewayGooglePayCallback {
        override fun onReceivedPaymentData(paymentData: JSONObject) {
            try {
                val description = paymentData.getJSONObject("paymentMethodData")
                        .getString("description")

                Log.d(GooglePayCallback::class.java.simpleName, "ReceivedPaymentData: $description")
            } catch (e: Exception) {

            }

            returnCardInfo(paymentData)
        }

        override fun onGooglePayCancelled() {
            Log.d(GooglePayCallback::class.java.simpleName, "Cancelled")
        }

        override fun onGooglePayError(status: Status) {
            Log.d(GooglePayCallback::class.java.simpleName, "Error")
            Toast.makeText(this@CollectCardInfoActivity, "Google Pay Error", Toast.LENGTH_SHORT).show()
        }
    }

    companion object {

        private const val EXTRA_PREFIX = "com.mastercard.gateway.sample.EXTRA_"

        // request
        const val EXTRA_GOOGLE_PAY_TXN_AMOUNT = EXTRA_PREFIX + "GOOGLE_PAY_TXN_AMOUNT"
        const val EXTRA_GOOGLE_PAY_TXN_CURRENCY = EXTRA_PREFIX + "GOOGLE_PAY_TXN_CURRENCY"

        // response
        const val EXTRA_CARD_DESCRIPTION = EXTRA_PREFIX + "CARD_DESCRIPTION"
        const val EXTRA_CARD_NAME = EXTRA_PREFIX + "CARD_NAME"
        const val EXTRA_CARD_NUMBER = EXTRA_PREFIX + "CARD_NUMBER"
        const val EXTRA_CARD_EXPIRY_MONTH = EXTRA_PREFIX + "CARD_EXPIRY_MONTH"
        const val EXTRA_CARD_EXPIRY_YEAR = EXTRA_PREFIX + "CARD_EXPIRY_YEAR"
        const val EXTRA_CARD_CVV = EXTRA_PREFIX + "CARD_CVC"
        const val EXTRA_PAYMENT_TOKEN = EXTRA_PREFIX + "PAYMENT_TOKEN"
    }
}
