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

import org.apache.commons.collections.CollectionUtils;
import org.apache.commons.lang.ObjectUtils;
import org.apache.commons.lang.StringUtils;

import com.emc.storageos.model.vpool.BlockVirtualPoolProtectionParam;
import com.emc.storageos.model.vpool.BlockVirtualPoolProtectionUpdateParam;
import com.emc.storageos.model.vpool.BlockVirtualPoolRestRep;
import com.emc.storageos.model.vpool.BlockVirtualPoolUpdateParam;
import com.emc.storageos.model.vpool.ProtectionCopyPolicy;
import com.emc.storageos.model.vpool.ProtectionSourcePolicy;
import com.emc.storageos.model.vpool.RaidLevelAssignments;
import com.emc.storageos.model.vpool.RaidLevelChanges;
import com.emc.storageos.model.vpool.VirtualPoolHighAvailabilityParam;
import com.emc.storageos.model.vpool.VirtualPoolHighAvailabilityParam.VirtualArrayVirtualPoolMapEntry;
import com.emc.storageos.model.vpool.VirtualPoolProtectionMirrorParam;
import com.emc.storageos.model.vpool.VirtualPoolProtectionRPChanges;
import com.emc.storageos.model.vpool.VirtualPoolProtectionRPParam;
import com.emc.storageos.model.vpool.VirtualPoolProtectionSnapshotsParam;
import com.emc.storageos.model.vpool.VirtualPoolProtectionVirtualArraySettingsParam;
import com.emc.storageos.model.vpool.VirtualPoolRemoteMirrorProtectionParam;
import com.emc.storageos.model.vpool.VirtualPoolRemoteProtectionUpdateParam;
import com.emc.storageos.model.vpool.VirtualPoolRemoteProtectionVirtualArraySettingsParam;
import com.google.common.collect.Sets;

public class BlockVirtualPoolUpdateBuilder extends VirtualPoolUpdateBuilder {
    private static final String NO_AUTO_TIER_POLICY = "none";
    private final BlockVirtualPoolRestRep oldVirtualPool;
    private final BlockVirtualPoolUpdateParam virtualPool;

    public BlockVirtualPoolUpdateBuilder(BlockVirtualPoolRestRep oldVirtualPool) {
        this(oldVirtualPool, new BlockVirtualPoolUpdateParam());
    }

    protected BlockVirtualPoolUpdateBuilder(BlockVirtualPoolRestRep oldVirtualPool,
            BlockVirtualPoolUpdateParam virtualPool) {
        super(oldVirtualPool, virtualPool);
        this.oldVirtualPool = oldVirtualPool;
        this.virtualPool = virtualPool;
    }

    @Override
    public BlockVirtualPoolRestRep getOldVirtualPool() {
        return oldVirtualPool;
    }

    @Override
    public BlockVirtualPoolUpdateParam getVirtualPoolUpdate() {
        return virtualPool;
    }

    public BlockVirtualPoolUpdateBuilder setMinPaths(int paths) {
        virtualPool.setMinPaths(paths);
        return this;
    }

    public BlockVirtualPoolUpdateBuilder setMaxPaths(int paths) {
        virtualPool.setMaxPaths(paths);
        return this;
    }

    public BlockVirtualPoolUpdateBuilder setPathsPerInitiator(int paths) {
        virtualPool.setPathsPerInitiator(paths);
        return this;
    }

    public BlockVirtualPoolUpdateBuilder setHostIOLimitBandwidth(int limit) {
        virtualPool.setHostIOLimitBandwidth(limit);
        return this;
    }

    public BlockVirtualPoolUpdateBuilder setHostIOLimitIOPs(int limit) {
        virtualPool.setHostIOLimitIOPs(limit);
        return this;
    }

    public BlockVirtualPoolUpdateBuilder setAutoTieringPolicyName(String autoTieringPolicyName) {
        String policyName = StringUtils.defaultIfBlank(autoTieringPolicyName, NO_AUTO_TIER_POLICY);
        String oldPolicyName = StringUtils.defaultIfBlank(oldVirtualPool.getAutoTieringPolicyName(),
                NO_AUTO_TIER_POLICY);
        if (!StringUtils.equals(policyName, oldPolicyName)) {
            virtualPool.setAutoTieringPolicyName(StringUtils.defaultString(autoTieringPolicyName));
        }
        return this;
    }

