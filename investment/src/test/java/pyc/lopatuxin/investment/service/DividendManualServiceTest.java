package pyc.lopatuxin.investment.service;

import jakarta.persistence.EntityNotFoundException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;
import pyc.lopatuxin.investment.dto.request.CreateDividendDto;
import pyc.lopatuxin.investment.dto.response.SecurityDividendDto;
import pyc.lopatuxin.investment.entity.Dividend;
import pyc.lopatuxin.investment.entity.Security;
import pyc.lopatuxin.investment.entity.enums.DividendSource;
import pyc.lopatuxin.investment.entity.enums.DividendStatus;
import pyc.lopatuxin.investment.entity.enums.HistoryStatus;
import pyc.lopatuxin.investment.entity.enums.PayoutKind;
import pyc.lopatuxin.investment.entity.enums.SecurityType;
import pyc.lopatuxin.investment.exception.DividendAlreadyExistsException;
import pyc.lopatuxin.investment.repository.DividendRepository;
import pyc.lopatuxin.investment.repository.PositionRepository;
import pyc.lopatuxin.investment.repository.SecurityRepository;

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
@DisplayName("DividendManualServiceTest")
class DividendManualServiceTest {

    @Mock
    private DividendRepository dividendRepository;

    @Mock
    private SecurityRepository securityRepository;

    @Mock
    private PositionRepository positionRepository;

    @InjectMocks
    private DividendManualService dividendManualService;

    private final UUID userId = UUID.randomUUID();

    private Security sber() {
        return Security.builder().ticker("SBER").name("Сбербанк").type(SecurityType.STOCK)
                .historyStatus(HistoryStatus.READY).build();
    }

    @Test
    @DisplayName("create — создаёт дивиденд с source=MANUAL и статусом по дате отсечки")
    void create_savesManualDividend() {
        CreateDividendDto dto = CreateDividendDto.builder()
                .ticker("sber").recordDate(LocalDate.now().plusDays(5))
                .amountPerShare(new BigDecimal("34.84")).currency("rub").build();
        when(positionRepository.existsByUserIdAndSecurity_Ticker(userId, "SBER")).thenReturn(true);
        when(dividendRepository.existsBySecurity_TickerAndRecordDate("SBER", dto.getRecordDate())).thenReturn(false);
        when(securityRepository.findById("SBER")).thenReturn(Optional.of(sber()));
        when(dividendRepository.saveAndFlush(any())).thenAnswer(inv -> inv.getArgument(0));

        SecurityDividendDto result = dividendManualService.create(userId, dto);

        ArgumentCaptor<Dividend> captor = ArgumentCaptor.forClass(Dividend.class);
        verify(dividendRepository).saveAndFlush(captor.capture());
        Dividend saved = captor.getValue();
        assertThat(saved.getSource()).isEqualTo(DividendSource.MANUAL);
        assertThat(saved.getCurrency()).isEqualTo("RUB");
        assertThat(saved.getStatus()).isEqualTo(DividendStatus.ANNOUNCED);
        assertThat(saved.getKind()).isEqualTo(PayoutKind.DIVIDEND);
        assertThat(result.getSource()).isEqualTo(DividendSource.MANUAL);
        assertThat(result.getKind()).isEqualTo(PayoutKind.DIVIDEND);
    }

