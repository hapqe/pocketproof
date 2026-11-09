package com.pocketproof.content

import com.pocketproof.engine.*

/** One move of a model solution, with what is needed to explain it as a hint. */
data class ScriptStep(
    val before: String,
    val text: String,
    val toolName: String?,
    val flipped: Boolean,
    val loc: Loc?,
    val path: Path?,
    val line: String,
)

/**
 * Plays a level's model solution through the real engine, using only what the player would have.
 * A failing step means the level is broken, so the test suite runs this for every level.
 */
object Script {
    private val HYP = Regex("h\\d+")

    fun display(s: Sym) = s.name.substringBefore('?')

    fun run(level: Level): Pair<ProofState, List<ScriptStep>> {
        val ctx = Course.context(level)
        val tools = Course.toolsFor(level).associateBy { it.id }
        var state = Course.start(level)
        val steps = mutableListOf<ScriptStep>()
        for (line in level.solution) {
            check(!state.solved) { "${level.id}: already solved before '$line'" }
            val before = state.fingerprint()
            val segs = line.split("|").map { it.trim() }
            val head = segs[0].split(" ", limit = 2)
            val cmd = head[0]
            val arg = head.getOrNull(1)?.trim()
            val opts = segs.drop(1)
            fun term(s: String) = parse(s, level.env)
            fun source(name: String): Source = if (HYP.matches(name)) {
                check(state.current.hyp(name) != null) { "${level.id}: no hypothesis $name in '$line'. Have ${state.current.hyps.map { it.id + ":" + render(it.stmt).text }}" }
                Source.Hypothesis(name)
            } else Source.Tool(tools[name] ?: error("${level.id}: tool '$name' not available in '$line'"))

            fun picksFor(a: Action): Map<Sym, Term> {
                val out = mutableMapOf<Sym, Term>()
                for (u in a.unbound) {
                    val opt = opts.firstOrNull { it.startsWith(display(u) + ":=") }
                        ?: error("${level.id}: '$line' needs a choice for ${display(u)}")
                    val v = term(opt.substringAfter(":="))
                    check(sortOf(v) == u.sort) { "${level.id}: wrong sort for ${display(u)} in '$line'" }
                    check(Engine.candidates(state.current, u.sort, ctx).any { alphaEq(it, v) }) {
                        "${level.id}: '${render(v).text}' is not offered to the player in '$line'. Add it to offers."
                    }
                    out[u] = v
                }
                return out
            }

            fun describePicks(a: Action, picks: Map<Sym, Term>) =
                if (picks.isEmpty()) "" else Wording.tr(", choosing ", ", wähle ") + a.unbound.joinToString(", ") { "${display(it)} := ${render(picks.getValue(it)).text}" }

            var toolName: String? = null
            var flipped = false
            var loc: Loc? = null
            var path: Path? = null
            val text: String
            when (cmd) {
                "intro" -> { state = Engine.intro(state, ctx); text = Wording.tr("Tap Intro", "Tippe auf Annehmen") }
                "split" -> { state = Engine.split(state, ctx); text = Wording.tr("Tap Split", "Tippe auf Aufteilen") }
                "left" -> { state = Engine.choose(state, true, ctx); text = Wording.tr("Tap Left", "Tippe auf Links") }
                "right" -> { state = Engine.choose(state, false, ctx); text = Wording.tr("Tap Right", "Tippe auf Rechts") }
                "use" -> {
                    val v = term(arg!!)
                    val q = state.current.target as Quant
                    check(q.kind == QKind.Exists) { "${level.id}: 'use' needs ∃" }
                    check(Engine.candidates(state.current, q.v.sort, ctx).any { alphaEq(it, v) }) {
                        "${level.id}: witness '${render(v).text}' is not offered in '$line'. Add it to offers."
                    }
                    state = Engine.witness(state, v, ctx)
                    text = Wording.tr("Tap Choose and pick ${render(v).text}", "Tippe auf Wählen und nimm ${render(v).text}")
                }
                "cases" -> { state = Engine.cases(state, arg!!, ctx); text = Wording.tr("Tap Cases on ${arg}", "Tippe bei ${arg} auf Fälle") }
                "compute" -> {
                    check(Engine.logicOptions(state, ctx).contains(LogicKind.Compute)) { "${level.id}: Compute not available in '$line'" }
                    state = Engine.compute(state, ctx) ?: error(
                        "${level.id}: Compute failed on ${render(state.current.target).text} with ${state.current.hyps.map { render(it.stmt).text }}",
                    )
                    text = Wording.tr("Tap {compute} Compute", "Tippe auf {compute} Rechnen")
                }
                "rw", "rw<" -> {
                    flipped = cmd == "rw<"
                    val src = source(arg!!)
                    toolName = src.label
                    val inHyp = opts.firstOrNull { it.startsWith("in ") }?.substringAfter("in ")?.trim()
                    val at = opts.firstOrNull { it.startsWith("@") }?.let { term(it.drop(1)) }
                    val g = state.current
                    val candidates = Engine.actions(state, src, flipped).filterIsInstance<Action.Rewrite>().filter { a ->
                        val l = a.loc
                        when {
                            inHyp == null && l != Loc.Target -> false
                            inHyp != null && (l !is Loc.InHyp || l.id != inHyp) -> false
                            else -> true
                        }
                    }.filter { a ->
                        if (at == null) return@filter true
                        val root = when (val l = a.loc) {
                            Loc.Target -> g.target
                            is Loc.InHyp -> g.hyp(l.id)!!.stmt
                        }
                        alphaEq(root.at(a.path), at)
                    }
                    val a = candidates.firstOrNull() ?: error(
                        "${level.id}: '$line' has no match. Goal: ${render(g.target).text}; hyps: ${g.hyps.map { it.id + ":" + render(it.stmt).text }}",
                    )
                    loc = a.loc
                    path = a.path
                    val picks = picksFor(a)
                    state = Engine.perform(state, a, picks, ctx)
                    text = (if (flipped) Wording.tr("Tap {flip} on ${src.label}, then drag it onto the marked part", "Tippe bei ${src.label} auf {flip} und ziehe es dann auf die markierte Stelle") else Wording.tr("Drag ${src.label} onto the marked part", "Ziehe ${src.label} auf die markierte Stelle")) + describePicks(a, picks)
                }
                "apply" -> {
                    val src = source(arg!!)
                    toolName = src.label
                    val g = state.current
                    val a = Engine.actions(state, src, false).filterIsInstance<Action.ApplyGoal>().firstOrNull()
                        ?: error("${level.id}: '$line' does not fit the goal ${render(g.target).text}; hyps: ${g.hyps.map { it.id + ":" + render(it.stmt).text }}")
                    loc = Loc.Target
                    path = emptyList()
                    val picks = picksFor(a)
                    state = Engine.perform(state, a, picks, ctx)
                    text = Wording.tr("Drag ${src.label} onto the whole goal", "Ziehe ${src.label} auf das ganze Ziel") + describePicks(a, picks)
                }
                "fwd" -> {
                    val parts = arg!!.split(" ")
                    val src = source(parts[1])
                    toolName = src.label
                    val g = state.current
                    val a = Engine.actions(state, src, false).filterIsInstance<Action.Forward>().firstOrNull { it.hypId == parts[0] }
                        ?: error("${level.id}: '$line' cannot use ${parts[0]}. Hyps: ${g.hyps.map { it.id + ":" + render(it.stmt).text }}")
                    loc = Loc.InHyp(parts[0])
                    path = emptyList()
                    val picks = picksFor(a)
                    state = Engine.perform(state, a, picks, ctx)
                    text = Wording.tr("Drag ${src.label} onto ${parts[0]}", "Ziehe ${src.label} auf ${parts[0]}") + describePicks(a, picks)
                }
                "have" -> {
                    val src = source(arg!!)
                    toolName = src.label
                    val a = Engine.actions(state, src, false).filterIsInstance<Action.Have>().first()
                    val picks = picksFor(a)
                    state = Engine.perform(state, a, picks, ctx)
                    text = Wording.tr("Drag ${src.label} into Given", "Ziehe ${src.label} nach Gegeben") + describePicks(a, picks)
                }
                else -> error("${level.id}: unknown command in '$line'")
            }
            steps += ScriptStep(before, text, toolName, flipped, loc, path, line)
        }
        return state to steps
    }

    /** Tools a level's model solution actually uses. */
    fun toolIds(level: Level): Set<String> = level.solution.mapNotNull { line ->
        val head = line.split("|")[0].trim().split(" ")
        when (head[0]) {
            "rw", "rw<", "apply", "have" -> head.getOrNull(1)
            "fwd" -> head.getOrNull(2)
            else -> null
        }
    }.filterNot { HYP.matches(it) }.toSet()
}
