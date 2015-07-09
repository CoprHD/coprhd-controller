/*
 * Copyright 2012-2015 iWave Software LLC
 * All Rights Reserved
 */
package com.iwave.utility.ssh;

import java.io.BufferedInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.UnsupportedEncodingException;

import org.apache.log4j.Logger;

/**
 * Consumes an InputStream, storing the results in a byte array.
 *
 * @author CDail
 */
public class StreamConsumer {
    public static final String DEFAULT_CHARSET = "UTF-8";
    
    private static final Logger log = Logger.getLogger(StreamConsumer.class);
	
    /** The thread to run the consumer. */
    private volatile Thread thread = new Thread() {
        public void run() {
            StreamConsumer.this.run();
        }
    };
    
    /** Buffer for the contents of the InputStream. */
    private ByteArrayOutputStream buffer = new ByteArrayOutputStream();
    
    /** The stream to consume. */
    private InputStream in;
    
    private String charset = DEFAULT_CHARSET;
    
    /**
     * Creates a StreamConsumer for consuming the data from the input stream.
     * 
     * @param  stream
     *         the stream to consume.
     */
    public StreamConsumer(InputStream stream) {
        in = new BufferedInputStream(stream);
        thread.start();
    }
    
    /**
     * Creates a StreamConsumer for consuming the data from the input stream.
     * 
     * @param  stream
     *         the stream to consume.
     */
    public StreamConsumer(InputStream stream, String charset) {
        in = new BufferedInputStream(stream);
        this.charset = charset;
        thread.start();
    }
    
    /**
     * Stops the monitor and closes the buffer. This should only be called
     * if the contents of the buffer are no longer required.
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
        
        try {
            buffer.close();
        }
        catch (IOException e) {
        	log.error(e.getMessage(), e);
            // Ignore
        }
    }
    
    /**
     * Consumes the input stream until it runs out or is interrupted.
     */
    private void run() {
        try {
            Thread current = Thread.currentThread();
            
            byte[] buf = new byte[1024];
            for (int len = in.read(buf); len != -1; len = in.read(buf)) {
                buffer.write(buf, 0, len);
                
                // Terminate early if the consumer was stopped
                if (current != thread) {
                    break;
                }
            }
            
            // Flush and close the stream buffer
            buffer.flush();
            buffer.close();
        }
        catch (IOException e) {
        	
        }
    }
    
    /**
     * Returns a String based on the contents of the buffer.
     */
    public String toString() {
    	try {
			return new String(buffer.toByteArray(), charset);
		} catch (UnsupportedEncodingException e) {
			throw new RuntimeException(e);
		}
    }
}
