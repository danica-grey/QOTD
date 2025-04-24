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
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.work.*
import com.example.qotd.ui.theme.QOTDTheme
import com.google.firebase.auth.FirebaseAuth
import java.util.*
import java.util.concurrent.TimeUnit
import java.util.Locale
import com.google.firebase.firestore.FirebaseFirestore
import androidx.compose.ui.res.painterResource
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.painter.Painter
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import coil.compose.rememberAsyncImagePainter



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

    LaunchedEffect(userId) {
        userId?.let {
            FirebaseFirestore.getInstance().collection("users").document(it)
                .get().addOnSuccessListener { doc ->
                    username = doc.getString("username") ?: "Username"
                    savedProfilePic = doc.getString("profilePicture")

                    if (savedProfilePic != null && savedProfilePic!!.startsWith("drawable/")) {
                        val resId = context.resources.getIdentifier(
                            savedProfilePic!!.removePrefix("drawable/"),
                            "drawable",
                            context.packageName
                        )
                        selectedImage = if (resId != 0) resId else null
                    } else {
                        selectedImage = null
                    }
                }
        }
    }

    // Updates pfp immediately so that user doesn't have to relaunch settings screen
    val lifecycleOwner = LocalLifecycleOwner.current

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                userId?.let {
                    FirebaseFirestore.getInstance().collection("users").document(it)
                        .get().addOnSuccessListener { doc ->
                            username = doc.getString("username") ?: "Username"
                            savedProfilePic = doc.getString("profilePicture")

                            if (savedProfilePic != null && savedProfilePic!!.startsWith("drawable/")) {
                                val resId = context.resources.getIdentifier(
                                    savedProfilePic!!.removePrefix("drawable/"),
                                    "drawable",
                                    context.packageName
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

        onDispose {
            lifecycle.removeObserver(observer)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Settings", style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(start = 8.dp)) },
                navigationIcon = {
                    IconButton(onClick = {
                        if (context is ComponentActivity) context.finish()
                    }) {
                        Icon(Icons.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        },
        bottomBar = { SettingsBottomNavigationBar() }
    ) { paddingValues ->
        Box(modifier = Modifier.fillMaxSize().padding(paddingValues)) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(24.dp),
                verticalArrangement = Arrangement.spacedBy(24.dp)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable {
                            val intent = Intent(context, ProfilePicActivity::class.java)
                            context.startActivity(intent)
                        },
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(72.dp)
                            .padding(end = 16.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        val isDrawable = selectedImage != null || (savedProfilePic?.startsWith("drawable/") == true)
                        val imageSize = if (isDrawable) 88.dp else 60.dp

                        val imagePainter: Painter? = when {
                            selectedImage != null -> painterResource(id = selectedImage!!)
                            savedProfilePic != null && savedProfilePic!!.startsWith("http") -> rememberAsyncImagePainter(model = savedProfilePic)
                            else -> null
                        }

                        if (imagePainter != null) {
                            Image(
                                painter = imagePainter,
                                contentDescription = "Profile Picture",
                                modifier = Modifier
                                    .size(imageSize)
                                    .clip(CircleShape),
                                contentScale = ContentScale.Crop
                            )

                        } else {
                            Icon(
                                imageVector = Icons.Default.AccountCircle,
                                contentDescription = "Default Profile Icon",
                                modifier = Modifier
                                    .fillMaxSize()
                                    .clip(CircleShape)
                            )
                        }

                        Icon(
                            imageVector = Icons.Default.Edit,
                            contentDescription = "Edit",
                            tint = Color.White,
                            modifier = Modifier
                                .align(Alignment.BottomEnd)
                                .size(20.dp)
                                .background(MaterialTheme.colorScheme.primary, shape = CircleShape)
                                .padding(2.dp)
                        )
                    }
                    Text(
                        text = username,
                        style = MaterialTheme.typography.titleMedium.copy(fontSize = 20.sp),
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }

                // Notifications Section
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
                            Icon(
                                imageVector = Icons.Filled.Notifications,
                                contentDescription = "Notifications",
                                modifier = Modifier.size(24.dp)
                            )
                            Spacer(modifier = Modifier.width(12.dp))
                            Text(
                                "Notifications",
                                style = MaterialTheme.typography.bodyLarge.copy(fontSize = 20.sp)
                            )
                        }
                        Text(
                            text = if (showNotificationDetails) "˅" else ">",
                            style = MaterialTheme.typography.titleMedium,
                            modifier = Modifier.size(24.dp)
                        )
                    }

                    AnimatedVisibility(visible = showNotificationDetails) {
                        Surface(
                            modifier = Modifier
                                .fillMaxWidth()
                                .shadow(4.dp, shape = MaterialTheme.shapes.medium)
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(16.dp),
                                verticalArrangement = Arrangement.spacedBy(16.dp)
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(start = 8.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        "Enable Daily Reminder",
                                        style = MaterialTheme.typography.bodyLarge.copy(fontSize = 18.sp)
                                    )
                                    Switch(
                                        checked = remindersEnabled,
                                        onCheckedChange = {
                                            remindersEnabled = it
                                            prefs.edit().putBoolean("remindersEnabled", it).apply()
                                            if (it) {
                                                scheduleReminder(context, reminderHour, reminderMinute)
                                            } else {
                                                cancelReminder(context)
                                            }
                                        }
                                    )
                                }

                                val formattedTime = String.format(Locale.getDefault(), "%02d:%02d", reminderHour, reminderMinute)

                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(start = 8.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        "Set Reminder Time: $formattedTime",
                                        style = MaterialTheme.typography.bodyLarge.copy(
                                            fontSize = 18.sp, color = MaterialTheme.colorScheme.primary
                                        ),
                                        modifier = Modifier.clickable {
                                            TimePickerDialog(
                                                context,
                                                { _, hour, minute ->
                                                    reminderHour = hour
                                                    reminderMinute = minute
                                                    prefs.edit().putInt("reminderHour", hour)
                                                        .putInt("reminderMinute", minute)
                                                        .apply()

                                                    if (remindersEnabled) {
                                                        cancelReminder(context)
                                                        scheduleReminder(context, hour, minute)
                                                    }
                                                },
                                                reminderHour,
                                                reminderMinute,
                                                false
                                            ).show()
                                        }
                                    )
                                }
                            }
                        }
                    }
                }

                // Reset Password
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable {
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
                        }
                        .padding(vertical = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.Email,
                            contentDescription = "Change Password",
                            modifier = Modifier.size(24.dp)
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Text(
                            "Reset Password",
                            style = MaterialTheme.typography.bodyLarge.copy(fontSize = 20.sp)
                        )
                    }
                }

                // Logout
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable {
                            FirebaseAuth.getInstance().signOut()
                            val intent = Intent(context, LoginActivity::class.java)
                            context.startActivity(intent)
                            if (context is ComponentActivity) context.finish()
                        }
                        .padding(vertical = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.ExitToApp,
                            contentDescription = "Logout",
                            tint = MaterialTheme.colorScheme.error,
                            modifier = Modifier.size(24.dp)
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Text(
                            "Logout",
                            style = MaterialTheme.typography.bodyLarge.copy(
                                fontSize = 20.sp,
                                color = MaterialTheme.colorScheme.error
                            )
                        )
                    }
                }
            }

            // Delete Account
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
                Text(
                    "Delete Account",
                    style = MaterialTheme.typography.bodyLarge.copy(fontSize = 20.sp)
                )
            }
        }
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

    WorkManager.getInstance(context).enqueueUniqueWork(
        "dailyReminder",
        ExistingWorkPolicy.REPLACE,
        request
    )
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
            Toast.makeText(
                context,
                "Notifications permission denied. Change in Android settings",
                Toast.LENGTH_SHORT
            ).show()
        }
    }

    LaunchedEffect(Unit) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val permission = Manifest.permission.POST_NOTIFICATIONS
            val granted = ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED
            if (!granted) {
                launcher.launch(permission)
            }
        }
    }
}

