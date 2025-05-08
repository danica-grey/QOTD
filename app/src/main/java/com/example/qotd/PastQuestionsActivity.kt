package com.example.qotd

import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
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
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Group
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.List
import androidx.compose.material.icons.filled.Settings
import androidx.compose.ui.graphics.Color
import kotlinx.coroutines.tasks.await

class PastQuestionsActivity : ComponentActivity() {

    @OptIn(ExperimentalMaterial3Api::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val prefs = getSharedPreferences("qotd_prefs", Context.MODE_PRIVATE)
        val isDarkMode = prefs.getBoolean("dark_mode", false)

        setContent {
            QOTDTheme(darkTheme = isDarkMode) {
                Surface(modifier = Modifier.fillMaxSize()) {
                    SettingsScreen()
                }

                Scaffold(
                    topBar = {
                        TopAppBar(
                            title = {
                                Text(
                                    text = "Past Questions",
                                    style = MaterialTheme.typography.titleLarge,
                                    modifier = Modifier.padding(start = 8.dp)
                                )
                            },
                            navigationIcon = {
                                IconButton(onClick = {
                                    onBackPressed()
                                }) {
                                    Icon(imageVector = Icons.Filled.ArrowBack, contentDescription = "Back")
                                }
                            },
                        )
                    },
                    bottomBar = { PastBottomNavigationBar() },
                ) { innerPadding ->
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(innerPadding)
                    ) {
                        PastQuestionsScreen()
                    }
                }
            }
        }
    }

    override fun onBackPressed() {
        super.onBackPressed()
    }
}


@Composable
fun PastQuestionsScreen() {
    val firestore = FirebaseFirestore.getInstance()
    var pastQuestions by remember { mutableStateOf<List<PastQuestion>>(emptyList()) }

    LaunchedEffect(Unit) {
        firestore.collection("dailyQuestions")
            .get()
            .addOnSuccessListener { snapshot ->
                pastQuestions = snapshot.documents.mapNotNull { document ->
                    val date = document.id
                    val question = document.getString("question") ?: ""
                    PastQuestion(date, question)
                }.sortedByDescending { it.date }
            }
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        items(pastQuestions) { pastQuestion ->
            PastQuestionItem(pastQuestion)
        }
    }
}

@Composable
fun PastQuestionItem(pastQuestion: PastQuestion) {
    val firestore = FirebaseFirestore.getInstance()
    val context = LocalContext.current

    val isToday = pastQuestion.date == LocalDate.now().toString()

    val formattedDate = try {
        val date = LocalDate.parse(pastQuestion.date)
        date.format(DateTimeFormatter.ofPattern("MMMM d, yyyy"))
    } catch (e: Exception) {
        "Invalid Date"
    }

    var answersCount by remember { mutableStateOf(0) }

    LaunchedEffect(pastQuestion) {
        firestore.collection("dailyAnswer")
            .whereEqualTo("questionDate", pastQuestion.date)
            .get()
            .addOnSuccessListener { snapshot ->
                answersCount = snapshot.size()
            }
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(8.dp)
            .clickable {
                val intent = Intent(context, UserAnswersActivity::class.java)
                intent.putExtra("questionDate", pastQuestion.date)
                intent.putExtra("questionText", pastQuestion.question)
                context.startActivity(intent)
            },
        elevation = CardDefaults.cardElevation(4.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = formattedDate + if (isToday) " (Today)" else "",
                style = MaterialTheme.typography.bodyLarge.copy(
                    fontWeight = if (isToday) FontWeight.Bold else FontWeight.Normal,
                    color = if (isToday) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onBackground
                )
            )

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = if (isToday) { pastQuestion.question } else pastQuestion.question,
                fontSize = MaterialTheme.typography.bodyLarge.fontSize * 1.2f,
                fontWeight = if (isToday) FontWeight.SemiBold else FontWeight.Normal
            )

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = "$answersCount Answers",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.primary
            )
        }
    }
}

data class PastQuestion(val date: String, val question: String)

@Composable
fun PastBottomNavigationBar() {
    val context = LocalContext.current
    BottomAppBar(
        modifier = Modifier.fillMaxWidth().height(88.dp),
        containerColor = MaterialTheme.colorScheme.primary
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceAround,
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = {
                context.startActivity(Intent(context, MainActivity::class.java))
            }) {
                Icon(Icons.Default.Home, contentDescription = "Home", modifier = Modifier.size(32.dp), tint = Color.White)
            }
            IconButton(onClick = {
                context.startActivity(Intent(context, AddFriendActivity::class.java))
            }) {
                Icon(Icons.Default.Group, contentDescription = "Friends", modifier = Modifier.size(32.dp), tint = Color.White)
            }
            IconButton(onClick = {
                context.startActivity(Intent(context, UserAnswersActivity::class.java))
            }) {
                Icon(Icons.Default.List, contentDescription = "Answers", modifier = Modifier.size(32.dp), tint = Color.White)
            }
            IconButton(onClick = {
                val intent = Intent(context, SettingsActivity::class.java)
                (context as? ComponentActivity)?.startActivityForResult(intent, 100)
            }) {
                Icon(Icons.Default.Settings, contentDescription = "Settings", modifier = Modifier.size(32.dp), tint = Color.White)
            }
        }
    }
}
