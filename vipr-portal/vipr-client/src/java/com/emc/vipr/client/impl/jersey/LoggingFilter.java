/*
 * Copyright 2015 EMC Corporation
 * All Rights Reserved
 */
package com.emc.vipr.client.impl.jersey;

import com.sun.jersey.api.client.*;
import com.sun.jersey.api.client.filter.ClientFilter;
import org.slf4j.Logger;
import java.io.*;
import java.util.concurrent.atomic.AtomicLong;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Filter to log input/output to our log file. This is based on the Jersey LoggingFilter with some differences:
 * <ul>
 * <li>Logs to slf4j</li>
 * <li>Does not include headers (so no tokens/passwords)</li>
 * <li>Includes timing information</li>
 * <li>Has a maximum length for entities (Prevents performance degradation)</li>
 * </ul>
 */
public class LoggingFilter extends ClientFilter {
    private static final Pattern PASSWORD_PATTERN = Pattern.compile(
            "(<[\\w\\-\\_]*password\\>|password\\<\\/key\\>\\s*\\<value\\>|<secret_key[\\w\\-\\_]*\\>)(.*?)(<\\/|$)",
            Pattern.CASE_INSENSITIVE | Pattern.DOTALL);
    private static final String PASSWORD_REPLACEMENT = "$1*****$3";

    private static AtomicLong id = new AtomicLong(0);
    private final int maxEntityLength;
    private final Logger log;

    private final class Adapter extends AbstractClientRequestAdapter {
        private final StringBuilder b;

        Adapter(ClientRequestAdapter cra, StringBuilder b) {
            super(cra);
            this.b = b;
        }

        public OutputStream adapt(ClientRequest request, OutputStream out) throws IOException {
            return new LoggingOutputStream(getAdapter().adapt(request, out), b);
        }
    }

    /**
     * Stream for buffering information to log that limits the size of the buffer to maxEntityLength. Once the maximum
     * length is reached or the stream is closed, the information is logged.
     */
    private final class LoggingBufferStream extends ByteArrayOutputStream {
        private final StringBuilder sb;
        private boolean logged;
        private boolean truncated;
        private long startTime;

        public LoggingBufferStream(StringBuilder sb, long startTime) {
            this.sb = sb;
            this.startTime = startTime;
        }

        @Override
        public synchronized void write(int b) {
            if (!isFull()) {
                super.write(b);
            }
        }

        @Override
        public void write(byte[] b) throws IOException {
            write(b, 0, b.length);
        }

        @Override
        public synchronized void write(byte[] b, int off, int len) {
            int writeLen = Math.min(len, available());
            if (writeLen > 0) {
                super.write(b, off, writeLen);
            }
            if (writeLen < len) {
                truncated = true;
            }
        }

        @Override
        public void close() throws IOException {
            log();
        }

        private boolean isFull() {
            return (maxEntityLength > 0) && (size() >= maxEntityLength);
        }

        private int available() {
            if (maxEntityLength > 0) {
                return maxEntityLength - size();
            }
            else {
                return Integer.MAX_VALUE - size();
            }
        }

        private void log() {
            if (!logged) {
                // If startTime is specified, include the timing of the operation as part of the log
                if (startTime > 0) {
                    long deltaTime = System.currentTimeMillis() - startTime;
                    sb.append("  took ").append(deltaTime).append(" ms");
                }

                printEntity(sb, toByteArray(), truncated);
                log.info(sb.toString());
                logged = true;
            }
        }
    }

    private final class LoggingOutputStream extends OutputStream {
        private final OutputStream out;
        private final LoggingBufferStream buffer;

        LoggingOutputStream(OutputStream out, StringBuilder sb) {
            this.out = out;
            this.buffer = new LoggingBufferStream(sb, 0);
        }

        @Override
        public void write(byte[] b) throws IOException {
            buffer.write(b);
            out.write(b);
        }

        @Override
        public void write(byte[] b, int off, int len) throws IOException {
            buffer.write(b, off, len);
            out.write(b, off, len);
        }

        @Override
        public void write(int b) throws IOException {
            buffer.write(b);
            out.write(b);
        }

        @Override
        public void close() throws IOException {
            buffer.close();
            out.close();
        }
    }

    private final class LoggingInputStream extends InputStream {
        private final InputStream in;
        private final LoggingBufferStream buffer;

        LoggingInputStream(InputStream in, StringBuilder sb, long startTime) {
            this.in = in;
            this.buffer = new LoggingBufferStream(sb, startTime);
        }

        @Override
        public int read() throws IOException {
            int b = in.read();
            if (b != -1) {
                buffer.write(b);
            }
            return b;
        }

        @Override
        public int read(byte[] b) throws IOException {
            return read(b, 0, b.length);
        }

        @Override
        public int read(byte[] b, int off, int len) throws IOException {
            int numBytes = in.read(b, off, len);
            if (numBytes != -1) {
                buffer.write(b, off, numBytes);
            }
            return numBytes;
        }

        @Override
        public void close() throws IOException {
            buffer.close();
            in.close();
        }
    }

    public LoggingFilter(Logger logger, int maxEntityLength) {
        this.log = logger;
        this.maxEntityLength = maxEntityLength;
    }

    private StringBuilder prefixId(StringBuilder b, long id) {
        b.append(id).append(" ");
        return b;
    }

    @Override
    public ClientResponse handle(ClientRequest request) throws ClientHandlerException {
        long id = LoggingFilter.id.incrementAndGet();

        if (log.isInfoEnabled()) {
            logRequest(id, request);
        }
        long startTime = System.currentTimeMillis();
        ClientResponse response = getNext().handle(request);

        if (log.isInfoEnabled()) {
            logResponse(id, response, startTime);
        }
        return response;
    }

    private void logRequest(long id, ClientRequest request) {
        StringBuilder b = new StringBuilder();

        prefixId(b, id).append("> ").append(request.getMethod()).append(" ").append(request.getURI().toASCIIString());

        if (request.getEntity() != null) {
            request.setAdapter(new Adapter(request.getAdapter(), b));
        }
        else {
            log.info(b.toString());
        }
    }

    private void logResponse(long id, ClientResponse response, long startTime) {
        StringBuilder b = new StringBuilder();

        String status = Integer.toString(response.getStatus());
        prefixId(b, id).append(String.format("< %s", status));

        InputStream in = response.getEntityInputStream();
        response.setEntityInputStream(new LoggingInputStream(in, b, startTime));
    }

    private void printEntity(StringBuilder b, byte[] entity, boolean truncated) {
        if (entity.length == 0)
            return;

        String entityStr = protectPasswords(new String(entity));
        b.append("\n").append(entityStr);
        if (truncated) {
            b.append("...");
        }
    }

    public static String protectPasswords(String entity) {
        Matcher m = PASSWORD_PATTERN.matcher(entity);
        return m.replaceAll(PASSWORD_REPLACEMENT);
    }
}