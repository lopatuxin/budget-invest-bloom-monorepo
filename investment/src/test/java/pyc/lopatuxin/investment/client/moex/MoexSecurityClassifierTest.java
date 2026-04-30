package pyc.lopatuxin.investment.client.moex;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;
import pyc.lopatuxin.investment.entity.enums.SecurityType;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("MoexSecurityClassifier — юнит-тесты классификации группы инструментов MOEX")
class MoexSecurityClassifierTest {

    @ParameterizedTest(name = "group={0} → STOCK")
    @CsvSource({
            "stock_shares",
            "stock_pref_shares"
    })
    @DisplayName("fromGroup — акции (обычные и привилегированные) → SecurityType.STOCK")
    void fromGroup_returnsStock(String group) {
        Optional<SecurityType> result = MoexSecurityClassifier.fromGroup(group);

        assertThat(result).contains(SecurityType.STOCK);
    }

    @ParameterizedTest(name = "group={0} → ETF")
    @CsvSource({
            "stock_etf",
            "stock_ppif"
    })
    @DisplayName("fromGroup — ETF и PPIF → SecurityType.ETF")
    void fromGroup_returnsEtf(String group) {
        Optional<SecurityType> result = MoexSecurityClassifier.fromGroup(group);

        assertThat(result).contains(SecurityType.ETF);
    }

    @ParameterizedTest(name = "group={0} → OFZ")
    @CsvSource({
            "stock_bonds_ofz",
            "stock_bonds_state"
    })
    @DisplayName("fromGroup — ОФЗ и государственные облигации → SecurityType.OFZ")
    void fromGroup_returnsOfz(String group) {
        Optional<SecurityType> result = MoexSecurityClassifier.fromGroup(group);

        assertThat(result).contains(SecurityType.OFZ);
    }

    @Test
    @DisplayName("fromGroup — корпоративные облигации (stock_bonds) → SecurityType.BOND")
    void fromGroup_returnsBond() {
        Optional<SecurityType> result = MoexSecurityClassifier.fromGroup("stock_bonds");

        assertThat(result).contains(SecurityType.BOND);
    }

    @Test
    @DisplayName("fromGroup — null → Optional.empty()")
    void fromGroup_returnsEmpty_whenNull() {
        Optional<SecurityType> result = MoexSecurityClassifier.fromGroup(null);

        assertThat(result).isEmpty();
    }

    @ParameterizedTest(name = "group=\"{0}\" → Optional.empty()")
    @ValueSource(strings = {
            "unknown_group",
            "stock_futures",
            "currency",
            "",
            "STOCK_SHARES"
    })
    @DisplayName("fromGroup — неизвестная или некорректная строка → Optional.empty()")
    void fromGroup_returnsEmpty_whenUnknownGroup(String group) {
        Optional<SecurityType> result = MoexSecurityClassifier.fromGroup(group);

        assertThat(result).isEmpty();
    }
}
