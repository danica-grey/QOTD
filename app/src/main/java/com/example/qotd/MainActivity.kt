package com.example.qotd

import android.content.Context
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import android.content.Intent
import com.example.qotd.ui.theme.QOTDTheme
import com.google.firebase.firestore.FirebaseFirestore




class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        setContent {
            QOTDTheme {
                Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                    QuestionAnswerScreen(modifier = Modifier.padding(innerPadding))
                }
            }
        }
    }
}

@Composable
fun QuestionAnswerScreen(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    var question by remember { mutableStateOf("Loading question...") }
    var userAnswer by remember { mutableStateOf("") }
    var message by remember { mutableStateOf("") }

    LaunchedEffect(Unit) {
        FirebaseFirestore.getInstance().collection("questions")
            //change to correct question ID can try to create something to randomize later
            .document("Question001")
            .get()
            .addOnSuccessListener { document ->
                question = document.getString("Question") ?: "No question found"
            }
            .addOnFailureListener {
                question = "Error loading question"
            }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.Center
    ) {
        Text(text = question, style = MaterialTheme.typography.headlineMedium)

        Spacer(modifier = Modifier.height(16.dp))

        BasicTextField(
            value = userAnswer,
            onValueChange = { userAnswer = it },
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(modifier = Modifier.height(16.dp))

        Button(onClick = {
            submitAnswer(userAnswer) { status ->
                message = status
                userAnswer = ""
            }
        }) {
            Text("Submit")
        }

        Spacer(modifier = Modifier.height(16.dp))


        Button(onClick = {
            val intent = Intent(context, ReplaceQuestionActivity::class.java)
            context.startActivity(intent)
        }) {
            Text("Click to Write Custom Question")
        }


        if (message.isNotEmpty()) {
            Text(text = message, color = MaterialTheme.colorScheme.primary)
        }
    }
}

fun submitAnswer(answer: String, callback: (String) -> Unit) {
    if (answer.isBlank()) {
        callback("Answer cannot be empty.")
        return
    }

    FirebaseFirestore.getInstance().collection("userAnswers").add(
        hashMapOf("answer" to answer)
    ).addOnSuccessListener {
        callback("Answer submitted.")
    }.addOnFailureListener {
        callback("Failed to submit answer.")
    }
}
