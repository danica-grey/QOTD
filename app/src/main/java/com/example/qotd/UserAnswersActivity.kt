package com.example.qotd

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.painterResource
import com.example.qotd.ui.theme.QOTDTheme
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import androidx.compose.foundation.Image
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Group
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.List
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import androidx.compose.material.icons.filled.Settings

class UserAnswersActivity : ComponentActivity() {
    @OptIn(ExperimentalMaterial3Api::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val extras = intent.extras
        val questionDate = extras?.getString("questionDate")?.let { LocalDate.parse(it) } ?: LocalDate.now()
        val displayDate = questionDate.format(DateTimeFormatter.ofPattern("MMMM d, yyyy"))

        setContent {
            val context = LocalContext.current

            QOTDTheme {
                Scaffold(
                    topBar = {
                        TopAppBar(
                            title = {
                                Text(
                                    text = displayDate,
                                    style = MaterialTheme.typography.titleLarge,
                                    modifier = Modifier.padding(start = 8.dp),
                                    maxLines = 1
                                )
                            },
                            navigationIcon = {
                                IconButton(onClick = {
                                    val intent = Intent(context, MainActivity::class.java)
                                    context.startActivity(intent)
                                    if (context is ComponentActivity) context.finish()
                                }) {
                                    Icon(
                                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                        contentDescription = "Back"
                                    )
                                }
                            },
                            actions = {
                                IconButton(
                                    onClick = {
                                        val intent = Intent(context, PastQuestionsActivity::class.java)
                                        context.startActivity(intent)
                                    },
                                    modifier = Modifier.size(48.dp)
                                ) {
                                    Image(
                                        painter = painterResource(id = R.drawable.past_icon),
                                        contentDescription = "Past QOTDs",
                                        modifier = Modifier
                                            .size(28.dp)
                                            .graphicsLayer { rotationY = 180f }
                                            .offset(x = 4.dp)
                                    )
                                }
                            },
                            colors = TopAppBarDefaults.topAppBarColors()
                        )
                    },
                    bottomBar = { AnswerBottomNavigationBar() },
                    modifier = Modifier.fillMaxSize()
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

    override fun onBackPressed() {
        super.onBackPressed()
        val intent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        startActivity(intent)
        finish()
    }
}

@Composable
fun UserAnswersScreen(questionDate: LocalDate, displayDate: String) {
    val firestore = FirebaseFirestore.getInstance()
    var answers by remember { mutableStateOf<List<Pair<String, Map<String, Any>>>>(emptyList()) }
    var questionOfTheDay by remember { mutableStateOf("Loading question...") }
    val currentUserId = FirebaseAuth.getInstance().currentUser?.uid ?: "UnknownUser"
    var userNames by remember { mutableStateOf(mapOf<String, String>()) }

    fun fetchUserName(userId: String, onSuccess: (String) -> Unit) {
        if (userId == "UnknownUser") {
            onSuccess("Unknown User")
            return
        }
        if (userNames.containsKey(userId)) {
            onSuccess(userNames[userId]!!)
            return
        }
        firestore.collection("users").document(userId).get()
            .addOnSuccessListener { document ->
                val username = document.getString("username") ?: "User $userId"
                userNames = userNames + (userId to username)
                onSuccess(username)
            }
            .addOnFailureListener {
                onSuccess("User $userId")
            }
    }

    LaunchedEffect(questionDate) {
        firestore.collection("dailyQuestions")
            .document(questionDate.toString())
            .get()
            .addOnSuccessListener { document ->
                questionOfTheDay = document.getString("question") ?: "No question available for today."
            }

        firestore.collection("dailyAnswer")
            .whereEqualTo("questionDate", questionDate.toString())
            .addSnapshotListener { snapshot, _ ->
                snapshot?.let {
                    answers = it.documents.mapNotNull { doc ->
                        val data = doc.data
                        if (data != null) Pair(doc.id, data) else null
                    }.let { list ->
                        val (userAnswers, otherAnswers) = list.partition { it.second["userId"] == currentUserId }

                        val sortedOthers = otherAnswers.sortedByDescending {
                            val timeStr = it.second["timestamp"] as? String
                            try {
                                LocalDateTime.parse(timeStr, DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm"))
                            } catch (e: Exception) {
                                LocalDateTime.MIN
                            }
                        }

                        userAnswers + sortedOthers
                    }
                }
            }
    }

    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        Text("QOTD: $questionOfTheDay", style = MaterialTheme.typography.headlineMedium)

        Spacer(modifier = Modifier.height(16.dp))

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
                var username by remember { mutableStateOf("Loading...") }

                LaunchedEffect(answer["userId"]) {
                    fetchUserName(answer["userId"] as? String ?: "UnknownUser") { name ->
                        username = name
                    }
                }

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

                        Column(modifier = Modifier.fillMaxWidth()) {
                            if (answer["userId"] == currentUserId) {
                                Text(
                                    text = "Your Answer:",
                                    style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Bold)
                                )
                            } else {
                                Text(
                                    text = "$username's Answer:",
                                    style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Bold)
                                )
                            }

                            Text(
                                text = answerText,
                                style = MaterialTheme.typography.bodyLarge,
                                modifier = Modifier.padding(top = 5.dp)
                            )
                        }

                        Spacer(modifier = Modifier.height(8.dp))

                        Row(verticalAlignment = Alignment.CenterVertically) {
                            IconButton(onClick = { likeAnswer(answerId, currentUserId) }) {
                                Icon(
                                    imageVector = if (isLiked) Icons.Filled.Favorite else Icons.Outlined.FavoriteBorder,
                                    contentDescription = "Like",
                                    tint = MaterialTheme.colorScheme.primary
                                )
                            }
                            Text(
                                "${likesList.size} Likes",
                                color = MaterialTheme.colorScheme.primary,
                                style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Bold)
                            )

                            Spacer(modifier = Modifier.width(16.dp))

                            if (commentsList.isNotEmpty()) {
                                Icon(
                                    imageVector = Icons.Default.ArrowDropDown,
                                    contentDescription = "Dropdown",
                                    tint = MaterialTheme.colorScheme.primary
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                            }

                            Text(
                                text = replyText,
                                color = MaterialTheme.colorScheme.primary,
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
                                val commentUserId = comment["userId"]?.toString() ?: "UnknownUser"
                                var commentUsername by remember { mutableStateOf("Loading...") }

                                LaunchedEffect(commentUserId) {
                                    fetchUserName(commentUserId) { name ->
                                        commentUsername = name
                                    }
                                }

                                val commentText = comment["comment"]?.toString() ?: ""
                                Text(text = "$commentUsername: $commentText")
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

            if (answers.isEmpty()) {
                item {
                    Text(
                        text = "No answers yet!",
                        style = MaterialTheme.typography.bodyLarge,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(start = 0.dp)
                            .padding(top = 32.dp),
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                }
            }
        }
    }
}

@Composable
fun AnswerBottomNavigationBar() {
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
                context.startActivity(Intent(context, MainActivity::class.java))
            }) {
                Icon(
                    Icons.Default.Home,
                    contentDescription = "Home",
                    modifier = Modifier.size(32.dp),
                    tint = MaterialTheme.colorScheme.onPrimary
                )
            }

            IconButton(onClick = {
                context.startActivity(Intent(context, AddFriendActivity::class.java))
            }) {
                Icon(
                    Icons.Default.Group,
                    contentDescription = "Friends",
                    modifier = Modifier.size(32.dp),
                    tint = MaterialTheme.colorScheme.onPrimary
                )
            }

            IconButton(onClick = {
                // Current screen - do nothing
            }) {
                Icon(
                    Icons.Default.List,
                    contentDescription = "Answers",
                    modifier = Modifier.size(32.dp),
                    tint = MaterialTheme.colorScheme.onPrimary
                )
            }

            IconButton(onClick = {
                context.startActivity(Intent(context, SettingsActivity::class.java))
            }) {
                Icon(
                    Icons.Default.Settings,
                    contentDescription = "Settings",
                    modifier = Modifier.size(32.dp),
                    tint = MaterialTheme.colorScheme.onPrimary
                )
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