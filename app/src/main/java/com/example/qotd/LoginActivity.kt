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
import java.time.LocalDate

class LoginActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        setContent {
            QOTDTheme {
                Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                    LoginScreen(modifier = Modifier.padding(innerPadding), activity = this)
                }
            }
        }
    }
}

@Composable
fun LoginScreen(modifier: Modifier, activity: LoginActivity) {
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var message by remember { mutableStateOf("") }
    val auth = FirebaseAuth.getInstance()
    val context = LocalContext.current

    // Login button action
    val loginAction = {
        // Validate if the email and password fields are not empty
        if (email.isEmpty() || password.isEmpty()) {
            message = "Please fill in both fields."
        } else {
            auth.signInWithEmailAndPassword(email, password)
                .addOnCompleteListener { task ->
                    if (task.isSuccessful) {
                        message = "Login Successful!"
                        val userId = FirebaseAuth.getInstance().currentUser?.uid

                        if (userId != null) {
                            val questionDate = LocalDate.now().toString() // Today's date in format yyyy-MM-dd

                            // Check if the user has answered today's question
                            // (Note: removed the firestore-related part for now)
                            val intent = Intent(context, MainActivity::class.java)
                            context.startActivity(intent)
                            activity.finish() // Prevent back navigation to Login screen
                        }
                    } else {
                        message = task.exception?.message ?: "Login Failed"
                    }
                }
        }
    }

    // Sign up button action
    val signUpAction = {
        // Validate if the email and password fields are not empty
        if (email.isEmpty() || password.isEmpty()) {
            message = "Please fill in both fields."
        } else {
            auth.createUserWithEmailAndPassword(email, password)
                .addOnCompleteListener { task ->
                    if (task.isSuccessful) {
                        message = "Signup Successful! You can now log in."
                    } else {
                        message = task.exception?.message ?: "Signup Failed"
                    }
                }
        }
    }

    Column(
        modifier = modifier
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

        Button(onClick = { signUpAction() }) {
            Text("Sign Up")
        }

        TextButton(onClick = {
            val intent = Intent(context, ForgotPasswordActivity::class.java)
            context.startActivity(intent)
        }) {
            Text("Forgot Password?")
        }

        if (message.isNotEmpty()) {
            Spacer(modifier = Modifier.height(8.dp))
            Text(message, color = MaterialTheme.colorScheme.primary)
        }
    }
}