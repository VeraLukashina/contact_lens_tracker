package com.example

import android.app.DatePickerDialog
import android.content.Context
import android.os.Bundle
import android.Manifest
import android.os.Build
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.*
import com.example.ui.theme.MyApplicationTheme
import com.example.ui.theme.OverdueRed
import java.util.Calendar

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        // Initialize local Room Database and Repository
        val database = LensDatabase.getDatabase(applicationContext)
        val repository = LensRepository(database.lensDao())
        val viewModel = ViewModelProvider(
            this,
            LensViewModelFactory(repository)
        )[LensViewModel::class.java]

        // Schedule daily notifications
        AlarmScheduler.scheduleDailyNotification(applicationContext)

        setContent {
            MyApplicationTheme {
                Scaffold(
                    modifier = Modifier
                        .fillMaxSize()
                        .testTag("main_scaffold"),
                    contentWindowInsets = WindowInsets.safeDrawing
                ) { innerPadding ->
                    LensTrackerDashboard(
                        viewModel = viewModel,
                        modifier = Modifier.padding(innerPadding)
                    )
                }
            }
        }
    }
}

@Composable
fun LensTrackerDashboard(
    viewModel: LensViewModel,
    modifier: Modifier = Modifier
) {
    val activeLens by viewModel.activeLens.collectAsStateWithLifecycle()
    val activeStats by viewModel.activeLensStats.collectAsStateWithLifecycle()
    val allStocks by viewModel.allStocks.collectAsStateWithLifecycle()
    val visitInfo by viewModel.ophthalmologistVisit.collectAsStateWithLifecycle()
    val stockWarnings by viewModel.stockWarnings.collectAsStateWithLifecycle()
    val ophAlert by viewModel.ophthalmologistAlert.collectAsStateWithLifecycle()

    // Dialog trigger states
    var showStartLensDialog by remember { mutableStateOf(false) }
    var showAddStockDialog by remember { mutableStateOf(false) }
    var showVisitDialog by remember { mutableStateOf(false) }
    var showEditDurationDialog by remember { mutableStateOf(false) }
    var editingDurationDays by remember { mutableIntStateOf(14) }
    var showInstructionsDialog by remember { mutableStateOf(false) }

    val context = LocalContext.current
    LaunchedEffect(activeLens, activeStats, allStocks) {
        LensWidgetProvider.triggerUpdate(context)
    }

    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        val permissionLauncher = rememberLauncherForActivityResult(
            contract = ActivityResultContracts.RequestPermission()
        ) { _ -> }
        LaunchedEffect(Unit) {
            permissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // App Title Section
        HeaderSection(onHelpClick = { showInstructionsDialog = true })

        // Warning Banners (Dynamic in-app alerts)
        AnimatedVisibility(
            visible = stockWarnings.isNotEmpty() || ophAlert != null,
            enter = fadeIn() + slideInVertically(),
            exit = fadeOut() + slideOutVertically()
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                stockWarnings.forEach { warning ->
                    AlertBanner(
                        message = warning,
                        color = MaterialTheme.colorScheme.tertiaryContainer,
                        iconColor = MaterialTheme.colorScheme.tertiary,
                        testTag = "stock_warning_banner"
                    )
                }
                ophAlert?.let { alert ->
                    AlertBanner(
                        message = "${alert.message}\n(Напоминание повторено ${alert.remindersCount} раз(а))",
                        color = MaterialTheme.colorScheme.errorContainer,
                        iconColor = MaterialTheme.colorScheme.error,
                        testTag = "oph_warning_banner"
                    )
                }
            }
        }

        // 1. ACTIVE LENS TRACKER CARD
        ActiveLensCard(
            activeLens = activeLens,
            stats = activeStats,
            onToggleSkip = { viewModel.toggleTodaySkipped() },
            onPrematureChange = {
                val duration = activeLens?.durationDays ?: 14
                viewModel.startNewLensPair(duration, allStocks.firstOrNull())
            },
            onStartNew = { showStartLensDialog = true },
            onStopWearing = { viewModel.stopWearingActiveLens() },
            onEditStartDate = { viewModel.updateActiveLensStartDate(it) },
            onEditDuration = { duration ->
                editingDurationDays = duration
                showEditDurationDialog = true
            }
        )

        // 2. STOCKS / INVENTORY SECTION
        StockSection(
            stocks = allStocks,
            activeLens = activeLens,
            activeStats = activeStats,
            onAddStockClick = { showAddStockDialog = true },
            onUpdateStock = { viewModel.updateStock(it) },
            onDeleteStock = { viewModel.deleteStock(it) }
        )

        // 3. OPHTHALMOLOGIST VISIT CARD
        OphthalmologistCard(
            visit = visitInfo,
            ophAlert = ophAlert,
            onEditVisit = { showVisitDialog = true },
            onClearVisit = { viewModel.clearOphthalmologistData() }
        )

        // 4. NOTIFICATION SETTINGS CARD
        NotificationSettingsCard()

        Spacer(modifier = Modifier.height(12.dp))

        // 5. WIDGET SETTINGS CARD
        WidgetSettingsCard()

        Spacer(modifier = Modifier.height(24.dp))
    }

    // Modal dialogs
    if (showStartLensDialog) {
        StartNewLensDialog(
            stocks = allStocks,
            onDismiss = { showStartLensDialog = false },
            onStart = { duration, selectedStock ->
                viewModel.startNewLensPair(duration, selectedStock)
                showStartLensDialog = false
            }
        )
    }

    if (showAddStockDialog) {
        AddStockDialog(
            existingStock = allStocks.firstOrNull(),
            onDismiss = { showAddStockDialog = false },
            onAdd = { brand, diopter, pairsPerBox, boxCount, openBoxPairs ->
                val existing = allStocks.firstOrNull()
                if (existing != null) {
                    val updated = existing.copy(
                        pairsPerBox = pairsPerBox,
                        boxCount = boxCount,
                        pairsInOpenBox = openBoxPairs
                    )
                    viewModel.updateStock(updated)
                } else {
                    viewModel.addNewStock(brand, diopter, pairsPerBox, boxCount, openBoxPairs)
                }
                showAddStockDialog = false
            }
        )
    }

    if (showVisitDialog) {
        OphthalmologistVisitDialog(
            currentVisit = visitInfo,
            onDismiss = { showVisitDialog = false },
            onSave = { lastVisit, nextAppointment ->
                viewModel.recordCheckup(lastVisit, nextAppointment)
                showVisitDialog = false
            }
        )
    }

    if (showEditDurationDialog) {
        EditDurationDialog(
            currentDuration = editingDurationDays,
            onDismiss = { showEditDurationDialog = false },
            onConfirm = { days ->
                viewModel.updateActiveLensDuration(days)
                showEditDurationDialog = false
            }
        )
    }

    if (showInstructionsDialog) {
        UserInstructionsDialog(
            onDismiss = { showInstructionsDialog = false }
        )
    }
}

