package com.pocketproof.engine

/**
 * Parser for the small statement language that levels are written in, for example
 * `forall a b x, a in U -> a + x*b in U`.
 * The sort of a variable is decided by its first letter, as in the course notation.
 */
class SortEnv(private val overrides: Map<String, Sort> = emptyMap()) {
    fun sortOf(name: String): Sort {
        val base = name.trimEnd('′', '\'').trimEnd { it.isDigit() }
        overrides[base]?.let { return it }
        return DEFAULTS[base] ?: error("No sort known for variable '$name'")
    }

    companion object {
        private val DEFAULTS: Map<String, Sort> = buildMap {
            "a b u v w p q e".split(" ").forEach { put(it, Sort.Vec) }
            "x y z r s t".split(" ").forEach { put(it, Sort.Scalar) }
            "c d".split(" ").forEach { put(it, Sort.Coef) }
            "m n B".split(" ").forEach { put(it, Sort.Fam) }
            "U W T S".split(" ").forEach { put(it, Sort.Set) }
            "f g h".split(" ").forEach { put(it, Sort.Map) }
            "σ τ ρ π".split(" ").forEach { put(it, Sort.Perm) }
            "k l".split(" ").forEach { put(it, Sort.Nat) }
            "i j".split(" ").forEach { put(it, Sort.Index) }
            "J".split(" ").forEach { put(it, Sort.Set) }
            "A C X Y P Q".split(" ").forEach { put(it, Sort.Mat) }
            "F".split(" ").forEach { put(it, Sort.Form) }
        }
    }
}

/** Functions written `name(arg, ...)`, with the operator name they parse to. */
private val FUNCTIONS = mapOf(
    "Sub" to "isSub", "Lin" to "Lin", "Indep" to "Indep", "Indep1" to "Indep1", "Indep2" to "Indep2",
    "Gen" to "Gen", "Gen2" to "Gen2", "Basis" to "Basis", "Inj" to "Inj", "Surj" to "Surj", "Bij" to "Bij",
    "Inv" to "Inv", "Transp" to "Transp", "Even" to "Even", "Form" to "Form",
    "lc" to "lc", "span" to "span", "ker" to "ker", "im" to "im", "line" to "line", "preimg" to "preimg",
    "nul" to "nul", "delta" to "delta", "ext" to "ext", "restrict" to "restrict", "mapFam" to "mapFam",
    "det" to "det", "sgn" to "sgn", "D" to "D", "dim" to "dim", "card" to "card", "rk" to "rk", "defect" to "defect",
    "Dl" to "Dl", "evalF" to "evalF",
)

/** Bare words that are constants. */
private val CONSTANTS = mapOf(
    "o" to "o", "zc" to "zc", "E" to "E", "idp" to "idp", "V" to "V", "Z" to "trivial",
    "dimV" to "dimV", "dimW" to "dimW", "n0" to "n0", "False" to "false", "True" to "true",
)

class Parser(private val src: String, private val env: SortEnv = SortEnv()) {
    private val tokens = tokenize(src)
    private var pos = 0

    private fun peek(): String? = tokens.getOrNull(pos)
    private fun accept(tok: String): Boolean = if (peek() == tok) { pos++; true } else false
    private fun expect(tok: String) { check(accept(tok)) { "Expected '$tok' at token $pos in: $src" } }

    fun parse(): Term {
        val t = iff()
        check(pos == tokens.size) { "Trailing input at token $pos in: $src" }
        return t
    }

    private fun iff(): Term {
        val l = imp()
        return if (accept("<->")) Op("iff", listOf(l, imp())) else l
    }

    private fun imp(): Term {
        val l = or()
        return if (accept("->")) Op("imp", listOf(l, imp())) else l
    }

    private fun or(): Term {
        val l = and()
        return if (accept("|")) Op("or", listOf(l, or())) else l
    }

    private fun and(): Term {
        val l = notP()
        return if (accept("&")) Op("and", listOf(l, and())) else l
    }

