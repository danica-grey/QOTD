package com.example.qotd

import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import com.example.qotd.ui.theme.QOTDTheme
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import coil.compose.AsyncImage

data class FriendSearchResult(
    val userId: String,
    val username: String,
    val profilePicture: String? = null,
    val isAlreadyFriend: Boolean = false,
    val hasPendingRequest: Boolean = false,
    val isRequestReceived: Boolean = false
)

class AddFriendActivity : ComponentActivity() {
    @OptIn(ExperimentalMaterial3Api::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        val prefs = getSharedPreferences("qotd_prefs", Context.MODE_PRIVATE)
        val isDarkMode = prefs.getBoolean("dark_mode", false)

        setContent {
            QOTDTheme(darkTheme = isDarkMode) {
                val snackbarHostState = remember { SnackbarHostState() }
                val scope = rememberCoroutineScope()

                Surface(modifier = Modifier.fillMaxSize()) {
                    SettingsScreen()
                }

                Scaffold(
                    topBar = {
                        TopAppBar(
                            title = {
                                Text("Add Friends", style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(start = 8.dp))
                            },
                            navigationIcon = {
                                IconButton(onClick = {
                                    // Use 'this@AddFriendActivity' instead of 'context'
                                    this@AddFriendActivity.apply {
                                        setResult(RESULT_OK)
                                        finish()
                                    }
                                }) {
                                    Icon(Icons.Filled.ArrowBack, contentDescription = "Back")
                                }
                            }
                        )
                    },
                    modifier = Modifier.fillMaxSize(),
                    snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
                    bottomBar = { FriendBottomNavigationBar() }
                ) { innerPadding ->
                    AddFriendScreen(
                        modifier = Modifier.padding(innerPadding),
                        scope = scope,
                        snackbarHostState = snackbarHostState
                    )
                }
            }
        }
    }
}

