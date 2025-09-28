package com.gio.runspot

import androidx.compose.foundation.layout.*
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun SpotDetailsDialog(
    spot: Spot,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(text = spot.type, fontWeight = FontWeight.Bold, fontSize = 20.sp) },
        text = {
            Column {
                Text(text = "Opis: ${spot.description}")
                Spacer(modifier = Modifier.height(8.dp))
                Text(text = "Ocena: ${spot.rating}/5")
                // TODO: Kasnije možemo dodati i ime autora
            }
        },
        confirmButton = {
            Button(onClick = onDismiss) {
                Text("Zatvori")
            }
        }
    )
}