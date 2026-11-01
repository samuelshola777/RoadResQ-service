package com.roadresq.roadresq.utils.appsecurity;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.jsonwebtoken.Claims;
import jakarta.annotation.PostConstruct;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Duration;
import java.util.Collections;
import java.util.List;

@Component
@RequiredArgsConstructor
@Slf4j
public class ApiSecurityFilter extends OncePerRequestFilter {

    private final JwtService jwtService;
    private final ObjectMapper objectMapper;
    private final RouteValidator routeValidator;

    @Value("${app.api-key}")
    private String apiKey;

    @Value("${app.auth-service-url:http://localhost:8071}")
    private String authServiceUrl;

    private RestClient authClient;

    @PostConstruct
    public void initializeAuthClient() {
        log.info("Initializing Auth RestClient with base URL: {}", authServiceUrl);
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(Duration.ofSeconds(2));
        factory.setReadTimeout(Duration.ofSeconds(3));
        authClient = RestClient.builder()
                .baseUrl(authServiceUrl)
                .requestFactory(factory)
                .build();
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain chain) throws ServletException, IOException {

        log.info("ApiSecurityFilter processing request: {} {}", request.getMethod(), request.getRequestURI());

        if (routeValidator.isPublic.test(request)) {
            log.info("Bypassing security for public route: {}", request.getRequestURI());
            chain.doFilter(request, response);
            return;
        }

        Claims claims;
        List<String> roles;

        try {
            // 1. Validate API Key
            String providedApiKey = singleHeader(request, "X-Api-Key");
            if (providedApiKey == null || apiKey == null || apiKey.isBlank()
                    || !MessageDigest.isEqual(apiKey.getBytes(StandardCharsets.UTF_8), providedApiKey.getBytes(StandardCharsets.UTF_8))) {
                log.info("Security check failed: Invalid or missing API key for URI: {}", request.getRequestURI());
                reject(response, 401, "Invalid request source");
                return;
            }

            // 2. Extract and Validate Bearer Token
            String authorization = singleHeader(request, "Authorization");
            if (authorization == null || !authorization.startsWith("Bearer ")) {
                log.info("Security check failed: Missing Bearer token for URI: {}", request.getRequestURI());
                reject(response, 401, "A Bearer access token is required");
                return;
            }
            claims = jwtService.parse(authorization.substring(7).trim());

            // 3. Validate Platform Access
            String platform = singleHeader(request, "X-Service-Type");
            if (!validPlatform(platform) || !platform.equals(claims.get("servicePlatformType", String.class))) {
                log.info("Security check failed: Token platform does not match requested platform '{}' for URI: {}", platform, request.getRequestURI());
                reject(response, 403, "Token does not permit access to the requested platform");
                return;
            }
            if (!"ROAD_RESQ".equals(platform)) {
                log.info("Security check failed: Backend only serves ROAD_RESQ, requested: {} for URI: {}", platform, request.getRequestURI());
                reject(response, 403, "This backend serves ROAD_RESQ only");
                return;
            }

            // 4. Validate Identity Claims
            String role = claims.get("userType", String.class);
            String profileClaim = switch (role == null ? "" : role) {
                case "VENDOR" -> "vendorId";
                case "CUSTOMER" -> "customerId";
                case "ADMIN" -> "adminId";
                default -> "";
            };

            if (profileClaim.isEmpty() || blank(claims.get("userId", String.class)) || blank(claims.get(profileClaim, String.class))) {
                log.info("Security check failed: Invalid or missing identity claims for URI: {}", request.getRequestURI());
                reject(response, 401, "Invalid account identity");
                return;
            }

            // 5. Validate Roles
            Object authorities = claims.get("auth");
            if (!(authorities instanceof List<?> values) || values.stream().anyMatch(value -> !(value instanceof String))) {
                log.info("Security check failed: Invalid roles format for URI: {}", request.getRequestURI());
                reject(response, 401, "Invalid account roles");
                return;
            }
            roles = values.stream().map(Object::toString).toList();

            if (!roles.contains("ROLE_" + role)) {
                log.info("Security check failed: Required role ROLE_{} missing for URI: {}", role, request.getRequestURI());
                reject(response, 403, "Required account role is missing");
                return;
            }

            // 6. Check Live Database Access
            if (!checkCurrentAccess(request, response, claims, role, profileClaim)) {
                return;
            }

        } catch (RuntimeException exception) {
            log.info("Security check failed: Invalid or expired token for URI: {}. Reason: {}", request.getRequestURI(), exception.getMessage());
            reject(response, 401, "Invalid or expired access token");
            return;
        }

        // 7. Set Security Context
        var authentication = new UsernamePasswordAuthenticationToken(
                claims.getSubject(),
                null,
                roles.stream().map(SimpleGrantedAuthority::new).toList()
        );

        SecurityContextHolder.getContext().setAuthentication(authentication);
        request.setAttribute("validatedClaims", claims);
        request.setAttribute("userId", claims.get("userId"));
        request.setAttribute("userType", claims.get("userType"));
        request.setAttribute("userRole", claims.get("userRole"));
        request.setAttribute("email", claims.getSubject());

        log.info("Token validated successfully for user: {}, role: {}", claims.getSubject(), claims.get("userType"));

        try {
            log.info("Access granted to URI: {}", request.getRequestURI());
            chain.doFilter(request, response);
        } finally {
            SecurityContextHolder.clearContext();
        }
    }

