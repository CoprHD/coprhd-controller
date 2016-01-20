/*
 * Copyright (c) 2012 EMC Corporation
 * All Rights Reserved
 */

package com.emc.storageos.db.client.model;

import java.util.*;

/**
 * Abstract base for map that tracks changes
 */
public abstract class AbstractChangeTrackingMap<K> extends HashMap<String, K> {
    private HashMap<String, K> _remove;
    private HashSet<String> _changed; // added or modified

    /**
     * Default constructor
     */
    public AbstractChangeTrackingMap() {
    }

    /**
     * Constructs a new map with the same mappings as source
     * 
     * @param source
     */
    public AbstractChangeTrackingMap(Map<String, K> source) {
        super(source);
    }

    /**
     * Get removed key set
     * 
     * @return removed keys
     */
    public Set<String> getRemovedKeySet() {
        if (_remove == null) {
            return null;
        }
        return Collections.unmodifiableSet(_remove.keySet());
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
     * Marks all entries as added new, for a complete db field value overwrite
     * used from inside migration engine, not recommended for use otherwise
     */
    public void markAllForOverwrite() {
        _changed = new HashSet<String>();
        _changed.addAll(this.keySet());
    }

    /**
     * Incrementally adds entry into the hash map
     * 
     * @param key
     * @param value
     * @return Previous value for the key, if one exists
     */
    @Override
    public K put(String key, K value) {
        if (_changed == null) {
            _changed = new HashSet<String>();
        }
        if (_remove != null) {
            _remove.remove(key);
        }
        _changed.add(key);
        return super.put(key, value);
    }

    /**
     * Adds entry into hashmap, not tracked as a change
     * 
     * @param key
     * @param value
     */
    public void putNoTrack(String key, byte[] value) {
        super.put(key, valFromByte(value));
    }

    /**
     * Removes entry from map without tracking
     * 
     * @param key
     */
    public void removeNoTrack(String key) {
        super.remove(key);
    }

    /**
     * Incrementally add multiple key, value pairs into the Map
     * 
     * @param add HashMap to add key, value pairs from
     */
    @Override
    public void putAll(Map<? extends String, ? extends K> add) {
        for (Map.Entry<? extends String, ? extends K> entry : add.entrySet()) {
            put(entry.getKey(), entry.getValue());
        }
    }

    /**
     * Incrementally remove a key from the map
     * 
     * @param key
     */
    public void remove(String key) {
        // check if need to remove from changed
        if (_changed != null && _changed.contains(key)) {
            _changed.remove(key);
        }

        K old = super.remove(key);
        if (_remove == null) {
            _remove = new HashMap<String, K>();
        }
        _remove.put(key, old);
    }

    public K getRemovedValue(String key) {
        return _remove.get(key);
    }

    /**
     * Removes all entries in the map.
     */
    @Override
    public void clear() {
        Set<String> keys = super.keySet();
        if (keys != null && _changed != null) {
            _changed.removeAll(keys);
        }

        if (_remove == null) {
            _remove = new HashMap<String, K>();
        }

        _remove.putAll(this);

        super.clear();
    }

    /**
     * replace current entries with the ones passed in
     * 
     * @param newEntries
     */
    public void replace(Map<String, K> newEntries) {
        if ((newEntries == null) || (newEntries.isEmpty())) {
            clear();
            return;
        }

        Set<String> keys = super.keySet();
        List<String> removedKeys = new ArrayList();
        for (String key : keys) {
            if (!newEntries.containsKey(key)) {
                removedKeys.add(key);
            }
        }

        for (String key : removedKeys) {
            remove(key);
        }

        Set<Map.Entry<String, K>> entries = newEntries.entrySet();
        for (Map.Entry<String, K> entry : entries) {
            put(entry.getKey(), entry.getValue());
        }
    }

    /**
     * Deserializes string value into K
     * 
     * @param value
     * @return
     */
    public abstract K valFromByte(byte[] value);

    /**
     * Serializes K into string
     * 
     * @param value
     * @return
     */
    public abstract byte[] valToByte(K value);
}
