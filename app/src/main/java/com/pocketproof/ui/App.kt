package com.pocketproof.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.pocketproof.content.Course
import com.pocketproof.content.Level
import com.pocketproof.content.World
import com.pocketproof.engine.FactKind
import com.pocketproof.engine.render
import kotlin.math.PI
import kotlin.math.sin
import kotlin.random.Random

private enum class Tab {
    Journey, Library, Progress;

    val label get() = when (this) {
        Journey -> tr("Journey", "Reise")
        Library -> tr("Library", "Bibliothek")
        Progress -> tr("Progress", "Fortschritt")
    }
}

@Composable
fun App() {
    val context = LocalContext.current
    val progress = remember { Progress(context.applicationContext) }
    var tab by rememberSaveable { mutableStateOf(Tab.Journey) }
    var openLevelId by rememberSaveable { mutableStateOf<String?>(null) }

    AnimatedContent(targetState = openLevelId, label = "screen") { id ->
        if (id != null) {
            LevelScreen(
                level = Course.level(id),
                progress = progress,
                onBack = { openLevelId = null },
                onNext = { next -> openLevelId = next?.id },
            )
        } else {
            Scaffold(
                bottomBar = {
                    NavigationBar {
                        Tab.entries.forEach { t ->
                            NavigationBarItem(
                                selected = tab == t,
                                onClick = { tab = t },
                                icon = {
                                    Icon(
                                        when (t) {
                                            Tab.Journey -> Icons.Rounded.Map
                                            Tab.Library -> Icons.Rounded.AutoStories
                                            Tab.Progress -> Icons.Rounded.EmojiEvents
                                        },
                                        null,
                                    )
                                },
                                label = { Text(t.label) },
                            )
                        }
                    }
                },
            ) { padding ->
                Box(Modifier.padding(padding)) {
                    when (tab) {
                        Tab.Journey -> Journey(progress) { openLevelId = it.id }
                        Tab.Library -> Library(progress)
                        Tab.Progress -> ProgressTab(progress)
                    }
                }
            }
        }
    }
}

@Composable
private fun Journey(progress: Progress, onOpen: (Level) -> Unit) {
    val frontier = progress.frontier()
    val frontierWorld = frontier?.worldId
    var expanded by rememberSaveable { mutableStateOf(frontierWorld ?: Course.worlds.first().id) }
    val list = rememberLazyListState()

    LazyColumn(
        state = list,
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        modifier = Modifier.fillMaxSize().statusBarsPadding(),
    ) {
        item { Header(progress, frontier, onOpen) }
        items(Course.worlds, key = { it.id }) { w ->
            WorldCard(
                world = w,
                progress = progress,
                expanded = expanded == w.id,
                onToggle = { expanded = if (expanded == w.id) "" else w.id },
                onOpen = onOpen,
            )
        }
    }
}

