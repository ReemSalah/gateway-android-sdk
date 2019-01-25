package com.mastercard.gateway.android.sdk

interface Gateway3DSecureCallback {

    /**
     * Callback method when webview-based 3DS authentication is complete
     *
     * @param acsResult A response map containing the ACS result
     */
    fun on3DSecureComplete(acsResult: GatewayMap)

    /**
     * Callback when a user cancels the 3DS authentication flow. (typically on back press)
     */
    fun on3DSecureCancel()
}
