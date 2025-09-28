package com.gio.runspot

import android.widget.Toast
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.google.android.gms.maps.model.LatLng
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.GeoPoint
import com.google.firebase.firestore.FieldValue
import java.util.Date


@Composable
fun AddRouteDialog(
    pathPoints: List<LatLng>,
    distanceInMeters: Int,
    onDismiss: () -> Unit
) {
    var routeName by remember { mutableStateOf("") }
    val context = LocalContext.current

    val startPoint = pathPoints.firstOrNull()
    val endPoint = pathPoints.lastOrNull()

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(text = "Sačuvaj novu rutu") },
        text = {
            Column {
                if (startPoint != null && endPoint != null) {
                    Text("Start: ${"%.5f".format(startPoint.latitude)}, ${"%.5f".format(startPoint.longitude)}")
                    Text("Cilj: ${"%.5f".format(endPoint.latitude)}, ${"%.5f".format(endPoint.longitude)}")
                    Text("Dužina: %.2f km".format(distanceInMeters / 1000.0))
                }
                Spacer(modifier = Modifier.height(16.dp))
                OutlinedTextField(
                    value = routeName,
                    onValueChange = { routeName = it },
                    label = { Text("Ime rute") }
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (routeName.isBlank()) {
                        Toast.makeText(context, "Ime rute je obavezno", Toast.LENGTH_SHORT).show()
                        return@Button
                    }
                    val db = FirebaseFirestore.getInstance()
                    val currentUser = FirebaseAuth.getInstance().currentUser
                    if (currentUser == null) {
                        Toast.makeText(context, "Greška: Korisnik nije prijavljen", Toast.LENGTH_SHORT).show()
                        return@Button
                    }

                    val routeData = hashMapOf(
                        "name" to routeName,
                        "pathPoints" to pathPoints.map { GeoPoint(it.latitude, it.longitude) },
                        "distance" to distanceInMeters,
                        "authorId" to currentUser.uid,
                        "createdAt" to Date()
                    )

                    db.collection("routes")
                        .add(routeData)
                        .addOnSuccessListener {
                            val userRef = db.collection("users").document(currentUser.uid)
                            // Dodeljujemo 10 poena za novu rutu
                            userRef.update("points", FieldValue.increment(10))
                                .addOnSuccessListener {
                                    Toast.makeText(context, "Ruta sačuvana! Osvojili ste 10 poena.", Toast.LENGTH_SHORT).show()
                                    onDismiss()
                                }
                                .addOnFailureListener {
                                    // Ako dodela poena ne uspe, i dalje obavesti korisnika da je ruta sačuvana
                                    Toast.makeText(context, "Ruta sačuvana (greška pri dodeli poena).", Toast.LENGTH_SHORT).show()
                                    onDismiss()
                                }
                        }
                        .addOnFailureListener { e ->
                            Toast.makeText(context, "Greška: ${e.message}", Toast.LENGTH_LONG).show()
                        }
                }
            ) {
                Text("Sačuvaj")
            }
        },
        dismissButton = {
            Button(onClick = onDismiss) {
                Text("Odustani")
            }
        }
    )
}