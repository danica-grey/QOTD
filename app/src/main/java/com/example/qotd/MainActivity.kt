package com.example.qotd

import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.qotd.ui.theme.QOTDTheme
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.launch
import kotlinx.coroutines.CoroutineScope

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        setContent {
            QOTDTheme {
                val snackbarHostState = remember { SnackbarHostState() }
                val scope = rememberCoroutineScope()

                Scaffold(
                    modifier = Modifier.fillMaxSize(),
                    snackbarHost = { SnackbarHost(hostState = snackbarHostState) }
                ) { innerPadding ->
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(innerPadding),
                        verticalArrangement = Arrangement.Top,
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Greeting()
                        FakeComments()
                        Spacer(modifier = Modifier.weight(1f))
                        QuestionAnswerScreen(scope, snackbarHostState)
                    }
                }
            }
        }
    }
}

@Composable
fun Greeting() {
    Text(
        text = "What's goin' on?",
        fontSize = 32.sp
    )
}

@Composable
fun FakeComments() {
    var checked by remember { mutableStateOf(false) }
    val imageId = if (checked) R.drawable.lmao else R.drawable.white

    Column(
        modifier = Modifier.padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Switch(
            checked = checked,
            onCheckedChange = { checked = it }
        )

        Image(
            painter = painterResource(imageId),
            modifier = Modifier.fillMaxWidth(),
            contentDescription = null
        )
    }
}

@Composable
fun QuestionAnswerScreen(scope: CoroutineScope, snackbarHostState: SnackbarHostState) {
    var question by remember { mutableStateOf("Loading question...") }
    var userAnswer by remember { mutableStateOf("") }
    var lastSubmittedAnswer by remember { mutableStateOf("") } // Store last submitted answer
    val context = LocalContext.current

    LaunchedEffect(Unit) {
        FirebaseFirestore.getInstance().collection("questions")
            .document("Question1")
            .get()
            .addOnSuccessListener { document ->
                question = document.getString("Question") ?: "No question found"
            }
            .addOnFailureListener {
                question = "Error loading question"
            }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
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
            if (userAnswer.isBlank()) {
                scope.launch {
                    snackbarHostState.showSnackbar("Answer cannot be empty.")
                }
            } else {
                submitAnswer(userAnswer) { status ->
                    scope.launch {
                        snackbarHostState.showSnackbar(status)
                    }
                    lastSubmittedAnswer = userAnswer // Store last submitted answer
                    userAnswer = "" // Clear input after submission
                }
            }
        }) {
            Text("Submit")
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Display last submitted answer
        if (lastSubmittedAnswer.isNotEmpty()) {
            Text(
                text = "Your answer: $lastSubmittedAnswer",
                style = MaterialTheme.typography.bodyLarge
            )
        }
    }
}

private fun submitAnswer(answer: String, callback: (String) -> Unit) {
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