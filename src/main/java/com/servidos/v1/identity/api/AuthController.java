package com.servidos.v1.identity.api;

import com.servidos.v1.identity.api.dto.LoginRequest;
import com.servidos.v1.identity.api.dto.MeResponse;
import com.servidos.v1.identity.api.dto.TokenResponse;
import com.servidos.v1.identity.application.auth.LoginCommand;
import com.servidos.v1.identity.application.auth.LoginSession;
import com.servidos.v1.identity.application.auth.LoginUseCase;
import com.servidos.v1.identity.application.auth.RefreshTokenService;
import com.servidos.v1.identity.application.auth.SesionRenovada;
import com.servidos.v1.identity.infrastructure.UsuarioJpaRepository;
import com.servidos.v1.identity.infrastructure.security.JwtService;
import com.servidos.v1.shared.exception.BusinessException;
import com.servidos.v1.shared.exception.UnauthorizedException;
import com.servidos.v1.shared.security.CurrentUser;
import com.servidos.v1.shared.security.TenantContext;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CookieValue;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Duration;

@RestController
@RequestMapping("/api/v1/auth")
@RequiredArgsConstructor
public class AuthController {

    private static final String REFRESH_COOKIE = "refresh_token";
    private static final String NON_TRIVIAL_HEADER = "X-Requested-With";
    private static final String NON_TRIVIAL_VALUE = "XMLHttpRequest";

    @Value("${app.security.secure-cookie}")
    private boolean secureCookie;

    private final LoginUseCase loginUseCase;
    private final RefreshTokenService refreshTokenService;
    private final JwtService jwtService;
    private final UsuarioJpaRepository usuarioRepository;

    @PostMapping("/login")
    public ResponseEntity<TokenResponse> login(
            @Valid @RequestBody LoginRequest request, HttpServletRequest http) {
        final LoginSession session;
        try {
            session = loginUseCase.ejecutar(new LoginCommand(
                    request.email(), request.password(), http.getRemoteAddr()));
        } catch (BusinessException e) {
            throw new UnauthorizedException("Credenciales inválidas");
        }
        return ResponseEntity.ok()
                .header(HttpHeaders.SET_COOKIE, refreshCookie(session.refreshToken()).toString())
                .body(new TokenResponse(session.accessToken()));
    }

    @PostMapping("/refresh")
    public ResponseEntity<TokenResponse> refresh(
            @CookieValue(name = REFRESH_COOKIE, required = false) String refreshToken,
            @RequestHeader(name = NON_TRIVIAL_HEADER, required = false) String requestedWith) {
        exigirCabeceraNoTrivial(requestedWith);
        if (refreshToken == null || refreshToken.isBlank()) {
            throw new UnauthorizedException("Sesión inválida");
        }
        final SesionRenovada renovada;
        try {
            renovada = refreshTokenService.rotate(refreshToken);
        } catch (BusinessException e) {
            throw new UnauthorizedException("Sesión inválida");
        }
        String access = jwtService.generate(renovada.usuarioId(), renovada.restauranteId(), renovada.rol());
        return ResponseEntity.ok()
                .header(HttpHeaders.SET_COOKIE, refreshCookie(renovada.refreshToken()).toString())
                .body(new TokenResponse(access));
    }

    @PostMapping("/logout")
    public ResponseEntity<Void> logout(
            @CookieValue(name = REFRESH_COOKIE, required = false) String refreshToken,
            @RequestHeader(name = NON_TRIVIAL_HEADER, required = false) String requestedWith) {
        exigirCabeceraNoTrivial(requestedWith);
        if (refreshToken != null && !refreshToken.isBlank()) {
            refreshTokenService.logout(refreshToken);
        }
        return ResponseEntity.noContent()
                .header(HttpHeaders.SET_COOKIE, limpiarCookie().toString())
                .build();
    }

    @GetMapping("/me")
    public ResponseEntity<MeResponse> me() {
        Long usuarioId = CurrentUser.getCurrentUser();
        if (usuarioId == null) {
            throw new UnauthorizedException("Sesión inválida");
        }
        var usuario = usuarioRepository.findById(usuarioId)
                .orElseThrow(() -> new UnauthorizedException("Sesión inválida"));
        return ResponseEntity.ok(new MeResponse(
                usuario.getUsuarioId(),
                usuario.getEmail(),
                usuario.getNombre(),
                TenantContext.getRestauranteId(),
                CurrentUser.getRole()));
    }

    private void exigirCabeceraNoTrivial(String requestedWith) {
        if (!NON_TRIVIAL_VALUE.equals(requestedWith)) {
            throw new UnauthorizedException("Sesión inválida");
        }
    }

    private ResponseCookie refreshCookie(String valor) {
        return ResponseCookie.from(REFRESH_COOKIE, valor)
                .httpOnly(true)
                .secure(secureCookie)
                .sameSite("Lax")
                .path("/api/v1/auth")
                .maxAge(Duration.ofDays(7))
                .build();
    }

    private ResponseCookie limpiarCookie() {
        return ResponseCookie.from(REFRESH_COOKIE, "")
                .httpOnly(true)
                .secure(secureCookie)
                .sameSite("Lax")
                .path("/api/v1/auth")
                .maxAge(0)
                .build();
    }
}
