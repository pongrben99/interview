package com.example.alerting.web;

import jakarta.servlet.ReadListener;
import jakarta.servlet.ServletInputStream;
import java.io.IOException;

/**
 * Passes a request body through while counting its bytes, and fails as soon as the limit is exceeded.
 *
 * <p>The {@code Content-Length} header can be absent (chunked transfer) or wrong, so the limit is enforced
 * on the bytes actually read. The check happens as the body is read, so an oversized body is never held in
 * memory beyond the limit.
 */
final class LimitedServletInputStream extends ServletInputStream {

    private final ServletInputStream delegate;
    private final long maxBytes;
    private long bytesRead;

    LimitedServletInputStream(ServletInputStream delegate, long maxBytes) {
        this.delegate = delegate;
        this.maxBytes = maxBytes;
    }

    @Override
    public int read() throws IOException {
        int value = delegate.read();
        if (value != -1) {
            count(1);
        }
        return value;
    }

    @Override
    public int read(byte[] buffer, int offset, int length) throws IOException {
        int read = delegate.read(buffer, offset, length);
        if (read > 0) {
            count(read);
        }
        return read;
    }

    private void count(int bytes) throws IOException {
        bytesRead += bytes;
        if (bytesRead > maxBytes) {
            throw new RequestBodyTooLargeException(maxBytes);
        }
    }

    @Override
    public boolean isFinished() {
        return delegate.isFinished();
    }

    @Override
    public boolean isReady() {
        return delegate.isReady();
    }

    @Override
    public void setReadListener(ReadListener readListener) {
        delegate.setReadListener(readListener);
    }
}
