package com.example.qotd

import android.app.NotificationManager
import android.content.Context
import androidx.core.app.NotificationCompat
import androidx.work.*
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import java.util.*
import java.util.concurrent.TimeUnit
import android.app.PendingIntent
import android.content.Intent

class DailyReminderWorker(context: Context, params: WorkerParameters) : Worker(context, params) {
    override fun doWork(): Result {
        val userId = FirebaseAuth.getInstance().currentUser?.uid ?: return Result.success()

        FirebaseFirestore.getInstance().collection("users")
            .document(userId)
            .get()
            .addOnSuccessListener { document ->
                val answeredToday = document.getBoolean("answeredToday") ?: false
                if (!answeredToday) {
                    showNotification()
                }
            }

        scheduleNextReminder(applicationContext)
        return Result.success()
    }

    private fun showNotification() {
        val manager = applicationContext.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val channelId = "qotd_reminder"

        val intent = Intent(applicationContext, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        }

        val pendingIntent = PendingIntent.getActivity(
            applicationContext,
            0,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(applicationContext, channelId)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle("QOTD")
            .setContentText("You haven’t answered today’s question!")
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .build()

        manager.notify(1, notification)
    }

    private fun scheduleNextReminder(context: Context) {
        val prefs = context.getSharedPreferences("qotd_prefs", Context.MODE_PRIVATE)
        val hour = prefs.getInt("reminderHour", 0)
        val minute = prefs.getInt("reminderMinute", 0)

        val now = Calendar.getInstance()
        val next = now.clone() as Calendar
        next.set(Calendar.HOUR_OF_DAY, hour)
        next.set(Calendar.MINUTE, minute)
        next.set(Calendar.SECOND, 0)
        next.set(Calendar.MILLISECOND, 0)
        next.add(Calendar.DAY_OF_MONTH, 1)

        val delay = next.timeInMillis - now.timeInMillis

        val request = OneTimeWorkRequestBuilder<DailyReminderWorker>()
            .setInitialDelay(delay, TimeUnit.MILLISECONDS)
            .build()

        WorkManager.getInstance(context).enqueueUniqueWork(
            "dailyReminder",
            ExistingWorkPolicy.REPLACE,
            request
        )
    }
}