package com.komamon.picturesearch.data

import android.content.Context

class PreferencesManager(context: Context) {
    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun saveProgress(questionIndex: Int) {
        prefs.edit().putInt(KEY_INDEX, questionIndex).apply()
    }

    fun loadProgress(): Int = prefs.getInt(KEY_INDEX, 0)

    fun saveSessionIds(ids: List<Int>) {
        prefs.edit().putString(KEY_SESSION_IDS, ids.joinToString(",")).apply()
    }

    fun loadSessionIds(): List<Int> {
        val str = prefs.getString(KEY_SESSION_IDS, "") ?: ""
        if (str.isEmpty()) return emptyList()
        return str.split(",").mapNotNull { it.toIntOrNull() }
    }

    fun clearProgress() {
        prefs.edit().remove(KEY_INDEX).remove(KEY_SESSION_IDS).apply()
    }

    companion object {
        private const val PREFS_NAME = "quiz_prefs"
        private const val KEY_INDEX = "current_index"
        private const val KEY_SESSION_IDS = "session_ids"
    }
}
