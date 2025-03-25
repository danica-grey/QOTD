package com.example.qotd

import android.content.Context
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
import androidx.compose.ui.unit.dp
import android.content.Intent
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.qotd.ui.theme.QOTDTheme
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.launch
import kotlinx.coroutines.CoroutineScope
import androidx.compose.foundation.shape.RoundedCornerShape

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
                        // Make this take up available space but allow scrolling
                        Box(
                            modifier = Modifier
                                .weight(1f) // Ensures the content doesn't push FakeComments() off-screen
                                .fillMaxWidth()
                        ) {
                            QuestionAnswerScreen(scope, snackbarHostState)
                        }
                        /* keeping code in case needed later but not calling
                        Greeting() // "Comments"
                        FakeComments() // Now always visible below everything else
                        */
                    }
                }
            }
        }
    }
}

// fake comment text "Comments"
@Composable
fun Greeting() {
    Text(
        text = "Comments",
        fontSize = 28.sp
    )
}

// fake comments, we can delete these later once actual comments implemented
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
    var lastSubmittedAnswer by remember { mutableStateOf("") }
    var message by remember { mutableStateOf("") }
    val context = LocalContext.current

    var seenQuestions by remember { mutableStateOf(emptyList<String>()) }  // Track seen questions

    fun fetchQuestion() {
        FirebaseFirestore.getInstance().collection("questions")
            .get()
            .addOnSuccessListener { result ->
                val questionsList = result.documents.filter { it.id !in seenQuestions }

                if (questionsList.isNotEmpty()) {
                    val randomQuestion = questionsList.random()
                    question = randomQuestion.getString("Question") ?: "No question found"

                    // Mark this question as seen
                    seenQuestions = seenQuestions + randomQuestion.id
                } else {
                    question = "No more new questions available."
                }
            }
    }

    LaunchedEffect(Unit) {
        fetchQuestion()
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // align question text to left
        Text(
            text = question,
            style = MaterialTheme.typography.headlineMedium,
            modifier = Modifier.fillMaxWidth().align(Alignment.Start) // align to L
        )

        Spacer(modifier = Modifier.height(16.dp))

        OutlinedTextField(
            value = userAnswer,
            onValueChange = { userAnswer = it },
            label = { Text("Type something...") },
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(modifier = Modifier.height(32.dp))

        // Row for aligning the Submit button to the right
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.End // Aligns the button to the right
        ) {
            // Submit Button with larger size
            Button(
                onClick = {
                    if (userAnswer.isBlank()) {
                        scope.launch {
                            snackbarHostState.showSnackbar("Answer cannot be empty.")
                        }
                    } else {
                        submitAnswer(userAnswer) { status ->
                            scope.launch {
                                snackbarHostState.showSnackbar(status)
                            }
                            lastSubmittedAnswer = userAnswer
                            userAnswer = ""
                            message = status // Update the message
                        }
                    }
                },
                modifier = Modifier
                    .height(52.dp) // Increase button height
            ) {
                Text("Submit", fontSize = 20.sp) // Increase font size
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Display the message if it's not empty
        if (message.isNotEmpty()) {
            Text(text = message, color = MaterialTheme.colorScheme.primary)
        }

        // Display last submitted answer
        if (lastSubmittedAnswer.isNotEmpty()) {
            Text(
                text = "Your answer: $lastSubmittedAnswer",
                style = MaterialTheme.typography.bodyLarge
            )
        }
    }

    // The Refresh Button stays in the bottom-right corner
    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(12.dp),
        contentAlignment = Alignment.BottomEnd // Position the button at the bottom-right
    ) {
        FilledIconButton(
            onClick = { fetchQuestion() },
            modifier = Modifier.size(80.dp), // Set the size to 80.dp
            colors = IconButtonDefaults.filledIconButtonColors(containerColor = MaterialTheme.colorScheme.primary)
        ) {
            Icon(
                imageVector = Icons.Filled.Refresh,
                contentDescription = "Refresh Question",
                modifier = Modifier.size(32.dp), // Increase size of the swirl
                tint = MaterialTheme.colorScheme.onPrimary // Makes the swirl white
            )
        }
    }

    // The "+" Button stays in the bottom-left corner
    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(12.dp),
        contentAlignment = Alignment.BottomStart // Position the button at the bottom-left
    ) {
        FloatingActionButton(
            onClick = {
                val intent = Intent(context, ReplaceQuestionActivity::class.java)
                context.startActivity(intent)
            },
            shape = RoundedCornerShape(16.dp), // Rounded square shape
            modifier = Modifier.size(80.dp), // Set the size to match the refresh button size
            containerColor = MaterialTheme.colorScheme.primary
        ) {
            Icon(
                imageVector = Icons.Filled.Add, // Plus sign icon
                contentDescription = "Add Question",
                modifier = Modifier.size(36.dp), // Increase size of the plus
                tint = MaterialTheme.colorScheme.onPrimary
            )
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