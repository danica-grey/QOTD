// Settings.kt
package com.example.qotd

import android.Manifest
import android.app.TimePickerDialog
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.work.*
import coil.compose.rememberAsyncImagePainter
import com.example.qotd.ui.theme.QOTDTheme
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import java.util.*
import java.util.concurrent.TimeUnit

class SettingsActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            QOTDTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    SettingsScreen()
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen() {
    RequestNotificationPermissionIfNeeded()

    val context = LocalContext.current
    val prefs = context.getSharedPreferences("qotd_prefs", Context.MODE_PRIVATE)

    var remindersEnabled by remember { mutableStateOf(prefs.getBoolean("remindersEnabled", false)) }
    var reminderHour by remember { mutableIntStateOf(prefs.getInt("reminderHour", 0)) }
    var reminderMinute by remember { mutableIntStateOf(prefs.getInt("reminderMinute", 0)) }
    var showNotificationDetails by remember { mutableStateOf(false) }

    val user = FirebaseAuth.getInstance().currentUser
    val userId = user?.uid
    var username by remember { mutableStateOf("Username") }
    var selectedImage by remember { mutableStateOf<Int?>(null) }
    var savedProfilePic by remember { mutableStateOf<String?>(null) }

    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                userId?.let {
                    FirebaseFirestore.getInstance().collection("users").document(it)
                        .get().addOnSuccessListener { doc ->
                            username = doc.getString("username") ?: "Username"
                            savedProfilePic = doc.getString("profilePicture")
                            if (savedProfilePic?.startsWith("drawable/") == true) {
                                val resId = context.resources.getIdentifier(
                                    savedProfilePic!!.removePrefix("drawable/"),
                                    "drawable", context.packageName
                                )
                                selectedImage = if (resId != 0) resId else null
                            } else {
                                selectedImage = null
                            }
                        }
                }
            }
        }

        val lifecycle = lifecycleOwner.lifecycle
        lifecycle.addObserver(observer)
        onDispose { lifecycle.removeObserver(observer) }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text("Settings", style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(start = 8.dp))
                },
                navigationIcon = {
                    IconButton(onClick = { if (context is ComponentActivity) context.finish() }) {
                        Icon(Icons.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        },
        bottomBar = { SettingsBottomNavigationBar() }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(24.dp)
        ) {
            // Profile Pic
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable {
                        context.startActivity(Intent(context, ProfilePicActivity::class.java))
                    },
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(72.dp)
                        .padding(end = 16.dp),
                    contentAlignment = Alignment.Center
                ) {
                    val imageSize = if (selectedImage != null || (savedProfilePic?.startsWith("drawable/") == true)) 88.dp else 60.dp
                    val imagePainter: Painter? = when {
                        selectedImage != null -> painterResource(id = selectedImage!!)
                        savedProfilePic?.startsWith("http") == true -> rememberAsyncImagePainter(model = savedProfilePic)
                        else -> null
                    }

                    if (imagePainter != null) {
                        Image(
                            painter = imagePainter,
                            contentDescription = "Profile Picture",
                            modifier = Modifier.size(imageSize).clip(CircleShape),
                            contentScale = ContentScale.Crop
                        )
                    } else {
                        Icon(Icons.Default.AccountCircle, contentDescription = null, modifier = Modifier.fillMaxSize().clip(CircleShape))
                    }

                    Icon(
                        Icons.Default.Edit,
                        contentDescription = "Edit",
                        tint = Color.White,
                        modifier = Modifier
                            .align(Alignment.BottomEnd)
                            .size(20.dp)
                            .background(MaterialTheme.colorScheme.primary, shape = CircleShape)
                            .padding(2.dp)
                    )
                }

                Text(username, style = MaterialTheme.typography.titleMedium.copy(fontSize = 20.sp))
            }

            // Notifications
            Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { showNotificationDetails = !showNotificationDetails }
                        .padding(vertical = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Filled.Notifications, contentDescription = null, modifier = Modifier.size(24.dp))
                        Spacer(modifier = Modifier.width(12.dp))
                        Text("Notifications", style = MaterialTheme.typography.bodyLarge.copy(fontSize = 20.sp))
                    }
                    Text(if (showNotificationDetails) "˅" else ">", style = MaterialTheme.typography.titleMedium)
                }

                AnimatedVisibility(showNotificationDetails) {
                    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(start = 8.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("Enable Daily Reminder", style = MaterialTheme.typography.bodyLarge)
                            Switch(
                                checked = remindersEnabled,
                                onCheckedChange = {
                                    remindersEnabled = it
                                    prefs.edit().putBoolean("remindersEnabled", it).apply()
                                    if (it) scheduleReminder(context, reminderHour, reminderMinute)
                                    else cancelReminder(context)
                                }
                            )
                        }

                        val formattedTime = String.format(Locale.getDefault(), "%02d:%02d", reminderHour, reminderMinute)
                        Text(
                            "Set Reminder Time: $formattedTime",
                            modifier = Modifier.clickable {
                                TimePickerDialog(context, { _, hour, minute ->
                                    reminderHour = hour
                                    reminderMinute = minute
                                    prefs.edit().putInt("reminderHour", hour).putInt("reminderMinute", minute).apply()
                                    if (remindersEnabled) {
                                        cancelReminder(context)
                                        scheduleReminder(context, hour, minute)
                                    }
                                }, reminderHour, reminderMinute, false).show()
                            },
                            style = MaterialTheme.typography.bodyLarge.copy(color = MaterialTheme.colorScheme.primary)
                        )
                    }
                }
            }

            // Reset Password
            SettingsOption("Reset Password", Icons.Default.Email) {
                val email = FirebaseAuth.getInstance().currentUser?.email
                if (!email.isNullOrEmpty()) {
                    FirebaseAuth.getInstance().sendPasswordResetEmail(email)
                        .addOnSuccessListener {
                            Toast.makeText(context, "Password reset email sent.", Toast.LENGTH_LONG).show()
                        }
                        .addOnFailureListener {
                            Toast.makeText(context, "Failed to send reset email.", Toast.LENGTH_LONG).show()
                        }
                } else {
                    Toast.makeText(context, "No email found.", Toast.LENGTH_LONG).show()
                }
            }

            // View Streaks
            SettingsOption("View Streaks", Icons.Default.Face) {
                context.startActivity(Intent(context, StreaksActivity::class.java))
            }

            // Logout
            SettingsOption("Logout", Icons.Default.ExitToApp, isDestructive = true) {
                FirebaseAuth.getInstance().signOut()
                context.startActivity(Intent(context, LoginActivity::class.java))
                if (context is ComponentActivity) context.finish()
            }

            // Delete Account
            Button(
                onClick = {
                    FirebaseAuth.getInstance().currentUser?.delete()?.addOnSuccessListener {
                        context.startActivity(Intent(context, LoginActivity::class.java))
                        if (context is ComponentActivity) context.finish()
                    }?.addOnFailureListener {
                        Toast.makeText(context, "You must sign in again before deleting your account.", Toast.LENGTH_LONG).show()
                    }
                },
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Delete Account", style = MaterialTheme.typography.bodyLarge.copy(fontSize = 20.sp))
            }
        }
    }
}

