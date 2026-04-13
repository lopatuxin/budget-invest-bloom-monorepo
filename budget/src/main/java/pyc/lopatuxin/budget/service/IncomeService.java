package pyc.lopatuxin.budget.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pyc.lopatuxin.budget.dto.request.CreateIncomeDto;
import pyc.lopatuxin.budget.dto.response.IncomeResponseDto;
import pyc.lopatuxin.budget.entity.Income;
import pyc.lopatuxin.budget.mapper.IncomeMapper;
import pyc.lopatuxin.budget.repository.IncomeRepository;

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
    @Transactional
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
}
