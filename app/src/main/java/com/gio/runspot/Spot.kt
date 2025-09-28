package com.gio.runspot

import com.google.firebase.firestore.GeoPoint
import java.util.Date

data class Spot(
    val id: String = "",
    // NOVO: ID rute kojoj ovaj spot pripada
    val routeId: String = "",
    val type: String = "",
    val description: String = "",
    val rating: Int = 0,
    val location: GeoPoint = GeoPoint(0.0, 0.0),
    val authorId: String = "",
    val createdAt: Date = Date()
)