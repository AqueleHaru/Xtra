package com.github.andreyasadchy.xtra.ui.settings

import android.app.Dialog
import android.content.Context
import android.content.DialogInterface
import android.os.Build
import android.os.Bundle
import android.view.View
import android.view.WindowInsets
import android.view.inputmethod.InputMethodManager
import android.widget.EditText
import androidx.preference.EditTextPreferenceDialogFragmentCompat
import com.google.android.material.dialog.MaterialAlertDialogBuilder

class MaterialEditTextPreference : EditTextPreferenceDialogFragmentCompat() {

    private var mEditText: EditText? = null
    private val mShowSoftInputRunnable = Runnable { scheduleShowSoftInputInner() }

    override fun onBindDialogView(view: View) {
        super.onBindDialogView(view)
        mEditText = view.findViewById(android.R.id.edit)
    }

    private fun scheduleShowSoftInputInner() {
        mEditText?.let { mEditText ->
            if (mEditText.isFocused) {
                val imm = mEditText.context.getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
                if (!imm.showSoftInput(mEditText, 0)) {
                    mEditText.removeCallbacks(mShowSoftInputRunnable)
                    mEditText.postDelayed(mShowSoftInputRunnable, 50)
                }
            }
        }
    }

    private var mWhichButtonClicked = 0

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        mWhichButtonClicked = DialogInterface.BUTTON_NEGATIVE
        val builder = MaterialAlertDialogBuilder(requireContext())
            .setTitle(preference.dialogTitle)
            .setIcon(preference.dialogIcon)
            .setPositiveButton(preference.positiveButtonText, this)
            .setNegativeButton(preference.negativeButtonText, this)
        val contentView = onCreateDialogView(requireContext())
        if (contentView != null) {
            onBindDialogView(contentView)
            builder.setView(contentView)
        } else {
            builder.setMessage(preference.dialogMessage)
        }
        onPrepareDialogBuilder(builder)

        // Create the dialog
        val dialog: Dialog = builder.create()
        requestInputMethod(dialog)
        return dialog
    }

    private fun requestInputMethod(dialog: Dialog) {
        val window = dialog.window
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            window!!.decorView.windowInsetsController!!.show(WindowInsets.Type.ime())
        } else {
            scheduleShowSoftInputInner()
        }
    }

    override fun onClick(dialog: DialogInterface, which: Int) {
        mWhichButtonClicked = which
    }

    override fun onDismiss(dialog: DialogInterface) {
        super.onDismiss(dialog)
        onDialogClosed(mWhichButtonClicked == DialogInterface.BUTTON_POSITIVE)
    }
}