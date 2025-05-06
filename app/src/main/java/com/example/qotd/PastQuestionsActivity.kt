package com.example.qotd

import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.qotd.ui.theme.QOTDTheme
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Settings
import kotlinx.coroutines.tasks.await

class PastQuestionsActivity : ComponentActivity() {

    @OptIn(ExperimentalMaterial3Api::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val prefs = getSharedPreferences("qotd_prefs", Context.MODE_PRIVATE)
        val isDarkMode = prefs.getBoolean("dark_mode", false)

        setContent {
            QOTDTheme(darkTheme = isDarkMode) {
                Surface(modifier = Modifier.fillMaxSize()) {
                    SettingsScreen()
                }

                Scaffold(
                    topBar = {
                        TopAppBar(
                            title = {
                                Text(
                                    text = "Past Questions",
                                    style = MaterialTheme.typography.titleLarge,
                                    modifier = Modifier.padding(start = 8.dp)
                                )
                            },
                            navigationIcon = {
                                // Modified back button logic to let the system handle the back stack
                                IconButton(onClick = {
                                    onBackPressed() // This will now properly return you to the last activity
                                }) {
                                    Icon(imageVector = Icons.Filled.ArrowBack, contentDescription = "Back")
                                }
                            },
                        )
                    }
                ) { innerPadding ->
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(innerPadding)
                    ) {
                        PastQuestionsScreen()
                    }
                }
            }
        }
    }

    // We removed the finish() here to let Android handle the back stack automatically
    override fun onBackPressed() {
        super.onBackPressed() // Android will now manage the back stack and return to the previous activity
    }
}

@Composable
fun SettingsButton() {
    val context = LocalContext.current

    IconButton(onClick = {
        val intent = Intent(context, SettingsActivity::class.java)
        context.startActivity(intent)
    }) {
        Icon(
            imageVector = Icons.Filled.Settings,
            contentDescription = "Settings",
            modifier = Modifier.size(32.dp)
        )
    }
}

@Composable
fun PastQuestionsScreen() {
    val firestore = FirebaseFirestore.getInstance()
    var pastQuestions by remember { mutableStateOf<List<PastQuestion>>(emptyList()) }

    // Fetch past questions from Firestore
    LaunchedEffect(Unit) {
        firestore.collection("dailyQuestions")
            .get()
            .addOnSuccessListener { snapshot ->
                pastQuestions = snapshot.documents.mapNotNull { document ->
                    val date = document.id
                    val question = document.getString("question") ?: ""
                    PastQuestion(date, question)
                }.sortedByDescending { it.date }
            }
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        items(pastQuestions) { pastQuestion ->
            PastQuestionItem(pastQuestion)
        }
    }
}

@Composable
fun PastQuestionItem(pastQuestion: PastQuestion) {
    val firestore = FirebaseFirestore.getInstance()
    val context = LocalContext.current

    val isToday = pastQuestion.date == LocalDate.now().toString()

    val formattedDate = try {
        val date = LocalDate.parse(pastQuestion.date)
        date.format(DateTimeFormatter.ofPattern("MMMM d, yyyy"))
    } catch (e: Exception) {
        "Invalid Date"
    }

    var answersCount by remember { mutableStateOf(0) }

    LaunchedEffect(pastQuestion) {
        val currentUserId = FirebaseAuth.getInstance().currentUser?.uid ?: return@LaunchedEffect

        val currentUserDoc = firestore.collection("users").document(currentUserId).get().await()
        val onlyShowFriendsAnswers = currentUserDoc.getBoolean("onlyShowFriendsAnswers") ?: false
        val currentUserFriends = currentUserDoc.get("friends") as? List<String> ?: emptyList()

        val answersSnapshot = firestore.collection("dailyAnswer")
            .whereEqualTo("questionDate", pastQuestion.date)
            .get()
            .await()

        var visibleCount = 0
        var currentUserAnswered = false

        for (doc in answersSnapshot.documents) {
            val userId = doc.getString("userId") ?: continue

            if (userId.isBlank()) continue

            val userDocRef = firestore.collection("users").document(userId)
            val userDoc = userDocRef.get().await()
            if (!userDoc.exists()) continue

            val privacy = userDoc.getString("privacy") ?: "Public"
            val isFriend = currentUserFriends.contains(userId)
            val isSelf = userId == currentUserId

            val shouldInclude = when {
                onlyShowFriendsAnswers && !(isFriend || isSelf) -> false
                privacy == "Public" -> true
                privacy == "Anonymous" -> true
                privacy == "Private" -> isFriend || isSelf
                else -> false
            }

            if (isSelf) {
                currentUserAnswered = true
            }

            if (shouldInclude) visibleCount++
        }

        answersCount = if (currentUserAnswered) {
            visibleCount
        } else {
            visibleCount
        }
    }

    if (answersCount > 0) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(8.dp)
                .clickable {
                    val intent = Intent(context, UserAnswersActivity::class.java)
                    intent.putExtra("questionDate", pastQuestion.date)
                    intent.putExtra("questionText", pastQuestion.question)
                    intent.putExtra("sourceActivity", "PastQuestionsActivity")
                    context.startActivity(intent)
                },
            elevation = CardDefaults.cardElevation(4.dp)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    text = formattedDate + if (isToday) " (Today)" else "",
                    style = MaterialTheme.typography.bodyLarge.copy(
                        fontWeight = if (isToday) FontWeight.Bold else FontWeight.Normal,
                        color = if (isToday) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onBackground
                    )
                )

                Spacer(modifier = Modifier.height(8.dp))

                Text(
                    text = pastQuestion.question,
                    fontSize = MaterialTheme.typography.bodyLarge.fontSize * 1.2f,
                    fontWeight = if (isToday) FontWeight.SemiBold else FontWeight.Normal
                )

                Spacer(modifier = Modifier.height(8.dp))

                Text(
                    text = "$answersCount Answers",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.primary
                )
            }
        }
    }
}

data class PastQuestion(val date: String, val question: String)