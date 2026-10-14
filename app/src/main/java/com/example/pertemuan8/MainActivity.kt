package com.example.pertemuan8

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import android.provider.CalendarContract
import android.util.Log
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat

class MainActivity : AppCompatActivity() {

    private lateinit var statusTextView: TextView
    private lateinit var btnStart: Button
    private lateinit var btnLatihan: Button

    companion object {
        const val ACTION_SERVICE_STOPPED_JUMP = "com.example.pertemuan8.ACTION_SERVICE_STOPPED_JUMP"
        const val ACTION_EXERCISE_DONE = "com.example.pertemuan8.ACTION_EXERCISE_DONE"
        const val EXTRA_JUMP_COUNT = "extra_jump_count"
        const val REQUEST_CODE_PERMISSIONS = 101
    }

    private val permissionsToRequest = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        arrayOf(
            Manifest.permission.ACTIVITY_RECOGNITION,
            Manifest.permission.POST_NOTIFICATIONS,
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.ACCESS_COARSE_LOCATION
        )
    } else {
        arrayOf(
            Manifest.permission.ACTIVITY_RECOGNITION,
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.ACCESS_COARSE_LOCATION
        )
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        statusTextView = findViewById(R.id.statusTextView)
        btnStart = findViewById(R.id.btnStart)
        btnLatihan = findViewById(R.id.btnLatihan)

        handleIntent(intent)

        btnStart.setOnClickListener {
            if (hasAllPermissions()) {
                startTrackerService()
            } else {
                ActivityCompat.requestPermissions(
                    this,
                    permissionsToRequest,
                    REQUEST_CODE_PERMISSIONS
                )
            }
        }

        btnLatihan.setOnClickListener {
            if (hasAllPermissions()) {
                startExerciseService()
            } else {
                ActivityCompat.requestPermissions(
                    this,
                    permissionsToRequest,
                    REQUEST_CODE_PERMISSIONS
                )
            }
        }
    }

    private fun hasAllPermissions(): Boolean {
        return permissionsToRequest.all {
            ContextCompat.checkSelfPermission(this, it) == PackageManager.PERMISSION_GRANTED
        }
    }

    private fun startTrackerService() {
        val serviceIntent = Intent(this, TrackerService::class.java)
        ContextCompat.startForegroundService(this, serviceIntent)
        statusTextView.text = "Background service sedang berjalan..."
    }

    private fun startExerciseService() {
        val serviceIntent = Intent(this, TrackerService::class.java).apply {
            putExtra(TrackerService.EXTRA_MODE, TrackerService.MODE_EXERCISE)
        }
        ContextCompat.startForegroundService(this, serviceIntent)
        statusTextView.text = "Latihan dimulai...\nBerjalan dan melompat untuk latihan!"
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == REQUEST_CODE_PERMISSIONS) {
            var allPermissionsGranted = true
            for (result in grantResults) {
                if (result != PackageManager.PERMISSION_GRANTED) {
                    allPermissionsGranted = false
                    break
                }
            }
            if (allPermissionsGranted) {
                statusTextView.text = "Izin diberikan. Silakan tekan tombol lagi."
            } else {
                statusTextView.text = "Izin ditolak, service tidak dapat dijalankan."
            }
        }
    }

    private fun handleIntent(intent: Intent?) {
        when (intent?.action) {
            ACTION_SERVICE_STOPPED_JUMP -> {
                statusTextView.text = "Background services sudah berhenti karena Anda melompat"
            }
            ACTION_EXERCISE_DONE -> {
                val jumpCount = intent.getIntExtra(EXTRA_JUMP_COUNT, 0)
                statusTextView.text = "Latihan Selesai!\nJumlah Lompatan: $jumpCount\nMembuat event latihan di Calendar..."
                Toast.makeText(this, "Latihan selesai! Lompatan: $jumpCount", Toast.LENGTH_LONG).show()
                createCalendarEvent(jumpCount)
            }
        }
    }

    private fun createCalendarEvent(jumpCount: Int) {
        val calendarIntent = Intent(Intent.ACTION_INSERT).apply {
            data = CalendarContract.Events.CONTENT_URI
            putExtra(CalendarContract.Events.TITLE, "Latihan Hari Ini")
            putExtra(CalendarContract.Events.DESCRIPTION, "Selesai latihan dengan $jumpCount lompatan.")
            putExtra(CalendarContract.EXTRA_EVENT_BEGIN_TIME, System.currentTimeMillis())
            putExtra(CalendarContract.EXTRA_EVENT_END_TIME, System.currentTimeMillis() + (60 * 60 * 1000))
        }
        try {
            startActivity(calendarIntent)
        } catch (e: Exception) {
            Log.e("MainActivity", "Gagal membuka aplikasi Calendar: ${e.message}")
            Toast.makeText(this, "Aplikasi Calendar tidak tersedia", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleIntent(intent)
    }
}