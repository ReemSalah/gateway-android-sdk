package com.mastercard.gateway.android.sampleapp

import android.content.Intent
import androidx.databinding.DataBindingUtil
import android.os.Bundle
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import android.text.Editable
import android.text.TextUtils
import android.text.TextWatcher
import android.util.Log

import com.mastercard.gateway.android.sampleapp.databinding.ActivityMainBinding
import com.mastercard.gateway.android.sdk.Gateway
import com.nds.threeds.core.EMVConfigParameters
import com.nds.threeds.core.EMVUiCustomization
import com.nds.threeds.core.ThreeDSInitializationCallback
import com.nds.threeds.core.ThreeDSSDK

class MainActivity : AppCompatActivity() {

    internal lateinit var binding: ActivityMainBinding
    internal var textChangeListener = TextChangeListener()

    internal lateinit var threeDSSDK: ThreeDSSDK

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        binding = DataBindingUtil.setContentView(this, R.layout.activity_main)

        binding.merchantId.apply {
            setText(Config.MERCHANT_ID.getValue(this@MainActivity))
            addTextChangedListener(textChangeListener)
        }

        binding.region.apply {
            setText(Config.REGION.getValue(this@MainActivity))
            addTextChangedListener(textChangeListener)
            setOnFocusChangeListener { _, hasFocus ->
                if (hasFocus) {
                    clearFocus()
                    showRegionPicker()
                }
            }
        }


        binding.merchantUrl.apply {
            setText(Config.MERCHANT_URL.getValue(this@MainActivity))
            addTextChangedListener(textChangeListener)
        }

        binding.processPaymentButton.setOnClickListener { v -> goTo(ProcessPaymentActivity::class.java) }

        enableButtons()


        threeDSSDK = ThreeDSSDK.Builder()
                .configParameters(EMVConfigParameters())
                .uiCustomization(EMVUiCustomization())
                .build(this) // this == activity

        threeDSSDK.initialize(object : ThreeDSInitializationCallback {
            // 3DS SDK successfully initialized
            override fun success() {
                Log.d(MainActivity::class.java.simpleName, "NuData initialization complete")
            }

            // 3DS SDK failed to initialize
            override fun error(e: Exception?) {
                Log.e(MainActivity::class.java.simpleName, "NuData initialization error", e)
            }
        })
    }

    internal fun goTo(klass: Class<*>) {
        startActivity(Intent(this, klass))
    }

    internal fun persistConfig() {
        Config.MERCHANT_ID.setValue(this, binding.merchantId.text!!.toString())
        Config.REGION.setValue(this, binding.region.text!!.toString())
        Config.MERCHANT_URL.setValue(this, binding.merchantUrl.text!!.toString())

        // update api controller url
        ApiController.instance.setMerchantServerUrl(Config.MERCHANT_URL.getValue(this))
    }

    internal fun enableButtons() {
        binding.processPaymentButton.isEnabled = (!TextUtils.isEmpty(binding.merchantId.text)
                && !TextUtils.isEmpty(binding.region.text)
                && !TextUtils.isEmpty(binding.merchantUrl.text))
    }

    internal fun showRegionPicker() {
        val regions = Gateway.Region.values()
        val items = arrayOfNulls<String>(regions.size + 1)
        items[0] = getString(R.string.none)
        for (i in regions.indices) {
            items[i + 1] = regions[i].name
        }

        AlertDialog.Builder(this)
                .setTitle(R.string.main_select_region)
                .setItems(items) { dialog, which ->
                    binding.region.setText(if (which == 0) "" else items[which])
                    dialog.cancel()
                }
                .show()
    }

    internal inner class TextChangeListener : TextWatcher {
        override fun beforeTextChanged(charSequence: CharSequence, i: Int, i1: Int, i2: Int) {

        }

        override fun onTextChanged(charSequence: CharSequence, i: Int, i1: Int, i2: Int) {
            enableButtons()
            persistConfig()
        }

        override fun afterTextChanged(editable: Editable) {

        }
    }
}
