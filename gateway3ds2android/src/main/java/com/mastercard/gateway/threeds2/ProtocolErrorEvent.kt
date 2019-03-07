package com.mastercard.gateway.threeds2

class ProtocolErrorEvent(
        val errorMessage: ErrorMessage,
        val sdkTransactionId: String
)