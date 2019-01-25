package com.mastercard.gateway.android.sdk


import com.google.android.gms.common.api.Status
import com.google.android.gms.wallet.PaymentData

import org.json.JSONObject

interface GatewayGooglePayCallback {

    /**
     * Called when payment data is returned from GooglePay
     *
     * @param paymentData A json object containing details about the payment
     * @see [PaymentData](https://developers.google.com/pay/api/android/reference/object.PaymentData)
     */
    fun onReceivedPaymentData(paymentData: JSONObject)

    /**
     * Called when a user cancels a GooglePay transaction
     */
    fun onGooglePayCancelled()

    /**
     * Called when an error occurs during a GooglePay transaction
     *
     * @param status The corresponding status object of the request
     */
    fun onGooglePayError(status: Status)
}
