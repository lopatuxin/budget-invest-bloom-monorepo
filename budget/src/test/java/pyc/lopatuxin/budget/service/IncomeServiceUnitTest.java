package pyc.lopatuxin.budget.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import pyc.lopatuxin.budget.dto.request.CreateIncomeDto;
import pyc.lopatuxin.budget.dto.request.DeleteIncomeRequestDto;
import pyc.lopatuxin.budget.dto.response.IncomeResponseDto;
import pyc.lopatuxin.budget.entity.Income;
import pyc.lopatuxin.budget.entity.enums.IncomeSource;
import pyc.lopatuxin.budget.mapper.IncomeMapper;
import pyc.lopatuxin.budget.repository.IncomeRepository;

import jakarta.persistence.EntityNotFoundException;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("IncomeServiceUnitTest")
class IncomeServiceUnitTest {

    @Mock
    private IncomeRepository incomeRepository;

    @Mock
    private IncomeMapper incomeMapper;

    @InjectMocks
    private IncomeService incomeService;

    private UUID userId;

    @BeforeEach
    void setUp() {
        userId = UUID.randomUUID();
    }

    @Test
    @DisplayName("Должен создать доход с указанной датой и вернуть корректный IncomeResponseDto")
    void shouldCreateIncomeWithExplicitDateAndReturnCorrectDto() {
        LocalDate explicitDate = LocalDate.of(2026, 3, 15);
        CreateIncomeDto dto = CreateIncomeDto.builder()
                .source(IncomeSource.SALARY)
                .amount(new BigDecimal("50000.00"))
                .description("Зарплата за март")
                .date(explicitDate)
                .build();

        UUID incomeId = UUID.randomUUID();
        when(incomeRepository.save(any(Income.class))).thenAnswer(invocation -> {
            Income saved = invocation.getArgument(0);
            saved.setId(incomeId);
            return saved;
        });

        IncomeResponseDto expectedDto = IncomeResponseDto.builder()
                .id(incomeId)
                .source(IncomeSource.SALARY)
                .sourceName("Зарплата")
                .amount(new BigDecimal("50000.00"))
                .description("Зарплата за март")
                .date(explicitDate)
                .build();
        when(incomeMapper.toDto(any(Income.class))).thenReturn(expectedDto);

        IncomeResponseDto result = incomeService.createIncome(userId, dto);

        assertThat(result).isNotNull();
        assertThat(result.getId()).isEqualTo(incomeId);
        assertThat(result.getSource()).isEqualTo(IncomeSource.SALARY);
        assertThat(result.getSourceName()).isEqualTo("Зарплата");
        assertThat(result.getAmount()).isEqualByComparingTo(new BigDecimal("50000.00"));
        assertThat(result.getDescription()).isEqualTo("Зарплата за март");
        assertThat(result.getDate()).isEqualTo(explicitDate);

        verify(incomeRepository).save(any(Income.class));
        verify(incomeMapper).toDto(any(Income.class));
    }

    @Test
    @DisplayName("Должен использовать текущую дату, если дата не указана в запросе")
    void shouldUseTodayDateWhenDateIsNull() {
        CreateIncomeDto dto = CreateIncomeDto.builder()
                .source(IncomeSource.FREELANCE)
                .amount(new BigDecimal("15000.00"))
                .date(null)
                .build();

        when(incomeRepository.save(any(Income.class))).thenAnswer(invocation -> {
            Income saved = invocation.getArgument(0);
            saved.setId(UUID.randomUUID());
            return saved;
        });
        when(incomeMapper.toDto(any(Income.class))).thenReturn(IncomeResponseDto.builder()
                .date(LocalDate.now())
                .build());

        IncomeResponseDto result = incomeService.createIncome(userId, dto);

        assertThat(result.getDate()).isEqualTo(LocalDate.now());

        ArgumentCaptor<Income> captor = ArgumentCaptor.forClass(Income.class);
        verify(incomeRepository).save(captor.capture());
        assertThat(captor.getValue().getDate()).isEqualTo(LocalDate.now());
    }

