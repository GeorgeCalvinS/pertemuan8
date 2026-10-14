package com.example.pertemuan8

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import kotlin.math.abs

class TrackerService : Service(), SensorEventListener {

    private lateinit var sensorManager: SensorManager
    private var accelerometer: Sensor? = null
    private val NOTIFICATION_CHANNEL_ID_SERVICE_RUNNING = "tracker_service_running"
    private val NOTIFICATION_CHANNEL_ID_JUMP_DETECTED = "tracker_jump_detected"
    private val NOTIFICATION_ID_SERVICE_RUNNING = 1
    private val NOTIFICATION_ID_JUMP_DETECTED = 2

    private var currentMode = MODE_NORMAL
    private var jumpCount = 0
    private var stepCount = 0
    private var lastStepTime = 0L
    private var lastJumpTime = 0L
    private val DEBOUNCE_TIME_MS = 500L

    companion object {
        const val EXTRA_MODE = "extra_mode"
        const val MODE_NORMAL = 0
        const val MODE_EXERCISE = 1
    }

    override fun onCreate() {
        super.onCreate()
        sensorManager = getSystemService(Context.SENSOR_SERVICE) as SensorManager
        accelerometer = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
        createNotificationChannels()
        Log.d("TrackerService", "Service onCreate")
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Log.d("TrackerService", "Service onStartCommand")
        currentMode = intent?.getIntExtra(EXTRA_MODE, MODE_NORMAL) ?: MODE_NORMAL
        jumpCount = 0
        stepCount = 0
        startForeground(
            NOTIFICATION_ID_SERVICE_RUNNING,
            buildServiceRunningNotification()
        )
        startJumpDetection()
        return START_STICKY
    }

    private fun createNotificationChannels() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val manager = getSystemService(NotificationManager::class.java)

            val serviceChannel = NotificationChannel(
                NOTIFICATION_CHANNEL_ID_SERVICE_RUNNING,
                "Service Running Channel",
                NotificationManager.IMPORTANCE_LOW
            )
            val jumpChannel = NotificationChannel(
                NOTIFICATION_CHANNEL_ID_JUMP_DETECTED,
                "Jump Detected Channel",
                NotificationManager.IMPORTANCE_HIGH
            )

