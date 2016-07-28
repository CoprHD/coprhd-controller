/*
 * Copyright (c) 2016 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.vnxe.models;

public class ConsistencyGroupCreateParam extends LunGroupCreateParam {
    private ReplicationParam replicationParameters;
    private DedupParam dedupParameters;

    public ReplicationParam getReplicationParameters() {
        return replicationParameters;
    }

    public void setReplicationParameters(ReplicationParam replicationParameters) {
        this.replicationParameters = replicationParameters;
    }

    public DedupParam getDedupParameters() {
        return dedupParameters;
    }

    public void setDedupParameters(DedupParam dedupParameters) {
        this.dedupParameters = dedupParameters;
    }

}
