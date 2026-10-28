package com.tpi.toxicity.api;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.tpi.toxicity.dto.ErrorResponse;
import com.tpi.toxicity.model.ToxicityProperties;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ReadListener;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletInputStream;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletRequestWrapper;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

/**
 * Protege /api/** con la cabecera {@code X-API-Key} y limita el tamaño del cuerpo antes de leerlo.
 * Los health checks quedan abiertos. Sin API key configurada la aplicación no arranca, salvo
 * que se active explícitamente {@code toxicity.auth-disabled} (solo desarrollo local).
 */
@Component
public class ApiKeyAndSizeFilter extends OncePerRequestFilter {

    static final String HEADER = "X-API-Key";

    private final byte[] apiKey;
    private final boolean authDisabled;
    private final long maxBodyBytes;
    private final ObjectMapper mapper;

    public ApiKeyAndSizeFilter(ToxicityProperties props, ObjectMapper mapper) {
        boolean blank = props.apiKey() == null || props.apiKey().isBlank();
        if (blank && !props.authDisabled()) {
            throw new IllegalStateException(
                    "TOXICITY_API_KEY es obligatorio (o TOXICITY_AUTH_DISABLED=true solo para desarrollo local)");
        }
        this.apiKey = blank ? new byte[0] : props.apiKey().getBytes(StandardCharsets.UTF_8);
        this.authDisabled = props.authDisabled();
        // peor caso JSON: cada carácter escapado como \\uXXXX (6 bytes) + margen para la estructura
        this.maxBodyBytes = (long) props.maxChars() * 6 + 1024;
        this.mapper = mapper;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return !request.getRequestURI().startsWith("/api/");
    }

    @Override
    protected void doFilterInternal(HttpServletRequest req, HttpServletResponse res, FilterChain chain)
            throws ServletException, IOException {
        if (!authDisabled && !authorized(req.getHeader(HEADER))) {
            res.setHeader("WWW-Authenticate", "ApiKey");
            write(res, HttpServletResponse.SC_UNAUTHORIZED, "unauthorized", "missing or invalid API key");
            return;
        }
        if (req.getContentLengthLong() > maxBodyBytes) {
            write(res, HttpServletResponse.SC_REQUEST_ENTITY_TOO_LARGE, "text_too_long", "request body too large");
            return;
        }
        chain.doFilter(new LimitedRequest(req, maxBodyBytes), res);
    }

    private boolean authorized(String provided) {
        if (provided == null) {
            return false;
        }
        return MessageDigest.isEqual(provided.getBytes(StandardCharsets.UTF_8), apiKey);
    }

    private void write(HttpServletResponse res, int status, String error, String message) throws IOException {
        res.setStatus(status);
        res.setContentType(MediaType.APPLICATION_JSON_VALUE);
        mapper.writeValue(res.getOutputStream(), new ErrorResponse(error, message));
    }

    /** Corta la lectura si el cuerpo (p. ej. chunked, sin Content-Length) supera el límite. */
    private static final class LimitedRequest extends HttpServletRequestWrapper {
        private final long limit;

        LimitedRequest(HttpServletRequest request, long limit) {
            super(request);
            this.limit = limit;
        }

        @Override
        public ServletInputStream getInputStream() throws IOException {
            ServletInputStream in = super.getInputStream();
            return new ServletInputStream() {
                private long read;

                @Override
                public int read() throws IOException {
                    int b = in.read();
                    if (b >= 0 && ++read > limit) {
                        throw new RequestBodyTooLargeException();
                    }
                    return b;
                }

                @Override
                public int read(byte[] buf, int off, int len) throws IOException {
                    int n = in.read(buf, off, len);
                    if (n > 0 && (read += n) > limit) {
                        throw new RequestBodyTooLargeException();
                    }
                    return n;
                }

                @Override
                public boolean isFinished() {
                    return in.isFinished();
                }

                @Override
                public boolean isReady() {
                    return in.isReady();
                }

                @Override
                public void setReadListener(ReadListener listener) {
                    in.setReadListener(listener);
                }
            };
        }
    }
}
