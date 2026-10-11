package staff.android.rendering

import android.content.Intent
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import androidx.recyclerview.widget.RecyclerView
import androidx.test.core.app.ActivityScenario
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ViewsContractTest {
    private fun descendants(view:View):List<View> = listOf(view)+if(view is ViewGroup) (0 until view.childCount).flatMap { descendants(view.getChildAt(it)) } else emptyList()
    @Test fun viewsSortAndRecyclingKeepSavedProductIdentity() {
        val instrumentation=InstrumentationRegistry.getInstrumentation()
        val intent=Intent(instrumentation.targetContext,ViewsActivity::class.java)
        ActivityScenario.launch<ViewsActivity>(intent).use { scenario ->
            fun awaitFirst(id:Long) {
                val deadline=System.nanoTime()+10_000_000_000L
                while(System.nanoTime()<deadline) {
                    var matched=false
                    scenario.onActivity { activity ->
                        val list=descendants(activity.window.decorView).filterIsInstance<RecyclerView>().single()
                        matched=list.adapter!!.itemCount==120 && list.findViewHolderForAdapterPosition(0)?.itemId==id
                    }
                    if(matched)return
                    Thread.sleep(30)
                }
                error("Expected first visible stable id $id")
            }
            awaitFirst(1)
            scenario.onActivity { activity ->
                val list=descendants(activity.window.decorView).filterIsInstance<RecyclerView>().single()
                descendants(list.findViewHolderForAdapterPosition(0)!!.itemView).filterIsInstance<Button>().single().performClick()
                descendants(activity.window.decorView).filterIsInstance<Button>().single { it.text=="Sort: Featured" }.performClick()
            }
            awaitFirst(2)
            scenario.onActivity { activity ->
                val list=descendants(activity.window.decorView).filterIsInstance<RecyclerView>().single()
                val first=descendants(list.findViewHolderForAdapterPosition(0)!!.itemView).filterIsInstance<Button>().single()
                assertEquals("Save",first.text.toString())
                descendants(activity.window.decorView).filterIsInstance<Button>().single { it.text=="Sort: Price" }.performClick()
            }
            awaitFirst(1)
            scenario.onActivity { activity ->
                val list=descendants(activity.window.decorView).filterIsInstance<RecyclerView>().single()
                val first=descendants(list.findViewHolderForAdapterPosition(0)!!.itemView).filterIsInstance<Button>().single()
                assertEquals("Saved",first.text.toString())
            }
        }
    }
}
