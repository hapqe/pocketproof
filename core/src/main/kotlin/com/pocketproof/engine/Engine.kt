package com.pocketproof.engine

data class Hyp(val id: String, val stmt: Term)

data class Goal(val vars: List<Sym>, val hyps: List<Hyp>, val target: Term) {
    fun hyp(id: String): Hyp? = hyps.firstOrNull { it.id == id }
}

data class ProofState(val goals: List<Goal>, val nextHyp: Int = 1) {
    val solved get() = goals.isEmpty()
    val current get() = goals.first()

    /** Identifies a state regardless of hypothesis names and order. Used to follow the model solution. */
    fun fingerprint(): String = goals.joinToString(" || ") { g ->
        g.hyps.map { it.stmt.key() }.sorted().joinToString(";") + " |- " + g.target.key()
    }
}

/** Something that can be dragged onto the proof: a tool from the library or a hypothesis. */
sealed interface Source {
    val label: String

    data class Tool(val fact: Fact) : Source {
        override val label get() = fact.localName
    }

    data class Hypothesis(val id: String) : Source {
        override val label get() = id
    }
}

sealed interface Loc {
    data object Target : Loc
    data class InHyp(val id: String) : Loc
}

/** A way of using a source. Variables still in [unbound] must be chosen before it can be carried out. */
sealed interface Action {
    val source: Source
    val unbound: List<Sym>

    data class Rewrite(
        override val source: Source,
        val flipped: Boolean,
        val loc: Loc,
        val path: Path,
        val into: Term,
        val premises: List<Term>,
        val bindings: Map<Sym, Term>,
        override val unbound: List<Sym>,
    ) : Action

    data class ApplyGoal(
        override val source: Source,
        val premises: List<Term>,
        val bindings: Map<Sym, Term>,
        override val unbound: List<Sym>,
    ) : Action

    data class Forward(
        override val source: Source,
        val hypId: String,
        val conclusion: Term,
        val premises: List<Term>,
        val bindings: Map<Sym, Term>,
        override val unbound: List<Sym>,
    ) : Action

    data class Have(
        override val source: Source,
        val conclusion: Term,
        val premises: List<Term>,
        override val unbound: List<Sym>,
    ) : Action
}

enum class LogicKind { Intro, Split, Left, Right, Use, Compute }

/** A statement split into its schematic variables, premises and conclusion. */
data class Decomposed(val schematic: Set<Sym>, val premises: List<Term>, val conclusion: Term)

private fun decompose(stmt: Term, freeAreSchematic: Boolean, stripNot: Boolean = true): Decomposed {
    val schematic = linkedSetOf<Sym>()
    var counter = 0
    // Every schematic variable gets a private name, so it can never be confused with a variable of the goal.
    fun private(v: Sym): Sym = v.copy(name = "${v.name}?${counter++}").also { schematic += it }
    var t = stmt
    if (freeAreSchematic) t = t.subst(t.freeSyms().associateWith { private(it) })
    val premises = mutableListOf<Term>()
    loop@ while (true) {
        val cur = t
        when {
            cur is Quant && cur.kind == QKind.Forall -> t = cur.body.subst(mapOf(cur.v to private(cur.v)))
            cur is Op && cur.op == "imp" -> {
                premises += cur.args[0]
                t = cur.args[1]
            }
            cur is Op && cur.op == "not" && stripNot -> {
                premises += cur.args[0]
                t = FALSE
                break@loop
            }
            else -> break@loop
        }
    }
    return Decomposed(schematic, premises, t)
}

/** Whether a term contains a binder between the root and [path]. */
private fun underBinder(root: Term, path: Path): Boolean {
    var t = root
    for (i in path) {
        if (t is Quant) return true
        t = t.children[i]
    }
    return false
}

object Engine {
    fun start(statement: Term, level: LevelContext): ProofState {
        val free = statement.freeSyms().toList()
        val g = Goal(free, emptyList(), statement)
        return settle(ProofState(listOf(g)), level)
    }

    private fun decomposedSource(goal: Goal, s: Source, stripNot: Boolean = true): Decomposed = when (s) {
        is Source.Tool -> decompose(s.fact.statement, freeAreSchematic = true, stripNot = stripNot)
        is Source.Hypothesis -> decompose(goal.hyp(s.id)!!.stmt, freeAreSchematic = false, stripNot = stripNot)
    }