    @Test
    @DisplayName("Должен корректно сохранить income с userId и полями из dto")
    void shouldSaveIncomeWithCorrectUserIdAndFields() {
        LocalDate date = LocalDate.of(2026, 4, 1);
        CreateIncomeDto dto = CreateIncomeDto.builder()
                .source(IncomeSource.INVESTMENTS)
                .amount(new BigDecimal("7500.50"))
                .description("Дивиденды")
                .date(date)
                .build();

        when(incomeRepository.save(any(Income.class))).thenAnswer(invocation -> {
            Income saved = invocation.getArgument(0);
            saved.setId(UUID.randomUUID());
            return saved;
        });
        when(incomeMapper.toDto(any(Income.class))).thenReturn(new IncomeResponseDto());

        incomeService.createIncome(userId, dto);

        ArgumentCaptor<Income> captor = ArgumentCaptor.forClass(Income.class);
        verify(incomeRepository).save(captor.capture());

        Income savedIncome = captor.getValue();
        assertThat(savedIncome.getUserId()).isEqualTo(userId);
        assertThat(savedIncome.getSource()).isEqualTo(IncomeSource.INVESTMENTS);
        assertThat(savedIncome.getAmount()).isEqualByComparingTo(new BigDecimal("7500.50"));
        assertThat(savedIncome.getDescription()).isEqualTo("Дивиденды");
        assertThat(savedIncome.getDate()).isEqualTo(date);
    }

    @Test
    @DisplayName("Должен вернуть IncomeResponseDto без описания, если оно не указано")
    void shouldReturnDtoWithNullDescriptionWhenNotProvided() {
        CreateIncomeDto dto = CreateIncomeDto.builder()
                .source(IncomeSource.GIFTS)
                .amount(new BigDecimal("3000.00"))
                .date(LocalDate.of(2026, 1, 10))
                .build();

        when(incomeRepository.save(any(Income.class))).thenAnswer(invocation -> {
            Income saved = invocation.getArgument(0);
            saved.setId(UUID.randomUUID());
            return saved;
        });
        when(incomeMapper.toDto(any(Income.class))).thenReturn(IncomeResponseDto.builder()
                .amount(new BigDecimal("3000.00"))
                .description(null)
                .build());

        IncomeResponseDto result = incomeService.createIncome(userId, dto);

        assertThat(result.getDescription()).isNull();
        assertThat(result.getAmount()).isEqualByComparingTo(new BigDecimal("3000.00"));
    }

    // --- deleteIncome ---

    @Test
    @DisplayName("deleteIncome: должен удалить свой доход")
    void deleteIncome_shouldDeleteOwnIncome() {
        UUID incomeId = UUID.randomUUID();
        Income income = Income.builder()
                .id(incomeId)
                .userId(userId)
                .source(IncomeSource.SALARY)
                .amount(new BigDecimal("50000.00"))
                .date(LocalDate.of(2026, 4, 13))
                .build();

        DeleteIncomeRequestDto request = DeleteIncomeRequestDto.builder().incomeId(incomeId).build();

        when(incomeRepository.findById(incomeId)).thenReturn(Optional.of(income));

        incomeService.deleteIncome(userId, request);

        verify(incomeRepository).delete(income);
    }

    @Test
    @DisplayName("deleteIncome: должен бросить EntityNotFoundException, когда доход не найден")
    void deleteIncome_shouldThrowEntityNotFoundException_whenIncomeNotFound() {
        UUID incomeId = UUID.randomUUID();
        DeleteIncomeRequestDto request = DeleteIncomeRequestDto.builder().incomeId(incomeId).build();

        when(incomeRepository.findById(incomeId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> incomeService.deleteIncome(userId, request))
                .isInstanceOf(EntityNotFoundException.class)
                .hasMessage("Доход не найден");

        verify(incomeRepository, never()).delete(any());
    }

    @Test
    @DisplayName("deleteIncome: должен бросить EntityNotFoundException при попытке удалить чужой доход")
    void deleteIncome_shouldThrowEntityNotFoundException_whenIncomeBelongsToAnotherUser() {
        UUID incomeId = UUID.randomUUID();
        Income income = Income.builder()
                .id(incomeId)
                .userId(UUID.randomUUID())
                .source(IncomeSource.SALARY)
                .amount(new BigDecimal("50000.00"))
                .date(LocalDate.of(2026, 4, 13))
                .build();

        DeleteIncomeRequestDto request = DeleteIncomeRequestDto.builder().incomeId(incomeId).build();

        when(incomeRepository.findById(incomeId)).thenReturn(Optional.of(income));

        assertThatThrownBy(() -> incomeService.deleteIncome(userId, request))
                .isInstanceOf(EntityNotFoundException.class)
                .hasMessage("Доход не найден");

        verify(incomeRepository, never()).delete(any());
    }
}
