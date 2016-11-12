package com.emc.storageos.db.server.impl;

import com.google.common.base.Objects;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/**
 * Created by brian on 7/4/16.
 */
public class MyRowKeys implements Iterable<MyRowKey> {
    private final List<MyRowKey> rowKeys;

    /**
     * Default empty constructor.
     */
    public MyRowKeys() {
        this(new ArrayList<MyRowKey>());
    }

    public MyRowKeys(List<MyRowKey> rowKeys) {
        this.rowKeys = rowKeys;
    }

    /** {@inheritDoc} */
    public Iterator<MyRowKey> iterator() {
        return rowKeys.iterator();
    }

    public int size() {
        return rowKeys.size();
    }

    public void add(MyRowKey rowKey) {
        rowKeys.add(rowKey);
    }

    /** {@inheritDoc} */
    @Override
    public String toString() {
        return Objects.toStringHelper(this).add("rowKeys", rowKeys).toString();
    }
}
