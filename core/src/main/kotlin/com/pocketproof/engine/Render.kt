package com.pocketproof.engine

enum class Style { Var, Sub, Sup, Keyword }

data class StyleRange(val start: Int, val end: Int, val style: Style)

/** The character range a subterm occupies, so taps and drops can be resolved to that subterm. */
data class TermSpan(val path: Path, val start: Int, val end: Int)

data class Rendered(val text: String, val styles: List<StyleRange>, val spans: List<TermSpan>) {
    fun spanFor(path: Path): TermSpan? = spans.firstOrNull { it.path == path }
}

private class Out {
    val sb = StringBuilder()
    val styles = mutableListOf<StyleRange>()
    val spans = mutableListOf<TermSpan>()

    fun text(s: String) { sb.append(s) }

    fun styled(s: String, style: Style) {
        val start = sb.length
        sb.append(s)
        styles += StyleRange(start, sb.length, style)
    }

    inline fun span(path: Path, body: () -> Unit) {
        val start = sb.length
        body()
        spans += TermSpan(path, start, sb.length)
    }
}

/** Precedence levels: higher binds tighter. */
private const val P_ANY = 0
private const val P_IMP = 2
private const val P_OR = 3
private const val P_AND = 4
private const val P_NOT = 5
private const val P_REL = 6
private const val P_ADD = 7
private const val P_MUL = 8
private const val P_COMP = 9
private const val P_ATOM = 10

private fun prec(t: Term): Int = when (t) {
    is Sym -> P_ATOM
    is Quant -> P_ANY
    is Op -> when (t.op) {
        "iff" -> 1
        "imp" -> P_IMP
        "or" -> P_OR
        "and" -> P_AND
        "not" -> if ((t.args[0] as? Op)?.op in setOf("eq", "mem")) P_REL else P_NOT
        "eq", "mem", "sub", "le" -> P_REL
        "+", "cap" -> P_ADD
        "*" -> P_MUL
        "comp" -> P_COMP
        "neg" -> P_ADD
        "ker", "im", "nul", "dim", "rk", "defect", "det", "sgn" -> P_COMP
        else -> P_ATOM
    }
}

private val PREDICATE_SUFFIX = mapOf(
    "Lin" to " linear", "Inj" to " injective", "Surj" to " surjective", "Bij" to " bijective",
    "Inv" to " invertible", "Transp" to " a transposition", "Even" to " even", "Form" to " alternating multilinear",
)

private val PREDICATE_SUFFIX_DE = mapOf(
    "Lin" to " linear", "Inj" to " injektiv", "Surj" to " surjektiv", "Bij" to " bijektiv",
    "Inv" to " invertierbar", "Transp" to " eine Transposition", "Even" to " gerade", "Form" to " alternierend multilinear",
)

fun render(t: Term): Rendered {
    val out = Out()
    emit(out, t, emptyList(), P_ANY)
    return Rendered(out.sb.toString(), out.styles, out.spans)
}

private fun emit(o: Out, t: Term, path: Path, minPrec: Int) {
    val parens = prec(t) < minPrec
    o.span(path) {
        if (parens) o.text("(")
        emitInner(o, t, path)
        if (parens) o.text(")")
    }
}

private fun emitInner(o: Out, t: Term, path: Path) {
    when (t) {
        is Sym -> emitName(o, t.name)
        is Quant -> emitQuant(o, t, path)
        is Op -> emitOp(o, t, path)
    }
}

/** A variable name; digits become subscripts, as in e₁. */
private fun emitName(o: Out, name: String) {
    val letters = name.takeWhile { !it.isDigit() }
    val digits = name.drop(letters.length).takeWhile { it.isDigit() }
    val primes = name.drop(letters.length + digits.length)
    o.styled(letters, Style.Var)
    if (digits.isNotEmpty()) o.styled(digits, Style.Sub)
    if (primes.isNotEmpty()) o.text(primes)
}

private fun emitQuant(o: Out, t: Quant, path: Path) {
    o.styled(if (t.kind == QKind.Forall) "∀ " else "∃ ", Style.Keyword)
    var q = t
    var p = path
    while (true) {
        emitName(o, q.v.name)
        val next = q.body
        if (next is Quant && next.kind == q.kind) {
            o.text(" ")
            q = next
            p = p + 0
        } else break
    }
    o.text(", ")
    emit(o, q.body, p + 0, P_ANY)
}

