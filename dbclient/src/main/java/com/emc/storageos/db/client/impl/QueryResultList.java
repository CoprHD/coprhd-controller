/*
 * Copyright (c) 2008-2011 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.db.client.impl;
import java.net.URI;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Iterator;
import java.util.List;
import java.util.ListIterator;
import java.util.concurrent.atomic.AtomicBoolean;


import com.emc.storageos.db.client.DbClient;
import com.emc.storageos.db.client.model.DataObject;


public abstract class QueryResultList<T extends DataObject> implements List<T> {
    protected DbClientImpl dbClient = null;
    protected List<URI> ids;
    protected Class<T> clazz; 
    protected boolean activeOnly;
    private AtomicBoolean initialized = new AtomicBoolean(false);
    
    public QueryResultList(DbClientImpl dbClient, Class<T> clazz, Collection<URI> ids, boolean activeOnly) {
        this.dbClient = dbClient;
        this.clazz = clazz;
        this.activeOnly = activeOnly;
        this.ids = new ArrayList<URI>(ids);
    }
    
    @Override
    public int size() {
        if (!this.initialized.get()) {
            this.iterateAll();
        }
        return ids.size();
    }

    @Override
    public boolean isEmpty() {
        if (!this.initialized.get()) {
            this.iterateAll();
        }
        return ids.isEmpty();
    }

    @Override
    public boolean contains(Object o) {
        if (!(o instanceof DataObject)) {
            return false;
        }
        
        DataObject obj = (DataObject)o;
        return ids.contains(obj.getId());
    }

    @Override
    public Object[] toArray() {
        return this.getAll().toArray();
    }

    @Override
    public <E> E[] toArray(E[] a) {
        List<T> objects = this.getAll();
        if (a.length < objects.size()) {
            return (E[]) Arrays.copyOf(objects.toArray(), a.length, a.getClass());
        }
        System.arraycopy(objects.toArray(), 0, a, 0, objects.size());
        if (a.length > objects.size()) {
            a[objects.size()] = null;
            
        }
        return a;
    }

    @Override
    public synchronized boolean add(T t) {
        if (!(t instanceof DataObject)) {
            return false;
        }
        
        DataObject obj = (DataObject)t;
        return this.ids.add(obj.getId());
    }

    @Override
    public synchronized boolean remove(Object o) {
        if (!(o instanceof DataObject)) {
            return false;
        }
        
        DataObject obj = (DataObject)o;
        return remove(obj.getId());
    }

    @Override
    public boolean containsAll(Collection<?> c) {
        for (Object o : c) {
            if(!contains(o))
                return false;
        }
        return true;
    }

    @Override
    public synchronized boolean addAll(Collection<? extends T> c) {
        for (T object : c) {
            if (!add(object)) {
                return false;
            }
        }
        return true;
    }

    @Override
    public synchronized boolean removeAll(Collection<?> c) {
        boolean modified = false;
        for (Object o : c) {
            modified |= remove(o);
        }
        return modified;
    }

    @Override
    public synchronized boolean retainAll(Collection<?> c) {
        boolean modified = false;
        for (Object o : c) {
            if (!contains(o)) {
                modified |= remove(o);
            }
        }
        return modified;
    }

    @Override
    public void clear() {
        this.ids.clear();
    }

    @Override
    public T get(int index) {
        checkRange(index);
        if (!this.initialized.get()) {
            this.iterateAll();
        }
        List<T> objects = queryObjects(Arrays.asList(this.ids.get(index)));
        return objects.isEmpty()? null : objects.get(0);
    }

    @Override
    public synchronized T set(int index, T element) {
        checkRange(index);
        T old = get(index);
        this.ids.set(index, element.getId());
        return old;
    }

    @Override
    public synchronized void add(int index, T element) {
        checkRangeForAdd(index);
        this.ids.add(element.getId());
    }

    @Override
    public synchronized T remove(int index) {
        checkRange(index);
         URI id = this.ids.remove(index);
         return queryObject(id);
    }

    @Override
    public int indexOf(Object o) {
        if (!(o instanceof DataObject)) {
            return -1;
        }
        return ids.indexOf(((DataObject)o).getId());
    }

    @Override
    public int lastIndexOf(Object o) {
        if (!(o instanceof DataObject)) {
            return -1;
        }
        return ids.lastIndexOf(((DataObject)o).getId());
    }
    
    @Override
    public Iterator<T> iterator() {
        return queryIterativeObjects();
    }
    
    @Override
    @Deprecated
    public ListIterator<T> listIterator() {
        throw new UnsupportedOperationException();
    }

    @Override
    @Deprecated
    public ListIterator<T> listIterator(int index) {
        throw new UnsupportedOperationException();
    }
    
    @Override
    @Deprecated
    public boolean addAll(int index, Collection<? extends T> c) {
        throw new UnsupportedOperationException();
    }
    
    private synchronized void iterateAll() {
        Iterator<T> iterator = queryIterativeObjects();
        List<URI> existIds = new ArrayList<URI>();
        while (iterator.hasNext()) {
            existIds.add(iterator.next().getId());
        }
        this.ids = existIds;
        this.initialized.getAndSet(true);
        
    }
    
    protected abstract List<T> queryObjects(List<URI> ids);
    protected abstract Iterator<T> queryIterativeObjects();
    protected abstract T queryObject(URI id);

    protected void checkRange(int index) {
        if (index < 0 || index >= this.ids.size()) {
            throw new IndexOutOfBoundsException("Out of Bound exception: size=" + this.ids.size() + ", index=" + index);
        }
    }
    
    private void checkRangeForAdd(int index) {
        if (index < 0 || index > this.ids.size()) {
            throw new IndexOutOfBoundsException("Out of Bound exception: size=" + this.ids.size() + ", index=" + index); 
        }
    }

    private List<T> getAll() {
        List<T> objects = new ArrayList<T>() ;
        Iterator<T> iterator = queryIterativeObjects();
        while (iterator.hasNext()) {
            objects.add(iterator.next());
        }
        return objects;
    }
    
    private boolean remove(URI uri) {
        Iterator<URI> iterator = this.ids.iterator();
        while (iterator.hasNext()) {
            if (iterator.next().equals(uri)) {
                iterator.remove();
                return true;
            }
        }
        return false;
    }
}
