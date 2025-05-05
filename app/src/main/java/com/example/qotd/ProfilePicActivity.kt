package com.example.qotd

import android.content.Intent
import android.graphics.Bitmap
import android.graphics.ImageDecoder
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.util.Size
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.RequiresApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.rememberAsyncImagePainter
import com.canhub.cropper.*
import com.example.qotd.ui.theme.QOTDTheme
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import com.google.firebase.storage.FirebaseStorage
import kotlinx.coroutines.*
import kotlinx.coroutines.tasks.await
import java.io.ByteArrayOutputStream

class ProfilePicActivity : ComponentActivity() {

    private val profileImages = listOf(
        R.drawable.bear, R.drawable.mouse, R.drawable.penguin, R.drawable.cat,
        R.drawable.cow, R.drawable.crocodile, R.drawable.hedgehog,
        R.drawable.fox, R.drawable.frog, R.drawable.racoon,
        R.drawable.elephant, R.drawable.panda, R.drawable.kangaroo, R.drawable.koala,
        R.drawable.lion, R.drawable.monkey, R.drawable.beaver, R.drawable.owl,
        R.drawable.husky, R.drawable.parrot, R.drawable.black_panther, R.drawable.puma,
        R.drawable.rabbit, R.drawable.german_shepherd, R.drawable.squirrel, R.drawable.unicorn,
        R.drawable.whale, R.drawable.octopus, R.drawable.ostrich, R.drawable.snake,
        R.drawable.monster, R.drawable.monster_1, R.drawable.monster_2, R.drawable.monster_3,
        R.drawable.monster_4, R.drawable.monster_5, R.drawable.monster_6, R.drawable.monster_7,
        R.drawable.monster_8, R.drawable.monster_9, R.drawable.monster_10,
        R.drawable.android, R.drawable.cowboy, R.drawable.frog_prince, R.drawable.death, R.drawable.dragon, R.drawable.gnome,
        R.drawable.humanoid, R.drawable.skeleton,
        R.drawable.wizard
    )

    @OptIn(ExperimentalMaterial3Api::class)
    @RequiresApi(Build.VERSION_CODES.Q)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        val isFirstTime = intent.getBooleanExtra("isFirstTime", false)

        fun exitProfilePic() {
            if (isFirstTime) {
                Intent(this, MainActivity::class.java).apply {
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                }.also(::startActivity)
            } else {
                finish()
            }
        }

