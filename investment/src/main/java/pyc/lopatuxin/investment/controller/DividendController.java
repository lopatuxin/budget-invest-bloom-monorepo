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
import pyc.lopatuxin.investment.dto.request.CreateDividendDto;
import pyc.lopatuxin.investment.dto.request.DeleteDividendDto;
import pyc.lopatuxin.investment.dto.response.SecurityDividendDto;
import pyc.lopatuxin.investment.service.DividendManualService;
import pyc.lopatuxin.shared.dto.ApiRequest;
import pyc.lopatuxin.shared.dto.ResponseApi;

@Slf4j
@RestController
@RequestMapping("/api/investment/dividends")
@RequiredArgsConstructor
@Tag(name = "Дивиденды")
public class DividendController {

    private final DividendManualService dividendManualService;

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ResponseApi<SecurityDividendDto> create(@RequestBody @Valid ApiRequest<CreateDividendDto> request) {
        SecurityDividendDto dto = dividendManualService.create(request.getUser().getUserId(), request.getData());
        return ResponseApi.created("Дивиденд добавлен", dto);
    }

    @PostMapping("/delete")
    public ResponseApi<Void> delete(@RequestBody @Valid ApiRequest<DeleteDividendDto> request) {
        dividendManualService.delete(request.getUser().getUserId(), request.getData().getDividendId());
        return ResponseApi.success("Дивиденд удалён", null);
    }
}
