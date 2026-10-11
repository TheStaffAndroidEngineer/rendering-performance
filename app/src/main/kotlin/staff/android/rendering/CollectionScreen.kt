package staff.android.rendering

import android.os.Trace
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.testTagsAsResourceId
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import staff.android.rendering.catalog.*

enum class RenderingMode { Repeated, Repaired }

fun prepareRows(builder: RowBuilder, snapshot: CatalogSnapshot, sort: SortOrder): List<ProductRow> {
    Trace.beginSection("CG21/buildRows")
    return try { builder.build(snapshot, sort) } finally { Trace.endSection() }
}

fun headerShift(state: LazyListState, maximum: Int): Int {
    val offset = state.firstVisibleItemScrollOffset
    val index = state.firstVisibleItemIndex
    return if (index > 0) maximum else offset.coerceAtMost(maximum)
}

@Composable
fun CollectionScreen(
    snapshot: CatalogSnapshot,
    sort: SortOrder,
    saved: Set<Long>,
    mode: RenderingMode,
    images: ProductImages,
    imagesReady: Boolean,
    onSort: () -> Unit,
    onSave: (Long) -> Unit,
    onLab: () -> Unit,
) {
    val listState = rememberLazyListState()
    val builder = remember { RowBuilder() }
    val maximumShift = with(LocalDensity.current) { 18.dp.roundToPx() }
    // Deliberately faulty branch: observing scroll here invalidates row preparation.
    val rows: List<ProductRow>
    val headingModifier: Modifier
    if (mode == RenderingMode.Repeated) {
        val shift = headerShift(listState, maximumShift)
        rows = prepareRows(builder, snapshot, sort)
        headingModifier = Modifier.offset(y = with(LocalDensity.current) { (-shift).toDp() })
    } else {
        rows = remember(snapshot, sort) { prepareRows(builder, snapshot, sort) }
        headingModifier = Modifier.offset { IntOffset(0, -headerShift(listState, maximumShift)) }
    }
    Column(Modifier.fillMaxSize().semantics { testTagsAsResourceId = true }) {
        Row(Modifier.fillMaxWidth().padding(horizontal=20.dp,vertical=12.dp),verticalAlignment=Alignment.CenterVertically) {
            Image(painterResource(R.drawable.ic_common_goods),null,Modifier.size(40.dp))
            Text("Common Goods",Modifier.weight(1f).padding(start=12.dp),style=MaterialTheme.typography.titleLarge)
            TextButton(onClick=onLab) { Text("Lab") }
        }
        Box(Modifier.fillMaxWidth().padding(horizontal=20.dp).padding(top=16.dp,bottom=6.dp)) {
            Column(headingModifier) {
                Text("The everyday\ncollection",style=MaterialTheme.typography.headlineLarge)
                Text("Made for the moments between.",Modifier.padding(top=6.dp),style=MaterialTheme.typography.bodyMedium,color=MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        Row(Modifier.fillMaxWidth().padding(horizontal=20.dp),verticalAlignment=Alignment.CenterVertically) {
            Text("${rows.size} pieces · ${saved.size} saved",Modifier.weight(1f).then(if(imagesReady) Modifier.testTag("collection_ready") else Modifier),style=MaterialTheme.typography.labelMedium)
            TextButton(onClick=onSort,modifier=Modifier.testTag("sort")) { Text(if(sort==SortOrder.Featured) "Sort: Featured" else "Sort: Price") }
        }
        if (!imagesReady) LinearProgressIndicator(Modifier.fillMaxWidth())
        if (rows.isEmpty()) {
            Column(Modifier.fillMaxWidth().padding(32.dp),horizontalAlignment=Alignment.CenterHorizontally) {
                Text("A little room for something new",style=MaterialTheme.typography.headlineSmall)
                Text("There are no pieces in this collection yet.",Modifier.padding(top=12.dp),style=MaterialTheme.typography.bodyLarge)
            }
        } else {
            LazyColumn(
                state=listState,
                modifier=Modifier.fillMaxSize().testTag("collection"),
                contentPadding=PaddingValues(start=20.dp,end=20.dp,top=8.dp,bottom=24.dp),
                verticalArrangement=Arrangement.spacedBy(14.dp),
            ) {
                items(rows,key={ it.id },contentType={ "product" }) { row ->
                    ProductCard(row,row.id in saved,images) { onSave(row.id) }
                }
            }
        }
    }
}

@Composable
fun ProductCard(row: ProductRow, saved: Boolean, images: ProductImages, onSave: () -> Unit) {
    val largeText = LocalDensity.current.fontScale >= 1.5f
    Surface(Modifier.fillMaxWidth().testTag("product_${row.id}"),shape=RoundedCornerShape(22.dp),color=MaterialTheme.colorScheme.surfaceContainerLow) {
        if (largeText) Column(Modifier.padding(14.dp)) {
            ProductPhoto(row,images,Modifier.fillMaxWidth().height(180.dp))
            ProductDetails(row,saved,onSave,Modifier.fillMaxWidth().padding(top=12.dp))
        } else Row(Modifier.padding(12.dp),verticalAlignment=Alignment.CenterVertically) {
            ProductPhoto(row,images,Modifier.size(128.dp))
            ProductDetails(row,saved,onSave,Modifier.weight(1f).padding(start=16.dp))
        }
    }
}

@Composable
private fun ProductDetails(row: ProductRow, saved: Boolean, onSave: () -> Unit, modifier: Modifier) {
    Column(modifier) {
        Text(row.title,style=MaterialTheme.typography.titleMedium)
        Text(row.rating,Modifier.padding(top=4.dp),style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
        Text(row.price,Modifier.padding(top=8.dp),style=MaterialTheme.typography.titleMedium)
        TextButton(onSave,Modifier.testTag("save_${row.id}")) { Text(if(saved) "Saved" else "Save") }
    }
}

@Composable
internal fun ProductPhoto(row: ProductRow, images: ProductImages, modifier: Modifier) {
    key(row.imageKey, images) {
    data class Photo(val bitmap: android.graphics.Bitmap?, val loaded: Boolean)
    val photo by produceState(Photo(images.cached(row.imageKey),images.cached(row.imageKey)!=null),row.imageKey,images) {
        value=Photo(images.load(row.imageKey),true)
    }
    Box(modifier.clip(RoundedCornerShape(15.dp)).background(MaterialTheme.colorScheme.surfaceContainerHighest),contentAlignment=Alignment.Center) {
        if(photo.bitmap!=null) Image(photo.bitmap!!.asImageBitmap(),row.title,Modifier.fillMaxSize(),contentScale=ContentScale.Crop)
        else Text(if(photo.loaded) "Image unavailable" else "Loading image…",Modifier.padding(12.dp),style=MaterialTheme.typography.bodySmall)
    }
}
}
