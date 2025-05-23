package com.example.qotd

import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import java.text.SimpleDateFormat
import java.util.*
import androidx.compose.ui.graphics.Color

class SplashActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val prefs = getSharedPreferences("qotd_prefs", Context.MODE_PRIVATE)
        val isDarkMode = prefs.getBoolean("dark_mode", false)

        setContent {
            val backgroundColor = if (isDarkMode) Color.Black else Color.White

            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(backgroundColor),  // Dynamically set background
                contentAlignment = Alignment.Center
            ) {
                SplashScreenContent()
            }
        }


        // Firebase auth and redirection logic
        val auth = FirebaseAuth.getInstance()
        val currentUser = auth.currentUser

        if (currentUser == null) {
            startActivity(Intent(this, LoginActivity::class.java))
            finish()
        } else {
            val userId = currentUser.uid
            val db = FirebaseFirestore.getInstance()
            val userRef = db.collection("users").document(userId)

            userRef.get().addOnSuccessListener {
                startActivity(Intent(this, MainActivity::class.java))
                finish()
            }.addOnFailureListener {
                startActivity(Intent(this, LoginActivity::class.java))
                finish()
            }
        }
    }
}

@Composable
fun SplashScreenContent() {
    val image: Painter = painterResource(id = R.drawable.quilcircle) // Add the image from drawable

    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),  // Adjust padding as necessary
        contentAlignment = Alignment.Center
    ) {
        // Set a fixed size for the image to make it smaller (e.g., 150.dp x 150.dp)
        Image(
            painter = image,
            contentDescription = "Splash Image",
            modifier = Modifier.size(150.dp) // Set size to your desired value
        )
    }
}
