/*
 * Copyright (c) 2015 EMC Corporation
 * All Rights Reserved
 */
package models.virtualpool;

import static com.emc.vipr.client.core.util.ResourceUtils.asString;
import static com.emc.vipr.client.core.util.ResourceUtils.uri;
import static com.emc.vipr.client.core.util.ResourceUtils.uris;
import static util.BourneUtil.getViprClient;

import java.net.URI;
import java.util.List;
import java.util.Set;

import jobs.vipr.AutoTierPolicyNamesCall;
import jobs.vipr.ConnectedBlockVirtualPoolsCall;
import jobs.vipr.ConnectedVirtualArraysCall;
import jobs.vipr.MatchingBlockStoragePoolsCall;
import models.ConnectivityTypes;
import models.HighAvailability;
import models.ProtectionSystemTypes;
import models.SizeUnit;
import models.StorageSystemTypes;

import org.apache.commons.lang.BooleanUtils;
import org.apache.commons.lang.StringUtils;

import play.Logger;
import play.data.validation.Max;
import play.data.validation.Min;
import play.data.validation.Validation;
import play.i18n.Messages;
import util.VirtualPoolUtils;
import util.builders.BlockVirtualPoolBuilder;
import util.builders.BlockVirtualPoolUpdateBuilder;

import com.emc.storageos.model.NamedRelatedResourceRep;
import com.emc.storageos.model.vpool.BlockVirtualPoolProtectionParam;
import com.emc.storageos.model.vpool.BlockVirtualPoolRestRep;
import com.emc.storageos.model.vpool.ProtectionSourcePolicy;
import com.emc.storageos.model.vpool.VirtualPoolHighAvailabilityParam;
import com.emc.storageos.model.vpool.VirtualPoolProtectionRPParam;
import com.emc.storageos.model.vpool.VirtualPoolProtectionVirtualArraySettingsParam;
import com.emc.storageos.model.vpool.VirtualPoolRemoteMirrorProtectionParam;
import com.emc.storageos.model.vpool.VirtualPoolRemoteProtectionVirtualArraySettingsParam;
import com.emc.vipr.client.core.BlockVirtualPools;
import com.emc.vipr.client.core.util.ResourceUtils;
import com.google.common.collect.Lists;
import com.google.common.collect.Sets;
import com.google.gson.Gson;

public class BlockVirtualPoolForm extends VirtualPoolCommonForm<BlockVirtualPoolRestRep> {
    @Min(0)
    public Integer maxSnapshots;
    @Min(1)
    public Integer minPaths;
    @Min(1)
    public Integer maxPaths;
    @Min(1)
    public Integer initiatorPaths;
    public Boolean expandable;
    public Boolean fastExpansion;
    public Boolean multiVolumeConsistency;
    public String driveType;
    public Set<String> raidLevels;
    public Boolean uniqueAutoTierPolicyNames;
    public String autoTierPolicy;
    @Min(0)
    @Max(100)
    public Integer thinPreAllocationPercent;
    @Min(0)
    public Integer maxContinuousCopies;
    public String continuousCopyVirtualPool;
    public String highAvailability;
    public String haVirtualArray;
    public String haVirtualPool;
    public Boolean enableAutoCrossConnExport;
    public Boolean protectSourceSite;
    public Boolean protectHASite;
    public String activeSite;
    public String remoteProtection;
    public String rpJournalSize;
    public String rpRemoteCopyMode;
    public Long rpRpoValue;
    public String rpRpoType;
    public SizeUnit rpJournalSizeUnit;
    public String rpCopiesJson = "[]";
    public RPCopyForm[] rpCopies = {};
    public String srdfCopyMode;
    public String srdfCopiesJson = "[]";
    public SrdfCopyForm[] srdfCopies = {};
    public String sourceJournalVArray;
    public String haJournalVArray;
    public String sourceJournalVPool;
    public String haJournalVPool;

    // VMAX Host IO Limits attributes
    public Integer hostIOLimitBandwidth; // Host Front End limit bandwidth. If not specified or 0, indicated unlimited
    public Integer hostIOLimitIOPs; // Host Front End limit I/O. If not specified or 0, indicated unlimited

