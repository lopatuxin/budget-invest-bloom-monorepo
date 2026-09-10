package pyc.lopatuxin.investment.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import pyc.lopatuxin.investment.AbstractIntegrationTest;
import pyc.lopatuxin.investment.dto.request.CreateDividendDto;
import pyc.lopatuxin.investment.dto.response.SecurityDividendDto;
import pyc.lopatuxin.investment.entity.Position;
import pyc.lopatuxin.investment.entity.Security;
import pyc.lopatuxin.investment.entity.enums.HistoryStatus;
import pyc.lopatuxin.investment.entity.enums.SecurityType;
import pyc.lopatuxin.investment.exception.DividendAlreadyExistsException;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

// Real Postgres (Testcontainers), not a mocked repository: the bug this guards against only
// shows up with a real persistence context. Dividend's id is app-generated
// (@GeneratedValue(UUID)), so a plain save() only queues the INSERT — Hibernate defers it to the
// transaction's commit-time flush, which happens after DividendManualService.create()'s own
// try/catch has already exited. Mocking save() to throw (the old test) can't see that timing at
// all; only two genuinely concurrent transactions racing on the same unique key can.
@DisplayName("DividendManualService — гонка при создании дивидендов вручную")
class DividendManualServiceConcurrencyIT extends AbstractIntegrationTest {

    @Autowired
    private DividendManualService dividendManualService;

    private UUID userId;

    @BeforeEach
    void setUp() {
        dividendRepository.deleteAll();
        priceSnapshotRepository.deleteAll();
        priceHistoryRepository.deleteAll();
        transactionRepository.deleteAll();
        positionRepository.deleteAll();
        securityRepository.deleteAll();
        userId = UUID.randomUUID();

        Security sber = securityRepository.save(Security.builder()
                .ticker("SBER").name("Сбербанк").type(SecurityType.STOCK)
                .historyStatus(HistoryStatus.READY).build());
        positionRepository.save(Position.builder()
                .userId(userId).security(sber)
                .quantity(new BigDecimal("100")).averagePrice(new BigDecimal("280.00"))
                .totalCost(new BigDecimal("28000.00")).build());
    }

    @Test
    @DisplayName("два конкурентных запроса на одну дату отсечки → ровно один успех, второй — 409 DIVIDEND_EXISTS, а не сырая ошибка целостности")
    void concurrentCreate_sameRecordDate_secondGetsDividendExistsNotRawConstraintViolation() throws Exception {
        LocalDate recordDate = LocalDate.now().plusDays(10);
        CreateDividendDto dto = CreateDividendDto.builder()
                .ticker("SBER").recordDate(recordDate).amountPerShare(new BigDecimal("34.84")).build();

        int threads = 2;
        ExecutorService executor = Executors.newFixedThreadPool(threads);
        CountDownLatch ready = new CountDownLatch(threads);
        CountDownLatch start = new CountDownLatch(1);
        try {
            Callable<Object> attempt = () -> {
                ready.countDown();
                start.await();
                try {
                    return dividendManualService.create(userId, dto);
                } catch (Exception e) {
                    return e;
                }
            };
            List<Future<Object>> futures = new ArrayList<>();
            for (int i = 0; i < threads; i++) {
                futures.add(executor.submit(attempt));
            }
            ready.await();
            start.countDown();

            List<Object> results = new ArrayList<>();
            for (Future<Object> future : futures) {
                results.add(future.get(15, TimeUnit.SECONDS));
            }

            long successes = results.stream().filter(SecurityDividendDto.class::isInstance).count();
            long conflicts = results.stream().filter(DividendAlreadyExistsException.class::isInstance).count();

            assertThat(successes).isEqualTo(1);
            assertThat(conflicts).isEqualTo(1);
            assertThat(dividendRepository.findBySecurity_TickerAndRecordDate("SBER", recordDate)).isPresent();
        } finally {
            executor.shutdownNow();
        }
    }
}