@Composable
fun AddFriendScreen(
    modifier: Modifier = Modifier,
    scope: CoroutineScope,
    snackbarHostState: SnackbarHostState
) {
    var searchQuery by remember { mutableStateOf(TextFieldValue("")) }
    var searchResults by remember { mutableStateOf(emptyList<FriendSearchResult>()) }
    var friendsList by remember { mutableStateOf(emptyList<FriendSearchResult>()) }
    var pendingRequests by remember { mutableStateOf(emptyList<FriendSearchResult>()) }
    var isLoading by remember { mutableStateOf(false) }
    val currentUserId = FirebaseAuth.getInstance().currentUser?.uid ?: ""
    val db = FirebaseFirestore.getInstance()

    fun loadFriends() {
        scope.launch {
            isLoading = true
            try {
                // Load friends
                val friendsDoc = db.collection("friends")
                    .document(currentUserId)
                    .get()
                    .await()

                val friendIds = friendsDoc.get("friends") as? List<String> ?: emptyList()
                if (friendIds.isNotEmpty()) {
                    val docs = db.collection("users")
                        .whereIn(FieldPath.documentId(), friendIds)
                        .get()
                        .await()
                    friendsList = docs.documents.mapNotNull { doc ->
                        val username = doc.getString("username") ?: return@mapNotNull null
                        FriendSearchResult(
                            userId = doc.id,
                            username = username,
                            profilePicture = doc.getString("profilePicture"),
                            isAlreadyFriend = true
                        )
                    }
                }

                // Load pending requests
                val requestsSnapshot = db.collection("friend_requests")
                    .whereEqualTo("receiverId", currentUserId)
                    .whereEqualTo("status", "pending")
                    .get()
                    .await()

                val requestorIds = requestsSnapshot.documents.map { it.getString("senderId") ?: "" }
                if (requestorIds.isNotEmpty()) {
                    val requestorDocs = db.collection("users")
                        .whereIn(FieldPath.documentId(), requestorIds)
                        .get()
                        .await()

                    pendingRequests = requestorDocs.documents.mapNotNull { doc ->
                        val username = doc.getString("username") ?: return@mapNotNull null
                        FriendSearchResult(
                            userId = doc.id,
                            username = username,
                            profilePicture = doc.getString("profilePicture"),
                            isRequestReceived = true
                        )
                    }
                }
            } catch (e: Exception) {
                snackbarHostState.showSnackbar("Error loading friends: ${e.message}")
            } finally {
                isLoading = false
            }
        }
    }

    LaunchedEffect(Unit) { loadFriends() }

    fun searchUsers() {
        if (searchQuery.text.isBlank()) {
            searchResults = emptyList()
            return
        }
        scope.launch {
            isLoading = true
            try {
                val snap = db.collection("users")
                    .whereGreaterThanOrEqualTo("username", searchQuery.text)
                    .whereLessThanOrEqualTo("username", searchQuery.text + "\uf8ff")
                    .get()
                    .await()

                val existingFriends = friendsList.map { it.userId }.toSet()
                val existingRequests = db.collection("friend_requests")
                    .whereEqualTo("senderId", currentUserId)
                    .whereEqualTo("status", "pending")
                    .get()
                    .await()
                    .documents
                    .map { it.getString("receiverId") ?: "" }
                    .toSet()

                searchResults = snap.documents.mapNotNull { doc ->
                    val uid = doc.id
                    if (uid == currentUserId) return@mapNotNull null
                    val username = doc.getString("username") ?: return@mapNotNull null
                    FriendSearchResult(
                        userId = uid,
                        username = username,
                        profilePicture = doc.getString("profilePicture"),
                        isAlreadyFriend = existingFriends.contains(uid),
                        hasPendingRequest = existingRequests.contains(uid)
                    )
                }
            } catch (e: Exception) {
                snackbarHostState.showSnackbar("Error searching users: ${e.message}")
            } finally {
                isLoading = false
            }
        }
    }

    fun sendFriendRequest(receiverId: String, receiverUsername: String) {
        scope.launch {
            isLoading = true
            try {
                val requestData = hashMapOf(
                    "senderId" to currentUserId,
                    "receiverId" to receiverId,
                    "status" to "pending",
                    "timestamp" to FieldValue.serverTimestamp()
                )

                db.collection("friend_requests").add(requestData).await()
                snackbarHostState.showSnackbar("Friend request sent to $receiverUsername!")
                searchUsers()
            } catch (e: Exception) {
                snackbarHostState.showSnackbar("Error sending request: ${e.message}")
            } finally {
                isLoading = false
            }
        }
    }

    fun acceptFriendRequest(senderId: String, senderUsername: String) {
        scope.launch {
            isLoading = true
            try {
                // Update the request status
                val requestQuery = db.collection("friend_requests")
                    .whereEqualTo("senderId", senderId)
                    .whereEqualTo("receiverId", currentUserId)
                    .whereEqualTo("status", "pending")
                    .get()
                    .await()

                if (!requestQuery.isEmpty) {
                    for (doc in requestQuery.documents) {
                        doc.reference.update("status", "accepted").await()
                    }
                }

                // Add to friends list for both users
                val batch = db.batch()

                val currentUserRef = db.collection("friends").document(currentUserId)
                if (currentUserRef.get().await().exists()) {
                    batch.update(currentUserRef, "friends", FieldValue.arrayUnion(senderId))
                } else {
                    batch.set(currentUserRef, mapOf("friends" to listOf(senderId)))
                }

                val senderRef = db.collection("friends").document(senderId)
                if (senderRef.get().await().exists()) {
                    batch.update(senderRef, "friends", FieldValue.arrayUnion(currentUserId))
                } else {
                    batch.set(senderRef, mapOf("friends" to listOf(currentUserId)))
                }

                batch.commit().await()

                // Immediately update UI
                pendingRequests = pendingRequests.filter { it.userId != senderId }
                friendsList = friendsList + FriendSearchResult(
                    userId = senderId,
                    username = senderUsername,
                    isAlreadyFriend = true
                )

                snackbarHostState.showSnackbar("Accepted $senderUsername's friend request!")
            } catch (e: Exception) {
                snackbarHostState.showSnackbar("Error accepting request: ${e.message}")
            } finally {
                isLoading = false
            }
        }
    }

    fun rejectFriendRequest(senderId: String, senderUsername: String) {
        scope.launch {
            isLoading = true
            try {
                val requestQuery = db.collection("friend_requests")
                    .whereEqualTo("senderId", senderId)
                    .whereEqualTo("receiverId", currentUserId)
                    .whereEqualTo("status", "pending")
                    .get()
                    .await()

                if (!requestQuery.isEmpty) {
                    for (doc in requestQuery.documents) {
                        doc.reference.update("status", "rejected").await()
                    }
                }

                // Immediately update UI
                pendingRequests = pendingRequests.filter { it.userId != senderId }

                snackbarHostState.showSnackbar("Declined $senderUsername's request")
            } catch (e: Exception) {
                snackbarHostState.showSnackbar("Error rejecting request: ${e.message}")
            } finally {
                isLoading = false
            }
        }
    }

    val context = LocalContext.current

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.Top,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        OutlinedTextField(
            value = searchQuery,
            onValueChange = {
                searchQuery = it
                searchUsers()
            },
            label = { Text("Search by username...") },
            trailingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(Modifier.height(16.dp))

        if (isLoading) {
            CircularProgressIndicator(Modifier.padding(16.dp))
        }

        if (pendingRequests.isNotEmpty()) {
            Text("Friend Requests", style = MaterialTheme.typography.titleLarge, modifier = Modifier.align(Alignment.Start))
            LazyColumn(Modifier.heightIn(max = 200.dp)) {
                items(pendingRequests) { request ->
                    FriendRequestItem(
                        username = request.username,
                        profilePicture = request.profilePicture,
                        onAccept = { acceptFriendRequest(request.userId, request.username) },
                        onReject = { rejectFriendRequest(request.userId, request.username) }
                    )
                }
            }
            Divider(Modifier.padding(vertical = 8.dp))
        }

        if (friendsList.isNotEmpty()) {
            Text("Your Friends", style = MaterialTheme.typography.titleLarge, modifier = Modifier.align(Alignment.Start))
            LazyColumn(Modifier.heightIn(max = 200.dp)) {
                items(friendsList) { friend ->
                    FriendItem(
                        username = friend.username,
                        isFriend = true,
                        profilePicture = friend.profilePicture
                    )
                }
            }
            Divider(Modifier.padding(vertical = 8.dp))
        }

        if (searchResults.isNotEmpty()) {
            Text(
                "Search Results",
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.align(Alignment.Start)
            )
            LazyColumn(
                modifier = Modifier.padding(top = 12.dp) // <-- Adjust this value (e.g., 8.dp, 12.dp, 16.dp)
            ) {
                items(searchResults) { user ->
                    FriendItem(
                        username = user.username,
                        isFriend = user.isAlreadyFriend,
                        profilePicture = user.profilePicture,
                        hasPendingRequest = user.hasPendingRequest,
                        onRequest = { sendFriendRequest(user.userId, user.username) }
                    )
                }
            }
        } else if (searchQuery.text.isNotBlank() && !isLoading) {
            Text("No users found!", Modifier.padding(16.dp))
        }
    }
}

