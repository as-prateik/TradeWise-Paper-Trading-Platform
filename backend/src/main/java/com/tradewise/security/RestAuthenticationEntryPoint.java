package com.tradewise.security;

import tools.jackson.databind.ObjectMapper;
import com.tradewise.exception.ErrorCode;
import com.tradewise.exception.ErrorResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.stereotype.Component;

/**
 * Replaces Spring Security's default empty 401 body so the error envelope contract
 * holds even before a controller is reached.
 */
@Component
@RequiredArgsConstructor
public class RestAuthenticationEntryPoint implements AuthenticationEntryPoint {

    private final ObjectMapper objectMapper;

    @Override
    public void commence(HttpServletRequest request, HttpServletResponse response,
                         AuthenticationException authException) throws IOException {
        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        ErrorResponse body = ErrorResponse.of(ErrorCode.UNAUTHORIZED,
                "Authentication is required to access this resource", request.getRequestURI());
        objectMapper.writeValue(response.getWriter(), body);
    }
}
