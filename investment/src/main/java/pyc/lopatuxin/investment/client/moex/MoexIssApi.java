package pyc.lopatuxin.investment.client.moex;

import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.service.annotation.GetExchange;
import org.springframework.web.service.annotation.HttpExchange;

import java.time.LocalDate;

@HttpExchange(accept = "application/json")
public interface MoexIssApi {

    @GetExchange("/securities/{ticker}.json")
    JsonNode getSecurity(@PathVariable("ticker") String ticker,
                         @RequestParam("iss.only") String only,
                         @RequestParam("iss.meta") String meta);

    @GetExchange("/securities.json")
    JsonNode searchSecurities(@RequestParam("q") String query,
                              @RequestParam("limit") int limit,
                              @RequestParam("iss.only") String only,
                              @RequestParam("iss.meta") String meta);

    @GetExchange("/engines/stock/markets/{market}/boards/{board}/securities.json")
    JsonNode listBoardSecurities(@PathVariable("market") String market, @PathVariable("board") String board,
                                 @RequestParam("iss.only") String only,
                                 @RequestParam("iss.meta") String meta,
                                 @RequestParam("securities.columns") String columns);

    @GetExchange("/engines/{engine}/markets/{market}/securities.json")
    JsonNode getMarketData(@PathVariable("engine") String engine, @PathVariable("market") String market,
                           @RequestParam("securities") String securities,
                           @RequestParam("iss.only") String only,
                           @RequestParam("iss.meta") String meta);

    @GetExchange("/history/engines/stock/markets/{market}/securities/{ticker}.json")
    JsonNode getHistory(@PathVariable("market") String market, @PathVariable("ticker") String ticker,
                        @RequestParam("from") LocalDate from, @RequestParam("till") LocalDate till,
                        @RequestParam("start") int start,
                        @RequestParam("iss.only") String only,
                        @RequestParam("iss.meta") String meta);
}
