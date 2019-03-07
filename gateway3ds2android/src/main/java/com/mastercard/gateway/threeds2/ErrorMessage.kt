package com.mastercard.gateway.threeds2

class ErrorMessage(
        val errorCode: String,
        val errorComponent: String,
        val errorDescription: String,
        val errorDetails: String?,
        val errorMessageType: String?,
        val messageVersionNumber: String
)