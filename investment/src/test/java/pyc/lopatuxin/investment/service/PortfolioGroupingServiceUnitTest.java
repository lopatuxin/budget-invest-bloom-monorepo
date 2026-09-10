package pyc.lopatuxin.investment.service;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import pyc.lopatuxin.investment.dto.request.PortfolioSort;
import pyc.lopatuxin.investment.dto.response.PortfolioGroupingResult;
import pyc.lopatuxin.investment.dto.response.PositionGroupDto;
import pyc.lopatuxin.investment.dto.response.PositionResponseDto;
import pyc.lopatuxin.investment.dto.response.SectorAllocationDto;
import pyc.lopatuxin.investment.dto.response.SectorGroupDto;
import pyc.lopatuxin.investment.dto.response.SnapshotResult;
import pyc.lopatuxin.investment.entity.enums.SecurityType;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("PortfolioGroupingServiceUnitTest — чистая арифметика группировки портфеля")
class PortfolioGroupingServiceUnitTest {

    private final PortfolioGroupingService service = new PortfolioGroupingService();

    @Test
    @DisplayName("group — порядок видов STOCK, BOND, OFZ, ETF независимо от порядка на входе")
    void group_ordersTypesAsStockBondOfzEtf() {
        List<PositionResponseDto> positions = List.of(
                position("FXRL", SecurityType.ETF, "Финансы", "1", "100", "100"),
                position("SU26238", SecurityType.OFZ, null, "1", "100", "100"),
                position("RU000A", SecurityType.BOND, null, "1", "100", "100"),
                position("SBER", SecurityType.STOCK, "Финансы", "1", "100", "100")
        );
        Map<String, SnapshotResult> snapshots = Map.of(
                "FXRL", snapshot("100", null),
                "SU26238", snapshot("100", null),
                "RU000A", snapshot("100", null),
                "SBER", snapshot("100", null)
        );

        PortfolioGroupingResult result = service.group(positions, snapshots, PortfolioSort.WEIGHT);

        assertThat(result.groups()).extracting(PositionGroupDto::getSecurityType)
                .containsExactly(SecurityType.STOCK, SecurityType.BOND, SecurityType.OFZ, SecurityType.ETF);
    }

    @Test
    @DisplayName("group — только присутствующие виды, отсутствующие не попадают в группы")
    void group_omitsAbsentTypes() {
        List<PositionResponseDto> positions = List.of(
                position("SBER", SecurityType.STOCK, "Финансы", "1", "100", "100")
        );
        Map<String, SnapshotResult> snapshots = Map.of("SBER", snapshot("100", null));

        PortfolioGroupingResult result = service.group(positions, snapshots, PortfolioSort.WEIGHT);

        assertThat(result.groups()).extracting(PositionGroupDto::getSecurityType).containsExactly(SecurityType.STOCK);
    }

    @Test
    @DisplayName("group — секторы акций по убыванию стоимости, «Без сектора» последним")
    void group_sortsStockSectorsByValueDescendingWithNoSectorLast() {
        List<PositionResponseDto> positions = List.of(
                position("LKOH", SecurityType.STOCK, "Нефть и газ", "10", "100", "1000"),
                position("SBER", SecurityType.STOCK, "Финансы", "10", "100", "1000"),
                position("UNKN", SecurityType.STOCK, null, "100", "100", "10000")
        );
        Map<String, SnapshotResult> snapshots = Map.of(
                "LKOH", snapshot("500", null),   // value 5000, largest real sector
                "SBER", snapshot("200", null),   // value 2000
                "UNKN", snapshot("1000", null)   // value 100000, biggest overall, but sector is null
        );

        PortfolioGroupingResult result = service.group(positions, snapshots, PortfolioSort.WEIGHT);

        List<SectorGroupDto> sectors = result.groups().get(0).getSectors();
        assertThat(sectors).extracting(SectorGroupDto::getSector)
                .containsExactly("Нефть и газ", "Финансы", "Без сектора");
    }

