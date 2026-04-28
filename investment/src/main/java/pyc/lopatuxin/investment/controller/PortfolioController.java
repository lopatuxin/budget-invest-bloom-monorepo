package pyc.lopatuxin.investment.controller;

import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import pyc.lopatuxin.investment.dto.common.ApiRequest;
import pyc.lopatuxin.investment.dto.request.GetPositionByTickerDto;
import pyc.lopatuxin.investment.dto.request.ListTransactionsDto;
import pyc.lopatuxin.investment.dto.response.PortfolioOverviewDto;
import pyc.lopatuxin.investment.dto.response.PositionResponseDto;
import pyc.lopatuxin.investment.dto.response.ResponseApi;
import pyc.lopatuxin.investment.service.PortfolioService;

import org.springframework.http.ResponseEntity;

import java.util.List;

@Slf4j
@RestController
@RequestMapping("/api/investment/portfolio")
@RequiredArgsConstructor
@Tag(name = "Портфель")
public class PortfolioController {

    private final PortfolioService portfolioService;

    @PostMapping("/positions")
    @ResponseStatus(HttpStatus.OK)
    public ResponseApi<List<PositionResponseDto>> listPositions(
            @RequestBody @Valid ApiRequest<ListTransactionsDto> request) {
        List<PositionResponseDto> positions = portfolioService.listPositions(
                request.getUser().getUserId()
        );
        return ResponseApi.success("Список позиций", positions);
    }

    @PostMapping("/positions/by-ticker")
    @ResponseStatus(HttpStatus.OK)
    public ResponseApi<PositionResponseDto> getByTicker(
            @RequestBody @Valid ApiRequest<GetPositionByTickerDto> request) {
        PositionResponseDto position = portfolioService.getByTicker(
                request.getUser().getUserId(),
                request.getData().getTicker()
        );
        return ResponseApi.success("Позиция найдена", position);
    }

    @PostMapping("/overview")
    public ResponseEntity<ResponseApi<PortfolioOverviewDto>> getOverview(
            @RequestBody @Valid ApiRequest<Void> request) {
        PortfolioOverviewDto overview = portfolioService.getOverview(request.getUser().getUserId());
        return ResponseEntity.ok(ResponseApi.success("Обзор портфеля", overview));
    }
}
