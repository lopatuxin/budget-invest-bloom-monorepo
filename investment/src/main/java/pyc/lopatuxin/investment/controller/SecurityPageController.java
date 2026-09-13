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
import pyc.lopatuxin.investment.dto.request.SecurityTickerRequestDto;
import pyc.lopatuxin.investment.dto.response.SecurityPageResponseDto;
import pyc.lopatuxin.investment.service.SecurityPageService;
import pyc.lopatuxin.shared.dto.ApiRequest;
import pyc.lopatuxin.shared.dto.ResponseApi;

@Slf4j
@RestController
@RequestMapping("/api/investment/securities")
@RequiredArgsConstructor
@Tag(name = "Бумага")
public class SecurityPageController {

    private final SecurityPageService securityPageService;

    @PostMapping("/page")
    @ResponseStatus(HttpStatus.OK)
    public ResponseApi<SecurityPageResponseDto> getSecurityPage(
            @RequestBody @Valid ApiRequest<SecurityTickerRequestDto> request) {
        SecurityPageResponseDto page = securityPageService.getSecurityPage(
                request.getUser().getUserId(), request.getData().getTicker());
        return ResponseApi.success("Страница бумаги", page);
    }
}