    @Test
    @DisplayName("create — облигация/ОФЗ → вид выплаты COUPON, а не DIVIDEND")
    void create_bondOrOfz_savesWithCouponKind() {
        Security ofz = Security.builder().ticker("SU26219RMFS4").name("ОФЗ 26219").type(SecurityType.OFZ)
                .historyStatus(HistoryStatus.READY).build();
        CreateDividendDto dto = CreateDividendDto.builder()
                .ticker("SU26219RMFS4").recordDate(LocalDate.now().plusDays(5))
                .amountPerShare(new BigDecimal("38.64")).currency("rub").build();
        when(positionRepository.existsByUserIdAndSecurity_Ticker(userId, "SU26219RMFS4")).thenReturn(true);
        when(dividendRepository.existsBySecurity_TickerAndRecordDate("SU26219RMFS4", dto.getRecordDate())).thenReturn(false);
        when(securityRepository.findById("SU26219RMFS4")).thenReturn(Optional.of(ofz));
        when(dividendRepository.saveAndFlush(any())).thenAnswer(inv -> inv.getArgument(0));

        SecurityDividendDto result = dividendManualService.create(userId, dto);

        ArgumentCaptor<Dividend> captor = ArgumentCaptor.forClass(Dividend.class);
        verify(dividendRepository).saveAndFlush(captor.capture());
        assertThat(captor.getValue().getKind()).isEqualTo(PayoutKind.COUPON);
        assertThat(result.getKind()).isEqualTo(PayoutKind.COUPON);
    }

    @Test
    @DisplayName("create — дата отсечки уже занята → 409 DIVIDEND_EXISTS")
    void create_duplicateRecordDate_throwsAlreadyExists() {
        CreateDividendDto dto = CreateDividendDto.builder()
                .ticker("SBER").recordDate(LocalDate.of(2026, 7, 18))
                .amountPerShare(new BigDecimal("34.84")).build();
        when(positionRepository.existsByUserIdAndSecurity_Ticker(userId, "SBER")).thenReturn(true);
        when(dividendRepository.existsBySecurity_TickerAndRecordDate("SBER", dto.getRecordDate())).thenReturn(true);

        assertThatThrownBy(() -> dividendManualService.create(userId, dto))
                .isInstanceOf(DividendAlreadyExistsException.class);
        verify(dividendRepository, never()).saveAndFlush(any());
    }

    // A mocked repository cannot reproduce the actual bug this guards against: with an
    // app-generated id (@GeneratedValue(UUID)), a plain save() only queues the INSERT and lets
    // Hibernate defer it to the transaction's commit-time flush — outside this method's own
    // try/catch — so mocking save() to throw here always passed, fix or no fix. This only checks
    // that the service calls saveAndFlush (which forces the INSERT, and therefore the constraint
    // violation, inside the try block) rather than save; the genuine concurrent-request race is
    // covered by DividendManualServiceConcurrencyIT against a real database.
    @Test
    @DisplayName("create — уникальный индекс сработал при saveAndFlush → 409 DIVIDEND_EXISTS вместо ошибки целостности")
    void create_uniqueConstraintViolationOnSaveAndFlush_throwsAlreadyExists() {
        CreateDividendDto dto = CreateDividendDto.builder()
                .ticker("SBER").recordDate(LocalDate.of(2026, 7, 18))
                .amountPerShare(new BigDecimal("34.84")).build();
        when(positionRepository.existsByUserIdAndSecurity_Ticker(userId, "SBER")).thenReturn(true);
        when(dividendRepository.existsBySecurity_TickerAndRecordDate("SBER", dto.getRecordDate())).thenReturn(false);
        when(securityRepository.findById("SBER")).thenReturn(Optional.of(sber()));
        when(dividendRepository.saveAndFlush(any())).thenThrow(new DataIntegrityViolationException("duplicate key value violates unique constraint \"uq_dividends_ticker_record_date\""));

        assertThatThrownBy(() -> dividendManualService.create(userId, dto))
                .isInstanceOf(DividendAlreadyExistsException.class);
        verify(dividendRepository, never()).save(any());
    }