    @Test
    @DisplayName("group — режим WEIGHT сортирует по currentValue по убыванию")
    void group_sortWeight_ordersByCurrentValueDescending() {
        PortfolioGroupingResult result = service.group(threeSamesectorPositions(), snapshotsForSorting(), PortfolioSort.WEIGHT);

        assertThat(onlySectorPositions(result)).extracting(PositionResponseDto::getTicker)
                .containsExactly("BIGV", "BIGD", "BIGP");
    }

    @Test
    @DisplayName("group — режим PNL сортирует по pnlPercent по убыванию")
    void group_sortPnl_ordersByPnlPercentDescending() {
        PortfolioGroupingResult result = service.group(threeSamesectorPositions(), snapshotsForSorting(), PortfolioSort.PNL);

        assertThat(onlySectorPositions(result)).extracting(PositionResponseDto::getTicker)
                .containsExactly("BIGP", "BIGD", "BIGV");
    }

    @Test
    @DisplayName("group — режим DAY сортирует по dailyChangePercent по убыванию")
    void group_sortDay_ordersByDailyChangePercentDescending() {
        PortfolioGroupingResult result = service.group(threeSamesectorPositions(), snapshotsForSorting(), PortfolioSort.DAY);

        assertThat(onlySectorPositions(result)).extracting(PositionResponseDto::getTicker)
                .containsExactly("BIGD", "BIGP", "BIGV");
    }

    @Test
    @DisplayName("group — расчёт currentValue/pnl/pnlPercent/dailyChange*/weightPercent по одной позиции")
    void group_computesPerPositionFieldsExactly() {
        PositionResponseDto lkoh = position("LKOH", SecurityType.STOCK, "Нефть и газ", "12", "6500.00", "78000.00");
        SnapshotResult snapshot = new SnapshotResult(new BigDecimal("7180.00"), new BigDecimal("7081.00"), Instant.now(), false);

        PortfolioGroupingResult result = service.group(List.of(lkoh), Map.of("LKOH", snapshot), PortfolioSort.WEIGHT);

        PositionResponseDto enriched = result.positions().get(0);
        assertThat(enriched.getCurrentValue()).isEqualByComparingTo("86160.00");
        assertThat(enriched.getPnl()).isEqualByComparingTo("8160.00");
        assertThat(enriched.getPnlPercent()).isEqualByComparingTo("10.5");
        assertThat(enriched.getDailyChangeAmount()).isEqualByComparingTo("1188.00");
        assertThat(enriched.getDailyChangePercent()).isEqualByComparingTo("1.4");
        assertThat(enriched.getWeightPercent()).isEqualByComparingTo("100.0");
    }

    @Test
    @DisplayName("group — бумага без цены: currentValue/pnl/weightPercent = null, в конце списка, не входит в totalValue")
    void group_unpricedPosition_excludedFromTotalsAndSortedLast() {
        PositionResponseDto priced = position("SBER", SecurityType.STOCK, "Финансы", "1", "100.00", "100.00");
        PositionResponseDto unpriced = position("UNKN", SecurityType.STOCK, "Финансы", "1", "50.00", "50.00");
        Map<String, SnapshotResult> snapshots = Map.of("SBER", snapshot("100.00", null));

        PortfolioGroupingResult result = service.group(List.of(unpriced, priced), snapshots, PortfolioSort.WEIGHT);

        assertThat(result.totalValue()).isEqualByComparingTo("100.00");
        assertThat(result.totalCost()).isEqualByComparingTo("150.00");
        assertThat(result.unpricedCount()).isEqualTo(1);
        List<PositionResponseDto> sectorPositions = result.groups().get(0).getSectors().get(0).getPositions();
        assertThat(sectorPositions).extracting(PositionResponseDto::getTicker).containsExactly("SBER", "UNKN");
        assertThat(sectorPositions.get(1).getCurrentValue()).isNull();
        assertThat(sectorPositions.get(1).getWeightPercent()).isNull();
    }

