package com.example.qotd

import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.rememberAsyncImagePainter
import com.example.qotd.ui.theme.QOTDTheme
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import coil.compose.AsyncImage

private var refreshAnswersTrigger = mutableStateOf(0)

class UserAnswersActivity : ComponentActivity() {
    @OptIn(ExperimentalMaterial3Api::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val extras = intent.extras
        val questionDate = extras?.getString("questionDate")?.let { LocalDate.parse(it) } ?: LocalDate.now()
        val displayDate = questionDate.format(DateTimeFormatter.ofPattern("MMMM d, yyyy"))
        val prefs = getSharedPreferences("qotd_prefs", Context.MODE_PRIVATE)
        val isDarkMode = prefs.getBoolean("dark_mode", false)

        setContent {
            val context = LocalContext.current
            val iconColor = if (isDarkMode) Color.White else Color.Black

            QOTDTheme(darkTheme = isDarkMode) {
                Surface(modifier = Modifier.fillMaxSize()) {
                    SettingsScreen()
                }

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
                                    if (context is ComponentActivity) {
                                        context.finish() // Just go back
                                    }
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
                                        colorFilter = ColorFilter.tint(iconColor),
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
                        UserAnswersScreen(questionDate, displayDate, refreshAnswersTrigger.value)
                    }
                }
            }
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == 100 && resultCode == RESULT_OK) {
            refreshAnswersTrigger.value += 1
        }
    }

    override fun onBackPressed() {
        val wasNavigatedFromPastQuestions = intent.getBooleanExtra("fromPastQuestions", false)

        if (wasNavigatedFromPastQuestions) {
            val intent = Intent(this, PastQuestionsActivity::class.java)
            startActivity(intent)
        } else {
            super.onBackPressed()
        }
    }
}

