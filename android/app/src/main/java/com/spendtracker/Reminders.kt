package com.spendtracker

import android.Manifest
import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.work.CoroutineWorker
import androidx.work.Data
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.work.Constraints
import java.time.Duration
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.YearMonth

/** What is still waiting to be paid today. */
data class Pending(val expenses: List<String>, val bills: List<String>) {
    val isEmpty: Boolean get() = expenses.isEmpty() && bills.isEmpty()
}

object Reminders {
    private const val CHANNEL = "pending_bills_v2"   // channel settings are fixed once created, so a new id was needed to add vibration
    private const val WORK = "daily_reminder"
    private const val NOTIF_ID = 1001
    const val START_DAY = 5

    fun prefs(c: Context) = c.getSharedPreferences("spend_tracker", Context.MODE_PRIVATE)

    fun enabled(c: Context) = prefs(c).getBoolean("remind_on", false)
    fun hour(c: Context) = prefs(c).getInt("remind_hour", 9)

    fun setEnabled(c: Context, on: Boolean) {
        prefs(c).edit().putBoolean("remind_on", on).apply()
        schedule(c)
    }

    fun setHour(c: Context, h: Int) {
        prefs(c).edit().putInt("remind_hour", h.coerceIn(0, 23)).apply()
        schedule(c)
    }

    private fun alarmIntent(c: Context): PendingIntent = PendingIntent.getBroadcast(
        c, 7, Intent(c, ReminderAlarmReceiver::class.java),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
    )

    fun canExact(c: Context): Boolean =
        Build.VERSION.SDK_INT < 31 || (c.getSystemService(Context.ALARM_SERVICE) as AlarmManager).canScheduleExactAlarms()

    /** Sets (or cancels) the alarm for the next occurrence of the chosen hour. Called at start, on boot and after each alarm. */
    fun schedule(c: Context) {
        val am = c.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        WorkManager.getInstance(c).cancelUniqueWork(WORK)   // older builds used a 24h periodic job
        val pi = alarmIntent(c)
        if (!enabled(c)) {
            am.cancel(pi)
            return
        }
        val now = LocalDateTime.now()
        var next = now.toLocalDate().atTime(hour(c), 0)
        if (!next.isAfter(now)) next = next.plusDays(1)
        val at = next.atZone(java.time.ZoneId.systemDefault()).toInstant().toEpochMilli()
        if (canExact(c)) am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pi)
        else am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pi)
    }

    /** Runs the daily check now (called by the alarm). */
    fun runCheck(c: Context) {
        val req = OneTimeWorkRequestBuilder<ReminderWorker>()
            .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
            .build()
        WorkManager.getInstance(c).enqueue(req)
    }

    fun sendTestNow(c: Context) {
        val req = OneTimeWorkRequestBuilder<ReminderWorker>()
            .setInputData(Data.Builder().putBoolean("test", true).build())
            .build()
        WorkManager.getInstance(c).enqueue(req)
    }

    fun canNotify(c: Context): Boolean =
        Build.VERSION.SDK_INT < 33 ||
            ContextCompat.checkSelfPermission(c, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED

    /** First number in text like "3rd" or "25". */
    fun dayOf(text: String): Int? = Regex("\\d{1,2}").find(text)?.value?.toIntOrNull()?.takeIf { it in 1..31 }

    private fun norm(s: String) = s.lowercase().filter { it.isLetterOrDigit() }

    /**
     * Pending expenses always count. A card bill counts only from its bill-generation day of this
     * month onwards. If the card's bill date is missing or can't be read, it is reminded anyway.
     */
    fun pending(state: SheetState, today: LocalDate, skipped: Set<String> = emptySet()): Pending {
        val expenses = state.expenses.filter { !it.isPaid && Skips.key(state.month, it.utility) !in skipped }.map { it.utility.trim() }.filter { it.isNotEmpty() }
        val bills = state.ccBills.filter { !it.isPaid }.filter { bill ->
            val n = norm(bill.card)
            val due = state.dues.firstOrNull {
                val d = norm(it.card)
                d.isNotEmpty() && n.isNotEmpty() && (d == n || d.contains(n) || n.contains(d))
            }
            val day = due?.let { dayOf(it.billDate) }
            if (day == null) true else today.dayOfMonth >= minOf(day, YearMonth.from(today).lengthOfMonth())
        }.map { it.card.trim() }.filter { it.isNotEmpty() }
        return Pending(expenses, bills)
    }

    private fun ensureChannel(c: Context) {
        if (Build.VERSION.SDK_INT >= 26) {
            val nm = c.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            nm.deleteNotificationChannel("pending_bills")
            val ch = NotificationChannel(CHANNEL, "Pending bills", NotificationManager.IMPORTANCE_HIGH).apply {
                enableVibration(true)
                vibrationPattern = longArrayOf(0, 400, 200, 400)
                enableLights(true)
            }
            nm.createNotificationChannel(ch)
        }
    }

    fun notify(c: Context, p: Pending, testIfEmpty: Boolean) {
        if (!canNotify(c)) return
        if (p.isEmpty && !testIfEmpty) return
        ensureChannel(c)
        val title: String
        val body: String
        if (p.isEmpty) {
            title = "All caught up"
            body = "Nothing pending right now."
        } else {
            val n = p.expenses.size + p.bills.size
            title = "$n pending payment" + if (n == 1) "" else "s"
            val parts = mutableListOf<String>()
            if (p.expenses.isNotEmpty()) parts += "Expenses: " + p.expenses.joinToString(", ")
            if (p.bills.isNotEmpty()) parts += "Card bills: " + p.bills.joinToString(", ")
            body = parts.joinToString("\n")
        }
        val open = PendingIntent.getActivity(
            c, 0, Intent(c, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val n = NotificationCompat.Builder(c, CHANNEL)
            .setSmallIcon(android.R.drawable.ic_menu_agenda)
            .setContentTitle(title)
            .setContentText(body.lineSequence().first())
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setContentIntent(open)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setDefaults(NotificationCompat.DEFAULT_ALL)
            .setAutoCancel(true)
            .build()
        NotificationManagerCompat.from(c).notify(NOTIF_ID, n)
    }
}

class ReminderWorker(ctx: Context, params: WorkerParameters) : CoroutineWorker(ctx, params) {
    override suspend fun doWork(): Result {
        val test = inputData.getBoolean("test", false)
        if (!test && !Reminders.enabled(applicationContext)) return Result.success()
        val prefs = Reminders.prefs(applicationContext)
        val url = prefs.getString("url", "") ?: ""
        val token = prefs.getString("token", "") ?: ""
        if (!url.startsWith("https://") || token.isBlank()) return Result.success()
        return try {
            // No reminders in the first days of the month: they start on the 5th.
            if (!test && LocalDate.now().dayOfMonth < Reminders.START_DAY) return Result.success()
            val state = Api.fetchState(url, token, YearMonth.now().toString())
            Reminders.notify(applicationContext, Reminders.pending(state, LocalDate.now(), Skips.all(applicationContext)), test)
            Result.success()
        } catch (e: Exception) {
            if (runAttemptCount < 3) Result.retry() else Result.success()
        }
    }
}

class ReminderAlarmReceiver : android.content.BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        Reminders.schedule(context)     // line up tomorrow's alarm first
        Reminders.runCheck(context)
    }
}

class BootReceiver : android.content.BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        Reminders.schedule(context)
    }
}