    @Test
    @DisplayName("create — нарушение другого ограничения не выдаётся за дубль даты отсечки")
    void create_unrelatedIntegrityViolation_isNotReportedAsDuplicate() {
        CreateDividendDto dto = CreateDividendDto.builder()
                .ticker("SBER").recordDate(LocalDate.of(2026, 7, 18))
                .amountPerShare(new BigDecimal("34.84")).build();
        when(positionRepository.existsByUserIdAndSecurity_Ticker(userId, "SBER")).thenReturn(true);
        when(dividendRepository.existsBySecurity_TickerAndRecordDate("SBER", dto.getRecordDate())).thenReturn(false);
        when(securityRepository.findById("SBER")).thenReturn(Optional.of(sber()));
        when(dividendRepository.saveAndFlush(any()))
                .thenThrow(new DataIntegrityViolationException("value too long for type character varying(3)"));

        assertThatThrownBy(() -> dividendManualService.create(userId, dto))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    @DisplayName("create — бумага не в портфеле пользователя → 404")
    void create_tickerNotInUserPortfolio_throwsNotFound() {
        CreateDividendDto dto = CreateDividendDto.builder()
                .ticker("SBER").recordDate(LocalDate.now())
                .amountPerShare(new BigDecimal("10")).build();
        when(positionRepository.existsByUserIdAndSecurity_Ticker(userId, "SBER")).thenReturn(false);

        assertThatThrownBy(() -> dividendManualService.create(userId, dto))
                .isInstanceOf(EntityNotFoundException.class);
        verify(dividendRepository, never()).saveAndFlush(any());
    }

    @Test
    @DisplayName("delete — удаляет только MANUAL дивиденд")
    void delete_removesManualDividend() {
        UUID id = UUID.randomUUID();
        Dividend manual = Dividend.builder().id(id).security(sber())
                .recordDate(LocalDate.now()).amountPerShare(new BigDecimal("10"))
                .currency("RUB").status(DividendStatus.ANNOUNCED).source(DividendSource.MANUAL).build();
        when(dividendRepository.findById(id)).thenReturn(Optional.of(manual));
        when(positionRepository.existsByUserIdAndSecurity_Ticker(userId, "SBER")).thenReturn(true);

        dividendManualService.delete(userId, id);

        verify(dividendRepository).delete(manual);
    }

    @Test
    @DisplayName("delete — попытка удалить синхронизированный (не MANUAL) дивиденд → 400")
    void delete_nonManualDividend_throwsBadRequest() {
        UUID id = UUID.randomUUID();
        Dividend tinvestDividend = Dividend.builder().id(id).security(sber())
                .recordDate(LocalDate.now()).amountPerShare(new BigDecimal("10"))
                .currency("RUB").status(DividendStatus.ANNOUNCED).source(DividendSource.TINVEST).build();
        when(dividendRepository.findById(id)).thenReturn(Optional.of(tinvestDividend));
        when(positionRepository.existsByUserIdAndSecurity_Ticker(userId, "SBER")).thenReturn(true);

        assertThatThrownBy(() -> dividendManualService.delete(userId, id))
                .isInstanceOf(IllegalArgumentException.class);
        verify(dividendRepository, never()).delete(any());
    }

    @Test
    @DisplayName("delete — дивиденд бумаги, которой нет в портфеле пользователя → 404")
    void delete_tickerNotInUserPortfolio_throwsNotFound() {
        UUID id = UUID.randomUUID();
        Dividend manual = Dividend.builder().id(id).security(sber())
                .recordDate(LocalDate.now()).amountPerShare(new BigDecimal("10"))
                .currency("RUB").status(DividendStatus.ANNOUNCED).source(DividendSource.MANUAL).build();
        when(dividendRepository.findById(id)).thenReturn(Optional.of(manual));
        when(positionRepository.existsByUserIdAndSecurity_Ticker(userId, "SBER")).thenReturn(false);

        assertThatThrownBy(() -> dividendManualService.delete(userId, id))
                .isInstanceOf(EntityNotFoundException.class);
        verify(dividendRepository, never()).delete(any());
    }

    @Test
    @DisplayName("delete — дивиденд не найден → 404")
    void delete_dividendNotFound_throwsNotFound() {
        UUID id = UUID.randomUUID();
        when(dividendRepository.findById(id)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> dividendManualService.delete(userId, id))
                .isInstanceOf(EntityNotFoundException.class);
    }
}