    public BlockVirtualPoolUpdateBuilder setDriveType(String driveType) {
        String newDriveType = StringUtils.defaultIfEmpty(driveType, DriveTypes.NONE);
        String oldDriveType = StringUtils.defaultIfEmpty(oldVirtualPool.getDriveType(), DriveTypes.NONE);
        if (!StringUtils.equals(newDriveType, oldDriveType)) {
            virtualPool.setDriveType(newDriveType);
        }
        return this;
    }

    public BlockVirtualPoolUpdateBuilder setExpandable(boolean expandable) {
        if (!ObjectUtils.equals(expandable, oldVirtualPool.getExpandable())) {
            virtualPool.setExpandable(expandable);
        }
        return this;
    }

    public BlockVirtualPoolUpdateBuilder setFastExpansion(boolean fastExpansion) {
        if (!ObjectUtils.equals(fastExpansion, oldVirtualPool.getFastExpansion())) {
            virtualPool.setFastExpansion(fastExpansion);
        }
        return this;
    }

    public BlockVirtualPoolUpdateBuilder setThinVolumePreAllocationPercentage(Integer percent) {
        if (!ObjectUtils.equals(percent, oldVirtualPool.getThinVolumePreAllocationPercentage())) {
            virtualPool.setThinVolumePreAllocationPercentage(percent);
        }
        return this;
    }

    public BlockVirtualPoolUpdateBuilder setUniquePolicyNames(boolean uniquePolicyNames) {
        if (!ObjectUtils.equals(uniquePolicyNames, oldVirtualPool.getUniquePolicyNames())) {
            virtualPool.setUniquePolicyNames(uniquePolicyNames);
        }
        return this;
    }

    public BlockVirtualPoolUpdateBuilder setMultiVolumeConsistency(boolean multiVolumeConsistency) {
        if (!ObjectUtils.equals(multiVolumeConsistency, oldVirtualPool.getMultiVolumeConsistent())) {
            virtualPool.setMultiVolumeConsistency(multiVolumeConsistency);
        }
        return this;
    }

    @SuppressWarnings("unchecked")
    public BlockVirtualPoolUpdateBuilder setRaidLevels(Collection<String> newValues) {
        Set<String> oldValues = oldVirtualPool.getRaidLevels();

        Set<String> add = Sets.newHashSet(CollectionUtils.subtract(newValues, oldValues));
        Set<String> remove = Sets.newHashSet(CollectionUtils.subtract(oldValues, newValues));

        RaidLevelChanges changes = new RaidLevelChanges();
        if (!add.isEmpty()) {
            changes.setAdd(new RaidLevelAssignments(add));
        }
        if (!remove.isEmpty()) {
            changes.setRemove(new RaidLevelAssignments(remove));
        }
        virtualPool.setRaidLevelChanges(changes);
        return this;
    }

    protected BlockVirtualPoolProtectionUpdateParam getProtection() {
        if (virtualPool.getProtection() == null) {
            virtualPool.setProtection(new BlockVirtualPoolProtectionUpdateParam());
        }
        return virtualPool.getProtection();
    }

    private Integer getOldMaxSnapshots() {
        VirtualPoolProtectionSnapshotsParam snapshots = getSnapshots(oldVirtualPool);
        return snapshots != null ? snapshots.getMaxSnapshots() : null;
    }

    public BlockVirtualPoolUpdateBuilder setSnapshots(Integer maxSnapshots) {
        if (!ObjectUtils.equals(maxSnapshots, getOldMaxSnapshots())) {
            getProtection().setSnapshots(new VirtualPoolProtectionSnapshotsParam(maxSnapshots));
        }
        return this;
    }

    private Integer getOldMaxCopies() {
        VirtualPoolProtectionMirrorParam continuousCopies = getContinuousCopies(oldVirtualPool);
        return continuousCopies != null ? continuousCopies.getMaxMirrors() : null;
    }

