package com.chennuri.farm

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.ImageDecoder
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.provider.MediaStore
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.BugReport
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material.icons.filled.Lightbulb
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.SignalCellularAlt
import androidx.compose.material.icons.filled.Spa
import androidx.compose.material.icons.filled.WaterDrop
import androidx.compose.material.icons.filled.Yard
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import com.chennuri.farm.data.model.ContextPayload
import com.chennuri.farm.data.model.CropStage
import com.chennuri.farm.data.model.DiagnosisResult
import com.chennuri.farm.data.repository.GeminiVisionRepository
import com.chennuri.farm.tts.TeluguTtsManager
import com.chennuri.farm.ui.theme.FarmAmberBg
import com.chennuri.farm.ui.theme.FarmAmberBorder
import com.chennuri.farm.ui.theme.FarmAmberIcon
import com.chennuri.farm.ui.theme.FarmBlueBg
import com.chennuri.farm.ui.theme.FarmBlueBorder
import com.chennuri.farm.ui.theme.FarmBlueIcon
import com.chennuri.farm.ui.theme.FarmCardBorder
import com.chennuri.farm.ui.theme.FarmGreenPrimary
import com.chennuri.farm.ui.theme.FarmGreenSecondary
import com.chennuri.farm.ui.theme.FarmLightMintBg
import com.chennuri.farm.ui.theme.FarmPaleMintBg
import com.chennuri.farm.ui.theme.FarmPurpleBg
import com.chennuri.farm.ui.theme.FarmPurpleBorder
import com.chennuri.farm.ui.theme.FarmPurpleIcon
import com.chennuri.farm.ui.theme.FarmRedBg
import com.chennuri.farm.ui.theme.FarmRedBorder
import com.chennuri.farm.ui.theme.FarmRedIcon
import com.chennuri.farm.ui.theme.FarmSurface
import com.chennuri.farm.ui.theme.FarmTextDark
import com.chennuri.farm.ui.theme.FarmTextMuted
import com.chennuri.farm.ui.theme.FarmTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        setContent {
            FarmTheme {
                SmartCropAdvisorScreen()
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SmartCropAdvisorScreen() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var capturedBitmap by remember { mutableStateOf<Bitmap?>(null) }
    var pendingCameraUri by remember { mutableStateOf<Uri?>(null) }
    var diagnosis by remember { mutableStateOf<DiagnosisResult?>(null) }
    var isAnalyzing by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    val teluguTtsManager = remember {
        TeluguTtsManager(context)
    }

    DisposableEffect(Unit) {
        onDispose {
            teluguTtsManager.release()
        }
    }

    val analyzeLeaf: (Bitmap) -> Unit = { bitmap ->
        scope.launch {
            if (
                ApiKeys.GEMINI_API_KEY.isBlank() ||
                ApiKeys.GEMINI_API_KEY == "PASTE_YOUR_REAL_GEMINI_KEY_HERE"
            ) {
                errorMessage = "Gemini API కీ సెట్ చేయబడలేదు."
                return@launch
            }

            isAnalyzing = true
            diagnosis = null
            errorMessage = null

            val fieldContext = ContextPayload(
                latitude = 0.0,
                longitude = 0.0,
                temperatureCelsius = 0.0,
                rainProbabilityNext24hPercent = 0,
                weatherDescription = "తేమతో కూడిన వాతావరణం",
                sowingDateEpochMillis = System.currentTimeMillis(),
                daysAfterSowing = 30,
                cropStage = CropStage.VEGETATIVE
            )

            val repository = GeminiVisionRepository(
                apiKey = ApiKeys.GEMINI_API_KEY
            )

            repository.diagnose(bitmap, fieldContext)
                .onSuccess { result ->
                    diagnosis = result
                    // Start Telugu TTS automatically after diagnosis succeeds
                    teluguTtsManager.speak(result.advisoryTelugu)
                }
                .onFailure { _ ->
                    errorMessage = "ఆకును సరిగ్గా విశ్లేషించలేకపోయాము. దయచేసి స్పష్టమైన వెలుతురులో మరో ఫోటో తీయండి."
                }

            isAnalyzing = false
        }
    }

    val cameraLauncher = androidx.activity.compose.rememberLauncherForActivityResult(
        contract = ActivityResultContracts.TakePicture()
    ) { success ->
        val photoUri = pendingCameraUri

        if (success && photoUri != null) {
            scope.launch {
                try {
                    val bitmap = withContext(Dispatchers.IO) {
                        decodeBitmap(context, photoUri)
                    }

                    capturedBitmap = bitmap
                    analyzeLeaf(bitmap)
                } catch (_: Exception) {
                    errorMessage = "తీసిన ఫోటోను చదవలేకపోయాము. దయచేసి మళ్ళీ ప్రయత్నించండి."
                }
            }
        } else if (!success) {
            errorMessage = "ఫోటో తీయడం రద్దు చేయబడింది."
        }
    }

    val openCamera: () -> Unit = {
        val picturesDirectory = context.getExternalFilesDir(Environment.DIRECTORY_PICTURES)

        if (picturesDirectory == null) {
            errorMessage = "కెమెరా నిల్వ అందుబాటులో లేదు."
        } else {
            picturesDirectory.mkdirs()

            val photoFile = File(
                picturesDirectory,
                "leaf_${System.currentTimeMillis()}.jpg"
            )

            val photoUri = FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                photoFile
            )

            pendingCameraUri = photoUri
            capturedBitmap = null
            diagnosis = null
            errorMessage = null

            cameraLauncher.launch(photoUri)
        }
    }

    val cameraPermissionLauncher =
        androidx.activity.compose.rememberLauncherForActivityResult(
            contract = ActivityResultContracts.RequestPermission()
        ) { granted ->
            if (granted) {
                openCamera()
            } else {
                errorMessage = "పంట ఆకును స్కాన్ చేయడానికి కెమెరా అనుమతి అవసరం. దయచేసి సెట్టింగ్స్‌లో అనుమతించండి."
            }
        }

    val launchCameraFlow: () -> Unit = {
        val cameraAllowed = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.CAMERA
        ) == PackageManager.PERMISSION_GRANTED

        if (cameraAllowed) {
            openCamera()
        } else {
            cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
        }
    }

    Scaffold(
        containerColor = FarmPaleMintBg,
        topBar = {
            TopAppBar(
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = FarmPaleMintBg
                ),
                title = {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        LeafHeaderLogo(modifier = Modifier.size(36.dp))

                        Column {
                            Text(
                                text = "స్మార్ట్ పంట సలహాదారు",
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.Bold,
                                color = FarmTextDark
                            )

                            Text(
                                text = "మీ పంటకు తక్షణ వ్యాధి నిర్ధారణ",
                                style = MaterialTheme.typography.labelMedium,
                                color = FarmTextMuted
                            )
                        }
                    }
                },
                actions = {
                    IconButton(onClick = { /* Settings */ }) {
                        Icon(
                            imageVector = Icons.Filled.Settings,
                            contentDescription = "సెట్టింగ్స్",
                            tint = FarmGreenPrimary,
                            modifier = Modifier.size(24.dp)
                        )
                    }
                }
            )
        },
        bottomBar = {
            BottomCameraBar(
                isDiagnosed = diagnosis != null,
                onCameraClick = launchCameraFlow
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 6.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Static, informational crop badges
            StaticCropBar()

            // State 1: Before photo
            if (capturedBitmap == null && !isAnalyzing) {
                DashedLeafScanCard()
                FarmerInspirationalBanner()
            }

            // State 2: During analysis
            if (capturedBitmap != null && isAnalyzing) {
                AnalyzingLeafCard(bitmap = capturedBitmap!!)
            }

            // State 3: Diagnosis Result View
            diagnosis?.let { result ->
                capturedBitmap?.let { bitmap ->
                    // 1. Small compact photo preview (reduced vertical size)
                    SmallCapturedPhotoCard(bitmap = bitmap)

                    // 2. The circled output from image.png (placed DOWN the photo & UPSIDE of image(1).png)
                    CircledDiagnosisCardsSection(diagnosis = result)

                    // 3. Audio advisory button (Telugu voice advisory)
                    ListenAudioCard(
                        onListenClick = {
                            teluguTtsManager.speak(result.advisoryTelugu)
                        }
                    )

                    // 4. The 2x2 advisory cards from image (1).png
                    AdvisoryCardsGrid(diagnosis = result)

                    // 5. Retake photo action button
                    RetakePhotoActionButton(onRetake = launchCameraFlow)
                }
            }

            // Localized Error state card
            errorMessage?.let { message ->
                LocalizedErrorCard(
                    errorMessage = message,
                    onRetry = launchCameraFlow
                )
            }

            Spacer(modifier = Modifier.height(84.dp))
        }
    }
}

