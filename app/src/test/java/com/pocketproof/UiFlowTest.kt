package com.pocketproof

import android.graphics.Bitmap
import android.graphics.Canvas
import androidx.activity.ComponentActivity
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.hapticfeedback.HapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.core.app.ApplicationProvider
import com.pocketproof.content.Course
import com.pocketproof.ui.*
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File

/** Runs the real screens at the Marble's screen size and performs real touch gestures on them. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w411dp-h914dp-xxhdpi")
class UiFlowTest {
    @get:Rule val rule = createAndroidComposeRule<ComponentActivity>()

    private val out = File(System.getProperty("shots.dir") ?: "build/shots").apply { mkdirs() }

    private fun shot(name: String) {
        rule.waitForIdle()
        val view = rule.activity.window.decorView
        val bmp = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
        rule.runOnUiThread { view.draw(Canvas(bmp)) }
        File(out, "$name.png").outputStream().use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }

    private fun progress() = Progress(ApplicationProvider.getApplicationContext())

    private fun open(id: String) {
        val p = progress()
        rule.setContent { PocketProofTheme { LevelScreen(Course.level(id), p, {}, {}) } }
        dismissIntro()
    }

    private fun dismissIntro() {
        rule.waitForIdle()
        val b = rule.onAllNodesWithText("Let's go")
        if (b.fetchSemanticsNodes().isNotEmpty()) b[0].performClick()
        rule.waitForIdle()
    }

    /** Drags a tray card up onto the middle of a node, the way a finger would. */
    private fun dragCardOnto(card: SemanticsNodeInteraction, target: SemanticsNodeInteraction, fx: Float = 0.5f) {
        val to = target.fetchSemanticsNode().boundsInRoot
        val from = card.fetchSemanticsNode().boundsInRoot
        val dest = Offset(to.left + to.width * fx, to.top + to.height / 2 + 44f * 2.625f)
        card.performTouchInput {
            down(center)
            val start = center
            val delta = Offset(dest.x - from.left - start.x, dest.y - from.top - start.y)
            for (i in 1..12) { moveTo(start + Offset(0f, -8f * i)); advanceEventTime(16) }
            for (i in 1..12) { moveTo(start + delta * (i / 12f)); advanceEventTime(16) }
        }
        rule.waitForIdle()
    }

    /** The floating drag label repeats the card's name, so the card is addressed as the first match. */
    private fun release(name: String) {
        rule.onAllNodesWithText(name)[0].performTouchInput { up() }
        rule.waitForIdle()
    }

    @Test fun journeyScreenRenders() {
        rule.setContent { PocketProofTheme { App() } }
        rule.mainClock.advanceTimeBy(600)
        rule.onNodeWithText("PocketProof").assertExists()
        rule.onNodeWithText("Start your first proof").assertExists()
        shot("01_journey")
    }

    @Test fun firstLevelSolvedByDragging() {
        open("first")
        shot("02_first_level")
        val goal = rule.onAllNodesWithText("1·a = a")[0]
        val card = rule.onNodeWithText("One")
        dragCardOnto(card, goal, 0.2f)
        shot("03_dragging")
        release("One")
        rule.mainClock.advanceTimeBy(800)
        rule.onNodeWithText("Proved!").assertExists()
        shot("04_victory")
    }

    @Test fun hoveringOverOneTargetVibratesOnce() {
        var ticks = 0
        val counting = object : HapticFeedback {
            override fun performHapticFeedback(hapticFeedbackType: HapticFeedbackType) {
                if (hapticFeedbackType == HapticFeedbackType.TextHandleMove) ticks++
            }
        }
        val p = progress()
        rule.setContent {
            CompositionLocalProvider(LocalHapticFeedback provides counting) {
                PocketProofTheme { LevelScreen(Course.level("first"), p, {}, {}) }
            }
        }
        dismissIntro()
        val goal = rule.onAllNodesWithText("1·a = a")[0]
        val card = rule.onNodeWithText("One")
        dragCardOnto(card, goal, 0.2f)
        val afterArriving = ticks
        // Keep the finger moving on the same target.
        repeat(30) { i ->
            rule.onAllNodesWithText("One")[0].performTouchInput { moveBy(Offset(if (i % 2 == 0) 2f else -2f, 0f)) }
        }
        rule.waitForIdle()
        assertEquals("No further vibration while staying on one target", afterArriving, ticks)
        // Picking up the card, crossing the "add to Given" zone on the way, and arriving: one tick each.
        assertEquals(3, afterArriving)
    }

    @Test fun tapToSelectThenTapTermWorksToo() {
        open("twice")
        val card = rule.onNodeWithText("Neutral right")
        card.performClick()
        rule.waitForIdle()
        shot("05_armed")
        val goal = rule.onAllNodesWithText("(a + o) + o = a")[0]
        goal.performTouchInput { click(Offset(width * 0.12f, height / 2f)) }
        rule.waitForIdle()
        shot("06_after_tap")
        // One of the two rewrites has been done, and the goal has changed.
        assertTrue(rule.onAllNodesWithText("(a + o) + o = a").fetchSemanticsNodes().isEmpty())
    }

    @Test fun droppingWhereARuleDoesNotFitChangesNothing() {
        open("neg_add_cancel")
        val before = "(−a) + (a + b) = b"
        val goal = rule.onAllNodesWithText(before)[0]
        val card = rule.onNodeWithText("Neutral left")
        dragCardOnto(card, goal)
        release("Neutral left")
        rule.onAllNodesWithText(before)[0].assertExists()
    }

    @Test fun longStatementsFitAndTrayFiltersShowTools() {
        progress().changeOpenAll(true)
        open("D_2x2")
        shot("07_long_goal")
        rule.onNodeWithText("Everything").performClick()
        rule.waitForIdle()
        shot("08_everything_tray")
    }

    @Test fun implicationLevelShowsIntroAndGiven() {
        open("cancel_r")
        rule.onNodeWithText("Intro").performClick()
        rule.waitForIdle()
        shot("09_after_intro")
        rule.onNodeWithText("h1").assertExists()
    }

    @Test fun hypothesisWithForallAndGoalsDisplay() {
        open("sub_closed")
        rule.onNodeWithText("Intro").performClick()
        rule.waitForIdle()
        shot("10_subspace_given")
    }

    @Test fun hintExplainsTheNextMove() {
        open("add_neg_cancel")
        rule.onNodeWithContentDescription("Hint").performClick()
        rule.waitForIdle()
        shot("11_hint")
        rule.onNodeWithText("Associativity").assertIsDisplayed()
    }

    /** A whole level through the interface: Intro, a flipped card, the value picker, a hypothesis as a tool. */
    @Test fun wholeLevelWithFlipPickerAndHypothesis() {
        open("cancel_r")
        rule.onNodeWithText("Intro").performClick()
        rule.waitForIdle()
        rule.onAllNodesWithContentDescription("Flip direction")[0].performClick()
        rule.waitForIdle()
        rule.onNodeWithText("Add and Take Away").performClick()
        rule.waitForIdle()
        shot("14_flipped_armed")
        rule.onAllNodesWithText("a = b")[0].performTouchInput { click(Offset(width * 0.02f, height / 2f)) }
        rule.waitForIdle()
        rule.onNodeWithText("Choose b").assertExists()
        rule.onAllNodesWithText("u", useUnmergedTree = true).fetchSemanticsNodes().let { assertTrue(it.isNotEmpty()) }
        rule.onAllNodesWithText("u")[0].performClick()
        rule.waitForIdle()
        rule.mainClock.advanceTimeBy(500)
        shot("15_after_pick")
        rule.onNodeWithText("a + u = b + u").performClick()
        rule.waitForIdle()
        rule.onAllNodesWithText("(a + u) + (−u) = b")[0].performTouchInput { click(Offset(width * 0.05f, height / 2f)) }
        rule.waitForIdle()
        shot("16_after_hyp")
        rule.onNodeWithText("Add and Take Away").performClick()
        rule.waitForIdle()
        rule.onAllNodesWithText("(b + u) + (−u) = b")[0].performTouchInput { click(Offset(width * 0.05f, height / 2f)) }
        rule.waitForIdle()
        rule.mainClock.advanceTimeBy(800)
        rule.onNodeWithText("Proved!").assertExists()
        shot("17_victory_reward")
    }

    @Test fun allToolsOverlaySelectsATool() {
        progress().changeOpenAll(true)
        open("sub_closed")
        rule.onNodeWithContentDescription("Show all tools").performClick()
        rule.waitForIdle()
        shot("18_all_tools")
        rule.onNodeWithText("Your tools").assertExists()
        rule.onNodeWithContentDescription("Close tools").performClick()
        rule.waitForIdle()
        rule.onNodeWithText("Your tools").assertDoesNotExist()
    }

    private fun settings() = ApplicationProvider.getApplicationContext<android.content.Context>()
        .getSharedPreferences("settings", android.content.Context.MODE_PRIVATE)

    /** These tests address the interface by its English labels. */
    @org.junit.Before fun startInEnglish() { settings().edit().putString("language", "en").commit() }

    @After fun backToEnglish() = Language.set(Lang.En)

    @Test fun germanIsTheDefaultUntilAnotherLanguageIsChosen() {
        settings().edit().clear().commit()
        val p = progress()
        assertEquals(Lang.De, Language.current)
        p.changeLanguage(Lang.En)
        progress()
        assertEquals(Lang.En, Language.current)
    }

    @Test fun languageCanBeSetToGermanAndIsRemembered() {
        rule.setContent { PocketProofTheme { App() } }
        rule.mainClock.advanceTimeBy(600)
        rule.onNodeWithText("Progress").performClick()
        rule.waitForIdle()
        rule.onNodeWithText("Deutsch").performClick()
        rule.waitForIdle()
        rule.onNodeWithText("Sprache").assertExists()
        shot("20_de_progress")
        rule.onNodeWithText("Reise").performClick()
        rule.waitForIdle()
        shot("21_de_journey")
        rule.onNodeWithText("Starte deinen ersten Beweis").performClick()
        rule.mainClock.advanceTimeBy(800)
        rule.waitForIdle()
        shot("22_de_intro")
        rule.onNodeWithText("Los geht's").performClick()
        rule.waitForIdle()
        rule.onNodeWithText("Erster Zug").assertExists()
        rule.onNodeWithText("Eins").assertExists()
        rule.onNodeWithContentDescription("Tipp").performClick()
        rule.waitForIdle()
        shot("23_de_hint")
        assertEquals(Lang.De, Language.current)
        progress()
        assertEquals("The saved language is restored", Lang.De, Language.current)
    }

    @Test fun resettingProgressKeepsTheLanguage() {
        val p = progress()
        p.changeLanguage(Lang.De)
        p.reset()
        progress()
        assertEquals(Lang.De, Language.current)
    }

    @Test fun storyAndHintWithIconsRender() {
        progress().changeOpenAll(true)
        open("self_sub")
        shot("24_compute_icon_story")
        rule.onNodeWithContentDescription("Hint").performClick()
        rule.waitForIdle()
        shot("25_compute_icon_hint")
    }

    @Test fun subscriptsInStoryTextRender() {
        progress().changeOpenAll(true)
        open("indep_no_repeat")
        shot("27_story_subscripts")
    }

    @Test fun superscriptsInMatrixVariablesRender() {
        progress().changeOpenAll(true)
        open("cancel_mat")
        shot("28_matrix_superscripts")
    }

    @Config(qualifiers = "+night")
    @Test fun dropZoneIsVisibleInDarkMode() {
        open("add_neg_cancel")
        val card = rule.onNodeWithText("Associativity")
        // Lift the card just far enough for the zone to appear, without hovering over it.
        card.performTouchInput {
            down(center)
            for (i in 1..14) { moveTo(center + Offset(0f, -12f * i)); advanceEventTime(16) }
        }
        rule.waitForIdle()
        rule.onNodeWithText("Drop here to add it to Given").assertExists()
        shot("26_dark_drop_zone")
        release("Associativity")
    }

    @Test fun libraryAndProgressRender() {
        rule.setContent { PocketProofTheme { App() } }
        rule.mainClock.advanceTimeBy(600)
        rule.onNodeWithText("Library").performClick()
        rule.waitForIdle()
        shot("12_library")
        rule.onNodeWithText("Progress").performClick()
        rule.waitForIdle()
        shot("13_progress")
    }
}
