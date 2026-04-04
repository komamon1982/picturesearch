package com.komamon.picturesearch.data

import android.content.Context

class PreferencesManager(context: Context) {
    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun saveProgress(questionIndex: Int) {
        prefs.edit().putInt(KEY_INDEX, questionIndex).apply()
    }

    fun loadProgress(): Int = prefs.getInt(KEY_INDEX, 0)

    fun clearProgress() {
        prefs.edit().remove(KEY_INDEX).apply()
    }

    companion object {
        private const val PREFS_NAME = "quiz_prefs"
        private const val KEY_INDEX = "current_index"
    }
}
