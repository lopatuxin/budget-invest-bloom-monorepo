package pyc.lopatuxin.budget.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import pyc.lopatuxin.budget.dto.request.InvestmentEntryRequestDto;
import pyc.lopatuxin.budget.dto.response.InvestmentEntryResponseDto;
import pyc.lopatuxin.shared.port.EntryType;
import pyc.lopatuxin.shared.port.InvestmentBudgetSync;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * In-process implementation of the {@link InvestmentBudgetSync} port on the budget side.
 * Delegates to {@link InvestmentEntryService}, whose methods already carry the transactional
 * boundary — this adapter intentionally does not add its own {@code @Transactional}.
 */
@Component
@RequiredArgsConstructor
public class InvestmentBudgetSyncAdapter implements InvestmentBudgetSync {

    private final InvestmentEntryService investmentEntryService;

    @Override
    public UUID createEntry(UUID userId, EntryType type, BigDecimal amount, Instant executedAt) {
        InvestmentEntryRequestDto dto = InvestmentEntryRequestDto.builder()
                .type(map(type))
                .amount(amount)
                .executedAt(executedAt)
                .build();
        InvestmentEntryResponseDto result = investmentEntryService.create(userId, dto);
        return result.getEntryId();
    }

    @Override
    public void deleteEntry(UUID userId, UUID budgetEntryId, EntryType type) {
        investmentEntryService.delete(userId, budgetEntryId, map(type));
    }

    private InvestmentEntryRequestDto.EntryType map(EntryType type) {
        return InvestmentEntryRequestDto.EntryType.valueOf(type.name());
    }
}
