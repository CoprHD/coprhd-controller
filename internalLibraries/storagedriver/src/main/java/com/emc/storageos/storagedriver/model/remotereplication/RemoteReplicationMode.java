/*
 * Copyright (c) 2017 Dell EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.storagedriver.model.remotereplication;

/**
 * This class defines remote replication mode for remote replication elements: sets, groups, pairs.
 */
public class RemoteReplicationMode {

    private String replicationModeName;
    /**
     * Defines if group consistency for link operations is enforced automatically.
     */
    private boolean isGroupConsistencyEnforcedAutomatically = false;

    /**
     * Defines if group consistency for link operations cannot be supported.
     */
    private boolean isGroupConsistencyNotSupported = true;

    public RemoteReplicationMode(String replicationModeName, boolean isGroupConsistencyEnforcedAutomatically, boolean isGroupConsistencyNotSupported) {
        this.replicationModeName = replicationModeName;
        this.isGroupConsistencyEnforcedAutomatically = isGroupConsistencyEnforcedAutomatically;
        this.isGroupConsistencyNotSupported = isGroupConsistencyNotSupported;
    }

    public String getReplicationModeName() {
        return replicationModeName;
    }

    public void setReplicationModeName(String replicationModeName) {
        this.replicationModeName = replicationModeName;
    }

    public boolean isGroupConsistencyEnforcedAutomatically() {
        return isGroupConsistencyEnforcedAutomatically;
    }

    public void setIsGroupConsistencyEnforcedAutomatically(boolean isGroupConsistencyEnforcedAutomatically) {
        this.isGroupConsistencyEnforcedAutomatically = isGroupConsistencyEnforcedAutomatically;
    }

    public boolean isGroupConsistencyNotSupported() {
        return isGroupConsistencyNotSupported;
    }

    public void setIsGroupConsistencyNotSupported(boolean isGroupConsistencyNotSupported) {
        this.isGroupConsistencyNotSupported = isGroupConsistencyNotSupported;
    }

    @Override
    public String toString() {
        return replicationModeName;
    }
}
