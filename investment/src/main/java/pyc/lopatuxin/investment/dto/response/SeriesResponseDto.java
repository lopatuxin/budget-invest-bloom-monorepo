package pyc.lopatuxin.investment.dto.response;

import lombok.AllArgsConstructor;
import lombok.Data;

import java.util.List;

@Data
@AllArgsConstructor
public class SeriesResponseDto<T> {
    private List<T> series;
    private boolean historyPending;
    private List<String> pendingTickers;
}
