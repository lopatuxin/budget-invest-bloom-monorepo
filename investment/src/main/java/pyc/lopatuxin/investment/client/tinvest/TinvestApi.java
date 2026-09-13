package pyc.lopatuxin.investment.client.tinvest;

import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.service.annotation.HttpExchange;
import org.springframework.web.service.annotation.PostExchange;

@HttpExchange(accept = "application/json", contentType = "application/json")
public interface TinvestApi {

    @PostExchange("/tinkoff.public.invest.api.contract.v1.InstrumentsService/FindInstrument")
    TinvestFindInstrumentResponse findInstrument(@RequestBody TinvestFindInstrumentRequest request);

    @PostExchange("/tinkoff.public.invest.api.contract.v1.InstrumentsService/GetDividends")
    TinvestDividendsResponse getDividends(@RequestBody TinvestDividendsRequest request);

    @PostExchange("/tinkoff.public.invest.api.contract.v1.InstrumentsService/GetBondCoupons")
    TinvestBondCouponsResponse getBondCoupons(@RequestBody TinvestBondCouponsRequest request);
}