// -------------------------------------------------------------------------------------------------
// Static Informational Crop Badges
// -------------------------------------------------------------------------------------------------

@Composable
private fun StaticCropBar() {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        CropBadgeItem(emoji = "🌾", label = "వరి", modifier = Modifier.weight(1f))
        Spacer(modifier = Modifier.width(6.dp))
        CropBadgeItem(emoji = "☁️", label = "పత్తి", modifier = Modifier.weight(1f))
        Spacer(modifier = Modifier.width(6.dp))
        CropBadgeItem(emoji = "🌶️", label = "మిరప", modifier = Modifier.weight(1f))
        Spacer(modifier = Modifier.width(6.dp))
        CropBadgeItem(emoji = "🌽", label = "మొక్కజొన్న", modifier = Modifier.weight(1f))
    }
}

@Composable
private fun CropBadgeItem(
    emoji: String,
    label: String,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(12.dp),
        color = FarmLightMintBg,
        border = androidx.compose.foundation.BorderStroke(1.dp, FarmCardBorder)
    ) {
        Column(
            modifier = Modifier.padding(vertical = 8.dp, horizontal = 4.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text(text = emoji, fontSize = 20.sp)
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = label,
                fontSize = 12.sp,
                fontWeight = FontWeight.Medium,
                color = FarmTextDark
            )
        }
    }
}