@Composable
fun HeaderSection(onHelpClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = "Мои Линзы",
                fontSize = 24.sp,
                fontWeight = FontWeight.Bold,
                color = com.example.ui.theme.TextDark,
                letterSpacing = (-0.5).sp
            )
        }
        IconButton(
            onClick = onHelpClick,
            modifier = Modifier.testTag("help_button")
        ) {
            Box(
                modifier = Modifier
                    .size(24.dp)
                    .background(com.example.ui.theme.BrandBlue, CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "?",
                    color = Color.White,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center
                )
            }
        }
    }
}

@Composable
fun AlertBanner(
    message: String,
    color: Color,
    iconColor: Color,
    testTag: String
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .shadow(1.dp, RoundedCornerShape(16.dp))
            .testTag(testTag),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = color),
        border = androidx.compose.foundation.BorderStroke(1.dp, color.copy(alpha = 0.5f))
    ) {
        Row(
            modifier = Modifier.padding(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Default.Warning,
                contentDescription = "Alert",
                tint = iconColor,
                modifier = Modifier.size(24.dp)
            )
            Spacer(modifier = Modifier.width(12.dp))
            Text(
                text = message,
                fontSize = 13.sp,
                color = com.example.ui.theme.TextDark,
                fontWeight = FontWeight.Medium,
                lineHeight = 18.sp
            )
        }
    }
}

