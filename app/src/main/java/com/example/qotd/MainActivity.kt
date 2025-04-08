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

        val currentUserId = FirebaseAuth.getInstance().currentUser?.uid ?: ""

        // Check if the user has already answered for today
        //checkIfAnsweredToday(currentUserId)
        // TODO:
        // make it so if you already answered,
        // answer option is greyed out
        // "take me to answers" button?
        // or take to answers by default, but
        // want to still get to MainActivity ability for nav

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
                        /*IconButton(onClick = { logoutAndNavigate() }) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ExitToApp,
                                contentDescription = "Logout"
                            )
                        }*/
                    }
                }
            }
        }
    }
/* in theory this logout button should work but back button is a bitch
    // Function to log out and navigate to the login screen
    private fun logoutAndNavigate() {
        // Sign out the user
        FirebaseAuth.getInstance().signOut()

        // Navigate to the login screen
        val intent = Intent(this, LoginActivity::class.java)
        startActivity(intent)
        finish() // Close the current activity to prevent returning after logout
    }*/

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

    // Function to fetch the daily question
    fun fetchQuestionOfTheDay() {
        val db = FirebaseFirestore.getInstance()
        val todayDate = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())
        val dailyQuestionRef = db.collection("dailyQuestions").document(todayDate)

        // Tries to get today's question from Firestore
        dailyQuestionRef.get().addOnSuccessListener { document ->
            // If it exists, show it
            if (document.exists()) {
                val qotd = document.getString("question") ?: "Error loading question."
                question = qotd
            // If not:
            } else {
                db.collection("questions").get().addOnSuccessListener { result ->
                    val questionsList = result.documents.mapNotNull { it.getString("Question") }
                    if (questionsList.isNotEmpty()) {
                        // Pick a random question and save it as today's question
                        val randomQuestion = questionsList.random()
                        dailyQuestionRef.set(mapOf("question" to randomQuestion))

                        // then display it
                        question = randomQuestion
                    } else {
                        question = "No questions available."
                    }
                }
            }
        // If the Firestore read fails (network issue, permission issue, etc.):
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
                .align(Alignment.TopStart)
                .padding(top = 16.dp, start = 16.dp)
        ) {
            Text("Login")
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

                                // After submitting the answer, go to UserAnswersActivity
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