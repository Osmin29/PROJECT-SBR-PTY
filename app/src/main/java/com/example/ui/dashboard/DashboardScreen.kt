package com.example.ui.dashboard

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.BuildConfig
import com.example.data.local.MeetingEntity
import com.example.data.model.DialogueLine
import com.example.data.model.Participant
import com.example.ui.viewmodel.MeetingViewModel
import com.squareup.moshi.Types
import kotlinx.coroutines.delay
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun DashboardScreen(
    viewModel: MeetingViewModel,
    modifier: Modifier = Modifier
) {
    val currentTab by viewModel.selectedTab.collectAsStateWithLifecycle()
    val isRecording by viewModel.isRecording.collectAsStateWithLifecycle()
    val durationSeconds by viewModel.recordingDurationSeconds.collectAsStateWithLifecycle()
    val amplitudes by viewModel.amplitudes.collectAsStateWithLifecycle()
    val isProcessing by viewModel.isProcessing.collectAsStateWithLifecycle()
    val meetings by viewModel.allMeetings.collectAsStateWithLifecycle()
    val selectedMeeting by viewModel.selectedMeeting.collectAsStateWithLifecycle()
    val errorMessage by viewModel.errorMessage.collectAsStateWithLifecycle()
    val successMessage by viewModel.successMessage.collectAsStateWithLifecycle()

    var renamingParticipantName by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(errorMessage, successMessage) {
        if (errorMessage != null || successMessage != null) {
            delay(5000)
            viewModel.clearAlerts()
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color(0xFF10111A))
            .testTag("dashboard_screen")
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp)
        ) {
            HeaderSection()

            TabRowSection(
                selectedTabIndex = currentTab,
                onTabSelected = { viewModel.selectTab(it) }
            )

            Spacer(modifier = Modifier.height(16.dp))

            Box(modifier = Modifier.weight(1f)) {
                if (currentTab == 0) {
                    RecordTabContent(
                        isRecording = isRecording,
                        durationSeconds = durationSeconds,
                        amplitudes = amplitudes,
                        onStartRecord = { viewModel.startRecording() },
                        onStopRecord = { viewModel.stopAndProcessRecording() },
                        onDiscardRecord = { viewModel.discardRecording() }
                    )
                } else {
                    HistoryTabContent(
                        meetings = meetings,
                        selectedMeeting = selectedMeeting,
                        onMeetingSelect = { viewModel.selectMeeting(it) },
                        onMeetingDelete = { viewModel.deleteMeeting(it) },
                        viewModel = viewModel,
                        onTriggerRename = { renamingParticipantName = it }
                    )
                }
            }
        }

        if (isProcessing) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.75f))
                    .clickable(enabled = false) {},
                contentAlignment = Alignment.Center
            ) {
                Card(
                    colors = CardDefaults.cardColors(containerColor = Color(0xFF1D1E2C)),
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier
                        .fillMaxWidth(0.85f)
                        .padding(16.dp)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        CircularProgressIndicator(
                            color = Color(0xFF3F51B5),
                            strokeWidth = 4.dp,
                            modifier = Modifier.size(56.dp)
                        )
                        Spacer(modifier = Modifier.height(20.dp))
                        Text(
                            text = "Procesando Grabación...",
                            fontWeight = FontWeight.Bold,
                            fontSize = 18.sp,
                            color = Color.White,
                            textAlign = TextAlign.Center
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "El modelo de voz de Gemini 3.5-flash está transcribiendo diálogos, identificando firmas vocales y asociando los nombres mencionados para crear la minuta ejecutiva offline.",
                            fontSize = 13.sp,
                            color = Color(0xFF8E92B3),
                            textAlign = TextAlign.Center,
                            lineHeight = 18.sp
                        )
                    }
                }
            }
        }

        renamingParticipantName?.let { oldName ->
            RenameParticipantDialog(
                oldName = oldName,
                onDismiss = { renamingParticipantName = null },
                onConfirm = { newName ->
                    selectedMeeting?.let { meeting ->
                        viewModel.renameParticipantInMeeting(meeting.id, oldName, newName)
                    }
                    renamingParticipantName = null
                }
            )
        }

        Box(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 32.dp)
        ) {
            AnimatedVisibility(
                visible = errorMessage != null,
                enter = fadeIn() + slideInVertically { it / 2 },
                exit = fadeOut() + slideOutVertically { it / 2 }
            ) {
                errorMessage?.let {
                    Card(
                        colors = CardDefaults.cardColors(containerColor = Color(0xFFD32F2F)),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth(0.9f)
                    ) {
                        Row(
                            modifier = Modifier.padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(Icons.Filled.Warning, "Error", tint = Color.White)
                            Spacer(modifier = Modifier.width(10.dp))
                            Text(it, color = Color.White, fontSize = 13.sp, lineHeight = 16.sp)
                        }
                    }
                }
            }

            AnimatedVisibility(
                visible = successMessage != null && errorMessage == null,
                enter = fadeIn() + slideInVertically { it / 2 },
                exit = fadeOut() + slideOutVertically { it / 2 }
            ) {
                successMessage?.let {
                    Card(
                        colors = CardDefaults.cardColors(containerColor = Color(0xFF2E7D32)),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth(0.9f)
                    ) {
                        Row(
                            modifier = Modifier.padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(Icons.Filled.CheckCircle, "Éxito", tint = Color.White)
                            Spacer(modifier = Modifier.width(10.dp))
                            Text(it, color = Color.White, fontSize = 13.sp, lineHeight = 16.sp)
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun HeaderSection() {
    val utcFormat = SimpleDateFormat("HH:mm 'UTC'", Locale.getDefault()).apply {
        timeZone = TimeZone.getTimeZone("UTC")
    }
    val utcTime = utcFormat.format(Date())

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 12.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column {
            Text(
                text = "Minutas IA",
                fontWeight = FontWeight.ExtraBold,
                fontSize = 24.sp,
                color = Color.White
            )
            Text(
                text = "Graba, transcribe, e identifica por voz",
                fontSize = 11.sp,
                color = Color(0xFF8E92B3)
            )
        }

        Column(horizontalAlignment = Alignment.End) {
            Badge(
                containerColor = Color(0xFF1D1E2C),
                modifier = Modifier.padding(bottom = 2.dp)
            ) {
                Text(
                    text = utcTime,
                    color = Color(0xFF8E92B3),
                    fontSize = 10.sp,
                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                )
            }

            val keyStatusColor = if (BuildConfig.GEMINI_API_KEY.isNotEmpty() && BuildConfig.GEMINI_API_KEY != "MY_GEMINI_API_KEY") {
                Color(0xFF00C853)
            } else {
                Color(0xFFFFAB00)
            }
            val keyStatusText = if (BuildConfig.GEMINI_API_KEY.isNotEmpty() && BuildConfig.GEMINI_API_KEY != "MY_GEMINI_API_KEY") {
                "Gemini Listo"
            } else {
                "Falta API Key"
            }

            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(6.dp)
                        .background(keyStatusColor, CircleShape)
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text(
                    text = keyStatusText,
                    fontSize = 9.sp,
                    color = keyStatusColor,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}

@Composable
fun TabRowSection(
    selectedTabIndex: Int,
    onTabSelected: (Int) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color(0xFF1C1D2A), RoundedCornerShape(12.dp))
            .padding(4.dp)
    ) {
        Box(
            modifier = Modifier
                .weight(1f)
                .clip(RoundedCornerShape(8.dp))
                .background(if (selectedTabIndex == 0) Color(0xFF2C2D42) else Color.Transparent)
                .clickable { onTabSelected(0) }
                .padding(vertical = 10.dp)
                .testTag("tab_record"),
            contentAlignment = Alignment.Center
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    Icons.Default.Mic,
                    "Grabar",
                    tint = if (selectedTabIndex == 0) Color.White else Color(0xFF8E92B3),
                    modifier = Modifier.size(16.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = "Grabar Reunión",
                    fontWeight = FontWeight.Bold,
                    fontSize = 13.sp,
                    color = if (selectedTabIndex == 0) Color.White else Color(0xFF8E92B3)
                )
            }
        }

        Box(
            modifier = Modifier
                .weight(1f)
                .clip(RoundedCornerShape(8.dp))
                .background(if (selectedTabIndex == 1) Color(0xFF2C2D42) else Color.Transparent)
                .clickable { onTabSelected(1) }
                .padding(vertical = 10.dp)
                .testTag("tab_history"),
            contentAlignment = Alignment.Center
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    Icons.Default.Folder,
                    "Historial",
                    tint = if (selectedTabIndex == 1) Color.White else Color(0xFF8E92B3),
                    modifier = Modifier.size(16.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = "Ver Historial",
                    fontWeight = FontWeight.Bold,
                    fontSize = 13.sp,
                    color = if (selectedTabIndex == 1) Color.White else Color(0xFF8E92B3)
                )
            }
        }
    }
}

@Composable
fun RecordTabContent(
    isRecording: Boolean,
    durationSeconds: Int,
    amplitudes: List<Float>,
    onStartRecord: () -> Unit,
    onStopRecord: () -> Unit,
    onDiscardRecord: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState()),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.SpaceBetween
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.fillMaxWidth()
        ) {
            if (BuildConfig.GEMINI_API_KEY.isEmpty() || BuildConfig.GEMINI_API_KEY == "MY_GEMINI_API_KEY") {
                Card(
                    colors = CardDefaults.cardColors(containerColor = Color(0xFF2C2216)),
                    shape = RoundedCornerShape(12.dp),
                    border = BorderStroke(1.dp, Color(0xFFFFAB00).copy(alpha = 0.5f)),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 16.dp)
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Filled.Warning, "Aviso", tint = Color(0xFFFFAB00), modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                "Se requiere API Key de Gemini",
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFFFFAB00),
                                fontSize = 13.sp
                            )
                        }
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            "Por favor, ingresa tu API Key en el panel de 'Secrets' de AI Studio para activar la transcripción y el resumen ejecutivo.",
                            fontSize = 11.sp,
                            color = Color(0xFFE5A643),
                            lineHeight = 15.sp
                        )
                    }
                }
            }

            Card(
                colors = CardDefaults.cardColors(containerColor = Color(0xFF131525)),
                shape = RoundedCornerShape(12.dp),
                border = BorderStroke(1.dp, Color(0xFF3F51B5).copy(alpha = 0.3f)),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 24.dp)
            ) {
                Row(
                    modifier = Modifier.padding(14.dp),
                    verticalAlignment = Alignment.Top
                ) {
                    Icon(
                        Icons.Default.Hearing,
                        "Identificación de Voz",
                        tint = Color(0xFF3F51B5),
                        modifier = Modifier.size(24.dp)
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Column {
                        Text(
                            text = "Cómo Identificar Participantes",
                            fontWeight = FontWeight.Bold,
                            color = Color.White,
                            fontSize = 13.sp
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = "Al comenzar la reunión, que cada uno diga su nombre (ej: 'Hola, soy Roberto'). El modelo acústico de Gemini identificará las diferencias vocales e instantáneamente asociará su voz con el nombre mencionado en la minuta.",
                            fontSize = 11.sp,
                            color = Color(0xFF8E92B3),
                            lineHeight = 15.sp
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            Box(
                modifier = Modifier
                    .size(200.dp)
                    .drawBehind {
                        if (isRecording) {
                            val pulseIntensity = (amplitudes.lastOrNull() ?: 0f).coerceAtLeast(0.05f)
                            drawCircle(
                                color = Color(0xFF00C853).copy(alpha = 0.05f * pulseIntensity),
                                radius = 100.dp.toPx() * (1f + pulseIntensity * 0.4f)
                            )
                            drawCircle(
                                color = Color(0xFF00C853).copy(alpha = 0.1f * pulseIntensity),
                                radius = 100.dp.toPx() * (1f + pulseIntensity * 0.15f)
                            )
                        } else {
                            drawCircle(
                                color = Color(0xFF3F51B5).copy(alpha = 0.04f),
                                radius = 100.dp.toPx()
                            )
                        }
                    },
                contentAlignment = Alignment.Center
            ) {
                Button(
                    onClick = {
                        if (isRecording) onStopRecord() else onStartRecord()
                    },
                    shape = CircleShape,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (isRecording) Color(0xFFD32F2F) else Color(0xFF3F51B5)
                    ),
                    modifier = Modifier
                        .size(150.dp)
                        .testTag("record_trigger_button")
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Icon(
                            imageVector = if (isRecording) Icons.Filled.Stop else Icons.Filled.Mic,
                            contentDescription = if (isRecording) "Detener" else "Grabar",
                            modifier = Modifier.size(44.dp),
                            tint = Color.White
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = if (isRecording) "DETENER" else "GRABAR",
                            fontWeight = FontWeight.ExtraBold,
                            fontSize = 12.sp,
                            color = Color.White
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            val minutesStr = (durationSeconds / 60).toString().padStart(2, '0')
            val secondsStr = (durationSeconds % 60).toString().padStart(2, '0')

            Text(
                text = "$minutesStr:$secondsStr",
                fontSize = 36.sp,
                color = if (isRecording) Color(0xFF00C853) else Color.White,
                fontWeight = FontWeight.Light,
                fontFamily = FontFamily.Monospace,
                modifier = Modifier.testTag("stopwatch_text")
            )

            if (isRecording) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(top = 8.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(8.dp)
                            .background(Color(0xFF00C853), CircleShape)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        "Grabando Audio En Vivo...",
                        color = Color(0xFF00C853),
                        fontWeight = FontWeight.Bold,
                        fontSize = 11.sp
                    )
                }
            }
        }

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 32.dp, top = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            if (isRecording) {
                Text(
                    text = "Señal del Micrófono",
                    fontSize = 11.sp,
                    color = Color(0xFF8E92B3),
                    modifier = Modifier.align(Alignment.Start).padding(bottom = 6.dp)
                )
                SoundWaveVisualizer(amplitudes = amplitudes)
                Spacer(modifier = Modifier.height(20.dp))
            }

            if (isRecording) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    TextButton(
                        onClick = onDiscardRecord,
                        colors = ButtonDefaults.textButtonColors(contentColor = Color(0xFF8E92B3))
                    ) {
                        Icon(Icons.Filled.Delete, "Descartar")
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Descartar Grabación", fontWeight = FontWeight.Bold)
                    }

                    Button(
                        onClick = onStopRecord,
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF00C853))
                    ) {
                        Icon(Icons.Filled.AutoAwesome, "Transcribir")
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Detener y Transcribir", fontWeight = FontWeight.Bold)
                    }
                }
            } else {
                Text(
                    text = "Toca el botón principal para dar inicio a la reunión.",
                    fontSize = 12.sp,
                    color = Color(0xFF8E92B3),
                    textAlign = TextAlign.Center
                )
            }
        }
    }
}

