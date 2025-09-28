package com.gio.runspot

import android.Manifest
import android.util.Log
import android.widget.Toast
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.rememberMultiplePermissionsState
import com.google.android.gms.maps.model.LatLng
import com.google.maps.android.compose.*
import com.google.firebase.firestore.FirebaseFirestore
import com.google.maps.android.PolyUtil
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.io.IOException
import android.content.Intent
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.NotificationsOff
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.navigation.NavController // NOVI IMPORT
import com.google.firebase.auth.FirebaseAuth
import androidx.lifecycle.compose.collectAsStateWithLifecycle // NOVI IMPORT

@OptIn(ExperimentalPermissionsApi::class)
@Composable
fun MainScreen(
    navController: NavController, // NOVO
    selectedRoute: Route?,
    onRouteSelected: (Route?) -> Unit,
    onNavigateToRanking: () -> Unit
) {
    val postNotificationPermission = rememberMultiplePermissionsState(
        permissions = listOf(Manifest.permission.POST_NOTIFICATIONS)
    )
    val locationPermissionsState = rememberMultiplePermissionsState(
        permissions = listOf(
            Manifest.permission.ACCESS_COARSE_LOCATION,
            Manifest.permission.ACCESS_FINE_LOCATION,
        )
    )
    //Stanje koje prati da li je servis pokrenut
    val isServiceRunning=LocationServiceState.isRunning

    var isInRouteCreationMode by remember { mutableStateOf(false) }
    var creationPathPoints by remember { mutableStateOf<List<LatLng>>(emptyList()) }
    var showAddRouteDialog by remember { mutableStateOf(false) }
    var routes by remember { mutableStateOf<List<Route>>(emptyList()) }
    //var selectedRoute by remember { mutableStateOf<Route?>(null) }
    var isLoadingDirections by remember { mutableStateOf(false) }
    var showAddSpotScreen by remember { mutableStateOf(false) }
    var spotLocation by remember { mutableStateOf<LatLng?>(null) }
    var spotsForSelectedRoute by remember { mutableStateOf<List<Spot>>(emptyList()) }
    var newRouteDistance by remember { mutableIntStateOf(0) }
    var selectedSpot by remember { mutableStateOf<Spot?>(null) }


    val coroutineScope = rememberCoroutineScope()
    val context = LocalContext.current

    LaunchedEffect(Unit) {
        val db = FirebaseFirestore.getInstance()
        db.collection("routes")
            .addSnapshotListener { snapshot, e ->
                if (e != null) {
                    Log.w("MainScreen", "Listen failed.", e)
                    return@addSnapshotListener
                }
                if (snapshot != null) {
                    val routeList = snapshot.documents.mapNotNull { doc ->
                        val route = doc.toObject(Route::class.java)
                        route?.copy(id = doc.id)
                    }
                    routes = routeList
                }
            }
    }

    LaunchedEffect(selectedRoute) {
        if (selectedRoute == null) {
            spotsForSelectedRoute = emptyList()
            return@LaunchedEffect
        }
        val db = FirebaseFirestore.getInstance()
        db.collection("spots")
            .whereEqualTo("routeId", selectedRoute.id)
            .addSnapshotListener { snapshot, e ->
                if (e != null) {
                    Log.w("MainScreen", "Listen for spots failed.", e)
                    spotsForSelectedRoute = emptyList()
                    return@addSnapshotListener
                }
                if (snapshot != null) {
                    val spotList = snapshot.documents.mapNotNull { doc ->
                        doc.toObject(Spot::class.java)?.copy(id = doc.id)
                    }
                    spotsForSelectedRoute = spotList
                }
            }
    }

    var mapProperties by remember {
        mutableStateOf(MapProperties(isMyLocationEnabled = locationPermissionsState.allPermissionsGranted))
    }
    var uiSettings by remember {
        mutableStateOf(MapUiSettings(myLocationButtonEnabled = locationPermissionsState.allPermissionsGranted))
    }

    Box(modifier = Modifier.fillMaxSize()) {
        GoogleMap(
            modifier = Modifier.fillMaxSize(),
            properties = mapProperties,
            uiSettings = uiSettings,
            onMapClick = { latLng ->
                if (isInRouteCreationMode) {
                    creationPathPoints = creationPathPoints + latLng
                } else {
                    onRouteSelected(null) // NOVO: Pozivamo funkciju da promenimo spoljašnje stanje
                }
            },
            onMapLongClick = { latLng ->
                if (selectedRoute != null && !isInRouteCreationMode) {
                    val routePath = selectedRoute.pathPoints.map { LatLng(it.latitude, it.longitude) }
                    if (PolyUtil.isLocationOnPath(latLng, routePath, true, 100.0)) {
                        spotLocation = latLng
                        showAddSpotScreen = true
                    } else {
                        Toast.makeText(context, "Spot mora biti na ruti ili blizu nje.", Toast.LENGTH_SHORT).show()
                    }
                }
            }
        ) {
            if (selectedRoute == null && !isInRouteCreationMode) {
                routes.forEach { route ->
                    if (route.pathPoints.isNotEmpty()) {
                        val startLatLng = LatLng(route.pathPoints.first().latitude, route.pathPoints.first().longitude)
                        Marker(
                            state = MarkerState(position = startLatLng),
                            title = route.name,
                            snippet = "Dužina: %.2f km".format(route.distance / 1000.0),
                            onClick = {
                                if (isInRouteCreationMode) {
                                    creationPathPoints = creationPathPoints + startLatLng
                                } else {
                                    onRouteSelected(route) // NOVO: Pozivamo funkciju da promenimo spoljašnje stanje
                                }
                                false
                            }
                        )
                    }
                }
            } else if (selectedRoute != null) {
                val route = selectedRoute
                val path = route.pathPoints.map { LatLng(it.latitude, it.longitude) }
                if (path.isNotEmpty()) {
                    Polyline(points = path, color = Color.Red, width = 10f)
                    Marker(state = MarkerState(position = path.first()), title = route.name, snippet = "Start")
                    Marker(state = MarkerState(position = path.last()), title = route.name, snippet = "Cilj")
                }
                spotsForSelectedRoute.forEach { spot ->
                    Marker(
                        state = MarkerState(position = LatLng(spot.location.latitude, spot.location.longitude)),
                        title = spot.type,
                        snippet = spot.description,
                        onClick = {
                            selectedSpot = spot
                            true // Vraćamo true da se ne bi prikazao podrazumevani info prozor
                        }
                    )
                }
            }

            if (isInRouteCreationMode && creationPathPoints.isNotEmpty()) {
                creationPathPoints.forEachIndexed { index, point ->
                    Marker(state = MarkerState(position = point), title = "Tačka ${index + 1}")
                }
                if (creationPathPoints.size > 1) {
                    Polyline(points = creationPathPoints, color = Color.Blue, width = 15f)
                }
            }
        }

        Row(
            modifier = Modifier
                .align(Alignment.TopStart) // Poravnanje gore levo
                .padding(16.dp),
            horizontalArrangement = Arrangement.Start, // Raspored elemenata od početka (levo)
            verticalAlignment = Alignment.CenterVertically
        ) {
            Button(onClick = onNavigateToRanking) {
                Text("Rang Lista")
            }

            Spacer(modifier = Modifier.width(8.dp))
            Button(onClick = {
                // Ugasi servis ako radi
                Intent(context, LocationService::class.java).also { context.stopService(it) }
                // Odjavi korisnika sa Firebase-a
                FirebaseAuth.getInstance().signOut()
                // Vrati se na Login ekran i obriši sve prethodne ekrane
                navController.navigate(Routes.LOGIN_SCREEN) {
                    popUpTo(navController.graph.startDestinationId) { inclusive = true }
                }
            }) {
                Text("Odjava")
            }

            Spacer(modifier = Modifier.width(8.dp))
            IconButton(
                onClick = {
                    if (isServiceRunning) {
                        // Ako servis radi, ugasi ga
                        Intent(context, LocationService::class.java).also {
                            context.stopService(it)
                            Toast.makeText(context, "Praćenje u pozadini zaustavljeno.", Toast.LENGTH_SHORT).show()
                        }
                    } else {
                        // Ako ne radi, pokreni ga (sa proverom dozvola)
                        coroutineScope.launch {
                            postNotificationPermission.launchMultiplePermissionRequest()
                            if (postNotificationPermission.allPermissionsGranted) {
                                // Kreiramo novi Intent i dodajemo ID rute
                                val intent = Intent(context, LocationService::class.java).apply {
                                    putExtra("ROUTE_ID", selectedRoute!!.id)
                                }
                                context.startService(intent)
                                Toast.makeText(context, "Praćenje rute '${selectedRoute?.name}' pokrenuto.", Toast.LENGTH_SHORT).show()
                            } else {
                                Toast.makeText(context, "Dozvola za notifikacije je neophodna.", Toast.LENGTH_SHORT).show()
                            }
                        }
                    }
                },
                // Dugme je omogućeno samo ako je neka ruta selektovana
                enabled = selectedRoute != null
            ) {
                Icon(
                    imageVector = if (isServiceRunning) Icons.Default.NotificationsOff else Icons.Default.Notifications,
                    contentDescription = if (isServiceRunning) "Isključi Notifikacije" else "Uključi Notifikacije"
                )
            }

        }

        Surface(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(16.dp),
            shape = RoundedCornerShape(16.dp),
            shadowElevation = 8.dp
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                if (selectedRoute != null) {
                    Text("Selektovana ruta: ${selectedRoute.name}")
                    Text("Dužina: %.2f km".format(selectedRoute.distance / 1000.0))
                    Text("Dugi pritisak na rutu za dodavanje spota")
                } else {
                    if (isInRouteCreationMode) {
                        Row {
                            Button(
                                onClick = {
                                    coroutineScope.launch {
                                        isLoadingDirections = true
                                        val apiKey = context.getString(R.string.google_maps_api_key)
                                        val directionsResult = getDirectionsWithWaypoints(apiKey, creationPathPoints)
                                        isLoadingDirections = false
                                        if (directionsResult.first.isNotEmpty()) {
                                            creationPathPoints = directionsResult.first
                                            newRouteDistance = directionsResult.second
                                            showAddRouteDialog = true
                                        } else {
                                            Toast.makeText(context, "Nije moguće pronaći putanju.", Toast.LENGTH_LONG).show()
                                        }
                                    }
                                },
                                enabled = creationPathPoints.size >= 2
                            ) {
                                Text("Završi i Optimizuj")
                            }
                            Spacer(modifier = Modifier.width(8.dp))
                            Button(onClick = {
                                isInRouteCreationMode = false
                                creationPathPoints = emptyList()
                            }) {
                                Text("Otkaži")
                            }
                        }
                    } else {
                        Button(onClick = {
                            isInRouteCreationMode = true
                            onRouteSelected(null) // NOVO: Pozivamo funkciju da promenimo spoljašnje stanje
                        }) {
                            Text("Započni Novu Rutu")
                        }
                    }
                }
            }
        }

        if (!locationPermissionsState.allPermissionsGranted) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(text = "Molimo, dajte dozvolu za lokaciju da biste koristili mapu.")
            }
            LaunchedEffect(Unit) { locationPermissionsState.launchMultiplePermissionRequest() }
        } else {
            mapProperties = mapProperties.copy(isMyLocationEnabled = true)
            uiSettings = uiSettings.copy(myLocationButtonEnabled = true)
        }

        if (isLoadingDirections) {
            CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
        }

        if (showAddSpotScreen && spotLocation != null && selectedRoute != null) {
            AddSpotScreen(
                routeId = selectedRoute.id,
                latitude = spotLocation!!.latitude,
                longitude = spotLocation!!.longitude,
                onSpotAdded = {
                    showAddSpotScreen = false
                    spotLocation = null
                }
            )
        }

        if (creationPathPoints.isNotEmpty() && showAddRouteDialog) {
            AddRouteDialog(
                pathPoints = creationPathPoints,
                distanceInMeters = newRouteDistance,
                onDismiss = {
                    showAddRouteDialog = false
                    isInRouteCreationMode = false
                    creationPathPoints = emptyList()
                }
            )
        }

        if (selectedSpot != null) {
            SpotDetailsDialog(
                spot = selectedSpot!!,
                onDismiss = { selectedSpot = null }
            )
        }
    }
}

