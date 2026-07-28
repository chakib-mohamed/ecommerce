package the.chak.ecommerce.analytics.control;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import the.chak.ecommerce.analytics.entity.DimProduct;
import the.chak.ecommerce.analytics.entity.FactSalesLine;
import the.chak.ecommerce.analytics.repository.DimProductRepository;
import the.chak.ecommerce.analytics.repository.FactSalesLineRepository;
import the.chak.ecommerce.orders.boundary.dto.OrderDTO;
import the.chak.ecommerce.orders.boundary.dto.ProductVO;
import the.chak.ecommerce.products.boundary.dto.CategoryDto;
import the.chak.ecommerce.products.boundary.dto.ProductDto;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class IngestionServiceTest {

    @InjectMocks
    IngestionService ingestionService;

    @Mock
    FactSalesLineRepository factRepository;

    @Mock
    DimProductRepository dimRepository;

    private static ProductVO line(String productId, int qty, double price, Double percentageOff) {
        ProductVO item = new ProductVO();
        item.setProductID(productId);
        item.setTitle("Desk lamp");
        item.setQty(qty);
        item.setPrice(price);
        item.setPercentageOff(percentageOff);
        return item;
    }

    private static CategoryDto category(Long id, String label) {
        CategoryDto dto = new CategoryDto();
        dto.setId(id);
        dto.setLabel(label);
        return dto;
    }

    private static OrderDTO order(String id, LocalDateTime placedAt, ProductVO... lines) {
        OrderDTO dto = new OrderDTO();
        dto.setId(id);
        dto.setCreationDate(placedAt);
        dto.setUserID("buyer@example.com");
        dto.setProducts(List.of(lines));
        return dto;
    }

    @Test
    @DisplayName("Records one sales row for each line of the order")
    void ingestOrder_multipleLines_recordsOneRowPerLine() {
        // given
        OrderDTO dto = order("order-1", LocalDateTime.of(2026, 3, 4, 10, 0),
                line("uuid-a", 2, 100d, null), line("uuid-b", 1, 50d, null));

        // when
        ingestionService.ingestOrder(dto);

        // then
        verify(factRepository, times(2)).persist(any(FactSalesLine.class));
    }

    @Test
    @DisplayName("Applies the discount that was active when the order was placed")
    void ingestOrder_discountedLine_appliesPurchaseTimeDiscount() {
        // given
        OrderDTO dto = order("order-1", LocalDateTime.of(2026, 3, 4, 10, 0),
                line("uuid-a", 2, 100d, 10d));

        // when
        ingestionService.ingestOrder(dto);

        // then
        ArgumentCaptor<FactSalesLine> captor = ArgumentCaptor.forClass(FactSalesLine.class);
        verify(factRepository).persist(captor.capture());
        assertEquals(180d, captor.getValue().getLineRevenue());
    }

    @Test
    @DisplayName("Charges the full price for a line that carried no discount")
    void ingestOrder_undiscountedLine_usesFullPrice() {
        // given
        OrderDTO dto = order("order-1", LocalDateTime.of(2026, 3, 4, 10, 0),
                line("uuid-a", 3, 25d, null));

        // when
        ingestionService.ingestOrder(dto);

        // then
        ArgumentCaptor<FactSalesLine> captor = ArgumentCaptor.forClass(FactSalesLine.class);
        verify(factRepository).persist(captor.capture());
        assertEquals(75d, captor.getValue().getLineRevenue());
    }

    @Test
    @DisplayName("Clears an order's existing rows before recording it, so redelivery cannot double-count")
    void ingestOrder_redelivered_clearsPreviousRowsFirst() {
        // given
        OrderDTO dto = order("order-1", LocalDateTime.of(2026, 3, 4, 10, 0),
                line("uuid-a", 1, 10d, null));

        // when
        ingestionService.ingestOrder(dto);

        // then
        verify(factRepository).deleteByOrderId("order-1");
    }

    @Test
    @DisplayName("Buckets the sale into the month the order was placed")
    void ingestOrder_always_bucketsByOrderMonth() {
        // given
        OrderDTO dto = order("order-1", LocalDateTime.of(2026, 3, 4, 10, 0),
                line("uuid-a", 1, 10d, null));

        // when
        ingestionService.ingestOrder(dto);

        // then
        ArgumentCaptor<FactSalesLine> captor = ArgumentCaptor.forClass(FactSalesLine.class);
        verify(factRepository).persist(captor.capture());
        assertEquals("2026-03", captor.getValue().getOrderMonth());
        assertEquals("order-1", captor.getValue().getOrderId());
        assertEquals("uuid-a", captor.getValue().getProductId());
        assertEquals("buyer@example.com", captor.getValue().getUserId());
    }

    @Test
    @DisplayName("Creates a dimension row carrying the product's category")
    void upsertProduct_newProduct_createsDimensionRowWithCategory() {
        // given
        UUID uuid = UUID.randomUUID();
        ProductDto product = new ProductDto();
        product.setUuid(uuid);
        product.setTitle("Desk lamp");
        product.setCategoryId(7L);
        when(dimRepository.findByIdOptional(uuid.toString())).thenReturn(Optional.empty());

        // when
        ingestionService.upsertProduct(product);

        // then
        ArgumentCaptor<DimProduct> captor = ArgumentCaptor.forClass(DimProduct.class);
        verify(dimRepository).persist(captor.capture());
        assertEquals(uuid.toString(), captor.getValue().getProductId());
        assertEquals("Desk lamp", captor.getValue().getTitle());
        assertEquals(7L, captor.getValue().getCategoryId());
    }

    @Test
    @DisplayName("Refreshes the existing dimension row when the product is already known")
    void upsertProduct_knownProduct_refreshesExistingRow() {
        // given
        UUID uuid = UUID.randomUUID();
        DimProduct existing = new DimProduct();
        existing.setProductId(uuid.toString());
        existing.setTitle("Old name");
        ProductDto product = new ProductDto();
        product.setUuid(uuid);
        product.setTitle("New name");
        when(dimRepository.findByIdOptional(uuid.toString())).thenReturn(Optional.of(existing));

        // when
        ingestionService.upsertProduct(product);

        // then
        assertEquals("New name", existing.getTitle());
        verify(dimRepository, times(0)).persist(any(DimProduct.class));
    }

    @Test
    @DisplayName("Flags a deleted product but keeps its row so past sales keep their category")
    void markProductDeleted_knownProduct_flagsRowWithoutRemovingIt() {
        // given
        String productId = UUID.randomUUID().toString();
        DimProduct existing = new DimProduct();
        existing.setProductId(productId);
        existing.setDeleted(false);
        when(dimRepository.findByIdOptional(productId)).thenReturn(Optional.of(existing));

        // when
        ingestionService.markProductDeleted(productId);

        // then
        assertTrue(existing.isDeleted());
        verify(dimRepository, times(0)).delete(any(DimProduct.class));
    }

    @Test
    @DisplayName("Ignores a delete for a product the warehouse never recorded")
    void markProductDeleted_unknownProduct_isIgnored() {
        // given
        String productId = UUID.randomUUID().toString();
        when(dimRepository.findByIdOptional(productId)).thenReturn(Optional.empty());

        // when
        ingestionService.markProductDeleted(productId);

        // then
        verify(dimRepository, times(0)).persist(any(DimProduct.class));
    }

    @Test
    @DisplayName("Labels the dimension row with the category it was sent alongside")
    void upsertProduct_withCategories_resolvesCategoryAndSubcategoryLabels() {
        // given
        UUID uuid = UUID.randomUUID();
        ProductDto product = new ProductDto();
        product.setUuid(uuid);
        product.setCategoryId(7L);
        product.setSubcategoryId(9L);
        product.setCategories(List.of(category(7L, "Lighting"), category(9L, "Desk lamps")));
        when(dimRepository.findByIdOptional(uuid.toString())).thenReturn(Optional.empty());

        // when
        ingestionService.upsertProduct(product);

        // then
        ArgumentCaptor<DimProduct> captor = ArgumentCaptor.forClass(DimProduct.class);
        verify(dimRepository).persist(captor.capture());
        assertEquals("Lighting", captor.getValue().getCategoryLabel());
        assertEquals("Desk lamps", captor.getValue().getSubcategoryLabel());
    }

    @Test
    @DisplayName("Files a sale under the category the product event actually carries")
    void upsertProduct_eventCarriesOnlyFiledCategories_usesTheFiledCategory() {
        // given - the shape a real product event has: filed categories, no submission-side ids
        UUID uuid = UUID.randomUUID();
        ProductDto product = new ProductDto();
        product.setUuid(uuid);
        product.setTitle("Marble Dining Table");
        product.setCategories(List.of(category(20L, "Dining Tables")));
        when(dimRepository.findByIdOptional(uuid.toString())).thenReturn(Optional.empty());

        // when
        ingestionService.upsertProduct(product);

        // then
        ArgumentCaptor<DimProduct> captor = ArgumentCaptor.forClass(DimProduct.class);
        verify(dimRepository).persist(captor.capture());
        assertEquals(20L, captor.getValue().getCategoryId());
        assertEquals("Dining Tables", captor.getValue().getCategoryLabel());
    }

    @Test
    @DisplayName("Records a product filed under nothing without a category")
    void upsertProduct_noFiledCategories_recordsProductWithoutCategory() {
        // given
        UUID uuid = UUID.randomUUID();
        ProductDto product = new ProductDto();
        product.setUuid(uuid);
        product.setTitle("Desk lamp");
        product.setCategories(List.of());
        when(dimRepository.findByIdOptional(uuid.toString())).thenReturn(Optional.empty());

        // when
        ingestionService.upsertProduct(product);

        // then
        ArgumentCaptor<DimProduct> captor = ArgumentCaptor.forClass(DimProduct.class);
        verify(dimRepository).persist(captor.capture());
        assertNull(captor.getValue().getCategoryId());
        assertNull(captor.getValue().getCategoryLabel());
    }

    @Test
    @DisplayName("Records a product that carries neither categories nor a category id")
    void upsertProduct_noCategoryInformation_recordsProductWithoutLabels() {
        // given
        UUID uuid = UUID.randomUUID();
        ProductDto product = new ProductDto();
        product.setUuid(uuid);
        product.setTitle("Desk lamp");
        when(dimRepository.findByIdOptional(uuid.toString())).thenReturn(Optional.empty());

        // when
        ingestionService.upsertProduct(product);

        // then
        ArgumentCaptor<DimProduct> captor = ArgumentCaptor.forClass(DimProduct.class);
        verify(dimRepository).persist(captor.capture());
        assertNull(captor.getValue().getCategoryId());
        assertNull(captor.getValue().getCategoryLabel());
        assertNull(captor.getValue().getSubcategoryLabel());
    }

    @Test
    @DisplayName("Clears the deleted flag when a previously removed product comes back")
    void upsertProduct_previouslyDeletedProduct_clearsTheDeletedFlag() {
        // given
        UUID uuid = UUID.randomUUID();
        DimProduct existing = new DimProduct();
        existing.setProductId(uuid.toString());
        existing.setDeleted(true);
        ProductDto product = new ProductDto();
        product.setUuid(uuid);
        product.setTitle("Desk lamp");
        when(dimRepository.findByIdOptional(uuid.toString())).thenReturn(Optional.of(existing));

        // when
        ingestionService.upsertProduct(product);

        // then
        assertFalse(existing.isDeleted());
    }

    @Test
    @DisplayName("Skips an order whose lines are missing entirely")
    void ingestOrder_missingLines_recordsNothing() {
        // given
        OrderDTO dto = new OrderDTO();
        dto.setId("order-null");
        dto.setCreationDate(LocalDateTime.of(2026, 3, 4, 10, 0));
        dto.setProducts(null);

        // when
        ingestionService.ingestOrder(dto);

        // then
        verify(factRepository, times(0)).persist(any(FactSalesLine.class));
        verify(factRepository).deleteByOrderId("order-null");
    }

    @Test
    @DisplayName("Skips an order that carries no lines")
    void ingestOrder_noLines_recordsNothing() {
        // given
        OrderDTO dto = order("order-empty", LocalDateTime.of(2026, 3, 4, 10, 0));

        // when
        ingestionService.ingestOrder(dto);

        // then
        verify(factRepository, times(0)).persist(any(FactSalesLine.class));
        assertFalse(dto.getProducts() == null);
    }
}