@Composable
fun SoundWaveVisualizer(amplitudes: List<Float>, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(72.dp)
            .background(Color(0xFF1D1E2C), RoundedCornerShape(12.dp))
            .padding(horizontal = 8.dp),
        contentAlignment = Alignment.Center
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val width = size.width
            val height = size.height
            val middleY = height / 2f

            if (amplitudes.isEmpty()) {
                drawLine(
                    color = Color(0xFF8E92B3).copy(alpha = 0.2f),
                    start = Offset(0f, middleY),
                    end = Offset(width, middleY),
                    strokeWidth = 2.dp.toPx()
                )
            } else {
                val paddingPx = 4.dp.toPx()
                val activeWidth = width - (paddingPx * 2)
                val spacing = activeWidth / 40f 
                amplitudes.forEachIndexed { index, amplitude ->
                    val x = paddingPx + (index * spacing)
                    val barHeight = (amplitude * height * 0.85f).coerceAtLeast(4.dp.toPx())
                    val startY = middleY - (barHeight / 2f)
                    val endY = middleY + (barHeight / 2f)

                    drawLine(
                        color = Color(0xFF00C853).copy(alpha = 0.5f + (amplitude * 0.5f)),
                        start = Offset(x, startY),
                        end = Offset(x, endY),
                        strokeWidth = 3.dp.toPx(),
                        cap = StrokeCap.Round
                    )
                }
            }
        }
    }
}

