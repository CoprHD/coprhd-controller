/*
 * Copyright (c) 2016 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.db.client.model;

import java.net.URI;

@Cf("FileReplicationTopology")
public class FileReplicationTopology extends DataObject {

    private URI sourceVArray;

    private StringSet targetVArrays;

    private String targetVAVPool;

    private Long numCopies = 1L;

    private URI policyId;

    @Name("sourceVArray")
    public URI getSourceVArray() {
        return sourceVArray;
    }

    public void setSourceVArray(URI srcVArray) {
        this.sourceVArray = srcVArray;
        setChanged("sourceVArray");
    }

    @Name("targetVArrays")
    public StringSet getTargetVArrays() {
        return targetVArrays;
    }

    public void setTargetVArrays(StringSet targetVArrays) {
        this.targetVArrays = targetVArrays;
        setChanged("targetVArrays");
    }

    public void addTargetVArrays(StringSet targetVArrays) {
        if (this.targetVArrays == null) {
            this.targetVArrays = new StringSet();
        }
        this.targetVArrays.addAll(targetVArrays);
        setChanged("targetVArrays");
    }

    @Name("targetVAVPool")
    public String getTargetVAVPool() {
        return targetVAVPool;
    }

    public void setTargetVAVPool(String targetVAVPool) {
        this.targetVAVPool = targetVAVPool;
        setChanged("targetVAVPool");
    }

    @Name("numCopies")
    public Long getNumCopies() {
        return numCopies;
    }

    public void setNumCopies(Long numCopies) {
        this.numCopies = numCopies;
        setChanged("numCopies");
    }

    @RelationIndex(cf = "RelationIndex", type = FilePolicy.class)
    @Name("policyId")
    public URI getPolicy() {
        return policyId;
    }

    public void setPolicy(URI policy) {
        this.policyId = policy;
        setChanged("policyId");
    }

}
