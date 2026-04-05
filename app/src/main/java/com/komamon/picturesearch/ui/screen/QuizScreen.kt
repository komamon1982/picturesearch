package com.komamon.picturesearch.ui.screen

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.komamon.picturesearch.ui.component.ChoiceImageCard
import com.komamon.picturesearch.ui.component.CorrectOverlay
import com.komamon.picturesearch.viewmodel.QuizUiState

@Composable
fun QuizScreen(
    uiState: QuizUiState,
    onChoiceTapped: (Int) -> Unit,
    onNextQuestion: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFFFFFDE7))
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // 進捗
            Text(
                text = "${uiState.currentIndex + 1}  /  ${uiState.total}",
                fontSize = 18.sp,
                color = Color(0xFF795548)
            )
            Spacer(modifier = Modifier.height(4.dp))

            // 問題文
            Text(
                text = "これな～んだ？",
                fontSize = 28.sp,
                fontWeight = FontWeight.Bold,
                color = Color(0xFF5D4037)
            )
            Spacer(modifier = Modifier.height(10.dp))

            // 問題の絵（大）
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(0.38f),
                shape = RoundedCornerShape(16.dp),
                elevation = CardDefaults.cardElevation(defaultElevation = 4.dp),
                colors = CardDefaults.cardColors(containerColor = Color.White)
            ) {
                Image(
                    painter = painterResource(uiState.questionImageRes),
                    contentDescription = null,
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(8.dp),
                    contentScale = ContentScale.Fit
                )
            }

            Spacer(modifier = Modifier.height(10.dp))

            // 区切り線
            HorizontalDivider(
                color = Color(0xFF757575),
                thickness = 3.dp
            )

            Spacer(modifier = Modifier.height(10.dp))

            // 2×2 選択肢グリッド
            if (uiState.choices.size == 4) {
                Column(
                    modifier = Modifier
                        .weight(0.62f)
                        .fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Row(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        ChoiceImageCard(
                            imageRes = uiState.choices[0].imageRes,
                            isShaking = uiState.wrongTappedIndex == 0,
                            onClick = { onChoiceTapped(0) },
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxSize()
                        )
                        ChoiceImageCard(
                            imageRes = uiState.choices[1].imageRes,
                            isShaking = uiState.wrongTappedIndex == 1,
                            onClick = { onChoiceTapped(1) },
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxSize()
                        )
                    }
                    Row(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        ChoiceImageCard(
                            imageRes = uiState.choices[2].imageRes,
                            isShaking = uiState.wrongTappedIndex == 2,
                            onClick = { onChoiceTapped(2) },
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxSize()
                        )
                        ChoiceImageCard(
                            imageRes = uiState.choices[3].imageRes,
                            isShaking = uiState.wrongTappedIndex == 3,
                            onClick = { onChoiceTapped(3) },
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxSize()
                        )
                    }
                }
            }
        }

        // 正解オーバーレイ
        if (uiState.showCorrect) {
            CorrectOverlay(onAnimationEnd = onNextQuestion)
        }
    }
}
