package com.roadresq.roadresq.utils.appsecurity;

import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.List;
import java.util.function.Predicate;
import java.util.regex.Pattern;

@Component
@Slf4j
public class DefaultRouteValidator {

    private static Pattern route(String routePrefix) {
        return Pattern.compile("^" + Pattern.quote(routePrefix) + "(?:/.*)?$");
    }

    private static List<Pattern> routes(String... routePrefixes) {
        return Arrays.stream(routePrefixes)
                .distinct()
                .map(DefaultRouteValidator::route)
                .toList();
    }

    public static final List<Pattern> ADMIN_ENDPOINTS = routes(
            "/api/v1/product/private",
            "/api/v1/admin/private",
            "/api/v1/customer/private/admin",
            "/api/v1/vendor/private/admin",
            "/api/v1/vendor-operation/private/admin",
            "/api/v1/equipment-capacity/private/admin",
            "/api/v1/files/private",
            "/api/v1/teams/private",
            "/api/v1/players/private",
            "/api/v1/player-stats/private",
            "/api/v1/gallery/private",
            "/api/v1/posts/private",
            "/api/v1/competitions/private",
            "/api/v1/competitions/private/admin",
            "/api/v1/activity-logs",
            "/api/v1/activity-logs/private",
            "/api/v1/activity-logs/private/admin",
            "/api/v1/dashboard/private",
            "/api/v1/dashboard/private/admin",
            "/api/v1/dashboard"
    );

    public DefaultRouteValidator() {
        log.info("DefaultRouteValidator initialized with {} admin endpoint patterns", ADMIN_ENDPOINTS.size());
    }

    public Predicate<HttpServletRequest> isCustomerSecured = req -> {
        String uri = req.getRequestURI();

        boolean isCustomerPath = uri.equals("/api/v1/customer/private")
                || uri.startsWith("/api/v1/customer/private/")
                || uri.equals("/api/v1/vendor-location/private/nearby-vendors")
                || uri.equals("/api/v1/rescue-requests/private")
                || uri.startsWith("/api/v1/rescue-requests/private/");

        boolean isNotAdminPath = !uri.equals("/api/v1/customer/private/admin")
                && !uri.startsWith("/api/v1/customer/private/admin/");

        boolean isSecured = isCustomerPath && isNotAdminPath;
        if (isSecured) {
            log.info("Customer route match found for URI: {}", uri);
        }
        return isSecured;
    };

    public Predicate<HttpServletRequest> isVendorSecured = req -> {
        String uri = req.getRequestURI();

        boolean isVendorPath = uri.equals("/api/v1/vendor/private")
                || uri.startsWith("/api/v1/vendor/private/")
                || uri.equals("/api/v1/vendor-location/private")
                || uri.startsWith("/api/v1/rescue-dispatch/private/")
                || uri.equals("/api/v1/vendor-operation/private")
                || uri.startsWith("/api/v1/vendor-operation/private/")
                || uri.equals("/api/v1/vendor-operation-equipment/private")
                || uri.startsWith("/api/v1/vendor-operation-equipment/private/")
                || uri.equals("/api/v1/equipment-capacity/private/equipment-capacities")
                || uri.startsWith("/api/v1/equipment-capacity/private/equipment-capacities/");

        boolean isNotAdminPath = !uri.equals("/api/v1/vendor/private/admin")
                && !uri.startsWith("/api/v1/vendor/private/admin/")
                && !uri.equals("/api/v1/vendor-operation/private/admin")
                && !uri.startsWith("/api/v1/vendor-operation/private/admin/");

        boolean isSecured = isVendorPath && isNotAdminPath;
        if (isSecured) {
            log.info("Vendor route match found for URI: {}", uri);
        }
        return isSecured;
    };

    public Predicate<HttpServletRequest> isAdminSecured = req -> {
        boolean matched = matches(req, ADMIN_ENDPOINTS);
        if (matched) {
            log.info("Admin route match found for URI: {}", req.getRequestURI());
        }
        return matched;
    };

    private boolean matches(HttpServletRequest request, List<Pattern> patterns) {
        String uri = request.getRequestURI();
        return patterns.stream().anyMatch(pattern -> pattern.matcher(uri).matches());
    }
}