    private fun orderedUnbound(d: Decomposed, bindings: Map<Sym, Term>, terms: List<Term>): List<Sym> {
        val out = linkedSetOf<Sym>()
        for (t in terms) out += t.freeSyms().filter { it in d.schematic && it !in bindings }
        return out.toList()
    }

    /** Every way the source can be used against the current goal. This drives the drop highlights. */
    fun actions(state: ProofState, source: Source, flipped: Boolean): List<Action> {
        if (state.solved) return emptyList()
        val g = state.current
        val d = decomposedSource(g, source)
        val out = mutableListOf<Action>()

        val concl = d.conclusion as? Op
        val isRule = concl != null && (concl.op == "eq" || concl.op == "iff")
        if (isRule) {
            val from = concl!!.args[if (flipped) 1 else 0]
            val to = concl.args[if (flipped) 0 else 1]
            val places = buildList {
                add(Loc.Target to g.target)
                g.hyps.forEach { h ->
                    if (source !is Source.Hypothesis || source.id != h.id) add(Loc.InHyp(h.id) to h.stmt)
                }
            }
            for ((loc, root) in places) for (p in root.allPaths()) {
                val sub = root.at(p)
                if (concl.op == "iff" && sortOf(sub) != Sort.Prop) continue
                val b = match(from, sub, d.schematic) ?: continue
                if (d.premises.isNotEmpty() && underBinder(root, p)) continue
                val unbound = orderedUnbound(d, b, listOf(to) + d.premises)
                val into = to.subst(b)
                if (unbound.isEmpty() && alphaEq(into, sub)) continue
                out += Action.Rewrite(source, flipped, loc, p, into, d.premises.map { it.subst(b) }, b, unbound)
            }
        }

        if (concl == null || concl.op != "iff") {
            // A goal of the form ¬P is matched against the tool's conclusion before ¬ is unfolded.
            val forApply = if ((g.target as? Op)?.op == "not") decomposedSource(g, source, stripNot = false) else d
            val c = forApply.conclusion
            val conclusion = if (isRule && flipped) Op("eq", (c as Op).args.reversed()) else c
            match(conclusion, g.target, forApply.schematic)?.let { b ->
                val unbound = orderedUnbound(forApply, b, forApply.premises)
                out += Action.ApplyGoal(source, forApply.premises.map { it.subst(b) }, b, unbound)
            }
        }

        for (h in g.hyps) {
            if (source is Source.Hypothesis && source.id == h.id) continue
            for ((i, prem) in d.premises.withIndex()) {
                val b = match(prem, h.stmt, d.schematic) ?: continue
                val rest = d.premises.filterIndexed { k, _ -> k != i }
                val unbound = orderedUnbound(d, b, rest + d.conclusion)
                out += Action.Forward(source, h.id, d.conclusion.subst(b), rest.map { it.subst(b) }, b, unbound)
                break
            }
        }

        val displayed = if (isRule && flipped) Op(concl!!.op, concl.args.reversed()) else d.conclusion
        val unbound = orderedUnbound(d, emptyMap(), listOf(displayed) + d.premises)
        out += Action.Have(source, displayed, d.premises, unbound)
        return out
    }

    fun perform(
        state: ProofState,
        action: Action,
        picks: Map<Sym, Term>,
        level: LevelContext,
    ): ProofState {
        require(action.unbound.all { it in picks }) { "Missing choices" }
        val g = state.current
        val rest = state.goals.drop(1)
        var nextHyp = state.nextHyp
        val sides: List<Term>
        val mainGoals: List<Goal>
        when (action) {
            is Action.Rewrite -> {
                sides = action.premises.map { it.subst(picks) }
                val value = action.into.subst(picks)
                mainGoals = listOf(
                    when (val l = action.loc) {
                        Loc.Target -> g.copy(target = g.target.replaceAt(action.path, value))
                        is Loc.InHyp -> g.copy(hyps = g.hyps.map {
                            if (it.id == l.id) it.copy(stmt = it.stmt.replaceAt(action.path, value)) else it
                        })
                    },
                )
            }
            is Action.ApplyGoal -> {
                sides = emptyList()
                mainGoals = action.premises.map { g.copy(target = it.subst(picks)) }
            }
            is Action.Forward -> {
                sides = action.premises.map { it.subst(picks) }
                mainGoals = listOf(g.copy(hyps = g.hyps + Hyp("h${nextHyp++}", action.conclusion.subst(picks))))
            }
            is Action.Have -> {
                sides = action.premises.map { it.subst(picks) }
                mainGoals = listOf(g.copy(hyps = g.hyps + Hyp("h${nextHyp++}", action.conclusion.subst(picks))))
            }
        }
        val sideGoals = sides.map { g.copy(target = it) }
        return settle(ProofState(mainGoals + sideGoals + rest, nextHyp), level)
    }

