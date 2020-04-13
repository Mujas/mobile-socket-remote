package com.creatricxz.remote.settings

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.creatricxz.remote.R

class SettingsActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_settting)
        supportFragmentManager
            .beginTransaction()
            .replace(R.id.setting_container, PreferencesFragment())
            .commit()
    }
}