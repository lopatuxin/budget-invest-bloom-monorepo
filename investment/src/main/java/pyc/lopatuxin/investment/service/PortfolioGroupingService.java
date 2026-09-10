package pyc.lopatuxin.investment.service;

import org.springframework.stereotype.Service;
import pyc.lopatuxin.investment.dto.request.PortfolioSort;
import pyc.lopatuxin.investment.dto.response.PortfolioAllocationDto;
import pyc.lopatuxin.investment.dto.response.PortfolioGroupingResult;
import pyc.lopatuxin.investment.dto.response.PortfolioTotals;
import pyc.lopatuxin.investment.dto.response.PositionGroupDto;
import pyc.lopatuxin.investment.dto.response.PositionResponseDto;
import pyc.lopatuxin.investment.dto.response.SectorAllocationDto;
import pyc.lopatuxin.investment.dto.response.SectorGroupDto;
import pyc.lopatuxin.investment.dto.response.SnapshotResult;
import pyc.lopatuxin.investment.dto.response.TypeAllocationDto;
import pyc.lopatuxin.investment.entity.enums.SecurityType;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Pure arithmetic over an already-loaded flat position list: no repositories, no I/O.
 * Turns {@link PositionResponseDto} + market snapshots into per-position derived fields
 * (currentValue, pnlPercent, dailyChange*, weightPercent), the type/sector allocation and
 * the nested groups the /investments page renders, plus the portfolio-level totals.
 */
@Service
public class PortfolioGroupingService {

    private static final String NO_SECTOR = "Без сектора";
    private static final List<SecurityType> TYPE_ORDER =
            List.of(SecurityType.STOCK, SecurityType.BOND, SecurityType.OFZ, SecurityType.ETF);

    public PortfolioGroupingResult group(List<PositionResponseDto> positions,
                                         Map<String, SnapshotResult> snapshots,
                                         PortfolioSort sort) {
        PortfolioTotals totals = computeTotals(positions, snapshots);
        PortfolioAllocationDto allocation = buildAllocation(positions, totals.totalValue());
        List<PositionGroupDto> groups = buildGroups(positions, totals.totalValue(), sort);

        return new PortfolioGroupingResult(positions, allocation, groups,
                totals.totalValue(), totals.totalCost(), totals.totalPnl(), totals.totalPnlPercent(),
                totals.dailyPnl(), totals.dailyPnlPercent(), positions.size(), countRealSectors(positions),
                totals.unpricedCount());
    }

    // "{n} секторов" must match what buildGroups actually shows as separate sector subheadings:
    // only STOCK positions get one (see buildGroups — bonds/OFZ/ETF are always a single stub
    // bucket regardless of their sector field), so a bond's or OFZ's dictionary sector (e.g.
    // "Корпоративные облигации") must not inflate the count, same as "Без сектора" must not.
    private int countRealSectors(List<PositionResponseDto> positions) {
        List<PositionResponseDto> stocks = positions.stream()
                .filter(p -> p.getSecurityType() == SecurityType.STOCK)
                .toList();
        return (int) sectorBuckets(stocks).stream()
                .filter(bucket -> !NO_SECTOR.equals(bucket.sector()))
                .count();
    }

    // Just the portfolio-level totals, without building the groups/allocation breakdown —
    // the light path for callers that only need totalValue/totalCost/totalPnl/... (see
    // PortfolioTotals), not the full nested page structure.
    public PortfolioTotals computeTotals(List<PositionResponseDto> positions, Map<String, SnapshotResult> snapshots) {
        for (PositionResponseDto position : positions) {
            enrichWithSnapshot(position, snapshots.get(position.getTicker()));
        }
        BigDecimal totalValue = sumCurrentValue(positions);
        for (PositionResponseDto position : positions) {
            position.setWeightPercent(percentOf(position.getCurrentValue(), totalValue));
        }

        BigDecimal totalCost = positions.stream().map(PositionResponseDto::getTotalCost)
                .reduce(BigDecimal.ZERO, BigDecimal::add).setScale(2, RoundingMode.HALF_UP);
        BigDecimal totalPnl = positions.stream().map(PositionResponseDto::getPnl)
                .filter(Objects::nonNull)
                .reduce(BigDecimal.ZERO, BigDecimal::add).setScale(2, RoundingMode.HALF_UP);
        BigDecimal totalPnlPercent = percentOf(totalPnl, totalCost);
        BigDecimal[] daily = sumDailyChange(positions);
        BigDecimal dailyPnl = daily[0].setScale(2, RoundingMode.HALF_UP);
        BigDecimal dailyPnlPercent = percentOf(dailyPnl, daily[1]);
        int unpricedCount = (int) positions.stream().filter(p -> p.getCurrentPrice() == null).count();

        return new PortfolioTotals(totalValue, totalCost, totalPnl, totalPnlPercent, dailyPnl, dailyPnlPercent, unpricedCount);
    }

