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
    }

    lateinit var transactionID: String
    lateinit var binding: ActivityChallengeBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = DataBindingUtil.setContentView(this, R.layout.activity_challenge)

        transactionID = intent.getStringExtra(EXTRA_TRANSACTION_ID)

        binding.buttonComplete.setOnClickListener {
            MockTransaction.currentStatusReceiver?.completed(CompletionEvent(transactionID, binding.textCompletionStatus.text.toString()))
            finish()
        }

        binding.buttonTimeout.setOnClickListener {
            MockTransaction.currentStatusReceiver?.timedout()
            finish()
        }

        binding.buttonProtocolError.setOnClickListener {
            MockTransaction.currentStatusReceiver?.protocolError(ProtocolErrorEvent(ErrorMessage("some error code", "some component", binding.textProtocolErrorMessage.text.toString(), null, null, "2.1.0"), transactionID))
            finish()
        }

        binding.buttonRuntimeError.setOnClickListener {
            MockTransaction.currentStatusReceiver?.runtimeError(RuntimeErrorEvent(binding.textRuntimeErrorCode.text.toString(), binding.textRuntimeErrorMessage.text.toString()))
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
                MockTransaction.currentStatusReceiver?.cancelled()
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