package com.pocketproof.content

import com.pocketproof.engine.*

data class Level(
    val id: String,
    val title: String,
    val statement: Term,
    val story: String,
    val solution: List<String>,
    val grants: List<Fact>,
    val offers: List<Term>,
    val german: String,
    val ref: String,
    val boss: Boolean,
    val hint: String,
    val isTheorem: Boolean,
    val worldId: String,
    val env: SortEnv,
    val titleDe: String = "",
    val storyDe: String = "",
    val hintDe: String = "",
) {
    val par get() = solution.size
    val localTitle get() = Wording.tr(title, titleDe)
    val localStory get() = Wording.tr(story, storyDe)
    val localHint get() = Wording.tr(hint, hintDe)
}

data class World(
    val id: String,
    val title: String,
    val topic: String,
    val ref: String,
    val intro: String,
    val colorIndex: Int,
    val levels: List<Level>,
    val titleDe: String = "",
    val topicDe: String = "",
    val refDe: String = "",
    val introDe: String = "",
) {
    val localTitle get() = Wording.tr(title, titleDe)
    val localTopic get() = Wording.tr(topic, topicDe)
    val localRef get() = Wording.tr(ref, refDe)
    val localIntro get() = Wording.tr(intro, introDe)
}

class Registry {
    val facts = linkedMapOf<String, Fact>()
    fun get(id: String) = facts[id] ?: error("Unknown fact $id")
}

class WorldBuilder(
    private val registry: Registry,
    private val id: String,
    private val env: SortEnv,
) {
    val levels = mutableListOf<Level>()

    private fun add(kind: FactKind, id: String, name: String, stmt: String, german: String, ref: String) {
        check(id !in registry.facts) { "Duplicate fact $id" }
        registry.facts[id] = Fact(id, name, german, ref, parse(stmt, env), kind, GermanText.facts[id].orEmpty())
    }

    fun axiom(id: String, name: String, stmt: String, german: String = "", ref: String = "") = add(FactKind.Axiom, id, name, stmt, german, ref)
    fun definition(id: String, name: String, stmt: String, german: String = "", ref: String = "") = add(FactKind.Definition, id, name, stmt, german, ref)
    fun field(id: String, name: String, stmt: String) = add(FactKind.Field, id, name, stmt, "Körperrechnung", "§1.10")
    /** A result the course proves by means this game cannot express; handed over as a black box. */
    fun lecture(id: String, name: String, stmt: String, german: String = "", ref: String = "") = add(FactKind.Lecture, id, name, stmt, german, ref)

    fun level(
        id: String,
        title: String,
        stmt: String,
        story: String,
        solution: String,
        grants: String = "",
        offers: String = "",
        german: String = "",
        ref: String = "",
        boss: Boolean = false,
        hint: String = "",
        theorem: Boolean = true,
    ) {
        val statement = parse(stmt, env)
        val granted = grants.split(",").map { it.trim() }.filter { it.isNotEmpty() }.map { registry.get(it) }
        val offerTerms = offers.split(";").map { it.trim() }.filter { it.isNotEmpty() }.map { parse(it, env) }
        val steps = solution.split(";").map { it.trim() }.filter { it.isNotEmpty() }
        val de = GermanText.levels[id]
        levels += Level(id, title, statement, story, steps, granted, offerTerms, german, ref, boss, hint, theorem, this.id, env, de?.title.orEmpty(), de?.story.orEmpty(), de?.hint.orEmpty())
        if (theorem) {
            check(id !in registry.facts) { "Duplicate fact $id" }
            val nameDe = de?.title.orEmpty().removePrefix("Letzter Endgegner: ").removePrefix("Endgegner: ")
            registry.facts[id] = Fact(id, title.removePrefix("FINAL BOSS: ").removePrefix("BOSS: "), german, ref, statement, FactKind.Theorem, nameDe)
        }
    }
}

object Course {
    private val registry = Registry()
    val worlds: List<World> = buildWorlds(registry)
    val levels: List<Level> = worlds.flatMap { it.levels }
    private val indexById = levels.withIndex().associate { (i, l) -> l.id to i }

    /** Compute becomes available once commutativity has been proved, because it relies on it. */
    private const val COMPUTE_AFTER = "add_comm"

    fun indexOf(level: Level) = indexById.getValue(level.id)
    fun level(id: String) = levels[indexById.getValue(id)]
    fun world(id: String) = worlds.first { it.id == id }

    fun context(level: Level) = LevelContext(indexOf(level) > indexById.getValue(COMPUTE_AFTER), level.offers)

    /** Everything usable in a level: tools granted so far and theorems of earlier levels. Newest first. */
    fun toolsFor(level: Level): List<Fact> {
        val upto = indexOf(level)
        val out = linkedMapOf<String, Fact>()
        for (i in 0..upto) {
            val l = levels[i]
            l.grants.forEach { out[it.id] = it }
            if (i < upto && l.isTheorem) out[l.id] = registry.get(l.id)
        }
        return out.values.reversed()
    }

    fun fact(id: String) = registry.get(id)

    /** The tool a level awards when completed, if any. */
    fun rewardOf(level: Level): Fact? = if (level.isTheorem) registry.get(level.id) else null

    fun unlocksCompute(level: Level) = level.id == COMPUTE_AFTER

    fun start(level: Level): ProofState = Engine.start(level.statement, context(level))
}
