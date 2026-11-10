package com.example.alerting.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import jakarta.servlet.ReadListener;
import jakarta.servlet.ServletInputStream;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.DelegatingServletInputStream;

/** The byte-counting stream that enforces the request size limit while the body is read. */
class LimitedServletInputStreamTest {

    private static LimitedServletInputStream streamOf(int bytes, long limit) {
        return new LimitedServletInputStream(
                new DelegatingServletInputStream(new ByteArrayInputStream(new byte[bytes])), limit);
    }

    @Test
    void aBodyWithinTheLimitIsReadCompletely() throws IOException {
        assertThat(streamOf(100, 100).readAllBytes()).hasSize(100);
    }

    @Test
    void aBodyJustOverTheLimitFailsWhenTheLimitIsCrossed() {
        LimitedServletInputStream stream = streamOf(101, 100);

        assertThatThrownBy(stream::readAllBytes)
                .isInstanceOfSatisfying(RequestBodyTooLargeException.class,
                        e -> assertThat(e.getMaxBytes()).isEqualTo(100));
    }

    @Test
    void readingByteByByteCountsEveryByte() throws IOException {
        LimitedServletInputStream stream = streamOf(3, 2);

        assertThat(stream.read()).isZero();
        assertThat(stream.read()).isZero();
        assertThatThrownBy(stream::read).isInstanceOf(RequestBodyTooLargeException.class);
    }

    @Test
    void theEndOfTheStreamIsNotCountedAndRepeatedEndReadsAreHarmless() throws IOException {
        LimitedServletInputStream stream = streamOf(1, 1);

        assertThat(stream.read()).isZero();
        assertThat(stream.read()).isEqualTo(-1);
        assertThat(stream.read()).isEqualTo(-1);
        assertThat(stream.read(new byte[8], 0, 8)).isEqualTo(-1);
    }

    @Test
    void anEmptyBodyIsAlwaysWithinTheLimit() throws IOException {
        assertThat(streamOf(0, 0).readAllBytes()).isEmpty();
    }

    @Test
    void readingInBlocksCountsTheBytesActuallyRead() throws IOException {
        LimitedServletInputStream stream = streamOf(10, 10);
        byte[] buffer = new byte[4];

        assertThat(stream.read(buffer, 0, 4)).isEqualTo(4);
        assertThat(stream.read(buffer, 0, 4)).isEqualTo(4);
        assertThat(stream.read(buffer, 0, 4)).isEqualTo(2);
        assertThat(stream.read(buffer, 0, 4)).isEqualTo(-1);
    }

    /** A stream that records what the wrapper asks of it. */
    private static final class RecordingStream extends ServletInputStream {

        private final ByteArrayInputStream bytes = new ByteArrayInputStream(new byte[] {1});
        private boolean ready = true;
        private ReadListener listener;

        @Override
        public int read() {
            return bytes.read();
        }

        @Override
        public boolean isFinished() {
            return bytes.available() == 0;
        }

        @Override
        public boolean isReady() {
            return ready;
        }

        @Override
        public void setReadListener(ReadListener readListener) {
            this.listener = readListener;
        }
    }

    @Test
    void theStateQueriesAndTheReadListenerAreHandedToTheUnderlyingStream() throws IOException {
        RecordingStream underlying = new RecordingStream();
        LimitedServletInputStream stream = new LimitedServletInputStream(underlying, 10);
        ReadListener listener = new ReadListener() {
            @Override
            public void onDataAvailable() {
            }

            @Override
            public void onAllDataRead() {
            }

            @Override
            public void onError(Throwable throwable) {
            }
        };

        assertThat(stream.isFinished()).isFalse();
        assertThat(stream.isReady()).isTrue();
        underlying.ready = false;
        assertThat(stream.isReady()).isFalse();
        stream.readAllBytes();
        assertThat(stream.isFinished()).isTrue();
        stream.setReadListener(listener);
        assertThat(underlying.listener).isSameAs(listener);
    }
}