// -------------------------------------------------------------------------------------------------
// Small Leaf Photo Card (Decreased Vertical Size)
// -------------------------------------------------------------------------------------------------

@Composable
private fun SmallCapturedPhotoCard(bitmap: Bitmap) {
    Card(
        shape = RoundedCornerShape(18.dp),
        border = androidx.compose.foundation.BorderStroke(1.dp, FarmCardBorder),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        modifier = Modifier
            .fillMaxWidth()
            .height(180.dp) // Fixed small height to prevent vertical stretching
    ) {
        Image(
            bitmap = bitmap.asImageBitmap(),
            contentDescription = "తీసిన పంట ఆకు",
            modifier = Modifier.fillMaxSize(),
            contentScale = ContentScale.Crop
        )
    }
}

// -------------------------------------------------------------------------------------------------
// The Circled Diagnosis Output from image.png
// (Placed DOWN the photo and UPSIDE of image(1).png)
// -------------------------------------------------------------------------------------------------

@Composable
private fun CircledDiagnosisCardsSection(diagnosis: DiagnosisResult) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        // Card 1: గుర్తించిన వ్యాధి (Detected Disease)
        FullWidthMetricCard(
            title = "గుర్తించిన వ్యాధి",
            value = getTeluguDisplayName(diagnosis.disease, default = diagnosis.disease),
            icon = Icons.Filled.BugReport,
            containerColor = FarmRedBg,
            iconColor = FarmRedIcon,
            borderColor = FarmRedBorder
        )

        // Card 2: గుర్తించిన పంట (Detected Crop)
        FullWidthMetricCard(
            title = "గుర్తించిన పంట",
            value = getTeluguDisplayName(diagnosis.crop, default = diagnosis.crop),
            icon = Icons.Filled.Spa,
            containerColor = FarmLightMintBg,
            iconColor = FarmGreenPrimary,
            borderColor = FarmCardBorder
        )

        // Card 3: AI నమ్మకత్వం (AI Confidence with Progress Bar)
        FullWidthConfidenceCard(confidence = diagnosis.confidence)
    }
}

@Composable
private fun FullWidthMetricCard(
    title: String,
    value: String,
    icon: ImageVector,
    containerColor: Color,
    iconColor: Color,
    borderColor: Color
) {
    Surface(
        shape = RoundedCornerShape(14.dp),
        color = containerColor,
        border = androidx.compose.foundation.BorderStroke(1.dp, borderColor),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .clip(CircleShape)
                    .background(Color.White.copy(alpha = 0.9f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = iconColor,
                    modifier = Modifier.size(20.dp)
                )
            }

            Column {
                Text(
                    text = title,
                    style = MaterialTheme.typography.labelSmall,
                    color = FarmTextMuted,
                    fontSize = 11.sp
                )
                Text(
                    text = value,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = FarmTextDark,
                    fontSize = 15.sp
                )
            }
        }
    }
}

