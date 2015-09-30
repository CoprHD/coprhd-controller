/*
 * Copyright (c) 2014 EMC Corporation
 * All Rights Reserved
 */

package com.emc.storageos.db.client.impl;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.ListIterator;

import com.emc.storageos.db.client.model.DataObject;
import com.emc.storageos.db.client.model.StringSet;

/**
 * @author cgarber
 * 
 */
public class LazyLoadedList<E extends DataObject> extends LazyLoadedCollection<E> implements List<E> {

    /**
     * @param name
     * @param mappedBy
     * @param id
     * @param lazyLoader2
     */
    public LazyLoadedList(String name, E parentObj, LazyLoader lazyLoader, StringSet mappedBy) {
        super(name, parentObj, lazyLoader, mappedBy);
        list = new ArrayList<E>();
    }

    /*
     * (non-Javadoc)
     * 
     * @see com.emc.storageos.db.client.constraint.LazyLoadedCollection#getNewCollection()
     */
    @Override
    protected Collection<E> getNewCollection() {
        return new ArrayList<E>();
    }

    /*
     * (non-Javadoc)
     * 
     * @see com.emc.storageos.db.client.constraint.LazyLoadedCollection#getCollection()
     */
    @Override
    protected List<E> getCollection() {
        return (List<E>) super.getCollection();
    }

    /*
     * (non-Javadoc)
     * 
     * @see java.util.List#addAll(int, java.util.Collection)
     */
    @Override
    public boolean addAll(int index, Collection<? extends E> c) {
        return getCollection().addAll(index, c);
    }

    /*
     * (non-Javadoc)
     * 
     * @see java.util.List#get(int)
     */
    @Override
    public E get(int index) {
        return getCollection().get(index);
    }

    /*
     * (non-Javadoc)
     * 
     * @see java.util.List#set(int, java.lang.Object)
     */
    @Override
    public E set(int index, E element) {
        return getCollection().set(index, element);
    }

    /*
     * (non-Javadoc)
     * 
     * @see java.util.List#add(int, java.lang.Object)
     */
    @Override
    public void add(int index, E element) {
        getCollection().add(element);
    }

    /*
     * (non-Javadoc)
     * 
     * @see java.util.List#remove(int)
     */
    @Override
    public E remove(int index) {
        return getCollection().remove(index);
    }

    /*
     * (non-Javadoc)
     * 
     * @see java.util.List#indexOf(java.lang.Object)
     */
    @Override
    public int indexOf(Object o) {
        return getCollection().indexOf(o);
    }

    /*
     * (non-Javadoc)
     * 
     * @see java.util.List#lastIndexOf(java.lang.Object)
     */
    @Override
    public int lastIndexOf(Object o) {
        return getCollection().lastIndexOf(o);
    }

    /*
     * (non-Javadoc)
     * 
     * @see java.util.List#listIterator()
     */
    @Override
    public ListIterator<E> listIterator() {
        return getCollection().listIterator();
    }

    /*
     * (non-Javadoc)
     * 
     * @see java.util.List#listIterator(int)
     */
    @Override
    public ListIterator<E> listIterator(int index) {
        return getCollection().listIterator(index);
    }

    /*
     * (non-Javadoc)
     * 
     * @see java.util.List#subList(int, int)
     */
    @Override
    public List<E> subList(int fromIndex, int toIndex) {
        return getCollection().subList(fromIndex, toIndex);
    }

}
