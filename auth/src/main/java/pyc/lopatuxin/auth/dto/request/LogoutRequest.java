package pyc.lopatuxin.auth.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.SuperBuilder;

@Getter
@Setter
@SuperBuilder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Данные для выхода из системы")
public class LogoutRequest {

    @Builder.Default
    @Schema(description = "Завершить все активные сессии", example = "false", defaultValue = "false")
    private Boolean logoutFromAll = false;
}