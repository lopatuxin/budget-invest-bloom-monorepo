package pyc.lopatuxin.investment.client;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import pyc.lopatuxin.investment.client.dto.BudgetApiResponse;
import pyc.lopatuxin.investment.client.dto.BudgetEntryResponse;
import pyc.lopatuxin.investment.entity.enums.TransactionType;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;

@Slf4j
@Component
@RequiredArgsConstructor
public class BudgetClient {

    private final RestClient budgetRestClient;

    public UUID createInvestmentEntry(UUID userId, TransactionType type, BigDecimal amount, Instant executedAt) {
        Map<String, Object> body = buildRequest(userId, Map.of(
                "type", type.name(),
                "amount", amount,
                "executedAt", executedAt.toString()
        ));
        BudgetApiResponse<BudgetEntryResponse> response = budgetRestClient.post()
                .uri("/api/budget/internal/investment-entry")
                .contentType(MediaType.APPLICATION_JSON)
                .body(body)
                .retrieve()
                .onStatus(status -> status.isError(), (req, res) -> {
                    throw new BudgetClientException(
                            "Budget service error on createInvestmentEntry: HTTP " + res.getStatusCode(),
                            res.getStatusCode().value());
                })
                .body(new ParameterizedTypeReference<BudgetApiResponse<BudgetEntryResponse>>() {});
        if (response == null || response.getBody() == null || response.getBody().getEntryId() == null) {
            throw new BudgetClientException("Budget response missing entryId");
        }
        UUID entryId = response.getBody().getEntryId();
        log.debug("Created budget entry: entryId={}, userId={}, type={}", entryId, userId, type);
        return entryId;
    }

    public void deleteInvestmentEntry(UUID userId, UUID entryId, TransactionType type) {
        Map<String, Object> body = buildRequest(userId, Map.of(
                "entryId", entryId.toString(),
                "type", type.name()
        ));
        budgetRestClient.post()
                .uri("/api/budget/internal/investment-entry/delete")
                .contentType(MediaType.APPLICATION_JSON)
                .body(body)
                .retrieve()
                .onStatus(status -> status.isError(), (req, res) -> {
                    throw new BudgetClientException(
                            "Budget service error on deleteInvestmentEntry: HTTP " + res.getStatusCode(),
                            res.getStatusCode().value());
                })
                .toBodilessEntity();
        log.debug("Deleted budget entry: entryId={}, userId={}, type={}", entryId, userId, type);
    }

    private Map<String, Object> buildRequest(UUID userId, Map<String, Object> data) {
        return Map.of(
                "user", Map.of("userId", userId.toString()),
                "data", data
        );
    }
}
