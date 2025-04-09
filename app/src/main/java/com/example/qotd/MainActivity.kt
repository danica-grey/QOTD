package com.example.qotd

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ExitToApp
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.qotd.ui.theme.QOTDTheme
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.launch
import kotlinx.coroutines.CoroutineScope
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.SetOptions
import java.text.SimpleDateFormat
import java.util.*

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        val sharedPreferences = getSharedPreferences("QOTD_PREFS", MODE_PRIVATE)
        val cameFromAnswerScreen = sharedPreferences.getBoolean("cameFromAnswerScreen", false)

        val currentUserId = FirebaseAuth.getInstance().currentUser?.uid ?: ""

        // If the user came from the answer screen, don't redirect to the answer screen again
        if (!cameFromAnswerScreen && currentUserId.isNotEmpty()) {
            // Check if the user has already answered for today
            checkIfAnsweredToday(currentUserId)
        }

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

        // Reset the flag after the user has entered the main screen
        sharedPreferences.edit().putBoolean("cameFromAnswerScreen", false).apply()
    }

    // Function to check if the user has already answered the QOTD today
    private fun checkIfAnsweredToday(userId: String) {
        if (userId.isNotEmpty()) {
            val db = FirebaseFirestore.getInstance()
            val userRef = db.collection("users").document(userId)

            userRef.get().addOnSuccessListener { document ->
                val answeredToday = document.getBoolean("answeredToday") ?: false
                if (answeredToday) {
                    // If already answered, go to UserAnswersActivity
                    val intent = Intent(this, UserAnswersActivity::class.java)
                    startActivity(intent)
                    finish()  // Close the current activity to prevent going back
                }
            }
        }
    }
}

@Composable
fun QuestionAnswerScreen(scope: CoroutineScope, snackbarHostState: SnackbarHostState) {
    var question by remember { mutableStateOf("Loading question...") }
    var userAnswer by remember { mutableStateOf("") }
    var message by remember { mutableStateOf("") }
    val context = LocalContext.current
    val currentUserId = FirebaseAuth.getInstance().currentUser?.uid ?: ""

    // Check if the user is signed in and conditionally show the login button
    val isUserSignedIn = currentUserId.isNotEmpty()

    // Function to fetch the daily question
    fun fetchQuestionOfTheDay() {
        val db = FirebaseFirestore.getInstance()
        val todayDate = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())
        val dailyQuestionRef = db.collection("dailyQuestions").document(todayDate)

        dailyQuestionRef.get().addOnSuccessListener { document ->
            if (document.exists()) {
                val qotd = document.getString("question") ?: "Error loading question."
                question = qotd
            } else {
                db.collection("questions").get().addOnSuccessListener { result ->
                    val questionsList = result.documents.mapNotNull { it.getString("Question") }
                    if (questionsList.isNotEmpty()) {
                        val randomQuestion = questionsList.random()
                        dailyQuestionRef.set(mapOf("question" to randomQuestion))
                        question = randomQuestion
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

    Box(modifier = Modifier.fillMaxSize()) {
        // Conditionally show the Login button based on user sign-in status
        IconButton(
            onClick = {
                if (isUserSignedIn) {
                    // Logout
                    FirebaseAuth.getInstance().signOut()
                    val intent = Intent(context, LoginActivity::class.java)
                    context.startActivity(intent)
                } else {
                    // Login
                    val intent = Intent(context, LoginActivity::class.java)
                    context.startActivity(intent)
                }
            },
            modifier = Modifier
                .align(Alignment.TopStart)
                .padding(top = 16.dp, start = 16.dp)
        ) {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.ExitToApp,
                contentDescription = if (isUserSignedIn) "Logout" else "Login",
                modifier = Modifier.graphicsLayer(
                    rotationZ = 180f // Rotate the icon 180 degrees to make it point left
                )
            )
        }

        // Main content (QOTD, answer input, etc.)
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = question,
                style = MaterialTheme.typography.headlineMedium,
                modifier = Modifier
                    .fillMaxWidth()
                    .align(Alignment.Start)
            )

            Spacer(modifier = Modifier.height(16.dp))

            OutlinedTextField(
                value = userAnswer,
                onValueChange = { userAnswer = it },
                label = { Text("Type something...") },
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(modifier = Modifier.height(32.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End
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
                                userAnswer = ""
                                message = status

                                val intent = Intent(context, UserAnswersActivity::class.java)
                                intent.putExtra("isComingFromQOTD", true)
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
            markAnsweredToday(userId)
        }
        .addOnFailureListener {
            callback("Failed to submit answer.")
        }
}

// Mark the user as having answered today
fun markAnsweredToday(userId: String) {
    val userAnsweredData = hashMapOf(
        "answeredToday" to true,
        "lastAnsweredDate" to java.time.LocalDate.now().toString()
    )

    FirebaseFirestore.getInstance()
        .collection("users")  // Assuming there's a 'users' collection
        .document(userId)     // Document ID is the user ID
        .set(userAnsweredData, SetOptions.merge())  // Merge to avoid overwriting other data
}