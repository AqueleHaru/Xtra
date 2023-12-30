package com.github.andreyasadchy.xtra.ui

import android.view.MotionEvent

fun MotionEvent.isClick(outDownLocation: FloatArray): Boolean {
    return when (actionMasked) {
        MotionEvent.ACTION_DOWN -> {
            outDownLocation[0] = x
            outDownLocation[1] = y
            false
        }
        MotionEvent.ACTION_UP -> {
            outDownLocation[0] in x - 50..x + 50 && outDownLocation[1] in y - 50..y + 50 && eventTime - downTime <= 500
        }
        else -> false
    }
}