package com.example.qotd

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
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Face
import androidx.compose.material.icons.filled.Group
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.List
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import com.example.qotd.ui.theme.QOTDTheme
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FieldPath
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import coil.compose.AsyncImage

data class FriendSearchResult(
    val userId: String,
    val username: String,
    val profilePicture: String? = null,      // renamed to match Firestore
    val isAlreadyFriend: Boolean = false
)

class AddFriendActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            QOTDTheme {
                val snackbarHostState = remember { SnackbarHostState() }
                val scope = rememberCoroutineScope()

                Scaffold(
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
    var isLoading by remember { mutableStateOf(false) }
    val currentUserId = FirebaseAuth.getInstance().currentUser?.uid ?: ""
    val db = FirebaseFirestore.getInstance()

    val loadFriends = {
        scope.launch {
            isLoading = true
            try {
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
                        // **Pull the same "profilePicture" field**:
                        val pfp = doc.getString("profilePicture")
                        FriendSearchResult(
                            userId = doc.id,
                            username = username,
                            profilePicture = pfp,
                            isAlreadyFriend = true
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

                val existing = friendsList.map { it.userId }.toSet()
                searchResults = snap.documents.mapNotNull { doc ->
                    val uid = doc.id
                    if (uid == currentUserId) return@mapNotNull null
                    val username = doc.getString("username") ?: return@mapNotNull null
                    // **Also pull "profilePicture" here**
                    FriendSearchResult(
                        userId = uid,
                        username = username,
                        profilePicture = doc.getString("profilePicture"),
                        isAlreadyFriend = existing.contains(uid)
                    )
                }
            } catch (e: Exception) {
                snackbarHostState.showSnackbar("Error searching users: ${e.message}")
            } finally {
                isLoading = false
            }
        }
    }

    fun addFriend(friendId: String, friendUsername: String) {
        scope.launch {
            isLoading = true
            try {
                val ref = db.collection("friends").document(currentUserId)
                if (ref.get().await().exists()) {
                    ref.update("friends", FieldValue.arrayUnion(friendId))
                } else {
                    ref.set(mapOf("friends" to listOf(friendId)))
                }
                snackbarHostState.showSnackbar("Added $friendUsername!")
                loadFriends()
                searchUsers()
            } catch (e: Exception) {
                snackbarHostState.showSnackbar("Error adding friend: ${e.message}")
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
        // … your header & search bar …
        OutlinedTextField(
            value = searchQuery,
            onValueChange = {
                searchQuery = it
                searchUsers()
            },
            label = { Text("Search by username…") },
            trailingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(Modifier.height(16.dp))

        if (isLoading) {
            CircularProgressIndicator(Modifier.padding(16.dp))
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
            Text("Search Results", style = MaterialTheme.typography.titleLarge, modifier = Modifier.align(Alignment.Start))
            LazyColumn {
                items(searchResults) { user ->
                    FriendItem(
                        username = user.username,
                        isFriend = user.isAlreadyFriend,
                        profilePicture = user.profilePicture,
                        onAdd    = { addFriend(user.userId, user.username) }
                    )
                }
            }
        } else if (searchQuery.text.isNotBlank() && !isLoading) {
            Text("No users found", Modifier.padding(16.dp))
        }
    }
}

@Composable
fun FriendItem(
    username: String,
    isFriend: Boolean,
    profilePicture: String? = null,
    onAdd: (() -> Unit)? = null
) {
    val context = LocalContext.current

    Card(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
        Row(
            Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment   = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                // 1) Network URL → AsyncImage
                if (profilePicture?.startsWith("http") == true) {
                    AsyncImage(
                        model             = profilePicture,
                        contentDescription = "Profile Pic",
                        modifier          = Modifier.size(48.dp).clip(CircleShape)
                    )
                }
                // 2) Drawable resource name → Image(painterResource)
                else if (profilePicture?.startsWith("drawable/") == true) {
                    val resId = context.resources.getIdentifier(
                        profilePicture.removePrefix("drawable/"),
                        "drawable",
                        context.packageName
                    )
                    if (resId != 0) {
                        Image(
                            painter            = painterResource(id = resId),
                            contentDescription = "Profile Pic",
                            modifier           = Modifier.size(48.dp).clip(CircleShape)
                        )
                    } else {
                        Icon(
                            Icons.Default.AccountCircle,
                            contentDescription = null,
                            modifier           = Modifier.size(48.dp).clip(CircleShape)
                        )
                    }
                }
                // 3) Fallback
                else {
                    Icon(
                        Icons.Default.AccountCircle,
                        contentDescription = null,
                        modifier           = Modifier.size(48.dp).clip(CircleShape)
                    )
                }

                Spacer(Modifier.width(12.dp))
                Text(username, style = MaterialTheme.typography.bodyLarge)
            }

            if (isFriend) {
                Text("Friends", color = MaterialTheme.colorScheme.primary)
            } else {
                onAdd?.let {
                    IconButton(onClick = it) {
                        Icon(Icons.Default.Add, contentDescription = "Add friend")
                    }
                }
            }
        }
    }
}

@Composable
fun FriendBottomNavigationBar() {
    val context = LocalContext.current
    BottomAppBar(
        modifier       = Modifier.fillMaxWidth().height(88.dp),
        containerColor = MaterialTheme.colorScheme.primary
    ) {
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceAround,
            verticalAlignment     = Alignment.CenterVertically
        ) {
            IconButton({ context.startActivity(Intent(context, MainActivity::class.java)) }) {
                Icon(
                    Icons.Default.Home,
                    contentDescription = null,
                    modifier = Modifier.size(32.dp),
                    tint     = MaterialTheme.colorScheme.onPrimary
                )
            }
            IconButton({ /* already here */ }) {
                Icon(
                    Icons.Default.Group,
                    contentDescription = null,
                    modifier = Modifier.size(32.dp),
                    tint     = MaterialTheme.colorScheme.onPrimary
                )
            }
            IconButton({ context.startActivity(Intent(context, UserAnswersActivity::class.java)) }) {
                Icon(
                    Icons.Default.List,
                    contentDescription = null,
                    modifier = Modifier.size(32.dp),
                    tint     = MaterialTheme.colorScheme.onPrimary
                )
            }
            IconButton({ context.startActivity(Intent(context, SettingsActivity::class.java)) }) {
                Icon(
                    Icons.Default.Settings,
                    contentDescription = null,
                    modifier = Modifier.size(32.dp),
                    tint     = MaterialTheme.colorScheme.onPrimary
                )
            }
        }
    }
}