@Composable
fun HistoryTabContent(
    meetings: List<MeetingEntity>,
    selectedMeeting: MeetingEntity?,
    onMeetingSelect: (MeetingEntity) -> Unit,
    onMeetingDelete: (MeetingEntity) -> Unit,
    viewModel: MeetingViewModel,
    onTriggerRename: (String) -> Unit
) {
    if (selectedMeeting == null) {
        if (meetings.isEmpty()) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.padding(24.dp)
                ) {
                    Icon(
                        Icons.Outlined.FolderOpen,
                        "Sin minutas",
                        tint = Color(0xFF2C2D42),
                        modifier = Modifier.size(72.dp)
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        "Historial Vacío",
                        fontWeight = FontWeight.Bold,
                        color = Color.White,
                        fontSize = 18.sp
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        "Graba y procesa tu primera reunión. El historial registrará todas tus transcripciones y resúmenes ejecutivos categorizados sin conexión.",
                        color = Color(0xFF8E92B3),
                        fontSize = 13.sp,
                        textAlign = TextAlign.Center,
                        lineHeight = 18.sp
                    )
                }
            }
        } else {
            Column(modifier = Modifier.fillMaxSize()) {
                Text(
                    text = "Reuniones Recientes (${meetings.size})",
                    fontWeight = FontWeight.Bold,
                    fontSize = 15.sp,
                    color = Color.White,
                    modifier = Modifier.padding(bottom = 12.dp)
                )

                LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier.fillMaxSize()
                ) {
                    items(meetings, key = { it.id }) { meeting ->
                        MeetingArchiveCard(
                            meeting = meeting,
                            onClick = { onMeetingSelect(meeting) },
                            onDelete = { onMeetingDelete(meeting) }
                        )
                    }
                }
            }
        }
    } else {
        MeetingDetailPanel(
            meeting = selectedMeeting,
            viewModel = viewModel,
            onTriggerRename = onTriggerRename
        )
    }
}

