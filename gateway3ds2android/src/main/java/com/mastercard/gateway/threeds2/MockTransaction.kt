package com.mastercard.gateway.threeds2

import android.app.Activity
import android.content.Intent

class MockTransaction : Transaction {

    companion object {
        internal var currentStatusReceiver: ChallengeStatusReceiver? = null
    }

    override fun getAuthenticationRequestParameters(): AuthenticationRequestParameters {
        return AuthenticationRequestParameters("00000000-0000-0000-0000-000000000000", "deviceData", "sdkEphemeralPublicKey", "00000000-0000-0000-0000-000000000000", "00000000-0000-0000-0000-000000000000", "2.1.0")
    }

    override fun doChallenge(currentActivity: Activity, challengeParameters: ChallengeParameters, challengeStatusReceiver: ChallengeStatusReceiver, timeOut: Int) {
        currentStatusReceiver = challengeStatusReceiver

        val intent = Intent(currentActivity, ChallengeActivity::class.java)
        intent.putExtra(ChallengeActivity.EXTRA_TRANSACTION_ID, challengeParameters.threeDSServerTransactionID)
        currentActivity.startActivity(intent)
    }

    override fun getProgressView(currentActivity: Activity): ProgressView {
        return ProgressView(currentActivity)
    }

    override fun close() {
        // ?
    }
}