package com.mastercard.gateway.threeds2

interface Transaction {
    fun getAuthenticationrequestParameters(): AuthenticationRequestParameters
    fun doChallenge()
    fun getProgressView(): ProgressView
    fun close()
}