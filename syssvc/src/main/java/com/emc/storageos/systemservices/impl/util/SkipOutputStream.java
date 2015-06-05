/**
* Copyright 2015 EMC Corporation
* All Rights Reserved
 */
/**
 * Copyright (c) 2014 EMC Corporation
 * All Rights Reserved
 *
 * This software contains the intellectual property of EMC Corporation
 * or is licensed to EMC Corporation from third parties.  Use of this
 * software and the intellectual property contained therein is expressly
 * limited to the terms and conditions of the License Agreement under which
 * it is provided by or on behalf of EMC.
 */
package com.emc.storageos.systemservices.impl.util;

import java.io.IOException;
import java.io.OutputStream;

/**
 * An OutputStream that will ignore first N bytes written to it, before passing
 * writes to underlying OutputStream.
 */
public class SkipOutputStream extends OutputStream {
    OutputStream innerStream;
    long bytesToSkip;

    public SkipOutputStream(OutputStream innerStream, long toSkip) {
        this.innerStream = innerStream;
        this.bytesToSkip = toSkip;
    }

    private int skip(int len) {
        long toSkip = Math.min(this.bytesToSkip, len);
        this.bytesToSkip -= toSkip;
        return (int)toSkip;
    }

    @Override
    public void write(int b) throws IOException {
        if (skip(1) > 0) {
            return;
        }
        this.innerStream.write(b);
    }

    @Override
    public void write(byte b[]) throws IOException {
        this.write(b, 0, b.length);
    }

    @Override
    public void write(byte b[], int off, int len) throws IOException {
        int toSkip = skip(len);
        this.innerStream.write(b, off + toSkip, len - toSkip);
    }

    @Override
    public void flush() throws IOException {
        this.innerStream.flush();
    }

    @Override
    public void close() throws IOException {
        this.innerStream.close();
    }
}