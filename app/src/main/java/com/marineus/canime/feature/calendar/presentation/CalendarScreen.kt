package com.marineus.canime.feature.calendar.presentation

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.IconButton
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.marineus.canime.feature.calendar.model.CalendarDay
import com.marineus.canime.feature.calendar.model.CalendarMonthFactory
import com.marineus.canime.feature.calendar.model.BirthdayCharacter
import com.marineus.canime.feature.calendar.data.BirthdayRepository
import com.marineus.canime.ui.theme.CAnimeTheme
import com.marineus.canime.ui.theme.OnPastelBlue
import com.marineus.canime.ui.theme.PastelBlue
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.util.Locale
import coil3.compose.AsyncImage
import kotlinx.coroutines.launch

private val TurkishLocale = Locale.forLanguageTag("tr-TR")
private val MonthFormatter = DateTimeFormatter.ofPattern("MMMM yyyy", TurkishLocale)
private val FullDateFormatter = DateTimeFormatter.ofPattern("d MMMM yyyy, EEEE", TurkishLocale)
private val WeekDayLabels = listOf("Pzt", "Sal", "Çar", "Per", "Cum", "Cmt", "Paz")

@Composable
fun CalendarRoute(
    isDarkTheme: Boolean,
    onThemeToggle: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val repository = remember { BirthdayRepository(context) }
    val scope = rememberCoroutineScope()
    val today = remember { LocalDate.now() }
    var displayedMonthValue by rememberSaveable { mutableLongStateOf(YearMonth.from(today).toEpochMonth()) }
    var selectedDateValue by rememberSaveable { mutableLongStateOf(today.toEpochDay()) }
    var username by rememberSaveable { mutableStateOf(repository.lastUsername) }
    var activeUsername by rememberSaveable { mutableStateOf(repository.lastUsername) }
    var birthdaysByDay by remember { mutableStateOf<Map<Int, List<BirthdayCharacter>>>(emptyMap()) }
    var isSyncing by remember { mutableStateOf(false) }
    var syncMessage by remember { mutableStateOf<String?>(null) }
    var syncError by remember { mutableStateOf<String?>(null) }
    var cacheRevision by remember { mutableLongStateOf(0L) }

    val displayedMonth = YearMonth.of(
        Math.floorDiv(displayedMonthValue, 12L).toInt(),
        Math.floorMod(displayedMonthValue, 12L).toInt() + 1,
    )
    val selectedDate = LocalDate.ofEpochDay(selectedDateValue)

    LaunchedEffect(activeUsername, displayedMonth, cacheRevision) {
        birthdaysByDay = repository
            .charactersForMonth(activeUsername, displayedMonth.monthValue)
            .groupBy { it.birthDay }
    }

    val uiState = CalendarUiState(
        month = remember(displayedMonth) { CalendarMonthFactory.create(displayedMonth) },
        selectedDate = selectedDate,
        today = today,
        birthdaysByDay = birthdaysByDay,
        username = username,
        isSyncing = isSyncing,
        syncMessage = syncMessage,
        syncError = syncError,
    )

    CalendarScreen(
        state = uiState,
        onPreviousMonth = { displayedMonthValue-- },
        onNextMonth = { displayedMonthValue++ },
        onTodayClick = {
            displayedMonthValue = YearMonth.from(today).toEpochMonth()
            selectedDateValue = today.toEpochDay()
        },
        onDateSelected = { date ->
            selectedDateValue = date.toEpochDay()
            displayedMonthValue = YearMonth.from(date).toEpochMonth()
        },
        isDarkTheme = isDarkTheme,
        onThemeToggle = onThemeToggle,
        onUsernameChange = {
            username = it
            syncError = null
        },
        onSyncClick = {
            if (username.isBlank()) {
                syncError = "AniList kullanıcı adını girin."
            } else if (!isSyncing) {
                scope.launch {
                    isSyncing = true
                    syncError = null
                    syncMessage = "Anime ve manga listeniz AniList'ten alınıyor…"
                    runCatching { repository.sync(username) }
                        .onSuccess { characterCount ->
                            activeUsername = username.trim()
                            cacheRevision++
                            syncMessage = "$characterCount karakter yerel olarak kaydedildi."
                        }
                        .onFailure { error ->
                            syncError = error.message ?: "AniList verisi alınamadı."
                        }
                    isSyncing = false
                }
            }
        },
        modifier = modifier,
    )
}

