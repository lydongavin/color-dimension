package com.example.colordimension

import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import com.example.colordimension.ui.theme.ColorDimensionTheme
import java.io.File
import androidx.core.graphics.get
import kotlin.math.sqrt

fun getColorNameFromRgb(r: Int, g: Int, b: Int): String {
    return when {
        r > 200 && g < 80 && b < 80 -> "Red"
        r < 80 && g > 200 && b < 80 -> "Green"
        r < 80 && g < 80 && b > 200 -> "Blue"
        r > 200 && g > 200 && b < 100 -> "Yellow"
        r > 200 && g < 100 && b > 200 -> "Magenta"
        r < 100 && g > 200 && b > 200 -> "Cyan"
        r > 230 && g > 230 && b > 230 -> "White"
        r < 50 && g < 50 && b < 50 -> "Black"
        r in 100..180 && g in 100..180 && b in 100..180 -> "Gray"
        else -> {
            val colorMap = mapOf(
                "White" to Triple(255, 255, 255),
                "Black" to Triple(0, 0, 0),
                "Red" to Triple(255, 0, 0),
                "Lime" to Triple(0, 255, 0),
                "Blue" to Triple(0, 0, 255),
                "Yellow" to Triple(255, 255, 0),
                "Cyan" to Triple(0, 255, 255),
                "Magenta" to Triple(255, 0, 255),
                "Silver" to Triple(192, 192, 192),
                "Gray" to Triple(128, 128, 128),
                "Maroon" to Triple(128, 0, 0),
                "Olive" to Triple(128, 128, 0),
                "Green" to Triple(0, 128, 0),
                "Purple" to Triple(128, 0, 128),
                "Teal" to Triple(0, 128, 128),
                "Navy" to Triple(0, 0, 128),
                "Orange" to Triple(255, 165, 0),
                "Pink" to Triple(255, 192, 203),
                "Brown" to Triple(165, 42, 42),
                "Gold" to Triple(255, 215, 0),
                "Beige" to Triple(245, 245, 220)
            )

            var closestColor = "Unknown"
            var minDistance = Double.MAX_VALUE

            for ((name, rgb) in colorMap) {
                val distance = sqrt(
                    ((r - rgb.first) * (r - rgb.first) +
                     (g - rgb.second) * (g - rgb.second) +
                     (b - rgb.third) * (b - rgb.third)).toDouble()
                )
                if (distance < minDistance) {
                    minDistance = distance
                    closestColor = name
                }
            }
            closestColor
        }
    }
}

class MainActivity : ComponentActivity() {

    private var imageUri: Uri? = null

    private val imageUriState = mutableStateOf<Uri?>(null)

    private val takePictureLauncher =
        registerForActivityResult(ActivityResultContracts.TakePicture()) { success ->
            if (success) {
                imageUriState.value = imageUri
            }
        }

    private val selectPictureLauncher =
        registerForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
            uri?.let {
                imageUriState.value = it
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            ColorDimensionTheme {
                Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                    HomeScreen(
                        modifier = Modifier.padding(innerPadding),
                        imageUri = imageUriState.value,
                        onTakePicture = {
                            val photoFile = File.createTempFile("picture", ".jpg", cacheDir).apply {
                                createNewFile()
                                deleteOnExit()
                            }
                            imageUri = FileProvider.getUriForFile(
                                this,
                            "$packageName.fileprovider",
                                photoFile
                            )
                            takePictureLauncher.launch(imageUri)
                        },
                        onSelectPicture = {
                            selectPictureLauncher.launch("image/*")
                        }
                    )
                }
            }
        }
    }
}

@Composable
fun HomeScreen(
    modifier: Modifier = Modifier,
    imageUri: Uri? = null,
    onTakePicture: () -> Unit = {},
    onSelectPicture: () -> Unit = {}
) {
    val context = LocalContext.current
    var hexColor by remember { mutableStateOf<String?>(null) }
    var bitmap by remember(imageUri) { mutableStateOf<android.graphics.Bitmap?>(null) }

    LaunchedEffect(imageUri) {
        imageUri?.let { uri ->
            context.contentResolver.openInputStream(uri)?.use { stream ->
                bitmap = BitmapFactory.decodeStream(stream)
            }
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.Top,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = "Color Dimension",
            style = MaterialTheme.typography.headlineMedium,
            modifier = Modifier.padding(bottom = 16.dp)
        )
        Button(onClick = onTakePicture) {
            Text(text = "Color from Picture")
        }
        Spacer(modifier = Modifier.height(8.dp))
        Button(onClick = onSelectPicture) {
            Text(text = "Upload from Gallery")
        }
        Spacer(modifier = Modifier.height(16.dp))
        imageUri?.let {
            bitmap?.let { bmp ->
                Image(
                    bitmap = bmp.asImageBitmap(),
                    contentDescription = null,
                    modifier = Modifier
                        .fillMaxWidth()
                        .aspectRatio(bmp.width.toFloat() / bmp.height)
                        .pointerInput(Unit) {
                            detectTapGestures { offset: Offset ->
                                val x = (offset.x * bmp.width / this.size.width).toInt()
                                val y = (offset.y * bmp.height / this.size.height).toInt()
                                if (x in 0 until bmp.width && y in 0 until bmp.height) {
                                    val pixel = bmp[x, y]
                                    val r = (pixel shr 16) and 0xFF
                                    val g = (pixel shr 8) and 0xFF
                                    val b = pixel and 0xFF
                                    hexColor = getColorNameFromRgb(r, g, b)
                                }
                            }
                        }
                )
            }
        }
        hexColor?.let {
            Spacer(modifier = Modifier.height(16.dp))
            Text(text = "Color: $it", style = MaterialTheme.typography.bodyLarge)
        }
    }
}

@Preview(showBackground = true)
@Composable
fun HomeScreenPreview() {
    ColorDimensionTheme {
        HomeScreen()
    }
}