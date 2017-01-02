/*
 * Copyright (c) 2015 EMC Corporation
 * All Rights Reserved
 */
package util.builders;

import com.emc.storageos.model.vpool.FileVirtualPoolParam;
import com.emc.storageos.model.vpool.FileVirtualPoolProtectionParam;
import com.emc.storageos.model.vpool.FileVirtualPoolRestRep;
import com.emc.storageos.model.vpool.VirtualPoolProtectionSnapshotsParam;

public class FileVirtualPoolBuilder extends VirtualPoolBuilder {
    private FileVirtualPoolParam virtualPool;

    public FileVirtualPoolBuilder() {
        this(new FileVirtualPoolParam());
    }

    protected FileVirtualPoolBuilder(FileVirtualPoolParam virtualPool) {
        super(virtualPool);
        this.virtualPool = virtualPool;
    }

    @Override
    public FileVirtualPoolParam getVirtualPool() {
        return virtualPool;
    }

    protected FileVirtualPoolProtectionParam getProtection() {
        if (virtualPool.getProtection() == null) {
            virtualPool.setProtection(new FileVirtualPoolProtectionParam());
        }
        return virtualPool.getProtection();
    }

    public FileVirtualPoolBuilder setSnapshots(Integer maxSnapshots) {
        getProtection().setSnapshots(new VirtualPoolProtectionSnapshotsParam(maxSnapshots));
        return this;
    }

    public static FileVirtualPoolProtectionParam getProtection(FileVirtualPoolRestRep virtualPool) {
        return virtualPool != null ? virtualPool.getProtection() : null;
    }

    public static VirtualPoolProtectionSnapshotsParam getSnapshots(FileVirtualPoolRestRep virtualPool) {
        return getSnapshots(getProtection(virtualPool));
    }

    public static VirtualPoolProtectionSnapshotsParam getSnapshots(FileVirtualPoolProtectionParam protection) {
        return protection != null ? protection.getSnapshots() : null;
    }

    public FileVirtualPoolBuilder setLongTermRetention(Boolean longTermRetention) {
        virtualPool.setLongTermRetention(longTermRetention);
        return this;
    }

    public FileVirtualPoolBuilder setScheduleSnapshots(Boolean scheduleSnapshots) {
        getProtection().setScheduleSnapshots(scheduleSnapshots);
        return this;
    }

    public static Boolean getScheduleSnapshots(FileVirtualPoolProtectionParam protection) {
        return protection != null ? protection.getScheduleSnapshots() : null;
    }

    public FileVirtualPoolBuilder setReplicationSupported(Boolean replicationSupported) {
        getProtection().setReplicationSupported(replicationSupported);
        return this;
    }

    public static Boolean getReplicationSupported(FileVirtualPoolProtectionParam protection) {
        return protection != null ? protection.getReplicationSupported() : null;
    }

    public FileVirtualPoolBuilder setAllowPolicyAtProject(Boolean policyAtPorject) {
        getProtection().setAllowFilePolicyAtProjectLevel(policyAtPorject);
        return this;
    }

    public static Boolean getAllowFilePolicyAtProjectLevel(FileVirtualPoolProtectionParam protection) {
        return protection != null ? protection.getAllowFilePolicyAtProjectLevel() : null;
    }

    public FileVirtualPoolBuilder setAllowPolicyAtFS(Boolean policyAtFs) {
        getProtection().setAllowFilePolicyAtFSLevel(policyAtFs);
        return this;
    }

    public static Boolean getAllowPolicyAtFS(FileVirtualPoolProtectionParam protection) {
        return protection != null ? protection.getAllowFilePolicyAtFSLevel() : null;
    }

    public FileVirtualPoolBuilder setFileReplicationRPO(Long rpo) {
        getProtection().setMinRpoValue(rpo);
        return this;
    }

    public static Long getFileReplicationRPO(FileVirtualPoolProtectionParam protection) {
        return protection != null ? protection.getMinRpoValue() : null;
    }

    public FileVirtualPoolBuilder setFileReplicationRpoType(String rpoType) {
        getProtection().setMinRpoType(rpoType);
        return this;
    }

    public static String getFileReplicationRpoType(FileVirtualPoolProtectionParam protection) {
        return protection != null ? protection.getMinRpoType() : null;
    }
}
