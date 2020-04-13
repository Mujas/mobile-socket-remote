package com.creatricxz.remote.settings

import android.os.Bundle
import androidx.preference.PreferenceFragmentCompat
import com.creatricxz.remote.R

class PreferencesFragment : PreferenceFragmentCompat() {
    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        setPreferencesFromResource(R.xml.preferences, rootKey)
    }
}