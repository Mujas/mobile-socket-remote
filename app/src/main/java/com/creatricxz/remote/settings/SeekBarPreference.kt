package com.creatricxz.remote.settings

import android.content.Context
import android.preference.DialogPreference
import android.util.AttributeSet
import android.view.Gravity
import android.view.View
import android.widget.LinearLayout
import android.widget.SeekBar
import android.widget.SeekBar.OnSeekBarChangeListener
import android.widget.TextView

class SeekBarPreference(
    private val mContext: Context,
    attrs: AttributeSet
) :
    DialogPreference(mContext, attrs), OnSeekBarChangeListener {
    private var mSeekBar: SeekBar? = null
    private var mSplashText: TextView? = null
    private var mValueText: TextView? = null
    private val mDialogMessage: String?
    private val mSuffix: String?
    private val mDefault: Int
    var max: Int
    private var mValue = 0
    override fun onCreateDialogView(): View {
        val params: LinearLayout.LayoutParams
        val layout = LinearLayout(mContext)
        layout.orientation = LinearLayout.VERTICAL
        layout.setPadding(6, 6, 6, 6)
        mSplashText = TextView(mContext)
        if (mDialogMessage != null) mSplashText!!.text = mDialogMessage
        layout.addView(mSplashText)
        mValueText = TextView(mContext)
        mValueText!!.gravity = Gravity.CENTER_HORIZONTAL
        mValueText!!.textSize = 32f
        params = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.FILL_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        )
        layout.addView(mValueText, params)
        mSeekBar = SeekBar(mContext)
        mSeekBar!!.setOnSeekBarChangeListener(this)
        layout.addView(
            mSeekBar,
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.FILL_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
        )
        mSeekBar!!.max = max
        if (shouldPersist()) mValue = getPersistedInt(mDefault)
        mSeekBar!!.progress = mValue
        return layout
    }

    public override fun onDialogClosed(positive: Boolean) {
        if (positive) {
            if (shouldPersist()) persistInt(mValue)
            callChangeListener(mValue)
        }
        super.onDialogClosed(positive)
    }

    override fun onBindDialogView(v: View) {
        super.onBindDialogView(v)
        mSeekBar!!.max = max
        mSeekBar!!.progress = mValue
    }

    override fun onSetInitialValue(
        restore: Boolean,
        defaultValue: Any
    ) {
        super.onSetInitialValue(restore, defaultValue)
        mValue =
            if (restore) if (shouldPersist()) getPersistedInt(mDefault) else 0 else defaultValue as Int
    }

    override fun onProgressChanged(
        seek: SeekBar,
        value: Int,
        fromTouch: Boolean
    ) {
        val t = value.toString()
        mValueText!!.text = if (mSuffix == null) t else t + mSuffix
        mValue = value
    }

    override fun onStartTrackingTouch(seek: SeekBar) {}
    override fun onStopTrackingTouch(seek: SeekBar) {}

    var progress: Int
        get() = mValue
        set(progress) {
            mValue = progress
            if (mSeekBar != null) mSeekBar!!.progress = progress
        }

    companion object {
        private const val androidns = "http://schemas.android.com/apk/res/android"
    }

    init {
        mDialogMessage = attrs.getAttributeValue(
            androidns,
            "dialogMessage"
        )
        mSuffix =
            attrs.getAttributeValue(androidns, "text")
        mDefault = attrs.getAttributeIntValue(
            androidns,
            "defaultValue",
            0
        )
        max = attrs.getAttributeIntValue(
            androidns,
            "max",
            100
        )
    }
}