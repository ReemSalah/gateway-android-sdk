package com.mastercard.gateway.android.sampleapp

import android.content.Context
import android.content.SharedPreferences
import android.preference.PreferenceManager

import com.mastercard.gateway.android.sdk.Gateway

enum class Config constructor(internal var defValue: String) {

    MERCHANT_ID(""),
    REGION(Gateway.Region.NORTH_AMERICA.name),
    MERCHANT_URL("");

    fun getValue(context: Context): String? {
        PreferenceManager.getDefaultSharedPreferences(context).apply {
            return@getValue getString(name, defValue)
        }
    }

    fun setValue(context: Context, value: String) {
        PreferenceManager.getDefaultSharedPreferences(context).apply {
            edit().apply {
                putString(name, value)
                apply()
            }
        }
    }
}
