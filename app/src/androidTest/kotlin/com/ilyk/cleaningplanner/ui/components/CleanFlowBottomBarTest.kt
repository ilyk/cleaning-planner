package com.ilyk.cleaningplanner.ui.components

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import org.junit.Rule
import org.junit.Test
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull

/**
 * Compose UI test for the 5-tab bottom bar shipped in W3.
 *
 * Locks in three invariants:
 *  1. All five labels render — verifies neither truncated nor wrapped off-screen.
 *  2. Selecting a new tab fires the `onSelect` callback with the tab's route.
 *  3. Selecting the already-current tab does NOT re-fire (idempotency).
 *
 * Lives in `androidTest` because Compose UI tests require an Android instrumented
 * runtime; the rest of this project's UI assertions are at the ViewModel level. CI
 * needs an emulator (or Robolectric setup) to execute — but the test exists as a
 * contract pin.
 */
class CleanFlowBottomBarTest {

    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun all_five_tab_labels_render() {
        composeRule.setContent {
            CleanFlowBottomBar(currentRoute = "home", onSelect = {})
        }

        listOf("Home", "Planner", "Family", "Kids", "AI").forEach { label ->
            composeRule.onNodeWithText(label).assertIsDisplayed()
        }
    }

    @Test
    fun clicking_a_different_tab_fires_onSelect_with_its_route() {
        var selected: String? = null
        composeRule.setContent {
            CleanFlowBottomBar(currentRoute = "home", onSelect = { selected = it })
        }

        composeRule.onNodeWithText("Planner").performClick()
        composeRule.runOnIdle {
            assertEquals("planner", selected)
        }
    }

    @Test
    fun clicking_the_current_tab_does_not_re_fire() {
        var selected: String? = null
        composeRule.setContent {
            CleanFlowBottomBar(currentRoute = "kids", onSelect = { selected = it })
        }

        composeRule.onNodeWithText("Kids").performClick()
        composeRule.runOnIdle {
            assertNull(
                "Tapping the currently-selected tab must not re-fire onSelect",
                selected
            )
        }
    }
}
