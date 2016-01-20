/*
 * Copyright (c) 2015 EMC Corporation
 * All Rights Reserved
 */
package util.builders;

import java.net.URI;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Set;

import models.DriveTypes;
import models.HighAvailability;

import org.apache.commons.lang.StringUtils;

import com.emc.storageos.model.vpool.BlockVirtualPoolParam;
import com.emc.storageos.model.vpool.BlockVirtualPoolProtectionParam;
import com.emc.storageos.model.vpool.BlockVirtualPoolRestRep;
import com.emc.storageos.model.vpool.ProtectionCopyPolicy;
import com.emc.storageos.model.vpool.ProtectionSourcePolicy;
import com.emc.storageos.model.vpool.VirtualPoolHighAvailabilityParam;
import com.emc.storageos.model.vpool.VirtualPoolHighAvailabilityParam.VirtualArrayVirtualPoolMapEntry;
import com.emc.storageos.model.vpool.VirtualPoolProtectionMirrorParam;
import com.emc.storageos.model.vpool.VirtualPoolProtectionRPParam;
import com.emc.storageos.model.vpool.VirtualPoolProtectionSnapshotsParam;
import com.emc.storageos.model.vpool.VirtualPoolProtectionVirtualArraySettingsParam;
import com.emc.storageos.model.vpool.VirtualPoolRemoteMirrorProtectionParam;
import com.emc.storageos.model.vpool.VirtualPoolRemoteProtectionVirtualArraySettingsParam;
import com.google.common.collect.Sets;

public class BlockVirtualPoolBuilder extends VirtualPoolBuilder {
    private final BlockVirtualPoolParam virtualPool;

    public BlockVirtualPoolBuilder() {
        this(new BlockVirtualPoolParam());
    }

    public BlockVirtualPoolBuilder(BlockVirtualPoolParam virtualPool) {
        super(virtualPool);
        this.virtualPool = virtualPool;
    }

    @Override
    public BlockVirtualPoolParam getVirtualPool() {
        return virtualPool;
    }

    public BlockVirtualPoolBuilder setHostIOLimitBandwidth(int limit) {
        virtualPool.setHostIOLimitBandwidth(limit);
        return this;
    }

    public BlockVirtualPoolBuilder setHostIOLimitIOPs(int limit) {
        virtualPool.setHostIOLimitIOPs(limit);
        return this;
    }

    public BlockVirtualPoolBuilder setMinPaths(int paths) {
        virtualPool.setMinPaths(paths);
        return this;
    }

    public BlockVirtualPoolBuilder setMaxPaths(int paths) {
        virtualPool.setMaxPaths(paths);
        return this;
    }

    public BlockVirtualPoolBuilder setPathsPerInitiator(int paths) {
        virtualPool.setPathsPerInitiator(paths);
        return this;
    }

    public BlockVirtualPoolBuilder setAutoTieringPolicyName(String autoTieringPolicyName) {
        virtualPool.setAutoTieringPolicyName(StringUtils.trimToNull(autoTieringPolicyName));
        return this;
    }

    public BlockVirtualPoolBuilder setDriveType(String driveType) {
        virtualPool.setDriveType(StringUtils.defaultIfEmpty(driveType, DriveTypes.NONE));
        return this;
    }

    public BlockVirtualPoolBuilder setExpandable(boolean expandable) {
        virtualPool.setExpandable(expandable);
        return this;
    }

    public BlockVirtualPoolBuilder setFastExpansion(boolean fastExpansion) {
        virtualPool.setFastExpansion(fastExpansion);
        return this;
    }

    public BlockVirtualPoolBuilder setThinVolumePreAllocationPercentage(Integer percent) {
        virtualPool.setThinVolumePreAllocationPercentage(percent);
        return this;
    }

    public BlockVirtualPoolBuilder setUniquePolicyNames(boolean uniquePolicyNames) {
        virtualPool.setUniquePolicyNames(uniquePolicyNames);
        return this;
    }

    public BlockVirtualPoolBuilder setMultiVolumeConsistency(boolean multiVolumeConsistency) {
        virtualPool.setMultiVolumeConsistency(multiVolumeConsistency);
        return this;
    }

    public BlockVirtualPoolBuilder setRaidLevels(Collection<String> raidLevels) {
        if (raidLevels != null) {
            virtualPool.setRaidLevels(Sets.newHashSet(raidLevels));
        }
        else {
            virtualPool.setRaidLevels(Sets.<String> newHashSet());
        }
        return this;
    }

    protected BlockVirtualPoolProtectionParam getProtection() {
        if (virtualPool.getProtection() == null) {
            virtualPool.setProtection(new BlockVirtualPoolProtectionParam());
        }
        return virtualPool.getProtection();
    }

