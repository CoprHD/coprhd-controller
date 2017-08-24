/*
 * Copyright (c) 2015 EMC Corporation
 * All Rights Reserved
 */
package util.builders;

import org.apache.commons.lang.ObjectUtils;

import com.emc.storageos.model.vpool.FileVirtualPoolProtectionParam;
import com.emc.storageos.model.vpool.FileVirtualPoolProtectionUpdateParam;
import com.emc.storageos.model.vpool.FileVirtualPoolRestRep;
import com.emc.storageos.model.vpool.FileVirtualPoolUpdateParam;
import com.emc.storageos.model.vpool.VirtualPoolProtectionSnapshotsParam;

public class FileVirtualPoolUpdateBuilder extends VirtualPoolUpdateBuilder {
    private FileVirtualPoolRestRep oldVirtualPool;
    private FileVirtualPoolUpdateParam virtualPool;

    public FileVirtualPoolUpdateBuilder(FileVirtualPoolRestRep oldVirtualPool) {
        this(oldVirtualPool, new FileVirtualPoolUpdateParam());
    }

    protected FileVirtualPoolUpdateBuilder(FileVirtualPoolRestRep oldVirtualPool, FileVirtualPoolUpdateParam virtualPool) {
        super(oldVirtualPool, virtualPool);
        this.oldVirtualPool = oldVirtualPool;
        this.virtualPool = virtualPool;
    }

    @Override
    public FileVirtualPoolRestRep getOldVirtualPool() {
        return oldVirtualPool;
    }

    @Override
    public FileVirtualPoolUpdateParam getVirtualPoolUpdate() {
        return virtualPool;
    }

    protected FileVirtualPoolProtectionUpdateParam getProtection() {
        if (virtualPool.getProtection() == null) {
            virtualPool.setProtection(new FileVirtualPoolProtectionUpdateParam());
        }
        return virtualPool.getProtection();
    }

    private Integer getOldMaxSnapshots() {
        if ((oldVirtualPool.getProtection() != null) && (oldVirtualPool.getProtection().getSnapshots() != null)) {
            return oldVirtualPool.getProtection().getSnapshots().getMaxSnapshots();
        }
        return null;
    }

    private Boolean getOldScheduleSnapshots() {
        if ((oldVirtualPool.getProtection() != null) && (oldVirtualPool.getProtection().getScheduleSnapshots() != null)) {
            return oldVirtualPool.getProtection().getScheduleSnapshots();
        }
        return null;
    }

    private Boolean getOldReplicationSupported() {
        if ((oldVirtualPool.getProtection() != null) && (oldVirtualPool.getProtection().getReplicationSupported() != null)) {
            return oldVirtualPool.getProtection().getReplicationSupported();
        }
        return null;
    }

    private Boolean getOldAllowPolicyAtProject() {
        if ((oldVirtualPool.getProtection() != null) && (oldVirtualPool.getProtection().getAllowFilePolicyAtProjectLevel() != null)) {
            return oldVirtualPool.getProtection().getAllowFilePolicyAtProjectLevel();
        }
        return null;
    }

    private Boolean getOldAllowPolicyAtFS() {
        if ((oldVirtualPool.getProtection() != null) && (oldVirtualPool.getProtection().getAllowFilePolicyAtFSLevel() != null)) {
            return oldVirtualPool.getProtection().getAllowFilePolicyAtFSLevel();
        }
        return null;
    }

    private Long getOldRPOValue() {
        if ((oldVirtualPool.getProtection() != null) && (oldVirtualPool.getProtection().getMinRpoValue() != null)) {
            return oldVirtualPool.getProtection().getMinRpoValue();
        }
        return null;
    }

    private String getOldRPOType() {
        if ((oldVirtualPool.getProtection() != null) && (oldVirtualPool.getProtection().getMinRpoType() != null)) {
            return oldVirtualPool.getProtection().getMinRpoType();
        }
        return null;
    }

