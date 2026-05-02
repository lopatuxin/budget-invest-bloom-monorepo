package pyc.lopatuxin.investment.dto.request;

import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

// Marker DTO for endpoints that require no payload in the "data" block
@Builder
@Getter
@Setter
@NoArgsConstructor
public class EmptyRequestDto {
}
