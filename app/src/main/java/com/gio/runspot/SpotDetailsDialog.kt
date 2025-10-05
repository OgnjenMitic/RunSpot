package com.gio.runspot

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import coil.compose.AsyncImage

@Composable
fun SpotDetailsDialog(
    spot: Spot,
    onDismiss: () -> Unit
) {
    // Stanje za prikaz uvećane slike
    var showEnlargedImage by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(text = spot.type, fontWeight = FontWeight.Bold, fontSize = 20.sp) },
        text = {
            Column {
                // Prikaz slike spota
                spot.imageUrl?.let { imageUrl ->
                    AsyncImage(
                        model = imageUrl,
                        contentDescription = "Slika spota: ${spot.type}",
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(150.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .clickable { showEnlargedImage = true }, // Klik za uvećanje
                        contentScale = ContentScale.Crop
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                }

                Text(text = "Opis: ${spot.description}")
                Spacer(modifier = Modifier.height(8.dp))
                Text(text = "Ocena: ${spot.rating}/5")
            }
        },
        confirmButton = {
            Button(onClick = onDismiss) {
                Text("Zatvori")
            }
        }
    )

    //  prikaz uvećane slike
    if (showEnlargedImage) {
        Dialog(onDismissRequest = { showEnlargedImage = false }) {
            spot.imageUrl?.let {
                AsyncImage(
                    model = it,
                    contentDescription = "Uvećana slika spota",
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(MaterialTheme.shapes.large)
                )
            }
        }
    }
}