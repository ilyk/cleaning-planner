package com.ilyk.cleaningplanner.ui.home

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.ilyk.cleaningplanner.domain.model.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class HomeScreenTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun homeScreen_displaysTitle() {
        composeTestRule.setContent {
            HomeScreen()
        }

        composeTestRule.onNodeWithText("Today's Plan")
            .assertIsDisplayed()
    }

    @Test
    fun homeScreen_displaysModeSelector() {
        composeTestRule.setContent {
            HomeScreen()
        }

        // Check that mode selector is displayed
        composeTestRule.onNodeWithText("Focus")
            .assertIsDisplayed()
        
        composeTestRule.onNodeWithText("LowEnergy")
            .assertIsDisplayed()
        
        composeTestRule.onNodeWithText("FullReset")
            .assertIsDisplayed()
        
        composeTestRule.onNodeWithText("PetMode")
            .assertIsDisplayed()
    }

    @Test
    fun homeScreen_displaysProgressCard() {
        composeTestRule.setContent {
            HomeScreen()
        }

        composeTestRule.onNodeWithText("Progress")
            .assertIsDisplayed()
    }

    @Test
    fun modeSelector_allowsModeSelection() {
        composeTestRule.setContent {
            HomeScreen()
        }

        // Click on LowEnergy mode
        composeTestRule.onNodeWithText("LowEnergy")
            .performClick()
            .assertIsSelected()
    }
}