    private URI getOldCopyVirtualPool() {
        VirtualPoolProtectionMirrorParam continuousCopies = getContinuousCopies(oldVirtualPool);
        return continuousCopies != null ? continuousCopies.getVpool() : null;
    }

    public BlockVirtualPoolUpdateBuilder setContinuousCopies(Integer maxCopies, URI virtualPoolId) {
        if (!ObjectUtils.equals(maxCopies, getOldMaxCopies())
                || !ObjectUtils.equals(virtualPoolId, getOldCopyVirtualPool())) {
            getProtection().setContinuousCopies(new VirtualPoolProtectionMirrorParam(maxCopies, virtualPoolId));
        }
        return this;
    }

    protected VirtualPoolRemoteProtectionUpdateParam getRemoteCopies() {
        if (getProtection().getRemoteCopies() == null) {
            getProtection().setRemoteCopies(new VirtualPoolRemoteProtectionUpdateParam());
        }

        return getProtection().getRemoteCopies();
    }

    public BlockVirtualPoolUpdateBuilder disableRemoteCopies() {
        getProtection().setRemoteCopies(new VirtualPoolRemoteProtectionUpdateParam());
        Set<VirtualPoolRemoteProtectionVirtualArraySettingsParam> noCopies = Sets.newHashSet();
        setRemoteCopies(noCopies);
        return this;
    }

    protected VirtualPoolProtectionRPChanges getRecoverPoint() {
        if (getProtection().getRecoverPoint() == null) {
            getProtection().setRecoverPoint(new VirtualPoolProtectionRPChanges());
        }
        return getProtection().getRecoverPoint();
    }

    public BlockVirtualPoolUpdateBuilder disableRecoverPoint() {
        getProtection().setRecoverPoint(new VirtualPoolProtectionRPChanges());
        Set<VirtualPoolProtectionVirtualArraySettingsParam> noCopies = Sets.newHashSet();
        setRecoverPointCopies(noCopies);
        return this;
    }

    public BlockVirtualPoolUpdateBuilder setRecoverPointJournalSize(String journalSize) {
        if (journalSize != null) {
            if (getProtectionSourcePolicy() == null) {
                getRecoverPoint().setSourcePolicy(new ProtectionSourcePolicy());
            }
            getProtectionSourcePolicy().setJournalSize(journalSize);
        }
        return this;
    }

    public BlockVirtualPoolUpdateBuilder setRecoverPointRemoteCopyMode(String remoteCopyMode) {
        if (remoteCopyMode != null) {
            if (getProtectionSourcePolicy() == null) {
                getRecoverPoint().setSourcePolicy(new ProtectionSourcePolicy());
            }
            getProtectionSourcePolicy().setRemoteCopyMode(remoteCopyMode);
        }
        return this;
    }

    public BlockVirtualPoolUpdateBuilder setJournalVarrayAndVpool(URI journalVarray, URI journalVpool) {
        if (getProtectionSourcePolicy() == null) {
            getRecoverPoint().setSourcePolicy(new ProtectionSourcePolicy());
        }

        getProtectionSourcePolicy().setJournalVarray(journalVarray);
        getProtectionSourcePolicy().setJournalVpool(journalVpool);
        return this;
    }

    public BlockVirtualPoolUpdateBuilder setStandByJournalVArrayVpool(URI standbyJournalVarray, URI standbyJournalVpool) {
        if (getProtectionSourcePolicy() == null) {
            getRecoverPoint().setSourcePolicy(new ProtectionSourcePolicy());
        }

        getProtectionSourcePolicy().setStandbyJournalVarray(standbyJournalVarray);
        getProtectionSourcePolicy().setStandbyJournalVpool(standbyJournalVpool);
        return this;
    }

