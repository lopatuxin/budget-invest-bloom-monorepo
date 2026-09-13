package pyc.lopatuxin.investment.service;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import pyc.lopatuxin.investment.entity.Security;
import pyc.lopatuxin.investment.entity.enums.HistoryStatus;
import pyc.lopatuxin.investment.entity.enums.SecurityType;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("BondPricingTest")
class BondPricingTest {

    private final BondPricing bondPricing = new BondPricing();

    @Test
    @DisplayName("quotedToRubles — ОФЗ 99.95 при номинале 1000 → 999.50")
    void quotedToRubles_ofzWithNominal_convertsPercentOfParToRubles() {
        Security ofz = security("SU26219RMFS4", SecurityType.OFZ, new BigDecimal("1000.00"));

        BigDecimal result = bondPricing.quotedToRubles(ofz, new BigDecimal("99.95"));

        assertThat(result).isEqualByComparingTo("999.50");
    }

    @Test
    @DisplayName("quotedToRubles — акция: цена не пересчитывается")
    void quotedToRubles_stock_passesThroughUnchanged() {
        Security stock = security("SBER", SecurityType.STOCK, null);

        BigDecimal result = bondPricing.quotedToRubles(stock, new BigDecimal("278.83"));

        assertThat(result).isEqualByComparingTo("278.83");
    }

    @Test
    @DisplayName("quotedToRubles — ETF: цена не пересчитывается")
    void quotedToRubles_etf_passesThroughUnchanged() {
        Security etf = security("FXRL", SecurityType.ETF, null);

        BigDecimal result = bondPricing.quotedToRubles(etf, new BigDecimal("50.00"));

        assertThat(result).isEqualByComparingTo("50.00");
    }

    @Test
    @DisplayName("quotedToRubles — облигация без известного номинала → принимается 1000 ₽")
    void quotedToRubles_bondWithoutNominal_defaultsTo1000() {
        Security bond = security("RU000A", SecurityType.BOND, null);

        BigDecimal result = bondPricing.quotedToRubles(bond, new BigDecimal("95.00"));

        assertThat(result).isEqualByComparingTo("950.00");
    }

    @Test
    @DisplayName("quotedToRubles — quoted = null → null")
    void quotedToRubles_nullQuoted_returnsNull() {
        Security ofz = security("SU26219RMFS4", SecurityType.OFZ, new BigDecimal("1000.00"));

        assertThat(bondPricing.quotedToRubles(ofz, null)).isNull();
    }

    @Test
    @DisplayName("quotedToRubles — облигация без номинала: предупреждение в лог пишется один раз на тикер, а не на каждый вызов")
    void quotedToRubles_bondWithoutNominal_warnsOncePerTicker() {
        Logger logger = (Logger) LoggerFactory.getLogger(BondPricing.class);
        ListAppender<ILoggingEvent> appender = new ListAppender<>();
        appender.start();
        logger.addAppender(appender);
        try {
            Security bond = security("RU000A", SecurityType.BOND, null);

            for (int i = 0; i < 5; i++) {
                bondPricing.quotedToRubles(bond, new BigDecimal("95.00"));
            }

            long warnCount = appender.list.stream()
                    .filter(event -> event.getLevel() == Level.WARN)
                    .filter(event -> event.getFormattedMessage().contains("RU000A"))
                    .count();
            assertThat(warnCount).isEqualTo(1);
        } finally {
            logger.detachAppender(appender);
        }
    }

    @Test
    @DisplayName("quotedToRubles — облигации без номинала с разными тикерами предупреждают каждая о себе")
    void quotedToRubles_differentBondsWithoutNominal_eachWarnsOnce() {
        Logger logger = (Logger) LoggerFactory.getLogger(BondPricing.class);
        ListAppender<ILoggingEvent> appender = new ListAppender<>();
        appender.start();
        logger.addAppender(appender);
        try {
            bondPricing.quotedToRubles(security("RU000B", SecurityType.BOND, null), new BigDecimal("95.00"));
            bondPricing.quotedToRubles(security("RU000C", SecurityType.BOND, null), new BigDecimal("95.00"));

            long warnCount = appender.list.stream().filter(event -> event.getLevel() == Level.WARN).count();
            assertThat(warnCount).isEqualTo(2);
        } finally {
            logger.detachAppender(appender);
        }
    }

    @Test
    @DisplayName("isQuotedAsPercentOfPar — BOND/OFZ true, остальные виды false")
    void isQuotedAsPercentOfPar_bondAndOfzTrue_othersFalse() {
        assertThat(bondPricing.isQuotedAsPercentOfPar(SecurityType.BOND)).isTrue();
        assertThat(bondPricing.isQuotedAsPercentOfPar(SecurityType.OFZ)).isTrue();
        assertThat(bondPricing.isQuotedAsPercentOfPar(SecurityType.STOCK)).isFalse();
        assertThat(bondPricing.isQuotedAsPercentOfPar(SecurityType.ETF)).isFalse();
    }

    @Test
    @DisplayName("rubleValue — облигация с НКД: стоимость = количество × (рублёвая цена + НКД)")
    void rubleValue_bondWithAccruedInterest_addsAccruedInterestPerUnit() {
        Security ofz = security("SU26219RMFS4", SecurityType.OFZ, new BigDecimal("1000.00"));

        BigDecimal result = bondPricing.rubleValue(ofz, new BigDecimal("99.95"),
                new BigDecimal("12.34"), new BigDecimal("71"));

        // (999.50 + 12.34) * 71 = 71840.64
        assertThat(result).isEqualByComparingTo("71840.64");
    }

    @Test
    @DisplayName("rubleValue — облигация без НКД (null) → стоимость только по цене")
    void rubleValue_bondWithoutAccruedInterest_usesPriceOnly() {
        Security ofz = security("SU26219RMFS4", SecurityType.OFZ, new BigDecimal("1000.00"));

        BigDecimal result = bondPricing.rubleValue(ofz, new BigDecimal("99.95"), null, new BigDecimal("10"));

        assertThat(result).isEqualByComparingTo("9995.00");
    }

    private Security security(String ticker, SecurityType type, BigDecimal nominal) {
        return Security.builder()
                .ticker(ticker)
                .name(ticker)
                .type(type)
                .nominal(nominal)
                .historyStatus(HistoryStatus.READY)
                .build();
    }
}
