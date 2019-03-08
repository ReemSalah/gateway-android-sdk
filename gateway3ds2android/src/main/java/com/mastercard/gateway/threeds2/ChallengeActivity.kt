package com.mastercard.gateway.threeds2

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.databinding.DataBindingUtil
import com.mastercard.gateway.threeds2.databinding.ActivityChallengeBinding

class ChallengeActivity : AppCompatActivity() {

    companion object {
        const val EXTRA_RECEIVER = "com.mastercard.gateway.threeds.EXTRA_RECEIVER"
    }

    lateinit var receiver: ChallengeStatusReceiver
    lateinit var binding: ActivityChallengeBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        receiver = intent.getSerializableExtra(EXTRA_RECEIVER) as ChallengeStatusReceiver

        binding = DataBindingUtil.setContentView(this, R.layout.activity_challenge)


    }
}