@Composable
fun MeetingArchiveCard(
    meeting: MeetingEntity,
    onClick: () -> Unit,
    onDelete: () -> Unit
) {
    val dateStr = SimpleDateFormat("dd MMM, yyyy - HH:mm", Locale.getDefault()).format(Date(meeting.timestamp))
    val minutes = meeting.durationMs / 60000
    val seconds = (meeting.durationMs % 60000) / 1000
    val durationText = "${minutes}m ${seconds}s"

    val participantCount = try {
        val moshi = com.example.data.api.RetrofitClient.moshi
        val type = Types.newParameterizedType(List::class.java, Participant::class.java)
        val adapter = moshi.adapter<List<Participant>>(type)
        adapter.fromJson(meeting.participantsJson)?.size ?: 0
    } catch (e: Exception) {
        0
    }

    Card(
        colors = CardDefaults.cardColors(containerColor = Color(0xFF1D1E2C)),
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() }
            .testTag("meeting_card_${meeting.id}")
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = meeting.title,
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp,
                    color = Color.White,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(4.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        Icons.Default.CalendarToday,
                        "Fecha",
                        tint = Color(0xFF8E92B3),
                        modifier = Modifier.size(12.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = dateStr,
                        fontSize = 11.sp,
                        color = Color(0xFF8E92B3)
                    )
                }
                Spacer(modifier = Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            Icons.Default.Schedule,
                            "Duración",
                            tint = Color(0xFF3F51B5),
                            modifier = Modifier.size(13.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = durationText,
                            fontSize = 11.sp,
                            color = Color.White,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            Icons.Default.People,
                            "Participantes",
                            tint = Color(0xFF00C853),
                            modifier = Modifier.size(13.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = "$participantCount voces",
                            fontSize = 11.sp,
                            color = Color.White
                        )
                    }
                }
            }

            IconButton(
                onClick = { onDelete() },
                colors = IconButtonDefaults.iconButtonColors(contentColor = Color(0xFFFF5252))
            ) {
                Icon(Icons.Default.Delete, "Borrar Minuta")
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun MeetingDetailPanel(
    meeting: MeetingEntity,
    viewModel: MeetingViewModel,
    onTriggerRename: (String) -> Unit
) {
    val dateStr = SimpleDateFormat("dd MMMM, yyyy - HH:mm", Locale.getDefault()).format(Date(meeting.timestamp))
    
    val isPlaying by viewModel.isPlaying.collectAsStateWithLifecycle()
    val progress by viewModel.playbackProgress.collectAsStateWithLifecycle()
    val playPositionMs by viewModel.playbackPositionMs.collectAsStateWithLifecycle()

    var subTabSelection by remember { mutableStateOf(0) }

    val participants = remember(meeting.participantsJson) {
        try {
            val moshi = com.example.data.api.RetrofitClient.moshi
            val type = Types.newParameterizedType(List::class.java, Participant::class.java)
            moshi.adapter<List<Participant>>(type).fromJson(meeting.participantsJson) ?: emptyList()
        } catch (e: Exception) {
            emptyList()
        }
    }

    val transcript = remember(meeting.transcriptJson) {
        try {
            val moshi = com.example.data.api.RetrofitClient.moshi
            val type = Types.newParameterizedType(List::class.java, DialogueLine::class.java)
            moshi.adapter<List<DialogueLine>>(type).fromJson(meeting.transcriptJson) ?: emptyList()
        } catch (e: Exception) {
            emptyList()
        }
    }

    LaunchedEffect(Unit) {
        viewModel.stopPlayback()
    }

    Column(modifier = Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(
                onClick = { 
                    viewModel.clearSelectedMeeting()
                },
                colors = IconButtonDefaults.iconButtonColors(contentColor = Color.White),
                modifier = Modifier.testTag("back_button")
            ) {
                Icon(Icons.Default.ArrowBack, "Atrás")
            }
            Text(
                "Volver al Historial",
                color = Color.White,
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium
            )
        }

        Text(
            text = meeting.title,
            fontWeight = FontWeight.ExtraBold,
            fontSize = 20.sp,
            color = Color.White,
            modifier = Modifier.testTag("detail_title")
        )
        Text(
            text = dateStr,
            fontSize = 11.sp,
            color = Color(0xFF8E92B3),
            modifier = Modifier.padding(bottom = 16.dp)
        )

        Card(
            colors = CardDefaults.cardColors(containerColor = Color(0xFF1D1E2C)),
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 16.dp)
                .testTag("audio_player_card")
        ) {
            Column(modifier = Modifier.padding(12.dp)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        IconButton(
                            onClick = {
                                if (isPlaying) {
                                    viewModel.pausePlayback()
                                } else {
                                    if (playPositionMs > 0) {
                                        viewModel.pausePlayback()
                                    } else {
                                        viewModel.startPlayback(meeting.audioPath)
                                    }
                                }
                            },
                            colors = IconButtonDefaults.iconButtonColors(
                                containerColor = if (isPlaying) Color(0xFFD32F2F) else Color(0xFF3F51B5),
                                contentColor = Color.White
                            ),
                            modifier = Modifier.size(40.dp)
                        ) {
                            Icon(
                                imageVector = if (isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                                contentDescription = if (isPlaying) "Pausar" else "Reproducir"
                            )
                        }

                        Spacer(modifier = Modifier.width(10.dp))

                        Column {
                            Text(
                                "Escuchar Audio Grabado",
                                fontWeight = FontWeight.Bold,
                                color = Color.White,
                                fontSize = 12.sp
                            )
                            val elapsedMin = (playPositionMs / 60000).toString().padStart(2, '0')
                            val elapsedSec = ((playPositionMs % 60000) / 1000).toString().padStart(2, '0')
                            val totalMin = (meeting.durationMs / 60000).toString().padStart(2, '0')
                            val totalSec = ((meeting.durationMs % 60000) / 1000).toString().padStart(2, '0')

                            Text(
                                "$elapsedMin:$elapsedSec / $totalMin:$totalSec",
                                fontSize = 11.sp,
                                color = Color(0xFF8E92B3)
                            )
                        }
                    }

                    if (isPlaying) {
                        CircularProgressIndicator(
                            color = Color(0xFF00C853),
                            strokeWidth = 2.dp,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                LinearProgressIndicator(
                    progress = { progress },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(6.dp)
                        .clip(RoundedCornerShape(3.dp)),
                    color = Color(0xFF3F51B5),
                    trackColor = Color(0xFF2C2D42)
                )
            }
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(Color(0xFF1C1D2A), RoundedCornerShape(10.dp))
                .padding(2.dp)
        ) {
            val tabs = listOf("Resumen Ejecutivo", "Transcripción", "Participantes")
            tabs.forEachIndexed { index, title ->
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(8.dp))
                        .background(if (subTabSelection == index) Color(0xFF2C2D42) else Color.Transparent)
                        .clickable { subTabSelection = index }
                        .padding(vertical = 8.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = title,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (subTabSelection == index) Color.White else Color(0xFF8E92B3),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(14.dp))

        when (subTabSelection) {
            0 -> {
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .background(Color(0xFF13141F), RoundedCornerShape(12.dp))
                        .padding(16.dp)
                ) {
                    MarkdownContentRenderer(markdown = meeting.summaryMarkdown)
                }
            }
            1 -> {
                if (transcript.isEmpty()) {
                    Box(
                        modifier = Modifier.weight(1f).fillMaxWidth(),
                        contentAlignment = Alignment.Center
                    ) {
                        Text("No se generó transcripción.", color = Color(0xFF8E92B3))
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        items(transcript) { line ->
                            DialogueItemRow(
                                line = line,
                                onSpeakerBadgeClicked = { onTriggerRename(line.speaker) }
                            )
                        }
                    }
                }
            }
            2 -> {
                if (participants.isEmpty()) {
                    Box(
                        modifier = Modifier.weight(1f).fillMaxWidth(),
                        contentAlignment = Alignment.Center
                    ) {
                        Text("No se listaron firmas de voz.", color = Color(0xFF8E92B3))
                    }
                } else {
                    Column(modifier = Modifier.weight(1f).fillMaxWidth()) {
                        Text(
                            text = "Firmas vocales identificadas en esta reunión:",
                            fontSize = 12.sp,
                            color = Color(0xFF8E92B3),
                            modifier = Modifier.padding(bottom = 10.dp)
                        )
                        LazyColumn(
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            items(participants) { participant ->
                                ParticipantMappingRow(
                                    participant = participant,
                                    onEditClicked = { onTriggerRename(participant.name) }
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun DialogueItemRow(
    line: DialogueLine,
    onSpeakerBadgeClicked: () -> Unit
) {
    val badgeColor = remember(line.speaker) {
        val hash = line.speaker.hashCode()
        val r = (hash and 0xFF0000 shr 16) % 120 + 40
        val g = (hash and 0x00FF00 shr 8) % 120 + 40
        val b = (hash and 0x0000FF) % 120 + 40
        Color(r, g, b, 255)
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color(0xFF1D1E2C), RoundedCornerShape(12.dp))
            .padding(12.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
            modifier = Modifier.fillMaxWidth()
        ) {
            Surface(
                color = badgeColor.copy(alpha = 0.15f),
                shape = RoundedCornerShape(6.dp),
                border = BorderStroke(1.dp, badgeColor.copy(alpha = 0.5f)),
                modifier = Modifier
                    .clickable { onSpeakerBadgeClicked() }
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                ) {
                    Icon(
                        Icons.Default.RecordVoiceOver,
                        "Hablante",
                        tint = badgeColor,
                        modifier = Modifier.size(12.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = line.speaker,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = badgeColor
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Icon(
                        Icons.Default.Edit,
                        "Corregir nombre",
                        tint = badgeColor.copy(alpha = 0.7f),
                        modifier = Modifier.size(10.dp)
                    )
                }
            }

            Text(
                "Voz",
                fontSize = 9.sp,
                color = Color(0xFF8E92B3).copy(alpha = 0.6f)
            )
        }

        Spacer(modifier = Modifier.height(6.dp))

        Text(
            text = line.text,
            color = Color.White,
            fontSize = 13.sp,
            lineHeight = 18.sp
        )
    }
}

@Composable
fun ParticipantMappingRow(
    participant: Participant,
    onEditClicked: () -> Unit
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = Color(0xFF1D1E2C)),
        shape = RoundedCornerShape(10.dp),
        border = BorderStroke(1.dp, Color(0xFF2C2D42))
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    Icons.Default.Face,
                    "Rostro",
                    tint = Color(0xFF3F51B5),
                    modifier = Modifier.size(24.dp)
                )
                Spacer(modifier = Modifier.width(12.dp))
                Column {
                    Text(
                        text = participant.name,
                        fontWeight = FontWeight.Bold,
                        color = Color.White,
                        fontSize = 14.sp
                    )
                    Text(
                        text = "Identificador: ${participant.voiceNickname}",
                        fontSize = 11.sp,
                        color = Color(0xFF8E92B3)
                    )
                }
            }

            IconButton(
                onClick = onEditClicked,
                colors = IconButtonDefaults.iconButtonColors(contentColor = Color(0xFF3F51B5))
            ) {
                Icon(Icons.Default.Edit, "Editar Nombre")
            }
        }
    }
}

@Composable
fun MarkdownContentRenderer(markdown: String) {
    val lines = remember(markdown) { markdown.split("\n") }

    LazyColumn(modifier = Modifier.fillMaxSize()) {
        items(lines) { line ->
            val trimmedLine = line.trim()
            when {
                trimmedLine.startsWith("###") -> {
                    Text(
                        text = trimmedLine.removePrefix("###").trim(),
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF00C853),
                        modifier = Modifier.padding(top = 12.dp, bottom = 4.dp)
                    )
                }
                trimmedLine.startsWith("##") -> {
                    Text(
                        text = trimmedLine.removePrefix("##").trim().uppercase(),
                        fontSize = 15.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = Color(0xFF3F51B5),
                        modifier = Modifier.padding(top = 16.dp, bottom = 6.dp)
                    )
                }
                trimmedLine.startsWith("#") -> {
                    Text(
                        text = trimmedLine.removePrefix("#").trim(),
                        fontSize = 18.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = Color.White,
                        modifier = Modifier.padding(top = 20.dp, bottom = 8.dp)
                    )
                }
                trimmedLine.startsWith("-") || trimmedLine.startsWith("*") -> {
                    val bulletText = trimmedLine.removePrefix("-").removePrefix("*").trim()
                    val boldParts = bulletText.split("**")
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 3.dp),
                        verticalAlignment = Alignment.Top
                    ) {
                        Text(
                            "•",
                            color = Color(0xFF3F51B5),
                            fontWeight = FontWeight.ExtraBold,
                            modifier = Modifier.padding(end = 8.dp),
                            fontSize = 14.sp
                        )
                        if (boldParts.size >= 3) {
                            Text(
                                text = boldParts[0],
                                fontSize = 12.sp,
                                color = Color.White,
                                lineHeight = 16.sp
                            )
                            Text(
                                text = boldParts[1],
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color.White,
                                lineHeight = 16.sp,
                                modifier = Modifier.padding(horizontal = 2.dp)
                            )
                            Text(
                                text = boldParts.subList(2, boldParts.size).joinToString(""),
                                fontSize = 12.sp,
                                color = Color.White,
                                lineHeight = 16.sp
                            )
                        } else {
                            Text(
                                text = bulletText,
                                fontSize = 12.sp,
                                color = Color.White,
                                lineHeight = 16.sp
                            )
                        }
                    }
                }
                trimmedLine.isBlank() -> {
                    Spacer(modifier = Modifier.height(8.dp))
                }
                else -> {
                    Text(
                        text = trimmedLine,
                        fontSize = 12.sp,
                        color = Color.White,
                        lineHeight = 17.sp,
                        modifier = Modifier.padding(vertical = 4.dp)
                    )
                }
            }
        }
    }
}

@Composable
fun RenameParticipantDialog(
    oldName: String,
    onDismiss: () -> Unit,
    onConfirm: (newName: String) -> Unit
) {
    var newName by remember { mutableStateOf(oldName) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Renombrar Hablante", color = Color.White) },
        text = {
            Column {
                Text(
                    "Cambiar el nombre asignado de '$oldName' en toda esta minuta (diálogos e identificador vocal).",
                    color = Color(0xFF8E92B3),
                    fontSize = 12.sp,
                    lineHeight = 16.sp,
                    modifier = Modifier.padding(bottom = 12.dp)
                )
                OutlinedTextField(
                    value = newName,
                    onValueChange = { newName = it },
                    label = { Text("Nombre Real", color = Color(0xFF8E92B3)) },
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White,
                        focusedBorderColor = Color(0xFF3F51B5),
                        unfocusedBorderColor = Color(0xFF8E92B3)
                    ),
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            Button(
                onClick = { onConfirm(newName) },
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF3F51B5))
            ) {
                Text("Actualizar")
            }
        },
        dismissButton = {
            TextButton(
                onClick = onDismiss,
                colors = ButtonDefaults.textButtonColors(contentColor = Color(0xFF8E92B3))
            ) {
                Text("Cancelar")
            }
        },
        containerColor = Color(0xFF1D1E2C)
    )
}
