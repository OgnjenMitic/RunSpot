package com.gio.runspot

import android.Manifest
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.rememberMultiplePermissionsState
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.storage.FirebaseStorage
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

@OptIn(ExperimentalPermissionsApi::class)
@Composable
fun RegistrationScreen(
    onRegistrationSuccess: () -> Unit
) {
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var fullName by remember { mutableStateOf("") }
    var phoneNumber by remember { mutableStateOf("") }
    var isPasswordVisible by remember { mutableStateOf(false) }
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    // --- NOVO: Stanja za sliku i Učitavanje ---
    var imageUri by remember { mutableStateOf<Uri?>(null) }
    var isLoading by remember { mutableStateOf(false) }

    // --- NOVO: Launcher za odabir slike iz galerije ---
    val galleryLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent(),
        onResult = { uri ->
            imageUri = uri
        }
    )

    // --- NOVO: Launcher i dozvole za kameru ---
    // Accompanist biblioteka za lakši rad sa dozvolama
    val cameraPermissionState = rememberMultiplePermissionsState(
        permissions = listOf(Manifest.permission.CAMERA)
    )
    val cameraLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.TakePicture(),
        onResult = { success ->
            // Ako je slikanje uspešno, URI je već sačuvan tamo gde smo ga prosledili
            if (!success) {
                // Ako korisnik otkaže slikanje, obriši privremeni URI
                imageUri = null
            }
        }
    )


    Box(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(text = "Kreiraj nalog")
            Spacer(modifier = Modifier.height(16.dp))

            // --- NOVO: Prikaz za sliku ---
            Box(
                modifier = Modifier
                    .size(120.dp)
                    .clip(CircleShape)
                    .clickable {
                        // TODO: Kasnije dodati izbor Kamera/Galerija
                        galleryLauncher.launch("image/*")
                    },
                contentAlignment = Alignment.Center
            ) {
                if (imageUri != null) {
                    AsyncImage(
                        model = imageUri,
                        contentDescription = "Profilna slika",
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop
                    )
                } else {
                    Text("Dodaj sliku", style = MaterialTheme.typography.bodyMedium)
                }
            }
            Spacer(modifier = Modifier.height(16.dp))

            OutlinedTextField(value = email, onValueChange = { email = it }, label = { Text("Email") })
            Spacer(modifier = Modifier.height(8.dp))

            OutlinedTextField(
                value = password,
                onValueChange = { password = it },
                label = { Text("Šifra") },
                visualTransformation = if (isPasswordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                trailingIcon = {
                    val image = if (isPasswordVisible) Icons.Filled.Visibility else Icons.Filled.VisibilityOff
                    IconButton(onClick = { isPasswordVisible = !isPasswordVisible }) {
                        Icon(imageVector = image, contentDescription = "Toggle password visibility")
                    }
                }
            )
            Spacer(modifier = Modifier.height(8.dp))

            OutlinedTextField(value = fullName, onValueChange = { fullName = it }, label = { Text("Ime i prezime") })
            Spacer(modifier = Modifier.height(8.dp))

            OutlinedTextField(
                value = phoneNumber,
                onValueChange = { phoneNumber = it },
                label = { Text("Broj telefona") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone)
            )
            Spacer(modifier = Modifier.height(16.dp))

            Button(
                onClick = {
                    if (email.isBlank() || password.isBlank() || fullName.isBlank() || phoneNumber.isBlank()) {
                        Toast.makeText(context, "Sva polja moraju biti popunjena", Toast.LENGTH_SHORT).show()
                        return@Button
                    }
                    if (imageUri == null) {
                        Toast.makeText(context, "Molimo, izaberite profilnu sliku", Toast.LENGTH_SHORT).show()
                        return@Button
                    }

                    // Pokrećemo korutinu za Firebase operacije
                    coroutineScope.launch {
                        isLoading = true
                        try {
                            val auth = FirebaseAuth.getInstance()
                            val db = FirebaseFirestore.getInstance()
                            val storage = FirebaseStorage.getInstance()

                            // 1. Kreiraj korisnika u Authentication
                            val authResult = auth.createUserWithEmailAndPassword(email, password).await()
                            val userId = authResult.user?.uid

                            if (userId != null) {
                                // 2. Upload-uj sliku na Storage
                                val storageRef = storage.reference.child("profile_images/$userId.jpg")
                                val uploadTask = storageRef.putFile(imageUri!!).await()
                                val downloadUrl = uploadTask.storage.downloadUrl.await().toString()

                                // 3. Sačuvaj podatke korisnika u Firestore, uključujući link slike
                                val userMap = hashMapOf(
                                    "email" to email,
                                    "fullName" to fullName,
                                    "phoneNumber" to phoneNumber,
                                    "points" to 0,
                                    "profileImageUrl" to downloadUrl // Čuvamo URL
                                )

                                db.collection("users").document(userId).set(userMap).await()

                                Toast.makeText(context, "Registracija uspešna!", Toast.LENGTH_SHORT).show()
                                onRegistrationSuccess()
                            }
                        } catch (e: Exception) {
                            Toast.makeText(context, "Registracija neuspešna: ${e.message}", Toast.LENGTH_LONG).show()
                        } finally {
                            isLoading = false
                        }
                    }
                },
                enabled = !isLoading // Onemogući dugme dok traje učitavanje
            ) {
                Text(text = "Registruj se")
            }
        }

        // Prikazuje indikator učitavanja preko celog ekrana
        if (isLoading) {
            CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
        }
    }
}