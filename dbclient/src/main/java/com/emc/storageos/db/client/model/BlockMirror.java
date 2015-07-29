/*
 * Copyright 2015 EMC Corporation
 * All Rights Reserved
 */
/*
 * Copyright (c) $today_year. EMC Corporation
 * All Rights Reserved
 *
 * This software contains the intellectual property of EMC Corporation
 * or is licensed to EMC Corporation from third parties.  Use of this
 * software and the intellectual property contained therein is expressly
 * limited to the terms and conditions of the License Agreement under which
 * it is provided by or on behalf of EMC.
 */
package com.emc.storageos.db.client.model;

@Cf("BlockMirror")
public class BlockMirror extends Volume {

    // Reference to the volume representing the SystemElement
    private NamedURI _source;
    // CIM reference to the CIM_StorageSynchronized instance
    private String _synchronizedInstance;
    // Synchronization state
    private String _syncState;
    // Synchronization type
    private String _syncType;

    public static String MIRROR_SYNC_TYPE = "6";

    public static enum SynchronizationState {
        UNKNOWN(0), RESYNCHRONIZING(5), SYNCHRONIZED(6), FRACTURED(13), COPYINPROGRESS(15);

        private int state;

        SynchronizationState(int state) {
            this.state = state;
        }

        public String toString() {
            return Integer.toString(state);
        }
    }

    @NamedRelationIndex(cf = "NamedRelation", type = Volume.class)
    @Name("source")
    public NamedURI getSource() {
        return _source;
    }

    public void setSource(NamedURI source) {
        _source = source;
        setChanged("source");
    }

    @Name("synchronizedInstance")
    public String getSynchronizedInstance() {
        return _synchronizedInstance;
    }

    public void setSynchronizedInstance(String synchronizedInstance) {
        _synchronizedInstance = synchronizedInstance;
        setChanged("synchronizedInstance");
    }

    @Name("syncState")
    public String getSyncState() {
        return _syncState;
    }

    public void setSyncState(String syncState) {
        _syncState = syncState;
        setChanged("syncState");
    }

    @Name("syncType")
    public String getSyncType() {
        return _syncType;
    }

    public void setSyncType(String syncType) {
        _syncType = syncType;
        setChanged("syncType");
    }
}
