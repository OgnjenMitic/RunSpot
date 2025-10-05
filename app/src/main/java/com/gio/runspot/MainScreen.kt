package com.gio.runspot

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.location.Location
import android.util.Log
import android.widget.Toast
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.NotificationsOff
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.rememberMultiplePermissionsState
import com.google.android.gms.location.LocationServices
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.model.LatLng
import com.google.maps.android.compose.*
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.maps.android.PolyUtil
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.filled.Map
import androidx.compose.foundation.clickable
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.ui.text.font.FontWeight

private const val MAX_DISTANCE_FILTER_KM = 50f

@OptIn(ExperimentalPermissionsApi::class, ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(
    navController: NavController,
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
    val isServiceRunning = LocationServiceState.isRunning

    var isInRouteCreationMode by remember { mutableStateOf(false) }
    var creationPathPoints by remember { mutableStateOf<List<LatLng>>(emptyList()) }
    var showAddRouteDialog by remember { mutableStateOf(false) }
    var routes by remember { mutableStateOf<List<Route>>(emptyList()) }
    var isLoadingDirections by remember { mutableStateOf(false) }
    var showAddSpotScreen by remember { mutableStateOf(false) }
    var spotLocation by remember { mutableStateOf<LatLng?>(null) }
    var spotsForSelectedRoute by remember { mutableStateOf<List<Spot>>(emptyList()) }
    var newRouteDistance by remember { mutableIntStateOf(0) }
    var selectedSpot by remember { mutableStateOf<Spot?>(null) }

    val cameraPositionState = rememberCameraPositionState()
    val coroutineScope = rememberCoroutineScope()
    val context = LocalContext.current

    var isMapView by remember { mutableStateOf(true) }
    var allUsers by remember { mutableStateOf<List<User>>(emptyList()) }

    // --- Stanja za filtriranje ---
    var showFilterDialog by remember { mutableStateOf(false) }
    var filterByMyRoutes by remember { mutableStateOf(false) }
    var filterDistanceRange by remember { mutableStateOf(0f..MAX_DISTANCE_FILTER_KM) }
    var filterByRadius by remember { mutableStateOf(false) }
    var filterRadiusKm by remember { mutableFloatStateOf(5f) }
    var filterStartDate by remember { mutableStateOf<Date?>(null) }
    var filterEndDate by remember { mutableStateOf<Date?>(null) }
    var filteredRoutes by remember { mutableStateOf<List<Route>>(emptyList()) }
    var searchQuery by remember { mutableStateOf("") }
    var allSpots by remember { mutableStateOf<List<Spot>>(emptyList()) }


    LaunchedEffect(Unit) {
        val db = FirebaseFirestore.getInstance()
        db.collection("routes").addSnapshotListener { snapshot, e ->
            if (e != null) {
                return@addSnapshotListener
            }
            if (snapshot != null) {
                routes = snapshot.documents.mapNotNull {
                    it.toObject(Route::class.java)?.copy(id = it.id)
                }
            }
        }
        db.collection("spots").addSnapshotListener { snapshot, e ->
            if (e != null) {
                return@addSnapshotListener
            }
            if (snapshot != null) {
                allSpots = snapshot.documents.mapNotNull {
                    it.toObject(Spot::class.java)?.copy(id = it.id)
                }
            }
        }
        db.collection("users").addSnapshotListener { snapshot, e ->
            if (e != null) {
                return@addSnapshotListener
            }
            if (snapshot != null) {
                allUsers = snapshot.documents.mapNotNull { document ->
                    document.toObject(User::class.java)?.copy(id = document.id)
                }
            }
        }
    }

    LaunchedEffect(selectedRoute) {
        if (selectedRoute == null) {
            spotsForSelectedRoute = emptyList()
            return@LaunchedEffect
        }
        val db = FirebaseFirestore.getInstance()
        db.collection("spots").whereEqualTo("routeId", selectedRoute.id)
            .addSnapshotListener { snapshot, e ->
                if (e != null) {
                    spotsForSelectedRoute = emptyList()
                    return@addSnapshotListener
                }
                if (snapshot != null) {
                    spotsForSelectedRoute = snapshot.documents.mapNotNull {
                        it.toObject(Spot::class.java)?.copy(id = it.id)
                    }
                }
            }
    }

    LaunchedEffect(
        searchQuery,
        routes,
        allSpots,
        filterByMyRoutes,
        filterDistanceRange,
        filterByRadius,
        filterRadiusKm,
        filterStartDate,
        filterEndDate
    ) {
        val currentUserId = FirebaseAuth.getInstance().currentUser?.uid

        // debounce: čekamo malo ako korisnik kuca u search-u
        if (searchQuery.isNotBlank()) {
            kotlinx.coroutines.delay(300)
        }

        // filtriranje prebacujemo na pozadinski thread
        val tempList = withContext(Dispatchers.Default) {
            var tempFilteredList = routes

            // 1. Filtriranje po search-u (pretraga po imenu ili spotovima)
            if (searchQuery.isNotBlank()) {
                val routeIdsWithMatchingSpots = allSpots
                    .filter { it.type.contains(searchQuery, ignoreCase = true) }
                    .map { it.routeId }
                    .toSet()

                tempFilteredList = tempFilteredList.filter { route ->
                    route.name.contains(searchQuery, ignoreCase = true) ||
                            route.id in routeIdsWithMatchingSpots
                }
            }

            // 2. Filtriranje po mojim rutama
            if (filterByMyRoutes && currentUserId != null) {
                tempFilteredList = tempFilteredList.filter { it.authorId == currentUserId }
            }

            // 3. Filtriranje po distanci
            val minDistanceMeters = (filterDistanceRange.start * 1000).toInt()
            val maxDistanceMeters = (filterDistanceRange.endInclusive * 1000).toInt()
            tempFilteredList = tempFilteredList.filter { it.distance in minDistanceMeters..maxDistanceMeters }

            // 4. Filtriranje po radijusu (ako ima dozvola i lokacija)
            if (filterByRadius && locationPermissionsState.allPermissionsGranted) {
                tempFilteredList = filterByRadius(context, tempFilteredList, filterRadiusKm)
            }

            // 5. Filtriranje po datumu početka
            filterStartDate?.let { startDate ->
                tempFilteredList = tempFilteredList.filter { route ->
                    route.createdAt?.after(startDate) ?: false
                }
            }

            // 6. Filtriranje po datumu kraja
            filterEndDate?.let { endDate ->
                val calendar = Calendar.getInstance().apply {
                    time = endDate
                    set(Calendar.HOUR_OF_DAY, 23)
                    set(Calendar.MINUTE, 59)
                    set(Calendar.SECOND, 59)
                }
                tempFilteredList = tempFilteredList.filter { route ->
                    route.createdAt?.before(calendar.time) ?: false
                }
            }

            tempFilteredList
        }

        // prikaz na mapi
        filteredRoutes = tempList
    }


    // EFEKAT ZA ZUMIRANJE
    LaunchedEffect(locationPermissionsState.allPermissionsGranted, cameraPositionState) {
        if (locationPermissionsState.allPermissionsGranted) {
            zoomToUserLocation(context, cameraPositionState, coroutineScope)
        }
    }

    var mapProperties by remember { mutableStateOf(MapProperties(isMyLocationEnabled = locationPermissionsState.allPermissionsGranted)) }
    var uiSettings by remember { mutableStateOf(MapUiSettings(myLocationButtonEnabled = locationPermissionsState.allPermissionsGranted)) }

    Box(modifier = Modifier.fillMaxSize()) {
        if (isMapView) {
            GoogleMap(
                modifier = Modifier.fillMaxSize(),
                cameraPositionState = cameraPositionState,
                properties = mapProperties,
                uiSettings = uiSettings,
                contentPadding = PaddingValues(top = 180.dp, bottom = 90.dp),
                onMapClick = { latLng ->
                    if (isInRouteCreationMode) creationPathPoints += latLng else onRouteSelected(
                        null
                    )
                },
                onMapLongClick = { latLng ->
                    if (selectedRoute != null && !isInRouteCreationMode) {
                        val routePath =
                            selectedRoute.pathPoints.map { LatLng(it.latitude, it.longitude) }
                        if (PolyUtil.isLocationOnPath(latLng, routePath, true, 100.0)) {
                            spotLocation = latLng
                            showAddSpotScreen = true
                        } else {
                            Toast.makeText(
                                context,
                                "Spot mora biti na ruti ili blizu nje.",
                                Toast.LENGTH_SHORT
                            ).show()
                        }
                    }
                }
            ) {
                if (selectedRoute == null && !isInRouteCreationMode) {
                    filteredRoutes.forEach { route ->
                        if (route.pathPoints.isNotEmpty()) {
                            val startLatLng = LatLng(
                                route.pathPoints.first().latitude,
                                route.pathPoints.first().longitude
                            )
                            Marker(
                                state = MarkerState(position = startLatLng),
                                title = route.name,
                                snippet = "Dužina: %.2f km".format(route.distance / 1000.0),
                                onClick = { onRouteSelected(route); false }
                            )
                        }
                    }
                } else if (selectedRoute != null) {
                    val route = selectedRoute
                    val path = route.pathPoints.map { LatLng(it.latitude, it.longitude) }
                    if (path.isNotEmpty()) {
                        Polyline(points = path, color = Color.Red, width = 10f)
                        Marker(
                            state = MarkerState(position = path.first()),
                            title = route.name,
                            snippet = "Start"
                        )
                        Marker(
                            state = MarkerState(position = path.last()),
                            title = route.name,
                            snippet = "Cilj"
                        )
                    }
                    spotsForSelectedRoute.forEach { spot ->
                        Marker(
                            state = MarkerState(
                                position = LatLng(
                                    spot.location.latitude,
                                    spot.location.longitude
                                )
                            ),
                            title = spot.type, snippet = spot.description,
                            onClick = { selectedSpot = spot; true }
                        )
                    }
                }
                if (isInRouteCreationMode && creationPathPoints.isNotEmpty()) {
                    creationPathPoints.forEachIndexed { index, point ->
                        Marker(
                            state = MarkerState(
                                position = point
                            ), title = "Tačka ${index + 1}"
                        )
                    }
                    if (creationPathPoints.size > 1) {
                        Polyline(points = creationPathPoints, color = Color.Blue, width = 15f)
                    }
                }
            }
        } else {
            RouteList(
                routes = filteredRoutes,
                allUsers = allUsers,
                onRouteClick = { route ->
                    onRouteSelected(route)
                    isMapView = true
                    if (route.pathPoints.isNotEmpty()) {
                        val firstPoint = route.pathPoints.first()
                        coroutineScope.launch {
                            cameraPositionState.animate(
                                CameraUpdateFactory.newLatLngZoom(
                                    LatLng(firstPoint.latitude, firstPoint.longitude),
                                    15f // zoom level
                                )
                            )
                        }
                    }
                }
            )
        }

        // Gornji panel sa kontrolama
        Surface(
            modifier = Modifier.align(Alignment.TopCenter).padding(8.dp).fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            shadowElevation = 8.dp
        ) {
            Column(modifier = Modifier.padding(vertical = 8.dp, horizontal = 16.dp)) {
                // Pretraga i Filter
                if (selectedRoute == null && !isInRouteCreationMode) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        OutlinedTextField(
                            value = searchQuery,
                            onValueChange = { searchQuery = it },
                            label = { Text("Pretraži rute ili spotove...") },
                            modifier = Modifier.weight(1f)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        IconButton(onClick = { showFilterDialog = true }) {
                            Icon(imageVector = Icons.Default.FilterList, contentDescription = "Filteri")
                        }
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))



                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Button(onClick = { navController.navigate(Routes.PROFILE_SCREEN) }) { Text("Profil") }
                        Button(onClick = onNavigateToRanking) { Text("Rang") }
                        // Dugme za prikaz liste ili mape
                        IconButton(onClick = { isMapView = !isMapView }) {
                            Icon(
                                imageVector = if (isMapView) Icons.AutoMirrored.Filled.List else Icons.Default.Map,
                                contentDescription = "Promeni prikaz"
                            )
                        }

                        // Dugme za notifikacije
                        IconButton(
                            onClick = {
                                if (isServiceRunning) {
                                    Intent(context, LocationService::class.java).also { context.stopService(it) }
                                } else if (selectedRoute != null) {
                                    coroutineScope.launch {
                                        postNotificationPermission.launchMultiplePermissionRequest()
                                        if (postNotificationPermission.allPermissionsGranted) {
                                            context.startService(Intent(context, LocationService::class.java).apply { putExtra("ROUTE_ID", selectedRoute.id) })
                                        }
                                    }
                                }
                            },
                            enabled = selectedRoute != null
                        ) {
                            Icon(
                                imageVector = if (isServiceRunning) Icons.Default.NotificationsOff else Icons.Default.Notifications,
                                contentDescription = "Notifikacije"
                            )
                        }

                }
            }
        }

        // Donji panel se prikazuje samo na mapi
        if (isMapView) {
            Surface(
                modifier = Modifier.align(Alignment.BottomCenter).padding(16.dp),
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
                                            val apiKey =
                                                context.getString(R.string.google_maps_api_key)
                                            val directionsResult = getDirectionsWithWaypoints(
                                                apiKey,
                                                creationPathPoints
                                            )
                                            isLoadingDirections = false
                                            if (directionsResult.first.isNotEmpty()) {
                                                creationPathPoints = directionsResult.first
                                                newRouteDistance = directionsResult.second
                                                showAddRouteDialog = true
                                            } else {
                                                Toast.makeText(
                                                    context,
                                                    "Nije moguće pronaći putanju.",
                                                    Toast.LENGTH_LONG
                                                ).show()
                                            }
                                        }
                                    },
                                    enabled = creationPathPoints.size >= 2
                                ) { Text("Završi i Optimizuj") }
                                Spacer(modifier = Modifier.width(8.dp))
                                Button(onClick = {
                                    isInRouteCreationMode = false; creationPathPoints = emptyList()
                                }) { Text("Otkaži") }
                            }
                        } else {
                            Button(onClick = {
                                isInRouteCreationMode = true; onRouteSelected(null)
                            }) { Text("Započni Novu Rutu") }
                        }
                    }
                }
            }
        }


        if (!locationPermissionsState.allPermissionsGranted) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) { Text(text = "Molimo, dajte dozvolu za lokaciju da biste koristili mapu.") }
            LaunchedEffect(Unit) { locationPermissionsState.launchMultiplePermissionRequest() }
        } else {
            mapProperties = mapProperties.copy(isMyLocationEnabled = true)
            uiSettings = uiSettings.copy(myLocationButtonEnabled = true)
        }

        if (isLoadingDirections) {
            CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
        }
        val currentSpotLocation = spotLocation
        if (showAddSpotScreen && currentSpotLocation != null && selectedRoute != null) {
            AddSpotScreen(
                routeId = selectedRoute.id,
                latitude = currentSpotLocation.latitude,
                longitude = currentSpotLocation.longitude
            ) { showAddSpotScreen = false; spotLocation = null }//lambda funkcija da bi se izvrsilo na kraju
        }
        if (creationPathPoints.isNotEmpty() && showAddRouteDialog) {
            AddRouteDialog(
                pathPoints = creationPathPoints,
                distanceInMeters = newRouteDistance
            ) {
                showAddRouteDialog = false; isInRouteCreationMode = false; creationPathPoints =
                emptyList()
            }
        }
        if (selectedSpot != null) {
            SpotDetailsDialog(spot = selectedSpot!!) { selectedSpot = null }
        }

        if (showFilterDialog) {
            FilterDialog(
                initialMyRoutes = filterByMyRoutes,
                initialDistanceRange = filterDistanceRange,
                initialByRadius = filterByRadius,
                initialRadiusKm = filterRadiusKm,
                initialStartDate = filterStartDate,
                initialEndDate = filterEndDate,
                onDismiss = { showFilterDialog = false },
                onApply = { newMyRoutes, newDistanceRange, newByRadius, newRadiusKm, newStartDate, newEndDate ->
                    filterByMyRoutes = newMyRoutes
                    filterDistanceRange = newDistanceRange
                    filterByRadius = newByRadius
                    filterRadiusKm = newRadiusKm
                    filterStartDate = newStartDate
                    filterEndDate = newEndDate
                    showFilterDialog = false
                }
            )
        }
    }
}