@Composable
fun SettingsBottomNavigationBar() {
    val context = LocalContext.current

    BottomAppBar(
        modifier = Modifier
            .fillMaxWidth()
            .height(88.dp),
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
                Icon(
                    Icons.Default.Home,
                    contentDescription = "Home",
                    modifier = Modifier.size(32.dp),
                    tint = MaterialTheme.colorScheme.onPrimary
                )
            }

            IconButton(onClick = {
                context.startActivity(Intent(context, AddFriendActivity::class.java))
            }) {
                Icon(
                    Icons.Default.Group,
                    contentDescription = "Friends",
                    modifier = Modifier.size(32.dp),
                    tint = MaterialTheme.colorScheme.onPrimary
                )
            }

            IconButton(onClick = {
                context.startActivity(Intent(context, UserAnswersActivity::class.java))
            }) {
                Icon(
                    Icons.Default.List,
                    contentDescription = "Answers",
                    modifier = Modifier.size(32.dp),
                    tint = MaterialTheme.colorScheme.onPrimary
                )
            }

            IconButton(onClick = {
            }) {
                Icon(
                    Icons.Default.Settings,
                    contentDescription = "Settings",
                    modifier = Modifier.size(32.dp),
                    tint = MaterialTheme.colorScheme.onPrimary
                )
            }
        }
    }
}
