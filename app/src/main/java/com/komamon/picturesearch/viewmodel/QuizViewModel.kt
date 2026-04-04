package com.komamon.picturesearch.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.komamon.picturesearch.data.PreferencesManager
import com.komamon.picturesearch.data.QuizRepository
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

enum class AppScreen { TITLE, QUIZ, CLEAR }

data class ChoiceItem(
    val imageRes: Int,
    val isCorrect: Boolean
)

data class QuizUiState(
    val screen: AppScreen = AppScreen.TITLE,
    val currentIndex: Int = 0,
    val total: Int = QuizRepository.questions.size,
    val questionImageRes: Int = 0,
    val choices: List<ChoiceItem> = emptyList(),
    val wrongTappedIndex: Int? = null,
    val showCorrect: Boolean = false
)

class QuizViewModel(application: Application) : AndroidViewModel(application) {
    private val prefsManager = PreferencesManager(application)
    private val questions = QuizRepository.questions

    private val _uiState = MutableStateFlow(QuizUiState())
    val uiState: StateFlow<QuizUiState> = _uiState.asStateFlow()

    val savedProgress: Int get() = prefsManager.loadProgress()

    fun startFromBeginning() {
        prefsManager.clearProgress()
        loadQuestion(0)
        _uiState.update { it.copy(screen = AppScreen.QUIZ) }
    }

    fun resumeFromSaved() {
        val index = prefsManager.loadProgress()
        loadQuestion(index)
        _uiState.update { it.copy(screen = AppScreen.QUIZ) }
    }

    private fun loadQuestion(index: Int) {
        if (index >= questions.size) {
            _uiState.update { it.copy(screen = AppScreen.CLEAR) }
            return
        }
        val question = questions[index]
        val distractors = questions.filter { it.id != question.id }.shuffled().take(3)
        val choices = (distractors.map { ChoiceItem(it.imageRes, false) } +
                ChoiceItem(question.imageRes, true)).shuffled()

        _uiState.update {
            it.copy(
                currentIndex = index,
                questionImageRes = question.imageRes,
                choices = choices,
                wrongTappedIndex = null,
                showCorrect = false
            )
        }
    }

    fun onChoiceTapped(index: Int) {
        val state = _uiState.value
        if (state.showCorrect || state.wrongTappedIndex != null) return

        val choice = state.choices[index]
        if (choice.isCorrect) {
            _uiState.update { it.copy(showCorrect = true) }
        } else {
            _uiState.update { it.copy(wrongTappedIndex = index) }
            viewModelScope.launch {
                delay(700)
                _uiState.update { it.copy(wrongTappedIndex = null) }
            }
        }
    }

    fun onNextQuestion() {
        val nextIndex = _uiState.value.currentIndex + 1
        prefsManager.saveProgress(nextIndex)
        loadQuestion(nextIndex)
    }

    fun resetQuiz() {
        prefsManager.clearProgress()
        _uiState.update { it.copy(screen = AppScreen.TITLE) }
    }
}
