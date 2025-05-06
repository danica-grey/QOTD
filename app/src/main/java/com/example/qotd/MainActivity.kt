package com.example.qotd

import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.qotd.ui.theme.QOTDTheme
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import kotlinx.coroutines.launch
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.tasks.await
import java.text.SimpleDateFormat
import java.util.*
import androidx.compose.material.icons.filled.Group
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.List
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.tooling.preview.PreviewParameter

class MainActivity : ComponentActivity() {
    @OptIn(ExperimentalMaterial3Api::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        val sharedPreferences = getSharedPreferences("QOTD_PREFS", MODE_PRIVATE)
        val cameFromAnswerScreen = sharedPreferences.getBoolean("cameFromAnswerScreen", false)
        val currentUserId = FirebaseAuth.getInstance().currentUser?.uid ?: ""
        val prefs = getSharedPreferences("qotd_prefs", Context.MODE_PRIVATE)
        val isDarkMode = prefs.getBoolean("dark_mode", false)

        setContent {
            QOTDTheme(darkTheme = isDarkMode) {
                val snackbarHostState = remember { SnackbarHostState() }
                val scope = rememberCoroutineScope()
                val context = LocalContext.current
                var answeredToday by remember { mutableStateOf(false) }

                Surface(modifier = Modifier.fillMaxSize()) {
                    SettingsScreen()
                }

                LaunchedEffect(currentUserId) {
                    if (!cameFromAnswerScreen && currentUserId.isNotEmpty()) {
                        val db = FirebaseFirestore.getInstance()
                        val userRef = db.collection("users").document(currentUserId)

                        try {
                            val document = userRef.get().await()
                            val lastAnsweredDate = document.getString("lastAnsweredDate")
                            val today = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())

                            if (lastAnsweredDate != today) {
                                userRef.set(
                                    mapOf("answeredToday" to false),
                                    SetOptions.merge()
                                ).await()
                                answeredToday = false
                            } else {
                                answeredToday = document.getBoolean("answeredToday") ?: false
                            }
                        } catch (e: Exception) {
                            scope.launch {
                                snackbarHostState.showSnackbar("Unable to check your answer status. Please try again later.")
                            }

                            answeredToday = false
                        }
                    }
                }

                Scaffold(
                    modifier = Modifier.fillMaxSize(),
                    snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
                    topBar = {
                    },
                    bottomBar = {
                        MainBottomNavigationBar()
                    }
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
                                .fillMaxWidth()
                                .padding(top = 48.dp)
                        ) {
                            Text(
                                text = "QOTD",
                                style = MaterialTheme.typography.headlineLarge.copy(
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 42.sp,
                                    color = MaterialTheme.colorScheme.primary
                                ),
                                modifier = Modifier.align(Alignment.Center)
                            )
                        }

                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 16.dp)
                        ) {
                            val dateFormatter = SimpleDateFormat("EEEE, MMMM d, yyyy", Locale.getDefault())
                            val formattedDate = dateFormatter.format(Date())
                            val textColor = if (isDarkMode) Color.White else MaterialTheme.colorScheme.secondary

                            Text(
                                text = formattedDate,
                                style = MaterialTheme.typography.bodyLarge.copy(
                                    fontSize = 20.sp,
                                    color = textColor
                                ),
                                modifier = Modifier.align(Alignment.Center)
                            )
                        }

                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxWidth()
                        ) {
                            QuestionAnswerScreen(
                                scope = scope,
                                snackbarHostState = snackbarHostState,
                                answeredToday = answeredToday,
                                setAnsweredToday = { answeredToday = it }
                            )
                        }
                    }
                }
            }
        }

        sharedPreferences.edit().putBoolean("cameFromAnswerScreen", false).apply()
    }


    override fun onResume() {
        super.onResume()

        val currentUserId = FirebaseAuth.getInstance().currentUser?.uid ?: ""
        if (currentUserId.isNotEmpty()) {
            val db = FirebaseFirestore.getInstance()
            val userRef = db.collection("users").document(currentUserId)

            userRef.get().addOnSuccessListener { document ->
                val lastAnsweredDate = document.getString("lastAnsweredDate")
                val today = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())

                if (lastAnsweredDate != today) {
                    userRef.set(
                        mapOf("answeredToday" to false),
                        SetOptions.merge()
                    )
                }
            }
        }
    }
}