@Composable
private fun FullWidthConfidenceCard(confidence: Float) {
    val percentage = (confidence * 100).toInt().coerceIn(1, 100)

    Surface(
        shape = RoundedCornerShape(14.dp),
        color = FarmBlueBg,
        border = androidx.compose.foundation.BorderStroke(1.dp, FarmBlueBorder),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .clip(CircleShape)
                    .background(Color.White.copy(alpha = 0.9f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Filled.SignalCellularAlt,
                    contentDescription = null,
                    tint = FarmBlueIcon,
                    modifier = Modifier.size(20.dp)
                )
            }

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "AI నమ్మకత్వం",
                    style = MaterialTheme.typography.labelSmall,
                    color = FarmTextMuted,
                    fontSize = 11.sp
                )

                Text(
                    text = "$percentage%",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = FarmTextDark,
                    fontSize = 15.sp
                )

                Spacer(modifier = Modifier.height(4.dp))

                LinearProgressIndicator(
                    progress = { confidence.coerceIn(0f, 1f) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(6.dp)
                        .clip(RoundedCornerShape(3.dp)),
                    color = FarmGreenPrimary,
                    trackColor = Color.White.copy(alpha = 0.7f)
                )
            }
        }
    }
}

// -------------------------------------------------------------------------------------------------
// Audio Advisory Button (Replays Telugu TTS)
// -------------------------------------------------------------------------------------------------

@Composable
private fun ListenAudioCard(onListenClick: () -> Unit) {
    Surface(
        shape = RoundedCornerShape(14.dp),
        color = FarmGreenPrimary,
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onListenClick)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 12.dp, horizontal = 16.dp),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.VolumeUp,
                contentDescription = "సలహాను వినండి",
                tint = Color.White,
                modifier = Modifier.size(22.dp)
            )

            Spacer(modifier = Modifier.width(10.dp))

            Text(
                text = "సలహాను వినండి",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = Color.White,
                fontSize = 16.sp
            )
        }
    }
}

// -------------------------------------------------------------------------------------------------
// The 2x2 Farm Advisory & Care Cards (image (1).png)
// -------------------------------------------------------------------------------------------------

@Composable
private fun AdvisoryCardsGrid(diagnosis: DiagnosisResult) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        // Row 1: ప్రస్తుతం పరిస్థితి & సిఫారసు చర్యలు
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(IntrinsicSize.Min),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            AdvisoryCardItem(
                modifier = Modifier.weight(1f).fillMaxHeight(),
                icon = Icons.Filled.Cloud,
                iconColor = FarmBlueIcon,
                title = "ప్రస్తుతం పరిస్థితి",
                body = "అధిక తేమతో పాటు వర్షాలు ఉండటం వల్ల వ్యాధి మరింత విస్తరించే అవకాశం ఉంది.",
                containerColor = FarmBlueBg,
                borderColor = FarmBlueBorder
            )

            AdvisoryCardItem(
                modifier = Modifier.weight(1f).fillMaxHeight(),
                icon = Icons.Filled.Shield,
                iconColor = FarmAmberIcon,
                title = "సిఫారసు చర్యలు",
                body = diagnosis.advisoryTelugu.ifBlank {
                    "వెంటనే తగిన శిలీంధ్ర సంహారిణిని ఆమోదించిన మోతాదులో పిచికారీ చేయండి."
                },
                containerColor = FarmAmberBg,
                borderColor = FarmAmberBorder
            )
        }

        // Row 2: వాతావరణ హెచ్చరిక & పంట దశ
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(IntrinsicSize.Min),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            AdvisoryCardItem(
                modifier = Modifier.weight(1f).fillMaxHeight(),
                icon = Icons.Filled.WaterDrop,
                iconColor = FarmPurpleIcon,
                title = "వాతావరణ హెచ్చరిక",
                body = "రేపు వర్షం పడే అవకాశం ఉంది. వర్షానికి ముందు మందులు పిచికారీ చేయడం మానండి.",
                containerColor = FarmPurpleBg,
                borderColor = FarmPurpleBorder
            )

            CropStageAdvisoryCard(
                modifier = Modifier.weight(1f).fillMaxHeight()
            )
        }
    }
}

