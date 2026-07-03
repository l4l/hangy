package me.kitsu.hangy

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * End-to-end smoke test over the real app (backed by the seeded database). Requires a
 * connected device or emulator; runs in CI via `connectedDebugAndroidTest`.
 */
@RunWith(AndroidJUnit4::class)
class NavigationTest {

    @get:Rule
    val composeRule = createAndroidComposeRule<MainActivity>()

    private fun string(id: Int) = composeRule.activity.getString(id)

    @Test
    fun measureScreenShowsConnectionCardByDefault() {
        composeRule.onNodeWithText(string(R.string.connect_title)).assertIsDisplayed()
    }

    @Test
    fun canNavigateToRoutinesAndSeeSeededRoutines() {
        composeRule.onNodeWithText(string(R.string.nav_routines)).performClick()
        composeRule.waitForIdle()

        // At least one of the pre-seeded routines should be visible.
        composeRule.waitUntil(timeoutMillis = 5_000) {
            composeRule.onAllNodesWithTextContaining("hang").fetchSemanticsNodes().isNotEmpty()
        }
    }

    @Test
    fun canOpenSettings() {
        composeRule.onNodeWithText(string(R.string.nav_settings)).performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithText(string(R.string.body_weight)).assertIsDisplayed()
    }
}

private fun androidx.compose.ui.test.junit4.ComposeTestRule.onAllNodesWithTextContaining(text: String) =
    onAllNodes(androidx.compose.ui.test.hasText(text, substring = true, ignoreCase = true))
