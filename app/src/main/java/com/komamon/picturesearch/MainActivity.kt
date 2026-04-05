package com.komamon.picturesearch

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import com.komamon.picturesearch.ui.screen.ClearScreen
import com.komamon.picturesearch.ui.screen.QuizScreen
import com.komamon.picturesearch.ui.screen.TitleScreen
import com.komamon.picturesearch.ui.theme.PictureSearchTheme
import com.komamon.picturesearch.viewmodel.AppScreen
import com.komamon.picturesearch.viewmodel.QuizViewModel

class MainActivity : ComponentActivity() {
    private val viewModel: QuizViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            PictureSearchTheme(darkTheme = false) {
                val uiState by viewModel.uiState.collectAsState()

                when (uiState.screen) {
                    AppScreen.TITLE -> TitleScreen(
                        savedProgress        = viewModel.savedProgress,
                        onStartFromBeginning = viewModel::startFromBeginning,
                        onContinue           = viewModel::resumeFromSaved
                    )
                    AppScreen.QUIZ -> QuizScreen(
                        uiState = uiState,
                        onChoiceTapped = viewModel::onChoiceTapped,
                        onNextQuestion = viewModel::onNextQuestion
                    )
                    AppScreen.CLEAR -> ClearScreen(
                        onReset = viewModel::resetQuiz
                    )
                }
            }
        }
    }
}
