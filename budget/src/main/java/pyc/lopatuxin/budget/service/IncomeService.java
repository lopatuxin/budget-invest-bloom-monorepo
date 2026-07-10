package pyc.lopatuxin.budget.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pyc.lopatuxin.budget.dto.request.CreateIncomeDto;
import pyc.lopatuxin.budget.dto.response.IncomeResponseDto;
import pyc.lopatuxin.budget.entity.Income;
import pyc.lopatuxin.budget.entity.enums.IncomeSource;
import pyc.lopatuxin.budget.mapper.IncomeMapper;
import pyc.lopatuxin.budget.repository.IncomeRepository;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

/** Сервис управления доходами пользователя. */
@Slf4j
@Service
@RequiredArgsConstructor
public class IncomeService {

    private final IncomeRepository incomeRepository;
    private final IncomeMapper incomeMapper;

    /**
     * Создаёт новый доход для пользователя.
     *
     * @param userId идентификатор пользователя
     * @param dto    данные нового дохода
     * @return DTO созданного дохода
     */
    @Transactional("budgetTransactionManager")
    public IncomeResponseDto createIncome(UUID userId, CreateIncomeDto dto) {
        LocalDate date = dto.getDate() != null ? dto.getDate() : LocalDate.now();

        Income income = Income.builder()
                .userId(userId)
                .source(dto.getSource())
                .amount(dto.getAmount())
                .description(dto.getDescription())
                .date(date)
                .build();

        income = incomeRepository.save(income);

        log.info("Создан доход {} для пользователя {}", income.getId(), userId);

        return incomeMapper.toDto(income);
    }

    /**
     * Creates an income record bypassing user-facing validation.
     * Used by internal services (e.g. investment entry recording for sell operations).
     *
     * @param userId      identifier of the user
     * @param source      income source
     * @param amount      income amount
     * @param date        income date
     * @param description optional description
     * @param isTransfer  true if this income represents a transfer between assets (e.g. investment sell)
     * @return created income entity
     */
    @Transactional("budgetTransactionManager")
    public Income createInternal(UUID userId, IncomeSource source, BigDecimal amount,
                                 LocalDate date, String description, boolean isTransfer) {
        Income income = Income.builder()
                .userId(userId)
                .source(source)
                .amount(amount)
                .description(description)
                .date(date)
                .isTransfer(isTransfer)
                .build();

        income = incomeRepository.save(income);
        log.info("Created internal income {} for user {}", income.getId(), userId);
        return income;
    }
}