    @Test
    @DisplayName("group — распределение по видам считается от текущей стоимости; один вид → один сегмент 100%")
    void group_allocationFromCurrentValue_singleTypeIsFullSegment() {
        PositionResponseDto sber = position("SBER", SecurityType.STOCK, "Финансы", "10", "100.00", "1000.00");
        Map<String, SnapshotResult> snapshots = Map.of("SBER", snapshot("150.00", null));

        PortfolioGroupingResult result = service.group(List.of(sber), snapshots, PortfolioSort.WEIGHT);

        assertThat(result.allocation().getByType()).hasSize(1);
        assertThat(result.allocation().getByType().get(0).getPercent()).isEqualByComparingTo("100.0");
        assertThat(result.allocation().getByType().get(0).getValue()).isEqualByComparingTo("1500.00");
    }

    @Test
    @DisplayName("group — распределение по секторам покрывает все виды бумаг, доли секторов дают 100%")
    void group_sectorAllocation_coversAllSecurityTypesAndSumsTo100() {
        List<PositionResponseDto> positions = List.of(
                position("LKOH", SecurityType.STOCK, "Нефть и газ", "10", "100", "1000"),
                position("SU26238", SecurityType.OFZ, "Государственные облигации", "1", "100", "100")
        );
        Map<String, SnapshotResult> snapshots = Map.of(
                "LKOH", snapshot("700", null),   // value 7000
                "SU26238", snapshot("3000", null) // value 3000
        );

        PortfolioGroupingResult result = service.group(positions, snapshots, PortfolioSort.WEIGHT);

        assertThat(result.allocation().getBySector()).extracting(SectorAllocationDto::getSector)
                .containsExactly("Нефть и газ", "Государственные облигации");
        BigDecimal totalPercent = result.allocation().getBySector().stream()
                .map(SectorAllocationDto::getPercent).reduce(BigDecimal.ZERO, BigDecimal::add);
        assertThat(totalPercent).isEqualByComparingTo("100.0");
        // sectorsCount counts only STOCK sectors — OFZ/BOND/ETF are shown as a single
        // sector-less bucket in groups (see buildGroups), so "Государственные облигации"
        // must not inflate the "{n} секторов" caption even though it appears in the
        // top-level sector allocation above.
        assertThat(result.sectorsCount()).isEqualTo(1);
    }

    @Test
    @DisplayName("group — sectorsCount не считает словарный сектор облигаций/ОФЗ — только сектора акций видны как подзаголовки")
    void group_sectorsCount_excludesBondAndOfzSectors() {
        List<PositionResponseDto> positions = List.of(
                position("SU26238", SecurityType.OFZ, "Государственные облигации", "1", "100", "100"),
                position("RU000A", SecurityType.BOND, "Корпоративные облигации", "1", "100", "100")
        );
        Map<String, SnapshotResult> snapshots = Map.of(
                "SU26238", snapshot("100", null),
                "RU000A", snapshot("100", null)
        );

        PortfolioGroupingResult result = service.group(positions, snapshots, PortfolioSort.WEIGHT);

        assertThat(result.sectorsCount()).isZero();
    }

    @Test
    @DisplayName("group — sectorsCount не считает корзину «Без сектора»")
    void group_sectorsCount_excludesNoSectorBucket() {
        List<PositionResponseDto> positions = List.of(
                position("LKOH", SecurityType.STOCK, "Нефть и газ", "10", "100", "1000"),
                position("SBER", SecurityType.STOCK, null, "1", "100", "100")
        );
        Map<String, SnapshotResult> snapshots = Map.of(
                "LKOH", snapshot("500", null),
                "SBER", snapshot("100", null)
        );

        PortfolioGroupingResult result = service.group(positions, snapshots, PortfolioSort.WEIGHT);

        assertThat(result.allocation().getBySector()).extracting(SectorAllocationDto::getSector)
                .containsExactly("Нефть и газ", "Без сектора");
        assertThat(result.sectorsCount()).isEqualTo(1);
    }