    public void deserialize() {
        Gson g = new Gson();
        srdfCopies = g.fromJson(srdfCopiesJson, SrdfCopyForm[].class);
        rpCopies = g.fromJson(rpCopiesJson, RPCopyForm[].class);
    }

    @Override
    public void validate(String formName) {
        super.validate(formName);

        if (this.expandable && this.maxContinuousCopies != null && this.maxContinuousCopies > 0) {
            Validation.addError(formName + ".expandable", "vpool.expandable.error.continousCopies");
            Validation.addError(formName + ".maxContinuousCopies", "vpool.continuousCopies.error.expandable");
        }

        // Recover point validation
        if (ProtectionSystemTypes.isRecoverPoint(remoteProtection)) {
            validateRecoverPoint(formName);
        }
        // SRDF validation
        else if (ProtectionSystemTypes.isSRDF(remoteProtection)) {
            validateSrdf(formName);
        }
        // High availability (vPlex) validation
        if (HighAvailability.isHighAvailability(highAvailability)) {
            validateHighAvailability(formName);
        }
    }

    private void validateRecoverPoint(String formName) {
        Validation.required(formName + ".rpJournalSize", rpJournalSize);
        if (!RPCopyForm.isValidJournalSize(rpJournalSize, rpJournalSizeUnit)) {
            Validation.addError(formName + ".rpJournalSize", "validation.invalid");
        }

        Set<String> varrayIds = Sets.newHashSet();
        Set<String> vpoolIds = Sets.newHashSet();

        boolean hasCopies = false;
        for (RPCopyForm rpCopy : rpCopies) {
            if (rpCopy != null && rpCopy.isEnabled()) {
                hasCopies = true;
                rpCopy.validate(formName + ".rpCopies");

                if (StringUtils.isNotBlank(rpCopy.virtualArray)) {
                    if (!varrayIds.add(rpCopy.virtualArray)) {
                        Validation.addError(formName + ".rpCopies", "rpCopy.virtualArray.error.duplicate");
                    }
                }
                if (StringUtils.isNotBlank(rpCopy.virtualPool)) {
                    vpoolIds.add(rpCopy.virtualPool);
                }
            }
        }
        if (!hasCopies) {
            Validation.required(formName + ".rpCopies", null);
        }

        // Extra validation when mixed with high availability
        if (HighAvailability.isHighAvailability(highAvailability)) {
            if (!Boolean.TRUE.equals(multiVolumeConsistency)) {
                Validation.addError(formName + ".multiVolumeConsistency", "vpool.rp.error.notMultiVolumeConsistent");
            }
        }
    }

    private void validateSrdf(String formName) {
        if (!StringUtils.equals(systemType, StorageSystemTypes.VMAX)) {
            Validation.addError(formName + ".systemType", "vpool.srdf.error.notSupported");
        }

        if ((srdfCopies == null) || (srdfCopies.length == 0)) {
            // Mark it as required
            Validation.required(formName + ".srdfCopies", null);
        }
        else {
            for (SrdfCopyForm copy1 : srdfCopies) {
                for (SrdfCopyForm copy2 : srdfCopies) {
                    if (!copy1.equals(copy2) && copy1.virtualArray.equals(copy2.virtualArray)) {
                        Validation.addError(formName + ".srdfCopies", "srdfCopy.virtualArray.error.duplicate");
                    }
                }
            }
        }
    }

    private void validateHighAvailability(String formName) {
        if (HighAvailability.isVplexDistributed(highAvailability)) {
            Validation.required(formName + ".haVirtualArray", haVirtualArray);
            if (ProtectionSystemTypes.isRecoverPoint(remoteProtection)) {
                if (BooleanUtils.isNotTrue(protectSourceSite) && BooleanUtils.isNotTrue(protectHASite)) {
                    Validation.addError(formName + ".protectSourceSite", Messages.get("vpool.protectSourceSite.error"));
                    Validation.addError(formName + ".protectHASite", Messages.get("vpool.protectHASite.error"));
                }
            }
        }

        if (!ProtectionSystemTypes.isRecoverPointOrNone(remoteProtection)) {
            Validation.addError(formName + ".remoteProtection", "vpool.remoteProtection.error.vplex");
        }
    }

