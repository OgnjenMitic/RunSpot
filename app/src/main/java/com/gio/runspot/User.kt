package com.gio.runspot

import androidx.annotation.Keep

@Keep
data class User(
    val email: String = "",
    val fullName: String = "",
    val phoneNumber: String = "",
    val points: Long = 0,
    val profileImageUrl: String? = null // NOVO: URL do profilne slike
)