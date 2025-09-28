package com.gio.runspot

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

object LocationServiceState {
    var isRunning by mutableStateOf(false)
}