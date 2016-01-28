/*
 * Copyright (c) 2015 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.db.client.model;

import java.net.URI;

import com.emc.storageos.model.valid.EnumType;
import com.google.common.base.Objects;

@Cf("RemoteDirectorGroup")
public class RemoteDirectorGroup extends DiscoveredDataObject {
    private String sourceGroupId;

    private String remoteGroupId;

    private URI sourcePort;

    private URI remotePort;

    private StringSet volumes;

    private URI sourceStorageSystemUri;

    private URI remoteStorageSystemUri;

    private Boolean active;

    private String supportedCopyMode;

    private String connectivityStatus;

    private String copyState;

    private Boolean supported = true;

    private String sourceReplicationGroupName;

    private String targetReplicationGroupName;

    public enum CopyStates {
        CONSISTENT,
        IN_CONSISTENT
    }

    public enum ConnectivityStatus {
        UP("2"),
        DOWN("3"),
        PARTITIONED("4"),
        UNKNOWN("0");

        private String statusIdentifier;

        private static final ConnectivityStatus[] copyValues = values();

        ConnectivityStatus(String identifier) {
            statusIdentifier = identifier;
        }

        public String getIdentifier() {
            return statusIdentifier;
        }

        public static String getConnectivityStatus(String identifier) {
            for (ConnectivityStatus status : copyValues) {
                if (status.getIdentifier().equalsIgnoreCase(identifier)) {
                    return status.name();
                }
            }
            return null;
        }
    }

    public enum SupportedCopyModes {
        SYNCHRONOUS("2"),
        ASYNCHRONOUS("3"),
        UNKNOWN("0"),
        ADAPTIVECOPY("32768"),
        ACTIVE("32770"),
        ALL("-1,32769");

        private String modeIdentifier;

        private static final SupportedCopyModes[] copyModes = values();

        private SupportedCopyModes(String identifier) {
            modeIdentifier = identifier;
        }

        public String getIdentifier() {
            return modeIdentifier;
        }

        public static String getCopyMode(String identifier) {
            for (SupportedCopyModes mode : copyModes) {
                if (mode.getIdentifier().contains(identifier)) {
                    return mode.name();
                }
            }
            return UNKNOWN.name();
        }
    }

    @Name("sourceGroupId")
    public String getSourceGroupId() {
        return sourceGroupId;
    }

    public void setSourceGroupId(String sourceGroupId) {
        this.sourceGroupId = sourceGroupId;
        setChanged("sourceGroupId");
    }

    @Name("remoteGroupId")
    public String getRemoteGroupId() {
        return remoteGroupId;
    }

    public void setRemoteGroupId(String remoteGroupId) {
        this.remoteGroupId = remoteGroupId;
        setChanged("remoteGroupId");
    }

    @Name("remotePortUri")
    public URI getRemotePort() {
        return remotePort;
    }

    public void setRemotePort(URI remotePort) {
        this.remotePort = remotePort;
        setChanged("remotePortUri");
    }

    @Name("sourcePortUri")
    public URI getSourcePort() {
        return sourcePort;
    }

    public void setSourcePort(URI sourcePort) {
        this.sourcePort = sourcePort;
        setChanged("sourcePortUri");
    }

    @Name("volumes")
    public StringSet getVolumes() {
        return volumes;
    }

    public void setVolumes(StringSet volumes) {
        this.volumes = volumes;

    }

    @RelationIndex(cf = "RelationIndex", type = StorageSystem.class)
    @Name("sourceStorageSystem")
    public URI getSourceStorageSystemUri() {
        return sourceStorageSystemUri;
    }

    public void setSourceStorageSystemUri(URI sourceStorageSystemUri) {
        this.sourceStorageSystemUri = sourceStorageSystemUri;
        setChanged("sourceStorageSystem");
    }

    @Name("remoteStorageSystem")
    public URI getRemoteStorageSystemUri() {
        return remoteStorageSystemUri;
    }

    public void setRemoteStorageSystemUri(URI remoteStorageSystemUri) {
        this.remoteStorageSystemUri = remoteStorageSystemUri;
        setChanged("remoteStorageSystem");
    }

    @Name("active")
    public Boolean getActive() {
        return active;
    }

    public void setActive(Boolean active) {
        this.active = active;
        setChanged("active");
    }

    @EnumType(ConnectivityStatus.class)
    @Name("connectivityStatus")
    public String getConnectivityStatus() {
        return connectivityStatus;
    }

    public void setConnectivityStatus(String connectivityStatus) {
        this.connectivityStatus = connectivityStatus;
        setChanged("connectivityStatus");
    }

    @EnumType(SupportedCopyModes.class)
    @Name("supportedCopyMode")
    public String getSupportedCopyMode() {
        return supportedCopyMode;
    }

    public void setSupportedCopyMode(String supportedCopyMode) {
        this.supportedCopyMode = supportedCopyMode;
        setChanged("supportedCopyMode");
    }

    @Name("copyState")
    public String getCopyState() {
        return copyState;
    }

    public void setCopyState(String copyState) {
        this.copyState = copyState;
        setChanged("copyState");
    }

    @Override
    public String toString() {
        return Objects.toStringHelper(this)
                .add("sourceGroupId", sourceGroupId)
                .add("remoteGroupId", remoteGroupId)
                .add("sourcePort", sourcePort)
                .add("remotePort", remotePort)
                .add("volumes", volumes)
                .add("sourceStorageSystemUri", sourceStorageSystemUri)
                .add("remoteStorageSystemUri", remoteStorageSystemUri)
                .add("active", active)
                .add("supportedCopyMode", supportedCopyMode)
                .add("connectivityStatus", connectivityStatus)
                .add("isSupported", supported)
                .toString();
    }

    @Name("isSupported")
    public Boolean getSupported() {
        return supported;
    }

    public void setSupported(Boolean isSupported) {
        this.supported = isSupported;
        setChanged("isSupported");
    }

    @Name("sourceGroup")
    public String getSourceReplicationGroupName() {
        return sourceReplicationGroupName;
    }

    public void setSourceReplicationGroupName(String sourceReplicationGroupName) {
        this.sourceReplicationGroupName = sourceReplicationGroupName;
        setChanged("sourceGroup");
    }

    @Name("targetGroup")
    public String getTargetReplicationGroupName() {
        return targetReplicationGroupName;
    }

    public void setTargetReplicationGroupName(String targetReplicationGroupName) {
        this.targetReplicationGroupName = targetReplicationGroupName;
        setChanged("targetGroup");
    }
}
