package com.pocketproof.ui

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.pocketproof.content.Course
import com.pocketproof.content.Level
import com.pocketproof.content.World
import java.time.LocalDate

/** Saved progress. Stars are 1 to 3; a level that is absent has not been solved. */
class Progress(context: Context) {
    private val prefs = context.getSharedPreferences("progress", Context.MODE_PRIVATE)
    // Kept apart from progress so that resetting progress does not change the language.
    private val settings = context.getSharedPreferences("settings", Context.MODE_PRIVATE)
    val stars = mutableStateMapOf<String, Int>()
    var openAll by mutableStateOf(prefs.getBoolean("openAll", false))
        private set
    var streak by mutableIntStateOf(prefs.getInt("streak", 0))
        private set

    init {
        Language.set(if (settings.getString("language", "de") == "de") Lang.De else Lang.En)
        for (l in Course.levels) {
            val s = prefs.getInt("stars_${l.id}", 0)
            if (s > 0) stars[l.id] = s
        }
    }

    val solvedCount get() = stars.size
    val totalStars get() = stars.values.sum()
    val xp get() = stars.values.sumOf { 10 + it * 5 }

    fun isSolved(l: Level) = l.id in stars

    fun isUnlocked(l: Level): Boolean {
        if (openAll) return true
        val i = Course.indexOf(l)
        return i == 0 || Course.levels[i - 1].id in stars
    }

    fun worldUnlocked(w: World) = isUnlocked(w.levels.first())
    fun worldSolved(w: World) = w.levels.count { isSolved(it) }

    /** The level to continue with: the first unsolved one. */
    fun frontier(): Level? = Course.levels.firstOrNull { it.id !in stars }

    fun record(l: Level, earned: Int) {
        val best = maxOf(stars[l.id] ?: 0, earned)
        stars[l.id] = best
        prefs.edit().putInt("stars_${l.id}", best).apply()
        touchStreak()
    }

    private fun touchStreak() {
        val today = LocalDate.now()
        val last = prefs.getString("lastDay", null)?.let(LocalDate::parse)
        if (last == today) return
        streak = if (last == today.minusDays(1)) streak + 1 else 1
        prefs.edit().putString("lastDay", today.toString()).putInt("streak", streak).apply()
    }

    fun changeOpenAll(v: Boolean) {
        openAll = v
        prefs.edit().putBoolean("openAll", v).apply()
    }

    fun changeLanguage(lang: Lang) {
        Language.set(lang)
        settings.edit().putString("language", if (lang == Lang.De) "de" else "en").apply()
    }

    fun reset() {
        stars.clear()
        streak = 0
        openAll = false
        prefs.edit().clear().apply()
    }
}
