package com.example.qotd

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Face
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
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

data class FriendSearchResult(
    val userId: String,
    val username: String,
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
                    snackbarHost = { SnackbarHost(hostState = snackbarHostState) }
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
            try {
                isLoading = true
                val friendsDoc = db.collection("friends")
                    .document(currentUserId)
                    .get()
                    .await()

                val friendIds = friendsDoc.get("friends") as? List<String> ?: emptyList()

                if (friendIds.isNotEmpty()) {
                    val friends = db.collection("users")
                        .whereIn(FieldPath.documentId(), friendIds)
                        .get()
                        .await()
                        .documents
                        .mapNotNull { doc ->
                            doc.getString("username")?.let { username ->
                                FriendSearchResult(
                                    userId = doc.id,
                                    username = username,
                                    isAlreadyFriend = true
                                )
                            }
                        }
                    friendsList = friends
                }
            } catch (e: Exception) {
                scope.launch {
                    snackbarHostState.showSnackbar("Error loading friends: ${e.message}")
                }
            } finally {
                isLoading = false
            }
        }
    }

    LaunchedEffect(Unit) {
        loadFriends()
    }

    fun searchUsers() {
        if (searchQuery.text.isEmpty()) {
            searchResults = emptyList()
            return
        }

        isLoading = true
        scope.launch {
            try {
                val querySnapshot = db.collection("users")
                    .whereGreaterThanOrEqualTo("username", searchQuery.text)
                    .whereLessThanOrEqualTo("username", searchQuery.text + "\uf8ff")
                    .get()
                    .await()

                val friendIds = friendsList.map { it.userId }

                searchResults = querySnapshot.documents.mapNotNull { doc ->
                    val username = doc.getString("username") ?: return@mapNotNull null
                    val userId = doc.id
                    if (userId != currentUserId) {
                        FriendSearchResult(
                            userId = userId,
                            username = username,
                            isAlreadyFriend = friendIds.contains(userId)
                        )
                    } else {
                        null
                    }
                }
            } catch (e: Exception) {
                scope.launch {
                    snackbarHostState.showSnackbar("Error searching users: ${e.message}")
                }
            } finally {
                isLoading = false
            }
        }
    }

    fun addFriend(friendId: String, friendUsername: String) {
        scope.launch {
            try {
                isLoading = true
                val friendsRef = db.collection("friends").document(currentUserId)

                if (friendsRef.get().await().exists()) {
                    friendsRef.update("friends", FieldValue.arrayUnion(friendId))
                } else {
                    friendsRef.set(mapOf("friends" to listOf(friendId)))
                }

                scope.launch {
                    snackbarHostState.showSnackbar("Added $friendUsername to friends!")
                }
                loadFriends()
                searchUsers()
            } catch (e: Exception) {
                scope.launch {
                    snackbarHostState.showSnackbar("Error adding friend: ${e.message}")
                }
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
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(
                onClick = {
                    if (context is ComponentActivity) {
                        context.finish()
                    }
                },
                modifier = Modifier
                    .size(40.dp)
                    .offset(x = (-8).dp) // Shift 8dp to the left
            ) {
                Icon(
                    imageVector = Icons.Filled.ArrowBack,
                    contentDescription = "Back",
                    modifier = Modifier.size(24.dp)
                )
            }

            Icon(
                imageVector = Icons.Default.Face,
                contentDescription = "Friends",
                modifier = Modifier
                    .size(32.dp)
                    .padding(end = 8.dp)
            )
            
            Text(
                text = "Friends",
                style = MaterialTheme.typography.headlineSmall,
                modifier = Modifier.weight(1f) 
            )
        }
        
        OutlinedTextField(
            value = searchQuery,
            onValueChange = {
                searchQuery = it
                searchUsers()
            },
            label = { Text("Search by username...") },
            trailingIcon = {
                Icon(Icons.Default.Search, contentDescription = "Search")
            },
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(modifier = Modifier.height(16.dp))

        if (isLoading) {
            CircularProgressIndicator(modifier = Modifier.padding(16.dp))
        }

        if (friendsList.isNotEmpty()) {
            Text(
                text = "Your Friends",
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier
                    .padding(vertical = 8.dp)
                    .align(Alignment.Start)
            )
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 200.dp)
            ) {
                items(friendsList) { friend ->
                    FriendItem(
                        username = friend.username,
                        isFriend = true
                    )
                }
            }
            Divider(modifier = Modifier.padding(vertical = 8.dp))
        }

        if (searchResults.isNotEmpty()) {
            Text(
                text = "Search Results",
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier
                    .padding(vertical = 8.dp)
                    .align(Alignment.Start)
            )
            LazyColumn(modifier = Modifier.fillMaxWidth()) {
                items(searchResults) { user ->
                    FriendItem(
                        username = user.username,
                        isFriend = user.isAlreadyFriend,
                        onAdd = { addFriend(user.userId, user.username) }
                    )
                }
            }
        } else if (searchQuery.text.isNotEmpty() && !isLoading) {
            Text("No users found", modifier = Modifier.padding(16.dp))
        }
    }
}


@Composable
fun FriendItem(
    username: String,
    isFriend: Boolean,
    onAdd: (() -> Unit)? = null
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(text = username, style = MaterialTheme.typography.bodyLarge)

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
