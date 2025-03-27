package com.example.qotd

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.ui.unit.sp
import com.example.qotd.ui.theme.QOTDTheme
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.launch
import kotlinx.coroutines.CoroutineScope
import androidx.compose.foundation.shape.RoundedCornerShape
import com.google.firebase.auth.FirebaseAuth
import java.text.SimpleDateFormat
import java.util.*

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
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxWidth()
                        ) {
                            QuestionAnswerScreen(scope, snackbarHostState)
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun QuestionAnswerScreen(scope: CoroutineScope, snackbarHostState: SnackbarHostState) {
    var question by remember { mutableStateOf("Loading question...") }
    var userAnswer by remember { mutableStateOf("") }
    var lastSubmittedAnswer by remember { mutableStateOf("") }
    var message by remember { mutableStateOf("") }
    val context = LocalContext.current
    val currentUserId = FirebaseAuth.getInstance().currentUser?.uid ?: ""

    var seenQuestions by remember { mutableStateOf(emptyList<String>()) }

    // Function to fetch the daily question
    fun fetchQuestionOfTheDay() {
        val db = FirebaseFirestore.getInstance()
        val todayDate = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())
        val dailyQuestionRef = db.collection("dailyQuestions").document(todayDate)

        dailyQuestionRef.get().addOnSuccessListener { document ->
            if (document.exists()) {
                val qotd = document.getString("question") ?: "Error loading question."
                question = qotd
                seenQuestions = seenQuestions + qotd
            } else {
                db.collection("questions").get().addOnSuccessListener { result ->
                    val questionsList = result.documents.mapNotNull { it.getString("Question") }
                    if (questionsList.isNotEmpty()) {
                        val randomQuestion = questionsList.random()
                        dailyQuestionRef.set(mapOf("question" to randomQuestion))

                        question = randomQuestion
                        seenQuestions = seenQuestions + randomQuestion
                    } else {
                        question = "No questions available."
                    }
                }
            }
        }.addOnFailureListener {
            question = "Failed to load question."
        }
    }

    // Fetch question when the screen is first composed
    LaunchedEffect(Unit) {
        fetchQuestionOfTheDay()
    }

    Box(
        modifier = Modifier.fillMaxSize()
    ) {
        // Login Button at the top-left corner
        Button(
            onClick = {
                val intent = Intent(context, LoginActivity::class.java)
                context.startActivity(intent)
            },
            modifier = Modifier
                .align(Alignment.TopStart) // Align to the top-left
                .padding(top = 16.dp, start = 16.dp) // Add some padding to the top and left
        ) {
            Text("Login")
        }

        // Main content (QOTD, answer input, etc.)
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp),
            verticalArrangement = Arrangement.Center,  // Vertically center the content
            horizontalAlignment = Alignment.CenterHorizontally  // Horizontally center the content
        ) {
            // QOTD Text
            Text(
                text = question,
                style = MaterialTheme.typography.headlineMedium,
                modifier = Modifier.fillMaxWidth().align(Alignment.Start)
            )

            Spacer(modifier = Modifier.height(16.dp))

            // Text input box for answer
            OutlinedTextField(
                value = userAnswer,
                onValueChange = { userAnswer = it },
                label = { Text("Type something...") },
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(modifier = Modifier.height(32.dp))

            // Submit Button aligned to the right
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End  // Align the submit button to the right
            ) {
                Button(
                    onClick = {
                        if (userAnswer.isBlank()) {
                            scope.launch {
                                snackbarHostState.showSnackbar("Answer cannot be empty.")
                            }
                        } else {
                            submitAnswer(currentUserId, userAnswer) { status ->
                                scope.launch {
                                    snackbarHostState.showSnackbar(status)
                                }
                                lastSubmittedAnswer = userAnswer
                                userAnswer = ""
                                message = status

                                // Navigate to the answers screen after submission
                                val intent = Intent(context, UserAnswersActivity::class.java)
                                context.startActivity(intent)
                            }
                        }
                    },
                    modifier = Modifier.height(52.dp)
                ) {
                    Text("Submit", fontSize = 20.sp)
                }
            }
        }

        // Fetch random question for refresh button
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

        // Refresh button
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(12.dp),
            contentAlignment = Alignment.BottomEnd
        ) {
            FilledIconButton(
                onClick = { fetchQuestion() },
                modifier = Modifier.size(80.dp),
                colors = IconButtonDefaults.filledIconButtonColors(containerColor = MaterialTheme.colorScheme.primary)
            ) {
                Icon(
                    imageVector = Icons.Filled.Refresh,
                    contentDescription = "Refresh Question",
                    modifier = Modifier.size(32.dp),
                    tint = MaterialTheme.colorScheme.onPrimary
                )
            }
        }

        // Create new question button
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(12.dp),
            contentAlignment = Alignment.BottomStart
        ) {
            FloatingActionButton(
                onClick = {
                    val intent = Intent(context, ReplaceQuestionActivity::class.java)
                    context.startActivity(intent)
                },
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier.size(80.dp),
                containerColor = MaterialTheme.colorScheme.primary
            ) {
                Icon(
                    imageVector = Icons.Filled.Add,
                    contentDescription = "Add Question",
                    modifier = Modifier.size(36.dp),
                    tint = MaterialTheme.colorScheme.onPrimary
                )
            }
        }
    }
}


fun submitAnswer(userId: String, answer: String, callback: (String) -> Unit) {
    if (answer.isBlank()) {
        callback("Answer cannot be empty.")
        return
    }
    val answerData = hashMapOf(
        "userId" to userId,
        "displayName" to userId,
        "answer" to answer,
        "likes" to emptyList<String>(),
        "comments" to emptyList<Map<String, Any>>(),
        "questionDate" to java.time.LocalDate.now().toString()
    )
    FirebaseFirestore.getInstance().collection("dailyAnswer").add(answerData)
        .addOnSuccessListener {
            callback("Answer submitted.")
        }
        .addOnFailureListener {
            callback("Failed to submit answer.")
        }
}
