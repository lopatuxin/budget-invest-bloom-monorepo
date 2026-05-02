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
import pyc.lopatuxin.investment.dto.request.EmptyRequestDto;
import pyc.lopatuxin.investment.dto.request.GetPositionByTickerDto;
import pyc.lopatuxin.investment.dto.response.PortfolioPageResponseDto;
import pyc.lopatuxin.investment.dto.response.PositionResponseDto;
import pyc.lopatuxin.investment.dto.response.ResponseApi;
import pyc.lopatuxin.investment.service.PortfolioService;

@Slf4j
@RestController
@RequestMapping("/api/investment/portfolio")
@RequiredArgsConstructor
@Tag(name = "Портфель")
public class PortfolioController {

    private final PortfolioService portfolioService;

    @PostMapping("/page")
    @ResponseStatus(HttpStatus.OK)
    public ResponseApi<PortfolioPageResponseDto> getPortfolioPage(
            @RequestBody @Valid ApiRequest<EmptyRequestDto> request) {
        PortfolioPageResponseDto page = portfolioService.getPortfolioPage(
                request.getUser().getUserId()
        );
        return ResponseApi.success("Страница портфеля", page);
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
}
