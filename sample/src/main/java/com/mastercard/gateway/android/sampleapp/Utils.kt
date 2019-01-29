package com.mastercard.gateway.android.sampleapp

import androidx.appcompat.widget.AppCompatEditText

fun AppCompatEditText.isNotBlank(): Boolean {
    return this.text!!.isNotBlank()
}