    // Package-private: also called directly by PortfolioService.getByTicker to enrich a single
    // position the same way computeTotals enriches every position in a portfolio, instead of
    // duplicating this arithmetic there.
    void enrichWithSnapshot(PositionResponseDto position, SnapshotResult snapshot) {
        if (snapshot == null || snapshot.lastPrice() == null) {
            return;
        }
        BigDecimal currentPrice = snapshot.lastPrice();
        BigDecimal previousClose = snapshot.previousClose();
        BigDecimal quantity = position.getQuantity();
        BigDecimal currentValue = currentPrice.multiply(quantity).setScale(2, RoundingMode.HALF_UP);
        BigDecimal pnl = currentPrice.subtract(position.getAveragePrice())
                .multiply(quantity).setScale(2, RoundingMode.HALF_UP);

        position.setCurrentPrice(currentPrice);
        position.setPreviousClose(previousClose);
        position.setCurrentValue(currentValue);
        position.setPnl(pnl);
        position.setPnlPercent(percentOf(pnl, position.getTotalCost()));
        if (previousClose != null) {
            BigDecimal dailyChangeAmount = currentPrice.subtract(previousClose)
                    .multiply(quantity).setScale(2, RoundingMode.HALF_UP);
            position.setDailyChangeAmount(dailyChangeAmount);
            position.setDailyChangePercent(percentOf(currentPrice.subtract(previousClose), previousClose));
        }
    }

    private BigDecimal sumCurrentValue(List<PositionResponseDto> positions) {
        return positions.stream().map(PositionResponseDto::getCurrentValue)
                .filter(Objects::nonNull)
                .reduce(BigDecimal.ZERO, BigDecimal::add).setScale(2, RoundingMode.HALF_UP);
    }

    // [0] = total daily change amount, [1] = the previous day's total value it is relative to
    private BigDecimal[] sumDailyChange(List<PositionResponseDto> positions) {
        BigDecimal amount = BigDecimal.ZERO;
        BigDecimal previousTotal = BigDecimal.ZERO;
        for (PositionResponseDto position : positions) {
            if (position.getDailyChangeAmount() == null) {
                continue;
            }
            amount = amount.add(position.getDailyChangeAmount());
            previousTotal = previousTotal.add(position.getPreviousClose().multiply(position.getQuantity()));
        }
        return new BigDecimal[]{amount, previousTotal};
    }

    private PortfolioAllocationDto buildAllocation(List<PositionResponseDto> positions, BigDecimal totalValue) {
        Map<SecurityType, List<PositionResponseDto>> byType = groupPreservingOrder(positions,
                PositionResponseDto::getSecurityType, TYPE_ORDER);
        List<TypeAllocationDto> typeAllocations = new ArrayList<>();
        for (Map.Entry<SecurityType, List<PositionResponseDto>> entry : byType.entrySet()) {
            BigDecimal value = sumCurrentValue(entry.getValue());
            typeAllocations.add(TypeAllocationDto.builder()
                    .securityType(entry.getKey())
                    .value(value)
                    .percent(percentOf(value, totalValue))
                    .assetsCount(entry.getValue().size())
                    .build());
        }

        List<SectorAllocationDto> sectorAllocations = new ArrayList<>();
        for (SectorBucket bucket : sectorBuckets(positions)) {
            BigDecimal value = sumCurrentValue(bucket.positions());
            sectorAllocations.add(SectorAllocationDto.builder()
                    .sector(bucket.sector())
                    .value(value)
                    .percent(percentOf(value, totalValue))
                    .assetsCount(bucket.positions().size())
                    .build());
        }
        return PortfolioAllocationDto.builder().byType(typeAllocations).bySector(sectorAllocations).build();
    }

    private List<PositionGroupDto> buildGroups(List<PositionResponseDto> positions, BigDecimal totalValue,
                                               PortfolioSort sort) {
        Map<SecurityType, List<PositionResponseDto>> byType = groupPreservingOrder(positions,
                PositionResponseDto::getSecurityType, TYPE_ORDER);
        List<PositionGroupDto> groups = new ArrayList<>();
        for (Map.Entry<SecurityType, List<PositionResponseDto>> entry : byType.entrySet()) {
            List<PositionResponseDto> typePositions = entry.getValue();
            List<SectorGroupDto> sectors = entry.getKey() == SecurityType.STOCK
                    ? buildStockSectors(typePositions, totalValue, sort)
                    : List.of(buildSingleSector(typePositions, totalValue, sort));
            groups.add(PositionGroupDto.builder()
                    .securityType(entry.getKey())
                    .value(sumCurrentValue(typePositions))
                    .percent(percentOf(sumCurrentValue(typePositions), totalValue))
                    .assetsCount(typePositions.size())
                    .sectors(sectors)
                    .build());
        }
        return groups;
    }

