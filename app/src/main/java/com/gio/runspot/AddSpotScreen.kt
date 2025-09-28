package com.gio.runspot

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.GeoPoint
import com.google.firebase.firestore.FieldValue
import java.util.Date

@Composable
fun AddSpotScreen(
    // NOVO: Prima i ID rute
    routeId: String,
    latitude: Double,
    longitude: Double,
    onSpotAdded: () -> Unit
) {
    var spotType by remember { mutableStateOf("") }
    var description by remember { mutableStateOf("") }
    var rating by remember { mutableStateOf("") }
    val context = LocalContext.current

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.scrim.copy(alpha = 0.6f)),
        contentAlignment = Alignment.Center
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            shape = MaterialTheme.shapes.medium,
            shadowElevation = 8.dp
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(text = "Dodaj novi Spot na rutu")
                Spacer(modifier = Modifier.height(8.dp))
                Text(text = "Lokacija: ${"%.5f".format(latitude)}, ${"%.5f".format(longitude)}")
                Spacer(modifier = Modifier.height(16.dp))
                OutlinedTextField(value = spotType, onValueChange = { spotType = it }, label = { Text("Tip (npr. Česma, Opasnost, Pogled)") }, modifier = Modifier.fillMaxWidth())
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(value = description, onValueChange = { description = it }, label = { Text("Kratak opis") }, modifier = Modifier.fillMaxWidth())
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(value = rating, onValueChange = { rating = it }, label = { Text("Ocena (1-5)") }, modifier = Modifier.fillMaxWidth())
                Spacer(modifier = Modifier.height(16.dp))

                Row {
                    Button(onClick = {
                        if (spotType.isBlank() || description.isBlank() || rating.isBlank()) {
                            Toast.makeText(context, "Sva polja su obavezna", Toast.LENGTH_SHORT).show()
                            return@Button
                        }
                        val db = FirebaseFirestore.getInstance()
                        val currentUser = FirebaseAuth.getInstance().currentUser
                        if (currentUser == null) {
                            Toast.makeText(context, "Greška: Korisnik nije prijavljen", Toast.LENGTH_SHORT).show()
                            return@Button
                        }

                        val spotData = hashMapOf(
                            "routeId" to routeId, // NOVO: Čuvamo ID rute
                            "type" to spotType,
                            "description" to description,
                            "rating" to rating.toIntOrNull(),
                            "location" to GeoPoint(latitude, longitude),
                            "authorId" to currentUser.uid,
                            "createdAt" to Date()
                        )

                        db.collection("spots")
                            .add(spotData)
                            .addOnSuccessListener {
                                val userRef = db.collection("users").document(currentUser.uid)
                                // Dodeljujemo 2 poena za novi spot
                                userRef.update("points", FieldValue.increment(2))
                                    .addOnSuccessListener {
                                        Toast.makeText(context, "Spot dodat! Osvojili ste 2 poena.", Toast.LENGTH_SHORT).show()
                                        onSpotAdded()
                                    }
                                    .addOnFailureListener {
                                        Toast.makeText(context, "Spot dodat (greška pri dodeli poena).", Toast.LENGTH_SHORT).show()
                                        onSpotAdded()
                                    }
                            }
                            .addOnFailureListener { e ->
                                Toast.makeText(context, "Greška: ${e.message}", Toast.LENGTH_LONG).show()
                            }
                    }) {
                        Text(text = "Sačuvaj")
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Button(onClick = onSpotAdded) {
                        Text(text = "Odustani")
                    }
                }
            }
        }
    }
}