@Composable
fun FriendItem(
    username: String,
    isFriend: Boolean,
    profilePicture: String? = null,
    hasPendingRequest: Boolean = false,
    onRequest: (() -> Unit)? = null
) {
    val context = LocalContext.current

    Card(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
        Row(
            Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (profilePicture?.startsWith("http") == true) {
                    AsyncImage(
                        model = profilePicture,
                        contentDescription = "Profile Pic",
                        modifier = Modifier.size(48.dp).clip(CircleShape)
                    )
                }
                else if (profilePicture?.startsWith("drawable/") == true) {
                    val resId = context.resources.getIdentifier(
                        profilePicture.removePrefix("drawable/"),
                        "drawable",
                        context.packageName
                    )
                    if (resId != 0) {
                        Image(
                            painter = painterResource(id = resId),
                            contentDescription = "Profile Pic",
                            modifier = Modifier.size(48.dp).clip(CircleShape)
                        )
                    } else {
                        Icon(
                            Icons.Default.AccountCircle,
                            contentDescription = null,
                            modifier = Modifier.size(48.dp).clip(CircleShape)
                        )
                    }
                }
                else {
                    Icon(
                        Icons.Default.AccountCircle,
                        contentDescription = null,
                        modifier = Modifier.size(48.dp).clip(CircleShape)
                    )
                }

                Spacer(Modifier.width(12.dp))
                Text(username, style = MaterialTheme.typography.bodyLarge)
            }

            if (isFriend) {
                Text("Friends", color = MaterialTheme.colorScheme.primary)
            } else if (hasPendingRequest) {
                Text("Request Sent", color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f))
            } else {
                onRequest?.let {
                    Button(
                        onClick = it,
                        modifier = Modifier.height(36.dp)
                    ) {
                        Text("Add Friend")
                    }
                }
            }
        }
    }
}