private fun binary(o: Out, t: Op, path: Path, symbol: String, left: Int, right: Int) {
    emit(o, t.args[0], path + 0, left)
    o.text(symbol)
    emit(o, t.args[1], path + 1, right)
}

private fun emitOp(o: Out, t: Op, path: Path) {
    when (t.op) {
        "iff" -> binary(o, t, path, "  ⇔  ", P_IMP, P_IMP)
        "imp" -> binary(o, t, path, "  ⇒  ", P_OR, P_IMP)
        "or" -> binary(o, t, path, "  ∨  ", P_AND, P_OR)
        "and" -> binary(o, t, path, "  ∧  ", P_NOT, P_AND)
        "not" -> {
            val inner = t.args[0] as? Op
            when (inner?.op) {
                "eq" -> {
                    o.span(path + 0) {
                        emit(o, inner.args[0], path + 0 + 0, P_REL + 1)
                        o.text(" ≠ ")
                        emit(o, inner.args[1], path + 0 + 1, P_REL + 1)
                    }
                }
                "mem" -> {
                    o.span(path + 0) {
                        emit(o, inner.args[0], path + 0 + 0, P_REL + 1)
                        o.text(" ∉ ")
                        emit(o, inner.args[1], path + 0 + 1, P_REL + 1)
                    }
                }
                else -> {
                    o.styled("¬", Style.Keyword)
                    emit(o, t.args[0], path + 0, P_NOT)
                }
            }
        }
        "eq" -> binary(o, t, path, " = ", P_REL + 1, P_REL + 1)
        "mem" -> binary(o, t, path, " ∈ ", P_REL + 1, P_REL + 1)
        "sub" -> binary(o, t, path, " ⊂ ", P_REL + 1, P_REL + 1)
        "le" -> binary(o, t, path, " ≤ ", P_REL + 1, P_REL + 1)
        // Sums keep their brackets: associativity is a tool the player uses, so it must stay visible.
        "+" -> binary(o, t, path, " + ", P_ADD + 1, P_ADD + 1)
        "cap" -> binary(o, t, path, " ∩ ", P_ADD + 1, P_ADD + 1)
        "*" -> binary(o, t, path, "·", P_MUL + 1, P_MUL + 1)
        "comp" -> binary(o, t, path, " ∘ ", P_ATOM, P_COMP)
        "neg" -> {
            o.text("−")
            emit(o, t.args[0], path + 0, P_ATOM)
        }
        "inv" -> {
            emit(o, t.args[0], path + 0, P_ATOM)
            o.styled("−1", Style.Sup)
        }
        "T" -> {
            emit(o, t.args[0], path + 0, P_ATOM)
            o.styled("T", Style.Sup)
        }
        "app" -> call(o, t.args[0], t.args.drop(1), path)
        "idx" -> {
            emit(o, t.args[0], path + 0, P_ATOM)
            o.span(path + 1) { subscript(o, t.args[1]) }
        }
        "o" -> o.text("o")
        "0" -> o.text("0")
        "1" -> o.text("1")
        "zc" -> o.text("𝟎")
        "E" -> o.text("E")
        "idp" -> o.text("id")
        "V" -> o.styled("V", Style.Var)
        "trivial" -> o.text("{o}")
        "true" -> o.text("⊤")
        "false" -> o.text("⊥")
        "n0" -> o.text("0")
        "dimV" -> { o.text("dim "); o.styled("V", Style.Var) }
        "dimW" -> { o.text("dim "); o.styled("W", Style.Var) }
        "isSub" -> {
            emit(o, t.args[0], path + 0, P_ATOM)
            o.text(" ≤ ")
            o.styled("V", Style.Var)
        }
        in PREDICATE_SUFFIX -> {
            emit(o, t.args[0], path + 0, P_ATOM)
            o.text(Wording.tr(PREDICATE_SUFFIX.getValue(t.op), PREDICATE_SUFFIX_DE.getValue(t.op)))
        }
        "Indep" -> familyWord(o, t, path, Wording.tr(" lin. indep.", " lin. unabh."))
        "Gen" -> familyWord(o, t, path, Wording.tr(" spans V", " erzeugt V"))
        "Basis" -> familyWord(o, t, path, Wording.tr(" is a basis", " ist eine Basis"))
        "Indep1" -> {
            o.text("(")
            emit(o, t.args[0], path + 0, P_ANY)
            o.text(Wording.tr(") lin. indep.", ") lin. unabh."))
        }
        "Indep2" -> pair(o, t, path, Wording.tr(" lin. indep.", " lin. unabh."))
        "Gen2" -> pair(o, t, path, Wording.tr(" spans V", " erzeugen V"))
        "lc" -> {
            o.text("Σ ")
            emit(o, t.args[0], path + 0, P_ATOM)
            o.styled("i", Style.Sub)
            o.text("·")
            emit(o, t.args[1], path + 1, P_ATOM)
            o.styled("i", Style.Sub)
        }
        "span" -> {
            o.text("[")
            emit(o, t.args[0], path + 0, P_ANY)
            o.text("]")
        }
        "line" -> {
            o.text("[")
            emit(o, t.args[0], path + 0, P_ANY)
            o.text("]")
        }
        "ker", "im", "nul", "dim", "rk", "defect", "det", "sgn", "card" -> {
            val word = when (t.op) {
                "ker", "nul" -> "ker"
                "im" -> "im"
                "dim" -> "dim"
                "rk" -> "rg"
                "defect" -> "def"
                "det" -> "det"
                "sgn" -> "sgn"
                else -> "#"
            }
            o.text(word)
            // A bare name reads as "det A"; anything larger keeps its brackets so "det(A⁻¹)" is never mistaken for "(det A)⁻¹".
            if (t.args[0] is Sym) {
                if (t.op != "card") o.text(" ")
                emit(o, t.args[0], path + 0, P_ATOM)
            } else {
                o.text("(")
                emit(o, t.args[0], path + 0, P_ANY)
                o.text(")")
            }
        }
        "preimg" -> {
            emit(o, t.args[0], path + 0, P_ATOM)
            o.styled("−1", Style.Sup)
            o.text("(")
            emit(o, t.args[1], path + 1, P_ANY)
            o.text(")")
        }
        "delta" -> {
            o.styled("δ", Style.Var)
            o.span(path + 0) { subscript(o, t.args[0]) }
        }
        "ext" -> {
            emit(o, t.args[0], path + 0, P_ATOM)
            o.text("↑")
            emit(o, t.args[1], path + 1, P_ATOM)
        }
        "restrict" -> {
            emit(o, t.args[0], path + 0, P_ATOM)
            o.text("|")
            emit(o, t.args[1], path + 1, P_ATOM)
        }
        "mapFam" -> {
            emit(o, t.args[0], path + 0, P_ATOM)
            o.text("∘")
            emit(o, t.args[1], path + 1, P_ATOM)
        }
        "D" -> {
            o.text("Δ(")
            emit(o, t.args[0], path + 0, P_ANY)
            o.text(", ")
            emit(o, t.args[1], path + 1, P_ANY)
            o.text(")")
        }
        "Dl" -> {
            o.text("(X ↦ det ")
            emit(o, t.args[0], path + 0, P_ATOM)
            o.text("X)")
        }
        "evalF" -> call(o, t.args[0], t.args.drop(1), path)
        else -> error("No renderer for ${t.op}")
    }
}

private fun subscript(o: Out, t: Term) {
    val text = when (t) {
        is Sym -> t.name
        else -> render(t).text
    }
    o.styled(text, Style.Sub)
}

private fun call(o: Out, fn: Term, args: List<Term>, path: Path) {
    emit(o, fn, path + 0, P_ATOM)
    o.text("(")
    args.forEachIndexed { i, a ->
        if (i > 0) o.text(", ")
        emit(o, a, path + (i + 1), P_ANY)
    }
    o.text(")")
}

private fun familyWord(o: Out, t: Op, path: Path, word: String) {
    emit(o, t.args[0], path + 0, P_ATOM)
    o.text(word)
}

private fun pair(o: Out, t: Op, path: Path, word: String) {
    o.text("(")
    emit(o, t.args[0], path + 0, P_ANY)
    o.text(", ")
    emit(o, t.args[1], path + 1, P_ANY)
    o.text(")")
    o.text(word)
}