@Composable
fun QuestionAnswerScreen(
    scope: CoroutineScope,
    snackbarHostState: SnackbarHostState,
    answeredToday: Boolean,
    setAnsweredToday: (Boolean) -> Unit
) {
    var question by remember { mutableStateOf("Loading question...") }
    var userAnswer by remember { mutableStateOf("") }
    val context = LocalContext.current
    val currentUserId = FirebaseAuth.getInstance().currentUser?.uid ?: ""

    suspend fun fetchQuestionOfTheDay() {
        val db = FirebaseFirestore.getInstance()
        val todayDate = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())
        val dailyQuestionRef = db.collection("dailyQuestions").document(todayDate)
        try {
            val todaySnap = dailyQuestionRef.get().await()
            if (todaySnap.exists()) {
                question = todaySnap.getString("question") ?: "Error loading question."
                return
            }

            val allQs = db.collection("questions")
                .get().await()
                .documents
                .mapNotNull { it.getString("Question") }

            val usedQs = db.collection("dailyQuestions")
                .get().await()
                .documents
                .mapNotNull { it.getString("question") }
                .toSet()

            val unused = allQs.filterNot { it in usedQs }

            val pool = if (unused.isEmpty()) allQs else unused

            val pick = pool.random()
            dailyQuestionRef.set(mapOf("question" to pick)).await()
            question = pick

        } catch (e: Exception) {
            question = "Failed to load question."
        }
    }

    LaunchedEffect(Unit) {
        fetchQuestionOfTheDay()
    }

    Box(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp)
                .offset(y = 120.dp),
            verticalArrangement = Arrangement.Top,
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
                label = {
                    Text(
                        if (answeredToday) "Answer Submitted!" else "Type something..."
                    )
                },
                modifier = Modifier.fillMaxWidth(),
                enabled = !answeredToday
            )

            Spacer(modifier = Modifier.height(32.dp))

            if (!answeredToday) {
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
                                    if (status == "Answer submitted.") {
                                        userAnswer = ""
                                        setAnsweredToday(true)
                                        val intent = Intent(context, UserAnswersActivity::class.java)
                                        intent.putExtra("isComingFromQOTD", true)
                                        context.startActivity(intent)
                                    }
                                }
                            }
                        },
                        modifier = Modifier.height(52.dp)
                    ) {
                        Text("Submit", fontSize = 20.sp, color = Color.White)
                    }
                }
            } else {
                Button(
                    onClick = {
                        val intent = Intent(context, UserAnswersActivity::class.java)
                        intent.putExtra("isComingFromQOTD", true)
                        context.startActivity(intent)
                    },
                    modifier = Modifier
                        .align(Alignment.End)
                        .height(52.dp)
                ) {
                    Text("View Answers", fontSize = 20.sp, color = Color.White)
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
            updateStreakAndAchievements(userId)
        }
        .addOnFailureListener {
            callback("Failed to submit answer.")
        }
}

fun markAnsweredToday(userId: String) {
    val userAnsweredData = hashMapOf(
        "answeredToday" to true,
        "lastAnsweredDate" to java.time.LocalDate.now().toString()
    )

    FirebaseFirestore.getInstance()
        .collection("users")
        .document(userId)
        .set(userAnsweredData, SetOptions.merge())
}

fun updateStreakAndAchievements(userId: String) {
    val userRef = FirebaseFirestore.getInstance().collection("users").document(userId)

    userRef.get().addOnSuccessListener { doc ->
        val data = doc.data ?: return@addOnSuccessListener

        val oldStreak = (data["streakCount"] as? Number)?.toInt() ?: 0
        val newStreak = oldStreak + 1

        val achievements = (data["achievements"] as? Map<*, *>)?.mapNotNull { (k, v) ->
            val key = k as? String
            val value = v as? Boolean
            if (key != null && value != null) key to value else null
        }?.toMap() ?: emptyMap()

        val newAchievements = achievements.toMutableMap()

        if (newStreak >= 3) newAchievements["streak3"] = true
        if (newStreak >= 7) newAchievements["streak7"] = true
        if (newStreak >= 30) newAchievements["streak30"] = true
        if (newStreak >= 100) newAchievements["streak100"] = true

        val updateData = mapOf(
            "streakCount" to newStreak,
            "achievements" to newAchievements
        )

        userRef.set(updateData, SetOptions.merge())
    }
}

@Composable
fun MainBottomNavigationBar() {
    val context = LocalContext.current

    BottomAppBar(
        modifier = Modifier
            .fillMaxWidth()
            .height(88.dp),
        containerColor = MaterialTheme.colorScheme.primary
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceAround,
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = {
                // (we're already here, do nothing)
            }) {
                Icon(
                    Icons.Default.Home,
                    contentDescription = "Home",
                    modifier = Modifier.size(32.dp),
                    tint = Color.White
                )
            }

            IconButton(onClick = {
                context.startActivity(Intent(context, AddFriendActivity::class.java))
            }) {
                Icon(
                    Icons.Default.Group,
                    contentDescription = "Friends",
                    modifier = Modifier.size(32.dp),
                    tint = Color.White
                )
            }

            IconButton(onClick = {
                context.startActivity(Intent(context, UserAnswersActivity::class.java))
            }) {
                Icon(
                    Icons.Default.List,
                    contentDescription = "Answers",
                    modifier = Modifier.size(32.dp),
                    tint = Color.White
                )
            }

            IconButton(onClick = {
                context.startActivity(Intent(context, SettingsActivity::class.java))
            }) {
                Icon(
                    Icons.Default.Settings,
                    contentDescription = "Settings",
                    modifier = Modifier.size(32.dp),
                    tint = Color.White
                )
            }
        }
    }
}