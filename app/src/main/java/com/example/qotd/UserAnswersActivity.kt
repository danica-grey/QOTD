package com.example.qotd

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.addCallback
import androidx.activity.compose.setContent
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ExitToApp
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.qotd.ui.theme.QOTDTheme
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

class UserAnswersActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val dateStringFromIntent = intent.getStringExtra("questionDate")
        val questionDate = dateStringFromIntent?.let { LocalDate.parse(it) } ?: LocalDate.now()
        val displayDate = questionDate.format(DateTimeFormatter.ofPattern("MMMM d, yyyy"))

        val isComingFromQOTD = intent.getBooleanExtra("isComingFromQOTD", false)
        if (isComingFromQOTD) {
            onBackPressedDispatcher.addCallback(this) {}
        }

        setContent {
            QOTDTheme {
                Scaffold(
                    modifier = Modifier.fillMaxSize(),
                    topBar = {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            // Left side (Date + Past Questions button)
                            Row(
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                IconButton(
                                    onClick = {
                                        val intent = Intent(this@UserAnswersActivity, PastQuestionsActivity::class.java)
                                        startActivity(intent)
                                    },
                                    modifier = Modifier.size(48.dp)
                                ) {
                                    Image(
                                        painter = painterResource(id = R.drawable.past_icon),
                                        contentDescription = "Past QOTDs",
                                        modifier = Modifier.size(28.dp)
                                    )
                                }

                                Spacer(modifier = Modifier.width(8.dp))

                                Text(
                                    text = displayDate,
                                    style = MaterialTheme.typography.titleLarge
                                )
                            }

                            // Right side: Reusable Logout Button
                            LogoutButton(onLogout = { logoutAndNavigate() })
                        }
                    }
                ) { innerPadding ->
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(innerPadding),
                        verticalArrangement = Arrangement.Top
                    ) {
                        UserAnswersScreen(questionDate, displayDate)
                    }
                }
            }
        }
    }

    private fun logoutAndNavigate() {
        FirebaseAuth.getInstance().signOut()
        val intent = Intent(this, LoginActivity::class.java)
        startActivity(intent)
        finish()
    }
}

// Reusable LogoutButton composable
@Composable
fun LogoutButton(
    onLogout: () -> Unit,
    modifier: Modifier = Modifier
) {
    IconButton(
        onClick = onLogout,
        modifier = modifier
    ) {
        Icon(
            imageVector = Icons.AutoMirrored.Filled.ExitToApp,
            contentDescription = "Logout",
            tint = MaterialTheme.colorScheme.error,
            modifier = Modifier.size(28.dp)
        )
    }
}



