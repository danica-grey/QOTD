package com.example.qotd

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.qotd.ui.theme.QOTDTheme

class ReplaceQuestionActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        setContent {
            QOTDTheme {
                Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                    ReplaceQuestionAnswerScreen(modifier = Modifier.padding(innerPadding))
                }
            }
        }
    }
}

@Composable
fun ReplaceQuestionAnswerScreen(modifier: Modifier = Modifier) {
    val userQuestionPrompt = "Your Question:"
    var userQuestion by remember { mutableStateOf("") }
    val userAnswerPrompt = "Your Answer:"
    var userAnswer by remember { mutableStateOf("") }
    var message by remember { mutableStateOf("") }
    var isSubmitted by remember { mutableStateOf(false) } 

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.Center
    ) {
        Text(text = userQuestionPrompt, style = MaterialTheme.typography.headlineMedium)

        Spacer(modifier = Modifier.height(10.dp))

        OutlinedTextField(
            value = userQuestion,
            onValueChange = { userQuestion = it },
            label = { Text("Let's hear it!") },
            modifier = Modifier.fillMaxWidth(),
            isError = isSubmitted && userQuestion.isBlank(), 
            singleLine = true
        )

        Spacer(modifier = Modifier.height(48.dp))

        Text(text = userAnswerPrompt, style = MaterialTheme.typography.headlineMedium)

        Spacer(modifier = Modifier.height(10.dp))

        OutlinedTextField(
            value = userAnswer,
            onValueChange = { userAnswer = it },
            label = { Text("Your turn!") },
            modifier = Modifier.fillMaxWidth(),
            isError = isSubmitted && userAnswer.isBlank(),
            singleLine = true
        )

        Spacer(modifier = Modifier.height(32.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.End
        ) {
            Button(
                onClick = {
                    isSubmitted = true 
                    submitQuestionPlusAnswer(userQuestion, userAnswer) { status ->
                        message = status
                        userAnswer = ""
                    }
                },
                modifier = Modifier
                    .height(52.dp)
            ) {
                Text("Submit", fontSize = 20.sp)
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        if (message.isNotEmpty()) {
            Text(text = message, color = MaterialTheme.colorScheme.primary)
        }
    }
}

fun submitQuestionPlusAnswer(userQuestion: String, userAnswer: String, callback: (String) -> Unit) {
    if (userAnswer.isBlank() || userQuestion.isBlank()) {
        callback("Please write both your question and answer before clicking.")
        return
    } else {
        callback("Submission successful!")
        return
    }
}
