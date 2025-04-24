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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch

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

// Function to scan the entire bitmap for presence of a specific color
suspend fun checkColorInBitmap(bitmap: android.graphics.Bitmap?, colorToFind: String): Boolean {
  if (bitmap == null) return false

  return withContext(Dispatchers.Default) {
    // Sample pixels at intervals to improve performance
    val sampleStep = 10
    val colorSet = mutableSetOf<String>()

    for (x in 0 until bitmap.width step sampleStep) {
      for (y in 0 until bitmap.height step sampleStep) {
        val pixel = bitmap[x, y]
        val r = (pixel shr 16) and 0xFF
        val g = (pixel shr 8) and 0xFF
        val b = pixel and 0xFF
        val colorName = getColorNameFromRgb(r, g, b)
        colorSet.add(colorName.lowercase())
      }
    }

    colorToFind.lowercase() in colorSet
  }
}

class MainActivity : ComponentActivity() {

  private var imageUri: Uri? = null
  private val imageUriState = mutableStateOf<Uri?>(null)
  private var currentBitmap = mutableStateOf<android.graphics.Bitmap?>(null)

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
        val context = LocalContext.current
        val activity = context as ComponentActivity

        var isProcessing by remember { mutableStateOf(false) }
        var tappedColorName by remember { mutableStateOf<String?>(null) }

        val hasMicPermission = remember {
          mutableStateOf(
            androidx.core.content.ContextCompat.checkSelfPermission(
              context,
              android.Manifest.permission.RECORD_AUDIO
            ) == android.content.pm.PackageManager.PERMISSION_GRANTED
          )
        }

        val permissionLauncher = rememberLauncherForActivityResult(
          ActivityResultContracts.RequestPermission()
        ) { granted ->
          hasMicPermission.value = granted
        }

        val speechLauncher = rememberLauncherForActivityResult(
          ActivityResultContracts.StartActivityForResult()
        ) { result ->
          if (result.resultCode == RESULT_OK) {
            val spoken = result.data
              ?.getStringArrayListExtra(android.speech.RecognizerIntent.EXTRA_RESULTS)
              ?.get(0) ?: ""

            isProcessing = true

            // Launch a coroutine to check if the color is in the image
            activity.lifecycleScope.launch {
              val isColorInImage = checkColorInBitmap(currentBitmap.value, spoken)

              val message = if (isColorInImage) {
                "Yes, $spoken is in the image!"
              } else {
                "No, $spoken wasn't found in the image."
              }

              withContext(Dispatchers.Main) {
                android.widget.Toast.makeText(context, message, android.widget.Toast.LENGTH_SHORT).show()
                isProcessing = false
              }
            }
          }
        }

        Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
          HomeScreen(
            modifier = Modifier.padding(innerPadding),
            imageUri = imageUriState.value,
            isProcessing = isProcessing,
            onTakePicture = {
              val photoFile = File.createTempFile("picture", ".jpg", cacheDir).apply {
                createNewFile()
                deleteOnExit()
              }
              imageUri = FileProvider.getUriForFile(
                activity,
                "$packageName.fileprovider",
                photoFile
              )
              takePictureLauncher.launch(imageUri)
            },
            onSelectPicture = {
              selectPictureLauncher.launch("image/*")
            },
            onAskColorCheck = {
              if (hasMicPermission.value) {
                val intent = android.content.Intent(
                  android.speech.RecognizerIntent.ACTION_RECOGNIZE_SPEECH
                ).apply {
                  putExtra(
                    android.speech.RecognizerIntent.EXTRA_LANGUAGE_MODEL,
                    android.speech.RecognizerIntent.LANGUAGE_MODEL_FREE_FORM
                  )
                  putExtra(
                    android.speech.RecognizerIntent.EXTRA_LANGUAGE,
                    java.util.Locale.getDefault()
                  )
                }
                speechLauncher.launch(intent)
              } else {
                permissionLauncher.launch(android.Manifest.permission.RECORD_AUDIO)
              }
            },
            onColorTapped = { colorName ->
              tappedColorName = colorName
            },
            onBitmapLoaded = { bitmap ->
              currentBitmap.value = bitmap
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
  isProcessing: Boolean = false,
  onTakePicture: () -> Unit = {},
  onSelectPicture: () -> Unit = {},
  onAskColorCheck: () -> Unit = {},
  onColorTapped: (String) -> Unit = {},
  onBitmapLoaded: (android.graphics.Bitmap) -> Unit = {}
) {
  val context = LocalContext.current
  var tappedColor by remember { mutableStateOf<String?>(null) }
  var bitmap by remember(imageUri) { mutableStateOf<android.graphics.Bitmap?>(null) }

  LaunchedEffect(imageUri) {
    imageUri?.let { uri ->
      // Data Extraction: Load the image from the URI
      context.contentResolver.openInputStream(uri)?.use { stream ->
        val originalBitmap = BitmapFactory.decodeStream(stream)
        if (originalBitmap != null) {
          // Preprocessing: Crop the image to a square and compress it losslessly
          val size = minOf(originalBitmap.width, originalBitmap.height)
          val xOffset = (originalBitmap.width - size) / 2
          val yOffset = (originalBitmap.height - size) / 2
          val squareBitmap = android.graphics.Bitmap.createBitmap(originalBitmap, xOffset, yOffset, size, size)

          // Compress to PNG (lossless) and then decode back to a Bitmap
          val byteStream = java.io.ByteArrayOutputStream()
          squareBitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, byteStream)
          val compressedBitmap = BitmapFactory.decodeByteArray(byteStream.toByteArray(), 0, byteStream.size())

          bitmap = compressedBitmap
          compressedBitmap?.let(onBitmapLoaded)
        }
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
    Spacer(modifier = Modifier.height(8.dp))
    Button(
      onClick = onAskColorCheck,
      enabled = bitmap != null && !isProcessing
    ) {
      Text(if (isProcessing) "Processing..." else "Ask About a Color")
    }
    Spacer(modifier = Modifier.height(16.dp))
    tappedColor?.let {

      Text(text = "Tapped Color: $it", style = MaterialTheme.typography.bodyLarge)
    }
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
                // Feature Extraction: Calculate pixel coordinates and extract RGB values from the tapped pixel
                val x = (offset.x * bmp.width / this.size.width).toInt()
                val y = (offset.y * bmp.height / this.size.height).toInt()
                if (x in 0 until bmp.width && y in 0 until bmp.height) {
                  val pixel = bmp[x, y]
                  val r = (pixel shr 16) and 0xFF
                  val g = (pixel shr 8) and 0xFF
                  val b = pixel and 0xFF
                  // Classification: Determine the color name from the RGB values
                  tappedColor = getColorNameFromRgb(r, g, b)
                  onColorTapped(tappedColor ?: "")
                }
              }
            }
        )
      }
    }
    Spacer(modifier = Modifier.height(16.dp))
    if (isProcessing) {
      Spacer(modifier = Modifier.height(8.dp))
      Text("Scanning image for color...", style = MaterialTheme.typography.bodyMedium)
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
