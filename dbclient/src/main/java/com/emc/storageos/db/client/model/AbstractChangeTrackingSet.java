/*
 * Copyright (c) 2012 EMC Corporation
 * All Rights Reserved
 */

package com.emc.storageos.db.client.model;

import java.util.*;

import com.emc.storageos.db.client.util.DbClientCallbackEvent;

/**
 * Abstract base for set that tracks changes
 */
public abstract class AbstractChangeTrackingSet<K> extends HashSet<K> {
    private HashSet<K> _remove;
    private HashSet<K> _added;
    DbClientCallbackEvent _cb;

    /**
     * Default constructor
     */
    public AbstractChangeTrackingSet() {
    }

    /**
     * Constructs a new set with the same set as source
     * 
     * @param source
     */
    public AbstractChangeTrackingSet(Collection<K> source) {
        super(source);
    }

    public void setCallback(DbClientCallbackEvent cb) {
        _cb = cb;
    }

    public DbClientCallbackEvent getCallback() {
        return _cb;
    }

    /**
     * Get removed value set
     * 
     * @return
     */
    public Set<K> getRemovedSet() {
        if (_remove == null) {
            return null;
        }
        return Collections.unmodifiableSet(_remove);
    }

    /**
     * Get modified value set
     * 
     * @return
     */
    public Set<K> getAddedSet() {
        if (_added == null) {
            return null;
        }
        return Collections.unmodifiableSet(_added);
    }

    /**
     * Marks all entries as added new, for a complete db field value overwrite used from inside migration engine, not
     * recommended for use otherwise
     */
    public void markAllForOverwrite() {
        _added = new HashSet<K>();
        _added.addAll(this);
    }

    /**
     * To incrementally add value to set, call this and follow up with DbClient.persist
     * 
     * @param val
     *            String to be added, if doesn't already exists
     * @return true if value is not already there, hence added now
     */
    @Override
    public boolean add(K val) {
        if (contains(val)) {
            return false;
        }
        if (_added == null) {
            _added = new HashSet<K>();
        }
        _added.add(val);
        if (_remove != null) {
            _remove.remove(val);
        }
        super.add(val);

        if (_cb != null) {
            _cb.call();
        }

        return true;
    }

    /**
     * Adds entry into set, not tracked as a change
     * 
     * @param val
     *            value to add into the set
     */
    public void addNoTrack(String val) {
        super.add(valFromString(val));
    }

    /**
     * Removes entry from set without tracking
     * 
     * @param val
     */
    public void removeNoTrack(String val) {
        super.remove(valFromString(val));
    }

    /**
     * Add set of values, calls add for each element in the input set
     * 
     * @param add
     *            Set of strings to be added to the Set
     */
    public void addAll(HashSet<K> add) {
        Iterator<K> it = add.iterator();
        while (it.hasNext()) {
            add(it.next());
        }

        if (_cb != null) {
            _cb.call();
        }
    }

    /**
     * To incrementally remove a value from set, call this and follow up with DbClient.persist
     * 
     * @param val
     *            String to be removed, if exists
     */
    @Override
    @SuppressWarnings("unchecked")
    public boolean remove(Object val) {
        // check if removing from newly added
        if (_added != null) {
            _added.remove(val);
        }
        boolean ret = super.remove(val);
        if (_remove == null) {
            _remove = new HashSet<K>();
        }
        _remove.add((K) val);

        if (_cb != null) {
            _cb.call();
        }

        return ret;
    }

    /**
     * Remove set of values, calls remove for each element in the input set.
     * 
     * @param remove
     *            Set of strings to be removed from the Set
     * 
     * @return true if an the set was changed, false otherwise.
     */
    public boolean removeAll(HashSet<K> remove) {
        boolean ret = false;
        Iterator<K> it = remove.iterator();
        while (it.hasNext()) {
            boolean removed = remove(it.next());
            if (!ret) {
                ret = removed;
            }
        }

        if (_cb != null) {
            _cb.call();
        }

        return ret;
    }

    /**
     * Removes all entries in the set.
     */
    @Override
    public void clear() {
        if (_added != null) {
            _added.removeAll(this);
        }

        if (_remove == null) {
            _remove = new HashSet<K>();
        }
        _remove.addAll(this);

        super.clear();

        if (_cb != null) {
            _cb.call();
        }
    }

    /**
     * replace current entries with the ones passed in
     * 
     * @param newEntries
     */
    public void replace(Set<K> newEntries) {
        if (newEntries == null || (newEntries.isEmpty())) {
            clear();
            return;
        }

        HashSet<K> toRemove = new HashSet();
        for (K entry : this) {
            if (!newEntries.contains(entry)) {
                toRemove.add(entry);
            }
        }

        removeAll(toRemove);

        for (K entry : newEntries) {
            add(entry);
        }

        if (_cb != null) {
            _cb.call();
        }
    }

    /**
     * Deserializes string value into K
     * 
     * @param value
     * @return
     */
    public abstract K valFromString(String value);

    /**
     * Serializes K into string
     * 
     * @param value
     * @return
     */
    public abstract String valToString(K value);
}
