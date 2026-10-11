package staff.android.rendering

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class CollectionTest {
    @get:Rule val compose=createAndroidComposeRule<MainActivity>()
    private fun lab(action:String) { compose.onNodeWithText("Lab").performClick();compose.onNodeWithText(action).performScrollTo().performClick() }
    @Test fun sortInvalidatesRowsAndPreservesSavedIdentity() {
        compose.onNodeWithTag("save_1").performClick()
        compose.onNodeWithTag("sort").performClick()
        compose.onNodeWithTag("collection").performScrollToIndex(0)
        compose.onNodeWithTag("product_2").assertIsDisplayed()
        compose.onNodeWithTag("sort").performClick()
        compose.onNodeWithTag("collection").performScrollToIndex(0)
        compose.onNodeWithTag("save_1").assertTextEquals("Saved")
    }
    @Test fun newSnapshotInvalidatesRememberedPrice() {
        compose.onNodeWithText("₹12,999").assertExists()
        lab("Update first price")
        compose.onNodeWithText("₹11,999").assertExists()
        compose.onNodeWithText("₹12,999").assertDoesNotExist()
    }
    @Test fun repeatedAndRepairedKeepTheSameSavedProduct() {
        compose.onNodeWithTag("save_1").performClick()
        lab("Repeated work")
        compose.onNodeWithTag("save_1").assertTextEquals("Saved")
        compose.onNodeWithText("Camera 1").assertExists()
        lab("Repaired")
        compose.onNodeWithTag("save_1").assertTextEquals("Saved")
        compose.onNodeWithText("₹12,999").assertExists()
    }
    @Test fun recyclingDoesNotMoveSavedStateToAnotherProduct() {
        compose.onNodeWithTag("save_1").performClick()
        compose.onNodeWithTag("collection").performScrollToIndex(50)
        compose.onNodeWithTag("save_51").assertTextEquals("Save")
        compose.onNodeWithTag("save_51").performClick()
        compose.onNodeWithTag("collection").performScrollToIndex(0)
        compose.onNodeWithTag("save_1").assertTextEquals("Saved")
    }
    @Test fun emptyAndRestoredCatalogAreExplicit() {
        lab("Empty collection")
        compose.onNodeWithText("There are no pieces in this collection yet.").assertIsDisplayed()
        compose.onNodeWithTag("collection").assertDoesNotExist()
        lab("Restore collection")
        compose.onNodeWithText("Camera 1").assertExists()
    }
}
