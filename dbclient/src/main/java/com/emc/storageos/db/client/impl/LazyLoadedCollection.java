/**
* Copyright 2015 EMC Corporation
* All Rights Reserved
 */
/**
 *  Copyright (c) 2014 EMC Corporation
 * All Rights Reserved
 *
 * This software contains the intellectual property of EMC Corporation
 * or is licensed to EMC Corporation from third parties.  Use of this
 * software and the intellectual property contained therein is expressly
 * limited to the terms and conditions of the License Agreement under which
 * it is provided by or on behalf of EMC.
 */

package com.emc.storageos.db.client.impl;

import java.util.Collection;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Set;

import com.emc.storageos.db.client.model.DataObject;
import com.emc.storageos.db.client.model.StringSet;
import com.emc.storageos.db.client.util.DbClientCallbackEvent;

/**
 * @author cgarber
 *
 */
public abstract class LazyLoadedCollection<E extends DataObject> implements Collection<E> {
    
    protected Collection<E> list;
    protected LazyLoader lazyLoader;
    protected String fieldName;
    protected E parentObj;
    protected boolean iteratorOnly;
    protected boolean loaded;
    protected Iterator<E> iterator;
    protected StringSet mappedByUriSet;
    
    protected abstract Collection<E> getNewCollection();

    /**
     * @param name
     * @param mappedBy 
     * @param id
     * @param lazyLoader2
     */
    public LazyLoadedCollection(String name, E parentObj, LazyLoader lazyLoader, StringSet mappedBy) {
        this.fieldName = name;
        this. parentObj = parentObj;
        this.lazyLoader = lazyLoader;
        this.mappedByUriSet = mappedBy;
        loaded = false;
        iteratorOnly = true;
    }

    protected synchronized Collection<E> getCollection() {
        if (list == null) list = getNewCollection();
        if (!loaded || (loaded && iteratorOnly)) {
            list.clear();
            // load collection elements
            lazyLoader.load(fieldName, parentObj, list, new InvalidateLazyLoadedListCb<E>(this));
            loaded = true;
            iteratorOnly = false;
        }
        return list;
    }
    
    protected synchronized Iterator<E> populateIteratorResults() {
        
        if (list == null) list = getNewCollection();
        
        if (loaded && !iteratorOnly) {
            return list.iterator();
        }
       
        if (!loaded) {
            list.clear();
            iterator = lazyLoader.load(fieldName, parentObj, (Collection<E>) null, new InvalidateLazyLoadedListCb<E>(this));
            if (iterator != null) {
                loaded = true;
                iteratorOnly = true;
            }
        }
        
        return iterator;
    }
    
    public synchronized void invalidate() {
        loaded = false;
    }
    
    /* (non-Javadoc)
     * @see java.util.Collection#size()
     */
    @Override
    public int size() {
        return getCollection().size();
    }

    /* (non-Javadoc)
     * @see java.util.Collection#isEmpty()
     */
    @Override
    public boolean isEmpty() {
        return getCollection().isEmpty();
    }

    /* (non-Javadoc)
     * @see java.util.Collection#contains(java.lang.Object)
     */
    @Override
    public boolean contains(Object o) {
        return getCollection().contains(o);
    }

    /* (non-Javadoc)
     * @see java.util.Collection#iterator()
     */
    @Override
    public Iterator<E> iterator() {
        return populateIteratorResults();
    }

    /* (non-Javadoc)
     * @see java.util.Collection#toArray()
     */
    @Override
    public Object[] toArray() {
        return getCollection().toArray();
    }

    /* (non-Javadoc)
     * @see java.util.Collection#toArray(java.lang.Object[])
     */
    @Override
    public <T> T[] toArray(T[] a) {
        return getCollection().toArray(a);
    }