@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun FilterDialog(
    initialMyRoutes: Boolean,
    initialDistanceRange: ClosedFloatingPointRange<Float>,
    initialByRadius: Boolean,
    initialRadiusKm: Float,
    initialStartDate: Date?,
    initialEndDate: Date?,
    onDismiss: () -> Unit,
    onApply: (Boolean, ClosedFloatingPointRange<Float>, Boolean, Float, Date?, Date?) -> Unit
) {
    var tempMyRoutes by remember { mutableStateOf(initialMyRoutes) }
    var tempDistanceRange by remember { mutableStateOf(initialDistanceRange) }
    var tempByRadius by remember { mutableStateOf(initialByRadius) }
    var tempRadiusKm by remember { mutableFloatStateOf(initialRadiusKm) }
    var tempStartDate by remember { mutableStateOf(initialStartDate) }
    var tempEndDate by remember { mutableStateOf(initialEndDate) }

    val datePickerState = rememberDatePickerState()
    var showDatePicker by remember { mutableStateOf(false) }
    var editingStartDate by remember { mutableStateOf(true) }

    val dateFormatter = SimpleDateFormat("dd.MM.yyyy", Locale.getDefault())

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Filteri pretrage") },
        text = {
            Column {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                    Checkbox(checked = tempMyRoutes, onCheckedChange = { tempMyRoutes = it })
                    Text("Prikaži samo moje rute")
                }
                Spacer(modifier = Modifier.height(16.dp))

                Text("Dužina rute (km):")
                RangeSlider(value = tempDistanceRange, onValueChange = { tempDistanceRange = it }, valueRange = 0f..MAX_DISTANCE_FILTER_KM, steps = (MAX_DISTANCE_FILTER_KM.toInt() / 5) - 1)
                Text("od %.1f km do %.1f km".format(tempDistanceRange.start, tempDistanceRange.endInclusive), modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Center)
                Spacer(modifier = Modifier.height(16.dp))

                HorizontalDivider()
                Spacer(modifier = Modifier.height(16.dp))
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                    Switch(checked = tempByRadius, onCheckedChange = { tempByRadius = it })
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Pretraži u radijusu od mene")
                }
                if (tempByRadius) {
                    Slider(value = tempRadiusKm, onValueChange = { tempRadiusKm = it }, valueRange = 1f..MAX_DISTANCE_FILTER_KM, steps = (MAX_DISTANCE_FILTER_KM.toInt() - 1))
                    Text("%.0f km".format(tempRadiusKm), modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Center)
                }

                // UI za datum
                HorizontalDivider(modifier = Modifier.padding(vertical = 16.dp))
                Text("Datum kreiranja rute:")
                Spacer(modifier = Modifier.height(8.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceAround) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        OutlinedButton(onClick = { editingStartDate = true; showDatePicker = true }) {
                            Text(tempStartDate?.let { dateFormatter.format(it) } ?: "Od datuma")
                        }
                        if (tempStartDate != null) {
                            TextButton(onClick = { tempStartDate = null }) {
                                Text("Reset")
                            }
                        }
                    }

                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        OutlinedButton(onClick = { editingStartDate = false; showDatePicker = true }) {
                            Text(tempEndDate?.let { dateFormatter.format(it) } ?: "Do datuma")
                        }
                        if (tempEndDate != null) {
                            TextButton(onClick = { tempEndDate = null }) {
                                Text("Reset")
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(onClick = { onApply(tempMyRoutes, tempDistanceRange, tempByRadius, tempRadiusKm, tempStartDate, tempEndDate) }) { Text("Primeni") }
        },
        dismissButton = {
            Button(onClick = onDismiss) { Text("Otkaži") }
        }
    )

    if (showDatePicker) {
        DatePickerDialog(
            onDismissRequest = { showDatePicker = false },
            confirmButton = {
                TextButton(onClick = {
                    val selectedDateInMillis = datePickerState.selectedDateMillis
                    if (selectedDateInMillis != null) {
                        if (editingStartDate) {
                            tempStartDate = Date(selectedDateInMillis)
                        } else {
                            tempEndDate = Date(selectedDateInMillis)
                        }
                    }
                    showDatePicker = false
                }) { Text("Potvrdi") }
            },
            dismissButton = {
                TextButton(onClick = { showDatePicker = false }) { Text("Otkaži") }
            }
        ) {
            DatePicker(state = datePickerState)
        }
    }
}

