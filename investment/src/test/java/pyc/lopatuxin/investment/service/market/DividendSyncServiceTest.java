package pyc.lopatuxin.investment.service.market;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import pyc.lopatuxin.investment.client.moex.MoexIssClient;
import pyc.lopatuxin.investment.client.moex.MoexUnavailableException;
import pyc.lopatuxin.investment.client.moex.dto.MoexDividendDto;
import pyc.lopatuxin.investment.entity.Dividend;
import pyc.lopatuxin.investment.entity.Security;
import pyc.lopatuxin.investment.entity.enums.DividendStatus;
import pyc.lopatuxin.investment.entity.enums.HistoryStatus;
import pyc.lopatuxin.investment.entity.enums.SecurityType;
import pyc.lopatuxin.investment.repository.DividendRepository;
import pyc.lopatuxin.investment.repository.SecurityRepository;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("DividendSyncServiceTest")
class DividendSyncServiceTest {

    @Mock
    private MoexIssClient moexIssClient;

    @Mock
    private DividendRepository dividendRepository;

    @Mock
    private SecurityRepository securityRepository;

    @InjectMocks
    private DividendSyncService dividendSyncService;

    private Security sber;

    @BeforeEach
    void setUp() {
        sber = Security.builder()
                .ticker("SBER")
                .name("Сбербанк")
                .type(SecurityType.STOCK)
                .historyStatus(HistoryStatus.READY)
                .build();
    }

    @Test
    @DisplayName("syncDividends — новый дивиденд → сохраняется один Dividend")
    void syncDividends_savesNewDividend() {
        MoexDividendDto dto = new MoexDividendDto();
        dto.setSecid("SBER");
        dto.setRegistryCloseDate(LocalDate.of(2023, 5, 15));
        dto.setDividendPaymentDate(LocalDate.of(2023, 7, 21));
        dto.setValue(new BigDecimal("25.0"));
        dto.setCurrencyId("RUB");

        when(moexIssClient.fetchDividends("SBER")).thenReturn(List.of(dto));
        when(securityRepository.findById("SBER")).thenReturn(Optional.of(sber));
        when(dividendRepository.existsBySecurity_TickerAndRecordDate("SBER", LocalDate.of(2023, 5, 15)))
                .thenReturn(false);

        dividendSyncService.syncDividends("SBER");

        ArgumentCaptor<Dividend> captor = ArgumentCaptor.forClass(Dividend.class);
        verify(dividendRepository).save(captor.capture());
        Dividend saved = captor.getValue();
        assertThat(saved.getSecurity()).isEqualTo(sber);
        assertThat(saved.getAmountPerShare()).isEqualByComparingTo(new BigDecimal("25.0"));
        assertThat(saved.getCurrency()).isEqualTo("RUB");
        assertThat(saved.getRecordDate()).isEqualTo(LocalDate.of(2023, 5, 15));
    }

    @Test
    @DisplayName("syncDividends — дивиденд уже существует → save не вызывается")
    void syncDividends_skipsExisting_whenAlreadySaved() {
        MoexDividendDto dto = new MoexDividendDto();
        dto.setSecid("SBER");
        dto.setRegistryCloseDate(LocalDate.of(2023, 5, 15));
        dto.setDividendPaymentDate(LocalDate.of(2023, 7, 21));
        dto.setValue(new BigDecimal("25.0"));
        dto.setCurrencyId("RUB");

        when(moexIssClient.fetchDividends("SBER")).thenReturn(List.of(dto));
        when(securityRepository.findById("SBER")).thenReturn(Optional.of(sber));
        when(dividendRepository.existsBySecurity_TickerAndRecordDate("SBER", LocalDate.of(2023, 5, 15)))
                .thenReturn(true);

        dividendSyncService.syncDividends("SBER");

        verify(dividendRepository, never()).save(any());
    }

    @Test
    @DisplayName("syncDividends — MOEX недоступен → метод завершается без исключений, save не вызывается")
    void syncDividends_doesNotThrow_whenMoexUnavailable() {
        when(moexIssClient.fetchDividends("SBER"))
                .thenThrow(new MoexUnavailableException("MOEX unavailable"));

        assertThatCode(() -> dividendSyncService.syncDividends("SBER"))
                .doesNotThrowAnyException();

        verify(dividendRepository, never()).save(any());
    }

    @Test
    @DisplayName("syncDividends — paymentDate в прошлом → status = PAID")
    void syncDividends_setsPaidStatus_whenPaymentDateInPast() {
        LocalDate pastDate = LocalDate.now().minusDays(1);

        MoexDividendDto dto = new MoexDividendDto();
        dto.setSecid("SBER");
        dto.setRegistryCloseDate(LocalDate.now().minusDays(10));
        dto.setDividendPaymentDate(pastDate);
        dto.setValue(new BigDecimal("18.5"));
        dto.setCurrencyId("RUB");

        when(moexIssClient.fetchDividends("SBER")).thenReturn(List.of(dto));
        when(securityRepository.findById("SBER")).thenReturn(Optional.of(sber));
        when(dividendRepository.existsBySecurity_TickerAndRecordDate(any(), any())).thenReturn(false);

        dividendSyncService.syncDividends("SBER");

        ArgumentCaptor<Dividend> captor = ArgumentCaptor.forClass(Dividend.class);
        verify(dividendRepository).save(captor.capture());
        assertThat(captor.getValue().getStatus()).isEqualTo(DividendStatus.PAID);
    }

    @Test
    @DisplayName("syncDividends — paymentDate в будущем → status = ANNOUNCED")
    void syncDividends_setsAnnouncedStatus_whenPaymentDateInFuture() {
        LocalDate futureDate = LocalDate.now().plusDays(30);

        MoexDividendDto dto = new MoexDividendDto();
        dto.setSecid("SBER");
        dto.setRegistryCloseDate(LocalDate.now().plusDays(10));
        dto.setDividendPaymentDate(futureDate);
        dto.setValue(new BigDecimal("30.0"));
        dto.setCurrencyId("RUB");

        when(moexIssClient.fetchDividends("SBER")).thenReturn(List.of(dto));
        when(securityRepository.findById("SBER")).thenReturn(Optional.of(sber));
        when(dividendRepository.existsBySecurity_TickerAndRecordDate(any(), any())).thenReturn(false);

        dividendSyncService.syncDividends("SBER");

        ArgumentCaptor<Dividend> captor = ArgumentCaptor.forClass(Dividend.class);
        verify(dividendRepository).save(captor.capture());
        assertThat(captor.getValue().getStatus()).isEqualTo(DividendStatus.ANNOUNCED);
    }
}
