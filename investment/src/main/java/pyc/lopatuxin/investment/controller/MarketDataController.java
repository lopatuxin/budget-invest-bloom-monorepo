package pyc.lopatuxin.investment.controller;

import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import pyc.lopatuxin.investment.client.moex.dto.MoexSecurityDto;
import pyc.lopatuxin.investment.dto.common.ApiRequest;
import pyc.lopatuxin.investment.dto.request.MarketSearchDto;
import pyc.lopatuxin.investment.dto.request.MarketSecurityDto;
import pyc.lopatuxin.investment.dto.request.SearchCategory;
import pyc.lopatuxin.investment.dto.response.ResponseApi;
import pyc.lopatuxin.investment.service.market.MarketDataService;

import java.util.List;

@RestController
@RequestMapping("/api/investment/market")
@RequiredArgsConstructor
@Tag(name = "Рыночные данные")
public class MarketDataController {

    private final MarketDataService marketDataService;

    @GetMapping("/securities")
    public ResponseEntity<ResponseApi<List<MoexSecurityDto>>> listSecurities(
            @RequestParam(required = false) SearchCategory category) {
        List<MoexSecurityDto> results = marketDataService.listSecurities(category);
        return ResponseEntity.ok(ResponseApi.success("Список бумаг", results));
    }

    @PostMapping("/search")
    public ResponseEntity<ResponseApi<List<MoexSecurityDto>>> search(
            @RequestBody @Valid ApiRequest<MarketSearchDto> request) {
        List<MoexSecurityDto> results = marketDataService.search(
                request.getData().getQ(),
                request.getData().getCategory());
        return ResponseEntity.ok(ResponseApi.success("Результаты поиска", results));
    }

    @PostMapping("/security")
    public ResponseEntity<ResponseApi<MoexSecurityDto>> getSecurity(
            @RequestBody @Valid ApiRequest<MarketSecurityDto> request) {
        MoexSecurityDto dto = marketDataService.getSecurityInfo(request.getData().getTicker().toUpperCase());
        return ResponseEntity.ok(ResponseApi.success("Информация о бумаге", dto));
    }
}
