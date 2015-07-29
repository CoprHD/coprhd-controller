/*
 * Copyright 2015 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.db.common;

import java.util.Iterator;

public class IterativeList<T> implements Iterable<T> {

    private Iterator<T> _it;

    public IterativeList(Iterator<T> it) {
        _it = it;
    }

    @Override
    public Iterator<T> iterator() {
        return _it;
    }
}
