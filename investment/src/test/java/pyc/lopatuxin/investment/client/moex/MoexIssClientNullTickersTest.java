package pyc.lopatuxin.investment.client.moex;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThatNullPointerException;

@ExtendWith(MockitoExtension.class)
@DisplayName("MoexIssClient — null-аргументы")
class MoexIssClientNullTickersTest extends AbstractMoexClientTest {

    @Test
    @DisplayName("fetchSnapshots(null) → NullPointerException до обращения к API")
    void fetchSnapshots_throwsNPE_whenTickersIsNull() {
        assertThatNullPointerException()
                .isThrownBy(() -> client.fetchSnapshots(null))
                .withMessage("tickers");
    }
}
