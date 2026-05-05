package pyc.lopatuxin.investment.service.market;

import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pyc.lopatuxin.investment.client.moex.MoexIssClient;
import pyc.lopatuxin.investment.client.moex.MoexUnavailableException;
import pyc.lopatuxin.investment.client.moex.dto.MoexDividendDto;
import pyc.lopatuxin.investment.entity.Dividend;
import pyc.lopatuxin.investment.entity.Security;
import pyc.lopatuxin.investment.entity.enums.DividendStatus;
import pyc.lopatuxin.investment.repository.DividendRepository;
import pyc.lopatuxin.investment.repository.SecurityRepository;

import java.time.LocalDate;
import java.util.List;

@Slf4j
@Service
public class DividendSyncService {

    private final MoexIssClient moexIssClient;
    private final DividendRepository dividendRepository;
    private final SecurityRepository securityRepository;
    private final DividendLoaderService dividendLoaderService;

    public DividendSyncService(MoexIssClient moexIssClient,
                               DividendRepository dividendRepository,
                               SecurityRepository securityRepository,
                               @Lazy DividendLoaderService dividendLoaderService) {
        this.moexIssClient = moexIssClient;
        this.dividendRepository = dividendRepository;
        this.securityRepository = securityRepository;
        this.dividendLoaderService = dividendLoaderService;
    }

    @Transactional
    public void syncDividends(String ticker) {
        List<MoexDividendDto> moexDividends;
        try {
            moexDividends = moexIssClient.fetchDividends(ticker);
        } catch (MoexUnavailableException e) {
            log.warn("MOEX unavailable while syncing dividends for {}", ticker);
            return;
        }
        Security security = securityRepository.findById(ticker).orElse(null);
        if (security == null) {
            return;
        }

        for (MoexDividendDto dto : moexDividends) {
            if (dto.getRegistryCloseDate() == null) {
                continue;
            }
            if (dividendRepository.existsBySecurity_TickerAndRecordDate(ticker, dto.getRegistryCloseDate())) {
                continue;
            }
            if (dto.getValue() == null) {
                continue;
            }
            DividendStatus status = dto.getRegistryCloseDate().isBefore(LocalDate.now())
                    ? DividendStatus.PAID : DividendStatus.ANNOUNCED;

            Dividend dividend = new Dividend();
            dividend.setSecurity(security);
            dividend.setRecordDate(dto.getRegistryCloseDate());
            dividend.setPaymentDate(null);
            dividend.setAmountPerShare(dto.getValue());
            dividend.setCurrency(dto.getCurrencyId() != null ? dto.getCurrencyId() : "RUB");
            dividend.setStatus(status);
            dividendRepository.save(dividend);
        }
        log.debug("Synced {} dividends for {}", moexDividends.size(), ticker);
    }

    // Triggers async dividend sync via a separate @Async proxy bean to avoid calling @Async from within the same transaction
    public void syncDividendsAsync(String ticker) {
        dividendLoaderService.loadAsync(ticker);
    }
}
