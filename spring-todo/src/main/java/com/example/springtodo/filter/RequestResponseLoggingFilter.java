package com.example.springtodo.filter;

import jakarta.servlet.Filter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.util.ContentCachingRequestWrapper;
import org.springframework.web.util.ContentCachingResponseWrapper;

import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.util.Collection;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.Map;

@Component
@Order(1)
public class RequestResponseLoggingFilter implements Filter {

    private static final Logger logger = LoggerFactory.getLogger(RequestResponseLoggingFilter.class);

    @Override
    public void doFilter(ServletRequest servletRequest, ServletResponse servletResponse, FilterChain filterChain)
            throws IOException, ServletException {

        HttpServletRequest request = (HttpServletRequest) servletRequest;
        HttpServletResponse response = (HttpServletResponse) servletResponse;

        // Wrap request and response to allow reading body multiple times
        ContentCachingRequestWrapper wrappedRequest = new ContentCachingRequestWrapper(request);
        ContentCachingResponseWrapper wrappedResponse = new ContentCachingResponseWrapper(response);

        long startTime = System.currentTimeMillis();

        try {
            // Log request
            logRequest(wrappedRequest);

            // Continue with the filter chain
            filterChain.doFilter(wrappedRequest, wrappedResponse);

        } finally {
            long duration = System.currentTimeMillis() - startTime;

            // Log response
            logResponse(wrappedRequest, wrappedResponse, duration);

            // Important: copy the cached response content to actual response
            wrappedResponse.copyBodyToResponse();
        }
    }

    private void logRequest(ContentCachingRequestWrapper request) {
        String uri = request.getRequestURI();
        String method = request.getMethod();
        String queryString = request.getQueryString();

        StringBuilder logMessage = new StringBuilder();
        logMessage.append("\n========== Incoming Request ==========\n");
        logMessage.append("Method: ").append(method).append("\n");
        logMessage.append("URI: ").append(uri);
        if (queryString != null) {
            logMessage.append("?").append(queryString);
        }
        logMessage.append("\n");

        // Log headers
        Map<String, String> headers = getHeaders(request);
        if (!headers.isEmpty()) {
            logMessage.append("Headers: ").append(headers).append("\n");
        }

        // Log request body (if present)
        String requestBody = getRequestBody(request);
        if (!requestBody.isEmpty()) {
            logMessage.append("Request Body: ").append(requestBody).append("\n");
        }

        logMessage.append("======================================");

        logger.info(logMessage.toString());
    }

    private void logResponse(ContentCachingRequestWrapper request,
                            ContentCachingResponseWrapper response,
                            long duration) {
        String uri = request.getRequestURI();
        String method = request.getMethod();
        int status = response.getStatus();

        StringBuilder logMessage = new StringBuilder();
        logMessage.append("\n========== Outgoing Response ==========\n");
        logMessage.append("Method: ").append(method).append("\n");
        logMessage.append("URI: ").append(uri).append("\n");
        logMessage.append("Status: ").append(status).append("\n");
        logMessage.append("Duration: ").append(duration).append(" ms\n");

        // Log response headers
        Map<String, String> headers = getResponseHeaders(response);
        if (!headers.isEmpty()) {
            logMessage.append("Headers: ").append(headers).append("\n");
        }

        // Log response body
        String responseBody = getResponseBody(response);
        if (!responseBody.isEmpty()) {
            logMessage.append("Response Body: ").append(responseBody).append("\n");
        }

        logMessage.append("=======================================");

        if (status >= 400) {
            logger.error(logMessage.toString());
        } else {
            logger.info(logMessage.toString());
        }
    }

    private Map<String, String> getHeaders(HttpServletRequest request) {
        Map<String, String> headers = new HashMap<>();
        Enumeration<String> headerNames = request.getHeaderNames();
        while (headerNames.hasMoreElements()) {
            String headerName = headerNames.nextElement();
            String headerValue = request.getHeader(headerName);
            // Don't log sensitive headers
            if (!headerName.equalsIgnoreCase("authorization") &&
                !headerName.equalsIgnoreCase("cookie")) {
                headers.put(headerName, headerValue);
            }
        }
        return headers;
    }

    private Map<String, String> getResponseHeaders(HttpServletResponse response) {
        Map<String, String> headers = new HashMap<>();
        Collection<String> headerNames = response.getHeaderNames();
        for (String headerName : headerNames) {
            String headerValue = response.getHeader(headerName);
            headers.put(headerName, headerValue);
        }
        return headers;
    }

    private String getRequestBody(ContentCachingRequestWrapper request) {
        byte[] buf = request.getContentAsByteArray();
        if (buf.length > 0) {
            try {
                return new String(buf, 0, buf.length, request.getCharacterEncoding());
            } catch (UnsupportedEncodingException e) {
                return "[Unable to parse request body]";
            }
        }
        return "";
    }

    private String getResponseBody(ContentCachingResponseWrapper response) {
        byte[] buf = response.getContentAsByteArray();
        if (buf.length > 0) {
            try {
                return new String(buf, 0, buf.length, response.getCharacterEncoding());
            } catch (UnsupportedEncodingException e) {
                return "[Unable to parse response body]";
            }
        }
        return "";
    }
}