@Composable
private fun AdvisoryCardItem(
    modifier: Modifier = Modifier,
    icon: ImageVector,
    iconColor: Color,
    title: String,
    body: String,
    containerColor: Color,
    borderColor: Color
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(16.dp),
        color = containerColor,
        border = androidx.compose.foundation.BorderStroke(1.dp, borderColor)
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(28.dp)
                        .clip(CircleShape)
                        .background(Color.White.copy(alpha = 0.85f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = iconColor,
                        modifier = Modifier.size(16.dp)
                    )
                }

                Text(
                    text = title,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = FarmTextDark,
                    fontSize = 12.sp
                )
            }

            Text(
                text = body,
                style = MaterialTheme.typography.bodySmall,
                color = FarmTextDark,
                lineHeight = 16.sp,
                fontSize = 11.sp
            )
        }
    }
}

@Composable
private fun CropStageAdvisoryCard(modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(16.dp),
        color = FarmLightMintBg,
        border = androidx.compose.foundation.BorderStroke(1.dp, FarmCardBorder)
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(28.dp)
                        .clip(CircleShape)
                        .background(Color.White.copy(alpha = 0.85f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Filled.Yard,
                        contentDescription = null,
                        tint = FarmGreenPrimary,
                        modifier = Modifier.size(16.dp)
                    )
                }

                Column {
                    Text(
                        text = "పంట దశ",
                        style = MaterialTheme.typography.labelSmall,
                        color = FarmTextMuted,
                        fontSize = 10.sp
                    )
                    Text(
                        text = "పుష్పించే దశ",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = FarmTextDark,
                        fontSize = 12.sp
                    )
                }
            }

            Spacer(modifier = Modifier.height(2.dp))

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(28.dp)
                        .clip(CircleShape)
                        .background(Color.White.copy(alpha = 0.85f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Filled.Lightbulb,
                        contentDescription = null,
                        tint = FarmAmberIcon,
                        modifier = Modifier.size(16.dp)
                    )
                }

                Column {
                    Text(
                        text = "ఈ దశలో సలహా",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = FarmTextDark,
                        fontSize = 11.sp
                    )
                    Text(
                        text = "ఈ దశలో సరైన సమయంలో నిర్వహణ చాలా ముఖ్యం.",
                        style = MaterialTheme.typography.bodySmall,
                        color = FarmTextMuted,
                        lineHeight = 14.sp,
                        fontSize = 10.sp
                    )
                }
            }
        }
    }
}

// -------------------------------------------------------------------------------------------------
// State 1 & State 2 Components
// -------------------------------------------------------------------------------------------------

@Composable
private fun DashedLeafScanCard() {
    val dashedBorderColor = FarmGreenSecondary.copy(alpha = 0.5f)

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .drawBehind {
                val strokeWidth = 2.dp.toPx()
                val dashInterval = 18f
                val gapInterval = 14f
                val radius = 24.dp.toPx()

                drawRoundRect(
                    color = dashedBorderColor,
                    topLeft = Offset(strokeWidth / 2, strokeWidth / 2),
                    size = Size(size.width - strokeWidth, size.height - strokeWidth),
                    cornerRadius = CornerRadius(radius, radius),
                    style = Stroke(
                        width = strokeWidth,
                        pathEffect = PathEffect.dashPathEffect(
                            floatArrayOf(dashInterval, gapInterval),
                            0f
                        )
                    )
                )
            }
            .clip(RoundedCornerShape(24.dp))
            .background(FarmSurface)
            .padding(vertical = 36.dp, horizontal = 24.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Box(
                modifier = Modifier
                    .size(136.dp)
                    .clip(CircleShape)
                    .background(FarmLightMintBg),
                contentAlignment = Alignment.Center
            ) {
                LeafIllustration(modifier = Modifier.size(86.dp))
            }

            Spacer(modifier = Modifier.height(24.dp))

            Text(
                text = "పంట ఆకును స్కాన్ చేయడానికి\nక్రింద ఉన్న కేమెరా బటన్ నొక్కండి",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = FarmTextDark,
                textAlign = TextAlign.Center,
                lineHeight = 26.sp
            )
        }
    }
}

