package staff.android.rendering.benchmark

import android.content.Intent
import androidx.benchmark.macro.CompilationMode
import androidx.benchmark.macro.FrameTimingMetric
import androidx.benchmark.macro.junit4.MacrobenchmarkRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.LargeTest
import androidx.test.uiautomator.By
import androidx.test.uiautomator.Direction
import androidx.test.uiautomator.Until
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.util.regex.Pattern

@RunWith(AndroidJUnit4::class)
@LargeTest
class CollectionBenchmark {
    @get:Rule val benchmark=MacrobenchmarkRule()
    @Test fun repeatedWork() = scroll("repeated")
    @Test fun repaired() = scroll("repaired")
    private fun scroll(mode:String) = benchmark.measureRepeated(
        packageName="staff.android.rendering",
        metrics=listOf(FrameTimingMetric()),
        compilationMode=CompilationMode.Full(),
        iterations=3,
        setupBlock={
            killProcess()
            startActivityAndWait(Intent().setClassName("staff.android.rendering","staff.android.rendering.MainActivity")
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK).putExtra("mode",mode))
            check(device.wait(Until.hasObject(By.res("collection_ready")),10_000))
            // All six local decoded images are ready. This is a warm-image scroll workload.
            device.waitForIdle()
        },
    ) {
        val list=device.findObject(By.res("collection")) ?: error("Collection is not visible")
        list.setGestureMargin(device.displayWidth/5)
        fun visibleIds() = device.findObjects(By.res(Pattern.compile("product_[0-9]+")))
            .map { it.resourceName }.sorted()
        val before=visibleIds()
        check(before.isNotEmpty()) { "No visible product identities before scrolling" }
        println("CG21_JOURNEY mode=$mode before=$before")
        repeat(5) { step ->
            val more=list.fling(Direction.DOWN)
            device.waitForIdle()
            val ids=visibleIds()
            check(ids.isNotEmpty()) { "No visible product identities after gesture" }
            println("CG21_JOURNEY mode=$mode gesture=${step+1} more=$more visible=$ids")
        }
        val after=visibleIds()
        check(after!=before) { "Scroll journey did not advance" }
        println("CG21_JOURNEY mode=$mode after=$after")
    }
}
