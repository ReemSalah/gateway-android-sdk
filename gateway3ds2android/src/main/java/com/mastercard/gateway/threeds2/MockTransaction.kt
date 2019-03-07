package com.mastercard.gateway.threeds2

import android.app.Activity

class MockTransaction : Transaction {

    override fun getAuthenticationRequestParameters(): AuthenticationRequestParameters {
        return AuthenticationRequestParameters("transaction id", "encrypted data", "ephemeral public key", "app id", "ref number", "message version")
    }

    override fun doChallenge(currentActivity: Activity, challengeParameters: ChallengeParameters, challengeStatusReceiver: ChallengeStatusReceiver, timeOut: Int) {
        // turn around callback complete method (don't mock UI)
        challengeStatusReceiver.completed(CompletionEvent("transaction id", "transaction status"))
    }

    override fun getProgressView(currentActivity: Activity): ProgressView {
        return ProgressView(currentActivity)
    }

    override fun close() {
        // ?
    }
}