package pyc.lopatuxin.investment.controller;

import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import pyc.lopatuxin.investment.dto.common.ApiRequest;
import pyc.lopatuxin.investment.dto.request.DateRangeDto;
import pyc.lopatuxin.investment.dto.request.ProjectionRequestDto;
import pyc.lopatuxin.investment.dto.request.SecurityHistoryRequestDto;
import pyc.lopatuxin.investment.dto.response.PortfolioValuePointDto;
import pyc.lopatuxin.investment.dto.response.PricePointDto;
import pyc.lopatuxin.investment.dto.response.ProjectionResultDto;
import pyc.lopatuxin.investment.dto.response.ResponseApi;
import pyc.lopatuxin.investment.dto.response.SeriesResponseDto;
import pyc.lopatuxin.investment.service.AnalyticsService;
import pyc.lopatuxin.investment.service.ProjectionService;

import java.time.LocalDate;
import java.util.UUID;

@Slf4j
@RestController
@RequestMapping("/api/investment/analytics")
@RequiredArgsConstructor
@Tag(name = "Аналитика")
public class AnalyticsController {

    private final AnalyticsService analyticsService;
    private final ProjectionService projectionService;

    @PostMapping("/portfolio/value-history")
    public ResponseApi<SeriesResponseDto<PortfolioValuePointDto>> portfolioValueHistory(
            @RequestBody @Valid ApiRequest<DateRangeDto> request) {
        DateRangeDto dto = request.getData();
        LocalDate from = dto.getFrom() != null ? dto.getFrom() : LocalDate.now().minusYears(1);
        LocalDate to = dto.getTo() != null ? dto.getTo() : LocalDate.now();
        validateDateRange(from, to);
        SeriesResponseDto<PortfolioValuePointDto> result = analyticsService.portfolioValueHistory(
                request.getUser().getUserId(), from, to);
        return ResponseApi.success("История стоимости портфеля", result);
    }

    @PostMapping("/security/price-history")
    public ResponseApi<SeriesResponseDto<PricePointDto>> securityPriceHistory(
            @RequestBody @Valid ApiRequest<SecurityHistoryRequestDto> request) {
        SecurityHistoryRequestDto dto = request.getData();
        LocalDate from = dto.getFrom() != null ? dto.getFrom() : LocalDate.now().minusYears(1);
        LocalDate to = dto.getTo() != null ? dto.getTo() : LocalDate.now();
        validateDateRange(from, to);
        SeriesResponseDto<PricePointDto> result = analyticsService.securityPriceHistory(dto.getTicker(), from, to);
        return ResponseApi.success("История цен инструмента", result);
    }

    @PostMapping("/projection")
    public ResponseApi<ProjectionResultDto> projection(
            @RequestBody @Valid ApiRequest<ProjectionRequestDto> request) {
        UUID userId = request.getUser().getUserId();
        return ResponseApi.success(projectionService.project(userId, request.getData()));
    }

    private void validateDateRange(LocalDate from, LocalDate to) {
        if (from.isAfter(to)) {
            throw new IllegalArgumentException("Дата начала не может быть позже даты окончания");
        }
    }
}