@Composable
fun ActiveLensCard(
    activeLens: LensWear?,
    stats: LensStats?,
    onToggleSkip: () -> Unit,
    onPrematureChange: () -> Unit,
    onStartNew: () -> Unit,
    onStopWearing: () -> Unit,
    onEditStartDate: (Long) -> Unit,
    onEditDuration: (Int) -> Unit
) {
    val isOverdue = stats?.isOverdue == true
    val cardBg = if (isOverdue) {
        com.example.ui.theme.OverdueRedBg
    } else if (activeLens != null) {
        com.example.ui.theme.BrandBlue
    } else {
        MaterialTheme.colorScheme.surface
    }

    val contentColor = if (activeLens != null && !isOverdue) {
        Color.White
    } else {
        com.example.ui.theme.TextDark
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .shadow(4.dp, RoundedCornerShape(28.dp))
            .testTag("active_lens_card"),
        shape = RoundedCornerShape(28.dp),
        colors = CardDefaults.cardColors(containerColor = cardBg)
    ) {
        Column(
            modifier = Modifier.padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Текущие Линзы".uppercase(),
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    color = if (activeLens != null && !isOverdue) com.example.ui.theme.SoftBlueContainer else com.example.ui.theme.GrayText,
                    letterSpacing = 1.sp
                )
                if (activeLens != null) {
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = if (isOverdue) com.example.ui.theme.OverdueRed.copy(alpha = 0.2f) else Color.White.copy(alpha = 0.2f)
                    ) {
                        Text(
                            text = if (isOverdue) "ПРОСРОЧЕНО" else "АКТИВНО",
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (isOverdue) com.example.ui.theme.OverdueRed else Color.White,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                        )
                    }
                }
            }
            Spacer(modifier = Modifier.height(20.dp))

            if (activeLens != null && stats != null) {
                // Circular Wearing Progress Indicator
                val progressFraction = if (isOverdue) 1f else {
                    stats.daysWorn.toFloat() / activeLens.durationDays.toFloat()
                }

                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier.size(160.dp)
                ) {
                    val sweepColor = if (isOverdue) com.example.ui.theme.OverdueRed else Color(0xFF38BDF8)
                    val trackColor = if (isOverdue) com.example.ui.theme.OverdueRed.copy(alpha = 0.15f) else Color.White.copy(alpha = 0.15f)

                    Canvas(modifier = Modifier.fillMaxSize()) {
                        drawCircle(
                            color = trackColor,
                            style = Stroke(width = 10.dp.toPx(), cap = StrokeCap.Round)
                        )
                        drawArc(
                            color = sweepColor,
                            startAngle = -90f,
                            sweepAngle = progressFraction * 360f,
                            useCenter = false,
                            style = Stroke(width = 10.dp.toPx(), cap = StrokeCap.Round)
                        )
                    }

                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.padding(12.dp)
                    ) {
                        if (isOverdue) {
                            Text(
                                text = "ПРОСРОЧЕНО",
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Black,
                                color = com.example.ui.theme.OverdueRed,
                                letterSpacing = 1.sp
                            )
                            Text(
                                text = "+${stats.overdueDays} дн.",
                                fontSize = 26.sp,
                                fontWeight = FontWeight.Bold,
                                color = com.example.ui.theme.OverdueRed
                            )
                        } else {
                            Text(
                                text = "Осталось",
                                fontSize = 11.sp,
                                color = com.example.ui.theme.SoftBlueContainer
                            )
                            Text(
                                text = "${stats.remainingDays}",
                                fontSize = 38.sp,
                                fontWeight = FontWeight.ExtraBold,
                                color = Color.White
                            )
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.Center
                            ) {
                                Text(
                                    text = "из ${activeLens.durationDays} дн.",
                                    fontSize = 11.sp,
                                    color = com.example.ui.theme.SoftBlueContainer
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                IconButton(
                                    onClick = { onEditDuration(activeLens.durationDays) },
                                    modifier = Modifier.size(20.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Edit,
                                        contentDescription = "Edit wear duration",
                                        tint = if (isOverdue) com.example.ui.theme.TextDark else Color.White,
                                        modifier = Modifier.size(12.dp)
                                    )
                                }
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(20.dp))

                val changeDateMs = DateUtils.getChangeDate(activeLens.startDate, activeLens.durationDays, stats.skippedDaysCount)
                // Detail labels row
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceAround
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = "Дата смены",
                            fontSize = 11.sp,
                            color = if (isOverdue) com.example.ui.theme.GrayText else com.example.ui.theme.SoftBlueContainer
                        )
                        Text(
                            text = DateUtils.formatDateToDisplay(changeDateMs),
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (isOverdue) com.example.ui.theme.TextDark else Color.White
                        )
                    }
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = "Дата начала",
                            fontSize = 11.sp,
                            color = if (isOverdue) com.example.ui.theme.GrayText else com.example.ui.theme.SoftBlueContainer
                        )
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.Center
                        ) {
                            Text(
                                text = DateUtils.formatDateToDisplay(activeLens.startDate),
                                fontSize = 15.sp,
                                fontWeight = FontWeight.Bold,
                                color = if (isOverdue) com.example.ui.theme.TextDark else Color.White
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            val context = LocalContext.current
                            IconButton(
                                onClick = {
                                    val cal = java.util.Calendar.getInstance().apply { timeInMillis = activeLens.startDate }
                                    android.app.DatePickerDialog(
                                        context,
                                        { _, year, month, dayOfMonth ->
                                            val newCal = java.util.Calendar.getInstance().apply {
                                                set(java.util.Calendar.YEAR, year)
                                                set(java.util.Calendar.MONTH, month)
                                                set(java.util.Calendar.DAY_OF_MONTH, dayOfMonth)
                                                set(java.util.Calendar.HOUR_OF_DAY, 0)
                                                set(java.util.Calendar.MINUTE, 0)
                                                set(java.util.Calendar.SECOND, 0)
                                                set(java.util.Calendar.MILLISECOND, 0)
                                            }
                                            onEditStartDate(newCal.timeInMillis)
                                        },
                                        cal.get(java.util.Calendar.YEAR),
                                        cal.get(java.util.Calendar.MONTH),
                                        cal.get(java.util.Calendar.DAY_OF_MONTH)
                                    ).show()
                                },
                                modifier = Modifier.size(24.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Edit,
                                    contentDescription = "Edit start date",
                                    tint = if (isOverdue) com.example.ui.theme.TextDark else Color.White,
                                    modifier = Modifier.size(14.dp)
                                )
                            }
                        }
                    }
                    if (stats.skippedDaysCount > 0) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(
                                text = "Пропущено",
                                fontSize = 11.sp,
                                color = if (isOverdue) com.example.ui.theme.GrayText else com.example.ui.theme.SoftBlueContainer
                            )
                            Text(
                                text = "${stats.skippedDaysCount} дн.",
                                fontSize = 15.sp,
                                fontWeight = FontWeight.Bold,
                                color = if (isOverdue) com.example.ui.theme.OverdueRed else Color(0xFFFDBA74)
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(20.dp))

                // Skip wearing today toggle with premium custom design
                val toggleBg = if (isOverdue) {
                    Color.Black.copy(alpha = 0.05f)
                } else {
                    Color.White.copy(alpha = 0.12f)
                }
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(16.dp))
                        .clickable { onToggleSkip() }
                        .testTag("skip_today_toggle"),
                    color = toggleBg
                ) {
                    Row(
                        modifier = Modifier.padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                            Text(
                                text = "👁️‍🗨️",
                                fontSize = 18.sp,
                                modifier = Modifier.padding(end = 12.dp)
                            )
                            Column {
                                Text(
                                    text = "Сегодня не ношу линзы",
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = if (isOverdue) com.example.ui.theme.TextDark else Color.White
                                )
                                Text(
                                    text = "Этот день исключен из срока использования",
                                    fontSize = 11.sp,
                                    color = if (isOverdue) com.example.ui.theme.GrayText else com.example.ui.theme.SoftBlueContainer
                                )
                            }
                        }
                        Checkbox(
                            checked = stats.isTodaySkipped,
                            onCheckedChange = { onToggleSkip() },
                            modifier = Modifier.testTag("skip_today_checkbox"),
                            colors = CheckboxDefaults.colors(
                                checkedColor = if (isOverdue) com.example.ui.theme.OverdueRed else Color.White,
                                uncheckedColor = if (isOverdue) com.example.ui.theme.GrayText else Color.White.copy(alpha = 0.6f),
                                checkmarkColor = if (isOverdue) Color.White else com.example.ui.theme.BrandBlue
                            )
                        )
                    }
                }

                Spacer(modifier = Modifier.height(20.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Button(
                        onClick = onPrematureChange,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp)
                            .testTag("replace_lens_button"),
                        shape = RoundedCornerShape(24.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = toggleBg,
                            contentColor = if (isOverdue) com.example.ui.theme.TextDark else Color.White
                        ),
                        elevation = ButtonDefaults.buttonElevation(defaultElevation = 2.dp)
                    ) {
                        val isChangeDayOrOverdue = isOverdue || (stats?.remainingDays ?: 0) <= 0
                        val buttonText = if (isChangeDayOrOverdue) "Смена пары" else "Досрочная смена пары"
                        Text(buttonText, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                    }
                }

            } else {
                // Empty state when there are no active lenses
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Image(
                        painter = painterResource(id = R.drawable.img_lens_hero),
                        contentDescription = "Lens illustration",
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(130.dp)
                            .clip(RoundedCornerShape(16.dp))
                            .shadow(1.dp, RoundedCornerShape(16.dp)),
                        contentScale = ContentScale.Crop
                    )

                    Spacer(modifier = Modifier.height(18.dp))

                    Text(
                        text = "Активные линзы не надеты",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        color = com.example.ui.theme.TextDark
                    )
                    Text(
                        text = "Запустите отслеживание новой пары линз прямо сейчас, чтобы автоматически рассчитать срок ношения.",
                        fontSize = 12.sp,
                        color = com.example.ui.theme.GrayText,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                    )

                    Spacer(modifier = Modifier.height(14.dp))

                    Button(
                        onClick = onStartNew,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp)
                            .testTag("start_lens_empty_button"),
                        shape = RoundedCornerShape(24.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = com.example.ui.theme.BrandBlue,
                            contentColor = Color.White
                        )
                    ) {
                        Icon(imageVector = Icons.Default.Add, contentDescription = "Start")
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Надеть новые линзы", fontSize = 14.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}

@Composable
fun StockSection(
    stocks: List<LensStock>,
    activeLens: LensWear?,
    activeStats: LensStats?,
    onAddStockClick: () -> Unit,
    onUpdateStock: (LensStock) -> Unit,
    onDeleteStock: (LensStock) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("stock_section"),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Запас Линз",
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onBackground
            )
            TextButton(
                onClick = onAddStockClick,
                modifier = Modifier.testTag("add_stock_text_button")
            ) {
                Icon(imageVector = Icons.Default.Add, contentDescription = "Add")
                Spacer(modifier = Modifier.width(4.dp))
                Text("Указать запас", fontSize = 14.sp)
            }
        }

        if (stocks.isEmpty()) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f))
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Icon(
                        imageVector = Icons.Default.Notifications,
                        contentDescription = "Stock",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f),
                        modifier = Modifier.size(36.dp)
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "Запасы пусты",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = "Добавьте упакованные линзы, чтобы списывать их автоматически и получать предупреждения, когда останется последняя пара.",
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(top = 4.dp)
                    )
                }
            }
        } else {
            stocks.forEach { stock ->
                StockItemCard(
                    stock = stock,
                    activeLens = activeLens,
                    activeStats = activeStats,
                    onUpdateStock = onUpdateStock,
                    onDeleteStock = onDeleteStock
                )
            }
        }
    }
}

