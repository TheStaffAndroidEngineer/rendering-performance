package staff.android.rendering

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import staff.android.rendering.catalog.*

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val images=LocalImages(this)
        setContent {
            CommonTheme {
                var snapshot by remember { mutableStateOf(seedCatalog()) }
                var sort by rememberSaveable { mutableStateOf(SortOrder.Featured) }
                var saved by rememberSaveable { mutableStateOf(longArrayOf()) }
                var mode by rememberSaveable { mutableStateOf(if(intent.getStringExtra("mode")=="repeated") RenderingMode.Repeated else RenderingMode.Repaired) }
                var lab by remember { mutableStateOf(false) }
                var ready by remember { mutableStateOf(false) }
                val views=rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
                    result.data?.getLongArrayExtra("saved")?.let { saved=it }
                }
                LaunchedEffect(images) { images.prepare(); ready=true }
                Surface(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.systemBars)) {
                    Box(Modifier.fillMaxSize(),contentAlignment=androidx.compose.ui.Alignment.TopCenter) {
                        Box(Modifier.widthIn(max=900.dp).fillMaxWidth()) {
                            key(mode) {
                                CollectionScreen(snapshot,sort,saved.toSet(),mode,images,ready,
                                    onSort={ sort=if(sort==SortOrder.Featured) SortOrder.Price else SortOrder.Featured },
                                    onSave={ saved=toggleSaved(saved.toSet(),it).toLongArray() },onLab={ lab=true })
                            }
                        }
                    }
                }
                if(lab) AlertDialog(onDismissRequest={ lab=false },title={ Text("Rendering investigation") },
                    text={ Column(Modifier.verticalScroll(rememberScrollState())) {
                        Text("120 local products and 24,000 review scores. Repeated work and Repaired produce the same rows. Switching modes returns to the start of the list.")
                        TextButton(onClick={ mode=RenderingMode.Repeated;lab=false }) { Text("Repeated work") }
                        TextButton(onClick={ mode=RenderingMode.Repaired;lab=false }) { Text("Repaired") }
                        TextButton(onClick={ lab=false;views.launch(Intent(this@MainActivity,ViewsActivity::class.java).putExtra("saved",saved)) }) { Text("Views comparison") }
                        TextButton(onClick={ snapshot=CatalogSnapshot(snapshot.products.map { if(it.id==1L) it.copy(priceMinor=1_199_900L) else it },snapshot.reviews);lab=false }) { Text("Update first price") }
                        TextButton(onClick={ snapshot=CatalogSnapshot(emptyList(),emptyList());lab=false }) { Text("Empty collection") }
                        TextButton(onClick={ snapshot=seedCatalog();lab=false }) { Text("Restore collection") }
                    } },confirmButton={ TextButton(onClick={ lab=false }) { Text("Close") } })
            }
        }
    }
}