// ----- POMOĆNE FUNKCIJE -----

@SuppressLint("MissingPermission")
private fun zoomToUserLocation(
    context: Context,
    cameraPositionState: CameraPositionState,
    coroutineScope: CoroutineScope
) {
    val fusedLocationClient = LocationServices.getFusedLocationProviderClient(context)
    fusedLocationClient.lastLocation.addOnSuccessListener { location ->
        if (location != null) {
            val userLatLng = LatLng(location.latitude, location.longitude)
            coroutineScope.launch {
                cameraPositionState.animate(CameraUpdateFactory.newLatLngZoom(userLatLng, 15f), 1000)
            }
        }
    }
}

@SuppressLint("MissingPermission")
private suspend fun filterByRadius(context: Context, routes: List<Route>, radiusKm: Float): List<Route> {
    val fusedLocationClient = LocationServices.getFusedLocationProviderClient(context)
    var filteredList = routes
    try {
        val lastLocation: Location? = fusedLocationClient.lastLocation.await()
        if (lastLocation != null) {
            val searchRadiusMeters = radiusKm * 1000
            filteredList = routes.filter { route ->
                if (route.pathPoints.isEmpty()) return@filter false
                val routeStartPoint = Location("").apply {
                    latitude = route.pathPoints.first().latitude
                    longitude = route.pathPoints.first().longitude
                }
                lastLocation.distanceTo(routeStartPoint) <= searchRadiusMeters
            }
        }
    } catch (e: Exception) {
        Log.e("MainScreenFilter", "Nije moguće dobiti lokaciju za filter radijusa.", e)
    }
    return filteredList
}

