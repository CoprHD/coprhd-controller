/*
 * Copyright (c) 2014 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.management.backup.util;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedReader;
import java.io.Closeable;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.IOException;
import java.io.OutputStream;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.NoSuchElementException;

/**
 * Common class to run an external process.
 */
public class ProcessRunner implements AutoCloseable {
    private static final Logger log = LoggerFactory.getLogger(ProcessRunner.class);

    private Process proc;

    private InputStream stdoutStream;
    private InputStream stderrStream;
    private OutputStream stdinStream;

    private List<Thread> backgroundThreads;

    public InputStream getStdOut() {
        return this.stdoutStream;
    }

    public InputStream getStdErr() {
        return this.stderrStream;
    }

    public OutputStream getStdIn() {
        return this.stdinStream;
    }

    /**
     * Create an instance of ProcessRunner.
     * 
     * @param proc the process started.
     * @param hasInput Whether you have anything to feed into STDIN of the child process.
     *            If you pass true, it's your responsibility to call .getStdIn().close()
     * @throws IOException
     */
    public ProcessRunner(Process proc, boolean hasInput) throws IOException {
        this.proc = proc;

        this.stdoutStream = proc.getInputStream();
        this.stderrStream = proc.getErrorStream();
        this.stdinStream = proc.getOutputStream();

        if (!hasInput) {
            this.stdinStream.close();
            this.stdinStream = null;
        }
    }

    public void captureAllText(InputStream input, StringBuilder capture) throws IOException {
        BufferedReader reader = new BufferedReader(new InputStreamReader(input));
        String line;
        while ((line = reader.readLine()) != null) {
            if (capture.length() > ProcessOutputStream.ERROR_TEXT_MAX_LENGTH) {
                log.warn("Current error text length {} exceeds maximum error text length {}. Discard further errors",
                        capture.length(), ProcessOutputStream.ERROR_TEXT_MAX_LENGTH);
                return;
            } else {
                capture.append(line);
                capture.append("\n");
            }
        }
    }

    /**
     * Capture all outputs from given stream into given StringBuilder.
     * This method itself is not thread-safe.
     * 
     * @param input The input stream
     * @param capture A String builder to contain all text outputs from given stream.
     *            If null is passed, a new StringBuilder will be created and returned.
     * @return The StringBuilder passed to capture, or new StringBuilder if capture is null.
     */
    public void captureAllTextInBackground(final InputStream input, final StringBuilder capture) {
        Thread thread = new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    captureAllText(input, capture);
                } catch (IOException e) {
                    log.error("Error when capturing output text", e);
                    capture.append(e.toString());
                    capture.append("\n");

                    StringWriter sw = new StringWriter();
                    e.printStackTrace(new PrintWriter(sw)); // NOSONAR ("squid:S1148 Suppressing sonar violation of no printStackTrace")
                    capture.append(sw.toString());
                }
            }
        });

        if (this.backgroundThreads == null) {
            this.backgroundThreads = new ArrayList<>();
        }
        this.backgroundThreads.add(thread);

        thread.setDaemon(true);
        thread.start();
    }

    public Iterable<String> enumLines(final InputStream stream) {
        return new Iterable<String>() {
            @Override
            public Iterator<String> iterator() {
                return new LineIterator(stream);
            }
        };
    }

    static class LineIterator implements Iterator<String> {
        BufferedReader reader;
        String line;

        public LineIterator(InputStream stream) {
            this.reader = new BufferedReader(new InputStreamReader(stream));
            pull();
        }

        protected void pull() {
            try {
                this.line = this.reader.readLine();
            } catch (IOException e) {
                log.error("Failed to read line from input stream", e);
            }
        }

        @Override
        public boolean hasNext() {
            return this.line != null;
        }

        @Override
        public String next() {
            if (this.line == null) {
                throw new NoSuchElementException();
            }

            String prevLine = this.line;
            pull();
            return prevLine;
        }

        @Override
        public void remove() {
            throw new UnsupportedOperationException();
        }
    }

    private static void closeStream(Closeable stream, String name) {
        if (stream != null) {
            try {
                stream.close();
            } catch (IOException e) {
                log.error(String.format("Failed to close %s of child process", name), e);
            }
        }
    }

    /**
     * Close the runner and free up resources (background threads and streams).
     * 
     */
    @Override
    public void close() {
        closeStream(this.stdinStream, "STDIN");
        this.stdinStream = null;

        if (this.proc != null) {
            try {
                this.proc.destroy();
                this.proc.waitFor();
            } catch (Exception e) {
                log.error("Exception when closing child process", e);
            }
        }

        if (this.backgroundThreads != null) {
            for (Thread t : this.backgroundThreads) {
                try {
                    t.join();
                } catch (Exception e) {
                    log.error("Failed to join thread", e);
                }
            }
            this.backgroundThreads = null;
        }

        closeStream(this.stdoutStream, "STDOUT");
        this.stdoutStream = null;

        closeStream(this.stderrStream, "STDERR");
        this.stderrStream = null;
    }

    // NOTE: If you're feeding STDIN in another thread, quit that thread before joining

    /**
     * Wait for the child process to quit by itself. Normally the child process quits when it outputs
     * all data to STDOUT or consumed all input from STDIN.
     * If you have passed true to constructor's hasInput parameter, be sure to call .getStdIn().close(), or
     * this method usually won't return as child process is waiting for more input.
     * 
     * @return The exit code of the child process.
     * @throws IOException
     * @throws InterruptedException
     */
    public int join() throws IOException, InterruptedException {
        // Normally the process will quit because either STDOUT or STDIN is end
        this.proc.waitFor();
        int exitVal = this.proc.exitValue();
        this.proc = null;

        return exitVal;
    }

}
