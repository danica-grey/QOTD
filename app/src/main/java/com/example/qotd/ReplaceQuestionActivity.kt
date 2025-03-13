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
    val userQuestionPrompt = "Please Write Your Question Below:"
    var userQuestion by remember { mutableStateOf ("") }
    val userAnswerPrompt = "Please Write Your Answer Below:"
    var userAnswer by remember { mutableStateOf("") }
    var message by remember { mutableStateOf("") }



    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.Center
    ) {
        Text(text = userQuestionPrompt, style = MaterialTheme.typography.headlineMedium)

        Spacer(modifier = Modifier.height(16.dp))

        BasicTextField(
            value = userQuestion,
            onValueChange = { userQuestion = it },
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(modifier = Modifier.height(16.dp))

        Text(text = userAnswerPrompt, style = MaterialTheme.typography.headlineMedium)

        Spacer(modifier = Modifier.height(16.dp))

        BasicTextField(
            value = userAnswer,
            onValueChange = { userAnswer = it },
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(modifier = Modifier.height(16.dp))

        Button(onClick = {
            submitQuestionPlusAnswer(userQuestion, userAnswer) { status ->
                message = status
                userAnswer = ""
            }
        }) {
            Text("Submit")
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
    }
    else {
        callback("Submission successful!")
        return
    }
}