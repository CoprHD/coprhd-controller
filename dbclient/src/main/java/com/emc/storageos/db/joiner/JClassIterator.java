/*
 * Copyright (c) 2014 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.db.joiner;

import java.net.URI;
import java.util.Iterator;

/**
 * An Iterator<T> that iterates through the results from 
 * a database query term.
 * @author watson
 * @param <T>
 */
class JClassIterator<T> implements Iterator<T> {
    private JClass jc;
    private QueryEngine engine;
    private Iterator<URI> uriIterator;
    
    JClassIterator(JClass jc, QueryEngine engine) {
        this.jc = jc;
        this.engine = engine;
        uriIterator = jc.getUris().iterator();
    }
    
    JClassIterator(JClass jc, QueryEngine engine, Iterator<URI> uriIterator) {
        this.jc = jc;
        this.engine = engine;
        this.uriIterator = uriIterator;
    }

    @Override
    public boolean hasNext() {
        return uriIterator.hasNext();
    }

    @Override
    public T next() {
        URI uri = uriIterator.next();
        return (T) jc.queryObject(engine, uri);
    }

    @Override
    public void remove() {
        throw new UnsupportedOperationException("remove not supported for JClassIterator");
    }
}
