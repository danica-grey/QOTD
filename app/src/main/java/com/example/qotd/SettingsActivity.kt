package com.example.qotd

import android.app.TimePickerDialog
import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.work.*
import com.example.qotd.ui.theme.QOTDTheme
import java.util.*
import java.util.concurrent.TimeUnit
import java.util.Locale
import android.Manifest
import android.os.Build
import android.content.pm.PackageManager
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat

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

@Composable
fun SettingsScreen() {
    RequestNotificationPermissionIfNeeded()
    val context = LocalContext.current
    val prefs = context.getSharedPreferences("qotd_prefs", Context.MODE_PRIVATE)

    var remindersEnabled by remember { mutableStateOf(prefs.getBoolean("remindersEnabled", false)) }
    var reminderHour by remember { mutableIntStateOf(prefs.getInt("reminderHour", 0)) }
    var reminderMinute by remember { mutableIntStateOf(prefs.getInt("reminderMinute", 0)) }


    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(32.dp)
    ) {
        Text("Settings", style = MaterialTheme.typography.headlineMedium)

        Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Text("Notification Settings", style = MaterialTheme.typography.titleLarge)

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text("Enable Daily Reminder")
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

            TextButton(onClick = {
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
            }) {
                val formattedTime =
                    String.format(Locale.getDefault(), "%02d:%02d", reminderHour, reminderMinute)
                Text("Set Reminder Time: $formattedTime")
            }
        }

        Button(
            onClick = {
                val intent = Intent(context, AccountSettingsActivity::class.java)
                context.startActivity(intent)
            },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Account Settings")

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
            Toast.makeText(context, "Notifications permission denied. Change in Android settings", Toast.LENGTH_SHORT).show()
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