    protected void validateTenant(String formName) {
        if (enableTenants) {
            Validation.required(String.format("%s.tenants", formName), this.tenants);
        }
    }

    @Override
    public void load(BlockVirtualPoolRestRep virtualPool) {
        doLoad(virtualPool);
        BlockVirtualPools vpools = getViprClient().blockVpools();
        loadQuota(vpools);
        loadTenantACLs(vpools);
    }

    protected void doLoad(BlockVirtualPoolRestRep virtualPool) {
        loadCommon(virtualPool);
        minPaths = virtualPool.getMinPaths();
        maxPaths = virtualPool.getMaxPaths();
        initiatorPaths = virtualPool.getPathsPerInitiator();
        driveType = virtualPool.getDriveType();
        autoTierPolicy = virtualPool.getAutoTieringPolicyName();
        expandable = virtualPool.getExpandable();
        fastExpansion = virtualPool.getFastExpansion();
        multiVolumeConsistency = virtualPool.getMultiVolumeConsistent();
        thinPreAllocationPercent = virtualPool.getThinVolumePreAllocationPercentage();
        uniqueAutoTierPolicyNames = virtualPool.getUniquePolicyNames();
        raidLevels = defaultSet(virtualPool.getRaidLevels());
        hostIOLimitBandwidth = virtualPool.getHostIOLimitBandwidth();
        hostIOLimitIOPs = virtualPool.getHostIOLimitIOPs();

        VirtualPoolHighAvailabilityParam highAvailabilityType = virtualPool.getHighAvailability();
        if (highAvailabilityType != null && HighAvailability.isHighAvailability(highAvailabilityType.getType())) {
            highAvailability = highAvailabilityType.getType();
            if (highAvailability.equals(HighAvailability.VPLEX_LOCAL)) {
                protectSourceSite = true;
            }
            enableAutoCrossConnExport = highAvailabilityType.getAutoCrossConnectExport();
            if (highAvailabilityType.getHaVirtualArrayVirtualPool() != null) {
                haVirtualArray = asString(highAvailabilityType.getHaVirtualArrayVirtualPool().getVirtualArray());
                haVirtualPool = asString(highAvailabilityType.getHaVirtualArrayVirtualPool().getVirtualPool());

                Boolean activeProtectionAtHASite = Boolean.TRUE.equals(highAvailabilityType.getHaVirtualArrayVirtualPool()
                        .getActiveProtectionAtHASite());
                Boolean metroPoint = Boolean.TRUE.equals(highAvailabilityType.getMetroPoint());
                if (metroPoint) {
                    protectSourceSite = true;
                    protectHASite = true;
                    if (activeProtectionAtHASite) {
                        activeSite = HighAvailability.VPLEX_HA.toString();
                    }
                    else {
                        activeSite = HighAvailability.VPLEX_SOURCE.toString();
                        protectSourceSite = true;
                    }
                }
                else {
                    protectHASite = activeProtectionAtHASite;
                    protectSourceSite = !protectHASite;
                }
            }
            else {
                protectSourceSite = true;
            }
        } else {
            protectSourceSite = true;
        }

        BlockVirtualPoolProtectionParam protection = virtualPool.getProtection();
        if (protection != null) {
            if (protection.getSnapshots() != null) {
                maxSnapshots = protection.getSnapshots().getMaxSnapshots();
            }
            if (protection.getContinuousCopies() != null) {
                maxContinuousCopies = protection.getContinuousCopies().getMaxMirrors();
                continuousCopyVirtualPool = asString(protection.getContinuousCopies().getVpool());
            }

            VirtualPoolProtectionRPParam recoverPoint = protection.getRecoverPoint();
            if (recoverPoint != null) {
                remoteProtection = ProtectionSystemTypes.RECOVERPOINT;
                ProtectionSourcePolicy sourcePolicy = recoverPoint.getSourcePolicy();
                if (sourcePolicy != null) {
                    String journalSize = sourcePolicy.getJournalSize();
                    rpJournalSizeUnit = RPCopyForm.parseJournalSizeUnit(journalSize);
                    rpJournalSize = StringUtils.defaultIfBlank(
                            RPCopyForm.parseJournalSize(journalSize, rpJournalSizeUnit), RPCopyForm.JOURNAL_SIZE_MIN);
                    rpRemoteCopyMode = sourcePolicy.getRemoteCopyMode();
                    rpRpoValue = sourcePolicy.getRpoValue();
                    rpRpoType = sourcePolicy.getRpoType();
                    if (protectHASite != null && protectSourceSite != null && protectHASite && protectSourceSite) {
                        // Backend will take care of swapping
                        // if(activeSite.equalsIgnoreCase(HighAvailability.VPLEX_SOURCE)){
                        sourceJournalVArray = asString(sourcePolicy.getJournalVarray());
                        sourceJournalVPool = asString(sourcePolicy.getJournalVpool());
                        haJournalVArray = asString(sourcePolicy.getStandbyJournalVarray());
                        haJournalVPool = asString(sourcePolicy.getStandbyJournalVpool());
                    }
                    else {
                        if (protectHASite != null && protectHASite) {
                            haJournalVArray = asString(sourcePolicy.getJournalVarray());
                            haJournalVPool = asString(sourcePolicy.getJournalVpool());
                        }
                        else if (protectSourceSite != null && protectSourceSite) {
                            sourceJournalVArray = asString(sourcePolicy.getJournalVarray());
                            sourceJournalVPool = asString(sourcePolicy.getJournalVpool());
                        }
                    }
                }

                List<RPCopyForm> rpCopyForms = Lists.newArrayList();
                for (VirtualPoolProtectionVirtualArraySettingsParam copy : recoverPoint.getCopies()) {
                    RPCopyForm rpCopy = new RPCopyForm();
                    rpCopy.load(copy);
                    rpCopyForms.add(rpCopy);
                }
                rpCopies = rpCopyForms.toArray(new RPCopyForm[0]);
                rpCopiesJson = new Gson().toJson(rpCopies);
            }
            VirtualPoolRemoteMirrorProtectionParam srdf = protection.getRemoteCopies();
            if (srdf != null) {
                remoteProtection = ProtectionSystemTypes.SRDF;

                List<SrdfCopyForm> copyForms = Lists.newArrayList();
                for (VirtualPoolRemoteProtectionVirtualArraySettingsParam copy : srdf.getRemoteCopySettings()) {
                    srdfCopyMode = copy.getRemoteCopyMode();

                    SrdfCopyForm srdfCopyForm = new SrdfCopyForm();
                    srdfCopyForm.load(copy);
                    copyForms.add(srdfCopyForm);
                }
                srdfCopies = copyForms.toArray(new SrdfCopyForm[0]);
                Gson gson = new Gson();
                srdfCopiesJson = gson.toJson(srdfCopies);
            }
            else {
                srdfCopiesJson = "[]";
            }
        }
    }

