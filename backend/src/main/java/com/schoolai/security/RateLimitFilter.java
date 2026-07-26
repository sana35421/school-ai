package com.schoolai.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.lang.NonNull;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@Component
public class RateLimitFilter extends OncePerRequestFilter {

    private static final int MAX_REQUESTS_PER_MINUTE = 10;
    private static final long WINDOW_MS = 60_000;

    private final Map<String, RateWindow> windows = new ConcurrentHashMap<>();

    @Override
    protected void doFilterInternal(@NonNull HttpServletRequest request,
                                     @NonNull HttpServletResponse response,
                                     @NonNull FilterChain chain) throws ServletException, IOException {
        if (!"/api/auth/login".equals(request.getRequestURI())) {
            chain.doFilter(request, response);
            return;
        }

        String ip = getClientIp(request);
        String key = "login:" + ip;

        long now = System.currentTimeMillis();
        RateWindow window = windows.compute(key, (k, v) -> {
            if (v == null || now - v.startTime > WINDOW_MS) {
                return new RateWindow(now, 1);
            }
            v.count++;
            return v;
        });

        if (window.count > MAX_REQUESTS_PER_MINUTE) {
            log.warn("登录频率超限: ip={}, count={}", ip, window.count);
            response.setStatus(429);
            response.setContentType("application/json;charset=utf-8");
            response.getWriter().write("{\"code\":429,\"message\":\"请求过于频繁，请稍后再试\"}");
            return;
        }

        chain.doFilter(request, response);
    }

    private String getClientIp(HttpServletRequest request) {
        String xff = request.getHeader("X-Forwarded-For");
        if (xff != null && !xff.isEmpty()) {
            return xff.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }

    private static class RateWindow {
        final long startTime;
        int count;

        RateWindow(long startTime, int count) {
            this.startTime = startTime;
            this.count = count;
        }
    }
}