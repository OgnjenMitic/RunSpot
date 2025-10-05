package com.gio.runspot

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.location.Location
import android.os.Build
import android.os.IBinder
import android.os.Looper
import android.util.Log
import androidx.core.app.NotificationCompat
import com.google.android.gms.location.*
import com.google.firebase.firestore.FirebaseFirestore

class LocationService : Service() {

    private lateinit var fusedLocationClient: FusedLocationProviderClient
    private lateinit var locationCallback: LocationCallback

    private var spotsToTrack = listOf<Spot>()
    private var trackedRouteId: String? = null
    // Lista za praćenje ID-jeva spotova za koje je notifikacija već poslata
    private val notifiedSpotIds = mutableSetOf<String>()

    override fun onCreate() {
        super.onCreate()
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)
        setupLocationCallback()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        trackedRouteId = intent?.getStringExtra("ROUTE_ID")

        if (trackedRouteId == null) {
            Log.e("LocationService", "Servis pokrenut bez ROUTE_ID.")
            stopSelf()
            return START_NOT_STICKY
        }

        fetchSpotsForRoute(trackedRouteId!!)

        createNotificationChannel()
        // Prosleđujemo ID rute funkciji koja pravi notifikaciju
        val notification = createForegroundNotification(trackedRouteId!!)

        LocationServiceState.isRunning = true
        startForeground(1, notification)
        startLocationUpdates()
        return START_STICKY
    }

    private fun fetchSpotsForRoute(routeId: String) {
        val db = FirebaseFirestore.getInstance()
        db.collection("spots")
            .whereEqualTo("routeId", routeId)
            .get()
            .addOnSuccessListener { snapshot ->
                spotsToTrack = snapshot.documents.mapNotNull { doc ->
                    doc.toObject(Spot::class.java)?.copy(id = doc.id)
                }
                Log.d("LocationService", "Preuzeto ${spotsToTrack.size} spotova za praćenje.")
            }
            .addOnFailureListener {
                Log.e("LocationService", "Greška pri preuzimanju spotova.", it)
            }
    }

    private fun startLocationUpdates() {
        val locationRequest = LocationRequest.Builder(Priority.PRIORITY_BALANCED_POWER_ACCURACY, 180000)
            .setMinUpdateIntervalMillis(60000)
            .build()
        try {
            fusedLocationClient.requestLocationUpdates(locationRequest, locationCallback, Looper.getMainLooper())
        } catch (e: SecurityException) {
            Log.e("LocationService", "Dozvola za lokaciju nije data.", e)
        }
    }

    private fun setupLocationCallback() {
        locationCallback = object : LocationCallback() {
            override fun onLocationResult(locationResult: LocationResult) {
                locationResult.lastLocation?.let { userLocation ->
                    Log.d("LocationService", "Lokacija: ${userLocation.latitude}, ${userLocation.longitude}")

                    spotsToTrack.forEach { spot ->
                        // Proveravamo da li smo već poslali notifikaciju za ovaj spot
                        if (!notifiedSpotIds.contains(spot.id)) {
                            val spotLocation = Location("").apply {
                                latitude = spot.location.latitude
                                longitude = spot.location.longitude
                            }

                            val distance = userLocation.distanceTo(spotLocation)

                            if (distance < 50) {
                                sendProximityNotification(spot)
                                // Dodajemo ID u listu da ne bismo ponovo slali
                                notifiedSpotIds.add(spot.id)
                            }
                        }
                    }
                }
            }
        }
    }

    private fun sendProximityNotification(spot: Spot) {
        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        // Kreiramo novi kanal specifično za notifikacije o blizini, sa visokim prioritetom
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val name = "Proximity Alerts"
            val descriptionText = "Notifikacije kada ste blizu nekog spota."
            val importance = NotificationManager.IMPORTANCE_HIGH // VAŽNO: Visok prioritet
            val channel = NotificationChannel("proximity_alert_channel", name, importance).apply {
                description = descriptionText
            }
            notificationManager.createNotificationChannel(channel)
        }

        val notification = NotificationCompat.Builder(this, "proximity_alert_channel")
            .setContentTitle("RunSpot: ${spot.type} u blizini!")
            .setContentText(spot.description)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .build()

        notificationManager.notify(spot.id.hashCode(), notification)
    }

    // NOVO: Funkcija sada prima ID rute
    private fun createForegroundNotification(routeId: String): android.app.Notification {
        val notificationIntent = Intent(this, MainActivity::class.java).apply {
            action = "SHOW_MAP"
            // NOVO: "Pakujemo" i ID rute u poruku
            putExtra("ROUTE_ID_TO_SHOW", routeId)
            // Važan fleg da se aktivnost ne kreira ponovo ako već postoji
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            notificationIntent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        return NotificationCompat.Builder(this, "location_channel")
            .setContentTitle("RunSpot je aktivan")
            .setContentText("Pratimo izabranu rutu u pozadini.")
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setContentIntent(pendingIntent)
            .build()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val name = "Location Service Channel"
            val descriptionText = "Channel for foreground service notification"
            val importance = NotificationManager.IMPORTANCE_LOW // Smanjujemo prioritet za stalnu notifikaciju
            val channel = NotificationChannel("location_channel", name, importance).apply {
                description = descriptionText
            }
            val notificationManager: NotificationManager =
                getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.createNotificationChannel(channel)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        fusedLocationClient.removeLocationUpdates(locationCallback)
        LocationServiceState.isRunning = false
    }

    override fun onBind(intent: Intent?): IBinder? {
        return null
    }
}