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
import com.servidos.v1.shared.exception.ErrorResponse;
import com.servidos.v1.shared.exception.UnauthorizedException;
import com.servidos.v1.shared.security.CurrentUser;
import com.servidos.v1.shared.security.TenantContext;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
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
@Tag(name = "Auth", description = "Login, refresh, logout y sesión actual")
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
    @Operation(summary = "Iniciar sesión",
            description = "Valida email/password con BCrypt. Retorna access JWT en body y refresh opaco rotativo en cookie HttpOnly. 401 genérico sin enumerar.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Sesión creada",
                    content = @Content(schema = @Schema(implementation = TokenResponse.class))),
            @ApiResponse(responseCode = "401", description = "Credenciales inválidas",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "422", description = "Validación Jakarta (@Valid)",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class)))})
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
    @Operation(summary = "Rotar refresh token",
            description = "Lee la cookie refresh_token y exige header X-Requested-With: XMLHttpRequest. Rota la familia y revoca en caso de reuso.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Sesión renovada",
                    content = @Content(schema = @Schema(implementation = TokenResponse.class))),
            @ApiResponse(responseCode = "401", description = "Sesión inválida",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class)))})
    public ResponseEntity<TokenResponse> refresh(
            @Parameter(description = "Refresh opaco en cookie HttpOnly", example = "abc123")
            @CookieValue(name = REFRESH_COOKIE, required = false) String refreshToken,
            @Parameter(description = "Debe ser XMLHttpRequest", example = "XMLHttpRequest")
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
    @Operation(summary = "Cerrar sesión",
            description = "Revoca el refresh token si existe y limpia la cookie. Siempre retorna 204.")
    @ApiResponse(responseCode = "204", description = "Sesión cerrada")
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
    @Operation(summary = "Sesión actual",
            description = "Retorna usuario, restaurante y rol derivados del JWT vía TenantContext/CurrentUser.")
    @SecurityRequirement(name = "bearerAuth")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Sesión válida",
                    content = @Content(schema = @Schema(implementation = MeResponse.class))),
            @ApiResponse(responseCode = "401", description = "Sesión inválida",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class)))})
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
