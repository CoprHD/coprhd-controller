/*
 * Copyright (c) 2015 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.volumecontroller.impl.plugins.discovery.smis.processor.detailedDiscovery;

import java.net.URI;

import com.emc.storageos.db.client.model.StringSet;

public class RemoteMirrorObject {

    private String copyMode;

    private URI sourceRaGroupUri;

    private URI targetRaGroupUri;

    // list of target volume uris
    private StringSet targetVolumenativeGuids;

    // source Volume Native Guid
    private String sourceVolumeNativeGuid;

    private String type;

    public enum Types {
        SOURCE,
        TARGET
    }

    public String getCopyMode() {
        return copyMode;
    }

    public void setCopyMode(String copyMode) {
        this.copyMode = copyMode;
    }

    public URI getSourceRaGroupUri() {
        return sourceRaGroupUri;
    }

    public void setSourceRaGroupUri(URI sourceRaGroupUri) {
        this.sourceRaGroupUri = sourceRaGroupUri;
    }

    public URI getTargetRaGroupUri() {
        return targetRaGroupUri;
    }

    public void setTargetRaGroupUri(URI targetRaGroupUri) {
        this.targetRaGroupUri = targetRaGroupUri;
    }

    public StringSet getTargetVolumenativeGuids() {
        return targetVolumenativeGuids;
    }

    public void setTargetVolumenativeGuids(StringSet targetVolumenativeGuids) {
        this.targetVolumenativeGuids = targetVolumenativeGuids;
    }

    public String getSourceVolumeNativeGuid() {
        return sourceVolumeNativeGuid;
    }

    public void setSourceVolumeNativeGuid(String sourceVolumeNativeGuid) {
        this.sourceVolumeNativeGuid = sourceVolumeNativeGuid;
    }

    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }

    @Override
    public String toString() {
        StringBuffer buffer = new StringBuffer();
        buffer.append("Source Remote Group :");
        buffer.append(sourceRaGroupUri);
        buffer.append(";Target Remote Group :");
        buffer.append(targetRaGroupUri);
        buffer.append(";Type :");
        buffer.append(type);
        buffer.append(";Mode :");
        buffer.append(copyMode);
        return buffer.toString();
    }

}
