package pyc.lopatuxin.investment.client.moex;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import pyc.lopatuxin.investment.dto.response.MoexCandleDto;
import pyc.lopatuxin.investment.dto.response.MoexDividendDto;
import pyc.lopatuxin.investment.dto.response.MoexSecurityDto;
import pyc.lopatuxin.investment.dto.response.MoexSnapshotDto;
import pyc.lopatuxin.investment.entity.enums.SecurityType;
import pyc.lopatuxin.investment.service.market.MoexResponseParser;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

@Slf4j
@Component
@RequiredArgsConstructor
public class MoexIssClient {

    private static final String META_OFF = "off";
    private static final String ONLY_DESCRIPTION_SECURITIES = "description,securities";
    private static final String ONLY_SECURITIES = "securities";
    private static final String ONLY_MARKETDATA_SECURITIES = "marketdata,securities";
    private static final String ONLY_HISTORY = "history,history.cursor";
    private static final String ONLY_DIVIDENDS = "dividends";
    private static final String BOARD_COLUMNS = "SECID,BOARDID,SHORTNAME,STATUS";
    private static final int SEARCH_LIMIT = 20;
    private static final int MAX_HISTORY_ITERATIONS = 200;

    private final MoexIssApi api;
    private final MoexResilience resilience;

    public Optional<MoexSecurityDto> fetchSecurity(String ticker) {
        return resilience.execute("fetchSecurity",
                () -> MoexResponseParser.parseSecurity(api.getSecurity(ticker, ONLY_DESCRIPTION_SECURITIES, META_OFF), ticker));
    }

    public Map<String, MoexSnapshotDto> fetchSnapshots(Collection<String> tickers) {
        Objects.requireNonNull(tickers, "tickers");
        if (tickers.isEmpty()) {
            return Collections.emptyMap();
        }
        String csv = String.join(",", tickers);
        return resilience.execute("fetchSnapshots", () -> {
            Map<String, MoexSnapshotDto> result = new HashMap<>();
            result.putAll(fetchMarketData(csv, "stock", "shares"));
            result.putAll(fetchMarketData(csv, "stock", "bonds"));
            return result;
        });
    }

    public List<MoexSecurityDto> searchSecurities(String query) {
        return resilience.execute("searchSecurities",
                () -> MoexResponseParser.parseSearchResults(api.searchSecurities(query, SEARCH_LIMIT, ONLY_SECURITIES, META_OFF)));
    }

    public List<MoexSecurityDto> listBoardSecurities(String market, String board, SecurityType securityType) {
        return resilience.execute("listBoardSecurities",
                () -> MoexResponseParser.parseBoardSecurities(
                        api.listBoardSecurities(market, board, ONLY_SECURITIES, META_OFF, BOARD_COLUMNS), securityType));
    }

    public List<MoexCandleDto> fetchHistory(String ticker, LocalDate from, LocalDate to) {
        return resilience.execute("fetchHistory", () -> {
            List<MoexCandleDto> result = fetchHistoryFromMarket(ticker, "shares", from, to);
            if (result.isEmpty()) {
                result = fetchHistoryFromMarket(ticker, "bonds", from, to);
            }
            return result;
        });
    }

    public List<MoexDividendDto> fetchDividends(String ticker) {
        return resilience.execute("fetchDividends",
                () -> MoexResponseParser.parseDividends(api.getDividends(ticker, ONLY_DIVIDENDS, META_OFF)));
    }

    private List<MoexCandleDto> fetchHistoryFromMarket(String ticker, String market, LocalDate from, LocalDate to) {
        try {
            List<MoexCandleDto> all = new ArrayList<>();
            int start = 0;
            int iterations = 0;
            while (iterations < MAX_HISTORY_ITERATIONS) {
                var root = api.getHistory(market, ticker, from, to, start, ONLY_HISTORY, META_OFF);
                List<MoexCandleDto> page = MoexResponseParser.parseHistoryPage(root, ticker);
                all.addAll(page);
                int[] cursor = MoexResponseParser.parseHistoryCursor(root);
                if (page.isEmpty() || cursor == null || cursor[0] + cursor[2] >= cursor[1]) {
                    return all;
                }
                start = cursor[0] + cursor[2];
                iterations++;
            }
            log.warn("history pagination hit max iterations limit {} for {} on market {}",
                    MAX_HISTORY_ITERATIONS, ticker, market);
            return all;
        } catch (MoexNotFoundException e) {
            log.debug("History not found for {}/{}", ticker, market);
            return Collections.emptyList();
        }
    }

    private Map<String, MoexSnapshotDto> fetchMarketData(String csv, String engine, String market) {
        try {
            var root = api.getMarketData(engine, market, csv, ONLY_MARKETDATA_SECURITIES, META_OFF);
            return MoexResponseParser.parseMarketData(root);
        } catch (MoexNotFoundException e) {
            log.debug("Market data not found for {}/{}", engine, market);
            return Collections.emptyMap();
        }
    }
}
