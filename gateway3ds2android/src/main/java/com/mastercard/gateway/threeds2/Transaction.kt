package com.mastercard.gateway.threeds2

import android.app.Activity

interface Transaction {
    fun getAuthenticationRequestParameters(): AuthenticationRequestParameters
    fun doChallenge(currentActivity: Activity, challengeParameters: ChallengeParameters, challengeStatusReceiver: ChallengeStatusReceiver, timeOut: Int)
    fun getProgressView(currentActivity: Activity): ProgressView
    fun close()
}