    private List<SectorGroupDto> buildStockSectors(List<PositionResponseDto> stocks, BigDecimal totalValue,
                                                    PortfolioSort sort) {
        List<SectorGroupDto> result = new ArrayList<>();
        for (SectorBucket bucket : sectorBuckets(stocks)) {
            BigDecimal value = sumCurrentValue(bucket.positions());
            result.add(SectorGroupDto.builder()
                    .sector(bucket.sector())
                    .value(value)
                    .percent(percentOf(value, totalValue))
                    .assetsCount(bucket.positions().size())
                    .positions(sortPositions(bucket.positions(), sort))
                    .build());
        }
        return result;
    }

    private SectorGroupDto buildSingleSector(List<PositionResponseDto> typePositions, BigDecimal totalValue,
                                             PortfolioSort sort) {
        BigDecimal value = sumCurrentValue(typePositions);
        return SectorGroupDto.builder()
                .sector(null)
                .value(value)
                .percent(percentOf(value, totalValue))
                .assetsCount(typePositions.size())
                .positions(sortPositions(typePositions, sort))
                .build();
    }

    // Real sectors ordered by value descending, "Без сектора" (null sector) always last.
    // Used both for the top-level sector allocation (all security types, see buildAllocation)
    // and for the stock group's own sector subheadings (buildStockSectors, STOCK only).
    private List<SectorBucket> sectorBuckets(List<PositionResponseDto> positions) {
        Map<String, List<PositionResponseDto>> bySector = new LinkedHashMap<>();
        List<PositionResponseDto> noSector = new ArrayList<>();
        for (PositionResponseDto position : positions) {
            if (position.getSector() == null) {
                noSector.add(position);
                continue;
            }
            bySector.computeIfAbsent(position.getSector(), k -> new ArrayList<>()).add(position);
        }
        List<SectorBucket> buckets = bySector.entrySet().stream()
                .map(e -> new SectorBucket(e.getKey(), e.getValue()))
                .sorted(Comparator.comparing((SectorBucket b) -> sumCurrentValue(b.positions())).reversed())
                .collect(Collectors.toCollection(ArrayList::new));
        if (!noSector.isEmpty()) {
            buckets.add(new SectorBucket(NO_SECTOR, noSector));
        }
        return buckets;
    }

    private List<PositionResponseDto> sortPositions(List<PositionResponseDto> positions, PortfolioSort sort) {
        Comparator<BigDecimal> descendingNullsLast = Comparator.nullsLast(Comparator.reverseOrder());
        Comparator<PositionResponseDto> comparator = switch (sort) {
            case PNL -> Comparator.comparing(PositionResponseDto::getPnlPercent, descendingNullsLast);
            case DAY -> Comparator.comparing(PositionResponseDto::getDailyChangePercent, descendingNullsLast);
            case WEIGHT -> Comparator.comparing(PositionResponseDto::getCurrentValue, descendingNullsLast);
        };
        return positions.stream().sorted(comparator).toList();
    }

    // A position whose key (e.g. securityType from a MOEX response that left it null) is not
    // in `order` still gets a bucket, appended last under a null key, so it stays visible in
    // allocation/groups instead of silently vanishing while still counting toward totalValue.
    private <K> Map<K, List<PositionResponseDto>> groupPreservingOrder(List<PositionResponseDto> positions,
                                                                       Function<PositionResponseDto, K> key,
                                                                       List<K> order) {
        Map<K, List<PositionResponseDto>> grouped = new LinkedHashMap<>();
        for (K k : order) {
            List<PositionResponseDto> matching = positions.stream()
                    .filter(p -> Objects.equals(key.apply(p), k)).toList();
            if (!matching.isEmpty()) {
                grouped.put(k, matching);
            }
        }
        List<PositionResponseDto> unmatched = positions.stream()
                .filter(p -> order.stream().noneMatch(k -> Objects.equals(k, key.apply(p))))
                .toList();
        if (!unmatched.isEmpty()) {
            grouped.put(null, unmatched);
        }
        return grouped;
    }

    public BigDecimal percentOf(BigDecimal part, BigDecimal whole) {
        if (part == null || whole == null || whole.signum() == 0) {
            return null;
        }
        return part.multiply(BigDecimal.valueOf(100)).divide(whole, 1, RoundingMode.HALF_UP);
    }

    private record SectorBucket(String sector, List<PositionResponseDto> positions) {
    }
}