    private fun notP(): Term = when {
        accept("~") -> not(notP())
        peek() == "forall" || peek() == "exists" -> quant()
        else -> relation()
    }

    private fun quant(): Term {
        val kind = if (peek() == "forall") QKind.Forall else QKind.Exists
        pos++
        val names = mutableListOf<String>()
        while (peek() != ",") names += tokens[pos++]
        expect(",")
        val body = iff()
        return names.foldRight(body) { n, acc -> Quant(kind, Sym(n, env.sortOf(n)), acc) }
    }

    private fun relation(): Term {
        val l = add()
        return when {
            accept("=") -> Op("eq", listOf(l, add()))
            accept("!=") -> not(Op("eq", listOf(l, add())))
            accept("in") -> Op("mem", listOf(l, add()))
            accept("notin") -> not(Op("mem", listOf(l, add())))
            accept("sub") -> Op("sub", listOf(l, add()))
            accept("<=") -> Op("le", listOf(l, add()))
            else -> l
        }
    }

    private fun add(): Term {
        var l = mul()
        while (true) {
            l = when {
                accept("+") -> Op("+", listOf(l, mul()))
                accept("-") -> Op("+", listOf(l, Op("neg", listOf(mul()))))
                accept("cap") -> Op("cap", listOf(l, mul()))
                else -> return l
            }
        }
    }

    private fun mul(): Term {
        var l = comp()
        while (accept("*")) l = Op("*", listOf(l, comp()))
        return l
    }

    private fun comp(): Term {
        val l = unary()
        return if (accept("∘")) Op("comp", listOf(l, comp())) else l
    }

    private fun unary(): Term = if (accept("-")) Op("neg", listOf(unary())) else postfix()

    private fun postfix(): Term {
        var t = primary()
        while (true) {
            t = when {
                accept("^-1") -> Op("inv", listOf(t))
                accept("^T") -> Op("T", listOf(t))
                peek() == "[" -> {
                    pos++
                    val idx = add()
                    expect("]")
                    Op("idx", listOf(t, idx))
                }
                peek() == "(" && t is Sym && t.sort == Sort.Map -> call(t)
                peek() == "(" && t is Op && t.op == "comp" -> call(t)
                peek() == "(" && t is Op && t.op == "inv" && sortOf(t) == Sort.Map -> call(t)
                else -> return t
            }
        }
    }

    private fun call(fn: Term): Term {
        expect("(")
        val arg = add()
        expect(")")
        return Op("app", listOf(fn, arg))
    }

    private fun primary(): Term {
        val tok = peek() ?: error("Unexpected end of input in: $src")
        pos++
        return when {
            tok == "(" -> iff().also { expect(")") }
            tok == "0" -> Op("0")
            tok == "1" -> Op("1")
            tok in FUNCTIONS && peek() == "(" -> {
                expect("(")
                val args = mutableListOf(add())
                while (accept(",")) args += add()
                expect(")")
                Op(FUNCTIONS.getValue(tok), args)
            }
            tok in CONSTANTS -> Op(CONSTANTS.getValue(tok))
            else -> Sym(tok, env.sortOf(tok))
        }
    }

    private fun tokenize(s: String): List<String> {
        val out = mutableListOf<String>()
        var i = 0
        val multi = listOf("<->", "->", "!=", "<=", "^-1", "^T")
        while (i < s.length) {
            val c = s[i]
            when {
                c.isWhitespace() -> i++
                multi.any { s.startsWith(it, i) } -> {
                    val m = multi.first { s.startsWith(it, i) }
                    out += m
                    i += m.length
                }
                c.isLetterOrDigit() -> {
                    var j = i
                    while (j < s.length && (s[j].isLetterOrDigit() || s[j] == '′' || s[j] == '\'')) j++
                    out += s.substring(i, j).replace('\'', '′')
                    i = j
                }
                else -> { out += c.toString(); i++ }
            }
        }
        return out
    }
}

fun parse(s: String, env: SortEnv = SortEnv()): Term = Parser(s, env).parse()
