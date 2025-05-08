package com.example.qotd

import android.app.Activity.RESULT_OK
import android.content.Context
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.qotd.ui.theme.QOTDTheme
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore

class StreaksActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            val prefs = getSharedPreferences("qotd_prefs", Context.MODE_PRIVATE)
            val isDarkMode = prefs.getBoolean("dark_mode", false)

            QOTDTheme(darkTheme = isDarkMode) {
                StreaksScreen()
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StreaksScreen() {
    val context = LocalContext.current
    val userId = FirebaseAuth.getInstance().currentUser?.uid ?: return
    var streakCount by remember { mutableIntStateOf(0) }
    var longestStreak by remember { mutableIntStateOf(0) }
    var achievements by remember { mutableStateOf(emptyMap<String, Boolean>()) }

    LaunchedEffect(userId) {
        val userRef = FirebaseFirestore.getInstance().collection("users").document(userId)
        userRef.get().addOnSuccessListener { doc ->
            val data = doc.data ?: return@addOnSuccessListener
            streakCount = (data["streakCount"] as? Long ?: 0L).toInt()
            longestStreak = (data["longestStreak"] as? Long ?: 0L).toInt()

            achievements = (data["achievements"] as? Map<*, *>)?.mapNotNull { (k, v) ->
                val key = k as? String
                val value = v as? Boolean
                if (key != null && value != null) key to value else null
            }?.toMap() ?: emptyMap()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Streaks") },
                navigationIcon = {
                    IconButton(onClick = {
                        (context as? ComponentActivity)?.apply {
                            setResult(RESULT_OK)
                            finish()
                        }
                    }) {
                        Icon(Icons.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        },
        bottomBar = { SettingsBottomNavigationBar() }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(modifier = Modifier.height(24.dp))

            Text("🔥", style = MaterialTheme.typography.displayLarge)

            Spacer(modifier = Modifier.height(12.dp))

            Text(
                text = "$streakCount",
                style = MaterialTheme.typography.displayLarge.copy(fontWeight = FontWeight.Bold)
            )

            Text(
                text = if (streakCount == 1) "Day Streak" else "Day Streak!",
                style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold)
            )

            Spacer(modifier = Modifier.height(16.dp))

            Text(
                text = "Longest Streak: $longestStreak days",
                style = MaterialTheme.typography.bodyLarge
            )

            Spacer(modifier = Modifier.height(32.dp))

            Divider(thickness = 1.dp)
            Spacer(modifier = Modifier.height(24.dp))

            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(text = "🏆", style = MaterialTheme.typography.titleLarge)
                Spacer(modifier = Modifier.width(8.dp))
                Text("Achievements", style = MaterialTheme.typography.titleLarge)
            }

            Spacer(modifier = Modifier.height(16.dp))

            AchievementItem("🔥 3-Day Answer Streak", achievements["streak3"] == true)
            AchievementItem("🔥 7-Day Answer Streak", achievements["streak7"] == true)
            AchievementItem("🔥 30-Day Answer Streak", achievements["streak30"] == true)
            AchievementItem("🔥 100-Day Answer Streak", achievements["streak100"] == true)
        }
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