    @Override
    public BlockVirtualPoolRestRep save() {
        BlockVirtualPoolRestRep virtualPool = doSave();
        BlockVirtualPools vpools = getViprClient().blockVpools();
        saveQuota(vpools);
        saveTenantACLs(vpools);
        return virtualPool;
    }

    protected BlockVirtualPoolRestRep doSave() {
        BlockVirtualPoolRestRep virtualPool;
        if (isNew()) {
            BlockVirtualPoolBuilder builder = apply(new BlockVirtualPoolBuilder());
            virtualPool = VirtualPoolUtils.create(builder.getVirtualPool());
            this.id = ResourceUtils.stringId(virtualPool);
        }
        else {
            BlockVirtualPoolRestRep oldVirtualPool = VirtualPoolUtils.getBlockVirtualPool(id);
            BlockVirtualPoolUpdateBuilder builder = apply(new BlockVirtualPoolUpdateBuilder(oldVirtualPool));
            virtualPool = VirtualPoolUtils.update(id, builder.getVirtualPoolUpdate());
            List<NamedRelatedResourceRep> matchingPools = VirtualPoolUtils.refreshMatchingPools(virtualPool);
            Logger.info("Refreshed Block Virtual Pool '%s' matching pools: %d", virtualPool.getName(), matchingPools.size());
        }
        virtualPool = saveStoragePools(virtualPool);
        return virtualPool;
    }