    fun logicOptions(state: ProofState, level: LevelContext): List<LogicKind> {
        if (state.solved) return emptyList()
        val t = state.current.target
        return buildList {
            if (t is Quant && t.kind == QKind.Forall) add(LogicKind.Intro)
            if (t is Op && t.op == "imp") add(LogicKind.Intro)
            if (t is Op && t.op == "not") add(LogicKind.Intro)
            if (t is Op && (t.op == "and" || t.op == "iff")) add(LogicKind.Split)
            if (t is Op && t.op == "or") { add(LogicKind.Left); add(LogicKind.Right) }
            if (t is Quant && t.kind == QKind.Exists) add(LogicKind.Use)
            // Offered on every equation once unlocked, even when it would fail, so it never leaks the answer.
            if (level.computeUnlocked && t is Op && t.op == "eq") add(LogicKind.Compute)
        }
    }

    fun intro(state: ProofState, level: LevelContext): ProofState {
        var g = state.current
        var next = state.nextHyp
        var t = g.target
        val vars = g.vars.toMutableList()
        val hyps = g.hyps.toMutableList()
        loop@ while (true) {
            when {
                t is Quant && t.kind == QKind.Forall -> {
                    val name = freshName(t.v.name, usedNames(vars, hyps, t))
                    val nv = t.v.copy(name = name)
                    vars += nv
                    t = t.body.subst(mapOf(t.v to nv))
                }
                t is Op && t.op == "imp" -> {
                    hyps += Hyp("h${next++}", t.args[0])
                    t = t.args[1]
                }
                t is Op && t.op == "not" -> {
                    hyps += Hyp("h${next++}", t.args[0])
                    t = FALSE
                    break@loop
                }
                else -> break@loop
            }
        }
        g = g.copy(vars = vars, hyps = hyps, target = t)
        return settle(state.copy(goals = listOf(g) + state.goals.drop(1), nextHyp = next), level)
    }

    fun split(state: ProofState, level: LevelContext): ProofState {
        val g = state.current
        val t = g.target as Op
        val parts = when (t.op) {
            "and" -> t.args
            "iff" -> listOf(imp(t.args[0], t.args[1]), imp(t.args[1], t.args[0]))
            else -> error("Cannot split")
        }
        return settle(state.copy(goals = parts.map { g.copy(target = it) } + state.goals.drop(1)), level)
    }

    fun choose(state: ProofState, left: Boolean, level: LevelContext): ProofState {
        val g = state.current
        val t = g.target as Op
        require(t.op == "or")
        val goal = g.copy(target = t.args[if (left) 0 else 1])
        return settle(state.copy(goals = listOf(goal) + state.goals.drop(1)), level)
    }

    fun witness(state: ProofState, value: Term, level: LevelContext): ProofState {
        val g = state.current
        val t = g.target as Quant
        require(t.kind == QKind.Exists)
        val goal = g.copy(target = t.body.subst(mapOf(t.v to value)))
        return settle(state.copy(goals = listOf(goal) + state.goals.drop(1)), level)
    }

    fun compute(state: ProofState, level: LevelContext): ProofState? {
        if (!Compute.closes(state.current)) return null
        return settle(state.copy(goals = state.goals.drop(1)), level)
    }

    fun canCase(goal: Goal, hypId: String): Boolean = (goal.hyp(hypId)?.stmt as? Op)?.op == "or"