@Composable
fun CalendarScreen(
    state: CalendarUiState,
    onPreviousMonth: () -> Unit,
    onNextMonth: () -> Unit,
    onTodayClick: () -> Unit,
    onDateSelected: (LocalDate) -> Unit,
    isDarkTheme: Boolean = false,
    onThemeToggle: () -> Unit = {},
    onUsernameChange: (String) -> Unit = {},
    onSyncClick: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    Surface(modifier = modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        BoxWithConstraints(Modifier.fillMaxSize()) {
            val isTablet = minOf(maxWidth, maxHeight) >= 600.dp
            if (isTablet && maxWidth > maxHeight) {
                LandscapeTabletCalendar(
                    state = state,
                    maxScreenWidth = maxWidth,
                    maxScreenHeight = maxHeight,
                    onPreviousMonth = onPreviousMonth,
                    onNextMonth = onNextMonth,
                    onTodayClick = onTodayClick,
                    onDateSelected = onDateSelected,
                    isDarkTheme = isDarkTheme,
                    onThemeToggle = onThemeToggle,
                    onUsernameChange = onUsernameChange,
                    onSyncClick = onSyncClick,
                )
            } else {
                StackedCalendar(
                    state = state,
                    isTablet = isTablet,
                    onPreviousMonth = onPreviousMonth,
                    onNextMonth = onNextMonth,
                    onTodayClick = onTodayClick,
                    onDateSelected = onDateSelected,
                    isDarkTheme = isDarkTheme,
                    onThemeToggle = onThemeToggle,
                    onUsernameChange = onUsernameChange,
                    onSyncClick = onSyncClick,
                )
            }
        }
    }
}

@Composable
private fun LandscapeTabletCalendar(
    state: CalendarUiState,
    maxScreenWidth: Dp,
    maxScreenHeight: Dp,
    onPreviousMonth: () -> Unit,
    onNextMonth: () -> Unit,
    onTodayClick: () -> Unit,
    onDateSelected: (LocalDate) -> Unit,
    isDarkTheme: Boolean,
    onThemeToggle: () -> Unit,
    onUsernameChange: (String) -> Unit,
    onSyncClick: () -> Unit,
) {
    // Card header + weekday bar use roughly 170 dp. The remaining height defines
    // the largest possible 7x6 square grid that fits without scrolling.
    val calendarAreaHeight = (maxScreenHeight - 126.dp).coerceAtLeast(480.dp)
    val widthFromAvailableHeight = (calendarAreaHeight - 170.dp) * 7f / 6f
    val calendarWidth = minOf(widthFromAvailableHeight, maxScreenWidth * 0.68f)

    Column(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .padding(horizontal = 12.dp, vertical = 16.dp),
    ) {
        CalendarTitle(isDarkTheme = isDarkTheme, onThemeToggle = onThemeToggle)
        Spacer(Modifier.height(14.dp))
        Row(
            modifier = Modifier.fillMaxSize(),
            horizontalArrangement = Arrangement.spacedBy(20.dp),
        ) {
            CalendarCard(
                state = state,
                onPreviousMonth = onPreviousMonth,
                onNextMonth = onNextMonth,
                onTodayClick = onTodayClick,
                onDateSelected = onDateSelected,
                modifier = Modifier.width(calendarWidth),
            )
            SelectedDayPanel(
                state = state,
                onUsernameChange = onUsernameChange,
                onSyncClick = onSyncClick,
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight(),
            )
        }
    }
}

@Composable
private fun StackedCalendar(
    state: CalendarUiState,
    isTablet: Boolean,
    onPreviousMonth: () -> Unit,
    onNextMonth: () -> Unit,
    onTodayClick: () -> Unit,
    onDateSelected: (LocalDate) -> Unit,
    isDarkTheme: Boolean,
    onThemeToggle: () -> Unit,
    onUsernameChange: (String) -> Unit,
    onSyncClick: () -> Unit,
) {
    val contentModifier = if (isTablet) {
        Modifier.fillMaxSize()
    } else {
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
    }

    Column(
        modifier = contentModifier
            .statusBarsPadding()
            .padding(horizontal = if (isTablet) 12.dp else 8.dp, vertical = 12.dp),
    ) {
        CalendarTitle(isDarkTheme = isDarkTheme, onThemeToggle = onThemeToggle)
        Spacer(Modifier.height(if (isTablet) 16.dp else 20.dp))
        CalendarCard(
            state = state,
            onPreviousMonth = onPreviousMonth,
            onNextMonth = onNextMonth,
            onTodayClick = onTodayClick,
            onDateSelected = onDateSelected,
        )
        Spacer(Modifier.height(16.dp))
        SelectedDayPanel(
            state = state,
            onUsernameChange = onUsernameChange,
            onSyncClick = onSyncClick,
            modifier = if (isTablet) Modifier.weight(1f) else Modifier.heightIn(min = 280.dp, max = 480.dp),
        )
    }
}