            manager?.createNotificationChannel(serviceChannel)
            manager?.createNotificationChannel(jumpChannel)
        }
    }

    private fun startJumpDetection() {
        accelerometer?.also {
            sensorManager.registerListener(
                this,
                it,
                SensorManager.SENSOR_DELAY_NORMAL
            )
        }
    }

    private fun buildServiceRunningNotification(): Notification {
        val notificationIntent = Intent(this, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(
            this, 0, notificationIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, NOTIFICATION_CHANNEL_ID_SERVICE_RUNNING)
            .setContentTitle("Pelacak Aktivitas Latar")
            .setContentText("Aplikasi Tracker Services sedang berjalan...")
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setOngoing(true)
            .setContentIntent(pendingIntent)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }

    override fun onSensorChanged(event: SensorEvent?) {
        if (event?.sensor?.type == Sensor.TYPE_ACCELEROMETER) {
            val y = event.values[1]
            val currentTime = System.currentTimeMillis()

            if (currentMode == MODE_EXERCISE) {
                handleExerciseMode(y, currentTime)
            } else {
                handleNormalMode(y)
            }
        }
    }

    private fun handleNormalMode(y: Float) {
        val jumpThreshold = 15.0
        if (abs(y) > jumpThreshold) {
            Log.d("TrackerService", "LOMPATAN Terdeteksi! y: $y")
            sensorManager.unregisterListener(this)
            showJumpNotification()
            stopSelf()
        }
    }

    private fun handleExerciseMode(y: Float, currentTime: Long) {
        val jumpThreshold = 15.0
        val stepThreshold = 12.0

        // Deteksi lompatan (threshold 15)
        if (abs(y) > jumpThreshold) {
            if (currentTime - lastJumpTime > DEBOUNCE_TIME_MS) {
                jumpCount++
                lastJumpTime = currentTime
                Log.d("TrackerService", "LOMPATAN terdeteksi! (y: $y) Total Lompatan: $jumpCount")
            }
        } else if (abs(y) > stepThreshold) {
            // Deteksi langkah (threshold 12)
            if (currentTime - lastStepTime > DEBOUNCE_TIME_MS) {
                stepCount++
                lastStepTime = currentTime
                Log.d("TrackerService", "LANGKAH terdeteksi! (y: $y) Total Langkah: $stepCount")

                // Latihan selesai jika lebih dari 2 langkah
                if (stepCount > 2) {
                    Log.d("TrackerService", "Latihan selesai! Total Lompatan: $jumpCount, Total Langkah: $stepCount")
                    sensorManager.unregisterListener(this)
                    openMainActivityWithResults()
                    stopSelf()
                }
            }
        }
    }

    private fun openMainActivityWithResults() {
        showExerciseDoneNotification()
        val intent = Intent(this, MainActivity::class.java).apply {
            action = MainActivity.ACTION_EXERCISE_DONE
            putExtra(MainActivity.EXTRA_JUMP_COUNT, jumpCount)
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
        try {
            startActivity(intent)
        } catch (e: Exception) {
            Log.e("TrackerService", "Gagal membuka activity: ${e.message}")
        }
    }

    private fun showExerciseDoneNotification() {
        val mainActivityIntent = Intent(this, MainActivity::class.java).apply {
            action = MainActivity.ACTION_EXERCISE_DONE
            putExtra(MainActivity.EXTRA_JUMP_COUNT, jumpCount)
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
        }

        val pendingMainActivityIntent = PendingIntent.getActivity(
            this, 2, mainActivityIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val exerciseNotification =
            NotificationCompat.Builder(this, NOTIFICATION_CHANNEL_ID_JUMP_DETECTED)
                .setContentTitle("Latihan Selesai!")
                .setContentText("Latihan selesai dengan $jumpCount lompatan. Ketuk untuk membuka.")
                .setSmallIcon(android.R.drawable.ic_dialog_alert)
                .setContentIntent(pendingMainActivityIntent)
                .setAutoCancel(true)
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .build()

        try {
            NotificationManagerCompat.from(this)
                .notify(NOTIFICATION_ID_JUMP_DETECTED, exerciseNotification)
        } catch (e: SecurityException) {
            Log.e("TrackerService", "Notifikasi gagal: ${e.message}")
        }
    }

    private fun showJumpNotification() {
        val mainActivityIntent = Intent(this, MainActivity::class.java).apply {
            action = MainActivity.ACTION_SERVICE_STOPPED_JUMP
            flags =
                Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
        }

        val pendingMainActivityIntent = PendingIntent.getActivity(
            this, 1, mainActivityIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val jumpNotification =
            NotificationCompat.Builder(this, NOTIFICATION_CHANNEL_ID_JUMP_DETECTED)
                .setContentTitle("Lompatan Terdeteksi")
                .setContentText("Service Berhenti. Ketuk untuk membuka aplikasi.")
                .setSmallIcon(android.R.drawable.ic_dialog_alert)
                .setContentIntent(pendingMainActivityIntent)
                .setAutoCancel(true)
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .build()

        try {
            NotificationManagerCompat.from(this)
                .notify(NOTIFICATION_ID_JUMP_DETECTED, jumpNotification)
        } catch (e: SecurityException) {
            Log.e(
                "TrackerService",
                "Gagal memunculkan notifikasi karena permission POST_NOTIFICATIONS tidak aktif: ${e.message}"
            )
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        sensorManager.unregisterListener(this)
        Log.d("TrackerService", "Service onDestroy")
        Log.d("TrackerService", "Activity updates removed.")
    }

    override fun onBind(intent: Intent?): IBinder? {
        return null
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {
    }
}

