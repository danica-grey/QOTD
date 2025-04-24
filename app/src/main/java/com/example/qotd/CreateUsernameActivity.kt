package com.example.qotd

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.example.qotd.ui.theme.QOTDTheme

class CreateUsernameActivity : ComponentActivity() {
    private val auth = FirebaseAuth.getInstance()
    private val db = FirebaseFirestore.getInstance()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            QOTDTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    if (auth.currentUser != null) {
                        SetUsernameScreen(
                            onUsernameSet = { finish() }
                        )
                    } else {
                        Text("Please log in to set a username")
                    }
                }
            }
        }
    }
}

@Composable
fun SetUsernameScreen(
    onUsernameSet: () -> Unit
) {
    var username by remember { mutableStateOf(TextFieldValue("")) }
    var isLoading by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var isUsernameAvailable by remember { mutableStateOf<Boolean?>(null) }
    val currentUser = FirebaseAuth.getInstance().currentUser
    val db = FirebaseFirestore.getInstance()
    val context = LocalContext.current

    // Function to check username availability
    fun checkUsernameAvailability(username: String) {
        if (username.isBlank()) {
            isUsernameAvailable = null
            return
        }

        isLoading = true
        db.collection("users")
            .whereEqualTo("username", username)
            .get()
            .addOnSuccessListener { documents ->
                isUsernameAvailable = documents.isEmpty
                isLoading = false
                if (!documents.isEmpty) {
                    errorMessage = "Username already taken"
                }
            }
            .addOnFailureListener { e ->
                isLoading = false
                errorMessage = "Error checking username: ${e.message}"
                isUsernameAvailable = null
            }
    }

    // Debounce the username check to avoid excessive queries
    LaunchedEffect(username.text) {
        if (username.text.isNotBlank()) {
            kotlinx.coroutines.delay(500) // Wait 500ms after last input
            checkUsernameAvailability(username.text)
        } else {
            isUsernameAvailable = null
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = "Set Your Username",
            style = MaterialTheme.typography.headlineSmall
        )
        Spacer(modifier = Modifier.height(16.dp))
        TextField(
            value = username,
            onValueChange = {
                username = it
                errorMessage = null
            },
            label = { Text("Username") },
            singleLine = true,
            isError = isUsernameAvailable == false,
            supportingText = {
                when (isUsernameAvailable) {
                    true -> Text("Username available!", color = MaterialTheme.colorScheme.primary)
                    false -> Text("Username taken", color = MaterialTheme.colorScheme.error)
                    null -> {}
                }
            }
        )

        errorMessage?.let {
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = it,
                color = MaterialTheme.colorScheme.error
            )
        }

        Spacer(modifier = Modifier.height(16.dp))
        Button(
            onClick = {
                when {
                    username.text.isBlank() -> {
                        errorMessage = "Username cannot be empty"
                    }
                    isUsernameAvailable == false -> {
                        errorMessage = "Please choose a different username"
                    }
                    else -> {
                        isLoading = true
                        currentUser?.let { user ->
                            db.collection("users")
                                .document(user.uid)
                                .set(mapOf("username" to username.text))
                                .addOnSuccessListener {
                                    isLoading = false
                                    onUsernameSet()
                                    val intent = Intent(context, ProfilePicActivity::class.java).apply {
                                        putExtra("isFirstTime", true)
                                    }
                                    context.startActivity(intent)
                                }
                                .addOnFailureListener { e ->
                                    isLoading = false
                                    errorMessage = "Failed to save username: ${e.message}"
                                }
                        }
                    }
                }
            },
            enabled = username.text.isNotBlank() && !isLoading && isUsernameAvailable == true
        ) {
            if (isLoading) {
                CircularProgressIndicator(
                    color = MaterialTheme.colorScheme.onPrimary,
                    modifier = Modifier.size(20.dp)
                )
            } else {
                Text("Save Username")
            }
        }
    }
}