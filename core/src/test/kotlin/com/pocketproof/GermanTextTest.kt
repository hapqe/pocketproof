package com.pocketproof

import com.pocketproof.content.*
import com.pocketproof.engine.*
import org.junit.After
import org.junit.Assert.*
import org.junit.Test

class GermanTextTest {
    @After fun english() { Wording.german = false }

    private val facts get() = Course.levels.flatMap { Course.toolsFor(it) + listOfNotNull(Course.rewardOf(it)) }.distinctBy { it.id }

    @Test fun `every world, level and tool has German text`() {
        val missing = Course.worlds.filter { it.titleDe.isEmpty() || it.topicDe.isEmpty() || it.introDe.isEmpty() }.map { "world ${it.id}" } +
            Course.levels.filter { it.titleDe.isEmpty() || it.storyDe.isEmpty() || (it.hint.isNotEmpty() && it.hintDe.isEmpty()) }.map { "level ${it.id}" } +
            facts.filter { it.nameDe.isEmpty() }.map { "fact ${it.id}" }
        assertTrue(missing.joinToString("\n", "\n"), missing.isEmpty())
    }

    @Test fun `no German entry is left over from a renamed id`() {
        assertEquals(emptySet<String>(), GermanText.levels.keys - Course.levels.map { it.id }.toSet())
        GermanText.facts.keys.forEach { Course.fact(it) } // Throws for an id that no longer exists.
        assertEquals(emptySet<String>(), GermanText.worlds.keys - Course.worlds.map { it.id }.toSet())
    }

    @Test fun `tools quoted in a German story exist under that name`() {
        val notTools = setOf("dafür genügt es zu zeigen, dass", "es existiert", "A ∧ B", "injektiv genau dann, wenn ker f = {o}")
        val names = facts.map { it.nameDe }.toSet()
        val quoted = Regex("„([^“]*)“")
        val unknown = Course.levels.flatMap { l ->
            quoted.findAll(l.storyDe + l.hintDe).map { it.groupValues[1] }.filter { it !in names && it !in notTools }.map { "${l.id}: $it" }.toList()
        }
        assertTrue(unknown.joinToString("\n", "\n"), unknown.isEmpty())
    }

    @Test fun `course text contains no emoji in either language`() {
        val texts = Course.worlds.flatMap { listOf(it.title, it.intro, it.titleDe, it.introDe) } +
            Course.levels.flatMap { listOf(it.title, it.story, it.hint, it.titleDe, it.storyDe, it.hintDe) } +
            facts.flatMap { listOf(it.name, it.nameDe) }
        val offending = texts.filter { t -> t.any { it == '⚡' || it == '⇄' || it.isSurrogate() && it != '\uD835' && it !in '\uDC00'..'\uDFFF' } }
        assertTrue(offending.joinToString("\n", "\n"), offending.isEmpty())
    }

    @Test fun `every level still solves and renders in German`() {
        Wording.german = true
        for (level in Course.levels) {
            val (end, steps) = Script.run(level)
            assertTrue(level.id, end.solved)
            assertTrue(level.id, steps.all { it.text.isNotBlank() })
            assertTrue(level.id, render(level.statement).text.isNotBlank())
        }
    }

    @Test fun `statements use German words when German is selected`() {
        val stmt = parse("Lin(f) -> Inj(f) -> Indep(m) -> Basis(m)")
        assertFalse(render(stmt).text.contains("injektiv"))
        Wording.german = true
        val text = render(stmt).text
        assertTrue(text, text.contains("injektiv") && text.contains("lin. unabh.") && text.contains("ist eine Basis"))
    }
}
