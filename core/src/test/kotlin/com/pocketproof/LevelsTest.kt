package com.pocketproof

import com.pocketproof.content.*
import com.pocketproof.engine.*
import org.junit.Assert.*
import org.junit.Test

class LevelsTest {
    @Test fun `every level can be solved with only the tools available at that point`() {
        val failures = mutableListOf<String>()
        for (level in Course.levels) {
            try {
                val (end, _) = Script.run(level)
                if (!end.solved) failures += "${level.id}: unfinished, ${end.goals.map { g -> render(g.target).text + " given " + g.hyps.map { it.id + ":" + render(it.stmt).text } }}"
            } catch (e: Throwable) {
                failures += "${level.id}: " + (e.message ?: e.toString())
            }
        }
        println("Levels: ${Course.levels.size}, failing: ${failures.size}")
        assertTrue(failures.joinToString("\n", "\n"), failures.isEmpty())
    }

    @Test fun `no level is solved before it starts`() {
        for (level in Course.levels) assertFalse(level.id, Course.start(level).solved)
    }

    @Test fun `a level's own theorem is not available inside that level`() {
        for (level in Course.levels) assertTrue(level.id, Course.toolsFor(level).none { it.id == level.id })
    }

    @Test fun `every statement renders`() {
        for (level in Course.levels) assertTrue(render(level.statement).text.isNotBlank())
    }
}

class EngineRejectsBadMovesTest {
    private val noCompute = LevelContext(computeUnlocked = false, offers = emptyList())
    private val withCompute = LevelContext(computeUnlocked = true, offers = emptyList())

    private fun fact(s: String) = Source.Tool(Fact("t", "t", "", "", parse(s), FactKind.Axiom))
    private fun rewrites(goal: String, rule: String, flipped: Boolean = false): List<Action.Rewrite> {
        val st = Engine.start(parse(goal), noCompute)
        return Engine.actions(st, fact(rule), flipped).filterIsInstance<Action.Rewrite>()
    }
    private fun computes(goal: String, hyps: List<String> = emptyList()): Boolean {
        val g = Goal(emptyList(), hyps.mapIndexed { i, h -> Hyp("h$i", parse(h)) }, parse(goal))
        return Compute.closes(g)
    }

    @Test fun `a rule only matches where its shape fits`() {
        assertTrue(rewrites("a + b = b", "x*(a + b) = x*a + x*b").isEmpty())
        assertEquals(1, rewrites("x*(a + b) = b", "x*(a + b) = x*a + x*b").size)
    }

    @Test fun `vector rules never match scalar terms`() {
        assertTrue(rewrites("x + 0 = x", "a + o = a").isEmpty())
    }

    @Test fun `repeated pattern variables must agree`() {
        assertTrue(rewrites("D(a,b) = 0", "D(a,a) = 0").isEmpty())
        assertEquals(1, rewrites("D(a,a) = x", "D(a,a) = 0").size)
    }

    @Test fun `compute accepts true identities and refuses false ones`() {
        assertTrue(computes("(x + y)*a = y*a + x*a"))
        assertTrue(computes("a + (-1)*a = o"))
        assertFalse(computes("x*a = a"))
        assertFalse(computes("a + b = a"))
        assertFalse(computes("x*y = x"))
    }

    @Test fun `compute cancels an inverse only when non-zero is known`() {
        assertFalse(computes("x^-1*x = 1"))
        assertTrue(computes("x^-1*x = 1", listOf("x != 0")))
        assertFalse(computes("y^-1*y = 1", listOf("x != 0")))
    }

    @Test fun `compute uses a matching hypothesis but not an unrelated one`() {
        assertTrue(computes("a = b", listOf("a + (-b) = o")))
        assertFalse(computes("a = b", listOf("a + b = o")))
    }

    @Test fun `tool variables stay separate from goal variables`() {
        val st = Engine.start(parse("a = b"), noCompute)
        val a = Engine.actions(st, fact("(-a) + (a + b) = b"), true)
            .filterIsInstance<Action.Rewrite>().first { it.path == listOf(0) }
        val pick = a.unbound.single()
        val done = Engine.perform(st, a, mapOf(pick to parse("u")), noCompute)
        assertEquals("(−u) + (u + a) = b", render(done.current.target).text)
    }

    @Test fun `unfolding a definition does not capture a goal variable`() {
        val rule = fact("b in line(u) <-> exists x, b = x*u")
        val st = Engine.start(parse("x*a in line(a)"), noCompute)
        val a = Engine.actions(st, rule, false).filterIsInstance<Action.Rewrite>().first()
        val done = Engine.perform(st, a, emptyMap(), noCompute)
        val q = done.current.target as Quant
        assertNotEquals("x", q.v.name)
    }

    @Test fun `a false goal is never closed by the automatic checks`() {
        assertFalse(Engine.start(parse("a = b"), withCompute).solved)
        assertFalse(Engine.start(parse("a = o -> a = b"), withCompute).solved)
    }

    @Test fun `a premise is required when a conditional rule is used`() {
        val st = Engine.start(parse("x^-1*x = y"), noCompute)
        val a = Engine.actions(st, fact("x != 0 -> x^-1*x = 1"), false).filterIsInstance<Action.Rewrite>().first()
        val done = Engine.perform(st, a, emptyMap(), noCompute)
        assertEquals(2, done.goals.size)
        assertEquals("x ≠ 0", render(done.goals[1].target).text)
    }
}
