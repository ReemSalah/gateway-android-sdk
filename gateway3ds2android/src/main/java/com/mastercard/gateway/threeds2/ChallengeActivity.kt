package com.mastercard.gateway.threeds2

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.databinding.DataBindingUtil
import com.mastercard.gateway.threeds2.databinding.ActivityChallengeBinding

class ChallengeActivity : AppCompatActivity() {

    companion object {
        const val EXTRA_TRANSACTION_ID = "com.mastercard.gateway.threeds.EXTRA_TRANSACTION_ID"
        const val EXTRA_RECEIVER = "com.mastercard.gateway.threeds.EXTRA_RECEIVER"
    }

    lateinit var transactionID: String
    lateinit var receiver: ChallengeStatusReceiver
    lateinit var binding: ActivityChallengeBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        transactionID = intent.getStringExtra(EXTRA_TRANSACTION_ID)
        receiver = intent.getSerializableExtra(EXTRA_RECEIVER) as ChallengeStatusReceiver

        binding = DataBindingUtil.setContentView(this, R.layout.activity_challenge)

        binding.buttonComplete.setOnClickListener {
            receiver.completed(CompletionEvent(transactionID, binding.textCompletionStatus.text.toString()))
            finish()
        }
    }
}