@Composable
private fun Header(progress: Progress, frontier: Level?, onOpen: (Level) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                "PocketProof",
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.weight(1f),
            )
            StatPill(Icons.Rounded.LocalFireDepartment, "${progress.streak}")
            Spacer(Modifier.width(8.dp))
            StatPill(Icons.Rounded.Star, "${progress.totalStars}")
        }
        LinearProgressIndicator(
            progress = { progress.solvedCount.toFloat() / Course.levels.size },
            modifier = Modifier.fillMaxWidth().height(8.dp),
            drawStopIndicator = {},
        )
        Text(
            tr(
                "${progress.solvedCount} of ${Course.levels.size} proofs complete · ${progress.xp} XP",
                "${progress.solvedCount} von ${Course.levels.size} Beweisen fertig · ${progress.xp} XP",
            ),
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        if (frontier != null) {
            Button(onClick = { onOpen(frontier) }, modifier = Modifier.fillMaxWidth().height(52.dp)) {
                Icon(Icons.Rounded.PlayArrow, null)
                Spacer(Modifier.width(8.dp))
                Prose(
                    if (progress.solvedCount == 0) tr("Start your first proof", "Starte deinen ersten Beweis") else tr("Continue: ", "Weiter: ") + frontier.localTitle,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

@Composable
private fun StatPill(icon: androidx.compose.ui.graphics.vector.ImageVector, text: String) {
    Surface(shape = CircleShape, color = MaterialTheme.colorScheme.secondaryContainer) {
        Row(Modifier.padding(horizontal = 10.dp, vertical = 5.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, null, Modifier.size(18.dp), tint = MaterialTheme.colorScheme.onSecondaryContainer)
            Spacer(Modifier.width(4.dp))
            Text(text, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSecondaryContainer)
        }
    }
}

@Composable
private fun WorldCard(world: World, progress: Progress, expanded: Boolean, onToggle: () -> Unit, onOpen: (Level) -> Unit) {
    val colors = worldColors(world.colorIndex)
    val unlocked = progress.worldUnlocked(world)
    val solved = progress.worldSolved(world)
    Surface(
        shape = RoundedCornerShape(24.dp),
        color = if (unlocked) colors.soft else MaterialTheme.colorScheme.surfaceContainerHigh,
    ) {
        Column {
            Row(
                Modifier.fillMaxWidth().clickable(onClick = onToggle).padding(16.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f)) {
                    Text(
                        world.localTitle,
                        style = MaterialTheme.typography.titleLarge,
                        color = if (unlocked) colors.onSoft else MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text(
                        "${world.localTopic} · ${world.localRef}",
                        style = MaterialTheme.typography.bodySmall,
                        color = if (unlocked) colors.onSoft.copy(alpha = 0.8f) else MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                if (!unlocked) {
                    Icon(Icons.Rounded.Lock, tr("Locked", "Gesperrt"), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                } else {
                    Text("$solved/${world.levels.size}", style = MaterialTheme.typography.titleMedium, color = colors.onSoft)
                }
            }
            if (expanded) {
                Column(Modifier.fillMaxWidth().padding(bottom = 16.dp)) {
                    world.levels.forEachIndexed { i, l -> LevelNode(l, i, colors, progress, onOpen) }
                }
            }
        }
    }
}

@Composable
private fun LevelNode(level: Level, index: Int, colors: WorldColors, progress: Progress, onOpen: (Level) -> Unit) {
    val unlocked = progress.isUnlocked(level)
    val stars = progress.stars[level.id] ?: 0
    val current = progress.frontier()?.id == level.id
    // The path winds left and right across the card.
    val swing = sin(index * PI / 2.6).toFloat()
    val size = if (level.boss) 76.dp else 60.dp
    val pulse = rememberInfiniteTransition(label = "pulse")
    val scale by pulse.animateFloat(
        1f, if (current) 1.09f else 1f,
        infiniteRepeatable(tween(800), RepeatMode.Reverse),
        label = "scale",
    )
    Box(Modifier.fillMaxWidth().padding(vertical = 3.dp), contentAlignment = Alignment.Center) {
        Row(
            Modifier.offset(x = (swing * 62).dp).clickable(enabled = unlocked) { onOpen(level) }.padding(4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Surface(
                shape = CircleShape,
                color = when {
                    stars > 0 -> colors.strong
                    unlocked -> MaterialTheme.colorScheme.surface
                    else -> MaterialTheme.colorScheme.surfaceVariant
                },
                shadowElevation = if (unlocked) 3.dp else 0.dp,
                modifier = Modifier.size(size).scale(scale),
            ) {
                Box(contentAlignment = Alignment.Center) {
                    when {
                        !unlocked -> Icon(Icons.Rounded.Lock, null, tint = MaterialTheme.colorScheme.outline)
                        level.boss -> Icon(
                            Icons.Rounded.WorkspacePremium,
                            null,
                            Modifier.size(38.dp),
                            tint = if (stars > 0) colors.onStrong else colors.strong,
                        )
                        else -> Text(
                            "${index + 1}",
                            style = MaterialTheme.typography.titleLarge,
                            color = if (stars > 0) colors.onStrong else colors.strong,
                        )
                    }
                }
            }
            Spacer(Modifier.width(10.dp))
            Column(Modifier.widthIn(max = 130.dp)) {
                Prose(
                    level.localTitle,
                    style = MaterialTheme.typography.labelMedium,
                    color = if (unlocked) colors.onSoft else MaterialTheme.colorScheme.outline,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                if (stars > 0) StarsRow(stars, 14.dp, colors.strong)
            }
        }
    }
}

@Composable
fun StarsRow(count: Int, size: androidx.compose.ui.unit.Dp, tint: Color) {
    Row {
        repeat(3) { i ->
            Icon(
                if (i < count) Icons.Rounded.Star else Icons.Rounded.StarBorder,
                null,
                Modifier.size(size),
                tint = tint,
            )
        }
    }
}

@Composable
private fun Library(progress: Progress) {
    var query by rememberSaveable { mutableStateOf("") }
    val frontierIndex = progress.frontier()?.let { Course.indexOf(it) } ?: Course.levels.lastIndex
    val cutoff = if (progress.openAll) Course.levels.lastIndex else frontierIndex
    val available = remember(cutoff) { Course.toolsFor(Course.levels[cutoff]).reversed().associateBy { it.id } }
    LazyColumn(
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier.fillMaxSize().statusBarsPadding(),
    ) {
        item {
            Text(tr("Library", "Bibliothek"), style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.SemiBold)
            Text(
                tr("Everything you have proved or been given so far.", "Alles, was du bisher bewiesen oder bekommen hast."),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(8.dp))
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                leadingIcon = { Icon(Icons.Rounded.Search, null) },
                placeholder = { Text(tr("Search by name, German term or section", "Nach Name, Begriff oder Abschnitt suchen")) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
        }
        Course.worlds.forEach { w ->
            val inWorld = w.levels.flatMap { l ->
                l.grants + listOfNotNull(Course.rewardOf(l))
            }.distinctBy { it.id }.filter { it.id in available }.filter {
                query.isBlank() || it.name.contains(query, true) || it.localName.contains(query, true) || it.german.contains(query, true) || it.ref.contains(query, true)
            }
            if (inWorld.isNotEmpty()) {
                item(key = "h_${w.id}") {
                    Text(
                        w.localTitle,
                        style = MaterialTheme.typography.titleMedium,
                        color = worldColors(w.colorIndex).strong,
                        modifier = Modifier.padding(top = 10.dp),
                    )
                }
                items(inWorld, key = { it.id }) { f -> LibraryCard(f) }
            }
        }
    }
}

@Composable
private fun LibraryCard(f: com.pocketproof.engine.Fact) {
    Surface(shape = RoundedCornerShape(16.dp), color = MaterialTheme.colorScheme.surfaceContainerHigh) {
        Column(Modifier.fillMaxWidth().padding(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Prose(f.localName, style = MaterialTheme.typography.titleSmall)
            MathText(render(f.statement), fontSize = 18.sp)
            Prose(
                factDetails(f),
                style = MaterialTheme.typography.labelSmall,
                color = if (f.kind == FactKind.Theorem) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun ProgressTab(progress: Progress) {
    var confirmReset by remember { mutableStateOf(false) }
    Column(
        Modifier.fillMaxSize().statusBarsPadding().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(tr("Progress", "Fortschritt"), style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.SemiBold)
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            StatCard(tr("Proofs", "Beweise"), "${progress.solvedCount}/${Course.levels.size}", Modifier.weight(1f))
            StatCard(tr("Stars", "Sterne"), "${progress.totalStars}", Modifier.weight(1f))
            StatCard("XP", "${progress.xp}", Modifier.weight(1f))
        }
        Course.worlds.forEach { w ->
            val c = worldColors(w.colorIndex)
            Column {
                Row {
                    Text(w.localTitle, style = MaterialTheme.typography.labelLarge, modifier = Modifier.weight(1f))
                    Text("${progress.worldSolved(w)}/${w.levels.size}", style = MaterialTheme.typography.labelLarge)
                }
                Spacer(Modifier.height(4.dp))
                LinearProgressIndicator(
                    progress = { progress.worldSolved(w).toFloat() / w.levels.size },
                    color = c.strong,
                    trackColor = c.soft,
                    drawStopIndicator = {},
                    modifier = Modifier.fillMaxWidth().height(8.dp),
                )
            }
        }
        HorizontalDivider()
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Rounded.Language, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.width(10.dp))
            Text(tr("Language", "Sprache"), style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
            SingleChoiceSegmentedButtonRow {
                Lang.entries.forEachIndexed { i, l ->
                    SegmentedButton(
                        selected = Language.current == l,
                        onClick = { progress.changeLanguage(l) },
                        shape = SegmentedButtonDefaults.itemShape(i, Lang.entries.size),
                        label = { Text(l.label) },
                    )
                }
            }
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(tr("Open all levels", "Alle Level öffnen"), style = MaterialTheme.typography.titleSmall)
                Text(tr("Jump ahead without finishing earlier proofs.", "Springe vor, ohne frühere Beweise abzuschließen."), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Switch(checked = progress.openAll, onCheckedChange = { progress.changeOpenAll(it) })
        }
        OutlinedButton(onClick = { confirmReset = true }) { Text(tr("Reset progress", "Fortschritt zurücksetzen")) }
    }
    if (confirmReset) {
        AlertDialog(
            onDismissRequest = { confirmReset = false },
            title = { Text(tr("Reset all progress?", "Gesamten Fortschritt zurücksetzen?")) },
            text = { Text(tr("All stars and completed levels will be erased.", "Alle Sterne und abgeschlossenen Level werden gelöscht.")) },
            confirmButton = { TextButton(onClick = { progress.reset(); confirmReset = false }) { Text(tr("Reset", "Zurücksetzen")) } },
            dismissButton = { TextButton(onClick = { confirmReset = false }) { Text(tr("Cancel", "Abbrechen")) } },
        )
    }
}

@Composable
private fun StatCard(label: String, value: String, modifier: Modifier) {
    Surface(shape = RoundedCornerShape(16.dp), color = MaterialTheme.colorScheme.secondaryContainer, modifier = modifier) {
        Column(Modifier.padding(12.dp)) {
            Text(value, style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.onSecondaryContainer)
            Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSecondaryContainer)
        }
    }
}

private data class Particle(val x: Float, val speed: Float, val size: Float, val color: Color, val phase: Float)

@Composable
private fun Confetti(modifier: Modifier) {
    val palette = listOf(
        MaterialTheme.colorScheme.primary, MaterialTheme.colorScheme.tertiary,
        MaterialTheme.colorScheme.secondary, Color(0xFFFFC107),
    )
    val particles = remember {
        List(90) {
            Particle(Random.nextFloat(), 0.5f + Random.nextFloat(), 6f + Random.nextFloat() * 12f, palette[it % palette.size], Random.nextFloat() * 6.28f)
        }
    }
    val t = remember { Animatable(0f) }
    LaunchedEffect(Unit) { t.animateTo(1f, tween(2400, easing = LinearEasing)) }
    Canvas(modifier) {
        particles.forEach { p ->
            val y = (t.value * p.speed * 1.3f - 0.12f) * size.height
            val x = (p.x + 0.04f * sin(t.value * 18f + p.phase)) * size.width
            drawRect(p.color.copy(alpha = 1f - t.value * 0.6f), Offset(x, y), Size(p.size, p.size * 1.6f))
        }
    }
}

@Composable
fun Victory(level: Level, play: Play, onReplay: () -> Unit, onBack: () -> Unit, onNext: () -> Unit) {
    val earned = play.starsEarned()
    val reward = Course.rewardOf(level)
    val compute = Course.unlocksCompute(level)
    val hasNext = Course.indexOf(level) < Course.levels.lastIndex
    Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surface)) {
        Confetti(Modifier.fillMaxSize())
        Column(
            Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().verticalScroll(rememberScrollState()).padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Spacer(Modifier.height(20.dp))
            Text("∎", fontFamily = MathFamily, fontSize = 48.sp, color = MaterialTheme.colorScheme.primary)
            Text(if (level.boss) tr("Boss defeated!", "Endgegner besiegt!") else tr("Proved!", "Bewiesen!"), style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.SemiBold)
            StarsRow(earned, 44.dp, Color(0xFFFFB300))
            Text(
                tr("${play.moves} moves · par ${level.par}", "${play.moves} Züge · Par ${level.par}") +
                    if (play.hintsUsed > 0) tr(" · ${play.hintsUsed} hints", " · ${play.hintsUsed} Tipps") else "",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
            if (reward != null) {
                Surface(shape = RoundedCornerShape(20.dp), color = MaterialTheme.colorScheme.tertiaryContainer, modifier = Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(tr("New tool unlocked", "Neues Werkzeug freigeschaltet"), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onTertiaryContainer)
                        Prose(reward.localName, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onTertiaryContainer)
                        MathText(render(reward.statement), fontSize = 20.sp, color = MaterialTheme.colorScheme.onTertiaryContainer)
                    }
                }
            }
            if (compute) {
                Surface(shape = RoundedCornerShape(20.dp), color = MaterialTheme.colorScheme.primaryContainer, modifier = Modifier.fillMaxWidth()) {
                    Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Rounded.Bolt, null, tint = MaterialTheme.colorScheme.onPrimaryContainer)
                        Spacer(Modifier.width(10.dp))
                        Text(
                            tr("Power unlocked: Compute. It closes any equation that is plain vector-space arithmetic.", "Kraft freigeschaltet: Rechnen. Es schließt jede Gleichung, die reine Vektorraum-Arithmetik ist."),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                        )
                    }
                }
            }
            Surface(shape = RoundedCornerShape(20.dp), color = MaterialTheme.colorScheme.surfaceContainerHigh, modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp)) {
                    Text(tr("Your proof", "Dein Beweis"), style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(bottom = 8.dp))
                    ProofLog(play)
                }
            }
            Button(onClick = onNext, enabled = hasNext, modifier = Modifier.fillMaxWidth().height(52.dp)) {
                Text(if (hasNext) tr("Next proof", "Nächster Beweis") else tr("Course complete", "Kurs abgeschlossen"))
            }
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedButton(onClick = onReplay) { Text(tr("Replay", "Nochmal")) }
                OutlinedButton(onClick = onBack) { Text(tr("Back to map", "Zurück zur Karte")) }
            }
        }
    }
    BackHandler(onBack = onBack)
}