private suspend fun getDirectionsWithWaypoints(apiKey: String, points: List<LatLng>): Pair<List<LatLng>, Int> = withContext(Dispatchers.IO) {
    if (points.size < 2) return@withContext Pair(emptyList(), 0)
    val origin = "${points.first().latitude},${points.first().longitude}"
    val destination = "${points.last().latitude},${points.last().longitude}"
    val waypoints = if (points.size > 2) "&waypoints=" + points.subList(1, points.size - 1).joinToString("|") { "${it.latitude},${it.longitude}" } else ""
    val url = "https://maps.googleapis.com/maps/api/directions/json?origin=$origin&destination=$destination$waypoints&mode=walking&key=$apiKey"
    val client = OkHttpClient()
    val request = Request.Builder().url(url).build()
    try {
        val response = client.newCall(request).execute()
        if (!response.isSuccessful) return@withContext Pair(emptyList(), 0)
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
                for (i in 0 until legs.length()) { totalDistance += legs.getJSONObject(i).getJSONObject("distance").getInt("value") }
                return@withContext Pair(path, totalDistance)
            }
        }
        Pair(emptyList(), 0)
    } catch (e: Exception) { Pair(emptyList(), 0) }
}

@Composable
fun RouteList(
    routes: List<Route>,
    allUsers: List<User>,
    onRouteClick: (Route) -> Unit
) {
    // Kreiramo mapu UID
    val userMap = allUsers.associateBy({ it.id }, { it.fullName })

    LazyColumn(
        contentPadding = PaddingValues(top = 180.dp, start = 16.dp, end = 16.dp, bottom = 16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        if (routes.isEmpty()) {
            item {
                Text(
                    text = "Nema ruta koje odgovaraju filterima.",
                    modifier = Modifier.fillMaxWidth().padding(32.dp),
                    textAlign = TextAlign.Center,
                    style = MaterialTheme.typography.bodyLarge
                )
            }
        } else {
            items(routes) { route ->
                Card(
                    modifier = Modifier.fillMaxWidth().clickable { onRouteClick(route) },
                    elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(route.name, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
                        Spacer(modifier = Modifier.height(4.dp))
                        Text("Dužina: %.2f km".format(route.distance / 1000.0), style = MaterialTheme.typography.bodyMedium)

                        //ime autora preko ID-a
                        val authorName = userMap[route.authorId] ?: "Nepoznat autor"
                        Text("Autor: $authorName", style = MaterialTheme.typography.bodySmall, color = Color.Gray)
                    }
                }
            }
        }
    }
}

private fun decodePoly(encoded: String): List<LatLng> {
    val poly = ArrayList<LatLng>()
    var index = 0; val len = encoded.length; var lat = 0; var lng = 0
    while (index < len) {
        var b: Int; var shift = 0; var result = 0
        do { b = encoded[index++].code - 63; result = result or (b and 0x1f shl shift); shift += 5 } while (b >= 0x20)
        val dlat = if (result and 1 != 0) (result shr 1).inv() else result shr 1
        lat += dlat
        shift = 0; result = 0
        do { b = encoded[index++].code - 63; result = result or (b and 0x1f shl shift); shift += 5 } while (b >= 0x20)
        val dlng = if (result and 1 != 0) (result shr 1).inv() else result shr 1
        lng += dlng
        poly.add(LatLng(lat.toDouble() / 1E5, lng.toDouble() / 1E5))
    }
    return poly
}