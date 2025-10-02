package com.gio.runspot

import android.util.Log
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import coil.compose.AsyncImage
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RankingScreen(
    onNavigateBack: () -> Unit
) {
    var userList by remember { mutableStateOf<List<User>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }

    // --- NOVO: Stanje za prikaz uvećane slike ---
    var selectedImageUrl by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(Unit) {
        val db = FirebaseFirestore.getInstance()
        db.collection("users")
            .orderBy("points", Query.Direction.DESCENDING)
            .get()
            .addOnSuccessListener { result ->
                userList = result.toObjects(User::class.java)
                isLoading = false
            }
            .addOnFailureListener { exception ->
                Log.w("RankingScreen", "Error getting documents.", exception)
                isLoading = false
            }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Rang Lista") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Nazad"
                        )
                    }
                }
            )
        }
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues),
            contentAlignment = Alignment.Center
        ) {
            if (isLoading) {
                CircularProgressIndicator()
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(16.dp)
                ) {
                    itemsIndexed(userList) { index, user ->
                        UserRankingCard(
                            rank = index + 1,
                            user = user,
                            // NOVO: Prosleđujemo lambda funkciju za klik na sliku
                            onImageClick = {
                                if (user.profileImageUrl != null) {
                                    selectedImageUrl = user.profileImageUrl
                                }
                            }
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                    }
                }
            }
        }
    }

    // --- NOVO: Dijalog za prikaz uvećane slike ---
    if (selectedImageUrl != null) {
        Dialog(onDismissRequest = { selectedImageUrl = null }) {
            AsyncImage(
                model = selectedImageUrl,
                contentDescription = "Uvećana profilna slika",
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(MaterialTheme.shapes.large)
            )
        }
    }
}

@Composable
fun UserRankingCard(
    rank: Int,
    user: User,
    onImageClick: () -> Unit // NOVO: Parametar za klik
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // --- NOVO: Prikaz slike korisnika ---
            AsyncImage(
                model = user.profileImageUrl,
                contentDescription = "Profilna slika korisnika ${user.fullName}",
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .clickable(onClick = onImageClick), // Omogućavamo klik
                contentScale = ContentScale.Crop,
                // Možemo dodati i placeholder sliku ako želimo
                // placeholder = painterResource(id = R.drawable.ic_profile_placeholder)
            )

            Spacer(modifier = Modifier.width(16.dp))

            // Tekst sa imenom i rangom
            Text(
                text = "$rank. ${user.fullName}",
                fontWeight = FontWeight.Bold,
                modifier = Modifier.weight(1f) // Zauzima sav preostali prostor
            )

            // Tekst sa poenima
            Text(text = "${user.points} poena")
        }
    }
}