@Composable
fun UserAnswersScreen(questionDate: LocalDate, displayDate: String) {
    val firestore = FirebaseFirestore.getInstance()
    var answers by remember { mutableStateOf<List<Pair<String, Map<String, Any>>>>(emptyList()) }
    var questionOfTheDay by remember { mutableStateOf("Loading question...") }
    val currentUserId = FirebaseAuth.getInstance().currentUser?.uid ?: "UnknownUser"

    // Fetch the question of the day
    LaunchedEffect(questionDate) {
        firestore.collection("dailyQuestions")
            .document(questionDate.toString()) // Use the internal date format for Firebase
            .get()
            .addOnSuccessListener { document ->
                questionOfTheDay = document.getString("question") ?: "No question available for today."
            }

        firestore.collection("dailyAnswer")
            .whereEqualTo("questionDate", questionDate.toString()) // Use the internal date format for Firebase
            .addSnapshotListener { snapshot, _ ->
                snapshot?.let { it ->
                    answers = it.documents.mapNotNull { doc ->
                        val data = doc.data
                        if (data != null) Pair(doc.id, data) else null
                    }.let { list ->
                        // Separate the current user's answer from others and sort the rest by timestamp
                        val (userAnswers, otherAnswers) = list.partition { it.second["userId"] == currentUserId }

                        val sortedOthers = otherAnswers.sortedByDescending {
                            val timeStr = it.second["timestamp"] as? String
                            try {
                                LocalDateTime.parse(timeStr, DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm"))
                            } catch (e: Exception) {
                                LocalDateTime.MIN
                            }
                        }

                        // Combine the user's answer first, followed by the sorted other answers
                        userAnswers + sortedOthers
                    }
                }
            }
    }

    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        Text(
            text = buildAnnotatedString {
                // QOTD: with blue and bold style
                withStyle(style = SpanStyle(fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary, fontSize = MaterialTheme.typography.headlineLarge.fontSize)) {
                    append("QOTD: ")
                }
                // The question of the day in its regular style
                withStyle(style = MaterialTheme.typography.headlineMedium.toSpanStyle()) {
                    append(questionOfTheDay)
                }
            },
            style = MaterialTheme.typography.headlineMedium.copy(lineHeight = 40.sp)  // line spacing
        )



        Spacer(modifier = Modifier.height(16.dp))

        // Display the answers
        LazyColumn {
            items(answers.size) { index ->
                val (answerId, answer) = answers[index]
                val answerText = answer["answer"] as? String ?: ""
                val likesList = (answer["likes"] as? List<*>)?.mapNotNull { it as? String } ?: emptyList()
                val commentsList = (answer["comments"] as? List<*>)?.mapNotNull {
                    (it as? Map<*, *>)?.mapKeys { entry -> entry.key.toString() }?.mapValues { entry -> entry.value }
                } ?: emptyList()
                val isLiked = likesList.contains(currentUserId)
                var showComments by remember { mutableStateOf(false) }
                var newComment by remember { mutableStateOf("") }

                // Calculate the comment button text
                val replyText = when (commentsList.size) {
                    0 -> "Reply"
                    1 -> "1 Reply"
                    else -> "${commentsList.size} Replies"
                }

                Card(
                    modifier = Modifier.fillMaxWidth().padding(8.dp),
                    elevation = CardDefaults.cardElevation(4.dp)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Spacer(modifier = Modifier.height(4.dp))

                        // Display "Your Answer:" in bold and the answer text in normal style
                        Column(modifier = Modifier.fillMaxWidth()) {
                            // "Your Answer:" text
                            if (answer["userId"] == currentUserId) {
                                Text(
                                    text = "Your Answer:",
                                    style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Bold)
                                )
                            }

                            // The actual answer text
                            Text(
                                text = answerText,
                                style = MaterialTheme.typography.bodyLarge,
                                modifier = Modifier.padding(top = 5.dp) // Add some space between the label and the answer
                            )
                        }

                        Spacer(modifier = Modifier.height(8.dp))

                        Row(verticalAlignment = Alignment.CenterVertically) {
                            // Heart icon and like count
                            IconButton(onClick = { likeAnswer(answerId, currentUserId) }) {
                                Icon(
                                    imageVector = if (isLiked) Icons.Filled.Favorite else Icons.Outlined.FavoriteBorder,
                                    contentDescription = "Like",
                                    tint = MaterialTheme.colorScheme.primary // Set color to blue
                                )
                            }
                            Text(
                                "${likesList.size} Likes",
                                color = MaterialTheme.colorScheme.primary, // Set the likes text to blue
                                style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Bold)
                            )

                            Spacer(modifier = Modifier.width(16.dp))

                            // Move the dropdown icon to the left of the "Reply" text
                            if (commentsList.isNotEmpty()) {
                                Icon(
                                    imageVector = Icons.Default.ArrowDropDown, // Dropdown symbol
                                    contentDescription = "Dropdown",
                                    tint = MaterialTheme.colorScheme.primary
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                            }

                            // Display the "Replies" text or "Reply" based on comment count
                            Text(
                                text = replyText,
                                color = MaterialTheme.colorScheme.primary, // Set reply text to blue
                                modifier = Modifier
                                    .clickable { showComments = !showComments }
                                    .padding(4.dp),
                                style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Bold)
                            )
                        }

                        if (showComments) {
                            Spacer(modifier = Modifier.height(8.dp))
                            Text("Comments:", style = MaterialTheme.typography.bodyLarge)

                            commentsList.forEach { comment ->
                                val commentUser = comment["userId"]?.toString() ?: "User"
                                val commentText = comment["comment"]?.toString() ?: ""
                                Text(text = "$commentUser: $commentText")
                            }

                            Spacer(modifier = Modifier.height(8.dp))

                            OutlinedTextField(
                                value = newComment,
                                onValueChange = { newComment = it },
                                label = { Text("Add a comment") },
                                modifier = Modifier.fillMaxWidth()
                            )

                            Button(
                                onClick = {
                                    if (newComment.isNotBlank()) {
                                        addComment(answerId, newComment)
                                        newComment = ""
                                    }
                                },
                                modifier = Modifier.padding(top = 8.dp)
                            ) {
                                Text("Reply")
                            }
                        }
                    }
                }
            }
        }
    }
}

fun likeAnswer(answerId: String, userId: String) {
    val firestore = FirebaseFirestore.getInstance()
    val answerRef = firestore.collection("dailyAnswer").document(answerId)

    firestore.runTransaction { transaction ->
        val snapshot = transaction.get(answerRef)
        val likesList = (snapshot.get("likes") as? List<*>)?.mapNotNull { it as? String }?.toMutableList() ?: mutableListOf()

        if (!likesList.contains(userId)) {
            likesList.add(userId)
            transaction.update(answerRef, "likes", likesList)
        } else {
            likesList.remove(userId)
            transaction.update(answerRef, "likes", likesList)
        }
    }
}

fun addComment(answerId: String, commentText: String) {
    val firestore = FirebaseFirestore.getInstance()
    val answerRef = firestore.collection("dailyAnswer").document(answerId)
    val formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")
    val currentTime = LocalDateTime.now().format(formatter)
    val currentUserId = FirebaseAuth.getInstance().currentUser?.uid ?: "UnknownUser"

    val newComment = hashMapOf(
        "userId" to currentUserId,
        "comment" to commentText,
        "timestamp" to currentTime
    )

    answerRef.update("comments", FieldValue.arrayUnion(newComment))
}
