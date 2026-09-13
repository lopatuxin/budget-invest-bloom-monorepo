package pyc.lopatuxin.investment.service;

import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pyc.lopatuxin.investment.dto.request.CreateDividendDto;
import pyc.lopatuxin.investment.dto.response.SecurityDividendDto;
import pyc.lopatuxin.investment.entity.Dividend;
import pyc.lopatuxin.investment.entity.Security;
import pyc.lopatuxin.investment.entity.enums.DividendSource;
import pyc.lopatuxin.investment.entity.enums.DividendStatus;
import pyc.lopatuxin.investment.entity.enums.PayoutKind;
import pyc.lopatuxin.investment.entity.enums.SecurityType;
import pyc.lopatuxin.investment.exception.DividendAlreadyExistsException;
import pyc.lopatuxin.investment.repository.DividendRepository;
import pyc.lopatuxin.investment.repository.PositionRepository;
import pyc.lopatuxin.investment.repository.SecurityRepository;

import java.time.LocalDate;
import java.util.EnumSet;
import java.util.Set;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class DividendManualService {

    private static final String DUPLICATE_RECORD_DATE_INDEX = "uq_dividends_ticker_record_date";
    private static final Set<SecurityType> COUPON_TYPES = EnumSet.of(SecurityType.BOND, SecurityType.OFZ);

    private final DividendRepository dividendRepository;
    private final SecurityRepository securityRepository;
    private final PositionRepository positionRepository;

    @Transactional("investmentTransactionManager")
    public SecurityDividendDto create(UUID userId, CreateDividendDto dto) {
        String ticker = dto.getTicker().toUpperCase();
        if (!positionRepository.existsByUserIdAndSecurity_Ticker(userId, ticker)) {
            throw new EntityNotFoundException("Бумага не найдена в портфеле: " + ticker);
        }
        if (dividendRepository.existsBySecurity_TickerAndRecordDate(ticker, dto.getRecordDate())) {
            throw new DividendAlreadyExistsException(
                    "Дивиденд с датой отсечки " + dto.getRecordDate() + " для " + ticker + " уже существует");
        }
        Security security = securityRepository.findById(ticker)
                .orElseThrow(() -> new EntityNotFoundException("Бумага не найдена: " + ticker));

        String currency = dto.getCurrency() != null && !dto.getCurrency().isBlank()
                ? dto.getCurrency().toUpperCase() : "RUB";
        DividendStatus status = dto.getRecordDate().isBefore(LocalDate.now())
                ? DividendStatus.PAID : DividendStatus.ANNOUNCED;
        PayoutKind kind = COUPON_TYPES.contains(security.getType()) ? PayoutKind.COUPON : PayoutKind.DIVIDEND;

        Dividend dividend = Dividend.builder()
                .security(security)
                .recordDate(dto.getRecordDate())
                .paymentDate(dto.getPaymentDate())
                .amountPerShare(dto.getAmountPerShare())
                .currency(currency)
                .status(status)
                .source(DividendSource.MANUAL)
                .kind(kind)
                .build();
        Dividend saved;
        try {
            // saveAndFlush, not save: the id is app-generated (@GeneratedValue(UUID)), so a plain
            // save() only queues the INSERT and lets it go out on commit-time flush — after this
            // try block has already returned. Flushing here forces the INSERT (and therefore the
            // unique index violation on a concurrent duplicate) to happen inside the catch's reach.
            saved = dividendRepository.saveAndFlush(dividend);
        } catch (DataIntegrityViolationException e) {
            // The existsBySecurity_TickerAndRecordDate check above is not atomic with this save —
            // a concurrent request for the same (ticker, recordDate) can slip through it and hit
            // the unique index here instead. Reported as the same 409 DIVIDEND_EXISTS rather than
            // letting the raw constraint violation surface (plan point 10). Matched by index name:
            // any other integrity failure must keep its own error instead of claiming a duplicate.
            if (!isDuplicateRecordDate(e)) {
                throw e;
            }
            throw new DividendAlreadyExistsException(
                    "Дивиденд с датой отсечки " + dto.getRecordDate() + " для " + ticker + " уже существует");
        }
        log.info("Дивиденд добавлен вручную: тикер={}, отсечка={}", ticker, dto.getRecordDate());
        return toDto(saved);
    }

    @Transactional("investmentTransactionManager")
    public void delete(UUID userId, UUID dividendId) {
        Dividend dividend = dividendRepository.findById(dividendId)
                .orElseThrow(() -> new EntityNotFoundException("Дивиденд не найден: " + dividendId));
        String ticker = dividend.getSecurity().getTicker();
        if (!positionRepository.existsByUserIdAndSecurity_Ticker(userId, ticker)) {
            throw new EntityNotFoundException("Дивиденд не найден: " + dividendId);
        }
        if (dividend.getSource() != DividendSource.MANUAL) {
            throw new IllegalArgumentException("Удалить можно только введённый вручную дивиденд");
        }
        dividendRepository.delete(dividend);
        log.info("Дивиденд удалён: тикер={}, отсечка={}", ticker, dividend.getRecordDate());
    }

    // Named after the unique index in 007-add-dividend-unique.yml. Hibernate wraps the driver
    // exception, so the index name is looked for across the whole cause chain's messages.
    private boolean isDuplicateRecordDate(DataIntegrityViolationException e) {
        for (Throwable cause = e; cause != null; cause = cause.getCause()) {
            String message = cause.getMessage();
            if (message != null && message.contains(DUPLICATE_RECORD_DATE_INDEX)) {
                return true;
            }
        }
        return false;
    }

    private SecurityDividendDto toDto(Dividend d) {
        return SecurityDividendDto.builder()
                .id(d.getId())
                .recordDate(d.getRecordDate())
                .paymentDate(d.getPaymentDate())
                .amountPerShare(d.getAmountPerShare())
                .currency(d.getCurrency())
                .status(d.getStatus())
                .source(d.getSource())
                .kind(d.getKind())
                .build();
    }
}
