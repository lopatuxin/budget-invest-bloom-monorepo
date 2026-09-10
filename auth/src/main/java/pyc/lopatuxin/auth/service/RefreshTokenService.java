package pyc.lopatuxin.auth.service;

import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.JwtException;
import jakarta.persistence.EntityNotFoundException;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Lazy;
import org.springframework.dao.DataAccessException;
import org.springframework.lang.Nullable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.TransactionException;
import org.springframework.transaction.annotation.Transactional;
import pyc.lopatuxin.auth.config.JwtConfig;
import pyc.lopatuxin.auth.dto.request.RequestHeadersDto;
import pyc.lopatuxin.auth.dto.response.RefreshTokenResponse;
import pyc.lopatuxin.auth.entity.RefreshToken;
import pyc.lopatuxin.auth.entity.User;
import pyc.lopatuxin.auth.exception.AccountInactiveException;
import pyc.lopatuxin.auth.exception.AccountLockedException;
import pyc.lopatuxin.auth.exception.InvalidRefreshTokenException;
import pyc.lopatuxin.auth.exception.RefreshTokenExpiredException;
import pyc.lopatuxin.auth.exception.RefreshTokenReusedException;
import pyc.lopatuxin.auth.repository.RefreshTokenRepository;
import pyc.lopatuxin.auth.repository.UserRepository;
import pyc.lopatuxin.auth.util.RefreshTokenCookieHelper;
import pyc.lopatuxin.auth.util.RefreshTokenHasher;

import java.time.LocalDateTime;
import java.util.UUID;

@Service
@Slf4j
public class RefreshTokenService {

    private final RefreshTokenRepository refreshTokenRepository;
    private final UserRepository userRepository;
    private final JwtService jwtService;
    private final RefreshTokenCookieHelper cookieHelper;
    private final RefreshTokenHasher tokenHasher;
    private final JwtConfig jwtConfig;
    private final RefreshTokenService self;

    public RefreshTokenService(RefreshTokenRepository refreshTokenRepository,
                                UserRepository userRepository,
                                JwtService jwtService,
                                RefreshTokenCookieHelper cookieHelper,
                                RefreshTokenHasher tokenHasher,
                                JwtConfig jwtConfig,
                                @Lazy RefreshTokenService self) {
        this.refreshTokenRepository = refreshTokenRepository;
        this.userRepository = userRepository;
        this.jwtService = jwtService;
        this.cookieHelper = cookieHelper;
        this.tokenHasher = tokenHasher;
        this.jwtConfig = jwtConfig;
        this.self = self;
    }

    // Not @Transactional: the DB writes below happen across at most one open connection at a
    // time instead of one read-modify-write spanning the whole request — the same reasoning as
    // LoginService.login(). self.rotateToken is one short transaction that marks the old token
    // used and creates the new one together (see its own comment for why that must be atomic).
    // The reuse-rejection path's self.deleteAllUserTokens opens its own connection only after
    // rotateToken's has already closed, so this method never holds two connections from the
    // shared 5-connection pool at once.
    public RefreshTokenResponse refreshTokens(
            String refreshToken,
            RequestHeadersDto headers,
            HttpServletResponse httpServletResponse) {

        log.info("Начало обновления токенов");

        UUID userId = validateAndExtractUserId(refreshToken);

        User user = userRepository.findByIdWithRoles(userId)
                .orElseThrow(() -> {
                    log.warn("Пользователь с ID {} не найден", userId);
                    return new EntityNotFoundException("Пользователь не найден");
                });

        validateUser(user);

        String tokenHash = tokenHasher.hash(refreshToken);
        String newRefreshToken = jwtService.generateRefreshToken(user);

        int rotatedRows = self.rotateToken(user, tokenHash, newRefreshToken, headers);
        if (rotatedRows <= 0) {
            rejectAsReused(user, tokenHash, httpServletResponse);
        }

        String newAccessToken = jwtService.generateAccessToken(user);
        log.debug("Сгенерированы новые токены для пользователя {}", userId);

        cookieHelper.setRefreshTokenCookie(httpServletResponse, newRefreshToken);

        log.info("Токены успешно обновлены для пользователя {}", userId);

        int expiresIn = (int) (jwtConfig.getAccessTokenExpiration() / 1000);
        return RefreshTokenResponse.builder()
                .accessToken(newAccessToken)
                .tokenType("Bearer")
                .expiresIn(expiresIn)
                .build();
    }

    private UUID validateAndExtractUserId(String refreshToken) {
        if (refreshToken == null || refreshToken.isBlank()) {
            log.warn("Отсутствует refresh token в запросе");
            throw new InvalidRefreshTokenException("Отсутствует обязательное поле refreshToken");
        }

        try {
            UUID userId = jwtService.extractUserId(refreshToken);
            log.debug("Извлечен userId: {} из refresh token", userId);

            return userId;
        } catch (ExpiredJwtException e) {
            log.warn("Refresh token истек: {}", e.getMessage());
            throw new RefreshTokenExpiredException("Refresh token истек. Требуется повторная аутентификация");
        } catch (JwtException | IllegalArgumentException e) {
            log.warn("Недействительный refresh token: {}", e.getMessage());
            throw new InvalidRefreshTokenException("Refresh token имеет недействительную подпись или формат");
        }
    }

