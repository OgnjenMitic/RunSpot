package com.gio.runspot

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import com.google.firebase.auth.FirebaseAuth

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        handleIntent(intent)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleIntent(intent)
    }

    private fun handleIntent(intent: Intent?) {
        val currentUser = FirebaseAuth.getInstance().currentUser
        val routeIdToShow = intent?.getStringExtra("ROUTE_ID_TO_SHOW")

        // NOVO: Jednostavnija i ispravnija logika
        val startDestination = if (currentUser != null) {
            // Ako korisnik postoji (ulogovan je), UVEK idi na mapu.
            Routes.MAIN_SCREEN
        } else {
            // Ako korisnik ne postoji, UVEK idi na login.
            Routes.LOGIN_SCREEN
        }

        if (currentUser == null) {
            Intent(this, LocationService::class.java).also { stopService(it) }
        }

        setContent {
            Surface(
                modifier = Modifier.fillMaxSize(),
                color = MaterialTheme.colorScheme.background
            ) {
                AppNavigation(startDestination = startDestination, routeIdToShow = routeIdToShow)
            }
        }
    }
}