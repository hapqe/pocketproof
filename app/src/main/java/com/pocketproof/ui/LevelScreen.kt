package com.pocketproof.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.awaitTouchSlopOrCancellation
import androidx.compose.foundation.gestures.awaitVerticalTouchSlopOrCancellation
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.drag
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.pocketproof.content.Course
import com.pocketproof.content.Level
import com.pocketproof.engine.*

private enum class TrayFilter {
    Suggested, Recent, All;

    val label get() = when (this) {
        Suggested -> tr("For this proof", "Für diesen Beweis")
        Recent -> tr("This world", "Diese Welt")
        All -> tr("Everything", "Alles")
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun LevelScreen(level: Level, progress: Progress, onBack: () -> Unit, onNext: (Level?) -> Unit) {
    val play = remember(level.id) { Play(level) }
    val world = remember(level.id) { Course.world(level.worldId) }
    val colors = worldColors(world.colorIndex)
    val haptic = LocalHapticFeedback.current
    var showAllTools by remember(level.id) { mutableStateOf(false) }
    var showIntro by remember(level.id) { mutableStateOf(world.levels.first().id == level.id) }
    var detail by remember { mutableStateOf<Fact?>(null) }
    var showLog by remember { mutableStateOf(false) }

    BackHandler(onBack = onBack)

    var recorded by remember(level.id) { mutableStateOf(false) }
    LaunchedEffect(play.state.solved) {
        if (play.state.solved && !recorded) {
            recorded = true
            progress.record(level, play.starsEarned())
            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
        }
    }

    Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surface)) {
        Column(Modifier.fillMaxSize()) {
            TopAppBar(
                windowInsets = WindowInsets.statusBars,
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, tr("Back", "Zurück")) }
                },
                title = {
                    Column {
                        Prose(level.localTitle, maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.titleMedium)
                        Prose(
                            "${world.localTitle} · ${level.ref.ifBlank { world.localRef }}",
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                },
                actions = {
                    IconButton(onClick = { play.undo() }, enabled = play.canUndo) { Icon(Icons.Rounded.Undo, tr("Undo", "Rückgängig")) }
                    IconButton(onClick = { play.requestHint() }) {
                        BadgedBox(badge = { if (play.hintsUsed > 0) Badge { Text("${play.hintsUsed}") } }) {
                            Icon(Icons.Rounded.Lightbulb, tr("Hint", "Tipp"))
                        }
                    }
                },
            )

            Column(
                Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp, vertical = 4.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                StoryCard(level, play, onShowLog = { showLog = true })
                GivenCard(play, onDrop = { haptic.performHapticFeedback(HapticFeedbackType.LongPress) })
                GoalArea(play, colors.strong)
                LogicRow(play)
                KeepZone(play)
                Spacer(Modifier.height(4.dp))
            }

            if (play.pending != null) {
                PickerPanel(play)
            } else {
                Tray(
                    play = play,
                    world = world.id,
                    onExpand = { showAllTools = true },
                    onDetail = { detail = it },
                    onCommitted = { haptic.performHapticFeedback(HapticFeedbackType.LongPress) },
                )
            }
        }

        DragGhost(play)
        MessageBar(play, Modifier.align(Alignment.BottomCenter).padding(bottom = 196.dp))

        if (showAllTools) {
            AllToolsOverlay(play, onDismiss = { showAllTools = false }, onDetail = { detail = it })
        }

        if (play.state.solved) {
            Victory(
                level = level,
                play = play,
                onReplay = { play.restart(); recorded = false },
                onBack = onBack,
                onNext = {
                    val i = Course.indexOf(level)
                    onNext(Course.levels.getOrNull(i + 1))
                },
            )
        }
    }

    BackHandler(enabled = play.pending != null) { play.cancelPick() }
    detail?.let { FactDialog(it, onDismiss = { detail = null }) }
    if (showIntro) {
        AlertDialog(
            onDismissRequest = { showIntro = false },
            icon = { Icon(Icons.Rounded.Explore, null) },
            title = { Prose(world.localTitle) },
            text = { Prose(world.localIntro) },
            confirmButton = { TextButton(onClick = { showIntro = false }) { Text(tr("Let's go", "Los geht's")) } },
        )
    }
    if (showLog) ProofLogDialog(play, onDismiss = { showLog = false })
}

