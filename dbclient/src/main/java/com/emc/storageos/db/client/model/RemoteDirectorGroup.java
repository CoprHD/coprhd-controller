/*
 * Copyright (c) 2015 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.db.client.model;

import java.net.URI;
import java.util.StringTokenizer;

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

    /**
     * Given a single RDF Group object, create a single String with useful information.
     * A similar (but not identical) method is in RDFGroupRestRep.java.
     * 
     * @return String
     */
    public String forDisplay() {
        StringBuffer sb = new StringBuffer();
        final String token = "+";
        
        // Example:
        // VMAX 1612 -> 5321 : G#-199 : BillRAGroup [5 Vols, SYNC/ASYNC/ANYMODE, Status: UP]
        // 
        // Format of NativeGUID
        //     1           2          3            4        5       6        7
        // SYMMETRIX+000196701343+REMOTEGROUP+000196701343+190+000196701405+190
        //                                           [1343|190]       [1405]
        StringTokenizer st = new StringTokenizer(getNativeGuid(), token);
        sb.append("VMAX ");
        try {
            st.nextToken(); // 1
            st.nextToken(); // 2
            st.nextToken(); // 3

            String srcSerial = st.nextToken(); // 4
            sb.append(srcSerial.substring(Math.max(0, srcSerial.length() - 4))); // 4

            sb.append(" -> ");
            st.nextToken(); // 5
            
            String tgtSerial = st.nextToken(); // 6
            sb.append(tgtSerial.substring(Math.max(0, tgtSerial.length() - 4))); // 6
            
            sb.append(": G#-" + getSourceGroupId());
            sb.append(": " + getLabel());
            // Using pipes "|" instead of commas because the UI order page treats the commas as newlines
            sb.append(String.format(" [%d Vols | ", (getVolumes() != null) ? getVolumes().size() : 0));
            
            // "ALL" doesn't mean anything to the end user, change it to ANYMODE
            if (getSupportedCopyMode().equalsIgnoreCase("ALL")) {
                sb.append("ANYMODE");
            } else if (getSupportedCopyMode().equalsIgnoreCase("SYNCHRONOUS")) {
                sb.append("SYNC"); // Brief versions of the word, since space is at a premium
            } else if (getSupportedCopyMode().equalsIgnoreCase("ASYNCHRONOUS")) {
                sb.append("ASYNC");
            } else {
                sb.append(getSupportedCopyMode());
            }
            
            sb.append(" | Status: " + getConnectivityStatus() + "]");
            
        } catch (Exception e) {
            sb = new StringBuffer();
            sb.append(this.getLabel());
        }
        return sb.toString();
        
    }
}
