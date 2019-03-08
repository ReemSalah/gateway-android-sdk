package com.mastercard.gateway.threeds2

import android.app.Activity
import android.content.Intent

class MockTransaction : Transaction {

    override fun getAuthenticationRequestParameters(): AuthenticationRequestParameters {
        return AuthenticationRequestParameters("transaction id", "encrypted data", "ephemeral public key", "app id", "ref number", "message version")
    }

    override fun doChallenge(currentActivity: Activity, challengeParameters: ChallengeParameters, challengeStatusReceiver: ChallengeStatusReceiver, timeOut: Int) {
        val intent = Intent()
        intent.putExtra(ChallengeActivity.EXTRA_RECEIVER, challengeStatusReceiver)
        currentActivity.startActivity(intent)
    }

    override fun getProgressView(currentActivity: Activity): ProgressView {
        return ProgressView(currentActivity)
    }

    override fun close() {
        // ?
    }
}