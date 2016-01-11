/*
 * Copyright (c) 2016 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.management.backup.util;

import java.io.IOException;
import java.io.InputStream;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ProcessInputStream extends InputStream {
    private static final Logger log = LoggerFactory.getLogger(ProcessInputStream.class);

    InputStream stdinStream;
    ProcessRunner processor;
    StringBuilder errText = new StringBuilder();

    public ProcessInputStream(ProcessRunner processor) {
        this.processor = processor;
        this.stdinStream = processor.getStdOut();

        processor.captureAllTextInBackground(processor.getStdErr(), this.errText);
    }

    public ProcessInputStream(Process childProcess) throws IOException {
        this(new ProcessRunner(childProcess, true));
    }

    @Override
    public int read() throws IOException {
        return this.stdinStream.read();
    }

    @Override
    public int read(byte b[]) throws IOException {
        return stdinStream.read(b);
    }

    @Override
    public int read(byte b[], int off, int len) throws IOException {
        return stdinStream.read(b, off, len);
    }

    @Override
    public void close() throws IOException {
        if (this.stdinStream != null) {
            int remains;
            while (( remains = stdinStream.available()) > 0) {
                stdinStream.skip(remains);
            }
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