@Composable
fun UserAnswersScreen(questionDate: LocalDate, displayDate: String, refreshTrigger: Int) {
    val firestore = FirebaseFirestore.getInstance()
    var answers by remember { mutableStateOf<List<Pair<String, Map<String, Any>>>>(emptyList()) }
    var questionOfTheDay by remember { mutableStateOf("Loading question...") }
    val currentUserId = FirebaseAuth.getInstance().currentUser?.uid ?: "UnknownUser"
    val userNames = remember { mutableStateMapOf<String, String>() }
    val profilePicCache = remember { mutableStateMapOf<String, String?>() }
    var userPrivacySettings by remember { mutableStateOf<Map<String, Any>?>(null) }
    val userStreaks = remember { mutableStateMapOf<String, Int>() }

    val friendsList = remember { mutableStateListOf<String>() }
    var friendsFetched by remember { mutableStateOf(false) }
    val userPrivacyMap = remember { mutableStateMapOf<String, String>() }

    fun fetchUserPrivacy(userId: String, onResult: (String) -> Unit) {
        if (userPrivacyMap.containsKey(userId)) {
            onResult(userPrivacyMap[userId] ?: "Public")
            return
        }

        firestore.collection("users").document(userId).get()
            .addOnSuccessListener { document ->
                val privacy = document.getString("privacy") ?: "Public"
                userPrivacyMap[userId] = privacy
                onResult(privacy)
            }
            .addOnFailureListener {
                userPrivacyMap[userId] = "Public"
                onResult("Public")
            }
    }

    fun fetchFriendsListIfNeeded(onComplete: () -> Unit) {
        if (friendsFetched) {
            onComplete()
            return
        }
        firestore.collection("friends").document(currentUserId).get()
            .addOnSuccessListener { document ->
                val list = document.get("friends") as? List<*>
                friendsList.clear()
                friendsList.addAll(list?.filterIsInstance<String>().orEmpty())
                friendsFetched = true
                onComplete()
            }
            .addOnFailureListener {
                friendsFetched = true
                onComplete()
            }
    }
    fun fetchUserStreaks(userIds: List<String>) {
        val firestore = FirebaseFirestore.getInstance()
        for (userId in userIds) {
            if (userStreaks.containsKey(userId)) continue

            firestore.collection("users").document(userId).get()
                .addOnSuccessListener { doc ->
                    val streak = doc.getLong("streakCount")?.toInt() ?: 0
                    userStreaks[userId] = streak
                }
                .addOnFailureListener {
                    userStreaks[userId] = 0
                }
        }
    }

    fun isUserFriendsWithOtherUser(otherUserId: String): Boolean {
        return friendsList.contains(otherUserId)
    }

    fun fetchUserPrivacySettings(onResult: (Map<String, Any>?) -> Unit) {
        firestore.collection("users").document(currentUserId).get()
            .addOnSuccessListener { document ->
                val privacySettings = document.data
                userPrivacySettings = privacySettings
                onResult(privacySettings)
            }
    }

    fun fetchUserNameAndPic(userId: String, onResult: (String, String?) -> Unit) {
        if (userId == "UnknownUser") {
            onResult("Unknown User", null)
            return
        }
        if (userNames.containsKey(userId) && profilePicCache.containsKey(userId)) {
            onResult(userNames[userId]!!, profilePicCache[userId])
            return
        }
        firestore.collection("users").document(userId).get()
            .addOnSuccessListener { document ->
                if (document.exists()) {
                    val username = document.getString("username") ?: "Deleted User"
                    val profilePic = document.getString("profilePicture")
                    userNames[userId] = username
                    profilePicCache[userId] = profilePic
                    onResult(username, profilePic)
                } else {
                    userNames[userId] = "Deleted User"
                    profilePicCache[userId] = null
                    onResult("Deleted User", null)
                }
            }
            .addOnFailureListener {
                onResult("Deleted User", null)
            }
    }

    LaunchedEffect(questionDate, refreshTrigger) {
        fetchUserPrivacySettings { privacySettings ->
            userPrivacySettings = privacySettings

            fetchFriendsListIfNeeded {
                firestore.collection("dailyQuestions")
                    .document(questionDate.toString())
                    .get()
                    .addOnSuccessListener { document ->
                        questionOfTheDay = document.getString("question") ?: "No question available for today."
                    }

                firestore.collection("dailyAnswer")
                    .whereEqualTo("questionDate", questionDate.toString())
                    .get()
                    .addOnSuccessListener { snapshot ->
                        val allAnswers = snapshot.documents.mapNotNull { doc ->
                            val data = doc.data ?: return@mapNotNull null
                            val userId = data["userId"] as? String ?: return@mapNotNull null
                            Triple(doc.id, userId, data)
                        }

                        val filteredAnswers = mutableListOf<Pair<String, Map<String, Any>>>()

                        val filteredAnswerJobs = allAnswers.map { (docId, userId, data) ->
                            kotlinx.coroutines.GlobalScope.async {
                                if (userId.isNullOrBlank()) {
                                    userPrivacyMap[userId] = "Public"
                                    return@async
                                }

                                val privacy = userPrivacyMap[userId] ?: run {
                                    try {
                                        val userDoc = firestore.collection("users").document(userId).get().await()
                                        if (userDoc.exists()) {
                                            val fetchedPrivacy = userDoc.getString("privacy") ?: "Public"
                                            userPrivacyMap[userId] = fetchedPrivacy
                                            fetchedPrivacy
                                        } else {
                                            userPrivacyMap[userId] = "Public"
                                            "Public"
                                        }
                                    } catch (e: Exception) {
                                        userPrivacyMap[userId] = "Public"
                                        "Public"
                                    }
                                }

                                val isFriend = isUserFriendsWithOtherUser(userId)
                                val isSelf = userId == currentUserId

                                if (privacy == "Public" || (privacy == "Private" && (isFriend || isSelf)) || privacy == "Anonymous") {
                                    synchronized(filteredAnswers) {
                                        filteredAnswers.add(docId to data)
                                    }
                                }
                            }
                        }


                        kotlinx.coroutines.GlobalScope.launch {
                            filteredAnswerJobs.awaitAll()

                            val (userAnswers, otherAnswers) = filteredAnswers.partition { it.second["userId"] == currentUserId }

                            val visibleAnswers = when {
                                userPrivacySettings?.get("onlyShowFriendsAnswers") == true -> {
                                    userAnswers + otherAnswers.filter { (_, data) ->
                                        val uid = data["userId"] as? String ?: return@filter false
                                        isUserFriendsWithOtherUser(uid)
                                    }
                                }
                                else -> userAnswers + otherAnswers
                            }

                            val sortedAnswers = visibleAnswers.filterNot { it.second["userId"] == currentUserId }
                                .sortedByDescending {
                                    val ts = it.second["timestamp"] as? String
                                    try {
                                        LocalDateTime.parse(ts, DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm"))
                                    } catch (e: Exception) {
                                        LocalDateTime.MIN
                                    }
                                }

                            answers = userAnswers + sortedAnswers
                            val userIdsToFetch = (userAnswers + sortedAnswers).mapNotNull { it.second["userId"] as? String }.distinct()
                            fetchUserStreaks(userIdsToFetch)
                        }
                    }
            }
        }
    }

    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        Text("$questionOfTheDay", style = MaterialTheme.typography.headlineMedium)
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
                var profilePicUrl by remember { mutableStateOf<String?>(null) }


                LaunchedEffect(answer["userId"]) {
                    fetchUserNameAndPic(answer["userId"] as? String ?: "UnknownUser") { name, pic ->
                        username = name
                        profilePicUrl = pic
                    }
                }

                val answerUserId = answer["userId"] as? String ?: "UnknownUser"
                val privacy = userPrivacyMap[answerUserId] ?: "Public"
                val isFriend = isUserFriendsWithOtherUser(answerUserId)
                val streak = userStreaks[answerUserId] ?: 0
                val displayName = if (streak > 0) "$username 🔥$streak" else username

                if (privacy == "Anonymous" && answerUserId != currentUserId && !isFriend) {
                    username = "Anonymous"
                    profilePicUrl = null
                }

                val replyText = when (commentsList.size) {
                    0 -> "Reply"
                    1 -> "1 Reply"
                    else -> "${commentsList.size} Replies"
                }

                val painter: Painter? = when {
                    profilePicUrl?.startsWith("drawable/") == true -> {
                        val resId = LocalContext.current.resources.getIdentifier(
                            profilePicUrl!!.removePrefix("drawable/"),
                            "drawable",
                            LocalContext.current.packageName
                        )
                        if (resId != 0) painterResource(id = resId) else null
                    }
                    profilePicUrl?.startsWith("http") == true -> rememberAsyncImagePainter(profilePicUrl)
                    else -> null
                }

                Card(
                    modifier = Modifier.fillMaxWidth().padding(8.dp),
                    elevation = CardDefaults.cardElevation(4.dp)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            if (painter != null) {
                                Image(
                                    painter = painter,
                                    contentDescription = "Profile Pic",
                                    modifier = Modifier
                                        .size(48.dp)
                                        .clip(CircleShape)
                                )
                            } else {
                                Icon(
                                    imageVector = Icons.Default.AccountCircle,
                                    contentDescription = "Default Icon",
                                    modifier = Modifier
                                        .size(48.dp)
                                        .clip(CircleShape)
                                )
                            }
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = displayName,
                                style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Bold)
                            )

                            // Moved like button to the right side
                            Spacer(modifier = Modifier.weight(1f))
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.Center // TODO: this doesn't do shit, not a big deal but bruh
                            ) {
                                IconButton(
                                    onClick = {
                                        likeAnswer(answerId, currentUserId) {
                                            refreshAnswersTrigger.value += 1
                                        }
                                    },
                                    modifier = Modifier.size(24.dp)
                                ) {
                                    Icon(
                                        imageVector = if (isLiked) Icons.Filled.Favorite else Icons.Outlined.FavoriteBorder,
                                        contentDescription = "Like",
                                        tint = MaterialTheme.colorScheme.primary
                                    )
                                }

                                Spacer(modifier = Modifier.height(4.dp))

                                Text(
                                    "${likesList.size}",
                                    color = MaterialTheme.colorScheme.primary,
                                    style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Bold),
                                    fontSize = 15.sp
                                )
                            }
                        }

                        Text(
                            text = answerText,
                            style = MaterialTheme.typography.bodyLarge,
                            modifier = Modifier.padding(top = 10.dp)
                        )

                        Spacer(modifier = Modifier.height(8.dp))

                        // Replies button moved to left-bottom
                        Text(
                            text = replyText,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier
                                .clickable { showComments = !showComments }
                                .padding(6.dp),
                            style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Bold),
                            fontSize = 15.sp
                        )

                        if (showComments) {
                            Spacer(modifier = Modifier.height(8.dp))
                            commentsList.forEach { comment ->
                                val commentUserId = comment["userId"]?.toString() ?: "UnknownUser"
                                var commentUsername by remember { mutableStateOf("Loading...") }
                                LaunchedEffect(commentUserId) {
                                    fetchUserNameAndPic(commentUserId) { name, _ ->
                                        commentUsername = name
                                    }
                                }
                                val commentText = comment["comment"]?.toString() ?: ""

                                // Use AnnotatedString to style parts differently
                                val annotatedText = buildAnnotatedString {
                                    // Bold username
                                    withStyle(style = SpanStyle(fontWeight = FontWeight.Bold)) {
                                        append("$commentUsername: ")
                                    }
                                    // Normal comment text
                                    append(commentText)
                                }

                                Text(
                                    text = annotatedText,
                                    style = MaterialTheme.typography.bodySmall,
                                    fontSize = 15.sp
                                )
                            }

                            Spacer(modifier = Modifier.height(12.dp))

                            OutlinedTextField(
                                value = newComment,
                                onValueChange = { newComment = it },
                                label = { Text("Add a comment...") },
                                modifier = Modifier.fillMaxWidth()
                            )

                            Spacer(modifier = Modifier.height(8.dp))

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.End
                            ) {
                                Button(
                                    onClick = {
                                        if (newComment.isNotBlank()) {
                                            addComment(answerId, newComment) {
                                                refreshAnswersTrigger.value += 1
                                            }
                                            newComment = ""
                                        }
                                    },
                                    modifier = Modifier.padding(top = 8.dp),
                                ) {
                                    Text("Reply", color = Color.White, fontSize = 15.sp)
                                }
                            }
                        }
                    }
                }
            }

            if (answers.isEmpty()) {
                item {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(top = 64.dp),
                        verticalArrangement = Arrangement.Center,
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Spacer(modifier = Modifier.weight(0.4f))
                        Text(
                            text = "No answers",
                            style = MaterialTheme.typography.bodyLarge.copy(fontSize = 24.sp),
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                        )
                        Text(
                            text = "☹",
                            style = MaterialTheme.typography.bodyLarge.copy(fontSize = 48.sp),
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun AnswerBottomNavigationBar() {
    val context = LocalContext.current
    BottomAppBar(
        modifier = Modifier.fillMaxWidth().height(88.dp),
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
                Icon(Icons.Default.Home, contentDescription = "Home", modifier = Modifier.size(32.dp), tint = Color.White)
            }
            IconButton(onClick = {
                context.startActivity(Intent(context, AddFriendActivity::class.java))
            }) {
                Icon(Icons.Default.Group, contentDescription = "Friends", modifier = Modifier.size(32.dp), tint = Color.White)
            }
            IconButton(onClick = { }) {
                Icon(Icons.Default.List, contentDescription = "Answers", modifier = Modifier.size(32.dp), tint = Color.White)
            }
            IconButton(onClick = {
                val intent = Intent(context, SettingsActivity::class.java)
                (context as? ComponentActivity)?.startActivityForResult(intent, 100)
            }) {
                Icon(Icons.Default.Settings, contentDescription = "Settings", modifier = Modifier.size(32.dp), tint = Color.White)
            }
        }
    }
}

fun likeAnswer(answerId: String, userId: String, onSuccess: () -> Unit) {
    val firestore = FirebaseFirestore.getInstance()
    val answerRef = firestore.collection("dailyAnswer").document(answerId)
    firestore.runTransaction { transaction ->
        val snapshot = transaction.get(answerRef)
        val likesList = (snapshot.get("likes") as? List<*>)?.mapNotNull { it as? String }?.toMutableList() ?: mutableListOf()
        if (!likesList.contains(userId)) likesList.add(userId) else likesList.remove(userId)
        transaction.update(answerRef, "likes", likesList)
    }.addOnSuccessListener {
        onSuccess()
    }
}

fun addComment(answerId: String, commentText: String, onSuccess: () -> Unit) {
    val firestore = FirebaseFirestore.getInstance()
    val answerRef = firestore.collection("dailyAnswer").document(answerId)
    val formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")
    val currentTime = LocalDateTime.now().format(formatter)
    val currentUserId = FirebaseAuth.getInstance().currentUser?.uid ?: "UnknownUser"
    val newComment = hashMapOf("userId" to currentUserId, "comment" to commentText, "timestamp" to currentTime)
    answerRef.update("comments", FieldValue.arrayUnion(newComment))
        .addOnSuccessListener {
            onSuccess()
        }
}

