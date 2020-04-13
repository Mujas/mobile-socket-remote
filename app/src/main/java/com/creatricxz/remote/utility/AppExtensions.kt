package com.creatricxz.remote.utility

import android.content.Context
import android.widget.Toast

fun Context.ShowToastMessage(msg: String?) {
    Toast.makeText(this, msg, Toast.LENGTH_LONG).show()
}

fun Context.IsInternetAvailable(): Boolean {
    return true
}