    /* (non-Javadoc)
     * @see java.util.Collection#add(java.lang.Object)
     */
    @Override
    public synchronized boolean add(E e) {
        if (mappedByUriSet != null) {
            DbClientCallbackEvent cb = mappedByUriSet.getCallback();
            mappedByUriSet.setCallback(null);
            mappedByUriSet.add(e.getId().toString());
            mappedByUriSet.setCallback(cb);
        }
        return getCollection().add(e);
    }

    /* (non-Javadoc)
     * @see java.util.Collection#remove(java.lang.Object)
     */
    @Override
    public synchronized boolean remove(Object o) {
        if (mappedByUriSet != null && DataObject.class.isAssignableFrom(o.getClass())) {
            DbClientCallbackEvent cb = mappedByUriSet.getCallback();
            mappedByUriSet.setCallback(null);
            mappedByUriSet.remove(((DataObject)o).getId().toString());
            mappedByUriSet.setCallback(cb);
       }
        return getCollection().remove(o);
    }

    /* (non-Javadoc)
     * @see java.util.Collection#containsAll(java.util.Collection)
     */
    @Override
    public boolean containsAll(Collection<?> c) {
        return getCollection().containsAll(c);
    }

    /* (non-Javadoc)
     * @see java.util.Collection#addAll(java.util.Collection)
     */
    @Override
    public synchronized boolean addAll(Collection<? extends E> c) {
        if (mappedByUriSet != null) {
            DbClientCallbackEvent cb = mappedByUriSet.getCallback();
            mappedByUriSet.setCallback(null);
            mappedByUriSet.addAll(toIds(c));
            mappedByUriSet.setCallback(cb);
        }
        return getCollection().addAll(c);
    }

    /* (non-Javadoc)
     * @see java.util.Collection#removeAll(java.util.Collection)
     */
    @Override
    public synchronized boolean removeAll(Collection<?> c) {
        if (mappedByUriSet != null) {
            DbClientCallbackEvent cb = mappedByUriSet.getCallback();
            mappedByUriSet.setCallback(null);
            mappedByUriSet.remove(toIds(c));
            mappedByUriSet.setCallback(cb);
        }
        return getCollection().removeAll(c);
    }

    /* (non-Javadoc)
     * @see java.util.Collection#retainAll(java.util.Collection)
     */
    @Override
    public synchronized boolean retainAll(Collection<?> c) {
        if (mappedByUriSet != null) {
            DbClientCallbackEvent cb = mappedByUriSet.getCallback();
            mappedByUriSet.setCallback(null);
            mappedByUriSet.retainAll(toIds(c));
            mappedByUriSet.setCallback(cb);
        }
        return getCollection().retainAll(c);
    }

    /* (non-Javadoc)
     * @see java.util.Collection#clear()
     */
    @Override
    public synchronized void clear() {
        if (mappedByUriSet != null) {
            DbClientCallbackEvent cb = mappedByUriSet.getCallback();
            mappedByUriSet.setCallback(null);
            mappedByUriSet.clear();
            mappedByUriSet.setCallback(cb);
        }
        getCollection().clear();
    }
    
    public static class InvalidateLazyLoadedListCb<T extends DataObject> implements DbClientCallbackEvent {
        
        private LazyLoadedCollection<T> list;
        
        public InvalidateLazyLoadedListCb(LazyLoadedCollection<T> lazyLoadedCollection) {
            this.list = lazyLoadedCollection;
        }
        
        /* (non-Javadoc)
         * @see com.emc.storageos.db.client.util.DbClientCallbackEvent#call(java.lang.Object[])
         */
        @Override
        public void call(Object... args) {
            list.invalidate();
        }
    }
    
    public boolean isLoaded() {
        return loaded;
    }

    private Set<String> toIds(Collection<?> c) {
        Set<String> ids = new HashSet<String>();
        for (Object obj : c) {
            if (DataObject.class.isAssignableFrom(obj.getClass())) {
                ids.add(((DataObject) obj).getId().toString());
            }
        }
        return ids;
    }

}
