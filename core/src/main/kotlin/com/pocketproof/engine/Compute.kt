package com.pocketproof.engine

/**
 * The "Compute" power: decides equalities that hold in every module over a field, by comparing normal forms.
 * Scalars become polynomials over atoms; vectors become linear forms over atoms with polynomial coefficients.
 * `x⁻¹` cancels against `x` only when `x ≠ 0` is among the hypotheses, which keeps the check sound.
 */
object Compute {
    /** A monomial is a sorted list of atom keys. */
    private fun addP(a: Map<List<String>, Int>, b: Map<List<String>, Int>): Map<List<String>, Int> {
        val out = a.toMutableMap()
        for ((m, c) in b) {
            val sum = (out[m] ?: 0) + c
            if (sum == 0) out.remove(m) else out[m] = sum
        }
        return out
    }

    private fun scaleP(a: Map<List<String>, Int>, k: Int) = if (k == 0) emptyMap() else a.mapValues { it.value * k }

    private fun mulP(
        a: Map<List<String>, Int>,
        b: Map<List<String>, Int>,
        nonzero: Set<String>,
    ): Map<List<String>, Int> {
        var out: Map<List<String>, Int> = emptyMap()
        for ((m1, c1) in a) for ((m2, c2) in b) {
            out = addP(out, mapOf(reduce(m1 + m2, nonzero) to c1 * c2))
        }
        return out
    }

    private fun reduce(mono: List<String>, nonzero: Set<String>): List<String> {
        val items = mono.toMutableList()
        for (x in nonzero) {
            val inverse = "inv($x)"
            while (x in items && inverse in items) {
                items.remove(x)
                items.remove(inverse)
            }
        }
        return items.sorted()
    }

    private val ZERO: Map<List<String>, Int> = emptyMap()
    private val ONE: Map<List<String>, Int> = mapOf(emptyList<String>() to 1)
    private fun atomP(k: String): Map<List<String>, Int> = mapOf(listOf(k) to 1)

    private fun scalar(t: Term, nz: Set<String>): Map<List<String>, Int> {
        if (t is Op) when (t.op) {
            "0" -> return ZERO
            "1" -> return ONE
            "+" -> return addP(scalar(t.args[0], nz), scalar(t.args[1], nz))
            "neg" -> return scaleP(scalar(t.args[0], nz), -1)
            "*" -> return mulP(scalar(t.args[0], nz), scalar(t.args[1], nz), nz)
        }
        return atomP(t.key())
    }

    private fun linear(t: Term, nz: Set<String>): Map<String, Map<List<String>, Int>> {
        if (t is Op) when (t.op) {
            "o", "zc" -> return emptyMap()
            "+" -> return merge(linear(t.args[0], nz), linear(t.args[1], nz))
            "neg" -> return linear(t.args[0], nz).mapValues { scaleP(it.value, -1) }
            "*" -> if (sortOf(t.args[0]) == Sort.Scalar) {
                val k = scalar(t.args[0], nz)
                return linear(t.args[1], nz)
                    .mapValues { mulP(k, it.value, nz) }
                    .filterValues { it.isNotEmpty() }
            }
        }
        return mapOf(t.key() to ONE)
    }

    private fun merge(
        a: Map<String, Map<List<String>, Int>>,
        b: Map<String, Map<List<String>, Int>>,
    ): Map<String, Map<List<String>, Int>> {
        val out = a.toMutableMap()
        for ((k, p) in b) {
            val sum = addP(out[k] ?: ZERO, p)
            if (sum.isEmpty()) out.remove(k) else out[k] = sum
        }
        return out
    }

    private fun difference(eq: Op, nz: Set<String>): Any? {
        val (l, r) = eq.args
        return when (sortOf(l)) {
            Sort.Scalar -> addP(scalar(l, nz), scaleP(scalar(r, nz), -1))
            Sort.Vec, Sort.Coef -> merge(linear(l, nz), linear(r, nz).mapValues { scaleP(it.value, -1) })
            else -> null
        }
    }

    private fun negated(d: Any): Any = when {
        (d as Map<*, *>).isEmpty() -> d
        d.keys.first() is List<*> -> {
            @Suppress("UNCHECKED_CAST")
            scaleP(d as Map<List<String>, Int>, -1)
        }
        else -> {
            @Suppress("UNCHECKED_CAST")
            (d as Map<String, Map<List<String>, Int>>).mapValues { scaleP(it.value, -1) }
        }
    }

    fun closes(goal: Goal): Boolean {
        val target = goal.target as? Op ?: return false
        if (target.op != "eq") return false
        val nonzero = goal.hyps.mapNotNull { h ->
            val n = h.stmt as? Op ?: return@mapNotNull null
            if (n.op != "not") return@mapNotNull null
            val eq = n.args[0] as? Op ?: return@mapNotNull null
            if (eq.op == "eq" && eq.args[1] == Op("0")) eq.args[0].key() else null
        }.toSet()
        val diff = difference(target, nonzero) ?: return false
        if ((diff as Map<*, *>).isEmpty()) return true
        val minus = negated(diff)
        return goal.hyps.any { h ->
            val eq = h.stmt as? Op
            if (eq == null || eq.op != "eq" || sortOf(eq.args[0]) != sortOf(target.args[0])) false
            else difference(eq, nonzero).let { it == diff || it == minus }
        }
    }
}