@Composable
private fun FarmerInspirationalBanner() {
    Surface(
        shape = RoundedCornerShape(20.dp),
        color = FarmLightMintBg,
        border = androidx.compose.foundation.BorderStroke(1.dp, FarmCardBorder),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(54.dp)
                    .clip(CircleShape)
                    .background(FarmGreenPrimary.copy(alpha = 0.15f)),
                contentAlignment = Alignment.Center
            ) {
                Text(text = "👨‍🌾", fontSize = 30.sp)
            }

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "“ఆరోగ్యమైన పంట",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = FarmGreenPrimary,
                    fontSize = 15.sp
                )
                Text(
                    text = "సమృద్ధి భవిష్యత్తు”",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = FarmGreenPrimary,
                    fontSize = 15.sp
                )
                Text(
                    text = "మీ పొలంలో ఆకుల వ్యాధులను క్షణాల్లో గుర్తించి తగిన సలహాలు పొందండి.",
                    style = MaterialTheme.typography.bodySmall,
                    color = FarmTextMuted,
                    lineHeight = 16.sp,
                    fontSize = 11.sp
                )
            }
        }
    }
}

@Composable
private fun AnalyzingLeafCard(bitmap: Bitmap) {
    Card(
        shape = RoundedCornerShape(24.dp),
        modifier = Modifier
            .fillMaxWidth()
            .height(260.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            Image(
                bitmap = bitmap.asImageBitmap(),
                contentDescription = "తీసిన పంట ఆకు",
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop
            )

            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.58f)),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                    modifier = Modifier.padding(24.dp)
                ) {
                    CircularProgressIndicator(
                        color = Color.White,
                        strokeWidth = 4.dp,
                        modifier = Modifier.size(52.dp)
                    )

                    Spacer(modifier = Modifier.height(20.dp))

                    Text(
                        text = "ఆకును విశ్లేషిస్తున్నాము...",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )

                    Spacer(modifier = Modifier.height(6.dp))

                    Text(
                        text = "దయచేసి కాసేపు వేచి ఉండండి",
                        style = MaterialTheme.typography.bodyMedium,
                        color = Color.White.copy(alpha = 0.88f)
                    )
                }
            }
        }
    }
}

// -------------------------------------------------------------------------------------------------
// Retake & Error Actions
// -------------------------------------------------------------------------------------------------

@Composable
private fun RetakePhotoActionButton(onRetake: () -> Unit) {
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = Color.Transparent,
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onRetake)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 10.dp),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Filled.Refresh,
                contentDescription = "మరొక ఫోటో తీయండి",
                tint = FarmGreenPrimary,
                modifier = Modifier.size(22.dp)
            )

            Spacer(modifier = Modifier.width(8.dp))

            Text(
                text = "మరొక ఫోటో తీయండి",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = FarmGreenPrimary,
                fontSize = 15.sp
            )
        }
    }
}

@Composable
private fun LocalizedErrorCard(
    errorMessage: String,
    onRetry: () -> Unit
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = FarmRedBg),
        shape = RoundedCornerShape(16.dp),
        border = androidx.compose.foundation.BorderStroke(1.dp, FarmRedBorder),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                text = "వ్యాధి నిర్ధారణ పూర్తి కాలేదు",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = FarmRedIcon
            )

            Text(
                text = errorMessage,
                style = MaterialTheme.typography.bodyMedium,
                color = FarmTextDark
            )

            Spacer(modifier = Modifier.height(4.dp))

            Surface(
                shape = RoundedCornerShape(10.dp),
                color = FarmGreenPrimary,
                modifier = Modifier.clickable(onClick = onRetry)
            ) {
                Text(
                    text = "మళ్ళీ ఫోటో తీయండి",
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                )
            }
        }
    }
}

// -------------------------------------------------------------------------------------------------
// Bottom Camera Button
// -------------------------------------------------------------------------------------------------