    private BlockVirtualPoolBuilder apply(BlockVirtualPoolBuilder builder) {
        applyCommon(builder);
        builder.setMinPaths(defaultInt(minPaths, 1));
        builder.setMaxPaths(defaultInt(maxPaths, 1));
        builder.setPathsPerInitiator(defaultInt(initiatorPaths, 1));
        builder.setDriveType(driveType);
        builder.setAutoTieringPolicyName(autoTierPolicy);
        builder.setExpandable(defaultBoolean(expandable));
        builder.setFastExpansion(defaultBoolean(fastExpansion));
        builder.setMultiVolumeConsistency(defaultBoolean(multiVolumeConsistency));
        builder.setSnapshots(maxSnapshots);
        builder.setContinuousCopies(maxContinuousCopies, uri(continuousCopyVirtualPool));
        builder.setThinVolumePreAllocationPercentage(thinPreAllocationPercent);
        builder.setUniquePolicyNames(defaultBoolean(uniqueAutoTierPolicyNames));
        builder.setRaidLevels(raidLevels);
        builder.setHostIOLimitBandwidth(defaultInt(hostIOLimitBandwidth, 0));
        builder.setHostIOLimitIOPs(defaultInt(hostIOLimitIOPs, 0));

        if (ProtectionSystemTypes.isRecoverPoint(remoteProtection)) {
            builder.enableRecoverPoint(RPCopyForm.formatJournalSize(rpJournalSize, rpJournalSizeUnit));
            builder.setRecoverPointRemoteCopyMode(rpRemoteCopyMode);
            builder.setRecoverPointRpo(rpRpoValue, rpRpoType);
            Set<VirtualPoolProtectionVirtualArraySettingsParam> copies = Sets.newLinkedHashSet();
            for (RPCopyForm rpCopy : rpCopies) {
                if (rpCopy != null && rpCopy.isEnabled()) {
                    copies.add(rpCopy.write());
                }
            }
            builder.setRecoverPointCopies(copies);
            // Set journal varray and vpool default for source
            builder.setJournalVarrayAndVpool(uri(sourceJournalVArray), uri(sourceJournalVPool));
        }

        if (ProtectionSystemTypes.isSRDF(remoteProtection)) {
            if (srdfCopies != null) {
                List<VirtualPoolRemoteProtectionVirtualArraySettingsParam> copies = Lists.newArrayList();
                for (SrdfCopyForm srdfCopyForm : srdfCopies) {
                    if (srdfCopyForm != null && srdfCopyForm.isEnabled()) {
                        copies.add(srdfCopyForm.write(srdfCopyMode));
                    }
                }
                builder.setRemoteCopies(copies);
            }
        }

        if (HighAvailability.isHighAvailability(highAvailability)) {
            URI virtualArrayId = uri(haVirtualArray);
            URI virtualPoolId = uri(haVirtualPool);
            boolean activeProtectionAtHASite = BooleanUtils.isTrue(protectHASite);
            boolean metroPoint = false;
            if (BooleanUtils.isTrue(protectSourceSite) && BooleanUtils.isTrue(protectHASite)) {
                metroPoint = true;
                activeProtectionAtHASite = StringUtils.equalsIgnoreCase(activeSite, HighAvailability.VPLEX_HA);
                builder.setJournalVarrayAndVpool(uri(sourceJournalVArray), uri(sourceJournalVPool));
                builder.setStandByJournalVArrayVpool(uri(haJournalVArray), uri(haJournalVPool));
            }
            else {
                if (activeProtectionAtHASite) {
                    builder.setJournalVarrayAndVpool(uri(haJournalVArray), uri(haJournalVPool));
                } else {
                    builder.setJournalVarrayAndVpool(uri(sourceJournalVArray), uri(sourceJournalVPool));
                }

                // Reset the standby varray/vpool values
                builder.setStandByJournalVArrayVpool(null, null);
            }
            builder.setHighAvailability(highAvailability, enableAutoCrossConnExport, virtualArrayId, virtualPoolId,
                    activeProtectionAtHASite, metroPoint);
        }
        return builder;
    }

