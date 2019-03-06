package com.mastercard.gateway.threeds2

class AuthenticationRequestParameters(
        val sdkTransactionID: String,
        val deviceData: String,
        val sdkEphemeralPublicKey: String,
        val sdkAppID: String,
        val sdkReferenceNumber: String,
        val messageVersion: String
)