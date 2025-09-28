package com.gio.runspot

// Koristimo @Keep da bismo osigurali da Proguard ne obriše polja
// kada budemo pravili finalnu verziju aplikacije.
import androidx.annotation.Keep

@Keep
data class User(
    val email: String = "",
    val fullName: String = "",
    val phoneNumber: String = "",
    val points: Long = 0 // Poeni su Long tipa u Firestore-u
)