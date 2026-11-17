package com.sankalp.fairexit.config;

import jakarta.servlet.ReadListener;
import jakarta.servlet.ServletInputStream;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletRequestWrapper;
import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;

/**
 * Reads the whole body up front so it can be hashed and still be read by the controller.
 * (Spring's ContentCachingRequestWrapper only caches after the controller has read it.)
 */
class CachedBodyRequest extends HttpServletRequestWrapper {

    static class TooLargeException extends IOException {
        TooLargeException() {
            super("Request body too large");
        }
    }

    private final byte[] body;

    CachedBodyRequest(HttpServletRequest request, int maxBytes) throws IOException {
        super(request);
        byte[] read = request.getInputStream().readNBytes(maxBytes + 1);
        if (read.length > maxBytes) {
            throw new TooLargeException();
        }
        this.body = read;
    }

    byte[] body() {
        return body;
    }

    @Override
    public ServletInputStream getInputStream() {
        ByteArrayInputStream in = new ByteArrayInputStream(body);
        return new ServletInputStream() {
            @Override
            public boolean isFinished() {
                return in.available() == 0;
            }

            @Override
            public boolean isReady() {
                return true;
            }

            @Override
            public void setReadListener(ReadListener listener) {
                throw new UnsupportedOperationException("Async reads are not supported");
            }

            @Override
            public int read() {
                return in.read();
            }

            @Override
            public int read(byte[] b, int off, int len) {
                return in.read(b, off, len);
            }
        };
    }

    @Override
    public BufferedReader getReader() {
        String encoding = getCharacterEncoding();
        Charset charset = encoding != null ? Charset.forName(encoding) : StandardCharsets.UTF_8;
        return new BufferedReader(new InputStreamReader(getInputStream(), charset));
    }
}
