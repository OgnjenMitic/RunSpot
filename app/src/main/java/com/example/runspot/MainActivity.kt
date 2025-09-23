package com.gio.runspot // TVOJ ISPRAVAN PACKAGE NAME

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
// import com.gio.runspot.ui.theme.RunSpotTheme // Ovu liniju ćemo možda kasnije dodati

class MainActivity : ComponentActivity() { // <-- VAŽNA PROMENA! Ne koristi se AppCompatActivity
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // setContent je glavna komanda za pokretanje Compose-a
        setContent {
            // RunSpotTheme { // Privremeno isključujemo našu temu, da pojednostavimo
            // A surface container using the 'background' color from the theme
            Surface(
                modifier = Modifier.fillMaxSize(),
                color = MaterialTheme.colorScheme.background
            ) {
                Greeting("Android")
            }
            // }
        }
    }
}

// Ovo je primer jedne Composable funkcije koja iscrtava tekst
@Composable
fun Greeting(name: String, modifier: Modifier = Modifier) {
    Text(
        text = "Hello $name!",
        modifier = modifier
    )
}

// Ovo omogućava da vidiš pregled dizajna u Android Studiju
@Preview(showBackground = true)
@Composable
fun GreetingPreview() {
    // RunSpotTheme {
    Greeting("Android")
    // }
}