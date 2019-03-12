package com.mastercard.gateway.threeds2

import android.os.Bundle
import android.view.Menu
import android.view.MenuItem
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

        binding.buttonTimeout.setOnClickListener {
            receiver.timedout()
            finish()
        }

        binding.buttonProtocolError.setOnClickListener {
            receiver.protocolError(ProtocolErrorEvent(ErrorMessage("some error code", "some component", binding.textProtocolErrorMessage.text.toString(), null, null, "2.1.0"), transactionID))
            finish()
        }

        binding.buttonRuntimeError.setOnClickListener {
            receiver.runtimeError(RuntimeErrorEvent(binding.textRuntimeErrorCode.text.toString(), binding.textRuntimeErrorMessage.text.toString()))
            finish()
        }
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.challenge, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            R.id.menu_cancel -> {
                receiver.cancelled()
                finish()
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }

    override fun onBackPressed() {
        // capture back press and do nothing
    }
}