@Composable
private fun CalendarTitle(isDarkTheme: Boolean, onThemeToggle: () -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(
            text = "CAnime Takvim",
            modifier = Modifier.weight(1f),
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onBackground,
        )
        TextButton(
            onClick = onThemeToggle,
            modifier = Modifier.semantics {
                contentDescription = if (isDarkTheme) "Açık temaya geç" else "Koyu temaya geç"
            },
        ) {
            Text(
                text = if (isDarkTheme) "☀ Açık tema" else "☾ Koyu tema",
                color = PastelBlue,
            )
        }
    }
    Text(
        text = "Anime dünyasındaki özel günler burada.",
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

@Composable
private fun CalendarCard(
    state: CalendarUiState,
    onPreviousMonth: () -> Unit,
    onNextMonth: () -> Unit,
    onTodayClick: () -> Unit,
    onDateSelected: (LocalDate) -> Unit,
    modifier: Modifier = Modifier,
) {
    Card(
        modifier = modifier,
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.background),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
    ) {
        Column(Modifier.padding(vertical = 14.dp)) {
            MonthHeader(
                month = state.month.yearMonth,
                onPreviousMonth = onPreviousMonth,
                onNextMonth = onNextMonth,
                onTodayClick = onTodayClick,
                modifier = Modifier.padding(horizontal = 14.dp),
            )
            Spacer(Modifier.height(12.dp))
            WeekHeader()
            Spacer(Modifier.height(8.dp))
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(0.dp),
            ) {
                state.month.weeks.forEach { week ->
                    Row(Modifier.fillMaxWidth()) {
                        week.forEach { day ->
                            CalendarDayCell(
                                day = day,
                                isSelected = day.date == state.selectedDate,
                                isToday = day.date == state.today,
                                onClick = { onDateSelected(day.date) },
                                modifier = Modifier.weight(1f),
                            ) {
                                if (day.isInDisplayedMonth) {
                                    BirthdayPortraits(
                                        characters = state.birthdaysByDay[day.date.dayOfMonth].orEmpty(),
                                        modifier = Modifier
                                            .align(Alignment.Center)
                                            .padding(top = 14.dp),
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

@Composable
private fun BirthdayPortraits(
    characters: List<BirthdayCharacter>,
    modifier: Modifier = Modifier,
) {
    if (characters.isEmpty()) return
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy((-5).dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        characters.take(3).forEach { character ->
            AsyncImage(
                model = character.imageUrl,
                contentDescription = character.name,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .size(24.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.surfaceVariant),
            )
        }
        if (characters.size > 3) {
            Text(
                text = "+${characters.size - 3}",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurface,
            )
        }
    }
}

@Composable
private fun MonthHeader(
    month: YearMonth,
    onPreviousMonth: () -> Unit,
    onNextMonth: () -> Unit,
    onTodayClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(modifier = modifier, verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(
                text = month.format(MonthFormatter).replaceFirstChar { it.titlecase(TurkishLocale) },
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.SemiBold,
            )
            TextButton(onClick = onTodayClick, contentPadding = ButtonDefaults.TextButtonContentPadding) {
                Text("Bugüne dön", color = PastelBlue)
            }
        }
        MonthNavigationButton(label = "‹", description = "Önceki ay", onClick = onPreviousMonth)
        Spacer(Modifier.width(8.dp))
        MonthNavigationButton(label = "›", description = "Sonraki ay", onClick = onNextMonth)
    }
}

@Composable
private fun MonthNavigationButton(label: String, description: String, onClick: () -> Unit) {
    IconButton(
        onClick = onClick,
        modifier = Modifier
            .size(44.dp)
            .semantics { contentDescription = description },
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.headlineMedium,
            color = PastelBlue,
        )
    }
}

@Composable
private fun WeekHeader() {
    Row(
        Modifier
            .fillMaxWidth(),
    ) {
        WeekDayLabels.forEach { label ->
            Box(
                modifier = Modifier
                    .weight(1f)
                    .height(32.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = label,
                    textAlign = TextAlign.Center,
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
fun CalendarDayCell(
    day: CalendarDay,
    isSelected: Boolean,
    isToday: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable BoxScope.() -> Unit = {},
) {
    val background = when {
        isSelected -> PastelBlue
        else -> Color.Transparent
    }
    val textColor = when {
        !day.isInDisplayedMonth -> MaterialTheme.colorScheme.onSurface.copy(alpha = 0.35f)
        else -> MaterialTheme.colorScheme.onSurface
    }
    Box(
        modifier = modifier
            .aspectRatio(1f)
            .clip(RoundedCornerShape(16.dp))
            .background(background)
            .clickable(role = Role.Button, onClick = onClick)
            .semantics {
                contentDescription = day.date.format(FullDateFormatter) +
                    if (isSelected) ", seçili" else ""
            }
            .padding(8.dp),
    ) {
        Column(
            modifier = Modifier.align(Alignment.TopStart),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                text = day.date.dayOfMonth.toString(),
                style = MaterialTheme.typography.labelMedium,
                fontWeight = if (isSelected || isToday) FontWeight.Bold else FontWeight.Normal,
                color = if (isSelected) OnPastelBlue else textColor,
            )
            if (isToday) {
                Spacer(Modifier.height(2.dp))
                Box(
                    Modifier
                        .size(4.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.primary),
                )
            }
        }
        content()
    }
}

@Composable
private fun SelectedDayPanel(
    state: CalendarUiState,
    onUsernameChange: (String) -> Unit,
    onSyncClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val selectedCharacters = state.birthdaysByDay[state.selectedDate.dayOfMonth].orEmpty()
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                OutlinedTextField(
                    value = state.username,
                    onValueChange = onUsernameChange,
                    modifier = Modifier.weight(1f),
                    label = { Text("AniList kullanıcı adı") },
                    singleLine = true,
                    enabled = !state.isSyncing,
                )
                TextButton(onClick = onSyncClick, enabled = !state.isSyncing) {
                    if (state.isSyncing) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(22.dp),
                            strokeWidth = 2.dp,
                            color = PastelBlue,
                        )
                    } else {
                        Text("Senkronize et", color = PastelBlue)
                    }
                }
            }
            state.syncError?.let {
                Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
            }
            state.syncMessage?.let {
                Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
            }
            Text(
                text = state.selectedDate.format(FullDateFormatter).replaceFirstChar {
                    it.titlecase(TurkishLocale)
                },
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
            )
            if (selectedCharacters.isEmpty()) {
                Text(
                    text = if (state.username.isBlank()) {
                        "Doğum günlerini görmek için AniList kullanıcı adınızı girip senkronize edin."
                    } else {
                        "Bu tarihte listenizdeki yapımlardan doğan bir karakter bulunamadı."
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else {
                LazyColumn(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    items(selectedCharacters, key = { it.id }) { character ->
                        BirthdayCharacterRow(character)
                    }
                }
            }
        }
    }
}

@Composable
private fun BirthdayCharacterRow(character: BirthdayCharacter) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        AsyncImage(
            model = character.imageUrl,
            contentDescription = character.name,
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .size(56.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.surface),
        )
        Column(Modifier.weight(1f)) {
            Text(
                text = character.name,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                text = character.mediaTitles.take(3).joinToString(" • ").ifBlank { "Anime / Manga" },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 2,
            )
        }
    }
}

private fun YearMonth.toEpochMonth(): Long = year.toLong() * 12L + monthValue - 1L

@Preview(showBackground = true, locale = "tr-rTR")
@Composable
private fun CalendarScreenPreview() {
    val today = LocalDate.of(2026, 10, 26)
    CAnimeTheme(dynamicColor = false) {
        CalendarScreen(
            state = CalendarUiState(
                month = CalendarMonthFactory.create(YearMonth.from(today)),
                selectedDate = today,
                today = today,
            ),
            onPreviousMonth = {},
            onNextMonth = {},
            onTodayClick = {},
            onDateSelected = {},
        )
    }
}
