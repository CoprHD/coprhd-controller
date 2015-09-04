/*
 * Copyright 2015 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.db.client.model.UnManagedDiscoveredObjects;

import java.net.URI;
import java.util.Set;

import com.emc.storageos.db.client.model.AlternateId;
import com.emc.storageos.db.client.model.Cf;
import com.emc.storageos.db.client.model.ExportRule;
import com.emc.storageos.db.client.model.FileShare;
import com.emc.storageos.db.client.model.Name;
import com.emc.storageos.db.client.model.RelationIndex;

@Cf("UnManagedFileExportRule")
public class UnManagedFileExportRule extends ExportRule {

    private String nativeGuid;

    // Export Path of an export.
    protected URI fileSystemId;

    protected String opType;

    @Name("opType")
    public String getOpType() {
        return opType;
    }

    public void setOpType(String opType) {
        this.opType = opType;
        setChanged("opType");
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
        if (getFileSystemId() != null && getExportPath() != null && getSecFlavor() != null) {
            this.setFsExportIndex(getFileSystemId().toString() + getExportPath()
                    + getSecFlavor());
        }
    }

    private String getHostsPrintLog(Set<String> hosts) {
        StringBuilder sb = new StringBuilder();
        if (hosts != null && hosts.size() > 0) {
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
        sb.append("[InActive : ")
                .append((getInactive() != null) ? getInactive() : null)
                .append("] ");
        return sb.toString();
    }

    @AlternateId("AltIdIndex")
    @Name("nativeGuid")
    public String getNativeGuid() {
        return nativeGuid;
    }

    public void setNativeGuid(String nativeGuid) {
        this.nativeGuid = nativeGuid;
        setChanged("nativeGuid");
    }

}