private suspend fun getDirectionsWithWaypoints(apiKey: String, points: List<LatLng>): Pair<List<LatLng>, Int> = withContext(Dispatchers.IO) {
    if (points.size < 2) return@withContext Pair(emptyList(), 0)
    val origin = "${points.first().latitude},${points.first().longitude}"
    val destination = "${points.last().latitude},${points.last().longitude}"
    val waypoints = if (points.size > 2) {
        "&waypoints=" + points.subList(1, points.size - 1).joinToString("|") { "${it.latitude},${it.longitude}" }
    } else { "" }
    val url = "https://maps.googleapis.com/maps/api/directions/json?origin=$origin&destination=$destination$waypoints&mode=walking&key=$apiKey"
    val client = OkHttpClient()
    val request = Request.Builder().url(url).build()
    try {
        val response = client.newCall(request).execute()
        if (!response.isSuccessful) {
            Log.e("MainScreen", "Directions API request failed with code: ${response.code}")
            return@withContext Pair(emptyList(), 0)
        }
        val responseBody = response.body?.string()
        if (responseBody != null) {
            val json = JSONObject(responseBody)
            val routes = json.getJSONArray("routes")
            if (routes.length() > 0) {
                val route = routes.getJSONObject(0)
                val pointsStr = route.getJSONObject("overview_polyline").getString("points")
                val path = decodePoly(pointsStr)
                val legs = route.getJSONArray("legs")
                var totalDistance = 0
                for (i in 0 until legs.length()) {
                    totalDistance += legs.getJSONObject(i).getJSONObject("distance").getInt("value")
                }
                return@withContext Pair(path, totalDistance)
            }
        }
        Pair(emptyList(), 0)
    } catch (e: IOException) {
        Log.e("MainScreen", "Network error fetching directions: ${e.message}")
        Pair(emptyList(), 0)
    } catch (e: Exception) {
        Log.e("MainScreen", "Error parsing directions: ${e.message}")
        Pair(emptyList(), 0)
    }
}

private fun decodePoly(encoded: String): List<LatLng> {
    val poly = ArrayList<LatLng>()
    var index = 0
    val len = encoded.length
    var lat = 0
    var lng = 0
    while (index < len) {
        var b: Int
        var shift = 0
        var result = 0
        do {
            b = encoded[index++].code - 63
            result = result or (b and 0x1f shl shift)
            shift += 5
        } while (b >= 0x20)
        val dlat = if (result and 1 != 0) (result shr 1).inv() else result shr 1
        lat += dlat
        shift = 0
        result = 0
        do {
            b = encoded[index++].code - 63
            result = result or (b and 0x1f shl shift)
            shift += 5
        } while (b >= 0x20)
        val dlng = if (result and 1 != 0) (result shr 1).inv() else result shr 1
        lng += dlng
        val p = LatLng(lat.toDouble() / 1E5, lng.toDouble() / 1E5)
        poly.add(p)
    }
    return poly
}