    private void validateUser(User user) {
        if (Boolean.FALSE.equals(user.getIsActive())) {
            log.warn("Попытка обновления токена для неактивного пользователя {}", user.getId());
            throw new AccountInactiveException("Аккаунт деактивирован. Обратитесь в службу поддержки");
        }

        LocalDateTime lockedUntil = user.getLockedUntil();
        if (lockedUntil != null && lockedUntil.isAfter(LocalDateTime.now())) {
            log.warn("Попытка обновления токена для заблокированного пользователя {} (блокировка до {})", user.getId(), lockedUntil);
            throw new AccountLockedException("Аккаунт заблокирован");
        }
    }

    @Nullable
    public RefreshToken findValidToken(String token, User user) {
        String tokenHash = tokenHasher.hash(token);
        return refreshTokenRepository
                .findActiveByUserAndTokenHash(user, tokenHash, LocalDateTime.now())
                .orElse(null);
    }

    // Called only after self.rotateToken above returned 0 rows updated, i.e. the old token was
    // not active. Distinguishes "never existed / expired" from a genuine replay of an
    // already-rotated token before deciding whether to wipe the account's sessions.
    private void rejectAsReused(User user, String tokenHash, HttpServletResponse httpServletResponse) {
        boolean alreadyUsed = refreshTokenRepository.existsByUserAndTokenHashAndExpiresAtAfterAndIsUsedTrue(
                user, tokenHash, LocalDateTime.now());
        if (!alreadyUsed) {
            log.warn("Refresh token не найден в БД или уже истек для пользователя {}", user.getId());
            throw new InvalidRefreshTokenException("Refresh token не найден или истек");
        }

        log.error("Обнаружено повторное использование refresh token для пользователя {}. Возможна компрометация", user.getId());

        // Best-effort: a failure while wiping sessions must not turn the 403 the client needs to
        // see (and act on) into an opaque 500 that hides the compromise, and must never leave the
        // caller thinking the stolen token is still accepted either way — the exception below is
        // thrown regardless of whether the deletion actually landed. That is also why the message
        // below stays neutral about the outcome of the wipe itself: it is still accurate when the
        // deletion silently failed here and was only recorded in the error log above.
        try {
            self.deleteAllUserTokens(user);
        } catch (DataAccessException | TransactionException e) {
            log.error("Не удалось удалить сессии пользователя {} после обнаружения компрометации токена", user.getId(), e);
        }
        cookieHelper.clearRefreshTokenCookie(httpServletResponse);

        throw new RefreshTokenReusedException(
                "Refresh token уже был использован. Возможна компрометация сессии. Требуется повторный вход");
    }

    // Own transaction, independent of the caller: refreshTokens() is not @Transactional (see its
    // own comment) and rotateToken's transaction has already committed and released its
    // connection by the time this runs, so there is no ambient transaction to nest inside and
    // REQUIRES_NEW buys nothing here. Reached through the self proxy because a plain in-class call
    // bypasses Spring's AOP proxy, so @Transactional here would otherwise never apply (see
    // LoginService.handleFailedLogin for the same pattern).
    @Transactional("authTransactionManager")
    public void deleteAllUserTokens(User user) {
        refreshTokenRepository.deleteAllByUser(user);
        log.info("Удалены все токены пользователя {}", user.getId());
    }

    // Marks the old token used and creates its replacement in one short transaction, so a failure
    // between the two (DB error, timeout, pool exhaustion) rolls both back instead of leaving the
    // old token stranded as used with no replacement — a retry with the same old token then rotates
    // normally instead of tripping reuse detection. Safe to keep short: everything inside is a fast
    // local DB write, no password check or network call. The affected-row count from the UPDATE is
    // still what tells a genuine rotation apart from a replay (see RefreshTokenRepository.markUsedIfActive).
    @Transactional("authTransactionManager")
    public int rotateToken(User user, String oldTokenHash, String newRefreshToken, RequestHeadersDto headers) {
        int updatedRows = refreshTokenRepository.markUsedIfActive(user, oldTokenHash, LocalDateTime.now());
        if (updatedRows > 0) {
            createRefreshToken(user, newRefreshToken, headers.getUserAgent(), headers.extractIpAddress());
            // Atomic UPDATE by id (see UserRepository) instead of a read-modify-write on this
            // method's User snapshot — the same reasoning as LoginService.completeSuccessfulLogin: a
            // read-modify-write here would merge the whole row back and could undo a concurrent
            // failed-login counter increment or lockout.
            userRepository.updateLastLoginAt(user.getId(), LocalDateTime.now());
        }
        return updatedRows;
    }

    public void createRefreshToken(User user, String token, String userAgent, String ipAddress) {
        String tokenHash = tokenHasher.hash(token);

        LocalDateTime expiresAt = LocalDateTime.now()
                .plusSeconds(jwtConfig.getRefreshTokenExpiration() / 1000);

        RefreshToken refreshToken = RefreshToken.builder()
                .user(user)
                .tokenHash(tokenHash)
                .expiresAt(expiresAt)
                .isUsed(false)
                .userAgent(userAgent)
                .ipAddress(ipAddress)
                .createdAt(LocalDateTime.now())
                .build();

        refreshTokenRepository.save(refreshToken);
    }
}
