package com.pocketproof.ui

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import com.pocketproof.content.Course
import com.pocketproof.content.Level
import com.pocketproof.content.Script
import com.pocketproof.content.ScriptStep
import com.pocketproof.engine.*

/** The result of a successful drop or tap, kept for the proof log. */
data class LogEntry(val label: String, val after: Rendered?)

/** A partly chosen move: the player is picking values for its open variables, one at a time. */
data class PendingPick(val action: Action?, val remaining: List<Sym>, val chosen: Map<Sym, Term>, val witnessSort: Sort?)

data class DropTarget(val loc: Loc?, val path: Path?)

fun logicLabel(kind: LogicKind): String = when (kind) {
    LogicKind.Intro -> tr("Intro", "Annehmen")
    LogicKind.Split -> tr("Split", "Aufteilen")
    LogicKind.Left -> tr("Left", "Links")
    LogicKind.Right -> tr("Right", "Rechts")
    LogicKind.Use -> tr("Choose", "Wählen")
    LogicKind.Compute -> tr("Compute", "Rechnen")
}

/** All of the mutable state of one attempt at a level. */
class Play(val level: Level) {
    val ctx = Course.context(level)
    val tools: List<Fact> = Course.toolsFor(level)
    val solutionTools: Set<String> = Script.toolIds(level)
    private val script: List<ScriptStep> = Script.run(level).second

    var state by mutableStateOf(Course.start(level))
        private set
    private val history = mutableStateListOf<ProofState>()
    val log = mutableStateListOf<LogEntry>()
    var moves by mutableIntStateOf(0)
        private set
    var hintsUsed by mutableIntStateOf(0)
        private set

    val flipped = mutableStateMapOf<String, Boolean>()
    var armed by mutableStateOf<Source?>(null)
    var dragging by mutableStateOf<Source?>(null)
    var pointer by mutableStateOf(Offset.Zero)
    var hover by mutableStateOf<Action?>(null)
    var pending by mutableStateOf<PendingPick?>(null)
    var message by mutableStateOf<String?>(null)
    var hint by mutableStateOf<ScriptStep?>(null)
    var hintText by mutableStateOf<String?>(null)

    val goalZone = MathZone(Loc.Target, render(state.current.target))
    val hypZones = mutableMapOf<String, MathZone>()
    var haveZone: Rect? = null

    val canUndo get() = history.isNotEmpty()
    val active: Source? get() = dragging ?: armed

    fun isFlipped(source: Source): Boolean = flipped[key(source)] == true
    fun toggleFlip(source: Source) { flipped[key(source)] = !isFlipped(source) }
    private fun key(s: Source) = when (s) {
        is Source.Tool -> s.fact.id
        is Source.Hypothesis -> s.id
    }

    fun actionsFor(source: Source): List<Action> = Engine.actions(state, source, isFlipped(source))

    fun activeActions(): List<Action> = active?.let(::actionsFor) ?: emptyList()

    fun targetOf(a: Action): DropTarget = when (a) {
        is Action.Rewrite -> DropTarget(a.loc, a.path)
        is Action.ApplyGoal -> DropTarget(Loc.Target, emptyList())
        is Action.Forward -> DropTarget(Loc.InHyp(a.hypId), emptyList())
        is Action.Have -> DropTarget(null, null)
    }

    /** Every (location, path) the active tool can land on. */
    fun highlightsIn(loc: Loc): Set<Path> =
        activeActions().map(::targetOf).filter { it.loc == loc }.mapNotNull { it.path }.toSet()

    fun actionAt(loc: Loc, path: Path): Action? =
        activeActions().firstOrNull { targetOf(it).let { t -> t.loc == loc && t.path == path } }

    /** Finds the nearest usable place to a drag position. Dropping does not need pixel precision. */
    fun resolve(point: Offset, snapRadius: Float): Action? {
        val actions = activeActions()
        haveZone?.let { z ->
            if (z.contains(point)) return actions.firstOrNull { it is Action.Have }
        }
        var best: Action? = null
        var bestDist = Float.MAX_VALUE
        var bestArea = Float.MAX_VALUE
        for (a in actions) {
            val t = targetOf(a)
            val loc = t.loc ?: continue
            val path = t.path ?: continue
            val zone = zoneFor(loc) ?: continue
            val span = zone.rendered.spanFor(path) ?: continue
            val r = zone.bounds(span) ?: continue
            val dx = maxOf(r.left - point.x, 0f, point.x - r.right)
            val dy = maxOf(r.top - point.y, 0f, point.y - r.bottom)
            val d = kotlin.math.hypot(dx, dy)
            val area = r.width * r.height
            if (d <= snapRadius && (d < bestDist - 1f || (d <= bestDist + 1f && area < bestArea))) {
                best = a
                bestDist = d
                bestArea = area
            }
        }
        return best
    }

    private fun zoneFor(loc: Loc): MathZone? = when (loc) {
        Loc.Target -> goalZone
        is Loc.InHyp -> hypZones[loc.id]
    }

    private fun label(a: Action): String {
        val name = when (val s = a.source) {
            is Source.Tool -> s.fact.localName
            is Source.Hypothesis -> s.id
        }
        return when (a) {
            is Action.Rewrite -> (if (a.flipped) "$name {flip}" else name) + ((a.loc as? Loc.InHyp)?.let { " in ${it.id}" } ?: "")
            is Action.ApplyGoal -> tr("Apply $name", "$name anwenden")
            is Action.Forward -> tr("$name on ${a.hypId}", "$name auf ${a.hypId}")
            is Action.Have -> tr("Add $name to Given", "$name zu Gegeben hinzufügen")
        }
    }

    private fun push(next: ProofState, label: String) {
        history += state
        state = next
        moves++
        armed = null
        hint = null
        hintText = null
        message = null
        log += LogEntry(label, if (next.solved) null else render(next.current.target))
    }