@Composable
fun FriendRequestItem(
    username: String,
    profilePicture: String? = null,
    onAccept: () -> Unit,
    onReject: () -> Unit
) {
    val context = LocalContext.current

    Card(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
        Row(
            Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (profilePicture?.startsWith("http") == true) {
                    AsyncImage(
                        model = profilePicture,
                        contentDescription = "Profile Pic",
                        modifier = Modifier.size(48.dp).clip(CircleShape)
                    )
                }
                else if (profilePicture?.startsWith("drawable/") == true) {
                    val resId = context.resources.getIdentifier(
                        profilePicture.removePrefix("drawable/"),
                        "drawable",
                        context.packageName
                    )
                    if (resId != 0) {
                        Image(
                            painter = painterResource(id = resId),
                            contentDescription = "Profile Pic",
                            modifier = Modifier.size(48.dp).clip(CircleShape)
                        )
                    } else {
                        Icon(
                            Icons.Default.AccountCircle,
                            contentDescription = null,
                            modifier = Modifier.size(48.dp).clip(CircleShape)
                        )
                    }
                }
                else {
                    Icon(
                        Icons.Default.AccountCircle,
                        contentDescription = null,
                        modifier = Modifier.size(48.dp).clip(CircleShape)
                    )
                }

                Spacer(Modifier.width(12.dp))
                Text(username, style = MaterialTheme.typography.bodyLarge)
            }

            Row {
                IconButton(onClick = onAccept) {
                    Icon(
                        Icons.Default.Check,
                        contentDescription = "Accept",
                        tint = MaterialTheme.colorScheme.primary
                    )
                }
                IconButton(onClick = onReject) {
                    Icon(
                        Icons.Default.Close,
                        contentDescription = "Reject",
                        tint = MaterialTheme.colorScheme.error
                    )
                }
            }
        }
    }
}

@Composable
fun FriendBottomNavigationBar() {
    val context = LocalContext.current
    BottomAppBar(
        modifier = Modifier.fillMaxWidth().height(88.dp),
        containerColor = MaterialTheme.colorScheme.primary
    ) {
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceAround,
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton({ context.startActivity(Intent(context, MainActivity::class.java)) }) {
                Icon(
                    Icons.Default.Home,
                    contentDescription = null,
                    modifier = Modifier.size(32.dp),
                    tint = MaterialTheme.colorScheme.onPrimary
                )
            }
            IconButton({ /* already here */ }) {
                Icon(
                    Icons.Default.Group,
                    contentDescription = null,
                    modifier = Modifier.size(32.dp),
                    tint = MaterialTheme.colorScheme.onPrimary
                )
            }
            IconButton({ context.startActivity(Intent(context, UserAnswersActivity::class.java)) }) {
                Icon(
                    Icons.Default.List,
                    contentDescription = null,
                    modifier = Modifier.size(32.dp),
                    tint = MaterialTheme.colorScheme.onPrimary
                )
            }
            IconButton({ context.startActivity(Intent(context, SettingsActivity::class.java)) }) {
                Icon(
                    Icons.Default.Settings,
                    contentDescription = null,
                    modifier = Modifier.size(32.dp),
                    tint = MaterialTheme.colorScheme.onPrimary
                )
            }
        }
    }
}