        setContent {
            QOTDTheme {
                val userId = FirebaseAuth.getInstance().currentUser?.uid
                val context = LocalContext.current

                var selectedImage by remember { mutableStateOf<Int?>(null) }
                var customImageUri by remember { mutableStateOf<Uri?>(null) }
                var savedImageUrl by remember { mutableStateOf<String?>(null) }
                var isLoading by remember { mutableStateOf(false) }

                val cropImageLauncher = rememberLauncherForActivityResult(
                    contract = CropImageContract()
                ) { result ->
                    if (result.isSuccessful) {
                        customImageUri = result.uriContent
                        selectedImage = null
                    }
                }

                val pickImageLauncher = rememberLauncherForActivityResult(
                    contract = ActivityResultContracts.GetContent()
                ) { uri ->
                    uri?.let {
                        if (context.contentResolver.persistedUriPermissions.none { it.uri == uri }) {
                            context.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
                        }
                        cropImageLauncher.launch(
                            CropImageContractOptions(
                                uri,
                                CropImageOptions().apply {
                                    guidelines = CropImageView.Guidelines.ON
                                    cropShape = CropImageView.CropShape.OVAL
                                    aspectRatioX = 1
                                    aspectRatioY = 1
                                    fixAspectRatio = true
                                    showCropOverlay = true
                                    allowFlipping = false
                                    allowRotation = false
                                }
                            )
                        )
                    }
                }

                LaunchedEffect(userId) {
                    userId?.let {
                        FirebaseFirestore.getInstance().collection("users").document(it)
                            .get().addOnSuccessListener { doc ->
                                val savedProfilePic = doc.getString("profilePicture")
                                savedImageUrl = savedProfilePic

                                when {
                                    savedProfilePic == null -> {
                                        selectedImage = null
                                        customImageUri = null
                                    }
                                    savedProfilePic.startsWith("drawable/") -> {
                                        val resId = context.resources.getIdentifier(
                                            savedProfilePic.removePrefix("drawable/"),
                                            "drawable",
                                            context.packageName
                                        )
                                        selectedImage = if (resId != 0) resId else null
                                        customImageUri = null
                                    }
                                    else -> {
                                        selectedImage = null
                                        customImageUri = Uri.parse(savedProfilePic)
                                    }
                                }
                            }
                    }
                }

                Scaffold(
                    topBar = {
                        TopAppBar(
                            title = { Text("Select Profile Image", modifier = Modifier.fillMaxWidth()) },
                            navigationIcon = {
                                IconButton(onClick = { onBackPressedDispatcher.onBackPressed() }) {
                                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                                }
                            }
                        )
                    },
                    bottomBar = {
                        Button(
                            onClick = {
                                userId?.let {
                                    val db = FirebaseFirestore.getInstance()

                                    val alreadySaved = when {
                                        customImageUri != null -> savedImageUrl == customImageUri.toString()
                                        selectedImage != null -> savedImageUrl == "drawable/${resources.getResourceEntryName(selectedImage!!)}"
                                        else -> true
                                    }

                                    if (alreadySaved) {
                                        Toast.makeText(context, "No changes made.", Toast.LENGTH_SHORT).show()
                                        exitProfilePic()
                                        return@let
                                    }

                                    if (customImageUri != null) {
                                        CoroutineScope(Dispatchers.IO).launch {
                                            try {
                                                withContext(Dispatchers.Main) { isLoading = true }

                                                val bitmap = ImageDecoder.decodeBitmap(
                                                    ImageDecoder.createSource(contentResolver, customImageUri!!)
                                                )
                                                val baos = ByteArrayOutputStream()
                                                bitmap.compress(Bitmap.CompressFormat.JPEG, 100, baos)
                                                val imageData = baos.toByteArray()

                                                val profileImagesRef = FirebaseStorage.getInstance().reference
                                                    .child("profile_pictures/${userId}.jpg")

                                                profileImagesRef.putBytes(imageData).await()
                                                Log.d("ProfilePic", "Image uploaded successfully to Storage.")

                                                val downloadUrl = profileImagesRef.downloadUrl.await()
                                                Log.d("ProfilePic", "Download URL: $downloadUrl")

                                                val userMap = mapOf("profilePicture" to downloadUrl.toString())
                                                db.collection("users").document(userId)
                                                    .set(userMap, SetOptions.merge()).await()

                                                withContext(Dispatchers.Main) {
                                                    savedImageUrl = downloadUrl.toString()
                                                    Toast.makeText(context, "Profile picture updated!", Toast.LENGTH_SHORT).show()
                                                    isLoading = false
                                                    exitProfilePic()
                                                }

                                            } catch (e: Exception) {
                                                Log.e("ProfilePic", "Upload failed: ${e.localizedMessage}", e)
                                                withContext(Dispatchers.Main) {
                                                    Toast.makeText(context, "Upload failed: ${e.localizedMessage}", Toast.LENGTH_LONG).show()
                                                    isLoading = false
                                                }
                                            }
                                        }
                                    } else {
                                        selectedImage?.let { resId ->
                                            val profilePicRef = "drawable/${resources.getResourceEntryName(resId)}"
                                            CoroutineScope(Dispatchers.IO).launch {
                                                db.collection("users").document(userId)
                                                    .set(mapOf("profilePicture" to profilePicRef), SetOptions.merge())
                                                    .addOnSuccessListener {
                                                        runOnUiThread {
                                                            Toast.makeText(context, "Profile picture updated!", Toast.LENGTH_SHORT).show()
                                                            exitProfilePic()
                                                        }
                                                    }
                                                    .addOnFailureListener { e ->
                                                        Log.e("ProfilePic", "Error saving drawable: ${e.message}", e)
                                                    }
                                            }
                                        }
                                    }
                                }
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(start = 16.dp, end = 16.dp, bottom = 32.dp)
                        ) {
                            Text("Done", style = MaterialTheme.typography.bodyLarge.copy(fontSize = 24.sp))
                        }

                    }
                ) { innerPadding ->

                    if (isLoading) {
                        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            CircularProgressIndicator()
                        }
                    } else {
                        Column(
                            modifier = Modifier
                                .padding(innerPadding)
                                .fillMaxSize(),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Spacer(modifier = Modifier.height(16.dp))

                            when {
                                customImageUri != null -> {
                                    Image(
                                        painter = rememberAsyncImagePainter(customImageUri),
                                        contentDescription = null,
                                        modifier = Modifier.size(100.dp).clip(CircleShape)
                                    )
                                }

                                selectedImage != null -> {
                                    Image(
                                        painter = painterResource(id = selectedImage!!),
                                        contentDescription = null,
                                        modifier = Modifier.size(100.dp).clip(CircleShape)
                                    )
                                }
                                savedImageUrl != null -> {
                                    Image(
                                        painter = rememberAsyncImagePainter(model = savedImageUrl),
                                        contentDescription = null,
                                        modifier = Modifier.size(100.dp).clip(CircleShape)
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.height(16.dp))

                            LazyVerticalGrid(columns = GridCells.Fixed(3), contentPadding = PaddingValues(8.dp)) {
                                item {
                                    Box(
                                        modifier = Modifier
                                            .size(100.dp)
                                            .clickable { pickImageLauncher.launch("image/*") },
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text("+", style = MaterialTheme.typography.headlineMedium)
                                    }
                                }

                                items(profileImages) { imageRes ->
                                    Image(
                                        painter = painterResource(id = imageRes),
                                        contentDescription = null,
                                        modifier = Modifier
                                            .size(100.dp)
                                            .padding(8.dp)
                                            .clickable {
                                                selectedImage = imageRes
                                                customImageUri = null
                                            }
                                    )
                                }

                                item(span = { GridItemSpan(3) }) {
                                    Text(
                                        text = "Icons made by Darius Dan from www.flaticon.com",
                                        style = MaterialTheme.typography.bodyMedium.copy(
                                            color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f)
                                        ),
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(16.dp),
                                        textAlign = TextAlign.Center
                                    )
                                }

                            }
                        }
                    }
                }
            }
        }
    }
}