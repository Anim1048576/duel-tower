package com.example.dueltower.common.logging;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.UUID;

@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 10)
public class RequestLogFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(RequestLogFilter.class);

    private static final String HEADER_REQ_ID = "X-Request-Id";
    private static final String MDC_KEY_RID = "rid";

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {

        String rid = request.getHeader(HEADER_REQ_ID);
        if (rid == null || rid.isBlank()) rid = UUID.randomUUID().toString();

        long startNs = System.nanoTime();

        MDC.put(MDC_KEY_RID, rid);
        response.setHeader(HEADER_REQ_ID, rid);

        String method = request.getMethod();
        String uri = request.getRequestURI();
        String qs = request.getQueryString();
        String path = (qs == null || qs.isBlank()) ? uri : (uri + "?" + qs);

        try {
            filterChain.doFilter(request, response);
        } finally {
            long ms = (System.nanoTime() - startNs) / 1_000_000L;
            int status = response.getStatus();
            log.info("{} {} -> {} ({}ms)", method, path, status, ms);
            MDC.remove(MDC_KEY_RID);
        }
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String path = request.getRequestURI();
        if (path == null) return false;

        // favicon / robots
        if (path.equals("/favicon.ico") || path.equals("/robots.txt")) return true;

        // 정적 리소스(스프링 기본/관례 경로들)
        return path.startsWith("/css/")
                || path.startsWith("/js/")
                || path.startsWith("/img/")
                || path.startsWith("/images/")
                || path.startsWith("/fonts/")
                || path.startsWith("/assets/")
                || path.startsWith("/static/")
                || path.startsWith("/public/")
                || path.startsWith("/webjars/")
                || path.matches(".*\\.(css|js|map|png|jpg|jpeg|gif|svg|ico|woff2?|ttf|eot)$");
    }
}