    @Test
    @DisplayName("group — позиция с securityType = null не пропадает из allocation.byType и groups")
    void group_positionWithNullSecurityType_isNotDropped() {
        PositionResponseDto unknownType = position("XYZ", null, null, "1", "100", "100");
        PositionResponseDto stock = position("SBER", SecurityType.STOCK, "Финансы", "1", "100", "100");
        Map<String, SnapshotResult> snapshots = Map.of(
                "XYZ", snapshot("100", null),
                "SBER", snapshot("100", null)
        );

        PortfolioGroupingResult result = service.group(List.of(unknownType, stock), snapshots, PortfolioSort.WEIGHT);

        assertThat(result.totalValue()).isEqualByComparingTo("200.00");
        assertThat(result.groups()).extracting(PositionGroupDto::getAssetsCount)
                .containsExactlyInAnyOrder(1, 1);
        assertThat(result.groups().stream().mapToInt(PositionGroupDto::getAssetsCount).sum()).isEqualTo(2);
        List<String> ticketsInGroups = result.groups().stream()
                .flatMap(g -> g.getSectors().stream())
                .flatMap(s -> s.getPositions().stream())
                .map(PositionResponseDto::getTicker)
                .toList();
        assertThat(ticketsInGroups).containsExactlyInAnyOrder("XYZ", "SBER");
    }

    @Test
    @DisplayName("group — ОФЗ/облигации/ETF: одна секция-заглушка с sector = null")
    void group_nonStockTypes_getSingleNullSectorBucket() {
        List<PositionResponseDto> bonds = List.of(
                position("RU000A", SecurityType.BOND, "Какой-то сектор", "1", "100", "100"),
                position("RU000B", SecurityType.BOND, "Другой сектор", "1", "100", "100")
        );
        Map<String, SnapshotResult> snapshots = Map.of(
                "RU000A", snapshot("100", null),
                "RU000B", snapshot("100", null)
        );

        PortfolioGroupingResult result = service.group(bonds, snapshots, PortfolioSort.WEIGHT);

        List<SectorGroupDto> sectors = result.groups().get(0).getSectors();
        assertThat(sectors).hasSize(1);
        assertThat(sectors.get(0).getSector()).isNull();
        assertThat(sectors.get(0).getPositions()).hasSize(2);
    }

    private List<PositionResponseDto> onlySectorPositions(PortfolioGroupingResult result) {
        return result.groups().get(0).getSectors().get(0).getPositions();
    }

    // BIGV wins on currentValue, BIGP wins on pnlPercent, BIGD wins on dailyChangePercent —
    // each of the three positions tops exactly one metric, so WEIGHT/PNL/DAY each yield a
    // different order over the same three positions.
    private List<PositionResponseDto> threeSamesectorPositions() {
        return List.of(
                position("BIGV", SecurityType.STOCK, "Финансы", "1000", "100.00", "100000.00"),
                position("BIGP", SecurityType.STOCK, "Финансы", "1", "10.00", "10.00"),
                position("BIGD", SecurityType.STOCK, "Финансы", "1", "100.00", "100.00")
        );
    }

    private Map<String, SnapshotResult> snapshotsForSorting() {
        return Map.of(
                // currentValue 101000.00 (largest), pnlPercent 1.0, dailyChangePercent 0.1
                "BIGV", new SnapshotResult(new BigDecimal("101.00"), new BigDecimal("100.90"), Instant.now(), false),
                // currentValue 100.00 (smallest), pnlPercent 900.0 (largest), dailyChangePercent 1.0
                "BIGP", new SnapshotResult(new BigDecimal("100.00"), new BigDecimal("99.00"), Instant.now(), false),
                // currentValue 200.00, pnlPercent 100.0, dailyChangePercent 300.0 (largest)
                "BIGD", new SnapshotResult(new BigDecimal("200.00"), new BigDecimal("50.00"), Instant.now(), false)
        );
    }

    private PositionResponseDto position(String ticker, SecurityType type, String sector,
                                         String qty, String avgPrice, String totalCost) {
        return PositionResponseDto.builder()
                .ticker(ticker)
                .securityName(ticker)
                .securityType(type)
                .sector(sector)
                .quantity(new BigDecimal(qty))
                .averagePrice(new BigDecimal(avgPrice))
                .totalCost(new BigDecimal(totalCost))
                .build();
    }

    private SnapshotResult snapshot(String lastPrice, String previousClose) {
        return new SnapshotResult(new BigDecimal(lastPrice),
                previousClose == null ? null : new BigDecimal(previousClose), Instant.now(), false);
    }
}
