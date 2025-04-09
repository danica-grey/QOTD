package com.example.qotd

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.example.qotd.ui.theme.QOTDTheme
import com.google.firebase.firestore.FirebaseFirestore

class ForgotPasswordActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            QOTDTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    ForgotPasswordScreen()
                }
            }
        }
    }
}

@Composable
fun ForgotPasswordScreen() {
    val firestore = FirebaseFirestore.getInstance()
    var email by remember { mutableStateOf("") }
    var newPassword by remember { mutableStateOf("") }
    var confirmPassword by remember { mutableStateOf("") }
    var message by remember { mutableStateOf("") }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text("Reset Your Password", style = MaterialTheme.typography.headlineSmall)
        Spacer(modifier = Modifier.height(16.dp))

        TextField(value = email, onValueChange = { email = it }, label = { Text("Email") })
        Spacer(modifier = Modifier.height(8.dp))

        TextField(
            value = newPassword,
            onValueChange = { newPassword = it },
            label = { Text("New Password") },
            visualTransformation = PasswordVisualTransformation()
        )
        Spacer(modifier = Modifier.height(8.dp))

        TextField(
            value = confirmPassword,
            onValueChange = { confirmPassword = it },
            label = { Text("Confirm Password") },
            visualTransformation = PasswordVisualTransformation()
        )
        Spacer(modifier = Modifier.height(16.dp))

        Button(onClick = {
            if (newPassword != confirmPassword) {
                message = "Passwords do not match"
                return@Button
            }

            val docRef = firestore.collection("user_credentials").document(email)
            docRef.get().addOnSuccessListener { doc ->
                if (doc.exists()) {
                    // Update the password
                    docRef.update("password", newPassword)
                        .addOnSuccessListener {
                            message = "Password updated successfully!"
                        }
                        .addOnFailureListener {
                            message = "Failed to update password: ${it.message}"
                        }
                } else {
                    message = "Account with this email doesn't exist"
                }
            }.addOnFailureListener {
                message = "Error: ${it.message}"
            }
        }) {
            Text("Reset Password")
        }

        if (message.isNotEmpty()) {
            Spacer(modifier = Modifier.height(16.dp))
            Text(message, color = MaterialTheme.colorScheme.primary)
        }
    }
}