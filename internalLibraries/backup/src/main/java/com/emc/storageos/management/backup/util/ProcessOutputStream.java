/*
 * Copyright (c) 2014 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.management.backup.util;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.OutputStream;

/**
 * An OutputStream wraps a child process, writing to this stream will writes to child process's STDIN.
 * And closing this stream will also close child process and recycle all related resources.
 * When closing this this stream, any output from child process's STDERR will be thrown as exception, and if
 * child process exited with non-zero code, an exception will be thrown too.
 */
public class ProcessOutputStream extends OutputStream {
    private static final Logger log = LoggerFactory.getLogger(ProcessOutputStream.class);

    OutputStream stdinStream;
    ProcessRunner processor;
    StringBuilder errText = new StringBuilder();
    
    public static final int ERROR_TEXT_MAX_LENGTH = 1024;

    public ProcessOutputStream(ProcessRunner processor) {
        this.processor = processor;
        this.stdinStream = processor.getStdIn();

        processor.captureAllTextInBackground(processor.getStdErr(), this.errText);
    }

    public ProcessOutputStream(Process childProcess) throws IOException {
        this(new ProcessRunner(childProcess, true));
    }

    @Override
    public void write(int b) throws IOException {
        this.stdinStream.write(b);
    }

    @Override
    public void write(byte b[]) throws IOException {
        this.stdinStream.write(b);
    }

    @Override
    public void write(byte b[], int off, int len) throws IOException {
        this.stdinStream.write(b, off, len);
    }

    @Override
    public void flush() throws IOException {
        this.stdinStream.flush();
    }

    @Override
    public void close() throws IOException {
        if (this.stdinStream != null) {
            this.stdinStream.close();
            this.stdinStream = null;
        }

        if (this.processor != null) {
            int exitCode = 0;
            try {
                exitCode = this.processor.join();
            } catch (InterruptedException e) {
                log.error("Interrupted when waiting for process", e);
            }

            this.processor.close();
            this.processor = null;

            if (exitCode != 0) {
                throw new IOException(errText.length() > 0 ? errText.toString() : Integer.toString(exitCode));
            }
        }
    }
}
