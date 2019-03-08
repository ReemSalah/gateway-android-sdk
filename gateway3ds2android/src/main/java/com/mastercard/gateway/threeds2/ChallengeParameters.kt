package com.mastercard.gateway.threeds2

class ChallengeParameters (
    var threeDSServerTransactionID: String? = null,
    var acsTransactionID: String? = null,
    var acsRefNumber: String? = null,
    var acsSignedContent: String? = null,
    var threeDSRequestorAppURL: String? = null
)