@Composable
fun SettingsOption(text: String, icon: ImageVector, isDestructive: Boolean = false, onClick: () -> Unit) {
    val color = if (isDestructive) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, contentDescription = null, tint = color, modifier = Modifier.size(24.dp))
        Spacer(modifier = Modifier.width(12.dp))
        Text(text, style = MaterialTheme.typography.bodyLarge.copy(fontSize = 20.sp, color = color))
    }
}

fun scheduleReminder(context: Context, hour: Int, minute: Int) {
    val now = Calendar.getInstance()
    val target = now.clone() as Calendar
    target.set(Calendar.HOUR_OF_DAY, hour)
    target.set(Calendar.MINUTE, minute)
    target.set(Calendar.SECOND, 0)
    target.set(Calendar.MILLISECOND, 0)

    if (target.before(now)) {
        target.add(Calendar.DAY_OF_MONTH, 1)
    }

    val delay = target.timeInMillis - now.timeInMillis
    val request = OneTimeWorkRequestBuilder<DailyReminderWorker>()
        .setInitialDelay(delay, TimeUnit.MILLISECONDS)
        .build()

    WorkManager.getInstance(context).enqueueUniqueWork("dailyReminder", ExistingWorkPolicy.REPLACE, request)
}

fun cancelReminder(context: Context) {
    WorkManager.getInstance(context).cancelUniqueWork("dailyReminder")
}

@Composable
fun RequestNotificationPermissionIfNeeded() {
    val context = LocalContext.current
    val launcher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (!isGranted) {
            Toast.makeText(context, "Notifications permission denied.", Toast.LENGTH_SHORT).show()
        }
    }

    LaunchedEffect(Unit) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val permission = Manifest.permission.POST_NOTIFICATIONS
            val granted = ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED
            if (!granted) launcher.launch(permission)
        }
    }
}

@Composable
fun SettingsBottomNavigationBar() {
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
                Icon(Icons.Default.Home, contentDescription = "Home", modifier = Modifier.size(32.dp), tint = MaterialTheme.colorScheme.onPrimary)
            }

            IconButton(onClick = {
                context.startActivity(Intent(context, AddFriendActivity::class.java))
            }) {
                Icon(Icons.Default.Group, contentDescription = "Friends", modifier = Modifier.size(32.dp), tint = MaterialTheme.colorScheme.onPrimary)
            }

            IconButton(onClick = {
                context.startActivity(Intent(context, UserAnswersActivity::class.java))
            }) {
                Icon(Icons.Default.List, contentDescription = "Answers", modifier = Modifier.size(32.dp), tint = MaterialTheme.colorScheme.onPrimary)
            }

            IconButton(onClick = {}) {
                Icon(Icons.Default.Settings, contentDescription = "Settings", modifier = Modifier.size(32.dp), tint = MaterialTheme.colorScheme.onPrimary)
            }
        }
    }
}
