package staff.android.rendering.catalog

import java.math.BigDecimal
import java.text.NumberFormat
import java.util.Collections
import java.util.Locale

data class Product(val id: Long, val title: String, val priceMinor: Long, val imageKey: String)
data class Review(val productId: Long, val stars: Int)
data class ProductRow(val id: Long, val title: String, val price: String, val rating: String, val imageKey: String)
enum class SortOrder { Featured, Price }

/** Owns stable, immutable inputs. A new catalog is a new snapshot object. */
class CatalogSnapshot(products: List<Product>, reviews: List<Review>) {
    val products: List<Product> = Collections.unmodifiableList(ArrayList(products))
    val reviews: List<Review> = Collections.unmodifiableList(ArrayList(reviews))
    init {
        val ids = this.products.map { it.id }.toSet()
        require(ids.size == this.products.size)
        require(this.products.all { it.id > 0 && it.title.isNotBlank() && it.priceMinor >= 0 && it.imageKey.isNotBlank() })
        require(this.reviews.all { it.productId in ids && it.stars in 1..5 })
    }
}

/** Deliberately scans all reviews per product, making repeated row preparation observable. */
class RowBuilder {
    fun build(snapshot: CatalogSnapshot, sort: SortOrder): List<ProductRow> {
        val ordered = when (sort) {
            SortOrder.Featured -> snapshot.products
            SortOrder.Price -> snapshot.products.sortedWith(compareBy<Product> { it.priceMinor }.thenBy { it.id })
        }
        val currency = NumberFormat.getCurrencyInstance(Locale.forLanguageTag("en-IN"))
        val ratingFormat = NumberFormat.getNumberInstance(Locale.US).apply {
            minimumFractionDigits = 1
            maximumFractionDigits = 1
        }
        return Collections.unmodifiableList(ordered.map { product ->
            var sum = 0L
            var count = 0
            for (review in snapshot.reviews) {
                if (review.productId == product.id) { sum += review.stars; count++ }
            }
            val rating = if (count == 0) "No reviews yet" else {
                val mean = ratingFormat.format(sum.toDouble() / count)
                "$mean · $count reviews"
            }
            currency.minimumFractionDigits = if (product.priceMinor % 100 == 0L) 0 else 2
            currency.maximumFractionDigits = 2
            ProductRow(
                id = product.id,
                title = product.title,
                price = currency.format(BigDecimal.valueOf(product.priceMinor, 2)),
                rating = rating,
                imageKey = product.imageKey,
            )
        })
    }
}

fun seedCatalog(): CatalogSnapshot {
    val kinds = listOf(
        Triple("Camera", 1_299_900L, "camera"), Triple("Tea", 45_000L, "tea"),
        Triple("Day bag", 249_000L, "bag"), Triple("Reading lamp", 199_000L, "lamp"),
        Triple("Everyday mug", 69_000L, "mug"), Triple("Headphones", 599_000L, "headphones"),
    )
    val products = (0 until 20).flatMap { edition ->
        kinds.mapIndexed { kind, (title, price, image) ->
            Product(edition * 6L + kind + 1, "$title ${edition + 1}", price + edition * 100L, image)
        }
    }
    val reviews = products.flatMap { p -> (0 until 200).map { Review(p.id, 3 + ((it + p.id) % 3).toInt()) } }
    return CatalogSnapshot(products, reviews)
}

/** User choice follows product identity, independently of presentation position. */
fun toggleSaved(saved: Set<Long>, productId: Long): Set<Long> {
    require(productId > 0)
    return if (productId in saved) saved - productId else saved + productId
}
