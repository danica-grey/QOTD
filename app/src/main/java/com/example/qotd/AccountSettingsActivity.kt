package com.example.qotd

import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.example.qotd.ui.theme.QOTDTheme
import com.google.firebase.auth.FirebaseAuth
import androidx.compose.ui.Alignment

class AccountSettingsActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            QOTDTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    AccountSettingsScreen()
                }
            }
        }
    }
}

@Composable
fun AccountSettingsScreen() {
    val context = LocalContext.current

    Box(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(24.dp)
        ) {
            Text("Account Settings", style = MaterialTheme.typography.headlineMedium)

            Button(
                onClick = {
                    val auth = FirebaseAuth.getInstance()
                    val email = auth.currentUser?.email
                    if (!email.isNullOrEmpty()) {
                        auth.sendPasswordResetEmail(email)
                            .addOnSuccessListener {
                                Toast.makeText(context, "Password reset email sent.", Toast.LENGTH_LONG).show()
                            }
                            .addOnFailureListener {
                                Toast.makeText(context, "Failed to send reset email.", Toast.LENGTH_LONG).show()
                            }
                    } else {
                        Toast.makeText(context, "No email found.", Toast.LENGTH_LONG).show()
                    }
                },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Change Password")
            }

            Button(
                onClick = {
                    FirebaseAuth.getInstance().signOut()
                    val intent = Intent(context, LoginActivity::class.java)
                    context.startActivity(intent)
                    if (context is ComponentActivity) context.finish()
                },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Logout")
            }
        }

        Button(
            onClick = {
                val user = FirebaseAuth.getInstance().currentUser
                user?.delete()
                    ?.addOnSuccessListener {
                        val intent = Intent(context, LoginActivity::class.java)
                        context.startActivity(intent)
                        if (context is ComponentActivity) context.finish()
                    }
                    ?.addOnFailureListener {
                        Toast.makeText(
                            context,
                            "You must sign in again before deleting your account.",
                            Toast.LENGTH_LONG
                        ).show()
                    }
            },
            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(24.dp)
                .fillMaxWidth()
        ) {
            Text("Delete Account")
        }
    }
}