fun getRussianPairWord(count: Int): String {
    val mod10 = count % 10
    val mod100 = count % 100
    return when {
        mod100 in 11..14 -> "пар"
        mod10 == 1 -> "пара"
        mod10 in 2..4 -> "пары"
        else -> "пар"
    }
}

@Composable
fun StockItemCard(
    stock: LensStock,
    activeLens: LensWear?,
    activeStats: LensStats?,
    onUpdateStock: (LensStock) -> Unit,
    onDeleteStock: (LensStock) -> Unit
) {
    val totalPairs = (stock.boxCount * stock.pairsPerBox) + stock.pairsInOpenBox
    val isLowStock = totalPairs == 1
    val isLastBox = stock.boxCount == 0

    val activeDuration = activeLens?.durationDays ?: 14
    val remainingOfActive = activeStats?.remainingDays?.coerceAtLeast(0) ?: 0
    val totalRemainingDays = remainingOfActive + (totalPairs * activeDuration)
    val stockEndDateMs = System.currentTimeMillis() + (totalRemainingDays * 24L * 60L * 60L * 1000L)

    val cardBg = if (isLowStock) {
        com.example.ui.theme.AlertOrangeBg
    } else {
        com.example.ui.theme.LightSurface
    }

    val cardBorder = if (isLowStock) {
        androidx.compose.foundation.BorderStroke(1.dp, com.example.ui.theme.AlertOrangeText.copy(alpha = 0.3f))
    } else {
        androidx.compose.foundation.BorderStroke(1.dp, com.example.ui.theme.GrayBorder)
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .shadow(1.dp, RoundedCornerShape(24.dp))
            .testTag("stock_item_${stock.id}"),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = cardBg),
        border = cardBorder
    ) {
        Column(modifier = Modifier.padding(20.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Stock numbers
                Column {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = "$totalPairs " + getRussianPairWord(totalPairs),
                            fontSize = 16.sp,
                            fontWeight = FontWeight.ExtraBold,
                            color = if (isLowStock) com.example.ui.theme.AlertOrangeText else com.example.ui.theme.BrandBlue
                        )
                        if (isLowStock) {
                            Spacer(modifier = Modifier.width(6.dp))
                            Surface(
                                shape = RoundedCornerShape(4.dp),
                                color = com.example.ui.theme.AlertOrangeText
                            ) {
                                Text(
                                    text = "ПОСЛЕДНЯЯ ПАРА!",
                                    fontSize = 8.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color.White,
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                )
                            }
                        } else if (isLastBox) {
                            Spacer(modifier = Modifier.width(6.dp))
                            Surface(
                                shape = RoundedCornerShape(4.dp),
                                color = com.example.ui.theme.AlertOrangeText.copy(alpha = 0.15f)
                            ) {
                                Text(
                                    text = "ПОСЛЕДНЯЯ КОРОБКА",
                                    fontSize = 8.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = com.example.ui.theme.AlertOrangeText,
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                )
                            }
                        }
                    }
                    Text(
                        text = "Целых упаковок: ${stock.boxCount} шт.\nВ открытой упаковке: ${stock.pairsInOpenBox} " + getRussianPairWord(stock.pairsInOpenBox),
                        fontSize = 11.sp,
                        color = com.example.ui.theme.GrayText,
                        lineHeight = 15.sp,
                        fontWeight = FontWeight.Medium
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "Запаса хватит до: ${DateUtils.formatDateToDisplay(stockEndDateMs)}",
                        fontSize = 11.sp,
                        color = if (isLowStock) com.example.ui.theme.AlertOrangeText else com.example.ui.theme.BrandBlue,
                        fontWeight = FontWeight.Bold
                    )
                }

                // Plus / Minus Adjusters for opened box pairs
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    IconButton(
                        onClick = {
                            if (stock.pairsInOpenBox > 0) {
                                onUpdateStock(stock.copy(pairsInOpenBox = stock.pairsInOpenBox - 1))
                            } else if (stock.boxCount > 0) {
                                onUpdateStock(
                                    stock.copy(
                                        boxCount = stock.boxCount - 1,
                                        pairsInOpenBox = stock.pairsPerBox - 1
                                    )
                                )
                            }
                        },
                        modifier = Modifier
                            .size(32.dp)
                            .background(
                                Color.Black.copy(alpha = 0.05f),
                                CircleShape
                            )
                            .testTag("decrement_stock_${stock.id}")
                    ) {
                        Text(
                            text = "−",
                            fontWeight = FontWeight.Bold,
                            fontSize = 16.sp,
                            color = com.example.ui.theme.TextDark
                        )
                    }

                    IconButton(
                        onClick = {
                            if (stock.pairsInOpenBox + 1 >= stock.pairsPerBox) {
                                // Full box completed, increment box count
                                onUpdateStock(
                                    stock.copy(
                                        boxCount = stock.boxCount + 1,
                                        pairsInOpenBox = 0
                                    )
                                )
                            } else {
                                onUpdateStock(stock.copy(pairsInOpenBox = stock.pairsInOpenBox + 1))
                            }
                        },
                        modifier = Modifier
                            .size(32.dp)
                            .background(
                                Color.Black.copy(alpha = 0.05f),
                                CircleShape
                            )
                            .testTag("increment_stock_${stock.id}")
                    ) {
                        Text(
                            text = "+",
                            fontWeight = FontWeight.Bold,
                            fontSize = 16.sp,
                            color = com.example.ui.theme.TextDark
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun OphthalmologistCard(
    visit: OphthalmologistVisit?,
    ophAlert: OphAlert?,
    onEditVisit: () -> Unit,
    onClearVisit: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .shadow(1.dp, RoundedCornerShape(24.dp))
            .testTag("ophthalmologist_card"),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = com.example.ui.theme.PurpleContainer),
        border = androidx.compose.foundation.BorderStroke(1.dp, com.example.ui.theme.PurpleBorder)
    ) {
        Column(modifier = Modifier.padding(20.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "ОФТАЛЬМОЛОГ",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = com.example.ui.theme.PurpleText,
                        letterSpacing = 1.sp
                    )
                }

                // Beautiful white calendar badge
                Box(
                    modifier = Modifier
                        .size(48.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(Color.White)
                        .shadow(1.dp, RoundedCornerShape(12.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    Text(text = "🗓️", fontSize = 22.sp)
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            if (visit != null && visit.lastVisitDate != null) {
                val nextSuggestedDate = DateUtils.addOneYear(visit.lastVisitDate)

                Column(modifier = Modifier.fillMaxWidth()) {
                    Text(
                        text = "Последний визит: ${DateUtils.formatDateToDisplay(visit.lastVisitDate)}",
                        fontSize = 13.sp,
                        color = com.example.ui.theme.PurpleTextDark,
                        fontWeight = FontWeight.SemiBold
                    )

                    Text(
                        text = "Рекомендуемый следующий осмотр (через 1 год): ${DateUtils.formatDateToDisplay(nextSuggestedDate)}",
                        fontSize = 12.sp,
                        color = com.example.ui.theme.PurpleTextDark.copy(alpha = 0.7f),
                        modifier = Modifier.padding(top = 2.dp)
                    )

                    if (visit.nextAppointmentDate != null) {
                        Spacer(modifier = Modifier.height(8.dp))
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = Color.White.copy(alpha = 0.6f),
                            modifier = Modifier.fillMaxWidth(),
                            border = androidx.compose.foundation.BorderStroke(1.dp, com.example.ui.theme.PurpleBorder)
                        ) {
                            Row(
                                modifier = Modifier.padding(10.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = Icons.Default.CheckCircle,
                                    contentDescription = "Confirmed",
                                    tint = com.example.ui.theme.PurpleText,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = "Запланированная запись на прием: ${DateUtils.formatDateToDisplay(visit.nextAppointmentDate)}",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = com.example.ui.theme.PurpleTextDark
                                )
                            }
                        }
                    } else {
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "Запись на следующий прием: не запланирована",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Medium,
                            color = if (ophAlert != null) com.example.ui.theme.OverdueRed else com.example.ui.theme.PurpleText.copy(alpha = 0.8f)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Button(
                        onClick = onEditVisit,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(40.dp)
                            .testTag("edit_visit_button"),
                        shape = RoundedCornerShape(20.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = com.example.ui.theme.PurpleText,
                            contentColor = Color.White
                        )
                    ) {
                        Text("Изменить запись", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    }
                }

            } else {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = "Информация о визитах отсутствует",
                        fontSize = 12.sp,
                        color = com.example.ui.theme.PurpleText.copy(alpha = 0.8f)
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Button(
                        onClick = onEditVisit,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(44.dp)
                            .testTag("record_first_visit_button"),
                        shape = RoundedCornerShape(22.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = com.example.ui.theme.PurpleText,
                            contentColor = Color.White
                        )
                    ) {
                        Icon(imageVector = Icons.Default.Add, contentDescription = "Add visit")
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Записать осмотр", fontSize = 13.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}

@Composable
fun NotificationSettingsCard(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val sharedPrefs = remember { context.getSharedPreferences("lens_notifications", Context.MODE_PRIVATE) }
    
    var hour by remember { mutableStateOf(sharedPrefs.getInt("wear_time_hour", 8)) }
    var minute by remember { mutableStateOf(sharedPrefs.getInt("wear_time_minute", 0)) }
    
    val formattedTime = String.format("%02d:%02d", hour, minute)
    
    Box(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(com.example.ui.theme.SoftBlueContainer)
            .padding(horizontal = 16.dp, vertical = 12.dp)
            .testTag("notification_settings_card")
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Обычно надеваю линзы в $formattedTime",
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
                color = com.example.ui.theme.BrandBlue,
                modifier = Modifier.weight(1f)
            )
            
            IconButton(
                onClick = {
                    android.app.TimePickerDialog(
                        context,
                        { _, selectedHour, selectedMinute ->
                            sharedPrefs.edit()
                                .putInt("wear_time_hour", selectedHour)
                                .putInt("wear_time_minute", selectedMinute)
                                .apply()
                            
                            hour = selectedHour
                            minute = selectedMinute
                            
                            // Reschedule alarm
                            AlarmScheduler.scheduleDailyNotification(context)
                        },
                        hour,
                        minute,
                        true // 24-hour format
                    ).show()
                },
                modifier = Modifier
                    .size(36.dp)
                    .background(Color.White, CircleShape)
            ) {
                Icon(
                    imageVector = Icons.Default.Edit,
                    contentDescription = "Изменить время",
                    tint = com.example.ui.theme.BrandBlue,
                    modifier = Modifier.size(16.dp)
                )
            }
        }
    }
}

@Composable
fun WidgetSettingsCard(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val sharedPrefs = remember { context.getSharedPreferences("lens_notifications", Context.MODE_PRIVATE) }
    
    var isTransparent by remember { mutableStateOf(sharedPrefs.getBoolean("widget_transparent", false)) }
    
    Box(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(com.example.ui.theme.SoftBlueContainer)
            .padding(horizontal = 16.dp, vertical = 12.dp)
            .testTag("widget_settings_card")
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Полупрозрачный виджет",
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    color = com.example.ui.theme.BrandBlue
                )
                Text(
                    text = "Сделать фон виджета на главном экране полупрозрачным (50%)",
                    fontSize = 11.sp,
                    color = com.example.ui.theme.BrandBlue.copy(alpha = 0.7f)
                )
            }
            
            Switch(
                checked = isTransparent,
                onCheckedChange = { checked ->
                    sharedPrefs.edit()
                        .putBoolean("widget_transparent", checked)
                        .apply()
                    isTransparent = checked
                    com.example.LensWidgetProvider.triggerUpdate(context)
                },
                modifier = Modifier.testTag("widget_transparent_switch")
            )
        }
    }
}

// ---------------- DIALOGS ----------------

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StartNewLensDialog(
    stocks: List<LensStock>,
    onDismiss: () -> Unit,
    onStart: (Int, LensStock?) -> Unit
) {
    var selectedDays by remember { mutableIntStateOf(14) }
    var selectedStockIndex by remember { mutableIntStateOf(-1) }
    var isExpanded by remember { mutableStateOf(false) }

    Dialog(onDismissRequest = onDismiss) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
                .testTag("start_lens_dialog"),
            shape = RoundedCornerShape(24.dp)
        ) {
            Column(
                modifier = Modifier
                    .padding(24.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Text(
                    text = "Запустить Новые Линзы",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )

                Text(
                    text = "Выберите срок плановой замены линз:",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                )

                // Common Presets (Chips)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    listOf(1, 14, 30).forEach { days ->
                        FilterChip(
                            selected = selectedDays == days,
                            onClick = { selectedDays = days },
                            label = {
                                Text(
                                    text = when (days) {
                                        1 -> "1 день"
                                        14 -> "14 дней"
                                        else -> "30 дней"
                                    }
                                )
                            },
                            modifier = Modifier.testTag("duration_chip_$days")
                        )
                    }
                }

                // Custom Day Input
                OutlinedTextField(
                    value = if (selectedDays > 0) selectedDays.toString() else "",
                    onValueChange = {
                        val value = it.toIntOrNull() ?: 0
                        selectedDays = value
                    },
                    label = { Text("Свой срок ношения (дней)") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("custom_duration_input"),
                    singleLine = true
                )

                Divider()

                Text(
                    text = "Списать пару из запасов (опционально):",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                )

                // Stock selection drop-down or radio list
                if (stocks.isEmpty()) {
                    Text(
                        text = "Запасы пусты. Линза не будет списана из запасов.",
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.error
                    )
                } else {
                    ExposedDropdownMenuBox(
                        expanded = isExpanded,
                        onExpandedChange = { isExpanded = !isExpanded }
                    ) {
                        OutlinedTextField(
                            value = if (selectedStockIndex in stocks.indices) {
                                val s = stocks[selectedStockIndex]
                                if (s.brand == "Контактные линзы" && s.diopter == "0.0") {
                                    "Запас линз"
                                } else {
                                    "${s.brand} (${s.diopter} D)"
                                }
                            } else {
                                "Не списывать из запасов"
                            },
                            onValueChange = {},
                            readOnly = true,
                            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = isExpanded) },
                            modifier = Modifier
                                .menuAnchor()
                                .fillMaxWidth()
                                .testTag("stock_dropdown_field"),
                            colors = ExposedDropdownMenuDefaults.outlinedTextFieldColors()
                        )
                        ExposedDropdownMenu(
                            expanded = isExpanded,
                            onDismissRequest = { isExpanded = false }
                        ) {
                            DropdownMenuItem(
                                text = { Text("Не списывать из запасов") },
                                onClick = {
                                    selectedStockIndex = -1
                                    isExpanded = false
                                },
                                modifier = Modifier.testTag("stock_item_none")
                            )
                            stocks.forEachIndexed { index, stock ->
                                val total = (stock.boxCount * stock.pairsPerBox) + stock.pairsInOpenBox
                                DropdownMenuItem(
                                    text = {
                                        val label = if (stock.brand == "Контактные линзы" && stock.diopter == "0.0") {
                                            "Запас линз - Осталось: $total " + getRussianPairWord(total)
                                        } else {
                                            "${stock.brand} (${stock.diopter} D) - Осталось: $total " + getRussianPairWord(total)
                                        }
                                        Text(text = label)
                                    },
                                    onClick = {
                                        selectedStockIndex = index
                                        isExpanded = false
                                    },
                                    modifier = Modifier.testTag("stock_item_option_$index")
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedButton(
                        onClick = onDismiss,
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("Отмена")
                    }

                    Button(
                        onClick = {
                            val stockToUse = if (selectedStockIndex in stocks.indices) {
                                stocks[selectedStockIndex]
                            } else null
                            onStart(selectedDays.coerceAtLeast(1), stockToUse)
                        },
                        modifier = Modifier
                            .weight(1.5f)
                            .testTag("confirm_start_lens_button"),
                        enabled = selectedDays > 0
                    ) {
                        Text("Надеть")
                    }
                }
            }
        }
    }
}

@Composable
fun AddStockDialog(
    existingStock: LensStock? = null,
    onDismiss: () -> Unit,
    onAdd: (String, String, Int, Int, Int) -> Unit
) {
    val brand = "Контактные линзы"
    val diopter = "0.0"
    var pairsPerBoxStr by remember { mutableStateOf(existingStock?.pairsPerBox?.toString() ?: "3") }
    var boxCountStr by remember { mutableStateOf(existingStock?.boxCount?.toString() ?: "1") }
    var openBoxPairsStr by remember { mutableStateOf(existingStock?.pairsInOpenBox?.toString() ?: "0") }

    Dialog(onDismissRequest = onDismiss) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
                .testTag("add_stock_dialog"),
            shape = RoundedCornerShape(24.dp)
        ) {
            Column(
                modifier = Modifier
                    .padding(24.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Text(
                    text = "Указать запас",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedTextField(
                        value = pairsPerBoxStr,
                        onValueChange = { pairsPerBoxStr = it },
                        label = { Text("Пар в коробке") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier
                            .weight(1f)
                            .testTag("stock_pairs_per_box_input"),
                        singleLine = true
                    )

                    OutlinedTextField(
                        value = boxCountStr,
                        onValueChange = { boxCountStr = it },
                        label = { Text("Целых упаковок") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier
                            .weight(1f)
                            .testTag("stock_box_count_input"),
                        singleLine = true
                    )
                }

                OutlinedTextField(
                    value = openBoxPairsStr,
                    onValueChange = { openBoxPairsStr = it },
                    label = { Text("Пар в открытой коробке") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("stock_open_pairs_input"),
                    singleLine = true
                )

                Spacer(modifier = Modifier.height(10.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedButton(
                        onClick = onDismiss,
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("Отмена")
                    }

                    Button(
                        onClick = {
                            val pairsPerBox = pairsPerBoxStr.toIntOrNull() ?: 3
                            val boxCount = boxCountStr.toIntOrNull() ?: 1
                            val openBoxPairs = openBoxPairsStr.toIntOrNull() ?: 0
                            onAdd(
                                brand,
                                diopter,
                                pairsPerBox.coerceAtLeast(1),
                                boxCount.coerceAtLeast(0),
                                openBoxPairs.coerceAtLeast(0)
                            )
                        },
                        modifier = Modifier
                            .weight(1.5f)
                            .testTag("confirm_add_stock_button"),
                        enabled = pairsPerBoxStr.isNotBlank() && boxCountStr.isNotBlank()
                    ) {
                        Text("Добавить")
                    }
                }
            }
        }
    }
}

@Composable
fun OphthalmologistVisitDialog(
    currentVisit: OphthalmologistVisit?,
    onDismiss: () -> Unit,
    onSave: (Long, Long?) -> Unit
) {
    val context = LocalContext.current
    val calendar = Calendar.getInstance()

    // Internal calendar States (default to current date or stored values)
    var lastVisitTime by remember {
        mutableStateOf(currentVisit?.lastVisitDate ?: System.currentTimeMillis())
    }
    var scheduleNext by remember {
        mutableStateOf(currentVisit?.nextAppointmentDate != null)
    }
    var nextAppointmentTime by remember {
        mutableStateOf(
            currentVisit?.nextAppointmentDate ?: (System.currentTimeMillis() + 365L * 24 * 60 * 60 * 1000) // 1 year ahead default
        )
    }

    // Helper DatePickerDialog triggers
    val showLastDatePicker = {
        val lastCal = Calendar.getInstance().apply { timeInMillis = lastVisitTime }
        DatePickerDialog(
            context,
            { _, year, month, dayOfMonth ->
                val newCal = Calendar.getInstance().apply {
                    set(Calendar.YEAR, year)
                    set(Calendar.MONTH, month)
                    set(Calendar.DAY_OF_MONTH, dayOfMonth)
                    set(Calendar.HOUR_OF_DAY, 0)
                    set(Calendar.MINUTE, 0)
                    set(Calendar.SECOND, 0)
                    set(Calendar.MILLISECOND, 0)
                }
                lastVisitTime = newCal.timeInMillis
            },
            lastCal.get(Calendar.YEAR),
            lastCal.get(Calendar.MONTH),
            lastCal.get(Calendar.DAY_OF_MONTH)
        ).show()
    }

    val showNextDatePicker = {
        val nextCal = Calendar.getInstance().apply { timeInMillis = nextAppointmentTime }
        DatePickerDialog(
            context,
            { _, year, month, dayOfMonth ->
                val newCal = Calendar.getInstance().apply {
                    set(Calendar.YEAR, year)
                    set(Calendar.MONTH, month)
                    set(Calendar.DAY_OF_MONTH, dayOfMonth)
                    set(Calendar.HOUR_OF_DAY, 0)
                    set(Calendar.MINUTE, 0)
                    set(Calendar.SECOND, 0)
                    set(Calendar.MILLISECOND, 0)
                }
                nextAppointmentTime = newCal.timeInMillis
            },
            nextCal.get(Calendar.YEAR),
            nextCal.get(Calendar.MONTH),
            nextCal.get(Calendar.DAY_OF_MONTH)
        ).show()
    }

    Dialog(onDismissRequest = onDismiss) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
                .testTag("visit_dialog"),
            shape = RoundedCornerShape(24.dp)
        ) {
            Column(
                modifier = Modifier
                    .padding(24.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Text(
                    text = "Записать визит к врачу",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )

                Text(
                    text = "Дата вашего последнего осмотра у офтальмолога:",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                )

                // Last Visit Date Selector Button
                OutlinedButton(
                    onClick = showLastDatePicker,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(52.dp)
                        .testTag("last_visit_date_picker"),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Icon(imageVector = Icons.Default.DateRange, contentDescription = "Calendar")
                    Spacer(modifier = Modifier.width(10.dp))
                    Text(
                        text = DateUtils.formatDateToDisplay(lastVisitTime),
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold
                    )
                }

                Divider()

                // Toggle schedule next appointment
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Запланировать следующий визит",
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = "Указать точную дату будущей записи к врачу",
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                        )
                    }
                    Switch(
                        checked = scheduleNext,
                        onCheckedChange = { scheduleNext = it },
                        modifier = Modifier.testTag("schedule_next_switch")
                    )
                }

                // Next Appointment Date Selector (Conditional)
                if (scheduleNext) {
                    Text(
                        text = "Дата запланированного приема:",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                    )

                    OutlinedButton(
                        onClick = showNextDatePicker,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(52.dp)
                            .testTag("next_appointment_date_picker"),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Icon(imageVector = Icons.Default.DateRange, contentDescription = "Calendar")
                        Spacer(modifier = Modifier.width(10.dp))
                        Text(
                            text = DateUtils.formatDateToDisplay(nextAppointmentTime),
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedButton(
                        onClick = onDismiss,
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("Отмена")
                    }

                    Button(
                        onClick = {
                            val nextApptVal = if (scheduleNext) nextAppointmentTime else null
                            onSave(lastVisitTime, nextApptVal)
                        },
                        modifier = Modifier
                            .weight(1.5f)
                            .testTag("confirm_save_visit_button")
                    ) {
                        Text("Сохранить")
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditDurationDialog(
    currentDuration: Int,
    onDismiss: () -> Unit,
    onConfirm: (Int) -> Unit
) {
    var selectedDays by remember { mutableIntStateOf(currentDuration) }

    Dialog(onDismissRequest = onDismiss) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
                .testTag("edit_duration_dialog"),
            shape = RoundedCornerShape(24.dp)
        ) {
            Column(
                modifier = Modifier
                    .padding(24.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Text(
                    text = "Изменить срок ношения",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )

                Text(
                    text = "Выберите количество дней носки линз:",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                )

                // Common Presets (Chips)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    listOf(1, 14, 30).forEach { days ->
                        FilterChip(
                            selected = selectedDays == days,
                            onClick = { selectedDays = days },
                            label = {
                                Text(
                                    text = when (days) {
                                        1 -> "1 день"
                                        14 -> "14 дней"
                                        else -> "30 дней"
                                    }
                                )
                            },
                            modifier = Modifier.testTag("edit_duration_chip_$days")
                        )
                    }
                }

                // Custom Day Input
                OutlinedTextField(
                    value = if (selectedDays > 0) selectedDays.toString() else "",
                    onValueChange = {
                        val value = it.toIntOrNull() ?: 0
                        selectedDays = value
                    },
                    label = { Text("Свой срок ношения (дней)") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("edit_duration_custom_input"),
                    singleLine = true
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    TextButton(onClick = onDismiss) {
                        Text("Отмена", color = MaterialTheme.colorScheme.primary)
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Button(
                        onClick = {
                            if (selectedDays > 0) {
                                onConfirm(selectedDays)
                            }
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                    ) {
                        Text("Сохранить", color = Color.White)
                    }
                }
            }
        }
    }
}

@Composable
fun UserInstructionsDialog(
    onDismiss: () -> Unit
) {
    Dialog(onDismissRequest = onDismiss) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 16.dp, horizontal = 8.dp)
                .testTag("instructions_dialog"),
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surface
            )
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp)
            ) {
                // Header with "X" (Close button)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Инструкция к приложению",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = com.example.ui.theme.BrandBlue
                    )
                    IconButton(
                        onClick = onDismiss,
                        modifier = Modifier
                            .size(36.dp)
                            .testTag("close_instructions_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Закрыть инструкцию",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(24.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Scrollable content
                Column(
                    modifier = Modifier
                        .weight(1f, fill = false)
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Text(
                        text = "Добро пожаловать в Мои Линзы! Это удобный помощник для отслеживания срока замены контактных линз.",
                        fontSize = 13.sp,
                        lineHeight = 18.sp,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.8f)
                    )

                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

                    // 1. Active Tracker
                    InstructionItem(
                        title = "1. Контроль ношения линз",
                        description = "• Срок службы: показывает, сколько дней осталось до замены активной пары.\n" +
                                "• Изменение параметров: нажмите на иконку карандаша возле даты начала или срока ношения, чтобы их отредактировать.\n" +
                                "• Пропуск дня: кнопка «День пропущен» позволяет не засчитывать сегодняшний день в срок носки, если вы не надевали линзы.\n" +
                                "• Замена пары: кнопка «Смена пары» списывает новую пару из запаса и сбрасывает таймер."
                    )

                    // 2. Stock Inventory
                    InstructionItem(
                        title = "2. Запас линз",
                        description = "• Учет остатков: отображает общее количество пар в запасах и количество пар в уже открытых упаковках.\n" +
                                "• Добавление запасов: кнопка «Добавить линзы в запас» позволяет указать бренд, диоптрии, количество упаковок и пар в коробке.\n" +
                                "• Расход: при начале ношения новой пары линзы автоматически списываются из ваших запасов."
                    )

                    // 3. Ophthalmologist Card
                    InstructionItem(
                        title = "3. Визит к офтальмологу",
                        description = "• Планирование: запишите дату следующего приема и важные заметки.\n" +
                                "• Напоминания: приложение вовремя предупредит вас об осмотре."
                    )

                    // 4. Notifications Settings
                    InstructionItem(
                        title = "4. Настройки уведомлений",
                        description = "• Напоминания: установите комфортное время для ежедневных вечерних уведомлений, чтобы не забыть снять линзы и отметить статус дня."
                    )
                }

                Spacer(modifier = Modifier.height(20.dp))

                Button(
                    onClick = onDismiss,
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = com.example.ui.theme.BrandBlue
                    ),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text(text = "Понятно", color = Color.White)
                }
            }
        }
    }
}

@Composable
fun InstructionItem(title: String, description: String) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(
            text = title,
            fontSize = 14.sp,
            fontWeight = FontWeight.Bold,
            color = com.example.ui.theme.TextDark
        )
        Text(
            text = description,
            fontSize = 12.sp,
            lineHeight = 16.sp,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
        )
    }
}
