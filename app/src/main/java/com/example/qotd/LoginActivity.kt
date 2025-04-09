package com.example.qotd

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.example.qotd.ui.theme.QOTDTheme
import com.google.firebase.auth.FirebaseAuth
import androidx.compose.ui.platform.LocalContext
import com.google.firebase.firestore.FirebaseFirestore

class LoginActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        setContent {
            QOTDTheme {
                Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                    LoginScreen(modifier = Modifier.padding(innerPadding))
                }
            }
        }
    }
}

@Composable
fun LoginScreen(modifier: Modifier) {
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var message by remember { mutableStateOf("") }
    val auth = FirebaseAuth.getInstance()
    val context = LocalContext.current

    // Login button action
    val loginAction = {
        auth.signInWithEmailAndPassword(email, password)
            .addOnCompleteListener { task ->
                if (task.isSuccessful) {
                    message = "Login Successful!"
                    val userId = auth.currentUser?.uid ?: ""
                    FirebaseFirestore.getInstance()
                        .collection("users")
                        .document(userId)
                        .get()
                        .addOnSuccessListener { document ->
                            if (document.exists() && document.getString("username") != null) {
                                val intent = Intent(context, MainActivity::class.java)
                                context.startActivity(intent)
                            } else {
                                val intent = Intent(context, CreateUsernameActivity::class.java)
                                context.startActivity(intent)
                            }
                        }
                        .addOnFailureListener {
                            message = "Error checking username"
                        }
                } else {
                    message = task.exception?.message ?: "Login Failed"
                }
            }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        TextField(value = email, onValueChange = { email = it }, label = { Text("Email") })
        Spacer(modifier = Modifier.height(8.dp))
        TextField(
            value = password,
            onValueChange = { password = it },
            label = { Text("Password") },
            visualTransformation = PasswordVisualTransformation()
        )
        Spacer(modifier = Modifier.height(16.dp))

        Button(onClick = { loginAction() }) {
            Text("Login")
        }

        Spacer(modifier = Modifier.height(8.dp))

        Button(onClick = {
            auth.createUserWithEmailAndPassword(email, password)
                .addOnCompleteListener { task ->
                    if (task.isSuccessful) {
                        message = "Signup Successful! You can now log in."
                    } else {
                        message = task.exception?.message ?: "Signup Failed"
                    }
                }
        }) {
            Text("Sign Up")
        }

        if (message.isNotEmpty()) {
            Spacer(modifier = Modifier.height(8.dp))
            Text(message, color = MaterialTheme.colorScheme.primary)
        }
    }
}