@Composable
private fun StoryCard(level: Level, play: Play, onShowLog: () -> Unit) {
    var expanded by remember(level.id) { mutableStateOf(true) }
    LaunchedEffect(play.moves) { if (play.moves > 0) expanded = false }
    Surface(
        onClick = { expanded = !expanded },
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
    ) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Prose(
                    level.localStory,
                    style = MaterialTheme.typography.bodyMedium,
                    maxLines = if (expanded) 12 else 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                Icon(
                    if (expanded) Icons.Rounded.ExpandLess else Icons.Rounded.ExpandMore,
                    null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            AnimatedVisibility(expanded) {
                Row(Modifier.padding(top = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                    Prose(
                        "Par ${level.par} " + moveWord(level.par),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    if (level.german.isNotBlank()) {
                        Prose(
                            "  ·  ${level.german}",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            }
            if (play.log.isNotEmpty()) {
                TextButton(onClick = onShowLog, contentPadding = PaddingValues(0.dp), modifier = Modifier.height(32.dp)) {
                    Text(tr("Proof so far: ", "Bisheriger Beweis: ") + "${play.moves} " + moveWord(play.moves), style = MaterialTheme.typography.labelMedium)
                }
            }
        }
    }
}

private fun moveWord(n: Int) = if (n == 1) tr("move", "Zug") else tr("moves", "Züge")

private fun variablesLine(goal: Goal): String {
    val subscripts = "₀₁₂₃₄₅₆₇₈₉"
    fun pretty(name: String) = name.map { if (it.isDigit()) subscripts[it - '0'] else it }.joinToString("")
    val described = mapOf(
        Sort.Vec to "∈ V", Sort.Scalar to "∈ K", Sort.Set to "⊂ V", Sort.Map to ": V → W",
        Sort.Mat to "∈ Kⁿˣⁿ", Sort.Perm to "∈ Sₙ", Sort.Fam to tr("families", "Familien"), Sort.Coef to tr("coefficients", "Koeffizienten"),
        Sort.Index to tr("indices", "Indizes"), Sort.Nat to "∈ ℕ",
    )
    return goal.vars.groupBy { it.sort }.entries.joinToString("   ") { (s, v) ->
        v.joinToString(", ") { pretty(it.name) } + " " + (described[s] ?: "")
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun GivenCard(play: Play, onDrop: () -> Unit) {
    if (play.state.solved) return
    val goal = play.state.current
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(tr("Given", "Gegeben"), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.width(10.dp))
            if (goal.vars.isNotEmpty()) {
                Prose(
                    variablesLine(goal),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        goal.hyps.forEach { h -> HypRow(play, goal, h) }
        if (goal.hyps.isEmpty()) {
            Text(tr("Nothing yet", "Noch nichts"), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.outline)
        }
    }
}

/** Sits below the goal, so that appearing mid-drag does not move anything the player is aiming at. */
@Composable
private fun KeepZone(play: Play) {
    val available = play.dragging != null && !play.state.solved && play.activeActions().any { it is Action.Have }
    val hovering = play.hover is Action.Have
    if (available) {
        Box(
            Modifier
                .fillMaxWidth()
                .onGloballyPositioned { play.haveZone = it.boundsInRoot() }
                .border(
                    BorderStroke(2.dp, if (hovering) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.outline),
                    RoundedCornerShape(12.dp),
                )
                .background(
                    if (hovering) MaterialTheme.colorScheme.tertiaryContainer else Color.Transparent,
                    RoundedCornerShape(12.dp),
                )
                .padding(vertical = 18.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                tr("Drop here to add it to Given", "Hier ablegen, um es zu Gegeben hinzuzufügen"),
                style = MaterialTheme.typography.labelLarge,
                color = if (hovering) MaterialTheme.colorScheme.onTertiaryContainer else MaterialTheme.colorScheme.onSurface,
            )
        }
    } else {
        SideEffect { play.haveZone = null }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun HypRow(play: Play, goal: Goal, h: Hyp) {
    val source = remember(h.id) { Source.Hypothesis(h.id) }
    val rendered = remember(h) { render(h.stmt) }
    val zone = remember(h.id) { MathZone(Loc.InHyp(h.id), rendered) }
    DisposableEffect(h.id) {
        play.hypZones[h.id] = zone
        onDispose { play.hypZones.remove(h.id) }
    }
    val loc = remember(h.id) { Loc.InHyp(h.id) }
    val highlights = play.highlightsIn(loc)
    val hoverTarget = play.hover?.let { play.targetOf(it) }
    val isArmed = play.armed == source
    val isHinted = play.hint?.loc == loc
    val canFlip = (h.stmt as? Op)?.op in setOf("eq", "iff")

    Surface(
        shape = RoundedCornerShape(12.dp),
        color = if (isArmed) MaterialTheme.colorScheme.tertiaryContainer else MaterialTheme.colorScheme.surfaceContainerHigh,
    ) {
        Row(Modifier.fillMaxWidth().padding(start = 2.dp, end = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier
                    .dragSource(play, source, anyDirection = true)
                    .size(36.dp, 44.dp),
                contentAlignment = Alignment.Center,
            ) { Icon(Icons.Rounded.DragIndicator, tr("Drag ${h.id}", "${h.id} ziehen"), tint = MaterialTheme.colorScheme.onSurfaceVariant) }
            Prose(
                h.id,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(end = 6.dp),
            )
            MathText(
                rendered = if (play.isFlipped(source)) renderFlipped(h.stmt) else rendered,
                fontSize = 17.sp,
                highlights = highlights,
                active = hoverTarget?.takeIf { it.loc == loc }?.path,
                hinted = play.hint?.takeIf { isHinted }?.path,
                zone = zone,
                onTapTerm = { p -> play.actionAt(loc, p)?.let(play::commit) },
                modifier = Modifier
                    .weight(1f)
                    .padding(vertical = 8.dp)
                    .let { m ->
                        if (highlights.isEmpty()) m.pointerInput(h.id) {
                            detectTapGestures(onTap = { play.armed = if (play.armed == source) null else source })
                        } else m
                    },
            )
            if (canFlip) {
                IconButton(onClick = { play.toggleFlip(source) }, modifier = Modifier.size(36.dp)) {
                    Icon(Icons.Rounded.SwapHoriz, tr("Flip", "Umdrehen"), tint = if (play.isFlipped(source)) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            if (Engine.canCase(goal, h.id)) {
                FilledTonalButton(
                    onClick = { play.cases(h.id) },
                    contentPadding = PaddingValues(horizontal = 10.dp),
                    modifier = Modifier.height(32.dp),
                ) { Text(tr("Cases", "Fälle"), style = MaterialTheme.typography.labelMedium) }
            }
        }
    }
}

private fun renderFlipped(t: Term): Rendered {
    val op = t as? Op ?: return render(t)
    return if (op.op == "eq" || op.op == "iff") render(op.copy(args = op.args.reversed())) else render(t)
}

/** Flipped display of a whole tool, keeping any premises and binders in front. */
private fun flippedStatement(t: Term): Term = when {
    t is Quant -> t.copy(body = flippedStatement(t.body))
    t is Op && t.op == "imp" -> t.copy(args = listOf(t.args[0], flippedStatement(t.args[1])))
    t is Op && (t.op == "eq" || t.op == "iff") -> t.copy(args = t.args.reversed())
    else -> t
}

private fun isReversible(t: Term): Boolean = when {
    t is Quant -> isReversible(t.body)
    t is Op && t.op == "imp" -> isReversible(t.args[1])
    t is Op && (t.op == "eq" || t.op == "iff") -> true
    else -> false
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun GoalArea(play: Play, accent: Color) {
    if (play.state.solved) return
    val goals = play.state.goals
    if (goals.size > 1) {
        Row(
            Modifier.horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            goals.forEachIndexed { i, g ->
                FilterChip(
                    selected = i == 0,
                    onClick = { if (i != 0) play.focus(i) },
                    label = { Text(tr("Goal ${i + 1}", "Ziel ${i + 1}")) },
                )
            }
        }
    }
    val rendered = remember(play.state.current.target) { render(play.state.current.target) }
    val highlights = play.highlightsIn(Loc.Target)
    val hoverTarget = play.hover?.let { play.targetOf(it) }
    val isHinted = play.hint?.loc == Loc.Target
    Surface(
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.primaryContainer,
        border = BorderStroke(2.dp, if (play.active != null && highlights.isNotEmpty()) accent else Color.Transparent),
    ) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp)) {
            Text(
                if (goals.size > 1) tr("Goal 1 of ${goals.size}", "Ziel 1 von ${goals.size}") else tr("Goal", "Ziel"),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.72f),
            )
            Spacer(Modifier.height(4.dp))
            MathText(
                rendered = rendered,
                fontSize = 24.sp,
                color = MaterialTheme.colorScheme.onPrimaryContainer,
                highlights = highlights,
                active = hoverTarget?.takeIf { it.loc == Loc.Target }?.path,
                hinted = play.hint?.takeIf { isHinted }?.path,
                zone = play.goalZone,
                onTapTerm = { p -> play.actionAt(Loc.Target, p)?.let(play::commit) },
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun LogicRow(play: Play) {
    val options = Engine.logicOptions(play.state, play.ctx)
    if (options.isEmpty()) return
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        options.forEach { k ->
            val icon = when (k) {
                LogicKind.Intro -> Icons.Rounded.Login
                LogicKind.Split -> Icons.Rounded.CallSplit
                LogicKind.Left -> Icons.Rounded.ArrowBack
                LogicKind.Right -> Icons.Rounded.ArrowForward
                LogicKind.Use -> Icons.Rounded.TouchApp
                LogicKind.Compute -> Icons.Rounded.Bolt
            }
            FilledTonalButton(onClick = { play.logic(k) }) {
                Icon(icon, null, Modifier.size(18.dp))
                Spacer(Modifier.width(6.dp))
                Text(logicLabel(k))
            }
        }
    }
}

/**
 * Starts a drag from a tool or hypothesis. Tray cards start a drag only on a vertical movement,
 * which leaves horizontal swipes to scroll the tray.
 */
@Composable
private fun Modifier.dragSource(play: Play, source: Source, anyDirection: Boolean): Modifier {
    val coords = remember { arrayOfNulls<LayoutCoordinates>(1) }
    val haptic = LocalHapticFeedback.current
    val density = LocalDensity.current
    val lift = with(density) { 44.dp.toPx() }
    val snap = with(density) { 72.dp.toPx() }
    return this
        .onGloballyPositioned { coords[0] = it }
        .pointerInput(source, anyDirection) {
            awaitEachGesture {
                val down = awaitFirstDown(requireUnconsumed = false)
                val start = if (anyDirection) {
                    awaitTouchSlopOrCancellation(down.id) { c, _ -> c.consume() }
                } else {
                    awaitVerticalTouchSlopOrCancellation(down.id) { c, _ -> c.consume() }
                }
                if (start != null) {
                    val c = coords[0] ?: return@awaitEachGesture
                    play.armed = null
                    play.dragging = source
                    play.pointer = c.localToRoot(start.position)
                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                    var lastTarget: DropTarget? = null
                    val finished = drag(start.id) { change ->
                        val p = c.localToRoot(change.position)
                        change.consume()
                        play.pointer = p
                        val hit = play.resolve(p + Offset(0f, -lift), snap)
                        // Actions are rebuilt on every move, so compare where they land, not the objects.
                        val target = hit?.let(play::targetOf)
                        if (target != lastTarget) {
                            if (target != null) haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            lastTarget = target
                            play.hover = hit
                        }
                    }
                    val chosen = play.hover
                    play.hover = null
                    play.dragging = null
                    if (finished && chosen != null) {
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        play.commit(chosen)
                    } else if (finished) {
                        play.message = play.missedDropMessage(source)
                    }
                }
            }
        }
}

@Composable
private fun Tray(play: Play, world: String, onExpand: () -> Unit, onDetail: (Fact) -> Unit, onCommitted: () -> Unit) {
    var filter by remember { mutableStateOf(TrayFilter.Suggested) }
    val all = play.tools
    val worldIds = remember(world) { Course.world(world).levels.flatMap { l -> l.grants.map { it.id } + l.id }.toSet() }
    val shown = when (filter) {
        TrayFilter.Suggested -> all.filter { it.id in play.solutionTools } + all.filter { it.id !in play.solutionTools && it.id in worldIds }.take(3)
        TrayFilter.Recent -> all.filter { it.id in worldIds }
        TrayFilter.All -> all
    }
    val hinted = play.hintTool()
    val ordered = if (hinted != null) shown.sortedBy { if (it.id == hinted) 0 else 1 } else shown
    val trayState = androidx.compose.foundation.lazy.rememberLazyListState()
    // A hint names a card, so that card is brought into view.
    LaunchedEffect(hinted) {
        if (hinted != null) {
            filter = TrayFilter.Suggested
            trayState.scrollToItem(0)
        }
    }

    Surface(
        tonalElevation = 3.dp,
        shadowElevation = 8.dp,
        shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
        color = MaterialTheme.colorScheme.surfaceContainer,
    ) {
        Column(Modifier.fillMaxWidth().navigationBarsPadding().padding(top = 8.dp, bottom = 8.dp)) {
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Row(Modifier.weight(1f).horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    TrayFilter.entries.forEach { f ->
                        FilterChip(selected = filter == f, onClick = { filter = f }, label = { Text(f.label) })
                    }
                }
                IconButton(onClick = onExpand) { Icon(Icons.Rounded.UnfoldMore, tr("Show all tools", "Alle Werkzeuge zeigen")) }
            }
            LazyRow(
                state = trayState,
                contentPadding = PaddingValues(horizontal = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.height(100.dp),
            ) {
                items(ordered, key = { it.id }) { f ->
                    ToolCard(play, f, highlighted = hinted == f.id, onDetail = { onDetail(f) }, compact = true)
                }
            }
            Text(
                if (play.active != null) tr("Tap a highlighted part of the proof", "Tippe auf eine markierte Stelle des Beweises") else tr("Drag a card up onto the proof, or tap it to select", "Ziehe eine Karte auf den Beweis oder tippe sie zum Auswählen an"),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 2.dp),
            )
        }
    }
}

@Composable
private fun kindColor(k: FactKind): Color = when (k) {
    FactKind.Axiom -> MaterialTheme.colorScheme.primary
    FactKind.Definition -> MaterialTheme.colorScheme.secondary
    FactKind.Field -> MaterialTheme.colorScheme.outline
    FactKind.Theorem -> MaterialTheme.colorScheme.tertiary
    FactKind.Lecture -> MaterialTheme.colorScheme.error
}

fun kindLabel(k: FactKind) = when (k) {
    FactKind.Axiom -> "Axiom"
    FactKind.Definition -> "Definition"
    FactKind.Field -> tr("Field rule", "Körperregel")
    FactKind.Theorem -> tr("Your theorem", "Dein Satz")
    FactKind.Lecture -> tr("From the lecture", "Aus der Vorlesung")
}

/** The kind, German term and section of a fact; in German the term is left out when it just repeats the name. */
fun factDetails(f: Fact): String =
    listOf(kindLabel(f.kind), f.german.takeIf { it != f.localName }.orEmpty(), f.ref).filter { it.isNotBlank() }.joinToString(" · ")

@Composable
private fun ToolCard(play: Play, f: Fact, highlighted: Boolean, compact: Boolean, onDetail: () -> Unit, onPicked: () -> Unit = {}) {
    val source = remember(f.id) { Source.Tool(f) }
    val isArmed = play.armed == source
    val flipped = play.isFlipped(source)
    val rendered = remember(f.id, flipped) { render(if (flipped) flippedStatement(f.statement) else f.statement) }
    val reversible = remember(f.id) { isReversible(f.statement) }
    val border = when {
        highlighted -> BorderStroke(2.dp, MaterialTheme.colorScheme.primary)
        isArmed -> BorderStroke(2.dp, MaterialTheme.colorScheme.tertiary)
        else -> null
    }
    Surface(
        shape = RoundedCornerShape(14.dp),
        color = if (isArmed) MaterialTheme.colorScheme.tertiaryContainer else MaterialTheme.colorScheme.surfaceContainerHighest,
        border = border,
        modifier = Modifier
            .then(if (compact) Modifier.widthIn(min = 120.dp, max = 320.dp).fillMaxHeight() else Modifier.fillMaxWidth())
            .then(if (compact) Modifier.dragSource(play, source, anyDirection = false) else Modifier)
            .pointerInput(f.id) {
                detectTapGestures(
                    onLongPress = { onDetail() },
                    onTap = {
                        play.armed = if (play.armed == source) null else source
                        onPicked()
                        if (play.armed != null && play.activeActions().none { it !is Action.Have }) {
                            play.message = play.missedDropMessage(source)
                        }
                    },
                )
            },
    ) {
        Column(Modifier.padding(start = 12.dp, end = 4.dp, top = 6.dp, bottom = 6.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(8.dp).clip(CircleShape).background(kindColor(f.kind)))
                Spacer(Modifier.width(6.dp))
                Prose(
                    f.localName,
                    style = MaterialTheme.typography.labelMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = !compact),
                )
                if (reversible) {
                    IconButton(onClick = { play.toggleFlip(source) }, modifier = Modifier.size(30.dp)) {
                        Icon(
                            Icons.Rounded.SwapHoriz,
                            tr("Flip direction", "Richtung umdrehen"),
                            Modifier.size(20.dp),
                            tint = if (flipped) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                } else {
                    Spacer(Modifier.height(30.dp))
                }
            }
            MathText(rendered, fontSize = 16.sp, modifier = Modifier.padding(end = 8.dp))
        }
    }
}

@Composable
private fun DragGhost(play: Play) {
    val source = play.dragging ?: return
    val density = LocalDensity.current
    val pointer = play.pointer
    val hover = play.hover
    val preview = remember(hover) { hover?.let(play::previewOf) }
    val closes = remember(hover) { hover?.let(play::closesGoal) == true }
    val screenWidth = androidx.compose.ui.platform.LocalWindowInfo.current.containerSize.width
    val widthPx = with(density) { 300.dp.toPx() }
    val heightPx = with(density) { 96.dp.toPx() }
    Box(
        Modifier
            .offset {
                val margin = 16.dp.toPx()
                val x = (pointer.x - widthPx / 2).coerceIn(margin, maxOf(margin, screenWidth - widthPx - margin))
                IntOffset(x.toInt(), (pointer.y - heightPx - 72.dp.toPx()).toInt().coerceAtLeast(0))
            }
            .width(300.dp)
            .shadow(10.dp, RoundedCornerShape(14.dp))
            .background(MaterialTheme.colorScheme.inverseSurface, RoundedCornerShape(14.dp))
            .padding(horizontal = 14.dp, vertical = 8.dp),
    ) {
        Column {
            Prose(
                source.label,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.inverseOnSurface,
            )
            when {
                hover == null -> Text(
                    if (play.activeActions().any { it !is Action.Have }) tr("Move onto a highlighted part", "Auf eine markierte Stelle ziehen") else tr("Doesn't fit anywhere yet", "Passt noch nirgends"),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.inverseOnSurface.copy(alpha = 0.76f),
                )
                closes -> Prose(tr("Release to close this goal ∎", "Loslassen schließt dieses Ziel ∎"), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.inverseOnSurface)
                preview != null -> MathText(preview, fontSize = 17.sp, color = MaterialTheme.colorScheme.inverseOnSurface)
                else -> Text(tr("Release, then choose a value", "Loslassen, dann einen Wert wählen"), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.inverseOnSurface)
            }
        }
    }
}

@Composable
private fun MessageBar(play: Play, modifier: Modifier) {
    val text = play.hintText ?: play.message ?: return
    LaunchedEffect(text) {
        if (play.message == text) {
            kotlinx.coroutines.delay(2600)
            if (play.message == text) play.message = null
        }
    }
    Surface(
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.inverseSurface,
        shadowElevation = 6.dp,
        modifier = modifier.padding(horizontal = 16.dp),
        onClick = {
            play.hintText = null
            play.hint = null
            play.message = null
        },
    ) {
        Prose(
            text,
            color = MaterialTheme.colorScheme.inverseOnSurface,
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
        )
    }
}

/** Takes the place of the tray while a value is being chosen, so the goal stays in view. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun PickerPanel(play: Play) {
    Surface(
        tonalElevation = 3.dp,
        shadowElevation = 8.dp,
        shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
        color = MaterialTheme.colorScheme.surfaceContainer,
    ) {
        Column(Modifier.fillMaxWidth().navigationBarsPadding().padding(horizontal = 16.dp, vertical = 10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Prose(tr("Choose ", "Wähle ") + play.pickName(), style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
                TextButton(onClick = { play.cancelPick() }) { Text(tr("Cancel", "Abbrechen")) }
            }
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.heightIn(max = 240.dp).verticalScroll(rememberScrollState()).padding(vertical = 6.dp),
            ) {
                play.candidatesForPick().forEach { t ->
                    Surface(
                        onClick = { play.choose(t) },
                        shape = RoundedCornerShape(12.dp),
                        color = MaterialTheme.colorScheme.secondaryContainer,
                    ) {
                        MathText(
                            render(t),
                            fontSize = 20.sp,
                            color = MaterialTheme.colorScheme.onSecondaryContainer,
                            modifier = Modifier.padding(horizontal = 18.dp, vertical = 10.dp),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun AllToolsOverlay(play: Play, onDismiss: () -> Unit, onDetail: (Fact) -> Unit) {
    var query by remember { mutableStateOf("") }
    val shown = play.tools.filter {
        query.isBlank() || it.name.contains(query, true) || it.localName.contains(query, true) || it.german.contains(query, true) || it.ref.contains(query, true)
    }
    BackHandler(onBack = onDismiss)
    Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.surface) {
        Column(Modifier.fillMaxSize().statusBarsPadding().padding(horizontal = 16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(tr("Your tools", "Deine Werkzeuge"), style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
                IconButton(onClick = onDismiss) { Icon(Icons.Rounded.Close, tr("Close tools", "Werkzeuge schließen")) }
            }
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                leadingIcon = { Icon(Icons.Rounded.Search, null) },
                placeholder = { Text(tr("Search your ${play.tools.size} tools", "${play.tools.size} Werkzeuge durchsuchen")) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(8.dp))
            Text(
                tr("Tap a tool to select it, then tap where to use it.", "Tippe ein Werkzeug an und dann die Stelle, an der du es verwenden willst."),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(8.dp))
            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(8.dp),
                contentPadding = PaddingValues(bottom = 32.dp),
                modifier = Modifier.fillMaxSize().navigationBarsPadding(),
            ) {
                items(shown, key = { it.id }) { f ->
                    ToolCard(play, f, highlighted = false, compact = false, onDetail = { onDetail(f) }, onPicked = onDismiss)
                }
            }
        }
    }
}

@Composable
fun FactDialog(f: Fact, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Prose(f.localName) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                MathText(render(f.statement), fontSize = 20.sp)
                Prose(factDetails(f), style = MaterialTheme.typography.bodySmall)
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text(tr("Close", "Schließen")) } },
    )
}

@Composable
private fun ProofLogDialog(play: Play, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(tr("Proof so far", "Bisheriger Beweis")) },
        text = { ProofLog(play) },
        confirmButton = { TextButton(onClick = onDismiss) { Text(tr("Close", "Schließen")) } },
    )
}

@Composable
fun ProofLog(play: Play, modifier: Modifier = Modifier) {
    LazyColumn(modifier = modifier.heightIn(max = 340.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        items(play.log.toList().withIndex().toList()) { (i, e) ->
            Column {
                Prose("${i + 1}. ${e.label}", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
                if (e.after != null) MathText(e.after, fontSize = 15.sp) else Text("∎", fontFamily = MathFamily, fontSize = 18.sp)
            }
        }
    }
}
