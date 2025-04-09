package com.example.qotd

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.Image
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

class SplashActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Set the splash screen content using Jetpack Compose
        setContent {
            SplashScreenContent()
        }

        // Logic to check user authentication and answer status
        val auth = FirebaseAuth.getInstance()
        val currentUser = auth.currentUser

        if (currentUser == null) {
            // Not logged in, go to Login screen
            startActivity(Intent(this, LoginActivity::class.java))
            finish()
        } else {
            val userId = currentUser.uid
            val db = FirebaseFirestore.getInstance()
            val userRef = db.collection("users").document(userId)

            userRef.get().addOnSuccessListener { document ->/* disable disabling back button for now
                val answeredToday = document.getBoolean("answeredToday") ?: false
                val lastAnsweredDate = document.getString("lastAnsweredDate")
                val todayDate = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())

                if (answeredToday && lastAnsweredDate == todayDate) {
                    // User already answered today, go to Answers screen
                    startActivity(Intent(this, UserAnswersActivity::class.java))
                } else {
                    // Not answered yet, go to QOTD screen
                    startActivity(Intent(this, MainActivity::class.java))
                }*/

                startActivity(Intent(this, MainActivity::class.java))

                finish()
            }.addOnFailureListener {
                // Couldn’t load user data — send to Login as fallback
                startActivity(Intent(this, LoginActivity::class.java))
                finish()
            }
        }
    }
}

@Composable
fun SplashScreenContent() {
    val image: Painter = painterResource(id = R.drawable.splash_image) // Add the image from drawable

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
