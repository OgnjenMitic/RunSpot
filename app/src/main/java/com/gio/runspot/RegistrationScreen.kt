package com.gio.runspot

import android.Manifest
import android.content.Context
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import coil.compose.AsyncImage
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.isGranted
import com.google.accompanist.permissions.rememberPermissionState
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.storage.FirebaseStorage
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import java.io.File

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

    var imageUri by remember { mutableStateOf<Uri?>(null) }
    var isLoading by remember { mutableStateOf(false) }
    var showImageSourceDialog by remember { mutableStateOf(false) }

    val galleryLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent(),
        onResult = { uri -> imageUri = uri }
    )
    val cameraLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.TakePicture(),
        onResult = { success -> if (!success) { imageUri = null } }
    )
    val cameraPermissionState = rememberPermissionState(Manifest.permission.CAMERA)

    Box(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier.fillMaxSize().padding(16.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(text = "Kreiraj nalog")
            Spacer(modifier = Modifier.height(16.dp))

            Box(
                modifier = Modifier.size(120.dp).clip(CircleShape).clickable { showImageSourceDialog = true },
                contentAlignment = Alignment.Center
            ) {
                if (imageUri != null) {
                    AsyncImage(model = imageUri, contentDescription = "Profilna slika", modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
                } else {
                    Text("Dodaj sliku", style = MaterialTheme.typography.bodyMedium)
                }
            }
            Spacer(modifier = Modifier.height(16.dp))

            OutlinedTextField(value = email, onValueChange = { email = it }, label = { Text("Email") })
            Spacer(modifier = Modifier.height(8.dp))
            OutlinedTextField(
                value = password,
                onValueChange = { password = it }, // Dozvoljavamo unos svega
                label = { Text("Šifra (min. 6 karaktera)") },
                visualTransformation = if (isPasswordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                trailingIcon = {
                    IconButton(onClick = { isPasswordVisible = !isPasswordVisible }) {
                        Icon(imageVector = if (isPasswordVisible) Icons.Filled.Visibility else Icons.Filled.VisibilityOff, contentDescription = "Toggle password visibility")
                    }
                }
            )
            Spacer(modifier = Modifier.height(8.dp))
            OutlinedTextField(value = fullName, onValueChange = { fullName = it }, label = { Text("Ime i prezime") })
            Spacer(modifier = Modifier.height(8.dp))
            OutlinedTextField(
                value = phoneNumber,
                onValueChange = {
                    // Dozvoljavamo unos samo 10 cifara
                    if (it.length <= 10 && it.all { char -> char.isDigit() }) {
                        phoneNumber = it
                    }
                },
                label = { Text("Broj telefona (10 cifara)") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone)
            )
            Spacer(modifier = Modifier.height(16.dp))

            Button(
                onClick = {
                    if (email.isBlank() || password.isBlank() || fullName.isBlank() || phoneNumber.isBlank()) {
                        Toast.makeText(context, "Sva polja moraju biti popunjena", Toast.LENGTH_SHORT).show()
                        return@Button
                    }
                    if (password.length < 6) {
                        Toast.makeText(context, "Šifra mora imati najmanje 6 karaktera", Toast.LENGTH_SHORT).show()
                        return@Button
                    }
                    if (phoneNumber.length != 10) {
                        Toast.makeText(context, "Broj telefona mora imati tačno 10 cifara", Toast.LENGTH_SHORT).show()
                        return@Button
                    }
                    if (imageUri == null) {
                        Toast.makeText(context, "Molimo, izaberite profilnu sliku", Toast.LENGTH_SHORT).show()
                        return@Button
                    }

                    coroutineScope.launch {
                        isLoading = true
                        try {
                            val auth = FirebaseAuth.getInstance()
                            val db = FirebaseFirestore.getInstance()
                            val storage = FirebaseStorage.getInstance()
                            val authResult = auth.createUserWithEmailAndPassword(email, password).await()
                            val userId = authResult.user?.uid

                            if (userId != null) {
                                val storageRef = storage.reference.child("profile_images/$userId.jpg")
                                val uploadTask = storageRef.putFile(imageUri!!).await()
                                val downloadUrl = uploadTask.storage.downloadUrl.await().toString()
                                val userMap = hashMapOf(
                                    "email" to email, "fullName" to fullName, "phoneNumber" to phoneNumber,
                                    "points" to 0, "profileImageUrl" to downloadUrl
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
                enabled = !isLoading
            ) {
                Text(text = "Registruj se")
            }
        }

        if (isLoading) {
            CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
        }

        if (showImageSourceDialog) {
            AlertDialog(
                onDismissRequest = { showImageSourceDialog = false },
                title = { Text("Izaberi izvor slike") },
                text = { Text("Odakle želite da izaberete sliku?") },
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

object ComposeFileProvider {
    fun getImageUri(context: Context): Uri {
        val directory = File(context.cacheDir, "images")
        directory.mkdirs()
        val file = File.createTempFile("selected_image_", ".jpg", directory)
        val authority = context.packageName + ".provider"
        return FileProvider.getUriForFile(context, authority, file)
    }
}