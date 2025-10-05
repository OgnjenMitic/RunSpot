package com.gio.runspot

import android.Manifest
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.isGranted
import com.google.accompanist.permissions.rememberPermissionState
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.GeoPoint
import com.google.firebase.storage.FirebaseStorage
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import java.util.*

@OptIn(ExperimentalPermissionsApi::class)
@Composable
fun AddSpotScreen(
    routeId: String,
    latitude: Double,
    longitude: Double,
    onSpotAdded: () -> Unit
) {
    var spotType by remember { mutableStateOf("") }
    var description by remember { mutableStateOf("") }
    var rating by remember { mutableStateOf("") }
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()


    var imageUri by remember { mutableStateOf<Uri?>(null) }
    var isLoading by remember { mutableStateOf(false) }
    var showImageSourceDialog by remember { mutableStateOf(false) }

    // Launcheri za galeriju i kameru
    val galleryLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent(),
        onResult = { uri -> imageUri = uri }
    )
    val cameraLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.TakePicture(),
        onResult = { success -> if (!success) imageUri = null }
    )
    val cameraPermissionState = rememberPermissionState(Manifest.permission.CAMERA)

    Box(
        modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.scrim.copy(alpha = 0.6f)),
        contentAlignment = Alignment.Center
    ) {
        Surface(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            shape = MaterialTheme.shapes.medium,
            shadowElevation = 8.dp
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(text = "Dodaj novi Spot na rutu")
                Spacer(modifier = Modifier.height(16.dp))

                // Prikaz i odabir slike
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(150.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant)
                        .clickable { showImageSourceDialog = true },
                    contentAlignment = Alignment.Center
                ) {
                    if (imageUri != null) {
                        AsyncImage(
                            model = imageUri, contentDescription = "Slika spota",
                            modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Crop
                        )
                    } else {
                        Text("Dodaj sliku (opciono)", style = MaterialTheme.typography.bodyMedium)
                    }
                }
                Spacer(modifier = Modifier.height(16.dp))

                OutlinedTextField(value = spotType, onValueChange = { spotType = it }, label = { Text("Tip (npr. Česma, Opasnost)") }, modifier = Modifier.fillMaxWidth())
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(value = description, onValueChange = { description = it }, label = { Text("Kratak opis") }, modifier = Modifier.fillMaxWidth())
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(
                    value = rating,
                    onValueChange = { if (it.length <= 1 && it.all { char -> char.isDigit() }) rating = it },
                    label = { Text("Ocena (1-5)") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(16.dp))

                Row {
                    Button(
                        onClick = {
                            if (spotType.isBlank() || description.isBlank() || rating.isBlank()) {
                                Toast.makeText(context, "Tip, opis i ocena su obavezni", Toast.LENGTH_SHORT).show()
                                return@Button
                            }
                            val ratingValue = rating.toIntOrNull()
                            if (ratingValue == null || ratingValue !in 1..5) {
                                Toast.makeText(context, "Ocena mora biti broj između 1 i 5", Toast.LENGTH_SHORT).show()
                                return@Button
                            }

                            coroutineScope.launch {
                                isLoading = true
                                try {
                                    val currentUser = FirebaseAuth.getInstance().currentUser
                                    if (currentUser == null) {
                                        Toast.makeText(context, "Greška: Korisnik nije prijavljen", Toast.LENGTH_SHORT).show()
                                        isLoading = false
                                        return@launch
                                    }

                                    var imageUrl: String? = null
                                    if (imageUri != null) {
                                        val storageRef = FirebaseStorage.getInstance().reference
                                            .child("spot_images/${UUID.randomUUID()}.jpg")
                                        imageUrl = storageRef.putFile(imageUri!!).await()
                                            .storage.downloadUrl.await().toString()
                                    }

                                    val spotData = hashMapOf(
                                        "routeId" to routeId,
                                        "type" to spotType,
                                        "description" to description,
                                        "rating" to ratingValue,
                                        "location" to GeoPoint(latitude, longitude),
                                        "authorId" to currentUser.uid,
                                        "createdAt" to Date(),
                                        "imageUrl" to imageUrl
                                    )

                                    val db = FirebaseFirestore.getInstance()
                                    db.collection("spots").add(spotData).await()

                                    val userRef = db.collection("users").document(currentUser.uid)
                                    userRef.update("points", FieldValue.increment(2)).await()

                                    Toast.makeText(context, "Spot dodat! Osvojili ste 2 poena.", Toast.LENGTH_SHORT).show()
                                    onSpotAdded()

                                } catch (e: Exception) {
                                    Toast.makeText(context, "Greška pri dodavanju spota: ${e.message}", Toast.LENGTH_LONG).show()
                                } finally {
                                    isLoading = false
                                }
                            }
                        },
                        enabled = !isLoading
                    ) {
                        Text(text = "Sačuvaj")
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Button(onClick = onSpotAdded, enabled = !isLoading) {
                        Text(text = "Odustani")
                    }
                }
            }
        }

        if (isLoading) {
            Box(modifier = Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.5f)), contentAlignment = Alignment.Center){
                CircularProgressIndicator()
            }
        }

        if (showImageSourceDialog) {
            AlertDialog(
                onDismissRequest = { showImageSourceDialog = false },
                title = { Text("Izaberi izvor slike") },
                confirmButton = {
                    Button(onClick = {
                        showImageSourceDialog = false
                        galleryLauncher.launch("image/*")
                    }) { Text("Galerija") }
                },
                dismissButton = {
                    Button(onClick = {
                        showImageSourceDialog = false
                        if (cameraPermissionState.status.isGranted) {
                            val uri = ComposeFileProvider.getImageUri(context)
                            imageUri = uri
                            cameraLauncher.launch(uri)
                        } else {
                            cameraPermissionState.launchPermissionRequest()
                        }
                    }) { Text("Kamera") }
                }
            )
        }
    }
}