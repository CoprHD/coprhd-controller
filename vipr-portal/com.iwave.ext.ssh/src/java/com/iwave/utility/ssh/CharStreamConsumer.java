/*
 * Copyright 2012-2015 iWave Software LLC
 * All Rights Reserved
 */
package com.iwave.utility.ssh;

import java.io.BufferedInputStream;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.io.UnsupportedEncodingException;

import org.apache.log4j.Logger;

public class CharStreamConsumer {
    public static final String DEFAULT_CHARSET = "UTF-8";

    /** Optional logger for logging. */
    private Logger logger;

    /** The thread to run the consumer. */
    private volatile Thread thread = new Thread("CharStreamConsumer") {
        public void run() {
            CharStreamConsumer.this.run();
        }
    };

    /** Buffer for the contents of the InputStream. */
    private StringBuffer buffer = new StringBuffer();

    /** The stream to consume. */
    private Reader in;

    /** Whether to strip control characters. */
    private boolean stripControlChars = true;

    /**
     * Creates a CharStreamConsumer for consuming the data from the character stream.
     * 
     * @param stream the stream to consume.
     */
    public CharStreamConsumer(InputStream stream) throws UnsupportedEncodingException {
        this(stream, DEFAULT_CHARSET);
    }

    /**
     * Creates a CharStreamConsumer for consuming the data from the character stream.
     * 
     * @param stream the stream to consume.
     * @param charset the charset to use.
     */
    public CharStreamConsumer(InputStream stream, String charset)
            throws UnsupportedEncodingException {
        this(new InputStreamReader(new BufferedInputStream(stream), charset));
    }

    /**
     * Creates a CharStreamConsumer for consuming the data from the character stream.
     * 
     * @param stream the character stream.
     */
    public CharStreamConsumer(Reader stream) {
        in = new BufferedReader(stream);
        thread.start();
    }

    public boolean isStripControlChars() {
        return stripControlChars;
    }

    public void setStripControlChars(boolean stripControlChars) {
        this.stripControlChars = stripControlChars;
    }

    public Logger getLogger() {
        return logger;
    }

    public void setLogger(Logger logger) {
        this.logger = logger;
    }

    public void createLogger() {
        logger = Logger.getLogger(getClass());
    }

    /**
     * Stops the monitor.
     */
    public void close() {
        Thread current = thread;
        thread = null;

        if (current != null) {
            current.interrupt();
            try {
                current.join();
            }
            catch (InterruptedException e) {
                // Ignore
            }
        }
    }

    /**
     * Consumes the character stream until it runs out or is interrupted.
     */
    private void run() {
        try {
            Thread current = Thread.currentThread();

            char[] buf = new char[1024];
            for (int len = in.read(buf); len != -1; len = in.read(buf)) {
                append(buf, 0, len);

                // Terminate early if the consumer was stopped
                if (current != thread) {
                    break;
                }
            }
        }
        catch (IOException e) {
        	logger.error(e.getMessage(), e);
        }
        trace("Finished");
    }

    protected void append(char[] buf, int start, int len) {
        trace("Received: %s", new String(buf, start, len));
        if (!stripControlChars) {
            buffer.append(buf, start, len);
            return;
        }

        int end = Math.min(buf.length, start + len);
        for (int i = start; i < end; i++) {
            // Look for ANSI escape sequences
            int escapeLen = getAnsiEscapeLength(buf, i, end);
            if (escapeLen > 0) {
                trace("Skipping ANSI escape: %s", new String(buf, i, escapeLen));
                i += escapeLen - 1;
            }
            else {
                char ch = buf[i];
                if (!isControlChar(ch)) {
                    buffer.append(ch);
                }
                else {
                    trace("Stripped Control Char: %02X", (int) ch);
                }
            }
        }
    }

    /**
     * Gets the length of the ANSI escape sequence at the given position in the buffer. If the
     * current position is not an ANSI escape, this will return 0;
     * 
     * @param buf the current buffer.
     * @param start the start position.
     * @param len the length available in the buffer.
     * @return the ANSI escape length.
     */
    protected int getAnsiEscapeLength(char[] buf, int start, int len) {
        if (buf[start] != 0x1B) {
            return 0;
        }
        // This means there is part of an escape sequence... kind of a problem
        if (start + 1 >= len) {
            return 1;
        }
        // 2 Character escape sequence has anything but 0x5B ([)
        if (buf[start + 1] != 0x5B) {
            return 2;
        }

        // Look for a terminating character to the escape (0x40-0x7E)
        int escapeLen = 2;
        while ((start + escapeLen) < len) {
            char ch = buf[start + escapeLen];
            escapeLen++;
            if ((ch >= 0x40) && (ch <= 0x7E)) {
                break;
            }
        }
        return escapeLen;
    }

    /**
     * Determines if the character is a control character.
     * 
     * @param ch the character.
     * @return true if the character is a control character.
     */
    protected boolean isControlChar(char ch) {
        return Character.isISOControl(ch) && !Character.isWhitespace(ch);
    }

    /**
     * Gets the raw buffer holding the output.
     * 
     * @return the buffer.
     */
    public StringBuffer getBuffer() {
        return buffer;
    }

    /**
     * Returns a String based on the contents of the buffer.
     */
    public String toString() {
        return buffer.toString();
    }

    /**
     * Outputs a trace level message, only if a logger is present and enabled for tracing.
     * 
     * @param message the message.
     * @param args the message args.
     */
    protected void trace(String message, Object... args) {
        if (logger != null && logger.isTraceEnabled()) {
            if (args.length > 0) {
                logger.trace(String.format(message, args));
            }
            else {
                logger.trace(message);
            }
        }
    }
}
