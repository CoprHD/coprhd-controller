/*
 * Copyright (c) 2008-2013 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.api.service.impl.response;

import java.util.Iterator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.emc.storageos.model.RelatedResourceRep;

/**
 * An iterator to create RelatedResourceRestRep elements
 * 
 */
public class FilterIterator<T extends RelatedResourceRep> 
    implements Iterator<T> {
    private static final Logger _log = LoggerFactory.getLogger(FilterIterator.class);

    private Iterator<T> _queryIterator;
    private ResRepFilter<T> _filter;
    
    T _next = null;
    
    public FilterIterator(Iterator<T> queryIterator,
            ResRepFilter<T> filter) {
        _queryIterator = queryIterator;
        _filter = filter;
    }

    @Override
    public boolean hasNext() {
        if (null == _next) {
            while (_queryIterator.hasNext()) {
                T element = _queryIterator.next();
                if(_filter.isAccessible(element)) {
                    _log.debug("found an accessible resource");
                    _next = element;
                    break;
                }
            }
        }            
        return _next!=null; 
    }
    
    @Override
    public T next() {
        T next = null;
        T ret = null;
        
        if (_next != null) {
            next = _next;
        } else {
            if (hasNext()) {
                next = _next;
            }
        }
        
        if (next != null) {
            ret = next;
            _next = null;
        }
        return ret;
    }
    
    @Override
    public void remove() {
        throw new UnsupportedOperationException();
    }
    
}


