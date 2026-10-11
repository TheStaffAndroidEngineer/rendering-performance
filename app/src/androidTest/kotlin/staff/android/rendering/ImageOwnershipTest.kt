package staff.android.rendering

import android.graphics.Bitmap
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.CompletableDeferred
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import staff.android.rendering.catalog.ProductRow

@RunWith(AndroidJUnit4::class)
class ImageOwnershipTest {
    @get:Rule val compose=createComposeRule()
    @Test fun changedImageKeyNeverDisplaysThePreviousBitmapWhileReplacementWaits() {
        fun bitmap(color:Int)=Bitmap.createBitmap(16,16,Bitmap.Config.ARGB_8888).apply { eraseColor(color) }
        val red=bitmap(android.graphics.Color.RED);val blue=bitmap(android.graphics.Color.BLUE)
        val pending=CompletableDeferred<Bitmap?>()
        val images=object:ProductImages {
            override fun cached(key:String)=if(key=="red") red else null
            override suspend fun load(key:String)=if(key=="red") red else pending.await()
        }
        val row=mutableStateOf(ProductRow(1,"Sample","₹10","No reviews yet","red"))
        compose.setContent { CommonTheme { ProductPhoto(row.value,images,Modifier.size(80.dp).testTag("photo")) } }
        fun center():Color { val pixels=compose.onNodeWithTag("photo").captureToImage().toPixelMap();return pixels[pixels.width/2,pixels.height/2] }
        assertEquals(Color.Red,center())
        compose.runOnIdle { row.value=row.value.copy(imageKey="blue") }
        compose.onNodeWithText("Loading image…").assertExists()
        assertNotEquals(Color.Red,center())
        compose.runOnIdle { pending.complete(blue) }
        compose.waitUntil { compose.onAllNodesWithText("Loading image…").fetchSemanticsNodes().isEmpty() }
        assertEquals(Color.Blue,center())
    }
}