    public BlockVirtualPoolBuilder setSnapshots(Integer maxSnapshots) {
        getProtection().setSnapshots(new VirtualPoolProtectionSnapshotsParam(maxSnapshots));
        return this;
    }

    public BlockVirtualPoolBuilder setContinuousCopies(Integer maxCopies, URI virtualPoolId) {
        getProtection().setContinuousCopies(new VirtualPoolProtectionMirrorParam(maxCopies, virtualPoolId));
        return this;
    }

    protected VirtualPoolProtectionRPParam getRecoverPoint() {
        if (getProtection().getRecoverPoint() == null) {
            getProtection().setRecoverPoint(new VirtualPoolProtectionRPParam());
        }
        return getProtection().getRecoverPoint();
    }

    public BlockVirtualPoolBuilder disableRecoverPoint() {
        getProtection().setRecoverPoint(new VirtualPoolProtectionRPParam());
        return this;
    }

    public BlockVirtualPoolBuilder enableRecoverPoint(String journalSize) {
        return setRecoverPointJournalSize(journalSize);
    }

    public BlockVirtualPoolBuilder setRecoverPointJournalSize(String journalSize) {
        if (journalSize != null) {
            if (getProtectionSourcePolicy() == null) {
                getRecoverPoint().setSourcePolicy(new ProtectionSourcePolicy());
            }

            getProtectionSourcePolicy().setJournalSize(journalSize);
        }

        return this;
    }

    public BlockVirtualPoolBuilder setRecoverPointRemoteCopyMode(String remoteCopyMode) {
        if (remoteCopyMode != null) {
            if (getProtectionSourcePolicy() == null) {
                getRecoverPoint().setSourcePolicy(new ProtectionSourcePolicy());
            }

            getProtectionSourcePolicy().setRemoteCopyMode(remoteCopyMode);
        }

        return this;
    }

    public BlockVirtualPoolBuilder setRecoverPointRpo(Long value, String type) {
        if (value != null) {
            if (getProtectionSourcePolicy() == null) {
                getRecoverPoint().setSourcePolicy(new ProtectionSourcePolicy());
            }
            getProtectionSourcePolicy().setRpoValue(value);
            getProtectionSourcePolicy().setRpoType(type);
        }

        return this;
    }

    protected ProtectionSourcePolicy getProtectionSourcePolicy() {
        return getRecoverPoint().getSourcePolicy();
    }

    public BlockVirtualPoolBuilder setJournalVarrayAndVpool(URI journalVarray, URI journalVpool) {
        if (getProtectionSourcePolicy() == null) {
            getRecoverPoint().setSourcePolicy(new ProtectionSourcePolicy());
        }
        getProtectionSourcePolicy().setJournalVarray(journalVarray);
        getProtectionSourcePolicy().setJournalVpool(journalVpool);

        return this;
    }

    public BlockVirtualPoolBuilder setStandByJournalVArrayVpool(URI standbyJournalVarray, URI standbyJournalVpool) {
        if (getProtectionSourcePolicy() == null) {
            getRecoverPoint().setSourcePolicy(new ProtectionSourcePolicy());
        }
        getProtectionSourcePolicy().setStandbyJournalVarray(standbyJournalVarray);
        getProtectionSourcePolicy().setStandbyJournalVpool(standbyJournalVpool);

        return this;
    }

    public BlockVirtualPoolBuilder setRecoverPointCopies(
            Collection<VirtualPoolProtectionVirtualArraySettingsParam> recoverPointCopies) {
        getRecoverPoint().setCopies(Sets.newHashSet(recoverPointCopies));
        return this;
    }

    public BlockVirtualPoolBuilder addRecoverPointCopy(URI virtualArrayId, URI virtualPoolId, String journalSize) {
        getRecoverPoint().getCopies().add(createRecoverPointCopy(virtualArrayId, virtualPoolId, journalSize));
        return this;
    }

    public VirtualPoolProtectionVirtualArraySettingsParam createRecoverPointCopy(URI virtualArrayId, URI virtualPoolId,
            String journalSize) {
        VirtualPoolProtectionVirtualArraySettingsParam param = new VirtualPoolProtectionVirtualArraySettingsParam();
        param.setCopyPolicy(new ProtectionCopyPolicy(journalSize));
        param.setVarray(virtualArrayId);
        param.setVpool(virtualPoolId);
        return param;
    }