    private boolean checkCurrentAccess(HttpServletRequest request, HttpServletResponse response,
                                       Claims claims, String role, String profileClaim) throws IOException {
        String account = switch (role) {
            case "VENDOR" -> "vendor";
            case "CUSTOMER" -> "customer";
            case "ADMIN" -> "admin";
            default -> "";
        };

        try {
            String body = authClient.get().uri("/api/v1/" + account + "/private/platform-access")
                    .header(HttpHeaders.AUTHORIZATION, request.getHeader(HttpHeaders.AUTHORIZATION))
                    .header("X-Api-Key", apiKey)
                    .header("X-Service-Type", "ROAD_RESQ")
                    .retrieve().body(String.class);

            JsonNode result = objectMapper.readTree(body == null ? "{}" : body);
            JsonNode profile = result.path("data");

            if (!result.path("success").asBoolean() || profile.isMissingNode() || profile.isNull()) {
                log.info("Access check failed: Auth service returned an invalid response for URI: {}", request.getRequestURI());
                reject(response, 503, "Account verification is temporarily unavailable");
                return false;
            }

            boolean active = profile.path("active").asBoolean();
            boolean verified = "ADMIN".equals(role)
                    ? profile.path("userActive").asBoolean() && profile.path("userVerified").asBoolean()
                    : profile.path("verified").asBoolean();

            if (!active || !verified
                    || !claims.get("userId", String.class).equals(profile.path("userId").asText())
                    || !claims.get(profileClaim, String.class).equals(profile.path(profileClaim).asText())) {
                log.info("Access denied: Live database identity mismatch or inactive account for user ID: {}", claims.get("userId", String.class));
                reject(response, 403, "Account access is inactive or unavailable");
                return false;
            }
            return true;

        } catch (RestClientResponseException exception) {
            int status = exception.getStatusCode().value();
            log.info("Access check failed: Auth service responded with status {} for URI: {}", status, request.getRequestURI());
            reject(response, status == 401 ? 401 : (status == 403 || status == 404 ? 403 : 503),
                    status == 401 ? "Please sign in again" : (status == 403 || status == 404
                            ? "Platform access is inactive or unavailable" : "Account verification is temporarily unavailable"));
            return false;
        } catch (RestClientException | IOException exception) {
            log.info("Access check failed: Auth service unreachable for URI: {}. Reason: {}", request.getRequestURI(), exception.getMessage());
            reject(response, 503, "Account verification is temporarily unavailable");
            return false;
        }
    }

    private boolean validPlatform(String value) {
        return value != null && List.of("ROAD_RESQ", "HOME_SERVICE", "PICK_DROP_OFF", "HOME_CHOP_CART").contains(value);
    }

    private String singleHeader(HttpServletRequest request, String name) {
        List<String> values = Collections.list(request.getHeaders(name));
        return values.size() == 1 && !values.get(0).isBlank() ? values.get(0) : null;
    }

    private boolean blank(String value) {
        return value == null || value.isBlank();
    }

    private void reject(HttpServletResponse response, int status, String message) throws IOException {
        log.info("Protected request rejected. Status: {}, Message: {}", status, message);
        response.setStatus(status);
        response.setContentType("application/json");
        objectMapper.writeValue(response.getWriter(), new ApiResponse<>(false, message, null));
    }
}