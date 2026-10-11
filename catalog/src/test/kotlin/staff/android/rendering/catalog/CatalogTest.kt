package staff.android.rendering.catalog

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class CatalogTest {
    private val camera = Product(1, "Camera", 1299900, "camera")
    private val tea = Product(2, "Tea", 45000, "tea")
    private fun snapshot() = CatalogSnapshot(listOf(camera, tea), listOf(Review(1, 4), Review(1, 5)))
    @Test fun repeatedPreparationPreservesEveryDisplayedField() {
        val input = seedCatalog(); val builder = RowBuilder()
        assertEquals(builder.build(input, SortOrder.Featured), builder.build(input, SortOrder.Featured))
    }
    @Test fun priceSortChangesOrderWithoutChangingIdentityOrFields() {
        val builder = RowBuilder(); val input = snapshot()
        val featured = builder.build(input, SortOrder.Featured)
        val priced = builder.build(input, SortOrder.Price)
        assertEquals(listOf(2L, 1L), priced.map { it.id })
        assertEquals(featured.associateBy { it.id }, priced.associateBy { it.id })
    }
    @Test fun tiedPricesUseStableIdOrder() {
        val input = CatalogSnapshot(listOf(tea.copy(id=9), tea.copy(id=2)), emptyList())
        assertEquals(listOf(2L,9L),RowBuilder().build(input,SortOrder.Price).map { it.id })
    }
    @Test fun ratingBelongsToItsProductAndEmptyReviewsRemainExplicit() {
        val rows = RowBuilder().build(snapshot(),SortOrder.Featured)
        assertEquals("4.5 · 2 reviews",rows[0].rating); assertEquals("No reviews yet",rows[1].rating)
    }
    @Test fun snapshotOwnsItsInputAndReturnedListsCannotBeMutated() {
        val products = mutableListOf(camera);val reviews = mutableListOf(Review(1,5))
        val s=CatalogSnapshot(products,reviews);products.clear();reviews.clear()
        assertEquals(1,s.products.size);assertEquals(1,s.reviews.size)
        assertThrows(UnsupportedOperationException::class.java) { (s.products as MutableList).clear() }
        assertThrows(UnsupportedOperationException::class.java) { (RowBuilder().build(s,SortOrder.Featured) as MutableList).clear() }
    }
    @Test fun newSnapshotUpdatesPriceAndReviews() {
        val initial=snapshot();val changed=CatalogSnapshot(listOf(camera.copy(priceMinor=125050)),listOf(Review(1,3)))
        val a=RowBuilder().build(initial,SortOrder.Featured)[0];val b=RowBuilder().build(changed,SortOrder.Featured)[0]
        assertNotEquals(a.price,b.price);assertEquals("3.0 · 1 reviews",b.rating)
        assertEquals(1299900L,initial.products[0].priceMinor)
    }
    @Test fun savedIdentitySurvivesReorderAndAnotherSelection() {
        val saved=toggleSaved(emptySet(),1);val both=toggleSaved(saved,2)
        assertEquals(setOf(1L,2L),both);assertEquals(setOf(1L),saved)
        assertTrue(RowBuilder().build(snapshot(),SortOrder.Price).single { it.id==1L }.id in both)
        assertEquals(setOf(2L),toggleSaved(both,1))
    }
    @Test fun emptyCatalogHasNoManufacturedRows() {
        assertEquals(emptyList<ProductRow>(),RowBuilder().build(CatalogSnapshot(emptyList(),emptyList()),SortOrder.Price))
    }
    @Test fun invalidSnapshotIsRejectedAtBoundary() {
        assertThrows(IllegalArgumentException::class.java) { CatalogSnapshot(listOf(camera,camera),emptyList()) }
        assertThrows(IllegalArgumentException::class.java) { CatalogSnapshot(listOf(camera),listOf(Review(2,5))) }
        assertThrows(IllegalArgumentException::class.java) { CatalogSnapshot(listOf(camera),listOf(Review(1,6))) }
        assertThrows(IllegalArgumentException::class.java) { CatalogSnapshot(listOf(camera.copy(priceMinor=-1)),emptyList()) }
    }
}