    public BlockVirtualPoolUpdateBuilder setRecoverPointRpo(Long value, String type) {
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

    @SuppressWarnings("unchecked")
    public BlockVirtualPoolUpdateBuilder setRecoverPointCopies(
            Collection<VirtualPoolProtectionVirtualArraySettingsParam> newValues) {
        Set<VirtualPoolProtectionVirtualArraySettingsParam> oldValues = getRecoverPointCopies(oldVirtualPool);

        getRecoverPoint().getAdd().addAll(CollectionUtils.subtract(newValues, oldValues));
        getRecoverPoint().getRemove().addAll(CollectionUtils.subtract(oldValues, newValues));
        return this;
    }

    public BlockVirtualPoolUpdateBuilder addRecoverPointCopy(URI virtualArrayId, URI virtualPoolId, String journalSize) {
        getRecoverPoint().getAdd().add(createRecoverPointCopy(virtualArrayId, virtualPoolId, journalSize));
        return this;
    }

    public BlockVirtualPoolUpdateBuilder removeRecoverPointCopy(URI virtualArrayId, URI virtualPoolId,
            String journalSize) {
        getRecoverPoint().getRemove().add(createRecoverPointCopy(virtualArrayId, virtualPoolId, journalSize));
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

    public BlockVirtualPoolUpdateBuilder addRemoteCopy(URI virtualArrayId, URI virtualPoolId, String copyMode) {
        getRemoteCopies().getAdd().add(createRemoteCopy(virtualArrayId, virtualPoolId, copyMode));
        return this;
    }

    public BlockVirtualPoolUpdateBuilder removeRemoteCopy(URI virtualArrayId, URI virtualPoolId, String copyMode) {
        getRemoteCopies().getRemove().add(createRemoteCopy(virtualArrayId, virtualPoolId, copyMode));
        return this;
    }

    @SuppressWarnings("unchecked")
    public BlockVirtualPoolUpdateBuilder setRemoteCopies(
            Collection<VirtualPoolRemoteProtectionVirtualArraySettingsParam> newValues) {
        List<VirtualPoolRemoteProtectionVirtualArraySettingsParam> oldValues = getRemoteCopySettings(oldVirtualPool);

        getRemoteCopies().getAdd().addAll(CollectionUtils.subtract(newValues, oldValues));
        getRemoteCopies().getRemove().addAll(CollectionUtils.subtract(oldValues, newValues));
        return this;
    }

    public VirtualPoolRemoteProtectionVirtualArraySettingsParam createRemoteCopy(URI virtualArrayId, URI virtualPoolId,
            String copyMode) {
        VirtualPoolRemoteProtectionVirtualArraySettingsParam param = new VirtualPoolRemoteProtectionVirtualArraySettingsParam();
        param.setRemoteCopyMode(copyMode);
        param.setVarray(virtualArrayId);
        param.setVpool(virtualPoolId);
        return param;
    }

    public BlockVirtualPoolUpdateBuilder setHighAvailability(String type, Boolean enableCrossConnect, URI virtualArrayId,
            URI virtualPoolId,
            Boolean activeProtectionAtHASite, Boolean metroPoint) {
        VirtualPoolHighAvailabilityParam highAvailability = new VirtualPoolHighAvailabilityParam();
        if (HighAvailability.isVplexDistributed(type) || HighAvailability.isVplexLocal(type)) {
            highAvailability.setType(type);
            highAvailability.setAutoCrossConnectExport(enableCrossConnect);
            if (HighAvailability.isVplexDistributed(type)) {
                VirtualArrayVirtualPoolMapEntry value = new VirtualArrayVirtualPoolMapEntry();
                value.setVirtualArray(virtualArrayId);
                value.setVirtualPool(defaultURI(virtualPoolId));
                value.setActiveProtectionAtHASite(activeProtectionAtHASite);
                highAvailability.setHaVirtualArrayVirtualPool(value);
                highAvailability.setMetroPoint(metroPoint);
            }
        }
        virtualPool.setHighAvailability(highAvailability);
        return this;
    }

    public BlockVirtualPoolUpdateBuilder disableHighAvailability() {
        setHighAvailability(null, false, null, null, false, false);
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

    public static VirtualPoolRemoteMirrorProtectionParam getRemoteCopies(BlockVirtualPoolProtectionParam protection) {
        return protection != null ? protection.getRemoteCopies() : null;
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
