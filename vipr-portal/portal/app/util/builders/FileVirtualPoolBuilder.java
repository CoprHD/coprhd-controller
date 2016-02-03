/*
 * Copyright (c) 2015 EMC Corporation
 * All Rights Reserved
 */
package util.builders;

import com.emc.storageos.model.vpool.FileVirtualPoolParam;
import com.emc.storageos.model.vpool.FileVirtualPoolProtectionParam;
import com.emc.storageos.model.vpool.FileVirtualPoolReplicationParam;
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
    
    protected FileVirtualPoolReplicationParam getReplicationParam() {
        if (getProtection().getReplicationParam() == null) {
            getProtection().setReplicationParam(new FileVirtualPoolReplicationParam());

        }
        return virtualPool.getProtection().getReplicationParam();
    }

    public static FileVirtualPoolReplicationParam getReplicationParam(FileVirtualPoolRestRep virtualPool) {
        return virtualPool != null ? virtualPool.getProtection().getReplicationParam() : null;
    }

    public FileVirtualPoolBuilder setReplicationParam(FileVirtualPoolReplicationParam replicationParam) {
        getProtection().setReplicationParam(replicationParam);
        return this;
    }
    
    public FileVirtualPoolBuilder setScheduleSnapshots(Boolean scheduleSnapshots) {
        getProtection().setScheduleSnapshots(scheduleSnapshots);
        return this;
    }
    
    public static Boolean getScheduleSnapshots(FileVirtualPoolProtectionParam protection) {
        return protection != null ? protection.getScheduleSnapshots() : null;
    }
}
