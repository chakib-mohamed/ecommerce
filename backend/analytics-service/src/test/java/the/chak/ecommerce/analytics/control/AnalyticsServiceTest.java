package the.chak.ecommerce.analytics.control;

import java.math.BigDecimal;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.time.format.TextStyle;
import java.util.List;
import java.util.Locale;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import the.chak.ecommerce.analytics.boundary.dto.AnalyticsResponse;
import the.chak.ecommerce.analytics.repository.CategoryAggregate;
import the.chak.ecommerce.analytics.repository.FactSalesLineRepository;
import the.chak.ecommerce.analytics.repository.MonthlyRevenue;
import the.chak.ecommerce.analytics.repository.ProductAggregate;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class AnalyticsServiceTest {

    private static final DateTimeFormatter BUCKET = DateTimeFormatter.ofPattern("yyyy-MM");

    @InjectMocks
    AnalyticsService analyticsService;

    @Mock
    FactSalesLineRepository factRepository;

    private static String label(YearMonth month) {
        return month.getMonth().getDisplayName(TextStyle.SHORT, Locale.ENGLISH);
    }

    @Test
    @DisplayName("Returns a full twelve-month series when the warehouse holds no sales")
    void buildAnalytics_emptyWarehouse_returnsTwelveZeroedMonths() {
        // given
        when(factRepository.revenueByMonth(anyString())).thenReturn(List.of());
        when(factRepository.salesByProduct()).thenReturn(List.of());
        when(factRepository.revenueByCategory()).thenReturn(List.of());
        when(factRepository.totalRevenue()).thenReturn(BigDecimal.valueOf(0));

        // when
        AnalyticsResponse result = analyticsService.buildAnalytics();

        // then
        assertEquals(12, result.getSales().size());
        assertTrue(result.getSales().stream().allMatch(m -> m.getValue().signum() == 0),
                "every month of an empty warehouse should report zero revenue");
        assertEquals(0, BigDecimal.valueOf(0).compareTo(result.getTotalRevenue()),
                "expected 0, was " + result.getTotalRevenue());
    }

    @Test
    @DisplayName("Orders the twelve-month series oldest first, ending with the current month")
    void buildAnalytics_always_returnsMonthsOldestFirstEndingThisMonth() {
        // given
        when(factRepository.revenueByMonth(anyString())).thenReturn(List.of());
        when(factRepository.salesByProduct()).thenReturn(List.of());
        when(factRepository.revenueByCategory()).thenReturn(List.of());
        when(factRepository.totalRevenue()).thenReturn(BigDecimal.valueOf(0));

        // when
        AnalyticsResponse result = analyticsService.buildAnalytics();

        // then
        YearMonth thisMonth = YearMonth.now();
        assertEquals(label(thisMonth.minusMonths(11)), result.getSales().get(0).getMonth());
        assertEquals(label(thisMonth), result.getSales().get(11).getMonth());
    }

    @Test
    @DisplayName("Reports revenue for months that have sales and zero for the months between")
    void buildAnalytics_sparseMonths_fillsGapsWithZero() {
        // given
        YearMonth thisMonth = YearMonth.now();
        when(factRepository.revenueByMonth(anyString()))
                .thenReturn(List.of(new MonthlyRevenue(thisMonth.format(BUCKET), BigDecimal.valueOf(4200))));
        when(factRepository.salesByProduct()).thenReturn(List.of());
        when(factRepository.revenueByCategory()).thenReturn(List.of());
        when(factRepository.totalRevenue()).thenReturn(BigDecimal.valueOf(4200));

        // when
        AnalyticsResponse result = analyticsService.buildAnalytics();

        // then
        assertEquals(0, BigDecimal.valueOf(4200).compareTo(result.getSales().get(11).getValue()),
                "expected 4200, was " + result.getSales().get(11).getValue());
        assertEquals(0, BigDecimal.valueOf(0).compareTo(result.getSales().get(10).getValue()),
                "expected 0, was " + result.getSales().get(10).getValue());
    }

    @Test
    @DisplayName("Queries the warehouse from the month that starts the twelve-month window")
    void buildAnalytics_always_queriesFromTheOldestMonthInTheWindow() {
        // given
        when(factRepository.revenueByMonth(anyString())).thenReturn(List.of());
        when(factRepository.salesByProduct()).thenReturn(List.of());
        when(factRepository.revenueByCategory()).thenReturn(List.of());
        when(factRepository.totalRevenue()).thenReturn(BigDecimal.valueOf(0));

        // when
        analyticsService.buildAnalytics();

        // then
        org.mockito.Mockito.verify(factRepository)
                .revenueByMonth(YearMonth.now().minusMonths(11).format(BUCKET));
    }

    @Test
    @DisplayName("Carries product sales through in the order the warehouse ranked them")
    void buildAnalytics_productSales_preservesWarehouseRanking() {
        // given
        when(factRepository.revenueByMonth(anyString())).thenReturn(List.of());
        when(factRepository.salesByProduct()).thenReturn(List.of(
                new ProductAggregate("uuid-a", "Desk lamp", 90L, BigDecimal.valueOf(1800)),
                new ProductAggregate("uuid-b", "Wall clock", 30L, BigDecimal.valueOf(900))));
        when(factRepository.revenueByCategory()).thenReturn(List.of());
        when(factRepository.totalRevenue()).thenReturn(BigDecimal.valueOf(2700));

        // when
        AnalyticsResponse result = analyticsService.buildAnalytics();

        // then
        assertEquals(2, result.getProductSales().size());
        assertEquals("uuid-a", result.getProductSales().get(0).getProductId());
        assertEquals("Desk lamp", result.getProductSales().get(0).getName());
        assertEquals(90L, result.getProductSales().get(0).getUnits());
        assertEquals(0, BigDecimal.valueOf(1800).compareTo(result.getProductSales().get(0).getRevenue()),
                "expected 1800, was " + result.getProductSales().get(0).getRevenue());
        assertEquals("uuid-b", result.getProductSales().get(1).getProductId());
    }

    @Test
    @DisplayName("Expresses each category's revenue as a percentage share of the total")
    void buildAnalytics_categoryBreakdown_computesPercentageShare() {
        // given
        when(factRepository.revenueByMonth(anyString())).thenReturn(List.of());
        when(factRepository.salesByProduct()).thenReturn(List.of());
        when(factRepository.revenueByCategory()).thenReturn(List.of(
                new CategoryAggregate(1L, "Lighting", BigDecimal.valueOf(750)),
                new CategoryAggregate(2L, "Decor", BigDecimal.valueOf(250))));
        when(factRepository.totalRevenue()).thenReturn(BigDecimal.valueOf(1000));

        // when
        AnalyticsResponse result = analyticsService.buildAnalytics();

        // then
        assertEquals("1", result.getCategoryBreakdown().get(0).getId());
        assertEquals("Lighting", result.getCategoryBreakdown().get(0).getName());
        assertEquals(0, BigDecimal.valueOf(750).compareTo(result.getCategoryBreakdown().get(0).getValue()),
                "expected 750, was " + result.getCategoryBreakdown().get(0).getValue());
        assertEquals(75d, result.getCategoryBreakdown().get(0).getPct());
        assertEquals(25d, result.getCategoryBreakdown().get(1).getPct());
    }

    @Test
    @DisplayName("Groups sales whose product has no known category under Uncategorized")
    void buildAnalytics_unknownCategory_groupsUnderUncategorized() {
        // given
        when(factRepository.revenueByMonth(anyString())).thenReturn(List.of());
        when(factRepository.salesByProduct()).thenReturn(List.of());
        when(factRepository.revenueByCategory())
                .thenReturn(List.of(new CategoryAggregate(null, null, BigDecimal.valueOf(400))));
        when(factRepository.totalRevenue()).thenReturn(BigDecimal.valueOf(400));

        // when
        AnalyticsResponse result = analyticsService.buildAnalytics();

        // then
        assertEquals(1, result.getCategoryBreakdown().size());
        assertEquals("uncategorized", result.getCategoryBreakdown().get(0).getId());
        assertEquals("Uncategorized", result.getCategoryBreakdown().get(0).getName());
        assertEquals(0, BigDecimal.valueOf(400).compareTo(result.getCategoryBreakdown().get(0).getValue()),
                "expected 400, was " + result.getCategoryBreakdown().get(0).getValue());
    }

    @Test
    @DisplayName("Reports a zero share rather than dividing by zero when there is no revenue")
    void buildAnalytics_noRevenue_reportsZeroShareInsteadOfDividingByZero() {
        // given
        when(factRepository.revenueByMonth(anyString())).thenReturn(List.of());
        when(factRepository.salesByProduct()).thenReturn(List.of());
        when(factRepository.revenueByCategory())
                .thenReturn(List.of(new CategoryAggregate(1L, "Lighting", BigDecimal.valueOf(0))));
        when(factRepository.totalRevenue()).thenReturn(BigDecimal.valueOf(0));

        // when
        AnalyticsResponse result = analyticsService.buildAnalytics();

        // then
        assertEquals(0d, result.getCategoryBreakdown().get(0).getPct());
    }
}
