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
import pyc.lopatuxin.investment.dto.request.CreateTransactionDto;
import pyc.lopatuxin.investment.dto.request.DeleteTransactionDto;
import pyc.lopatuxin.investment.dto.request.ListTransactionsDto;
import pyc.lopatuxin.investment.dto.response.ResponseApi;
import pyc.lopatuxin.investment.dto.response.TransactionResponseDto;
import pyc.lopatuxin.investment.service.TransactionService;

import java.util.List;

@Slf4j
@RestController
@RequestMapping("/api/investment/transactions")
@RequiredArgsConstructor
@Tag(name = "Сделки")
public class TransactionController {

    private final TransactionService transactionService;

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ResponseApi<TransactionResponseDto> create(
            @RequestBody @Valid ApiRequest<CreateTransactionDto> request) {
        TransactionResponseDto dto = transactionService.create(
                request.getUser().getUserId(),
                request.getData()
        );
        return ResponseApi.created("Сделка добавлена", dto);
    }

    @PostMapping("/list")
    @ResponseStatus(HttpStatus.OK)
    public ResponseApi<List<TransactionResponseDto>> list(
            @RequestBody @Valid ApiRequest<ListTransactionsDto> request) {
        List<TransactionResponseDto> list = transactionService.list(
                request.getUser().getUserId(),
                request.getData().getTicker()
        );
        return ResponseApi.success("Список сделок", list);
    }

    @PostMapping("/delete")
    @ResponseStatus(HttpStatus.OK)
    public ResponseApi<Void> delete(
            @RequestBody @Valid ApiRequest<DeleteTransactionDto> request) {
        transactionService.delete(
                request.getUser().getUserId(),
                request.getData().getId()
        );
        return ResponseApi.success("Сделка удалена", null);
    }
}