    private BlockVirtualPoolUpdateBuilder apply(BlockVirtualPoolUpdateBuilder builder) {
        applyCommon(builder);
        builder.setSnapshots(defaultInt(maxSnapshots));
        builder.setContinuousCopies(maxContinuousCopies, uri(continuousCopyVirtualPool));

        // Only allow updating these fields if not locked
        if (!isLocked()) {
            builder.setMinPaths(defaultInt(minPaths, 1));
            builder.setMaxPaths(defaultInt(maxPaths, 1));
            builder.setPathsPerInitiator(defaultInt(initiatorPaths, 1));
            builder.setAutoTieringPolicyName(autoTierPolicy);
            builder.setDriveType(driveType);
            builder.setExpandable(defaultBoolean(expandable));
            builder.setFastExpansion(defaultBoolean(fastExpansion));
            builder.setThinVolumePreAllocationPercentage(thinPreAllocationPercent);
            builder.setUniquePolicyNames(defaultBoolean(uniqueAutoTierPolicyNames));
            builder.setMultiVolumeConsistency(defaultBoolean(multiVolumeConsistency));
            builder.setRaidLevels(defaultSet(raidLevels));
            builder.setHostIOLimitBandwidth(defaultInt(hostIOLimitBandwidth, 0));
            builder.setHostIOLimitIOPs(defaultInt(hostIOLimitIOPs, 0));

            if (ProtectionSystemTypes.isRecoverPoint(remoteProtection)) {
                builder.setRecoverPointJournalSize(RPCopyForm.formatJournalSize(rpJournalSize, rpJournalSizeUnit));
                builder.setRecoverPointRemoteCopyMode(rpRemoteCopyMode);
                builder.setRecoverPointRpo(rpRpoValue, rpRpoType);
                Set<VirtualPoolProtectionVirtualArraySettingsParam> copies = Sets.newLinkedHashSet();
                for (RPCopyForm rpCopy : rpCopies) {
                    if (rpCopy != null && rpCopy.isEnabled()) {
                        copies.add(rpCopy.write());
                    }
                }
                builder.setRecoverPointCopies(copies);
                builder.setJournalVarrayAndVpool(uri(sourceJournalVArray), uri(sourceJournalVPool));
            }
            else {
                builder.disableRecoverPoint();
            }

            if (ProtectionSystemTypes.isSRDF(remoteProtection)) {
                Set<VirtualPoolRemoteProtectionVirtualArraySettingsParam> copies = Sets.newHashSet();
                for (SrdfCopyForm srdfCopyForm : srdfCopies) {
                    if (srdfCopyForm != null && srdfCopyForm.isEnabled()) {
                        copies.add(srdfCopyForm.write(srdfCopyMode));
                    }
                }
                builder.setRemoteCopies(copies);
            } else {
                builder.disableRemoteCopies();
            }

            if (HighAvailability.isHighAvailability(highAvailability)) {
                boolean activeProtectionAtHASite = BooleanUtils.isTrue(protectHASite);
                boolean metroPoint = false;
                if (BooleanUtils.isTrue(protectSourceSite) && BooleanUtils.isTrue(protectHASite)) {
                    metroPoint = true;
                    activeProtectionAtHASite = StringUtils.equalsIgnoreCase(activeSite, HighAvailability.VPLEX_HA);
                    builder.setJournalVarrayAndVpool(uri(sourceJournalVArray), uri(sourceJournalVPool));
                    builder.setStandByJournalVArrayVpool(uri(haJournalVArray), uri(haJournalVPool));
                } else {
                    if (activeProtectionAtHASite) {
                        builder.setJournalVarrayAndVpool(uri(haJournalVArray), uri(haJournalVPool));
                    } else {
                        builder.setJournalVarrayAndVpool(uri(sourceJournalVArray), uri(sourceJournalVPool));
                    }

                    // Reset the standby varray/vpool values
                    builder.setStandByJournalVArrayVpool(null, null);
                }
                builder.setHighAvailability(highAvailability, enableAutoCrossConnExport, uri(haVirtualArray), uri(haVirtualPool),
                        activeProtectionAtHASite, metroPoint);
            }
            else {
                builder.disableHighAvailability();
            }
        }

        return builder;
    }

    @Override
    protected BlockVirtualPoolRestRep updateStoragePools(Set<String> add, Set<String> remove) {
        return VirtualPoolUtils.updateAssignedBlockPools(id, add, remove);
    }

