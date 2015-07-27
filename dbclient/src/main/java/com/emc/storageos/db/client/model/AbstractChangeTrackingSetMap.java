/*
 * Copyright (c) 2012 EMC Corporation
 * All Rights Reserved
 */

package com.emc.storageos.db.client.model;

import java.util.*;


/**
 * Abstract base for map that tracks changes
 */
public abstract class AbstractChangeTrackingSetMap<K> extends HashMap<String, AbstractChangeTrackingSet<K>> {
    private HashSet<String> _changed; // added or modified

    /**
     * Default constructor
     */
    public AbstractChangeTrackingSetMap() {
    }

    /**
     * Constructs a new map with the same mappings as source
     *
     * @param source
     */
    public AbstractChangeTrackingSetMap(Map<String, AbstractChangeTrackingSet<K>> source) {
        super(source);
    }

    /**
     * Get modified keys set
     *
     * @return modified keys set
     */
    public Set<String> getChangedKeySet() {
        if (_changed == null) {
            return null;
        }
        return Collections.unmodifiableSet(_changed);
    }

    /**
     * Mark a row as changed
     * @param key
     */
    private void setValueChanged(String key) {
        if (_changed == null) {
            _changed = new HashSet<String>();
        }
        _changed.add(key);
    }

    /**
     * Get the Set value for a key, if key doesn't exist creates a new Set
     * @param key
     * @return
     */
    private AbstractChangeTrackingSet<K> getValue(String key){
        if (super.containsKey(key)){
            return super.get(key);
        } else {
            AbstractChangeTrackingSet<K> val = createSetInstance();
            super.put(key, val);
            setValueChanged(key);
            return val;
        }
    }

    /**
     * Get the Set value for a key, if key doesn't exist returns null
     * @param key
     * @return
     */
    protected AbstractChangeTrackingSet<K> getValueNoCreate(String key) {
        return (super.get(key));
    }

    @Override
    public AbstractChangeTrackingSet<K> put(String key, AbstractChangeTrackingSet<K> values) {
        getValue(key).addAll(values);
        return getValue(key);        
    }

    /**
     * Incrementally adds entry into the setmap
     * @param key
     * @param value
     */
    public void put(String key, K value) {
        getValue(key).add(value);
        setValueChanged(key);
    }
    
    /**
     * Incrementally removes an entry into the setmap
     * @param key
     * @param value
     */
    public void remove(String key, K value) {
        if (super.get(key) != null) {
            super.get(key).remove(value);
            setValueChanged(key);
        }
    }

    /**
     * Adds entry into hashmap, not tracked as a change
     * @param key
     * @param value
     */
    public void putNoTrack(String key, String value) {
        if (!super.containsKey(key)) {
            super.put(key, createSetInstance());
        }
        super.get(key).addNoTrack(value);
    }

    /**
     * Remove an entry without tracking
     *
     * @param key
     * @param value
     */
    public void removeNoTrack(String key, String value) {
        AbstractChangeTrackingSet<K> set = super.get(key);
        if (set != null) {
            set.removeNoTrack(value);
            if (set.isEmpty()) {
                super.remove(key);
            }
        }
    }

    /**
     * Remove a key from the map
     * @param key
     */
    public void remove(String key) {
        if (super.containsKey(key)) {
            AbstractChangeTrackingSet<K> trackingSet = super.get(key);
            trackingSet.clear();
            setValueChanged(key);
        }
    }

    /**
     * Removes all entries in the map.
     */
    @Override
    public void clear() {
        Iterator<String> keys = super.keySet().iterator();

        while(keys.hasNext()) {
            remove(keys.next());
        }
    }

    /**
     * replace current entries with the ones passed in
     * @param newEntries
     */
    public void replace(Map<String, AbstractChangeTrackingSet<K>> newEntries) {
        if ( newEntries == null || (newEntries.isEmpty() )) {
            clear();
            return;
        }

        Set<String> keys = super.keySet();
        List<String> removedKeys = new ArrayList();
        for (String key : keys) {
            if (!newEntries.containsKey(key))
                removedKeys.add(key);
        }

        for (String key: removedKeys)
            remove(key);

        Set<Map.Entry<String, AbstractChangeTrackingSet<K>>> entries = newEntries.entrySet();
        String key = null;
        for (Map.Entry<String, AbstractChangeTrackingSet<K>> entry : entries) {
            key = entry.getKey();
            getValue(key).replace(entry.getValue());
            setValueChanged(key);
        }
    }

    /**
     * Create an instance of the Set
     * @return
     */
    public abstract AbstractChangeTrackingSet<K> createSetInstance();

    /**
     * Returns the Set value for the key, null if the key doesn't exist
     * @param key
     * @return
     */
    public abstract AbstractChangeTrackingSet<K> get(String key);
}
