package com.example.qotd

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Group
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.List
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.example.qotd.ui.theme.QOTDTheme
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore

class StreaksActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            QOTDTheme {
                Scaffold(
                    bottomBar = { SettingsBottomNavigationBar() }
                ) { padding ->
                    Box(modifier = Modifier
                        .fillMaxSize()
                        .padding(padding)) {
                        StreaksScreen()
                    }
                }
            }
        }
    }
}

@Composable
fun StreaksScreen() {
    val userId = FirebaseAuth.getInstance().currentUser?.uid ?: return
    var streakCount by remember { mutableIntStateOf(0) }
    var achievements by remember { mutableStateOf(emptyMap<String, Boolean>()) }

    LaunchedEffect(userId) {
        val userRef = FirebaseFirestore.getInstance().collection("users").document(userId)
        userRef.get().addOnSuccessListener { doc ->
            val data = doc.data ?: return@addOnSuccessListener
            streakCount = (data["streakCount"] as? Long ?: 0L).toInt()

            achievements = (data["achievements"] as? Map<*, *>)?.mapNotNull { (k, v) ->
                val key = k as? String
                val value = v as? Boolean
                if (key != null && value != null) key to value else null
            }?.toMap() ?: emptyMap()
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        verticalArrangement = Arrangement.Top,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text("Your Streak: $streakCount days", style = MaterialTheme.typography.headlineMedium)

        Spacer(modifier = Modifier.height(32.dp))

        Text("Achievements", style = MaterialTheme.typography.titleLarge)

        Spacer(modifier = Modifier.height(16.dp))

        AchievementItem("🔥 3-Day Answer Streak", achievements["streak3"] == true)
        AchievementItem("🔥 7-Day Answer Streak", achievements["streak7"] == true)
        AchievementItem("🔥 30-Day Answer Streak", achievements["streak30"] == true)
        AchievementItem("🔥 100-Day Answer Streak", achievements["streak100"] == true)
    }
}

@Composable
fun AchievementItem(label: String, unlocked: Boolean) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = if (unlocked) "✅" else "❌",
            style = MaterialTheme.typography.headlineSmall,
            modifier = Modifier.padding(end = 12.dp)
        )
        Text(
            text = label,
            style = MaterialTheme.typography.bodyLarge
        )
    }
}
