package com.example.shit

import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.example.shit.ui.theme.ShitTheme
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import androidx.compose.runtime.*
import androidx.compose.material3.*
import androidx.compose.ui.unit.sp

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            ShitTheme {
                val snackbarHostState = remember { SnackbarHostState() }
                val scope = rememberCoroutineScope()
                var storedText by remember { mutableStateOf("") } // Store the user's input

                Scaffold(
                    modifier = Modifier.fillMaxSize(),
                    snackbarHost = { SnackbarHost(hostState = snackbarHostState) }
                ) { innerPadding ->
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(innerPadding),
                        verticalArrangement = Arrangement.Center,
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Greeting(name = "Android")

                        TextEntryComposable(
                            scope = scope,
                            snackbarHostState = snackbarHostState,
                            storedText = storedText,
                            onTextChanged = { newText -> storedText = newText } // Update state
                        )

                        // Display stored text visibly in UI
                        if (storedText.isNotEmpty()) {
                            Text(
                                text = "Stored Input: $storedText",
                                modifier = Modifier.padding(top = 16.dp),
                                fontSize = 20.sp
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun Greeting(name: String, modifier: Modifier = Modifier) {
    Text(
        text = "What's goin' on?",
        modifier = modifier,
        fontSize = 32.sp, // fontsize
    )
}

// text input  shit
@Composable
fun TextEntryComposable(
    modifier: Modifier = Modifier,
    scope: CoroutineScope,
    snackbarHostState: SnackbarHostState,
    storedText: String,
    onTextChanged: (String) -> Unit
) {
    var input by remember { mutableStateOf("") }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.Bottom,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        TextField(
            value = input,
            onValueChange = {
                input = it
                onTextChanged(it) // Store the input globally
            },
            label = { Text("Enter text") },
            placeholder = { Text(text = "Type something...") }
        )

        val context = LocalContext.current
        val controller = LocalSoftwareKeyboardController.current

        Button(
            onClick = {
                controller?.hide()
                scope.launch {
                    val result: SnackbarResult = snackbarHostState.showSnackbar(
                        message = "Input stored successfully!",
                        actionLabel = "Show Info",
                        duration = SnackbarDuration.Indefinite
                    )

                    if (result == SnackbarResult.ActionPerformed) {
                        Toast.makeText(context, "Stored: $storedText", Toast.LENGTH_SHORT).show()
                    }
                }
            }
        ) {
            Text(text = "Submit")
        }
    }
}

@Preview(showBackground = true)
@Composable
fun GreetingPreview() {
    ShitTheme {
        Greeting("Android")
    }
}