    public MatchingBlockStoragePoolsCall matchingStoragePools() {
        BlockVirtualPoolBuilder builder = new BlockVirtualPoolBuilder();
        apply(builder);
        builder.setUseMatchedPools(true);
        return new MatchingBlockStoragePoolsCall(builder.getVirtualPool());
    }

    public ConnectedBlockVirtualPoolsCall connectedVirtualPools() {
        return new ConnectedBlockVirtualPoolsCall(uris(virtualArrays));
    }

    public ConnectedVirtualArraysCall recoverPointVirtualArrays() {
        boolean isRecoverPoint = ProtectionSystemTypes.isRecoverPoint(remoteProtection);
        List<URI> varrayIds = isRecoverPoint ? uris(virtualArrays) : uris();
        return new ConnectedVirtualArraysCall(varrayIds, ConnectivityTypes.RECOVER_POINT);
    }

    public ConnectedVirtualArraysCall sourceJournalVirtualArrays() {
        boolean isRecoverPoint = ProtectionSystemTypes.isRecoverPoint(remoteProtection);
        List<URI> varrayIds = isRecoverPoint ? uris(virtualArrays) : uris();
        return new ConnectedVirtualArraysCall(varrayIds, ConnectivityTypes.RECOVER_POINT);
    }

    public ConnectedVirtualArraysCall haRpJournalVirtualArrays() {
        boolean isRecoverPoint = ProtectionSystemTypes.isRecoverPoint(remoteProtection);
        List<URI> varrayIds = isRecoverPoint ? uris(virtualArrays) : uris();
        return new ConnectedVirtualArraysCall(varrayIds, ConnectivityTypes.RECOVER_POINT);
    }

    public ConnectedBlockVirtualPoolsCall sourceRpJournalVirtualPools() {
        return new ConnectedBlockVirtualPoolsCall(uris(sourceJournalVArray));
    }

    public ConnectedBlockVirtualPoolsCall haRpJournalVirtualPools() {
        return new ConnectedBlockVirtualPoolsCall(uris(haJournalVArray));
    }

    public ConnectedVirtualArraysCall srdfVirtualArrays() {
        boolean isSrdf = ProtectionSystemTypes.isSRDF(remoteProtection);
        List<URI> varrayIds = isSrdf ? uris(virtualArrays) : uris();
        return new ConnectedVirtualArraysCall(varrayIds, ConnectivityTypes.SRDF);
    }

    public ConnectedBlockVirtualPoolsCall srdfVirtualPools() {
        boolean isSrdf = ProtectionSystemTypes.isSRDF(remoteProtection);
        List<URI> varrayIds = isSrdf ? uris(virtualArrays) : uris();
        return new ConnectedBlockVirtualPoolsCall(varrayIds);
    }

    public ConnectedVirtualArraysCall highAvailabilityVirtualArrays() {
        boolean isHighAvailability = HighAvailability.isHighAvailability(highAvailability);
        List<URI> varrayIds = isHighAvailability ? uris(virtualArrays) : uris();
        return new ConnectedVirtualArraysCall(varrayIds, ConnectivityTypes.VPLEX);
    }

    public ConnectedBlockVirtualPoolsCall highAvailabilityVirtualPools() {
        return new ConnectedBlockVirtualPoolsCall(uris(haVirtualArray));
    }

    public AutoTierPolicyNamesCall autoTierPolicyNames() {
        boolean isVnx = StorageSystemTypes.isVnxBlock(systemType) || StorageSystemTypes.isVNXe(systemType);
        boolean uniqueNames = defaultBoolean(uniqueAutoTierPolicyNames) || isVnx;
        return new AutoTierPolicyNamesCall(uris(virtualArrays), provisioningType, systemType, uniqueNames);
    }

    protected static int defaultInt(Integer value) {
        return defaultInt(value, 0);
    }

    protected static int defaultInt(Integer value, int defaultValue) {
        return value != null ? value : defaultValue;
    }

    protected static boolean defaultBoolean(Boolean value) {
        return defaultBoolean(value, false);
    }

    protected static boolean defaultBoolean(Boolean value, boolean defaultValue) {
        return value != null ? value : defaultValue;
    }

}
