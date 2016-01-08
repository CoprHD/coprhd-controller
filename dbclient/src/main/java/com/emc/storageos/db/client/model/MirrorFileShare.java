package com.emc.storageos.db.client.model;

/**
 * Created by bonduj on 1/7/2016.
 */
public class MirrorFileShare extends FileShare {
    // Reference to the FileShare representing the SystemElement
    private NamedURI _source;

    // Synchronization state (UNKNOWN, SYNCHRONIZED, CREATED, RESYNCED, INACTIVE, DETACHED, RESTORED)
    private String _syncState;

    // Synchronization type (MIRROR_SYNC_TYPE etc..)
    private String _syncType;

    public static String MIRROR_SYNC_TYPE = "6";

    public NamedURI getSource() {
        return _source;
    }

    public void setSource(NamedURI _source) {
        this._source = _source;
    }


    public String getSyncState() {
        return _syncState;
    }

    public void setSyncState(String _syncState) {
        this._syncState = _syncState;
    }


    public String getSyncType() {
        return _syncType;
    }

    public void setSyncType(String _syncType) {
        this._syncType = _syncType;
    }

}