@Composable
private fun BottomCameraBar(
    isDiagnosed: Boolean,
    onCameraClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .padding(bottom = 12.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Box(
                modifier = Modifier
                    .size(68.dp)
                    .clip(CircleShape)
                    .background(FarmGreenPrimary)
                    .border(3.dp, Color.White, CircleShape)
                    .clickable(onClick = onCameraClick),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Filled.CameraAlt,
                    contentDescription = "కెమెరా",
                    tint = Color.White,
                    modifier = Modifier.size(32.dp)
                )
            }

            Spacer(modifier = Modifier.height(4.dp))

            Text(
                text = if (isDiagnosed) "మరొక ఫోటో తీయండి" else "ఫోటో తీయండి",
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold,
                color = FarmTextDark,
                fontSize = 12.sp
            )
        }
    }
}

// -------------------------------------------------------------------------------------------------
// Graphic Elements & Helpers
// -------------------------------------------------------------------------------------------------

@Composable
private fun LeafHeaderLogo(modifier: Modifier = Modifier) {
    Canvas(modifier = modifier) {
        val w = size.width
        val h = size.height

        val path = Path().apply {
            moveTo(w * 0.15f, h * 0.85f)
            cubicTo(w * 0.05f, h * 0.35f, w * 0.45f, h * 0.05f, w * 0.88f, h * 0.12f)
            cubicTo(w * 0.95f, h * 0.55f, w * 0.65f, h * 0.95f, w * 0.15f, h * 0.85f)
            close()
        }
        drawPath(path = path, color = FarmGreenPrimary)

        drawLine(
            color = Color.White.copy(alpha = 0.5f),
            start = Offset(w * 0.15f, h * 0.85f),
            end = Offset(w * 0.80f, h * 0.20f),
            strokeWidth = 2.dp.toPx(),
            cap = StrokeCap.Round
        )
    }
}

@Composable
private fun LeafIllustration(modifier: Modifier = Modifier) {
    Canvas(modifier = modifier) {
        val w = size.width
        val h = size.height

        val leftLeaf = Path().apply {
            moveTo(w * 0.48f, h * 0.85f)
            cubicTo(w * 0.12f, h * 0.65f, w * 0.05f, h * 0.32f, w * 0.32f, h * 0.18f)
            cubicTo(w * 0.55f, h * 0.32f, w * 0.58f, h * 0.60f, w * 0.48f, h * 0.85f)
            close()
        }
        drawPath(path = leftLeaf, color = FarmGreenSecondary)

        val rightLeaf = Path().apply {
            moveTo(w * 0.48f, h * 0.85f)
            cubicTo(w * 0.52f, h * 0.50f, w * 0.65f, h * 0.22f, w * 0.92f, h * 0.12f)
            cubicTo(w * 0.96f, h * 0.45f, w * 0.78f, h * 0.72f, w * 0.48f, h * 0.85f)
            close()
        }
        drawPath(path = rightLeaf, color = FarmGreenPrimary)

        drawLine(
            color = FarmGreenPrimary,
            start = Offset(w * 0.48f, h * 0.85f),
            end = Offset(w * 0.48f, h * 0.98f),
            strokeWidth = 4.dp.toPx(),
            cap = StrokeCap.Round
        )
    }
}

private fun getTeluguDisplayName(name: String, default: String): String {
    return when (name.trim().lowercase()) {
        "rice", "paddy", "వరి" -> "వరి"
        "cotton", "పత్తి" -> "పత్తి"
        "chilli", "chili", "మిరప" -> "మిరప"
        "maize", "corn", "మొక్కజొన్న" -> "మొక్కజొన్న"
        "powdery mildew", "బూడిద తెగులు" -> "బూడిద తెగులు"
        "rice blast", "blast", "వరి బ్లాస్ట్" -> "వరి బ్లాస్ట్"
        "bacterial blight", "బాక్టీరియల్ బ్లైట్" -> "బాక్టీరియల్ బ్లైట్"
        "leaf spot", "ఆకు మచ్చ తెగులు" -> "ఆకు మచ్చ తెగులు"
        else -> default
    }
}

private fun decodeBitmap(
    context: Context,
    uri: Uri
): Bitmap {
    return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
        val source = ImageDecoder.createSource(context.contentResolver, uri)
        ImageDecoder.decodeBitmap(source) { decoder, _, _ ->
            decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
            decoder.isMutableRequired = true
        }
    } else {
        @Suppress("DEPRECATION")
        MediaStore.Images.Media.getBitmap(context.contentResolver, uri)
    }
}