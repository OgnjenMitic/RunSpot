package com.gio.runspot

import com.google.firebase.firestore.GeoPoint
import java.util.Date

data class Route(
    val id: String = "",
    val name: String = "",
    val pathPoints: List<GeoPoint> = emptyList(),
    val distance: Int = 0,
    val authorId: String = "",
    val createdAt: Date = Date()
)