    /** Runs an action, first asking for any values it still needs. */
    fun commit(a: Action) {
        armed = null
        // A flip applies to one use, so a card always starts out in its normal direction.
        flipped.remove(key(a.source))
        if (a.unbound.isEmpty()) {
            push(Engine.perform(state, a, emptyMap(), ctx), label(a))
        } else {
            pending = PendingPick(a, a.unbound, emptyMap(), null)
        }
    }

    fun pickSort(): Sort? = pending?.let { p -> p.witnessSort ?: p.remaining.first().sort }

    fun pickName(): String = pending?.let { p ->
        if (p.witnessSort != null) (state.current.target as Quant).v.name else Script.display(p.remaining.first())
    } ?: ""

    fun candidatesForPick(): List<Term> = pickSort()?.let { Engine.candidates(state.current, it, ctx) } ?: emptyList()

    fun choose(value: Term) {
        val p = pending ?: return
        if (p.witnessSort != null) {
            pending = null
            push(Engine.witness(state, value, ctx), tr("Choose ${render(value).text}", "Wähle ${render(value).text}"))
            return
        }
        val chosen = p.chosen + (p.remaining.first() to value)
        val rest = p.remaining.drop(1)
        if (rest.isEmpty()) {
            pending = null
            val names = chosen.entries.joinToString(", ") { "${Script.display(it.key)} := ${render(it.value).text}" }
            push(Engine.perform(state, p.action!!, chosen, ctx), label(p.action) + " ($names)")
        } else {
            pending = p.copy(remaining = rest, chosen = chosen)
        }
    }

    fun cancelPick() { pending = null }

    fun previewOf(a: Action): Rendered? {
        if (a.unbound.isNotEmpty()) return null
        return runCatching {
            val next = Engine.perform(state, a, emptyMap(), ctx)
            when {
                next.goals.size < state.goals.size -> null
                a is Action.Rewrite && a.loc is Loc.InHyp -> {
                    val id = (a.loc as Loc.InHyp).id
                    next.current.hyp(id)?.let { render(it.stmt) } ?: render(next.current.target)
                }
                a is Action.Have || a is Action.Forward ->
                    next.goals.firstOrNull()?.hyps?.lastOrNull()?.let { render(it.stmt) }
                else -> render(next.current.target)
            }
        }.getOrNull()
    }

    fun logic(kind: LogicKind) {
        when (kind) {
            LogicKind.Intro -> push(Engine.intro(state, ctx), logicLabel(kind))
            LogicKind.Split -> push(Engine.split(state, ctx), logicLabel(kind))
            LogicKind.Left -> push(Engine.choose(state, true, ctx), logicLabel(kind))
            LogicKind.Right -> push(Engine.choose(state, false, ctx), logicLabel(kind))
            LogicKind.Use -> pending = PendingPick(null, emptyList(), emptyMap(), (state.current.target as Quant).v.sort)
            LogicKind.Compute -> {
                val next = Engine.compute(state, ctx)
                if (next != null) push(next, "{compute} " + logicLabel(kind)) else message = tr("That is not pure arithmetic yet. Rewrite more first.", "Das ist noch keine reine Arithmetik. Forme zuerst weiter um.")
            }
        }
    }

    fun cases(hypId: String) = push(Engine.cases(state, hypId, ctx), tr("Cases on $hypId", "Fälle für $hypId"))

    fun focus(index: Int) {
        state = Engine.focus(state, index)
        armed = null
    }

    fun undo() {
        if (history.isEmpty()) return
        state = history.removeAt(history.lastIndex)
        if (log.isNotEmpty()) log.removeAt(log.lastIndex)
        moves = maxOf(0, moves - 1)
        armed = null
        hint = null
        hintText = null
    }

    fun restart() {
        state = Course.start(level)
        history.clear()
        log.clear()
        moves = 0
        hintsUsed = 0
        armed = null
        hint = null
        hintText = null
    }

    /** Follows the model solution from the current position when the player is still on it. */
    fun requestHint() {
        hintsUsed++
        val here = state.fingerprint()
        val step = script.firstOrNull { it.before == here }
        if (step != null) {
            hint = step
            hintText = step.text + "."
        } else {
            hint = null
            hintText = level.localHint.ifBlank { level.localStory } + tr(
                " You have left the model route, which is fine. Undo to return to it.",
                " Du hast den Musterweg verlassen, das ist in Ordnung. Mit Rückgängig kommst du zu ihm zurück.",
            )
        }
    }

    /** A hint's tool, if it is a tool in the tray rather than a hypothesis. */
    fun hintTool(): String? = hint?.line?.let { line ->
        val parts = line.split("|")[0].trim().split(" ")
        when (parts[0]) {
            "rw", "rw<", "apply", "have" -> parts.getOrNull(1)
            "fwd" -> parts.getOrNull(2)
            else -> null
        }
    }

    /** Whether a hovered action would finish the current goal. */
    fun closesGoal(a: Action): Boolean = a.unbound.isEmpty() &&
        runCatching { Engine.perform(state, a, emptyMap(), ctx).goals.size < state.goals.size }.getOrDefault(false)

    fun missedDropMessage(source: Source): String =
        if (actionsFor(source).any { it !is Action.Have }) tr("Drop it on a highlighted part of the proof.", "Lege es auf eine markierte Stelle des Beweises.")
        else tr("No place to use that here. Try flipping it with {flip}.", "Das passt hier nirgends. Versuche, es mit {flip} umzudrehen.")

    fun starsEarned(): Int = when {
        hintsUsed == 0 && moves <= level.par -> 3
        hintsUsed <= 1 && moves <= level.par + 3 -> 2
        else -> 1
    }
}
