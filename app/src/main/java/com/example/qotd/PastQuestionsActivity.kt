package com.example.qotd

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
import androidx.compose.ui.unit.sp
import com.example.qotd.ui.theme.QOTDTheme
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Settings

class PastQuestionsActivity : ComponentActivity() {

    @OptIn(ExperimentalMaterial3Api::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContent {
            QOTDTheme {
                Scaffold(
                    topBar = {
                        SmallTopAppBar(
                            title = {
                                Text(
                                    text = "Past Questions",
                                    style = MaterialTheme.typography.titleLarge,
                                    modifier = Modifier.padding(start = 8.dp)
                                )
                            },
                            navigationIcon = {
                                IconButton(onClick = {
                                    finish()
                                }) {
                                    Icon(imageVector = Icons.Filled.ArrowBack, contentDescription = "Back")
                                }
                            },
                            actions = {
                                SettingsButton()
                            }
                        )
                    }
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
        finish()
    }
}

@Composable
fun SettingsButton() {
    val context = LocalContext.current

    IconButton(onClick = {
        val intent = Intent(context, SettingsActivity::class.java)
        context.startActivity(intent)
    }) {
        Icon(
            imageVector = Icons.Filled.Settings,
            contentDescription = "Settings",
            modifier = Modifier.size(32.dp)
        )
    }
}

@Composable
fun PastQuestionsScreen() {
    val firestore = FirebaseFirestore.getInstance()
    var pastQuestions by remember { mutableStateOf<List<PastQuestion>>(emptyList()) }

    // Fetch past questions from Firestore
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
