/*
 * Copyright 2015 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.db.client.model;

import java.net.URI;
import java.util.Set;

@Cf("FileExportRule")
public class FileExportRule extends ExportRule {

    // Export Path of an export.
    protected URI fileSystemId;
    protected URI snapshotId;
    protected String opType;

    @RelationIndex(cf = "RelationIndex", type = Snapshot.class)
    @Name("snapshotId")
    public URI getSnapshotId() {
        return snapshotId;
    }

    public void setSnapshotId(URI snapshotId) {
        this.snapshotId = snapshotId;
        calculateExportRuleIndex();
        setChanged("snapshotId");
    }

    @RelationIndex(cf = "RelationIndex", type = FileShare.class)
    @Name("fileSystemId")
    public URI getFileSystemId() {
        return fileSystemId;
    }

    public void setFileSystemId(URI fileSystemId) {
        this.fileSystemId = fileSystemId;
        calculateExportRuleIndex();
        setChanged("fileSystemId");
    }

    @Override
    public void calculateExportRuleIndex() {

        if (getExportPath() != null && getSecFlavor() != null) {
            if (getFileSystemId() != null) {
                this.setFsExportIndex(getFileSystemId().toString() + getExportPath()
                        + getSecFlavor());
            }
            if (getSnapshotId() != null) {
                this.setSnapshotExportIndex(getSnapshotId().toString() + getExportPath()
                        + getSecFlavor());
            }
        }

    }

    @Name("opType")
    public String getOpType() {
        return opType;
    }

    public void setOpType(String opType) {
        this.opType = opType;
        setChanged("opType");
    }

    private String getHostsPrintLog(Set<String> hosts) {
        StringBuilder sb = new StringBuilder();
        if (hosts != null && !hosts.isEmpty()) {
            for (String endPoint : hosts) {
                sb.append("{").append(endPoint).append("}");
            }
        }
        return sb.toString();
    }

    @Override
    public String toString() {

        StringBuilder sb = new StringBuilder();
        sb.append("[secFlavor : ").append((secFlavor != null) ? secFlavor : "")
                .append("] ");
        sb.append("[exportPath : ").append((exportPath != null) ? exportPath : "")
                .append("] ");
        sb.append("[mountPoint : ").append((mountPoint != null) ? mountPoint : "")
                .append("] ");
        sb.append("[anon : ").append((anon != null) ? anon : "").append("] ");
        sb.append("[Number of readOnlyHosts : ")
                .append((readOnlyHosts != null) ? readOnlyHosts.size() : 0)
                .append("] ").append(getHostsPrintLog(readOnlyHosts));
        sb.append("[Number of readWriteHosts : ")
                .append((readWriteHosts != null) ? readWriteHosts.size() : 0)
                .append("] ").append(getHostsPrintLog(readWriteHosts));
        sb.append("[Number of rootHosts : ")
                .append((rootHosts != null) ? rootHosts.size() : 0)
                .append("] ").append(getHostsPrintLog(rootHosts));
        sb.append("[fileSystemId : ")
                .append((fileSystemId != null) ? fileSystemId : null)
                .append("] ");
        sb.append("[fsExportIndex : ")
                .append((fsExportIndex != null) ? fsExportIndex : null)
                .append("] ");
        sb.append("[SnapshotId : ")
                .append((snapshotId != null) ? snapshotId : null)
                .append("] ");
        sb.append("[snapshotExportIndex : ")
                .append((snapshotExportIndex != null) ? snapshotExportIndex : null)
                .append("] ");
        sb.append("[deviceExportId : ").append((deviceExportId != null) ? deviceExportId : "").append("] ");
        sb.append("[InActive : ")
                .append((getInactive() != null) ? getInactive() : null)
                .append("] ");
        return sb.toString();
    }
}