    fun cases(state: ProofState, hypId: String, level: LevelContext): ProofState {
        val g = state.current
        val h = g.hyp(hypId)!!
        val or = h.stmt as Op
        require(or.op == "or")
        var next = state.nextHyp
        val branches = or.args.map { branch ->
            g.copy(hyps = g.hyps.filter { it.id != hypId } + Hyp("h${next++}", branch))
        }
        return settle(state.copy(goals = branches + state.goals.drop(1), nextHyp = next), level)
    }

    fun focus(state: ProofState, index: Int): ProofState {
        val g = state.goals[index]
        return state.copy(goals = listOf(g) + state.goals.filterIndexed { i, _ -> i != index })
    }

    fun candidates(goal: Goal, sort: Sort, level: LevelContext): List<Term> {
        val seen = linkedMapOf<String, Term>()
        fun add(t: Term) {
            if (sortOf(t) == sort && t.key() !in seen) seen[t.key()] = t
        }
        goal.vars.forEach { add(it) }
        when (sort) {
            Sort.Vec -> add(Op("o"))
            Sort.Scalar -> { add(Op("0")); add(Op("1")) }
            Sort.Coef -> add(Op("zc"))
            Sort.Mat -> add(Op("E"))
            Sort.Perm -> add(Op("idp"))
            else -> {}
        }
        fun harvest(t: Term, local: Set<Sym>) {
            if (t.freeSyms().none { it in local } && t !is Quant && sortOf(t) != Sort.Prop) add(t)
            if (t is Quant) harvest(t.body, local + t.v) else t.children.forEach { harvest(it, local) }
        }
        harvest(goal.target, emptySet())
        goal.hyps.forEach { harvest(it.stmt, emptySet()) }
        level.offers.forEach { add(it) }
        return seen.values.toList()
    }

    private fun usedNames(vars: List<Sym>, hyps: List<Hyp>, extra: Term?): Set<String> = buildSet {
        vars.forEach { add(it.name) }
        hyps.forEach { h -> h.stmt.freeSyms().forEach { add(it.name) } }
        extra?.freeSyms()?.forEach { add(it.name) }
    }

    /** Unpack conjunctions and existentials in hypotheses, and close goals that are now trivial. */
    private fun settle(state: ProofState, level: LevelContext): ProofState {
        val out = mutableListOf<Goal>()
        var next = state.nextHyp
        for (g0 in state.goals) {
            var vars = g0.vars
            val done = mutableListOf<Hyp>()
            val queue = ArrayDeque(g0.hyps)
            while (queue.isNotEmpty()) {
                val h = queue.removeFirst()
                val s = h.stmt
                when {
                    s is Op && s.op == "and" -> {
                        queue.addFirst(Hyp("h${next++}", s.args[1]))
                        queue.addFirst(h.copy(stmt = s.args[0]))
                    }
                    s is Quant && s.kind == QKind.Exists -> {
                        val used = usedNames(vars, done + queue, s) + g0.target.freeSyms().map { it.name }
                        val nv = s.v.copy(name = freshName(s.v.name, used))
                        vars = vars + nv
                        queue.addFirst(h.copy(stmt = s.body.subst(mapOf(s.v to nv))))
                    }
                    done.any { alphaEq(it.stmt, s) } -> {}
                    else -> done += h
                }
            }
            val g = g0.copy(vars = vars, hyps = done)
            if (!isClosed(g)) out += g
        }
        return state.copy(goals = out, nextHyp = next)
    }

    private fun isClosed(g: Goal): Boolean {
        val t = g.target
        if (t is Op && t.op == "true") return true
        if (t is Op && t.op == "eq" && alphaEq(t.args[0], t.args[1])) return true
        if (t is Op && t.op == "iff" && alphaEq(t.args[0], t.args[1])) return true
        if (g.hyps.any { (it.stmt as? Op)?.op == "false" }) return true
        return g.hyps.any { alphaEq(it.stmt, t) }
    }
}

/** What the engine needs to know about the level being played. */
data class LevelContext(val computeUnlocked: Boolean, val offers: List<Term>)

enum class FactKind { Axiom, Definition, Field, Theorem, Lecture }

data class Fact(
    val id: String,
    val name: String,
    val german: String,
    val ref: String,
    val statement: Term,
    val kind: FactKind,
    val nameDe: String = "",
) {
    val localName get() = Wording.tr(name, nameDe)
}
