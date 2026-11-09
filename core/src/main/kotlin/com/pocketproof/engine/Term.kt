package com.pocketproof.engine

/** The sorts of objects a proof can talk about. Matching respects sorts, so `a + b` on vectors never matches scalars. */
enum class Sort { Scalar, Vec, Coef, Fam, Set, Map, Mat, Perm, Nat, Index, Form, Prop }

sealed interface Term

/** A variable: rigid when it lives in a goal, schematic when it lives in a tool. */
data class Sym(val name: String, val sort: Sort) : Term {
    override fun toString() = name
}

/** An operator application. Constants such as `o` or `E` are zero-argument operators. */
data class Op(val op: String, val args: List<Term> = emptyList()) : Term

enum class QKind { Forall, Exists }

data class Quant(val kind: QKind, val v: Sym, val body: Term) : Term

typealias Path = List<Int>

private val PROP_OPS = setOf(
    "eq", "mem", "sub", "le", "and", "or", "imp", "iff", "not", "false", "true",
    "isSub", "Lin", "Indep", "Indep1", "Indep2", "Gen", "Gen2", "Basis", "Inj", "Surj", "Bij",
    "Inv", "Transp", "Even", "Form",
)

fun sortOf(t: Term): Sort = when (t) {
    is Sym -> t.sort
    is Quant -> Sort.Prop
    is Op -> when (t.op) {
        in PROP_OPS -> Sort.Prop
        "o", "lc", "app", "mapSym" -> Sort.Vec
        "0", "1", "det", "sgn", "D", "evalF" -> Sort.Scalar
        "zc", "delta", "ext" -> Sort.Coef
        "E", "T" -> Sort.Mat
        "idp" -> Sort.Perm
        "V", "trivial", "span", "ker", "im", "cap", "line", "preimg", "nul" -> Sort.Set
        "dim", "card", "rk", "defect", "dimV", "dimW", "n0" -> Sort.Nat
        "mapFam", "restrict" -> Sort.Fam
        "Dl" -> Sort.Form
        "+", "neg", "inv", "comp" -> sortOf(t.args[0])
        "*" -> sortOf(t.args[1])
        "idx" -> if (sortOf(t.args[0]) == Sort.Coef) Sort.Scalar else Sort.Vec
        else -> error("Unknown operator ${t.op}")
    }
}

val Term.children: List<Term>
    get() = when (this) {
        is Sym -> emptyList()
        is Op -> args
        is Quant -> listOf(body)
    }

fun Term.at(path: Path): Term = path.fold(this) { t, i -> t.children[i] }

fun Term.replaceAt(path: Path, with: Term): Term {
    if (path.isEmpty()) return with
    val i = path[0]
    val rest = path.drop(1)
    return when (this) {
        is Sym -> error("No children")
        is Op -> copy(args = args.mapIndexed { k, a -> if (k == i) a.replaceAt(rest, with) else a })
        is Quant -> copy(body = body.replaceAt(rest, with))
    }
}

/** Pre-order walk over every subterm: outermost and leftmost first. */
fun Term.allPaths(prefix: Path = emptyList(), out: MutableList<Path> = mutableListOf()): List<Path> {
    out += prefix
    children.forEachIndexed { i, c -> c.allPaths(prefix + i, out) }
    return out
}

fun Term.freeSyms(bound: Set<Sym> = emptySet(), out: MutableSet<Sym> = linkedSetOf()): Set<Sym> {
    when (this) {
        is Sym -> if (this !in bound) out += this
        is Op -> args.forEach { it.freeSyms(bound, out) }
        is Quant -> body.freeSyms(bound + v, out)
    }
    return out
}

fun Term.mentions(s: Sym): Boolean = s in freeSyms()

/** Capture-avoiding substitution. */
fun Term.subst(map: Map<Sym, Term>): Term {
    if (map.isEmpty()) return this
    return when (this) {
        is Sym -> map[this] ?: this
        is Op -> if (args.isEmpty()) this else copy(args = args.map { it.subst(map) })
        is Quant -> {
            val inner = map - v
            val incoming = inner.filterKeys { body.mentions(it) }.values.flatMap { it.freeSyms() }.toSet()
            if (v in incoming) {
                val used = incoming.map { it.name }.toSet() + body.freeSyms().map { it.name }
                val nv = v.copy(name = freshName(v.name, used))
                copy(v = nv, body = body.subst(inner + (v to nv)))
            } else {
                copy(body = body.subst(inner))
            }
        }
    }
}

fun freshName(base: String, used: Set<String>): String {
    var candidate = base
    while (candidate in used) candidate += "′"
    return candidate
}

/** A canonical key: two terms have equal keys exactly when they are alpha-equivalent. */
fun Term.key(env: Map<Sym, Int> = emptyMap()): String = when (this) {
    is Sym -> env[this]?.let { "#$it" } ?: "$name:${sort.name}"
    is Op -> if (args.isEmpty()) op else "$op(${args.joinToString(",") { it.key(env) }})"
    is Quant -> {
        val depth = env.size
        "${kind.name}#$depth:${v.sort.name}.${body.key(env + (v to depth))}"
    }
}

fun alphaEq(a: Term, b: Term) = a.key() == b.key()

/**
 * First-order matching. Only symbols in [schematic] may be bound.
 * Returns the extended substitution, or null when the pattern does not fit.
 */
fun match(
    pattern: Term,
    subject: Term,
    schematic: Set<Sym>,
    seed: Map<Sym, Term> = emptyMap(),
): Map<Sym, Term>? {
    val binding = seed.toMutableMap()
    fun go(p: Term, t: Term, local: Set<Sym>): Boolean = when {
        p is Sym && p in schematic -> {
            val prior = binding[p]
            when {
                prior != null -> alphaEq(prior, t)
                p.sort != sortOf(t) -> false
                t.freeSyms().any { it in local } -> false
                else -> { binding[p] = t; true }
            }
        }
        p is Sym -> p == t
        p is Op && t is Op ->
            p.op == t.op && p.args.size == t.args.size &&
                p.args.indices.all { go(p.args[it], t.args[it], local) }
        p is Quant && t is Quant ->
            p.kind == t.kind && p.v.sort == t.v.sort &&
                go(p.body.subst(mapOf(p.v to t.v)), t.body, local + t.v)
        else -> false
    }
    return if (go(pattern, subject, emptySet())) binding else null
}

// Constructors used by the engine.
val FALSE: Term = Op("false")
fun imp(a: Term, b: Term): Term = Op("imp", listOf(a, b))
fun not(a: Term): Term = Op("not", listOf(a))