    public BlockVirtualPoolBuilder setHighAvailability(String type, Boolean enableCrossConnect, URI virtualArrayId,
            URI virtualPoolId, Boolean activeProtectionAtHASite, Boolean metroPoint) {
        VirtualPoolHighAvailabilityParam highAvailability = new VirtualPoolHighAvailabilityParam();
        if (HighAvailability.isHighAvailability(type)) {
            highAvailability.setType(type);
            highAvailability.setAutoCrossConnectExport(enableCrossConnect);
            if (HighAvailability.isVplexDistributed(type)) {
                VirtualArrayVirtualPoolMapEntry value = new VirtualArrayVirtualPoolMapEntry();
                value.setVirtualArray(virtualArrayId);
                value.setVirtualPool(virtualPoolId);
                value.setActiveProtectionAtHASite(activeProtectionAtHASite);
                highAvailability.setHaVirtualArrayVirtualPool(value);
                highAvailability.setMetroPoint(metroPoint);
            }
        }
        virtualPool.setHighAvailability(highAvailability);
        return this;
    }

    public BlockVirtualPoolBuilder disableHighAvailability() {
        virtualPool.setHighAvailability(null);
        return this;
    }

    public static BlockVirtualPoolProtectionParam getProtection(BlockVirtualPoolRestRep virtualPool) {
        return virtualPool != null ? virtualPool.getProtection() : null;
    }

    public static VirtualPoolProtectionSnapshotsParam getSnapshots(BlockVirtualPoolRestRep virtualPool) {
        return getSnapshots(getProtection(virtualPool));
    }

    public static VirtualPoolProtectionMirrorParam getContinuousCopies(BlockVirtualPoolRestRep virtualPool) {
        return getContinuousCopies(getProtection(virtualPool));
    }

    public static VirtualPoolProtectionRPParam getRecoverPoint(BlockVirtualPoolRestRep virtualPool) {
        return getRecoverPoint(getProtection(virtualPool));
    }

    public static VirtualPoolProtectionSnapshotsParam getSnapshots(BlockVirtualPoolProtectionParam protection) {
        return protection != null ? protection.getSnapshots() : null;
    }

    public static VirtualPoolProtectionMirrorParam getContinuousCopies(BlockVirtualPoolProtectionParam protection) {
        return protection != null ? protection.getContinuousCopies() : null;
    }

    public static VirtualPoolProtectionRPParam getRecoverPoint(BlockVirtualPoolProtectionParam protection) {
        return protection != null ? protection.getRecoverPoint() : null;
    }

    public static Set<VirtualPoolProtectionVirtualArraySettingsParam> getRecoverPointCopies(
            BlockVirtualPoolRestRep virtualPool) {
        return getRecoverPointCopies(getProtection(virtualPool));
    }

    public static Set<VirtualPoolProtectionVirtualArraySettingsParam> getRecoverPointCopies(
            BlockVirtualPoolProtectionParam protection) {
        return getRecoverPointCopies(getRecoverPoint(protection));
    }

    public static Set<VirtualPoolProtectionVirtualArraySettingsParam> getRecoverPointCopies(
            VirtualPoolProtectionRPParam recoverPoint) {
        if (recoverPoint != null) {
            return recoverPoint.getCopies();
        }
        return Collections.emptySet();
    }

    protected VirtualPoolRemoteMirrorProtectionParam getRemoteCopies() {
        if (getProtection().getRemoteCopies() == null) {
            getProtection().setRemoteCopies(new VirtualPoolRemoteMirrorProtectionParam());
        }

        return getProtection().getRemoteCopies();
    }

    public static VirtualPoolRemoteMirrorProtectionParam getRemoteCopies(BlockVirtualPoolProtectionParam protection) {
        return protection != null ? protection.getRemoteCopies() : null;
    }

    public BlockVirtualPoolBuilder setRemoteCopies(List<VirtualPoolRemoteProtectionVirtualArraySettingsParam> newValues) {
        getRemoteCopies().setRemoteCopySettings(newValues);
        return this;
    }

    public static List<VirtualPoolRemoteProtectionVirtualArraySettingsParam> getRemoteCopySettings(
            BlockVirtualPoolRestRep virtualPool) {
        return getRemoteCopySettings(getProtection(virtualPool));
    }

    public static List<VirtualPoolRemoteProtectionVirtualArraySettingsParam> getRemoteCopySettings(
            BlockVirtualPoolProtectionParam protection) {
        return getRemoteCopySettings(getRemoteCopies(protection));
    }

    public static List<VirtualPoolRemoteProtectionVirtualArraySettingsParam> getRemoteCopySettings(
            VirtualPoolRemoteMirrorProtectionParam remoteCopies) {
        if (remoteCopies != null) {
            return remoteCopies.getRemoteCopySettings();
        }
        return Collections.emptyList();
    }
}
