/*
 * Copyright (c) 2016 Dell EMC Software
 * All Rights Reserved
 */

package com.iwave.ext.windows.winrm.entity;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

import org.apache.commons.io.IOUtils;
import org.apache.http.HttpEntity;
import org.apache.http.entity.HttpEntityWrapper;

/**
 * Creates a repeatable entity out of an entity.
 * 
 * @author Jason Forand
 *
 */
public class RepeatableEntity extends HttpEntityWrapper {

    /** The content from the original entity. */
    private byte[] content;

    /**
     * Creates a repeatable HttpEntity.
     * 
     * @param wrappedEntity
     *            the original entity to repeat
     */
    public RepeatableEntity(HttpEntity wrappedEntity) {
        super(wrappedEntity);
        try {
            content = IOUtils.toByteArray(wrappedEntity.getContent());
        } catch (Exception e) {
            throw new RuntimeException("There was an error when trying to make entity repeatable.", e);
        }
    }

    @Override
    public InputStream getContent() throws IOException {
        if (content != null) {
            return new ByteArrayInputStream(content);
        }
        return super.getContent();
    }

    @Override
    public void writeTo(OutputStream outstream) throws IOException {
        if (content != null) {
            outstream.write(content);
        } else {
            super.writeTo(outstream);
        }
    }

    @Override
    public boolean isRepeatable() {
        return true;
    }

}