    public FileVirtualPoolUpdateBuilder setSnapshots(Integer maxSnapshots) {
        if (!ObjectUtils.equals(maxSnapshots, getOldMaxSnapshots())) {
            getProtection().setSnapshots(new VirtualPoolProtectionSnapshotsParam(maxSnapshots));
        }
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

    public FileVirtualPoolUpdateBuilder setLongTermRetention(Boolean longTermRetention) {
        if (!ObjectUtils.equals(longTermRetention, oldVirtualPool.getLongTermRetention())) {
            getVirtualPoolUpdate().setLongTermRetention(longTermRetention);
        }
        return this;
    }

    public FileVirtualPoolUpdateBuilder setScheduleSnapshots(Boolean scheduleSnapshots) {
        if (!ObjectUtils.equals(scheduleSnapshots, getOldScheduleSnapshots())) {
            getProtection().setScheduleSnapshots(scheduleSnapshots);
        }
        return this;
    }

    public static Boolean getScheduleSnapshots(FileVirtualPoolRestRep virtualPool) {
        return getScheduleSnapshots(getProtection(virtualPool));
    }

    public static Boolean getScheduleSnapshots(FileVirtualPoolProtectionParam protection) {
        return protection != null ? protection.getScheduleSnapshots() : null;
    }

    public FileVirtualPoolUpdateBuilder setReplicationSupported(Boolean replicationSupported) {
        if (!ObjectUtils.equals(replicationSupported, getOldReplicationSupported())) {
            getProtection().setReplicationSupported(replicationSupported);
        }
        return this;
    }

    public static Boolean getReplicationSupported(FileVirtualPoolProtectionParam protection) {
        return protection != null ? protection.getReplicationSupported() : null;
    }

    public static Boolean getReplicationSupported(FileVirtualPoolRestRep virtualPool) {
        return getReplicationSupported(getProtection(virtualPool));
    }

    public FileVirtualPoolUpdateBuilder setAllowPolicyAtProject(Boolean allowPolicyAtProject) {
        if (!ObjectUtils.equals(allowPolicyAtProject, getOldAllowPolicyAtProject())) {
            getProtection().setAllowFilePolicyAtProjectLevel(allowPolicyAtProject);
        }
        return this;
    }

    public static Boolean getAllowPolicyAtProject(FileVirtualPoolProtectionParam protection) {
        return protection != null ? protection.getAllowFilePolicyAtProjectLevel() : null;
    }

    public static Boolean getAllowPolicyAtProject(FileVirtualPoolRestRep virtualPool) {
        return getAllowPolicyAtProject(getProtection(virtualPool));
    }

    public FileVirtualPoolUpdateBuilder setAllowPolicyAtFS(Boolean allowPolicyAtFS) {
        if (!ObjectUtils.equals(allowPolicyAtFS, getOldAllowPolicyAtFS())) {
            getProtection().setAllowFilePolicyAtFSLevel(allowPolicyAtFS);
        }
        return this;
    }

    public static Boolean getAllowPolicyAtFS(FileVirtualPoolProtectionParam protection) {
        return protection != null ? protection.getAllowFilePolicyAtFSLevel() : null;
    }

    public static Boolean getAllowPolicyAtFS(FileVirtualPoolRestRep virtualPool) {
        return getAllowPolicyAtFS(getProtection(virtualPool));
    }

    public FileVirtualPoolUpdateBuilder setMinRPO(Long rpo) {
        if (!ObjectUtils.equals(rpo, getOldRPOValue())) {
            getProtection().setMinRpoValue(rpo);
        }
        return this;
    }

    public static Long getMinRPO(FileVirtualPoolProtectionParam protection) {
        return protection != null ? protection.getMinRpoValue() : null;
    }

    public static Long getMinRPO(FileVirtualPoolRestRep virtualPool) {
        return getMinRPO(getProtection(virtualPool));
    }

    public FileVirtualPoolUpdateBuilder setMinRPOType(String rpoType) {
        if (!ObjectUtils.equals(rpoType, getOldRPOType())) {
            getProtection().setMinRpoType(rpoType);
        }
        return this;
    }

    public static String getMinRPOType(FileVirtualPoolProtectionParam protection) {
        return protection != null ? protection.getMinRpoType() : null;
    }

    public static String getMinRPOType(FileVirtualPoolRestRep virtualPool) {
        return getMinRPOType(getProtection(virtualPool));
    }

}
