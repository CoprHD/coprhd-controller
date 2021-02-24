/*
 * Copyright (c) 2008-2013 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.api.service.impl.resource.utils;

import java.math.BigInteger;
import java.net.URI;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.emc.storageos.api.service.authorization.PermissionsHelper;
import com.emc.storageos.api.service.impl.resource.ArgValidator;
import com.emc.storageos.api.service.impl.resource.blockingestorchestration.BlockIngestOrchestrator;
import com.emc.storageos.api.service.impl.resource.blockingestorchestration.BlockRecoverPointIngestOrchestrator;
import com.emc.storageos.api.service.impl.resource.blockingestorchestration.IngestionException;
import com.emc.storageos.api.service.impl.resource.blockingestorchestration.context.IngestionRequestContext;
import com.emc.storageos.api.service.impl.resource.blockingestorchestration.context.VolumeIngestionContext;
import com.emc.storageos.api.service.impl.resource.blockingestorchestration.context.impl.BlockVolumeIngestionContext;
import com.emc.storageos.api.service.impl.resource.blockingestorchestration.context.impl.RecoverPointVolumeIngestionContext;
import com.emc.storageos.api.service.impl.resource.blockingestorchestration.context.impl.RpVplexVolumeIngestionContext;
import com.emc.storageos.api.service.impl.resource.blockingestorchestration.context.impl.VplexVolumeIngestionContext;
import com.emc.storageos.api.service.impl.resource.utils.PropertySetterUtil.VolumeObjectProperties;
import com.emc.storageos.computesystemcontroller.impl.ComputeSystemHelper;
import com.emc.storageos.coordinator.client.service.CoordinatorClient;
import com.emc.storageos.db.client.DbClient;
import com.emc.storageos.db.client.URIUtil;
import com.emc.storageos.db.client.constraint.AlternateIdConstraint;
import com.emc.storageos.db.client.constraint.ContainmentConstraint;
import com.emc.storageos.db.client.constraint.PrefixConstraint;
import com.emc.storageos.db.client.constraint.URIQueryResultList;
import com.emc.storageos.db.client.impl.DbModelClientImpl;
import com.emc.storageos.db.client.model.AbstractChangeTrackingSet;
import com.emc.storageos.db.client.model.BlockConsistencyGroup;
import com.emc.storageos.db.client.model.BlockConsistencyGroup.Types;
import com.emc.storageos.db.client.model.BlockMirror;
import com.emc.storageos.db.client.model.BlockObject;
import com.emc.storageos.db.client.model.BlockSnapshot;
import com.emc.storageos.db.client.model.BlockSnapshotSession;
import com.emc.storageos.db.client.model.Cluster;
import com.emc.storageos.db.client.model.DataObject;
import com.emc.storageos.db.client.model.DataObject.Flag;
import com.emc.storageos.db.client.model.DiscoveredDataObject.RegistrationStatus;
import com.emc.storageos.db.client.model.ExportGroup;
import com.emc.storageos.db.client.model.ExportMask;
import com.emc.storageos.db.client.model.ExportPathParams;
import com.emc.storageos.db.client.model.Host;
import com.emc.storageos.db.client.model.HostInterface;
import com.emc.storageos.db.client.model.Initiator;
import com.emc.storageos.db.client.model.NamedURI;
import com.emc.storageos.db.client.model.Project;
import com.emc.storageos.db.client.model.ProtectionSet;
import com.emc.storageos.db.client.model.ProtectionSet.ProtectionStatus;
import com.emc.storageos.db.client.model.ProtectionSystem;
import com.emc.storageos.db.client.model.RemoteDirectorGroup.SupportedCopyModes;
import com.emc.storageos.db.client.model.StoragePool;
import com.emc.storageos.db.client.model.StoragePort;
import com.emc.storageos.db.client.model.StorageSystem;
import com.emc.storageos.db.client.model.StringMap;
import com.emc.storageos.db.client.model.StringSet;
import com.emc.storageos.db.client.model.StringSetMap;
import com.emc.storageos.db.client.model.TenantOrg;
import com.emc.storageos.db.client.model.VirtualArray;
import com.emc.storageos.db.client.model.VirtualPool;
import com.emc.storageos.db.client.model.VirtualPool.SystemType;
import com.emc.storageos.db.client.model.Volume;
import com.emc.storageos.db.client.model.Volume.PersonalityTypes;
import com.emc.storageos.db.client.model.ZoneInfoMap;
import com.emc.storageos.db.client.model.UnManagedDiscoveredObjects.UnManagedConsistencyGroup;
import com.emc.storageos.db.client.model.UnManagedDiscoveredObjects.UnManagedExportMask;
import com.emc.storageos.db.client.model.UnManagedDiscoveredObjects.UnManagedProtectionSet;
import com.emc.storageos.db.client.model.UnManagedDiscoveredObjects.UnManagedProtectionSet.SupportedCGInformation;
import com.emc.storageos.db.client.model.UnManagedDiscoveredObjects.UnManagedVolume;
import com.emc.storageos.db.client.model.UnManagedDiscoveredObjects.UnManagedVolume.SupportedVolumeCharacterstics;
import com.emc.storageos.db.client.model.UnManagedDiscoveredObjects.UnManagedVolume.SupportedVolumeInformation;
import com.emc.storageos.db.client.model.util.BlockConsistencyGroupUtils;
import com.emc.storageos.db.client.util.CommonTransformerFunctions;
import com.emc.storageos.db.client.util.CustomQueryUtility;
import com.emc.storageos.db.client.util.NullColumnValueGetter;
import com.emc.storageos.db.client.util.ResourceAndUUIDNameGenerator;
import com.emc.storageos.db.client.util.StringSetUtil;
import com.emc.storageos.model.ResourceOperationTypeEnum;
import com.emc.storageos.protectioncontroller.impl.recoverpoint.RPHelper;
import com.emc.storageos.recoverpoint.responses.GetCopyResponse.GetCopyAccessStateResponse;
import com.emc.storageos.svcs.errorhandling.resources.APIException;
import com.emc.storageos.util.ConnectivityUtil;
import com.emc.storageos.util.VPlexUtil;
import com.emc.storageos.volumecontroller.impl.ControllerUtils;
import com.emc.storageos.volumecontroller.impl.plugins.discovery.smis.processor.detailedDiscovery.RemoteMirrorObject;
import com.emc.storageos.volumecontroller.impl.utils.ExportMaskUtils;
import com.emc.storageos.volumecontroller.placement.BlockStorageScheduler;
import com.emc.storageos.vplex.api.VPlexApiConstants;
import com.emc.storageos.vplexcontroller.VPlexControllerUtils;
import com.emc.storageos.vplexcontroller.VplexBackendIngestionContext;
import com.google.common.base.Joiner;
import com.google.common.collect.Collections2;
import com.google.common.collect.Sets;
import com.google.common.collect.Sets.SetView;

public class VolumeIngestionUtil {
    private static Logger _logger = LoggerFactory.getLogger(VolumeIngestionUtil.class);
    public static final String UNMANAGEDVOLUME = "UNMANAGEDVOLUME";
    public static final String VOLUME = "VOLUME";
    private static final String VOLUME_TEXT = "Volume";
    private static final String FALSE = "false";
    private static final String TRUE = "true";
    private static final String UNMANAGEDVOLUME_CLUSTER_FILTERING_SETTING = "api_ingestion_allow_cluster_volumes_for_single_hosts";
    public static final String RP_JOURNAL = "journal";

    /**
     * Validation Steps 1. validate PreExistingVolume uri. 2. Check PreExistingVolume is under
     * Bourne Management already. 3. Check whether given vPool is present in the PreExistingVolumes
     * Supported vPool List.
     *
     * @param unManagedVolumes the UnManagedVolumes from the request to validate
     * @param vPool the VirtualPool to validate against
     * @throws IngestionException
     */
    public static void checkIngestionRequestValidForUnManagedVolumes(
            List<URI> unManagedVolumes, VirtualPool vPool, DbClient dbClient)
            throws IngestionException {

        for (URI unManagedVolumeUri : unManagedVolumes) {
            UnManagedVolume unManagedVolume = dbClient.queryObject(UnManagedVolume.class,
                    unManagedVolumeUri);

            checkUnmanagedVolumePartiallyDiscovered(unManagedVolume, unManagedVolumeUri);

            StringSetMap unManagedVolumeInformation = unManagedVolume.getVolumeInformation();

            try {
                // Check if UnManagedVolume is CG enabled and VPool is not CG enabled.
                if (checkUnManagedResourceAddedToConsistencyGroup(unManagedVolume) && !vPool.getMultivolumeConsistency()) {
                    _logger.error(String
                            .format("The requested Virtual Pool %s does not have the Multi-Volume Consistency flag set, and unmanage volume %s is part of a consistency group.",
                                    vPool.getLabel(), unManagedVolume.getLabel()));
                    throw APIException.internalServerErrors.unmanagedVolumeVpoolConsistencyGroupMismatch(vPool.getLabel(),
                            unManagedVolume.getLabel());
                }

                // Check if the UnManagedVolume is a snapshot & Vpool doesn't have snapshotCount defined.
                if (isSnapshot(unManagedVolume) && 0 == vPool.getMaxNativeSnapshots()) {
                    throw APIException.internalServerErrors.noMaxSnapshotsDefinedInVirtualPool(
                            vPool.getLabel(), unManagedVolume.getLabel());
                }

                // a VPLEX volume and snapshot will not have an associated pool
                if (!isVplexVolume(unManagedVolume) && !isSnapshot(unManagedVolume)) {
                    checkStoragePoolValidForUnManagedVolumeUri(unManagedVolumeInformation,
                            dbClient, unManagedVolumeUri);
                }

                if (!isVplexBackendVolume(unManagedVolume)) {
                    checkVPoolValidForGivenUnManagedVolumeUris(unManagedVolumeInformation, unManagedVolume,
                            vPool.getId(), dbClient);
                }
            } catch (APIException ex) {
                _logger.error(ex.getLocalizedMessage());
                throw IngestionException.exceptions.validationException(ex.getLocalizedMessage());
            }
        }
    }

    /**
     * Checks that the Unmanaged Volume is compatible with the Virtual Pool's protection settings.
     *
     * @param vpool the Virtual Pool
     * @param unManagedVolume the Unmanaged Volume
     * @param dbClient an instance of the database client
     *
     * @throws IngestionException
     */
    public static void checkVPoolValidForUnManagedVolumeInProtectedMode(
            VirtualPool vpool, UnManagedVolume unManagedVolume, DbClient dbClient) throws IngestionException {

        // cannot ingest volumes other than VPlex virtual volumes into VPlex virtual pools.
        boolean haEnabledVpool = VirtualPool.vPoolSpecifiesHighAvailability(vpool);
        boolean isVplexVolume = VolumeIngestionUtil.isVplexVolume(unManagedVolume);
        boolean isVplexBackendVolume = VolumeIngestionUtil.isVplexBackendVolume(unManagedVolume);
        if (haEnabledVpool && !isVplexVolume && !isVplexBackendVolume) {
            throw IngestionException.exceptions.cannotIngestNonVplexVolumeIntoVplexVpool(unManagedVolume.getLabel());
        }

        boolean remoteProtectionEnabled = VirtualPool.vPoolSpecifiesSRDF(vpool);
        _logger.debug("Remote protection enabled {}, {}", remoteProtectionEnabled, unManagedVolume.getId());
        StringSetMap unManagedVolumeInformation = unManagedVolume.getVolumeInformation();
        StringSet copyModes = unManagedVolumeInformation.get(SupportedVolumeInformation.REMOTE_COPY_MODE.toString());
        String remoteMirrorEnabledInVolume = unManagedVolume.getVolumeCharacterstics().get(
                SupportedVolumeCharacterstics.REMOTE_MIRRORING.toString());
        String type = PropertySetterUtil.extractValueFromStringSet(SupportedVolumeInformation.REMOTE_VOLUME_TYPE.toString(),
                unManagedVolumeInformation);
        _logger.debug("UnManaged Volume Remote mirror Enabled {}", remoteMirrorEnabledInVolume);
        if (remoteProtectionEnabled) {
            // Usecase where VPool is remote protection enabled and want to ingest replicas into the same VPool.
            if (VolumeIngestionUtil.isParentSRDFProtected(unManagedVolume, dbClient)) {
                _logger.info("Found a Replica {} and its source volume is SRDF protected.", unManagedVolume.getId());
                return;
            }
            if (RemoteMirrorObject.Types.TARGET.toString().equalsIgnoreCase(type)) {
                _logger.warn(
                        "UnManaged Volume {} is a SRDF Target, hence cannot be associated with VPool which contains SRDF remote settings configured. Skipping Ingestion",
                        unManagedVolume.getId());
                throw IngestionException.exceptions.unmanagedVolumeIsAnSrdfTarget(unManagedVolume.getLabel());
            }
            if (null == copyModes) {
                throw IngestionException.exceptions.unmanagedVolumeWithoutSRDFProtection(unManagedVolume.getLabel());
            }
            String copyMode = null;
            for (String cMode : copyModes) {
                copyMode = cMode;
                break;
            }
            _logger.debug("UnManaged Volume Copy Mode {}", copyMode);
            if (!Boolean.parseBoolean(unManagedVolume.getVolumeCharacterstics().get(
                    SupportedVolumeCharacterstics.REMOTE_MIRRORING.toString()))) {
                _logger.warn(
                        "UnManaged Volume {} is not remote protected, hence cannot be associated with VPool with remote protection configured. Skipping Ingestion",
                        unManagedVolume.getId(), copyMode);
                throw IngestionException.exceptions.srdfVpoolRemoteProtectionCopyModeMismatch(unManagedVolume.getLabel(), copyMode);
            } else {
                Map<String, List<String>> groupCopyModesByVPools = VirtualPool.groupRemoteCopyModesByVPool(vpool, dbClient);
                Set<String> supportedVPoolCopyModes = VolumeIngestionUtil
                        .getSupportedCopyModesFromGivenRemoteSettings(groupCopyModesByVPools);
                if (null == copyMode
                        || (!SupportedCopyModes.ALL.toString().equalsIgnoreCase(copyMode) && !supportedVPoolCopyModes.contains(copyMode))) {
                    _logger.warn(
                            "UnManaged Volume {} is remote protected via {}, hence cannot be associated "
                                    + "with VPool which doesn't contain this mode in at least one of its remote settings. Skipping Ingestion",
                            unManagedVolume.getId(), copyMode);
                    throw IngestionException.exceptions.srdfVolumeRemoteProtectionCopyModeMismatch(unManagedVolume.getLabel(), copyMode);
                }
            }
        } else if (RemoteMirrorObject.Types.SOURCE.toString().equalsIgnoreCase(type)
                &&
                Boolean.parseBoolean(unManagedVolume.getVolumeCharacterstics().get(
                        SupportedVolumeCharacterstics.REMOTE_MIRRORING.toString()))) {
            _logger.warn(
                    "UnManaged Volume {} is remote protected, hence cannot be associated with VPool without remote protection configured. Skipping Ingestion",
                    unManagedVolume.getId());
            throw IngestionException.exceptions.srdfVolumeRemoteProtectionMismatch(unManagedVolume.getLabel());
        }
    }

    /**
     * Verify whether unManagedVolume is a replica of a source volume which is SRDF protected.
     *
     * 1. Verify whether sourceVolume is a UnManagedVolume.
     * 2. If it is UnManagedVolume, then check whether its REMOTE_MIRRORING enabled or not.
     * 3. If it is a ingested Volume, then check whether its personality is set or not.
     *
     * @param unManagedVolume the UnManagedVolume to check
     * @param dbClient a reference to the database client
     * @return true if the UnManagedVolume's parent is SRDF protected
     */
    private static boolean isParentSRDFProtected(UnManagedVolume unManagedVolume, DbClient dbClient) {
        boolean isParentSRDFProtected = false;
        String parentVolumeNativeGuid = PropertySetterUtil.extractValueFromStringSet(
                SupportedVolumeInformation.LOCAL_REPLICA_SOURCE_VOLUME.toString(),
                unManagedVolume.getVolumeInformation());
        if (null != parentVolumeNativeGuid) {
            StringSet umvSet = new StringSet();
            umvSet.add(parentVolumeNativeGuid);
            List<URI> unManagedvolumeList = getUnManagedVolumeUris(umvSet, dbClient);
            if (!unManagedvolumeList.isEmpty()) {
                for (URI unManagedVolumeURI : unManagedvolumeList) {
                    _logger.debug("Found a replica {} source volume in unmanagedVolumes.", unManagedVolume.getId());
                    UnManagedVolume parentUnManagedVolume = dbClient.queryObject(UnManagedVolume.class, unManagedVolumeURI);
                    String remoteMirrorEnabledOnParent = parentUnManagedVolume.getVolumeCharacterstics().get(
                            SupportedVolumeCharacterstics.REMOTE_MIRRORING.toString());
                    isParentSRDFProtected = (null != remoteMirrorEnabledOnParent && Boolean.valueOf(remoteMirrorEnabledOnParent));
                    break;
                }
            } else {
                StringSet ingestedVolumeNativeGuids = getListofVolumeIds(umvSet);
                List<URI> volumeURIs = getVolumeUris(ingestedVolumeNativeGuids, dbClient);
                if (!volumeURIs.isEmpty()) {
                    _logger.debug("Found a ingested volume of a replica {} source", unManagedVolume.getId());
                    List<Volume> volumeList = dbClient.queryObject(Volume.class, volumeURIs);
                    for (Volume volume : volumeList) {
                        isParentSRDFProtected = (null != volume.getPersonality());
                        break;
                    }
                }
            }
        }
        return isParentSRDFProtected;
    }

    /**
     * Checks that the Virtual Pool has a protocols setting that is
     * compatible with the UnManagedVolume. Does not apply to UnManagedVolumes
     * that are not exported.
     *
     * @param vpool the virtual pool requested
     * @param unManagedVolume the unmanaged volume being ingested
     * @param dbClient database client
     * @return true if the virtual pool is compatible with
     *         the unmaanged volume's export's initiators' protocols
     */
    public static boolean checkVPoolValidForExportInitiatorProtocols(
            VirtualPool vpool, UnManagedVolume unManagedVolume, DbClient dbClient) {
        if (unManagedVolume.getInitiatorUris().isEmpty()) {
            _logger.info("unmanaged volume {} has no initiators, so no need to verify vpool protocols",
                    unManagedVolume.getNativeGuid());
            return true;
        }

        _logger.info("checking validity of virtual pool {} protocols for unmanaged volume {}",
                vpool.getLabel(), unManagedVolume.getNativeGuid());
        Set<String> initiatorProtocols = new HashSet<String>();

        for (String initUri : unManagedVolume.getInitiatorUris()) {
            Initiator init = dbClient.queryObject(Initiator.class, URI.create(initUri));
            if (init != null) {
                initiatorProtocols.add(init.getProtocol());
            }
        }

        _logger.info("this unmanaged volume's export's initiators' protocols are {}",
                Joiner.on(",").join(initiatorProtocols));
        _logger.info("the requested virtual pool's protocols are {}",
                Joiner.on(",").join(vpool.getProtocols()));

        boolean atLeastOneProtocolIsSatisfied = false;
        for (String protocol : initiatorProtocols) {
            if (vpool.getProtocols().contains(protocol)) {
                _logger.info("at least one protocol matches between the volume and virtual pool");
                atLeastOneProtocolIsSatisfied = true;
                break;
            }
        }

        if (!atLeastOneProtocolIsSatisfied) {
            _logger.warn("no protocol overlap found between unmanaged volume and "
                    + "virtual pool. ingestion will be skipped.");
        }
        return atLeastOneProtocolIsSatisfied;
    }

    /**
     * Converts a StringSet of UnManagedVolume object native GUIDs to their
     * equivalent block Volume GUIDs.
     *
     * @param targets a set of UnManagedVolume object native GUIDs
     * @return a set of block Volume GUIDs
     */
    public static StringSet getListofVolumeIds(StringSet targets) {
        StringSet targetVolumeIds = new StringSet();
        for (String target : targets) {
            targetVolumeIds.add(target.replace(VolumeIngestionUtil.UNMANAGEDVOLUME, VolumeIngestionUtil.VOLUME));
        }
        return targetVolumeIds;
    }

    /**
     * For a given set of Volume native GUIDs, this method will return a List of
     * URIs for any Volumes that were actually found in the database for that GUID.
     *
     * @param targets a list of Volume native GUIDs to look for
     * @param dbClient a reference to the database client
     * @return a List of Volume URIs
     */
    public static List<URI> getVolumeUris(StringSet targets, DbClient dbClient) {
        List<URI> targetUriList = new ArrayList<URI>();
        for (String targetId : targets) {
            List<URI> targetUris = dbClient.queryByConstraint(AlternateIdConstraint.Factory.getVolumeNativeGuidConstraint(targetId));
            if (null != targetUris && !targetUris.isEmpty()) {
                List<Volume> targetVolumes = dbClient.queryObject(Volume.class, targetUris);
                for (Volume volume : targetVolumes) {
                    if (!volume.getInactive()) {
                        targetUriList.add(volume.getId());
                    } else {
                        _logger.warn("Volume object {} was retrieved from the database but is in an inactive state. "
                                + "there may be a stale Volume column family alternate id index entry present "
                                + "for native guid {}", volume.forDisplay(), targetId);
                    }
                }
            } else {
                _logger.info("Volume not ingested yet {}", targetId);
            }
        }
        return targetUriList;
    }

    /**
     * For a given set of Volume native GUIDs, this method will return a List of
     * URIs for any Volumes that were actually found in the database for that GUID.
     * If a Volume cannot be found in the database, then the IngestionRequestContext
     * will be checked for any Volumes that were created but not saved yet.
     *
     * @param targets a list of Volume native GUIDs to look for
     * @param requestContext the IngestionRequestContext to analyze for newly-created objects
     * @param dbClient a reference to the database client
     * @return a List of BlockObjects for the given native GUIDs
     */
    public static List<BlockObject> getVolumeObjects(StringSet targets, IngestionRequestContext requestContext, DbClient dbClient) {
        List<BlockObject> targetUriList = new ArrayList<BlockObject>();
        for (String targetId : targets) {
            List<URI> targetUris = dbClient.queryByConstraint(AlternateIdConstraint.Factory.getVolumeNativeGuidConstraint(targetId));
            if (null != targetUris && !targetUris.isEmpty()) {
                for (URI targetUri : targetUris) {
                    BlockObject bo = (BlockObject) dbClient.queryObject(targetUri);
                    _logger.info("found volume block object: " + bo);
                    if (null != bo) {
                        if (!bo.getInactive()) {
                            targetUriList.add(bo);
                            break;
                        } else {
                            _logger.warn("BlockObject {} was retrieved from the database but is in an inactive state. "
                                    + "there may be a stale BlockObject column family alternate id index entry present "
                                    + "for native guid {}", bo.forDisplay(), targetId);
                        }
                    }
                }
            } else {
                _logger.info("Volume not ingested yet {}. Checking in the created object map", targetId);
                // check in the created object map
                BlockObject blockObject = requestContext.getRootIngestionRequestContext().findCreatedBlockObject(targetId);
                if (blockObject != null) {
                    _logger.info("Found the volume in the created object map");
                    targetUriList.add(blockObject);
                }
            }
        }
        return targetUriList;
    }

    /**
     * For a given set of mirror Volume native GUIDs, this method will return a List of
     * URIs for any mirror Volumes that were actually found in the database for that GUID.
     * If a mirror Volume cannot be found in the database, then the IngestionRequestContext
     * will be checked for any mirror Volumes that were created but not saved yet.
     *
     * @param targets a list of mirror Volume native GUIDs to look for
     * @param requestContext the IngestionRequestContext to analyze for newly-created objects
     * @param dbClient a reference to the database client
     * @return a List of BlockObjects for the given native GUIDs
     */
    public static List<BlockObject> getMirrorObjects(StringSet targets, IngestionRequestContext requestContext, DbClient dbClient) {
        List<BlockObject> targetUriList = new ArrayList<BlockObject>();
        for (String targetId : targets) {
            List<URI> targetUris = dbClient.queryByConstraint(AlternateIdConstraint.Factory.getMirrorByNativeGuid(targetId));
            if (null != targetUris && !targetUris.isEmpty()) {
                BlockObject bo = (BlockObject) dbClient.queryObject(targetUris.get(0));
                _logger.info("found mirror block object: " + bo);
                if (null != bo) {
                    targetUriList.add(bo);
                }
            } else {
                _logger.info("Mirror not ingested yet {}", targetId);
                // check in the created object map
                BlockObject blockObject = requestContext.getRootIngestionRequestContext().findCreatedBlockObject(targetId);
                if (blockObject != null) {
                    _logger.info("Found the mirror in the created object map");
                    targetUriList.add(blockObject);
                }
            }
        }
        return targetUriList;
    }

    /**
     * For a given set of snapshot Volume native GUIDs, this method will return a List of
     * URIs for any snapshot Volumes that were actually found in the database for that GUID.
     * If a snapshot Volume cannot be found in the database, then the IngestionRequestContext
     * will be checked for any snapshot Volumes that were created but not saved yet.
     *
     * @param targets a list of snapshot Volume native GUIDs to look for
     * @param requestContext the IngestionRequestContext to analyze for newly-created objects
     * @param dbClient a reference to the database client
     * @return a List of BlockObjects for the given native GUIDs
     */
    public static List<BlockObject> getSnapObjects(StringSet targets, IngestionRequestContext requestContext, DbClient dbClient) {
        List<BlockObject> targetUriList = new ArrayList<BlockObject>();
        for (String targetId : targets) {
            List<URI> targetUris = dbClient.queryByConstraint(AlternateIdConstraint.Factory.getBlockSnapshotsByNativeGuid(targetId));
            if (null != targetUris && !targetUris.isEmpty()) {
                BlockObject bo = (BlockObject) dbClient.queryObject(targetUris.get(0));
                _logger.info("found snapshot block object: " + bo);
                if (null != bo) {
                    targetUriList.add(bo);
                }
            } else {
                _logger.info("Snap not ingested yet {}", targetId);
                // check in the created object map
                BlockObject blockObject = requestContext.getRootIngestionRequestContext().findCreatedBlockObject(targetId);
                if (blockObject != null) {
                    _logger.info("Found the snap in the created object map");
                    targetUriList.add(blockObject);
                }
            }
        }
        return targetUriList;
    }

    /**
     * Converts a List of URIs to a StringSet of URI strings.
     *
     * @param targetUris the list of URIs to convert
     * @return a StringSet of URI strings
     */
    public static StringSet convertUrisToStrings(List<URI> targetUris) {
        StringSet targetVolumeUris = new StringSet();
        for (URI uri : targetUris) {
            targetVolumeUris.add(uri.toString());
        }
        return targetVolumeUris;
    }

    /**
     * Filters a StringSet of target UnManagedVolume native GUIDs to contain
     * only those UnManagedVolumes that exist in the database still.
     *
     * @param targets a set of UnManagedVolume native GUIDs
     * @param dbClient a reference to the database client
     * @return a filtered List of URIs
     */
    public static List<URI> getUnManagedVolumeUris(StringSet targets, DbClient dbClient) {
        List<URI> targetUriList = new ArrayList<URI>();
        for (String targetId : targets) {
            List<URI> targetUris = dbClient.queryByConstraint(AlternateIdConstraint.Factory.getVolumeInfoNativeIdConstraint(targetId));
            if (null != targetUris && !targetUris.isEmpty()) {
                targetUriList.addAll(targetUris);
            } else {
                // FIXME: if it's null, doesn't this mean the UnManagedVolume is, in fact, ingested??
                // ...confused by this method (NBB 2/6/2016)
                _logger.info("UnManagedVolume not yet ingested {}", targetId);
            }
        }
        return targetUriList;
    }

    /**
     * Check to see if an unmanaged resource is RecoverPoint enabled
     * (part of a RecoverPoint Consistency Group) or not.
     *
     * @param unManagedVolume the UnManagedVolume to check
     * @return true if it's part of a RecoverPoint Consistency Group
     */
    public static boolean checkUnManagedResourceIsRecoverPointEnabled(UnManagedVolume unManagedVolume) {
        StringMap unManagedVolumeCharacteristics = unManagedVolume.getVolumeCharacterstics();
        String isRecoverPointEnabled = unManagedVolumeCharacteristics
                .get(SupportedVolumeCharacterstics.IS_RECOVERPOINT_ENABLED.toString());
        if (null != isRecoverPointEnabled && Boolean.parseBoolean(isRecoverPointEnabled)) {
            return true;
        }

        return false;
    }

    /**
     * Checks whether RP is protecting any VPLEX volumes or not.
     *
     * 1. Get the ProtectionSet from the context for the given unmanagedvolume.
     * 2. Check every volume in the protectionset.
     * 3. If the volume belongs to a VPLEX or not.
     * 4. If it belongs to VPLEX break the loop and return true.
     *
     * @param umv - unmanaged volume to ingest
     * @param requestContext - current unmanaged volume context.
     * @param dbClient - dbclient reference.
     *
     */
    public static boolean isRPProtectingVplexVolumes(UnManagedVolume umv, IngestionRequestContext requestContext, DbClient dbClient) {
        VolumeIngestionContext context = requestContext.getVolumeContext(umv.getNativeGuid());
        boolean isRPProtectingVplexVolumes = false;
        // We expect RP Context to validate Vplex volumes protected by RP or not.
        if (context instanceof RecoverPointVolumeIngestionContext) {
            RecoverPointVolumeIngestionContext rpContext = (RecoverPointVolumeIngestionContext) context;
            ProtectionSet pset = rpContext.getManagedProtectionSet();

            if (pset == null) {
                return isRPProtectingVplexVolumes;
            }

            // Iterate thru protection set volumes.
            for (String volumeIdStr : pset.getVolumes()) {
                for (Set<DataObject> dataObjList : rpContext.getDataObjectsToBeUpdatedMap().values()) {
                    for (DataObject dataObj : dataObjList) {
                        if (URIUtil.identical(dataObj.getId(), URI.create(volumeIdStr))) {
                            Volume volume = (Volume) dataObj;
                            if (volume.isVPlexVolume(dbClient)) {
                                isRPProtectingVplexVolumes = true;
                                break;
                            }
                        }
                    }
                }
            }
        } else if (context instanceof BlockVolumeIngestionContext) {
            // In this case, the last volume ingested was a replica, so we need to fish out RP information slightly differently.
            Set<DataObject> updatedObjects = requestContext.getDataObjectsToBeUpdatedMap().get(umv.getNativeGuid());
            if (updatedObjects != null && !updatedObjects.isEmpty()) {
                for (DataObject dataObj : updatedObjects) {
                    if (dataObj instanceof Volume) {
                        Volume volume = (Volume) dataObj;
                        if (volume.isVPlexVolume(dbClient)) {
                            isRPProtectingVplexVolumes = true;
                            break;
                        }
                    }
                }
            }

        } else {
            _logger.error("Context found of type: {} invalid", context.getClass().toString());
        }

        return isRPProtectingVplexVolumes;
    }

    /**
     * Check to see if an unmanaged resource is exported to anything non-RP.
     * Note: Being exported to RP doesn't not mean this returns false. It's a way
     * to check if something is exported to something other than RP, regardless of RP.
     *
     * @param unManagedVolume unmanaged volume
     * @return true if object is exported to something non-RP
     */
    public static boolean checkUnManagedResourceIsNonRPExported(UnManagedVolume unManagedVolume) {
        StringMap unManagedVolumeCharacteristics = unManagedVolume.getVolumeCharacterstics();
        String isNonRPExported = unManagedVolumeCharacteristics
                .get(SupportedVolumeCharacterstics.IS_NONRP_EXPORTED.toString());
        if (null != isNonRPExported && Boolean.parseBoolean(isNonRPExported)) {
            return true;
        }

        return false;
    }

    /**
     * Check if the unmanaged volume under RP control is in an image access state that indicates that
     * the volume is "locked-down" in a target operation.
     *
     * @param unManagedVolume unmanaged volume
     * @return true if the volume is in an image access mode. Several modes qualify.
     */
    public static boolean isRPUnManagedVolumeInImageAccessState(UnManagedVolume unManagedVolume) {
        boolean isImageAccessState = false;
        String rpAccessState = PropertySetterUtil.extractValueFromStringSet(SupportedVolumeInformation.RP_ACCESS_STATE.toString(),
                unManagedVolume.getVolumeInformation());
        if (GetCopyAccessStateResponse.ENABLING_LOGGED_ACCESS.name().equals(rpAccessState) ||
                GetCopyAccessStateResponse.ENABLING_VIRTUAL_ACCESS.name().equals(rpAccessState) ||
                GetCopyAccessStateResponse.LOGGED_ACCESS.name().equals(rpAccessState) ||
                GetCopyAccessStateResponse.LOGGED_ACCESS_ROLL_COMPLETE.name().equals(rpAccessState) ||
                GetCopyAccessStateResponse.VIRTUAL_ACCESS.equals(rpAccessState) ||
                GetCopyAccessStateResponse.VIRTUAL_ACCESS_ROLLING_IMAGE.name().equals(rpAccessState) ||
                GetCopyAccessStateResponse.DIRECT_ACCESS.name().equals(rpAccessState)) {
            isImageAccessState = true;
        }
        return isImageAccessState;
    }

    /**
     * Checks if the unmanaged resource is part of a consistency group
     *
     * @param unManagedVolume - the resource being examined
     * @return boolean indicating if the resource is part of a consistency group
     */
    public static boolean checkUnManagedResourceAddedToConsistencyGroup(UnManagedVolume unManagedVolume) {
        _logger.info("Determining if the unmanaged volume {} belongs to an unmanaged consistency group", unManagedVolume.getLabel());
        StringMap unManagedVolumeCharacteristics = unManagedVolume.getVolumeCharacterstics();
        String isVolumeAddedToConsistencyGroup = unManagedVolumeCharacteristics
                .get(SupportedVolumeCharacterstics.IS_VOLUME_ADDED_TO_CONSISTENCYGROUP.toString());
        if (null != isVolumeAddedToConsistencyGroup && Boolean.parseBoolean(isVolumeAddedToConsistencyGroup)) {
            _logger.info("The unmanaged volume {} belongs to an unmanaged consistency group", unManagedVolume.getLabel());
            return true;
        }
        _logger.info("The unmanaged volume {} does not belong to an unmanaged consistency group", unManagedVolume.getLabel());
        return false;
    }

    /**
     * Once a volume has been ingested it is moved from the list of unmanaged volumes
     * to the list of managed volumes within the unmanaged consistency group object
     *
     * @param unManagedCG - the unmanaged consistency group object
     * @param unManagedVolume - the unmanaged volume
     * @param blockObject - the ingested volume
     * @return integer indicating the number of unmanaged volumes still remaining in the
     *         unmanaged consistency group
     */
    public static void updateVolumeInUnManagedConsistencyGroup(UnManagedConsistencyGroup unManagedCG, UnManagedVolume unManagedVolume,
            BlockObject blockObject) {
        // ensure that unmanaged cg contains the unmanaged volume
        if (unManagedCG.getUnManagedVolumesMap().containsKey(unManagedVolume.getNativeGuid())) {
            // add the volume to the list of managed volumes
            unManagedCG.getManagedVolumesMap().put(blockObject.getNativeGuid(), blockObject.getId().toString());
            _logger.info("Added volume {} to the managed volume list of unmanaged consistency group {}", blockObject.getLabel(),
                    unManagedCG.getLabel());
            // remove the unmanaged volume from the list of unmanaged volumes
            unManagedCG.getUnManagedVolumesMap().remove(unManagedVolume.getNativeGuid());
            _logger.info("Removed volume {} from the unmanaged volume list of unmanaged consistency group {}", unManagedVolume.getLabel(),
                    unManagedCG.getLabel());
            // decrement the number of volumes to be ingested
        } else {
            _logger.info("Volume {} was not in the unmanaged volume list of unmanaged consistency group {}", unManagedVolume.getLabel(),
                    unManagedCG.getLabel());
        }
    }

    /**
     * Determines if all the unmanaged volumes within an unmanaged consistency group
     * have been ingested
     *
     * @param unManagedCG - the unmanaged consistency group object
     * @return boolean indicating if the map of unmanaged volumes is empty
     */
    public static boolean allVolumesInUnamangedCGIngested(UnManagedConsistencyGroup unManagedCG) {
        return unManagedCG.getUnManagedVolumesMap().isEmpty();
    }

    /**
     * Checks if the given UnManagedVolume has replicas or is a VPLEX volume
     * (i.e., containing VPLEX backend volumes).
     *
     * @param unManagedVolume the UnManagedVolume to check
     * @return true if the UnManagedVolume has replicas
     */
    public static boolean checkUnManagedVolumeHasReplicas(UnManagedVolume unManagedVolume) {
        StringMap unManagedVolumeCharacteristics = unManagedVolume.getVolumeCharacterstics();
        String volumeHasReplicas = unManagedVolumeCharacteristics
                .get(SupportedVolumeCharacterstics.HAS_REPLICAS.toString());
        String volumeHasRemoteReplicas = unManagedVolumeCharacteristics
                .get(SupportedVolumeCharacterstics.REMOTE_MIRRORING.toString());
        String isVplexVolume = unManagedVolumeCharacteristics
                .get(SupportedVolumeCharacterstics.IS_VPLEX_VOLUME.toString());
        if (null != volumeHasReplicas
                && Boolean.parseBoolean(volumeHasReplicas)
                || (null != volumeHasRemoteReplicas && Boolean
                        .parseBoolean(volumeHasRemoteReplicas))
                || (null != isVplexVolume && Boolean
                        .parseBoolean(isVplexVolume))) {
            return true;
        }
        return false;
    }

    /**
     * Checks if the given UnManagedVolume is already exported to Host.
     *
     * @param unManagedVolume the UnManagedVolume to check
     */
    public static boolean checkUnManagedResourceAlreadyExported(
            UnManagedVolume unManagedVolume) {
        StringMap unManagedVolumeCharacteristics = unManagedVolume.getVolumeCharacterstics();
        String isVolumeExported = unManagedVolumeCharacteristics
                .get(SupportedVolumeCharacterstics.IS_VOLUME_EXPORTED.toString());
        if (checkUnManagedResourceIsRecoverPointEnabled(unManagedVolume)) {
            isVolumeExported = unManagedVolumeCharacteristics
                    .get(SupportedVolumeCharacterstics.IS_NONRP_EXPORTED.toString());
        }
        if (null != isVolumeExported && Boolean.parseBoolean(isVolumeExported)) {
            return true;
        }
        return false;
    }

    /**
     * Checks if the given UnManagedVolume has been marked ingestable.
     *
     * @param unManagedVolume the UnManagedVolume to check
     */
    public static void checkUnManagedResourceIngestable(UnManagedVolume unManagedVolume) {
        StringMap unManagedVolumeCharacteristics = unManagedVolume
                .getVolumeCharacterstics();
        String isVolumeIngestable = unManagedVolumeCharacteristics
                .get(SupportedVolumeCharacterstics.IS_INGESTABLE.toString());
        if (null != isVolumeIngestable && Boolean.parseBoolean(isVolumeIngestable)) {
            return;
        }

        String reason = unManagedVolumeCharacteristics
                .get(SupportedVolumeCharacterstics.IS_NOT_INGESTABLE_REASON.toString());
        if (reason == null || reason.isEmpty()) {
            reason = "Unknown";
        }

        throw IngestionException.exceptions.unmanagedVolumeNotIngestable(unManagedVolume.getLabel(), reason);
    }

    /**
     * Checks for the presence of a WWN on a given UnManagedVolume
     * if the volume is exported. If the WWN is not present, an
     * IngestionException will be thrown.
     *
     * @param unManagedVolume the UnMangedVolume to check
     * @throws IngestionException
     */
    public static void checkUnManagedResourceExportWwnPresent(
            UnManagedVolume unManagedVolume) throws IngestionException {
        StringMap unManagedVolumeCharacteristics = unManagedVolume.getVolumeCharacterstics();
        String isVolumeExported = unManagedVolumeCharacteristics
                .get(SupportedVolumeCharacterstics.IS_VOLUME_EXPORTED.toString());
        if (null != isVolumeExported && Boolean.parseBoolean(isVolumeExported)) {
            String wwn = unManagedVolume.getWwn();
            if (null == wwn || wwn.isEmpty()) {
                throw IngestionException.exceptions
                        .exportedVolumeIsMissingWwn(unManagedVolume.getLabel());
            }
        }
    }

    /**
     * Validates Host IO limits for the given UnManagedVolume against
     * the given VirtualPool.
     *
     * @param vpool the VirtualPool to validate against
     * @param unManagedVolume the UnManagedVolume to check
     * @return true if the VirtualPool's Host IO limits are suitable for the UnManagedVolume
     */
    public static boolean validateHostIOLimit(VirtualPool vpool, UnManagedVolume unManagedVolume) {
        Set<String> hostIoBws = PropertySetterUtil.extractValuesFromStringSet(
                SupportedVolumeInformation.EMC_MAXIMUM_IO_BANDWIDTH.name(), unManagedVolume.getVolumeInformation());
        Set<String> hostIoPs = PropertySetterUtil.extractValuesFromStringSet(SupportedVolumeInformation.EMC_MAXIMUM_IOPS.name(),
                unManagedVolume.getVolumeInformation());
        String vPoolBw = "0";
        if (vpool.getHostIOLimitBandwidth() != null) {
            vPoolBw = String.valueOf(vpool.getHostIOLimitBandwidth());
        }

        String vPoolIoPs = "0";
        if (vpool.getHostIOLimitIOPs() != null) {
            vPoolIoPs = String.valueOf(vpool.getHostIOLimitIOPs());
        }
        _logger.info("Volume's bw {} and iops {} --> Virtual Pool's bw {} and iops {}", new Object[] { Joiner.on(",").join(hostIoBws),
                Joiner.on(",").join(hostIoPs), vPoolBw, vPoolIoPs });
        // values [0,2000] hence if size is > 1 then we need to explicitly return false, if vpool value is 0.
        if (hostIoBws.size() > 1) {
            if ("0".equalsIgnoreCase(vPoolBw)) {
                return false;
            }
        }

        if (hostIoPs.size() > 1) {
            if ("0".equalsIgnoreCase(vPoolIoPs)) {
                return false;
            }
        }
        return hostIoBws.contains(vPoolBw) && hostIoPs.contains(vPoolIoPs);

    }

    /**
     * Check if valid storage Pool is associated with UnManaged Volume Uri is valid.
     *
     * @param unManagedVolumeInformation the UnManagedVolume's information collection
     * @param dbClient a reference to the database client
     * @param unManagedVolumeUri the URI of the UnManagedVolume
     * @throws APIException
     */
    private static void checkStoragePoolValidForUnManagedVolumeUri(
            StringSetMap unManagedVolumeInformation, DbClient dbClient,
            URI unManagedVolumeUri) throws APIException {
        String pool = PropertySetterUtil.extractValueFromStringSet(VolumeObjectProperties.STORAGE_POOL.toString(),
                unManagedVolumeInformation);
        if (null == pool) {
            throw APIException.internalServerErrors.storagePoolError("", VOLUME_TEXT, unManagedVolumeUri);
        }
        StoragePool poolObj = dbClient.queryObject(StoragePool.class, URI.create(pool));
        if (null == poolObj) {
            throw APIException.internalServerErrors.noStoragePool(pool, VOLUME_TEXT, unManagedVolumeUri);
        }
    }

    /**
     * Get Supported vPool from PreExistingVolume Storage Pools. Verify if the given vPool is part of
     * the supported vPool List.
     *
     * @param preExistVolumeInformation the pre-existing volume information collection
     * @param unManagedVolume the UnManagedVolume to check
     * @param vpoolUri the URI of the VirtualPool to check
     * @param dbClient a reference to the database client
     */
    private static void checkVPoolValidForGivenUnManagedVolumeUris(
            StringSetMap preExistVolumeInformation, UnManagedVolume unManagedVolume,
            URI vpoolUri, DbClient dbClient) {
        StringSet supportedVPoolUris = unManagedVolume.getSupportedVpoolUris();
        String spoolName = "(not set)";
        if (unManagedVolume.getStoragePoolUri() != null) {
            StoragePool spool = dbClient.queryObject(StoragePool.class, unManagedVolume.getStoragePoolUri());
            if (spool != null) {
                spoolName = spool.getLabel();
            }
        }
        if (null == supportedVPoolUris || supportedVPoolUris.isEmpty()) {
            if (isVplexVolume(unManagedVolume)) {
                throw APIException.internalServerErrors.noMatchingVplexVirtualPool(
                        unManagedVolume.getLabel(), unManagedVolume.getId());
            }

            throw APIException.internalServerErrors.storagePoolNotMatchingVirtualPoolNicer(
                    spoolName, VOLUME_TEXT, unManagedVolume.getLabel());
        }
        VirtualPool vpool = dbClient.queryObject(VirtualPool.class, vpoolUri);
        if (!supportedVPoolUris.contains(vpoolUri.toString())) {
            String vpoolName = vpool != null ? vpool.getLabel() : vpoolUri.toString();
            List<VirtualPool> supportedVpools = dbClient.queryObject(
                    VirtualPool.class, Collections2.transform(supportedVPoolUris,
                            CommonTransformerFunctions.FCTN_STRING_TO_URI));
            String vpoolsString = null;
            if (supportedVpools != null && !supportedVpools.isEmpty()) {
                List<String> supportedVpoolNames = new ArrayList<String>();
                for (VirtualPool svp : supportedVpools) {
                    supportedVpoolNames.add(svp.getLabel());
                }
                vpoolsString = Joiner.on(", ").join(supportedVpoolNames);
            } else {
                vpoolsString = Joiner.on(", ").join(supportedVPoolUris);
            }
            throw APIException.internalServerErrors.virtualPoolNotMatchingStoragePoolNicer(
                    vpoolName, VOLUME_TEXT, unManagedVolume.getLabel(), vpoolsString);
        }
        // Check to ensure this isn't a non-RP protected unmanaged volume attempted to be ingested
        // by an RP source vpool.
        if (VirtualPool.vPoolSpecifiesProtection(vpool)) {
            String value = unManagedVolume.getVolumeCharacterstics().get(
                    UnManagedVolume.SupportedVolumeCharacterstics.IS_RECOVERPOINT_ENABLED.toString());
            if (FALSE.equalsIgnoreCase(value)) {
                throw APIException.internalServerErrors.ingestNotAllowedNonRPVolume(vpool.getLabel(), unManagedVolume.getLabel());
            }
        }
    }

    /**
     * Gets and verifies the VirtualPool passed in the request.
     *
     * @param project the Project in the request
     * @param vPoolUri the VirutalPool URI
     * @param permissionsHelper the security permissions helper
     * @param dbClient a reference to the database client
     * @return the VirtualPool if it's valid
     */
    public static VirtualPool getVirtualPoolForVolumeCreateRequest(
            Project project, URI vPoolUri, PermissionsHelper permissionsHelper,
            DbClient dbClient) {
        ArgValidator.checkUri(vPoolUri);
        VirtualPool vPool = dbClient.queryObject(VirtualPool.class, vPoolUri);
        ArgValidator.checkEntity(vPool, vPoolUri, false);
        if (!VirtualPool.Type.block.name().equals(vPool.getType())) {
            throw APIException.badRequests.virtualPoolNotForFileBlockStorage(VirtualPool.Type.block.name());
        }
        return vPool;
    }

    /**
     * Gets and verifies that the VirtualArray passed in the request is accessible to the tenant.
     *
     * @param project the Project in the request
     * @param varrayUri the VirtualArray URI to check
     * @param permissionsHelper the security permissions helper
     * @param dbClient a reference to the database client
     * @return the VirtualArray if it's valid
     */
    public static VirtualArray getVirtualArrayForVolumeCreateRequest(
            Project project, URI varrayUri, PermissionsHelper permissionsHelper,
            DbClient dbClient) {
        VirtualArray neighborhood = dbClient.queryObject(VirtualArray.class,
                varrayUri);
        ArgValidator.checkEntity(neighborhood, varrayUri, false);
        permissionsHelper.checkTenantHasAccessToVirtualArray(project.getTenantOrg().getURI(), neighborhood);
        return neighborhood;
    }

    /**
     * Returns a Set of copy modes from a map of VirtualPools to copy modes
     *
     * @param groupCopyModesByVPools a Map of VirtualPool names to copy modes
     * @return a Set of all copy modes in the given Map
     */
    public static Set<String> getSupportedCopyModesFromGivenRemoteSettings(Map<String, List<String>> groupCopyModesByVPools) {
        Set<String> copyModes = new HashSet<String>();
        if (groupCopyModesByVPools != null) {
            for (Entry<String, List<String>> entry : groupCopyModesByVPools.entrySet()) {
                copyModes.addAll(entry.getValue());
            }
        }
        return copyModes;
    }

    /**
     * The total capacity of of the given URI List of UnManagedVolumes.
     *
     * @param dbClient a reference to the database client
     * @param unManagedVolumeUris a List of UnManagedVolume URIs to add up
     * @return the total capacity of the given UnManagedVolumes
     */
    public static long getTotalUnManagedVolumeCapacity(DbClient dbClient,
            List<URI> unManagedVolumeUris) {
        BigInteger totalUnManagedVolumeCapacity = new BigInteger("0");
        try {
            Iterator<UnManagedVolume> unManagedVolumes = dbClient.queryIterativeObjects(UnManagedVolume.class,
                    unManagedVolumeUris);

            while (unManagedVolumes.hasNext()) {
                UnManagedVolume unManagedVolume = unManagedVolumes.next();
                StringSetMap unManagedVolumeInfo = unManagedVolume
                        .getVolumeInformation();
                if (null == unManagedVolumeInfo) {
                    continue;
                }
                String unManagedVolumeCapacity = PropertySetterUtil
                        .extractValueFromStringSet(SupportedVolumeInformation.PROVISIONED_CAPACITY
                                .toString(), unManagedVolumeInfo);
                if (null != unManagedVolumeCapacity && !unManagedVolumeCapacity.isEmpty()) {
                    totalUnManagedVolumeCapacity = totalUnManagedVolumeCapacity
                            .add(new BigInteger(unManagedVolumeCapacity));
                }

            }
        } catch (Exception e) {
            throw APIException.internalServerErrors.capacityComputationFailed();
        }
        return totalUnManagedVolumeCapacity.longValue();
    }

    /**
     * Returns true if the BlockObject represents a VPLEX virtual volume.
     *
     * @param blockObject the BlockObject to check
     * @param dbClient a reference to the database client
     * @return true if the block object is a VPLEX virtual volume
     */
    public static boolean isVplexVolume(BlockObject blockObject, DbClient dbClient) {
        UnManagedVolume volume = getUnManagedVolumeForBlockObject(blockObject, dbClient);
        if (null == volume) {
            String message = "could not locate an UnManagedVolume for BlockObject " + blockObject.getLabel()
                    + ". This means that the volume was marked ingested before all its replicas were ingested.";
            _logger.error(message);
            throw IngestionException.exceptions.generalException(message);
        }
        return isVplexVolume(volume);
    }

    /**
     * Returns true if the UnManagedVolume represents a RP/VPLEX virtual volume.
     * That is, a VPLEX volume that is RecoverPoint-enabled.
     *
     * @param unManagedVolume the UnManagedVolume in question
     * @return true if the volume is an RP/VPLEX virtual volume
     */
    public static boolean isRpVplexVolume(UnManagedVolume unManagedVolume) {
        return isVplexVolume(unManagedVolume) && checkUnManagedResourceIsRecoverPointEnabled(unManagedVolume);
    }

    /**
     * Returns true if the UnManagedVolume represents a VPLEX virtual volume.
     *
     * @param volume the UnManagedVolume in question
     * @return true if the volume is a VPLEX virtual volume
     */
    public static boolean isVplexVolume(UnManagedVolume volume) {
        if (null == volume || null == volume.getVolumeCharacterstics()) {
            return false;
        }

        String status = volume.getVolumeCharacterstics()
                .get(SupportedVolumeCharacterstics.IS_VPLEX_VOLUME.toString());
        return TRUE.equals(status);
    }

    /**
     * Returns true if the BlockObject represents a VPLEX backend volume.
     *
     * @param blockObject the BlockObject to check
     * @param dbClient a reference to the database client
     * @return true if the block object is a VPLEX backend volume
     */
    public static boolean isVplexBackendVolume(BlockObject blockObject, DbClient dbClient) {
        UnManagedVolume volume = getUnManagedVolumeForBlockObject(blockObject, dbClient);
        if (null == volume) {
            String message = "could not locate an UnManagedVolume for BlockObject " + blockObject.getLabel()
                    + ". This means that the volume was marked ingested before all its replicas were ingested.";
            _logger.error(message);
            throw IngestionException.exceptions.generalException(message);
        }
        return isVplexBackendVolume(volume);
    }

    /**
     * Returns true if the UnManagedVolume represents a VPLEX backend volume.
     *
     * @param volume the UnManagedVolume in question
     * @return true if the volume is a VPLEX backend volume
     */
    public static boolean isVplexBackendVolume(UnManagedVolume volume) {
        if (null == volume || null == volume.getVolumeCharacterstics()) {
            return false;
        }

        String status = volume.getVolumeCharacterstics()
                .get(SupportedVolumeCharacterstics.IS_VPLEX_BACKEND_VOLUME.toString());
        return TRUE.equals(status);
    }

    /**
     * Returns an UnManagedVolume object if the blockObject has an UnManagedVolume.
     * Otherwise, returns null;
     *
     * @param blockObject the block object to check
     * @param dbClient a reference to the database client
     * @return a UnManagedVolume object
     */
    public static UnManagedVolume getUnManagedVolumeForBlockObject(BlockObject blockObject, DbClient dbClient) {
        String unmanagedVolumeGUID = blockObject.getNativeGuid().replace(VOLUME, UNMANAGEDVOLUME);
        URIQueryResultList umvUriList = new URIQueryResultList();
        dbClient.queryByConstraint(AlternateIdConstraint.Factory.getVolumeInfoNativeIdConstraint(unmanagedVolumeGUID), umvUriList);
        while (umvUriList.iterator().hasNext()) {
            URI umvUri = (umvUriList.iterator().next());
            UnManagedVolume umVolume = dbClient.queryObject(UnManagedVolume.class, umvUri);
            _logger.info("block object {} is UnManagedVolume {}", blockObject.getLabel(), umVolume.getId());
            return umVolume;
        }

        return null;
    }

    /**
     * Returns true if the given UnManagedVolume is a snapshot.
     *
     * @param volume the UnManagedVolume to check
     * @return true if the given UnManagedVolume is a snapshot
     */
    public static boolean isSnapshot(UnManagedVolume volume) {
        if (null == volume.getVolumeCharacterstics()) {
            return false;
        }

        String status = volume.getVolumeCharacterstics()
                .get(SupportedVolumeCharacterstics.IS_SNAP_SHOT.toString());
        return TRUE.equals(status);
    }

    /**
     * Returns true if the given UnManagedVolume is a mirror.
     *
     * @param volume the UnManagedVolume to check
     * @return true if the given UnManagedVolume is a mirror
     */
    public static boolean isMirror(UnManagedVolume volume) {
        if (null == volume.getVolumeCharacterstics()) {
            return false;
        }

        String status = volume.getVolumeCharacterstics()
                .get(SupportedVolumeCharacterstics.IS_LOCAL_MIRROR.toString());
        return TRUE.equals(status);
    }

    /**
     * Returns true if the given UnManagedVolume is a full copy (clone).
     *
     * @param volume the UnManagedVolume to check
     * @return true if the given UnManagedVolume is a full copy (clone)
     */
    public static boolean isFullCopy(UnManagedVolume volume) {
        if (null == volume.getVolumeCharacterstics()) {
            return false;
        }

        String status = volume.getVolumeCharacterstics()
                .get(SupportedVolumeCharacterstics.IS_FULL_COPY.toString());
        return TRUE.equals(status);
    }

    /**
     * Determines if the varray specified in the ingestion request is valid for
     * the volume being ingested. Principally applies to VPLEX volumes, which
     * can reside on one or both cluster of the VPLEX system, where each cluster
     * is in a different virtual array.
     *
     * @param unmanagedVolume the UnManagedVolume to check
     * @param varrayURI the VirtualArray to check
     * @param clusterIdToNameMap a Map of VPLEX cluster ID strings to their names
     * @param varrayToClusterIdMap a Map of varrays to VPLEX cluster IDs
     * @param dbClient a reference to the database client
     * @throws IngestionException
     */
    public static void checkValidVarrayForUnmanagedVolume(UnManagedVolume unmanagedVolume, URI varrayURI,
            Map<String, String> clusterIdToNameMap, Map<String, String> varrayToClusterIdMap, DbClient dbClient)
            throws IngestionException {
        if (isVplexVolume(unmanagedVolume)) {
            StringSet unmanagedVolumeClusters = unmanagedVolume.getVolumeInformation().get(
                    SupportedVolumeInformation.VPLEX_CLUSTER_IDS.toString());
            if (unmanagedVolumeClusters == null) {
                String reason = "Unmanaged VPLEX volume has no cluster info";
                _logger.error(reason);
                throw IngestionException.exceptions.varrayIsInvalidForVplexVolume(unmanagedVolume.getLabel(), reason);
            }

            String varrayClusterId = varrayToClusterIdMap.get(varrayURI.toString());
            if (null == varrayClusterId) {
                varrayClusterId = ConnectivityUtil.getVplexClusterForVarray(varrayURI, unmanagedVolume.getStorageSystemUri(), dbClient);
                varrayToClusterIdMap.put(varrayURI.toString(), varrayClusterId);
                _logger.debug("added {} to varrayToClusterIdMap cache", varrayClusterId);
            }

            if (varrayClusterId.equals(ConnectivityUtil.CLUSTER_UNKNOWN)) {
                String reason = "Virtual Array is not associated with either cluster of the VPLEX";
                _logger.error(reason);
                throw IngestionException.exceptions.varrayIsInvalidForVplexVolume(unmanagedVolume.getLabel(), reason);
            }

            String varrayClusterName = clusterIdToNameMap.get(varrayClusterId);
            if (null == varrayClusterName) {
                varrayClusterName = VPlexControllerUtils.getClusterNameForId(
                        varrayClusterId, unmanagedVolume.getStorageSystemUri(), dbClient);
                clusterIdToNameMap.put(varrayClusterId, varrayClusterName);
                _logger.debug("added {} to clusterIdToNameMap cache", varrayClusterName);
            }

            if (null == varrayClusterName) {
                String reason = "Couldn't find VPLEX cluster name for cluster id " + varrayClusterId;
                _logger.error(reason);
                throw IngestionException.exceptions.varrayIsInvalidForVplexVolume(unmanagedVolume.getLabel(), reason);
            }
            if (!unmanagedVolumeClusters.contains(varrayClusterName)) {
                String reason = "volume is available on cluster " + unmanagedVolumeClusters
                        + ", but the varray is only connected to " + varrayClusterName;
                _logger.error(reason);
                throw IngestionException.exceptions.varrayIsInvalidForVplexVolume(unmanagedVolume.getLabel(), reason);
            }
        }
    }

    /**
     * Checks whether an UnManagedVolume is inactive.
     *
     * @param unManagedVolume the unmanaged volume to check
     * @throws IngestionException
     */
    public static void checkUnmanagedVolumeInactive(UnManagedVolume unManagedVolume) throws IngestionException {
        if (unManagedVolume.getInactive()) {
            _logger.warn("UnManaged Volume {} is in inactive state. Skipping Ingestion", unManagedVolume.getLabel());
            throw IngestionException.exceptions.unmanagedVolumeIsInactive(unManagedVolume.getLabel());
        }
    }

    /**
     * Checks if an UnManagedVolume is partially discovered.
     *
     * @param unManagedVolume the UnManagedVolume to check
     * @param unManagedVolumeUri the URI of the UnManagedVolume
     * @throws IngestionException
     */
    public static void checkUnmanagedVolumePartiallyDiscovered(
            UnManagedVolume unManagedVolume, URI unManagedVolumeUri)
            throws IngestionException {

        if (null == unManagedVolume
                || null == unManagedVolume.getVolumeCharacterstics()
                || null == unManagedVolume.getVolumeInformation()) {

            _logger.warn("UnManaged Volume {} partially discovered, hence not enough "
                    + "information available to validate neither "
                    + "virtualPool nor other criterias.  Skipping Ingestion",
                    unManagedVolumeUri);

            throw IngestionException.exceptions
                    .unmanagedVolumePartiallyIngested(unManagedVolumeUri.toString());
        }
    }

    /**
     * Returns a BlockConsistencyGroup URI. If an existing group matches
     * the name, project, tenant, and varray, then it can be re-used.
     * Otherwise, a new BlockConsistencyGroup will be created in ViPR.
     *
     * @param unManagedVolume the unmanaged virtual volume object
     * @param vpool the VirtualPool for the Volume
     * @param project the Project
     * @param tenant the Tenant
     * @param varrayUri the VirtualArray URI
     * @param _dbClient the ViPR database client
     * @return a BlockConsistencyGroup, or null if none could be found or created
     */
    public static BlockConsistencyGroup getVplexConsistencyGroup(UnManagedVolume unManagedVolume, BlockObject blockObj, VirtualPool vpool,
            Project project, TenantOrg tenant, URI varrayUri, DbClient _dbClient) {

        String cgName = PropertySetterUtil.extractValueFromStringSet(
                SupportedVolumeInformation.VPLEX_CONSISTENCY_GROUP_NAME.toString(),
                unManagedVolume.getVolumeInformation());

        // Don't create CG if the vplex is behind RP
        if (VolumeIngestionUtil.isRpVplexVolume(unManagedVolume)) {
            StringSet clusterEntries = PropertySetterUtil.extractValuesFromStringSet(
                    SupportedVolumeInformation.VPLEX_CLUSTER_IDS.toString(),
                    unManagedVolume.getVolumeInformation());
            for (String clusterEntry : clusterEntries) {
                // It is assumed that distributed volumes contain both clusters and either is OK in the CG name key.
                blockObj.setReplicationGroupInstance(BlockConsistencyGroupUtils.buildClusterCgName(clusterEntry, cgName));
            }
            return null;
        }

        if (cgName != null) {
            _logger.info("VPLEX UnManagedVolume {} is added to consistency group {}",
                    unManagedVolume.getLabel(), cgName);

            if (!vpool.getMultivolumeConsistency()) {
                _logger.warn("The requested Virtual Pool {} does not have "
                        + "the Multi-Volume Consistency flag set, and this volume "
                        + "is part of a consistency group.", vpool.getLabel());
                throw IngestionException.exceptions
                        .unmanagedVolumeVpoolConsistencyGroupMismatch(vpool.getLabel(), unManagedVolume.getLabel());
            } else {

                List<BlockConsistencyGroup> groups = CustomQueryUtility
                        .queryActiveResourcesByConstraint(_dbClient,
                                BlockConsistencyGroup.class, PrefixConstraint.Factory
                                        .getFullMatchConstraint(
                                                BlockConsistencyGroup.class, "label",
                                                cgName));

                StorageSystem storageSystem = _dbClient.queryObject(StorageSystem.class,
                        unManagedVolume.getStorageSystemUri());
                BlockConsistencyGroup potentialUnclaimedCg = null;
                if (!groups.isEmpty()) {
                    for (BlockConsistencyGroup cg : groups) {
                        // first check that the tenant and project are a match
                        if (cg.getProject().getURI().equals(project.getId()) &&
                                cg.getTenant().getURI().equals(tenant.getId())) {
                            // need to check for several matching properties
                            URI storageControllerUri = cg.getStorageController();
                            URI virtualArrayUri = cg.getVirtualArray();
                            if (!NullColumnValueGetter.isNullURI(storageControllerUri)
                                    && !NullColumnValueGetter.isNullURI(virtualArrayUri)) {
                                if (storageControllerUri.equals(storageSystem.getId()) &&
                                        virtualArrayUri.equals(varrayUri)) {
                                    _logger.info("Found a matching BlockConsistencyGroup {} "
                                            + "for virtual volume {}.", cgName, unManagedVolume.getLabel());
                                    // @TODO add logic to update the existing CG's
                                    return cg;
                                }
                            }
                            if (null == storageControllerUri && null == virtualArrayUri) {
                                potentialUnclaimedCg = cg;
                            }
                        }
                    }
                }

                // if not match on label, project, tenant, storage array, and virtual array
                // was found, then we can return the one found with null storage array and
                // virtual array. this would indicate the user created the CG, but hadn't
                // used it yet in creating a volume
                // COP-20683: Update the properties in the user created CG
                if (null != potentialUnclaimedCg) {
                    potentialUnclaimedCg.addConsistencyGroupTypes(Types.VPLEX.name());
                    potentialUnclaimedCg.setStorageController(storageSystem.getId());
                    potentialUnclaimedCg.setVirtualArray(varrayUri);
                    return potentialUnclaimedCg;
                }

                _logger.info("Did not find an existing Consistency Group named {} that is associated "
                        + "already with the requested VPLEX device and Virtual Array. "
                        + "ViPR will create a new one.", cgName);

                // create a new consistency group
                BlockConsistencyGroup cg = new BlockConsistencyGroup();
                cg.setId(URIUtil.createId(BlockConsistencyGroup.class));
                cg.setLabel(cgName);
                cg.setProject(new NamedURI(project.getId(), project.getLabel()));
                cg.setTenant(project.getTenantOrg());
                cg.setArrayConsistency(false);
                cg.addConsistencyGroupTypes(Types.VPLEX.name());
                cg.setStorageController(storageSystem.getId());
                cg.setVirtualArray(varrayUri);
                return cg;
            }
        }

        return null;
    }

    /**
     * Find List of Export Masks already available in the database for
     * the given List of UnManagedExportMasks.
     *
     * @param masks a List of UnManagedExportMasks to check
     * @param dbClient a reference to the database client
     * @return a List of UnManagedExportMasks
     */
    public static List<UnManagedExportMask> getExportsMaskAlreadyIngested(List<UnManagedExportMask> masks,
            DbClient dbClient) {
        List<UnManagedExportMask> unManagedMasks = new ArrayList<UnManagedExportMask>();
        for (UnManagedExportMask mask : masks) {
            ExportMask exportMask = getExportsMaskAlreadyIngested(mask, dbClient);
            if (null == exportMask) {
                unManagedMasks.add(mask);
            } else {
                _logger.info("Export Mask {} already ingested ", mask.getLabel());
            }
        }
        return unManagedMasks;
    }

    /**
     * Find Export Mask already available in the database.
     *
     * @param mask the UnManagedExportMask to check
     * @param dbClient a reference to the database client
     * @return a ExportMask if present, or null
     */
    public static ExportMask getExportsMaskAlreadyIngested(UnManagedExportMask mask, DbClient dbClient) {
        @SuppressWarnings("deprecation")
        List<URI> maskUris = dbClient.queryByConstraint(AlternateIdConstraint.Factory
                .getExportMaskByNameConstraint(mask.getMaskName()));
        if (null != maskUris && !maskUris.isEmpty()) {
            for (URI maskUri : maskUris) {
                ExportMask exportMask = dbClient.queryObject(ExportMask.class, maskUri);
                // skip if the mask is null, the storage device doesn't match, or is on the incorrect vplex cluster path
                if (null == exportMask
                        || null == exportMask.getStorageDevice()
                        || !exportMask.getStorageDevice().equals(mask.getStorageSystemUri())
                        || VolumeIngestionUtil.hasIncorrectMaskPathForVplex(mask, exportMask, dbClient)) {
                    continue;
                }
                return exportMask;
            }
        }

        return null;
    }

    /**
     * Checks an UnManagedExportMask against an existing ExportMask to make sure
     * their nativeId (storage view paths) are the same.  This is to handle cases
     * where a VPLEX could have the same storage view name on both VPLEX clusters.
     * 
     * If the UnManagedExportMask is not on a VPLEX, it will always return false.
     * 
     * @param mask the UnManagedExportMask to check
     * @param exportMask the existing ExportMask to compare against
     * @param dbClient a reference to the database client
     * @return true if the masks don't match
     */
    public static boolean hasIncorrectMaskPathForVplex(UnManagedExportMask mask, ExportMask exportMask, DbClient dbClient) {
        // if this is a vplex system,
        // it could be the case that there are storage views on each vplex cluster
        // with the same name, so we need to check the full unmanaged export mask path
        StorageSystem storageSystem = dbClient.queryObject(StorageSystem.class, mask.getStorageSystemUri());
        boolean checkMaskPathForVplex = storageSystem != null && ConnectivityUtil.isAVPlex(storageSystem);
        if (checkMaskPathForVplex && (exportMask.getNativeId() != null 
                && !exportMask.getNativeId().equalsIgnoreCase(mask.getNativeId()))){
            // this is not the right mask
            _logger.info("found an existing mask with the same name or initiators {}, but the mask view paths are different. "
                    + "UnManagedExportMask: {} Existing ExportMask: {}", mask.getMaskName(),
                    mask.getNativeId(), exportMask.getNativeId());
            return true;
        }
        return false;
    }

    /**
     * Initialize an Export Group.
     *
     * @param project the Project
     * @param type the ExportGroup type
     * @param vArray the VirtualArray for the ExportGroup
     * @param label the text label for the ExportGroup
     * @param dbClient a reference to the database client
     * @param nameGenerator a name generator
     * @param tenantOrg the TenantOrg to use
     * @return a newly-created ExportGroup
     */
    public static ExportGroup initializeExportGroup(Project project, String type, URI vArray, String label,
            DbClient dbClient, ResourceAndUUIDNameGenerator nameGenerator, TenantOrg tenantOrg) {
        ExportGroup exportGroup = new ExportGroup();
        exportGroup.setLabel(label);

        exportGroup.setType(type);
        exportGroup.setId(URIUtil.createId(ExportGroup.class));
        exportGroup.setProject(new NamedURI(project.getId(), exportGroup.getLabel()));
        exportGroup.setVirtualArray(vArray);
        exportGroup.setTenant(new NamedURI(project.getTenantOrg().getURI(), exportGroup.getLabel()));

        String generatedName = nameGenerator.generate(tenantOrg.getLabel(), exportGroup.getLabel(),
                exportGroup.getId().toString(), '_', 56);
        exportGroup.setGeneratedName(generatedName);
        return exportGroup;
    }

    /**
     * Creates an ExportMask for the given arguments and returns the BlockObject.
     *
     * @param eligibleMask an UnManagedExportMask to base the ExportMask on
     * @param unManagedVolume the UnManagedVolume being ingested
     * @param exportGroup the ExportGroup for the ExportMask
     * @param volume the Volume object for the ExportMask
     * @param dbClient a reference to the database client
     * @param hosts a List of Hosts for the ExportMask
     * @param cluster a Cluster for the ExportMask
     * @param exportMaskLabel the name of the ExportMask
     * @throws Exception
     */
    public static <T extends BlockObject> ExportMask createExportMask(UnManagedExportMask eligibleMask, UnManagedVolume unManagedVolume,
            ExportGroup exportGroup, T volume, DbClient dbClient, List<Host> hosts, Cluster cluster, String exportMaskLabel)
            throws Exception {
        _logger.info("Creating ExportMask for unManaged Mask {}", eligibleMask.getMaskName());
        List<URI> initiatorUris = new ArrayList<URI>(Collections2.transform(
                eligibleMask.getKnownInitiatorUris(), CommonTransformerFunctions.FCTN_STRING_TO_URI));
        List<Initiator> allInitiators = dbClient.queryObject(Initiator.class, initiatorUris);

        List<Initiator> userAddedInis = VolumeIngestionUtil.findUserAddedInisFromExistingIniListInMask(allInitiators, eligibleMask.getId(),
                dbClient);

        List<URI> storagePortUris = new ArrayList<URI>(Collections2.transform(
                eligibleMask.getKnownStoragePortUris(), CommonTransformerFunctions.FCTN_STRING_TO_URI));

        Map<String, Integer> wwnToHluMap = extractWwnToHluMap(eligibleMask, dbClient);

        ExportMask exportMask = ExportMaskUtils.initializeExportMaskWithVolumes(eligibleMask.getStorageSystemUri(), exportGroup,
                eligibleMask.getMaskName(), exportMaskLabel, allInitiators, null, storagePortUris, eligibleMask.getZoningMap(), volume,
                eligibleMask.getUnmanagedInitiatorNetworkIds(), eligibleMask.getNativeId(), userAddedInis, dbClient, wwnToHluMap);

        // remove unmanaged mask if created if the block object is not marked as internal
        if (!volume.checkInternalFlags(Flag.PARTIALLY_INGESTED)) {
            _logger.info("breaking relationship between UnManagedExportMask {} and UnManagedVolume {}",
                    eligibleMask.getMaskName(), unManagedVolume.getLabel());
            unManagedVolume.getUnmanagedExportMasks().remove(eligibleMask.getId().toString());
            eligibleMask.getUnmanagedVolumeUris().remove(unManagedVolume.getId().toString());
        }

        updateExportGroup(exportGroup, volume, wwnToHluMap, dbClient, allInitiators, hosts, cluster);

        return exportMask;
    }

    /**
     * Extracts a map of WWNs to HLUs for UnManagedVolumes in a given UnManagedExportMask.
     *
     * @param unManagedExportMask the UnManagedExportMask to check
     * @param dbClient a reference to the database client
     *
     * @return a map of WWNs to HLUs for UnManagedVolumes in a given UnManagedExportMask
     */
    public static Map<String, Integer> extractWwnToHluMap(UnManagedExportMask unManagedExportMask, DbClient dbClient) {
        // create the volume wwn to hlu map
        Map<String, Integer> wwnToHluMap = new HashMap<String, Integer>();
        Iterator<UnManagedVolume> unManagedVolumes = dbClient.queryIterativeObjects(
                UnManagedVolume.class, Collections2.transform(
                        unManagedExportMask.getUnmanagedVolumeUris(), CommonTransformerFunctions.FCTN_STRING_TO_URI));
        while (unManagedVolumes.hasNext()) {
            UnManagedVolume vol = unManagedVolumes.next();
            String wwn = vol.getWwn();
            if (wwn != null) {
                wwnToHluMap.put(wwn, findHlu(vol, unManagedExportMask.getMaskName()));
            }
        }
        _logger.info("wwn to hlu map for {} is " + wwnToHluMap, unManagedExportMask.getMaskName());
        return wwnToHluMap;
    }

    /**
     * Validate the unmanaged export mask is a mask that aligns with the host/cluster initiators provided.
     * If they do match, they will eventually be fed into the export group.
     *
     * @param dbClient dbclient
     * @param exportGroup export group. Used for error messages only
     * @param computeInitiators list of initiators
     * @param unManagedExportMask unmanaged export mask
     * @param errorMessages error messages
     * @return true if the export mask is aligned to the export group.
     */
    public static boolean validateExportMaskMatchesComputeResourceInitiators(DbClient dbClient, ExportGroup exportGroup,
            StringSet computeInitiators,
            UnManagedExportMask unManagedExportMask, List<String> errorMessages) {
        _logger.info("computeInitiators :{}", computeInitiators);
        // Validate future export group initiators
        if (computeInitiators == null) {
            String errorMessage = String.format(
                    "ExportGroup %s has no initiators and therefore unmanaged export mask %s can't be ingested with it.",
                    exportGroup.getLabel(), unManagedExportMask.getMaskName());
            errorMessages.add(errorMessage.toString());
            _logger.warn(errorMessage);
            return false;
        }

        // Validate unmanaged export mask initiators
        if (unManagedExportMask.getKnownInitiatorUris() == null) {
            String errorMessage = String.format(
                    "Unmanaged export mask %s has no initiators and therefore it can't be ingested.  (ExportGroup: %s)",
                    unManagedExportMask.getMaskName(), exportGroup.getLabel());
            errorMessages.add(errorMessage.toString());
            _logger.warn(errorMessage);
            return false;
        }

        // If you find the compute resource contains some initiators in the unmanaged export mask, go ahead and process it.
        if (StringSetUtil.hasIntersection(unManagedExportMask.getKnownInitiatorUris(), computeInitiators)) {
            String message = String
                    .format(
                            "Unmanaged export mask (%s) has initiators that match the export group (%s) initiators and therefore will be attempted to be ingested.",
                            unManagedExportMask.getMaskName(), exportGroup.getLabel());
            _logger.info(message);
            return true;
        }
        _logger.info("Mask knownInitiatorUris :{}", unManagedExportMask.getKnownInitiatorUris());

        // Probably the most common scenario. Don't try to ingest an export mask that doesn't match the export group's initiators.
        String errorMessage = String.format(
                "ExportGroup %s has no initiators that match unmanaged export mask %s and therefore can't be ingested with it.",
                exportGroup.getLabel(), unManagedExportMask.getMaskName());
        errorMessages.add(errorMessage.toString());
        _logger.warn(errorMessage);
        return false;
    }

    /**
     * Validates StoragePorts in a VirtualArray for an UnManagedExportMask.
     *
     * @param dbClient a reference to the database client
     * @param volume a BlockObject
     * @param varray the VirtualArray to validate
     * @param portsInUnManagedMask the set of ports in the UnManagedExportMask
     * @param mask the UnManagedExportMask to validate
     * @param errorMessages error messages to return if necessary
     * @return a BlockObject
     */
    public static <T extends BlockObject> boolean validateStoragePortsInVarray(DbClient dbClient, T volume, URI varray,
            Set<String> portsInUnManagedMask, UnManagedExportMask mask, List<String> errorMessages) {
        _logger.info("validating storage ports in varray " + varray);
        List<URI> allVarrayStoragePortUris = dbClient.queryByConstraint(AlternateIdConstraint.Factory
                .getVirtualArrayStoragePortsConstraint(varray.toString()));
        allVarrayStoragePortUris = filterOutUnregisteredPorts(dbClient, allVarrayStoragePortUris);
        Set<String> allVarrayStoragePortUriStrings = new HashSet<String>((Collections2.transform(allVarrayStoragePortUris,
                CommonTransformerFunctions.FCTN_URI_TO_STRING)));
        SetView<String> unManagedExportMaskPortsNotInSourceVarray = Sets.difference(portsInUnManagedMask, allVarrayStoragePortUriStrings);
        // Temporary relaxation of storage port restriction for XIO:
        // With XIO we do not have the ability to remove specific (and possibly unavailable) storage ports
        // from the LUN maps. So a better check specifically for XIO is to ensure that we at least have one
        // storage port in the varray.
        StorageSystem storageSystem = dbClient.queryObject(StorageSystem.class, mask.getStorageSystemUri());
        boolean portsValid = true;
        boolean atLeastOnePortMatched = unManagedExportMaskPortsNotInSourceVarray.size() < portsInUnManagedMask.size();
        if (storageSystem != null) {
            if (storageSystem.getSystemType().equalsIgnoreCase(SystemType.xtremio.toString()) ||
                    storageSystem.getSystemType().equalsIgnoreCase(SystemType.unity.toString())) {
                portsValid = atLeastOnePortMatched;
            } else {
                portsValid = unManagedExportMaskPortsNotInSourceVarray.isEmpty();
            }
        }

        if (!portsValid) {
            _logger.warn("The following Storage Ports in UnManagedExportMask {} are unavailable in source Virtual Array {}: {}", 
                    new Object[] {mask.getMaskName(), varray, Joiner.on(",").join(unManagedExportMaskPortsNotInSourceVarray)});
            if (volume instanceof Volume) {
                Volume vol = (Volume) volume;
                if (isVplexVolume(vol, dbClient)) {
                    URI haVarray = checkVplexHighAvailabilityArray(vol, dbClient);
                    if (null != haVarray) {
                        _logger.info("Checking high availability Virtual Array {} for Storage Ports as well.",
                                haVarray);
                        allVarrayStoragePortUris = dbClient.queryByConstraint(AlternateIdConstraint.Factory
                                .getVirtualArrayStoragePortsConstraint(haVarray.toString()));
                        allVarrayStoragePortUris = filterOutUnregisteredPorts(dbClient, allVarrayStoragePortUris);
                        allVarrayStoragePortUriStrings = new HashSet<String>((Collections2.transform(allVarrayStoragePortUris,
                                CommonTransformerFunctions.FCTN_URI_TO_STRING)));
                        SetView<String> unManagedExportMaskPortsNotInHaVarray = 
                                Sets.difference(unManagedExportMaskPortsNotInSourceVarray, allVarrayStoragePortUriStrings);
                        if (!unManagedExportMaskPortsNotInHaVarray.isEmpty()) {
                            int unfoundPortCount = unManagedExportMaskPortsNotInSourceVarray.size() - unManagedExportMaskPortsNotInHaVarray.size();
                            if (unfoundPortCount < portsInUnManagedMask.size()) {
                                _logger.warn("Storage Ports {} in unmanaged mask {} were not found in VPLEX source or high availability varray, "
                                        + "but at least one port was found in either, so this mask is okay for further processing...", 
                                        new Object[] { Joiner.on(",").join(getStoragePortNames(Collections2.transform(unManagedExportMaskPortsNotInHaVarray,
                                                CommonTransformerFunctions.FCTN_STRING_TO_URI), dbClient)), mask.forDisplay() });
                                return true;
                            } else {
                                _logger.warn("The following Storage Ports in UnManagedExportMask {} are not available in high "
                                        + "availability varray {} either, so matching fails for this mask: {}",
                                        new Object[] { mask.getMaskName(), getVarrayName(haVarray, dbClient), 
                                                Joiner.on(",").join(unManagedExportMaskPortsNotInHaVarray) });
                                StringBuffer errorMessage = new StringBuffer("Unable to find the following Storage Port(s) of unmanaged export mask ");
                                errorMessage.append(mask.forDisplay());
                                errorMessage.append(" in source Virtual Array ");
                                errorMessage.append(getVarrayName(varray, dbClient));
                                errorMessage.append(" or in high availability Virtual Array ");
                                errorMessage.append(getVarrayName(haVarray, dbClient)).append(": ");
                                errorMessage.append(Joiner.on(", ").join(
                                        getStoragePortNames((Collections2.transform(unManagedExportMaskPortsNotInHaVarray,
                                        CommonTransformerFunctions.FCTN_STRING_TO_URI)), dbClient)));
                                errorMessage.append(". All ports must be present in one Virtual Array or the other for exported distributed VPLEX volume ingestion.");
                                errorMessages.add(errorMessage.toString());
                                return false;
                            }
                        } else {
                            _logger.info("Storage Ports {} in unmanaged mask {} found in high availability varray {}, so this mask is okay", 
                                    new Object[] { Joiner.on(",").join(getStoragePortNames(Collections2.transform(unManagedExportMaskPortsNotInSourceVarray,
                                            CommonTransformerFunctions.FCTN_STRING_TO_URI), dbClient)),
                                    mask.forDisplay(), getVarrayName(haVarray, dbClient) });
                            return true;
                        }
                    } else if (atLeastOnePortMatched) {
                        _logger.info("Storage Ports {} in unmanaged mask {} not found in VPLEX local varray, but at least one port was found, so this mask is okay", 
                                new Object[] { Joiner.on(",").join(getStoragePortNames(Collections2.transform(unManagedExportMaskPortsNotInSourceVarray,
                                        CommonTransformerFunctions.FCTN_STRING_TO_URI), dbClient)), mask.forDisplay() });
                        return true;
                    }
                }
            }
            StringBuffer errorMessage = new StringBuffer("Storage Port(s) ");
            errorMessage.append(Joiner.on(", ").join(getStoragePortNames(Collections2.transform(unManagedExportMaskPortsNotInSourceVarray,
                    CommonTransformerFunctions.FCTN_STRING_TO_URI), dbClient)));
            errorMessage.append(" in unmanaged export mask ").append(mask.forDisplay());
            errorMessage.append(" are not available in Virtual Array ").append(getVarrayName(varray, dbClient));
            errorMessage.append(". All ports of the mask must be present in the ingesting Virtual Array for storage systems of type ");
            errorMessage.append(storageSystem.getSystemType()).append(". ");
            errorMessages.add(errorMessage.toString());
            _logger.warn(errorMessages.toString());
            return false;
        }
        _logger.info("Storage Ports {} in unmanaged mask {} are valid for varray {}, so this mask is okay", 
                new Object[] { Joiner.on(",").join(getStoragePortNames(Collections2.transform(portsInUnManagedMask,
                        CommonTransformerFunctions.FCTN_STRING_TO_URI), dbClient)),
                mask.forDisplay(), getVarrayName(varray, dbClient) });
        return true;
    }

    /**
     * Convenience method to convert a Collection of UnManagedExportMask URIs to their pretty names.
     *
     * @param unmanagedExportMaskUris a Collection of Initiator URIs
     * @param dbClient a reference to the database client
     *
     * @return a List of friendly Initiator names
     */
    public static List<String> getMaskNames(Collection<URI> unmanagedExportMaskUris, DbClient dbClient) {
        List<String> maskNames = new ArrayList<String>();
        if (unmanagedExportMaskUris != null && !unmanagedExportMaskUris.isEmpty()) {
            List<UnManagedExportMask> masks = dbClient.queryObject(UnManagedExportMask.class, unmanagedExportMaskUris);
            for (UnManagedExportMask mask : masks) {
                if (mask != null) {
                    maskNames.add(mask.getMaskName());
                }
            }
        }

        return maskNames;
    }

    /**
     * Convenience method to convert a Collection of Initiator URIs to their pretty names.
     *
     * @param initiatorUris a Collection of Initiator URIs
     * @param dbClient a reference to the database client
     *
     * @return a List of friendly Initiator names
     */
    public static List<String> getInitiatorNames(Collection<URI> initiatorUris, DbClient dbClient) {
        List<String> initiatorNames = new ArrayList<String>();
        if (initiatorUris != null && !initiatorUris.isEmpty()) {
            List<Initiator> inits = dbClient.queryObject(Initiator.class, initiatorUris);
            for (Initiator init : inits) {
                if (init != null) {
                    initiatorNames.add(init.forDisplay());
                }
            }
        }

        return initiatorNames;
    }

    /**
     * Convenience method to convert a Collection of Storage Port URIs to their storage port names.
     *
     * @param storagePortUris a Collection of Storage Port URIs
     * @param dbClient a reference to the database client
     *
     * @return a List of Storage Port names
     */
    private static List<String> getStoragePortNames(Collection<URI> storagePortUris, DbClient dbClient) {
        List<String> storagePortNames = new ArrayList<String>();
        if (storagePortUris != null && !storagePortUris.isEmpty()) {
            List<StoragePort> storagePorts = dbClient.queryObject(StoragePort.class, storagePortUris);
            for (StoragePort storagePort : storagePorts) {
                if (storagePort != null) {
                    storagePortNames.add(storagePort.getPortGroup() + "/" + storagePort.getPortName());
                }
            }
        }

        return storagePortNames;
    }

    /**
     * Convenience method to return the Virtual Array name for a given Virtual Array URI.
     *
     * @param virtualArrayUri the Virtual Array URI to check
     * @param dbClient a reference to the database client
     *
     * @return a Virtual Array name or the URI if it could not be found
     */
    public static String getVarrayName(URI virtualArrayUri, DbClient dbClient) {
        if (virtualArrayUri != null) {
            VirtualArray varray = dbClient.queryObject(VirtualArray.class, virtualArrayUri);
            if (varray != null) {
                return varray.getLabel();
            }
        }

        return virtualArrayUri.toString();
    }

    /**
     * Remove UNREGISTERED storage ports from a URI list.
     *
     * @param dbClient -- DbClient
     * @param storagePortUris -- List<URI>
     * @return List<URI> StoragePorts with UNREGISTERED ports removed
     */
    static private List<URI> filterOutUnregisteredPorts(DbClient dbClient, List<URI> storagePortUris) {
        List<StoragePort> ports = dbClient.queryObject(StoragePort.class, storagePortUris);
        Iterator<StoragePort> portItr = ports.iterator();
        while (portItr.hasNext()) {
            StoragePort stPort = portItr.next();
            if (stPort.getRegistrationStatus().equalsIgnoreCase(RegistrationStatus.UNREGISTERED.toString())) {
                _logger.info("Removing unregistered port {}", stPort.getNativeGuid());
                storagePortUris.remove(stPort.getId());
            }
        }
        return storagePortUris;
    }

    /**
     * Validates Initiators are registered.
     *
     * @param initiators a List of Initiators to validate
     * @return true if the any initiators are registered
     */
    public static boolean validateInitiatorPortsRegistered(List<Initiator> initiators) {
        List<Initiator> regInis = new ArrayList<Initiator>();
        for (Initiator initiator : initiators) {
            if (RegistrationStatus.REGISTERED.name().equalsIgnoreCase(initiator.getRegistrationStatus())) {
                regInis.add(initiator);
            } else {
                _logger.info("Initiator {} not registered", initiator.getId());
            }
        }
        return !regInis.isEmpty();
    }

    /**
     * Update an ExportGroup.
     *
     * @param exportGroup the ExportGroup to update
     * @param volume a BlockObject for the ExportGroup
     * @param wwnToHluMap the wwn to hlu map
     * @param dbClient a reference to the database client
     * @param allInitiators a List of all initiators for the ExportGroup
     * @param hosts a List of Hosts for the ExportGroup
     * @param cluster a Cluster for the ExportGroup
     */
    public static <T extends BlockObject> void updateExportGroup(ExportGroup exportGroup, T volume, Map<String, Integer> wwnToHluMap,
            DbClient dbClient, List<Initiator> allInitiators, List<Host> hosts, Cluster cluster) {

        for (Host host : hosts) {
            if (null == exportGroup.getHosts() || !exportGroup.getHosts().contains(host.getId().toString())) {
                exportGroup.addHost(host);
            }

        }

        if (null != cluster
                && (null == exportGroup.getClusters() || !exportGroup.getClusters().contains(
                        cluster.getId().toString()))) {
            exportGroup.addCluster(cluster);

        }

        for (Initiator ini : allInitiators) {
            if (exportGroup.getInitiators() == null
                    || !exportGroup.getInitiators().contains(ini.getId().toString())) {
                exportGroup.addInitiator(ini);

            }
        }

        // Do not add the block object to the export group if it is partially ingested
        if (!volume.checkInternalFlags(Flag.PARTIALLY_INGESTED)) {
            _logger.info("adding volume {} to export group {}", volume.forDisplay(), exportGroup.forDisplay());
            Integer hlu = ExportGroup.LUN_UNASSIGNED;
            if (wwnToHluMap.containsKey(volume.getWWN())) {
                hlu = wwnToHluMap.get(volume.getWWN());
            }
            exportGroup.addVolume(volume.getId(), hlu);
        } else {
            _logger.info("volume {} is partially ingested, so not adding to export group {}",
                    volume.forDisplay(), exportGroup.forDisplay());
        }

        if (volume instanceof Volume) {
            Volume vol = (Volume) volume;

            URI haVarray = checkVplexHighAvailabilityArray(vol, dbClient);
            if (null != haVarray) {
                exportGroup.putAltVirtualArray(
                        volume.getStorageController().toString(), haVarray.toString());
            }
        }
    }

    /**
     * Find matching UnManagedExportMasks for a Host.
     *
     * @param volume the BlockObject being ingested
     * @param unManagedMasks a List of UnManagedExportMasks
     * @param initiatorUris a List of Initiator URIs
     * @param iniByProtocol a Map of Initiators sorted by protocol
     * @param dbClient a reference to the database client
     * @param vArray the VirtualArray
     * @param vPoolURI the VirtualPool
     * @param hostPartOfCluster boolean indicating if the Host is part of a Cluster
     * @param initiatorsPartOfCluster initiators that are part of the Cluster
     * @param clusterUri the URI of the Cluster
     * @param errorMessages error messages to add to if necessary
     * @return a List of matching UnManagedExportMasks for a Host
     */
    public static <T extends BlockObject> List<UnManagedExportMask> findMatchingExportMaskForHost(T volume,
            List<UnManagedExportMask> unManagedMasks, Set<String> initiatorUris,
            Map<String, Set<String>> iniByProtocol, DbClient dbClient, URI vArray, URI vPoolURI,
            boolean hostPartOfCluster, Set<String> initiatorsPartOfCluster, URI clusterUri,
            List<String> errorMessages) {
        List<UnManagedExportMask> eligibleMasks = new ArrayList<UnManagedExportMask>();
        Iterator<UnManagedExportMask> itr = unManagedMasks.iterator();
        while (itr.hasNext()) {
            // if required initiators are available in the mask, then choose it,
            // irrespective of actual initiators on the MV on Array

            // if all initiators are available, then choose it
            // else, group initiators by protocol
            // for each group, find if its a complete subset of the available
            // masking views, if yes then choose it
            // if its not a complete subset & if other unknown initiators are
            // not available, then choose it
            UnManagedExportMask mask = itr.next();
            if (!VolumeIngestionUtil.validateStoragePortsInVarray(dbClient, volume,
                    vArray, mask.getKnownStoragePortUris(), mask, errorMessages)) {
                itr.remove();
                _logger.info("unManagedMask skipped due to invalid storage ports: " + mask.getMaskName());
                continue;
            }

            if (null != mask.getKnownInitiatorUris() && !mask.getKnownInitiatorUris().isEmpty()) {
                _logger.info("unManagedMask being checked now: " + mask.getMaskName());
                _logger.info("Grouping initiators by protocol: {}",
                        Joiner.on(",").join(iniByProtocol.entrySet()));
                // group Initiators by Protocol
                for (Entry<String, Set<String>> entry : iniByProtocol.entrySet()) {
                    Set<String> hostInitiatorsForProtocol = entry.getValue();
                    _logger.info("Processing initiators for {} Protocol Group: {}", entry.getKey(), hostInitiatorsForProtocol);
                    if (hostPartOfCluster) {
                        /**
                         * If Host is part of a Cluster, then
                         *
                         * Host's initiators -> UnManagedExportMask's initiators
                         * case 1: I1,I2 -> I1,I3,I4,I2 [Verify whether I3,I4 are part of then same Cluster, then skip it]
                         * case 2: I1,I2,I3 -> I1,I2 -- mask selected
                         * case 3: I1,I3 -> I1,I2 -- not selected
                         *
                         */
                        _logger.info("Host in Cluster- Comparing host's initiators [{}] \nwith UnManagedExportMask {} initiators [{}] ", 
                                Joiner.on(",").join(getInitiatorNames(URIUtil.toURIList(hostInitiatorsForProtocol), dbClient)),
                                mask.forDisplay(),
                                Joiner.on(",").join(getInitiatorNames(URIUtil.toURIList(mask.getKnownInitiatorUris()), dbClient)));
                        Set<String> hostInitiatorsNotInUnManagedExportMask = Sets.difference(hostInitiatorsForProtocol,
                                mask.getKnownInitiatorUris());

                        if (hostInitiatorsNotInUnManagedExportMask.isEmpty()) {

                            // check whether remaining existing initiators on mask are part of the cluster
                            Set<String> unManagedExportMaskInitiatorsNotInHost = Sets.difference(mask.getKnownInitiatorUris(), hostInitiatorsForProtocol);
                            Set<String> remainingInitsNotInClusterOrUnManagedExportMask = Sets.difference(unManagedExportMaskInitiatorsNotInHost, initiatorsPartOfCluster);
                            _logger.info(
                                    "The host's initiators are all included in the UnManagedExportMask {} initiators. "
                                    + "Checking whether any remaining initiators [{}] in the unmanaged mask are actually "
                                    + "included in the cluster {} this host belongs to.",
                                    mask.forDisplay(),
                                    Joiner.on(",").join(remainingInitsNotInClusterOrUnManagedExportMask), 
                                    clusterUri);
                            if (remainingInitsNotInClusterOrUnManagedExportMask.size() == unManagedExportMaskInitiatorsNotInHost.size()) {
                                _logger.info(
                                        "UnManagedExportMask {} is a match, as there are no other initiators "
                                        + "unaccounted for in the cluster this host belongs to.",
                                        mask.forDisplay());
                                if (verifyNumPath(Collections.singletonList(initiatorUris), mask,
                                        volume, vPoolURI, vArray, dbClient)) {
                                    eligibleMasks.add(mask);
                                } else {
                                    _logger.info("UnManagedExportMask {} doesn't satisfy the num path requirements, so it'll be skipped.", 
                                            mask.forDisplay());
                                }
                                itr.remove();
                            } else {
                                _logger.info(
                                        "Even though UnManagedExportMask {} contains a subset of the cluster's initiators, "
                                        + "it can't be used as there are other initiators [{}] in the mask which are owned "
                                        + "by a different host in the same cluster this host belongs to.",
                                        new Object[] { mask.forDisplay(), 
                                                Joiner.on(",").join(getInitiatorNames(URIUtil.toURIList(remainingInitsNotInClusterOrUnManagedExportMask), dbClient)), 
                                                clusterUri });
                            }
                        } else {

                            Set<String> unManagedExportMaskInitiatorsNotInHost = Sets.difference(mask.getKnownInitiatorUris(),
                                    hostInitiatorsForProtocol);

                            if (unManagedExportMaskInitiatorsNotInHost.isEmpty()) {
                                _logger.info(
                                        "Matching UnManagedExportMask {} found, as its initiators are a complete subset of the host's initiators.",
                                        mask.forDisplay());
                                if (verifyNumPath(Collections.singletonList(initiatorUris), mask,
                                        volume, vPoolURI, vArray, dbClient)) {
                                    eligibleMasks.add(mask);
                                } else {
                                    _logger.info("UnManagedExportMask {} doesn't satisfy the num path requirements, so it'll be skipped.", 
                                            mask.forDisplay());
                                }
                                itr.remove();
                            } else {
                                _logger.info(
                                        "UnManagedExportMask initiators are not a complete subset of "
                                        + "the host's initiators, skipping UnManagedExportMask {}, inits not in host are: {}",
                                        mask.forDisplay(),
                                        Joiner.on(",").join(getInitiatorNames(URIUtil.toURIList(unManagedExportMaskInitiatorsNotInHost), dbClient)));
                            }

                        }

                    } else {
                        _logger.info("Host not part of any Cluster- Comparing host's initiators "
                                + "[{}] with UnManagedExportMask's initiators [{}] ",
                                Joiner.on(",").join(getInitiatorNames(URIUtil.toURIList(hostInitiatorsForProtocol), dbClient)), 
                                Joiner.on(",").join(getInitiatorNames(URIUtil.toURIList(mask.getKnownInitiatorUris()), dbClient)));
                        Set<String> unManagedExportMaskInitiatorsNotInHost = Sets.difference(mask.getKnownInitiatorUris(),
                                hostInitiatorsForProtocol);

                        if (unManagedExportMaskInitiatorsNotInHost.isEmpty()) {
                            _logger.info("Matching UnManagedExportMask {} found, since its initiators "
                                    + "are a complete subset of the host's initiators", mask.getMaskName());
                            if (verifyNumPath(Collections.singletonList(initiatorUris), mask,
                                    volume, vPoolURI, vArray, dbClient)) {
                                eligibleMasks.add(mask);
                            } else {
                                _logger.info("UnManagedExportMask {} doesn't satisfy the num path requirements, so it'll be skipped.", 
                                        mask.forDisplay());
                            }
                            itr.remove();
                        } else {
                            _logger.info(
                                    "UnManagedExportMask has initiators from a different host [{}], not part of cluster",
                                    Joiner.on(",").join(getInitiatorNames(URIUtil.toURIList(unManagedExportMaskInitiatorsNotInHost), dbClient)));
                        }
                    }

                }

            }

        }

        _logger.info("returning host eligible masks: " + getMaskNames(URIUtil.toUris(eligibleMasks), dbClient));
        return eligibleMasks;
    }

    /**
     * Find matching UnManagedExportMasks for a Cluster.
     *
     * @param volume the BlockObject being ingested
     * @param unManagedMasks a List of UnManagedExportMasks
     * @param initiatorUris a List of Initiator URIs
     * @param dbClient a reference to the database client
     * @param vArray the VirtualArray
     * @param vPoolURI the VirtualPool
     * @param clusterUri the URI of the Cluster
     * @param errorMessages error messages to add to if necessary
     * @return a List of matching UnManagedExportMasks for a Cluster
     */
    public static <T extends BlockObject> List<UnManagedExportMask> findMatchingExportMaskForCluster(T volume,
            List<UnManagedExportMask> unManagedMasks, List<Set<String>> initiatorUris, DbClient dbClient, URI vArray,
            URI vPoolURI, URI cluster, List<String> errorMessages) {
        List<UnManagedExportMask> eligibleMasks = new ArrayList<UnManagedExportMask>();

        Set<String> clusterInitiators = new HashSet<String>();
        for (Set<String> initiatorUriList : initiatorUris) {
            clusterInitiators.addAll(initiatorUriList);
        }
        Map<String, Set<String>> clusterIniByProtocol = groupInitiatorsByProtocol(clusterInitiators, dbClient);
        Iterator<UnManagedExportMask> itr = unManagedMasks.iterator();
        List<String> maskErrorMessages = new ArrayList<String>();

        try {
            while (itr.hasNext()) {
                UnManagedExportMask mask = itr.next();
                if (!VolumeIngestionUtil.validateStoragePortsInVarray(dbClient, volume, vArray,
                        mask.getKnownStoragePortUris(), mask, errorMessages)) {
                    // not a valid mask remove it
                    itr.remove();
                    _logger.info("unManagedMask skipped due to invalid storage ports: " + mask.getMaskName());
                    continue;
                }

                // if required initiators are available in the mask, then choose it,
                // irrespective of actual initiators on the MV on Array
                if (null != mask.getKnownInitiatorUris() && !mask.getKnownInitiatorUris().isEmpty()) {

                    _logger.info("unManagedMask being checked now: " + mask.getMaskName());
                    for (Entry<String, Set<String>> entry : clusterIniByProtocol.entrySet()) {
                        Set<String> clusterInitiatorsForProtocol = entry.getValue();
                        _logger.info("Processing Initiators by {} Protocol Group: {}", entry.getKey(), clusterInitiatorsForProtocol);
                        _logger.info("Comparing cluster's initiators [{}] \nwith UnManagedExportMask's initiators [{}] ", 
                                Joiner.on(",").join(getInitiatorNames(URIUtil.toURIList(clusterInitiatorsForProtocol), dbClient)),
                                Joiner.on(",").join(getInitiatorNames(URIUtil.toURIList(mask.getKnownInitiatorUris()), dbClient)));
                        Set<String> unmanagedExportMaskInitiatorsNotInCluster = Sets.difference(mask.getKnownInitiatorUris(),
                                clusterInitiatorsForProtocol);
                        /**
                         * Host's initiators -> UnManagedExportMask's initiators
                         * case 1: I1,I2,I3,I4 -> I1,I2 -- mask skipped, as I1,I2 are initiators of a single node in cluster (exclusive export mode)
                         * case 2: I1,I2 -> I1,I2,I3 -- mask selected
                         * case 3: I1,I3 -> I1,I2 -- not selected
                         */
                        if (unmanagedExportMaskInitiatorsNotInCluster.isEmpty()) {
                            _logger.info(
                                    "UnManagedExportMask {} matches: its initiators are all included in the cluster's initiators. "
                                    + "Will try to find whether the subset actually corresponds to a single node in the cluster. "
                                    + "If true, then skip this UnManagedExportMask, as it is meant for Exclusive mode exports.",
                                    mask.forDisplay());
                            if (groupInitiatorsByHost(mask.getKnownInitiatorUris(), dbClient).size() == 1) {
                                _logger.info(
                                        "Skipping UnManagedExportMask {}, as the mask has only initiators from a single node in the cluster. "
                                        + "It is probably meant for Exclusive mode export.",
                                        mask.forDisplay());
                            } else {
                                _logger.info("UnManagedExportMask {} found with a subset of initiators from more than a single node in the cluster.",
                                        mask.forDisplay());
                                if (verifyNumPath(initiatorUris, mask, volume, vPoolURI, vArray, dbClient)) {
                                    eligibleMasks.add(mask);
                                } else {
                                    _logger.info("UnManagedExportMask {} doesn't satisfy the num path requirements, so it'll be skipped.", 
                                            mask.forDisplay());
                                }
                                itr.remove();
                            }
                        } else {
                            _logger.info(
                                    "The initiators of UnManagedExportMask {} are NOT all included in the cluster's initiators, "
                                    + "checking whether the cluster's initiators are a subset of the UnManagedExportMask's initiators instead.");
                            Set<String> clusterInitiatorsNotInUnManagedExportMask = Sets.difference(clusterInitiatorsForProtocol,
                                    mask.getKnownInitiatorUris());
                            if (clusterInitiatorsNotInUnManagedExportMask.isEmpty()) {
                                _logger.info("UnManagedExportMask {} found with a subset of the cluster's initiators.", mask.forDisplay());
                                if (verifyNumPath(initiatorUris, mask, volume, vPoolURI, vArray, dbClient)) {
                                    eligibleMasks.add(mask);
                                } else {
                                    _logger.info("UnManagedExportMask {} doesn't satisfy the num path requirements, so it'll be skipped.", 
                                            mask.forDisplay());
                                }
                                itr.remove();
                            }
                        }
                    }
                }
            }

            if (eligibleMasks.isEmpty() && !unManagedMasks.isEmpty()) {
                _logger.info(
                        "Unable to find a masking construct with all the cluster initiators. "
                        + "Grouping initiators by host and restarting the search.");
                // return individual Host MVs if found any for each Cluster Node as
                // well, to support exclusive mode volume export.
                for (Set<String> initiatorUriList : initiatorUris) {
                    // if mask is already selected, the no need to run this again
                    if (unManagedMasks.isEmpty()) {
                        break;
                    }
                    _logger.info("Looking for an UnManagedExportMask for initiators {} belonging to a cluster node.", 
                            Joiner.on(",").join(initiatorUriList));
                    Map<String, Set<String>> iniByProtocol = groupInitiatorsByProtocol(initiatorUriList, dbClient);
                    eligibleMasks.addAll(findMatchingExportMaskForHost(volume, unManagedMasks, initiatorUriList,
                            iniByProtocol, dbClient, vArray, vPoolURI, true, clusterInitiators, cluster, errorMessages));
                }
            } else {
                _logger.info("Either masks were already found or there are no unmanaged masks available.");
            }

        } catch (IngestionException ex) {
            _logger.error(ex.getLocalizedMessage());
            if (!maskErrorMessages.contains(ex.getLocalizedMessage())) {
                maskErrorMessages.add(ex.getLocalizedMessage());
            }
        }

        if (!maskErrorMessages.isEmpty()) {
            String message = maskErrorMessages.size() + " of " + unManagedMasks.size() + " unmanaged export mask(s) failed zoning checks: ";
            String messages = Joiner.on("; ").join(maskErrorMessages);
            _logger.error(message + messages);
            throw IngestionException.exceptions.inconsistentZoningAcrossHosts(message + messages);
        }

        _logger.info("returning cluster eligible masks: " + getMaskNames(URIUtil.toUris(eligibleMasks), dbClient));
        return eligibleMasks;
    }

    /**
     * Given a ZoneInfoMap, check that the hosts in a cluster have a number of
     * paths that is compliant with the vpool specifications.
     *
     * @param initiatorUris
     *            a list of initiators sets, each set belongs to one host in the
     *            cluster
     * @param umask
     *            the UnManagedExportMask being checked
     * @param block
     *            the volume or snapshot for which the zoning are verified
     * @param vPoolURI
     *            - URI of the VPool to ingest blockObject.
     * @param varrayURI
     *            - URI of the Varray to ingest blockObject.
     * @param dbClient
     *            an instance of dbclient
     * @return true if the number of paths is valid.
     */
    private static boolean verifyNumPath(List<Set<String>> initiatorUris, UnManagedExportMask umask, BlockObject block,
            URI vPoolURI, URI varrayUri, DbClient dbClient) {
        DbModelClientImpl dbModelClient = new DbModelClientImpl(dbClient);
        ExportPathParams pathParams = BlockStorageScheduler.getExportPathParam(block, vPoolURI, dbClient);
        for (Set<String> hostInitiatorUris : initiatorUris) {
            List<Initiator> initiators = CustomQueryUtility.iteratorToList(dbModelClient.find(Initiator.class,
                    StringSetUtil.stringSetToUriList(hostInitiatorUris)));

            // If this an RP initiator, do not validate num path against the vpool; it's a back-end mask with different
            // pathing rules.
            boolean avoidNumPathCheck = false;
            for (Initiator initiator : initiators) {
                if (initiator.checkInternalFlags(Flag.RECOVERPOINT)) {
                    avoidNumPathCheck = true;
                }
            }

            // if vplex distributed, only verify initiators connected to same vplex cluster as unmanaged export mask
            if (isVplexVolume(block, dbClient)) {
                VirtualPool vpool = dbClient.queryObject(VirtualPool.class, vPoolURI);
                if (VirtualPool.vPoolSpecifiesHighAvailabilityDistributed(vpool)) {
                    _logger.info("initiators before filtering for vplex distributed: " + initiators);

                    // determine the source and ha vplex cluster names for comparing to the unmanaged export mask
                    StorageSystem vplex = dbClient.queryObject(StorageSystem.class, umask.getStorageSystemUri());
                    String sourceVarrayVplexClusterName = VPlexControllerUtils.getVPlexClusterName(dbClient, varrayUri, vplex.getId());
                    List<URI> varrayUris = new ArrayList<URI>();
                    varrayUris.add(varrayUri);
                    URI haVarrayUri = VPlexUtil.getHAVarray(vpool);
                    String haVarrayVplexClusterName = null;
                    if (null != haVarrayUri) {
                        varrayUris.add(haVarrayUri);
                        haVarrayVplexClusterName = VPlexControllerUtils.getVPlexClusterName(dbClient, haVarrayUri, vplex.getId());
                    }

                    // determine the vplex cluster name that the unmanaged export mask resides upon
                    String umaskVplexClusterId = ConnectivityUtil.getVplexClusterForStoragePortUris(
                            URIUtil.toURIList(umask.getKnownStoragePortUris()), umask.getStorageSystemUri(), dbClient);
                    String umaskVplexClusterName = VPlexControllerUtils.getClusterNameForId(umaskVplexClusterId, vplex.getId(), dbClient);

                    // partition the host's initiators by virtual array (source or high availability)
                    Map<URI, List<URI>> varraysToInitiators = 
                            VPlexUtil.partitionInitiatorsByVarray(dbClient, URIUtil.toURIList(hostInitiatorUris), varrayUris, vplex);

                    // determine the varray to check by matching the vplex cluster names 
                    URI varrayToCheck = null;
                    URI otherVarray = null;
                    if (null != umaskVplexClusterName) {
                        if (umaskVplexClusterName.equalsIgnoreCase(sourceVarrayVplexClusterName)) {
                            varrayToCheck = varrayUri;
                            otherVarray = haVarrayUri;
                        } else if (umaskVplexClusterName.equalsIgnoreCase(haVarrayVplexClusterName)) {
                            varrayToCheck = haVarrayUri;
                            otherVarray = varrayUri;
                        }
                    } else {
                        _logger.error("Could not determine UnManagedExportMask VPLEX cluster name for mask " + umask.getMaskName());
                        return false;
                    }

                    // if a varray for filtering could be determined, only include those initiators
                    // that have network connectivity to the varray of this unmanaged export mask.
                    // if no initiators match, then skip the num path check, it doesn't apply to this host.
                    if (null != varrayToCheck) {
                        List<URI> initsToCheck = varraysToInitiators.get(varrayToCheck);
                        if (initsToCheck != null && !initsToCheck.isEmpty()) {
                            initiators = CustomQueryUtility.iteratorToList(dbModelClient.find(Initiator.class, initsToCheck));
                        } else {
                            List<URI> otherVarrayInits = varraysToInitiators.get(otherVarray);
                            if (null != otherVarrayInits && !otherVarrayInits.isEmpty()) {
                                avoidNumPathCheck = true;
                            }
                        }
                    } else {
                        _logger.error("inits not filtered for vplex distributed because a varray couldn't be determined for mask " 
                                        + umask.getMaskName());
                        return false;
                    }
                    _logger.info("initiators after filtering for vplex distributed: " + initiators);
                }
            }

            if (hasFCInitiators(initiators) && !avoidNumPathCheck) {
                return verifyHostNumPath(pathParams, initiators, umask.getZoningMap(), dbClient);
            }
        }
        return true;
    }

    /**
     * Checks if any Initiator in the given collection is Fibre-Channel enabled.
     *
     * @param initiators a List of Initiators to check
     * @return true if any Initiator in the given collection is Fibre-Channel enabled
     */
    private static boolean hasFCInitiators(List<Initiator> initiators) {
        for (Initiator initiator : initiators) {
            if (HostInterface.Protocol.FC.toString().equals(initiator.getProtocol())) {
                return true;
            }
        }
        return false;
    }

    /**
     * Given the zoneInfoMap, check the existing paths to make sure they
     * comply with the ingestion vpool requirements.
     *
     * @param pathParams the ingestion parameter
     * @param initiators the host initiators to be checked
     * @param zoneInfoMap the zoneInfoMap that is stored in the UnManagedExportMask
     * @param dbClient a reference to the database client
     * @return true if the host paths are compliant. False otherwise.
     */
    private static boolean verifyHostNumPath(ExportPathParams pathParams,
            List<Initiator> initiators, ZoneInfoMap zoneInfoMap, DbClient dbClient) {
        _logger.info("verifyHostNumPath for initiators {} with zoningMap {}", initiators, zoneInfoMap);
        if (initiators == null || initiators.isEmpty()) {
            _logger.error("Host has no initiators configured.");
            throw IngestionException.exceptions.hostHasNoInitiators();
        }
        int unassignedInitiators = 0;
        int totalPaths = 0;
        StringSetMap zoningMap = ExportMaskUtils.getZoneMapFromZoneInfoMap(zoneInfoMap, initiators);
        if (null == zoningMap || zoningMap.isEmpty()) {
            _logger.error("No zoning information found for the initiators");
            List<String> messageArray = new ArrayList<String>();
            for (Initiator init : initiators) {
                messageArray.add(init.getHostName() + ":" + init.getInitiatorPort());
            }
            throw IngestionException.exceptions.hostHasNoZoning(Joiner.on(", ").join(messageArray));
        }
        if (VPlexControllerUtils.isVplexInitiator(initiators.get(0), dbClient)) {
            _logger.info("these are VPLEX backend initiators, "
                    + "so no need to validate against virtual pool path params");
            return true;
        }
        String hostName = initiators.get(0).getHostName();
        URI hostURI = initiators.get(0).getHost() == null ? URIUtil.NULL_URI : initiators.get(0).getHost();
        _logger.info("Checking numpath for host {}", hostName);
        for (Initiator initiator : initiators) {
            if (initiator.getHostName() != null) {
                hostName = initiator.getHostName();
            }
            StringSet ports = zoningMap.get(initiator.getId().toString());
            if (ports == null || ports.isEmpty()) {
                unassignedInitiators++;
                _logger.info("Initiator {} of host {} is not assigned to any ports.",
                        new Object[] { initiator.getInitiatorPort(), hostName });
            } else if (ports.size() < pathParams.getPathsPerInitiator()) {
                _logger.error("Initiator {} of host {} has fewer SAN paths than what is required according to the virtual pool "
                        + "({} are zoned, but {} are required)", new Object[] { initiator.getInitiatorPort(),
                        hostName, ports.size(), pathParams.getPathsPerInitiator() });
                throw IngestionException.exceptions.hostZoningHasDifferentPortCount(
                        initiator.getInitiatorPort(), hostName,
                        String.valueOf(ports.size()), String.valueOf(pathParams.getPathsPerInitiator()));
            } else {
                totalPaths += ports.size();
                _logger.info("Initiator {} of host {} has {} paths", new Object[] { initiator.getInitiatorPort(),
                        hostName, ports.size(), ports.size() });
            }

        }
        if (totalPaths < pathParams.getMinPaths()) {
            _logger.error(String.format("Host %s (%s) has fewer paths assigned %d than min_paths %d",
                    hostName, hostURI.toString(), totalPaths, pathParams.getMinPaths()));
            throw IngestionException.exceptions.hostZoningHasFewerPorts(hostName,
                    String.valueOf(totalPaths), String.valueOf(pathParams.getMinPaths()));
        }
        if (totalPaths > pathParams.getMaxPaths()) {
            _logger.warn(String.format("Host %s (%s) has more paths assigned %d than max_paths %d",
                    hostName, hostURI.toString(), totalPaths, pathParams.getMaxPaths()));
        }
        if (unassignedInitiators > 0) {
            _logger.info(String.format("Host %s (%s) has %d unassigned initiators",
                    hostName, hostURI.toString(), unassignedInitiators));
        }
        return true;
    }

    /**
     * Group the given Initiators by protocol and return a Map of protocol
     * Strings to Initiators that have that protocol.
     *
     * @param iniStrList a List of Initiator URIs
     * @param dbClient a reference to the database client
     * @return a Map of protocol Strings to Initiators that have that protocol
     */
    public static Map<String, Set<String>> groupInitiatorsByProtocol(Set<String> iniStrList, DbClient dbClient) {
        Map<String, Set<String>> iniByProtocol = new HashMap<String, Set<String>>();
        List<URI> iniList = new ArrayList<URI>(Collections2.transform(
                iniStrList, CommonTransformerFunctions.FCTN_STRING_TO_URI));
        List<Initiator> initiators = dbClient.queryObject(Initiator.class, iniList);
        for (Initiator ini : initiators) {
            if (null == ini.getProtocol()) {
                _logger.warn("Initiator {} with protocol set to Null", ini.getId());
                continue;
            }
            if (!iniByProtocol.containsKey(ini.getProtocol())) {
                iniByProtocol.put(ini.getProtocol(), new HashSet<String>());
            }
            iniByProtocol.get(ini.getProtocol()).add(ini.getId().toString());
        }
        return iniByProtocol;

    }

    /**
     * Find the user-added initiators from a List of existing Initiators.
     *
     * @param existingInitiators a List of existing Initiators
     * @param excludeUnmanagedMask an UnManagedExportMask URI to exclude
     * @param dbClient a reference to the database client
     * @return a List of user-added Initiators
     */
    public static List<Initiator> findUserAddedInisFromExistingIniListInMask(List<Initiator> existingInitiators, URI excludeUnmanagedMask,
            DbClient dbClient) {
        List<Initiator> userAddedInis = new ArrayList<Initiator>();
        for (Initiator initiator : existingInitiators) {
            // get All unmanaged masks
            List<URI> unManagedMaskUris = dbClient.queryByConstraint(AlternateIdConstraint.Factory
                    .getUnManagedExportMaskKnownInitiatorConstraint(initiator.getInitiatorPort()));
            if (null == unManagedMaskUris || unManagedMaskUris.isEmpty()) {
                _logger.info("UnManaged Masks Empty, adding initiator {}..{} to userAddedList", initiator.getId(),
                        initiator.getInitiatorPort());
                userAddedInis.add(initiator);
            } else {
                unManagedMaskUris.remove(excludeUnmanagedMask);
                if (unManagedMaskUris.isEmpty()) {
                    userAddedInis.add(initiator);

                }
                List<UnManagedExportMask> unManagedMasks = dbClient.queryObject(UnManagedExportMask.class, unManagedMaskUris);
                for (UnManagedExportMask unManagedMask : unManagedMasks) {
                    ExportMask exportMask = getExportsMaskAlreadyIngested(unManagedMask, dbClient);

                    if (null != exportMask && exportMask.getCreatedBySystem()) {
                        _logger.info(
                                "ViPR created/ingested Export Mask {} found for unmanaged mask, adding initiator {}..{} to userAddedList",
                                new Object[] { exportMask.getMaskName(), initiator.getId(), initiator.getInitiatorPort() });
                        // update the userAddedInitiators for those already ingested export mask as well.
                        exportMask.addToUserCreatedInitiators(initiator);
                        exportMask.addToExistingInitiatorsIfAbsent(initiator);
                        exportMask.addInitiator(initiator);
                        dbClient.updateAndReindexObject(exportMask);
                        userAddedInis.add(initiator);
                    } else {
                        _logger.info(
                                "UnManaged Mask {} doesn't have any ViPR created export masks, skipping initiator from user added list",
                                unManagedMask.getMaskName(), initiator.getId());
                    }
                }
            }
        }
        _logger.info("User Added Initiators found {}", Joiner.on(",").join(userAddedInis));
        return userAddedInis;
    }

    /**
     * Group Initiators by Host containing them and return a
     * Map of Host to Initiators the Host contains.
     *
     * @param iniStrList a set of Initiator URI Strings
     * @param dbClient a reference to the database client
     * @return a Map of Host to Initiators the Host contains
     */
    private static Map<String, Set<String>> groupInitiatorsByHost(Set<String> iniStrList, DbClient dbClient) {
        Map<String, Set<String>> iniByHost = new HashMap<String, Set<String>>();
        List<URI> iniList = new ArrayList<URI>(Collections2.transform(
                iniStrList, CommonTransformerFunctions.FCTN_STRING_TO_URI));
        List<Initiator> initiators = dbClient.queryObject(Initiator.class, iniList);
        for (Initiator ini : initiators) {
            if (null == ini.getHost()) {
                _logger.warn("Initiator {} with Host set to Null", ini.getId());
                continue;
            }
            if (!iniByHost.containsKey(ini.getHost())) {
                iniByHost.put(ini.getHost().toString(), new HashSet<String>());
            }
            iniByHost.get(ini.getHost().toString()).add(ini.getId().toString());
        }
        return iniByHost;

    }

    /**
     * Verifies that the given ExportGroup matches the rest of the parameters.
     * ComputeResource URI and resourceType are only check if both are non-null
     *
     * @param exportGroupToCheck the ExportGroup to check
     * @param exportGroupLabel the name of the ExportGroup
     * @param project the URI of the ExportGroup's Project
     * @param vArray the URI of the ExportGroup's VirtualArray
     * @param computeResource the URI of the ExportGroup's ComputeResource (optional)
     * @param resourceType the ExportGroup's resource type (optional)
     * @return true if the exportGroupToCheck is a match for the rest of the parameters
     */
    public static boolean verifyExportGroupMatches(ExportGroup exportGroupToCheck, String exportGroupLabel,
            URI project, URI vArray, URI computeResource, String resourceType) {

        if (exportGroupToCheck != null) {
            if (!exportGroupToCheck.getLabel().equals(exportGroupLabel)) {
                _logger.info("export group label mismatch: {} and {}", exportGroupToCheck.getLabel(), exportGroupLabel);
                return false;
            }
            if (!exportGroupToCheck.getProject().getURI().equals(project)) {
                _logger.info("export group project mismatch: {} and {}", exportGroupToCheck.getProject().getURI(), project);
                return false;
            }
            if (!exportGroupToCheck.getVirtualArray().equals(vArray)) {
                _logger.info("export group varray mismatch: {} and {}", exportGroupToCheck.getLabel(), exportGroupLabel);
                return false;
            }

            // optionally check compute resource and resource type
            if (computeResource != null && resourceType != null) {
                if (ExportGroup.ExportGroupType.Host.toString().equalsIgnoreCase(resourceType)) {
                    if (exportGroupToCheck.hasHost(computeResource) &&
                            !ExportGroup.ExportGroupType.Cluster.toString().equalsIgnoreCase(exportGroupToCheck.getType())) {
                        _logger.info("Export Groups {} matching Varray/Project/ComputeResource exists", exportGroupToCheck.getId());
                        return true;
                    }
                } else if (ExportGroup.ExportGroupType.Cluster.toString().equalsIgnoreCase(resourceType)) {
                    if (exportGroupToCheck.hasCluster(computeResource)) {
                        _logger.info("Export Groups {} matching Varray/Project/ComputeResource exists", exportGroupToCheck.getId());
                        return true;
                    }
                } else {
                    return false;
                }
            }
        }

        return true;
    }

    /**
     * Verify a matching ExportGroup exists for the given parameters.
     *
     * @param requestContext current unManagedVolume Ingestion context.
     * @param project the Project URI
     * @param computeResource the ComputeResource URI
     * @param vArray the VirtualArray URI
     * @param resourceType the resource type (Host or Cluster)
     * @param dbClient a reference to the database client
     * @return an ExportGroup if already available in the database
     */
    public static ExportGroup verifyExportGroupExists(IngestionRequestContext requestContext, URI project, URI computeResource, URI vArray,
            String resourceType,
            DbClient dbClient) {

        List<URI> exportGroupUris = dbClient.queryByConstraint(ContainmentConstraint.Factory.getProjectExportGroupConstraint(project));
        List<ExportGroup> exportGroups = dbClient.queryObject(ExportGroup.class, exportGroupUris);

        if (null == exportGroups || exportGroups.isEmpty()) {
            return null;
        }
        ExportGroup exportGroup = null;
        for (ExportGroup eg : exportGroups) {
            if (eg.getVirtualArray().equals(vArray)) {

                if (ExportGroup.ExportGroupType.Host.toString().equalsIgnoreCase(resourceType)) {
                    if (eg.hasHost(computeResource) &&
                            !ExportGroup.ExportGroupType.Cluster.toString().equalsIgnoreCase(eg.getType())) {
                        _logger.info("Export Groups {} matching Varray/Project/ComputeResource exists", eg.getId());
                        exportGroup = eg;
                        break;
                    }
                } else if (ExportGroup.ExportGroupType.Cluster.toString().equalsIgnoreCase(resourceType)) {
                    if (eg.hasCluster(computeResource)) {
                        _logger.info("Export Groups {} matching Varray/Project/ComputeResource exists", eg.getId());
                        exportGroup = eg;
                        break;
                    }
                }

            }
        }

        if (exportGroup != null) {
            DataObject alreadyLoadedExportGroup = requestContext.findInUpdatedObjects(exportGroup.getId());
            if (alreadyLoadedExportGroup != null && (alreadyLoadedExportGroup instanceof ExportGroup)) {
                _logger.info("Found an already loaded export group");
                exportGroup = (ExportGroup) alreadyLoadedExportGroup;
            }
        }

        return exportGroup;
    }

    /**
     * Get the export group associated with initiator URIs
     *
     * Note: Once it finds an export group associated with any initiator, it returns that export group. This may not
     * be what the caller wants.
     *
     * @param requestContext current unManagedVolume Ingestion context.
     * @param exportGroupGeneratedName the generated name for the ExportGroup label
     * @param project project
     * @param knownInitiatorUris initiators list
     * @param vArray virtual array
     * @param dbClient dbclient
     * @return export group
     */
    public static ExportGroup verifyExportGroupExists(IngestionRequestContext requestContext,
            String exportGroupGeneratedName, URI project, StringSet knownInitiatorUris,
            URI vArray, DbClient dbClient) {
        ExportGroup exportGroup = null;

        for (String initiatorIdStr : knownInitiatorUris) {
            AlternateIdConstraint constraint = AlternateIdConstraint.Factory.getExportGroupInitiatorConstraint(initiatorIdStr);
            URIQueryResultList egUris = new URIQueryResultList();
            dbClient.queryByConstraint(constraint, egUris);
            List<ExportGroup> queryExportGroups = dbClient.queryObject(ExportGroup.class, egUris);
            for (ExportGroup eg : queryExportGroups) {

                if (!eg.getGeneratedName().equals(exportGroupGeneratedName)) {
                    continue;
                }

                if (!eg.getProject().getURI().equals(project)) {
                    continue;
                }

                if (!eg.getVirtualArray().equals(vArray)) {
                    continue;
                }

                if (queryExportGroups.size() > 1) {
                    _logger.info("More than one export group contains the initiator(s) requested.  Choosing : "
                            + eg.getId().toString());
                }
                exportGroup = eg;
                break;
            }
        }

        if (exportGroup != null) {
            DataObject alreadyLoadedExportGroup = requestContext.findInUpdatedObjects(exportGroup.getId());
            if (alreadyLoadedExportGroup != null && (alreadyLoadedExportGroup instanceof ExportGroup)) {
                _logger.info("Found an already loaded export group");
                exportGroup = (ExportGroup) alreadyLoadedExportGroup;
            }
        }

        return exportGroup;
    }

    /**
     * Get UnManagedVolumes associated with a Host.
     *
     * @param hostUri the URI of the Host to check
     * @param dbClient a reference to the database client
     * @param coordinator a reference to the coordinator client
     * @return a List of UnManagedVolume associated with the given Host
     */
    public static List<UnManagedVolume> findUnManagedVolumesForHost(URI hostUri, DbClient dbClient, CoordinatorClient coordinator) {

        _logger.info("finding unmanaged volumes for host " + hostUri);
        List<UnManagedVolume> unmanagedVolumes = new ArrayList<UnManagedVolume>();
        List<Initiator> initiators = ComputeSystemHelper.queryInitiators(dbClient, hostUri);
        Map<String, UnManagedExportMask> cache = new HashMap<String, UnManagedExportMask>();
        Host host = dbClient.queryObject(Host.class, hostUri);
        Set<String> clusterInis = new HashSet<String>();
        /**
         * If host is part of a cluster, then unmanaged volumes which are exclusive to this host will be selected
         * Get the remaining Host initiators of this cluster, and if there is at least a match between the cluster inis and
         * the initiators the unmanaged volume is exposed, then the volume will be skipped.
         */
        if (!NullColumnValueGetter.isNullURI(host.getCluster())) {
            List<URI> hostUris = ComputeSystemHelper.getChildrenUris(dbClient, host.getCluster(), Host.class, "cluster");
            hostUris.remove(hostUri);
            for (URI uri : hostUris) {
                List<URI> inis = dbClient.queryByConstraint(ContainmentConstraint.Factory.getContainedObjectsConstraint(uri,
                        Initiator.class, "host"));
                clusterInis.addAll(Collections2.transform(inis, CommonTransformerFunctions.FCTN_URI_TO_STRING));
            }
        }

        Set<URI> unManagedVolumeUris = new HashSet<URI>();
        URIQueryResultList results = new URIQueryResultList();
        for (Initiator initiator : initiators) {
            _logger.info("      looking at initiator " + initiator.getInitiatorPort());
            dbClient.queryByConstraint(
                    AlternateIdConstraint.Factory.getUnManagedVolumeInitiatorNetworkIdConstraint(initiator.getInitiatorPort()), results);
            if (results.iterator() != null) {
                for (URI uri : results) {
                    _logger.debug("      found UnManagedVolume " + uri);
                    unManagedVolumeUris.add(uri);
                }
            }
        }
        _logger.info("Found Unmanaged volumes {} associated to Host {}", Joiner.on(",").join(unManagedVolumeUris), hostUri);
        for (URI unmanagedVolumeUri : unManagedVolumeUris) {
            UnManagedVolume unmanagedVolume = dbClient.queryObject(UnManagedVolume.class, unmanagedVolumeUri);
            if (unmanagedVolume == null || unmanagedVolume.getInactive() == true) {
                continue;
            }
            boolean noFilteringOutClusterVolumes = Boolean.valueOf(ControllerUtils.getPropertyValueFromCoordinator(
                    coordinator, UNMANAGEDVOLUME_CLUSTER_FILTERING_SETTING));
            Set<String> inisOfunManagedMask = getInitiatorsOfUnmanagedExportMask(unmanagedVolume, cache, dbClient);
            Set<String> interSection = Sets.intersection(clusterInis, inisOfunManagedMask);
            if (noFilteringOutClusterVolumes || interSection.isEmpty()) {
                unmanagedVolumes.add(unmanagedVolume);
                _logger.info("   volume: " + unmanagedVolume.getLabel() + " nativeGuid: " + unmanagedVolume.getNativeGuid());
            } else {
                _logger.info("UnManagedVolume {} is exposed to cluster as well, skipping", unmanagedVolume.getNativeGuid());
            }

        }

        if (unmanagedVolumes.isEmpty()) {
            _logger.info("   did not find any unmanaged volumes for this host");
        }

        return unmanagedVolumes;
    }

    /**
     * Return a Set of Initiator URI Strings for the given UnManagedExportMasks.
     *
     * @param unManagedVolume the UnManagedVolume
     * @param cache a Map of UnManagedExportMask URI Strings to UnManagedExportMask objects
     * @param dbClient a reference to the database client
     * @return a List of all the Initiators for the given UnManagedExportMasks
     */
    private static Set<String> getInitiatorsOfUnmanagedExportMask(UnManagedVolume unManagedVolume,
            Map<String, UnManagedExportMask> cache, DbClient dbClient) {
        Set<String> inis = new HashSet<String>();
        for (String eMaskUri : unManagedVolume.getUnmanagedExportMasks()) {
            UnManagedExportMask unManagedExportMask = cache.get(eMaskUri);
            if (null == unManagedExportMask) {
                unManagedExportMask = dbClient.queryObject(UnManagedExportMask.class, URI.create(eMaskUri));
            }
            if (unManagedExportMask != null) {
                cache.put(unManagedExportMask.getId().toString(), unManagedExportMask);
                inis.addAll(unManagedExportMask.getKnownInitiatorUris());
            }
        }
        return inis;
    }

    /**
     * Returns a List of UnManagedVolumes for the given Cluster URI.
     *
     * @param clusterUri the Cluster URI to check
     * @param dbClient a reference to the database client
     * @return a List of UnManagedVolumes for the given Cluster URI
     */
    public static List<UnManagedVolume> findUnManagedVolumesForCluster(URI clusterUri, DbClient dbClient) {

        _logger.info("finding unmanaged volumes for cluster " + clusterUri);
        Set<URI> consistentVolumeUris = new HashSet<URI>();
        List<URI> hostUris = ComputeSystemHelper.getChildrenUris(dbClient, clusterUri, Host.class, "cluster");

        int hostIndex = 0;
        for (URI hostUri : hostUris) {
            _logger.info("   looking at host " + hostUri);
            List<Initiator> initiators = ComputeSystemHelper.queryInitiators(dbClient, hostUri);
            URIQueryResultList results = new URIQueryResultList();
            Set<URI> unManagedVolumeUris = new HashSet<URI>();
            for (Initiator initiator : initiators) {
                _logger.info("      looking at initiator " + initiator.getInitiatorPort());
                dbClient.queryByConstraint(
                        AlternateIdConstraint.Factory.getUnManagedVolumeInitiatorNetworkIdConstraint(initiator.getInitiatorPort()),
                        results);
                if (results.iterator() != null) {
                    for (URI uri : results) {
                        _logger.info("      found UnManagedVolume " + uri);
                        unManagedVolumeUris.add(uri);
                    }
                }
            }
            Set<URI> thisHostsUris = new HashSet<URI>();
            for (URI unmanagedVolumeUri : unManagedVolumeUris) {
                if (hostIndex == 0) {
                    // on the first host, just add all UnManagedVolumes that were found
                    consistentVolumeUris.add(unmanagedVolumeUri);
                } else {
                    // on subsequent hosts, create a collection to use in diffing the sets
                    thisHostsUris.add(unmanagedVolumeUri);
                }
            }

            if (hostIndex > 0) {
                // retain only UnManagedVolumes that are found exposed to all hosts in the cluster
                consistentVolumeUris.retainAll(thisHostsUris);
            }
            hostIndex++;
        }

        _logger.info("   found {} UnManagedVolumes to be consistent across all hosts", consistentVolumeUris.size());
        List<UnManagedVolume> unmanagedVolumes = new ArrayList<UnManagedVolume>();
        for (URI unmanagedVolumeUri : consistentVolumeUris) {
            UnManagedVolume unmanagedVolume = dbClient.queryObject(UnManagedVolume.class, unmanagedVolumeUri);
            if (unmanagedVolume == null || unmanagedVolume.getInactive() == true) {
                continue;
            }
            unmanagedVolumes.add(unmanagedVolume);
            _logger.info("      volume: " + unmanagedVolume.getLabel() + " nativeGuid: " + unmanagedVolume.getNativeGuid());
        }

        return unmanagedVolumes;
    }

    /**
     * Returns a List of the UnManagedExportMasks associated with a given Host URI.
     *
     * @param hostUri the Host URI to check
     * @param dbClient a reference to the database client
     * @return a List of the UnManagedExportMasks associated with a given Host URI
     */
    public static List<UnManagedExportMask> findUnManagedExportMasksForHost(URI hostUri, DbClient dbClient) {

        _logger.info("finding unmanaged export masks for host " + hostUri);
        List<UnManagedExportMask> uems = new ArrayList<UnManagedExportMask>();
        List<Initiator> initiators = ComputeSystemHelper.queryInitiators(dbClient, hostUri);

        Set<URI> uemUris = new HashSet<URI>();
        URIQueryResultList results = new URIQueryResultList();
        for (Initiator initiator : initiators) {
            _logger.info("      looking at initiator " + initiator.getInitiatorPort());
            dbClient.queryByConstraint(
                    AlternateIdConstraint.Factory.getUnManagedExportMaskKnownInitiatorConstraint(initiator.getInitiatorPort()), results);
            if (results.iterator() != null) {
                for (URI uri : results) {
                    _logger.info("      found UnManagedExportMask " + uri);
                    uemUris.add(uri);
                }
            }
        }
        for (URI uemUri : uemUris) {
            UnManagedExportMask uem = dbClient.queryObject(UnManagedExportMask.class, uemUri);
            if (uem == null || uem.getInactive() == true) {
                continue;
            }
            uems.add(uem);
            _logger.info("   maskName: " + uem.getMaskName() + " nativeGuid: " + uem.getNativeGuid());
        }
        if (uems.isEmpty()) {
            _logger.info("   did not find any unmanaged export masks for this host");
        }

        return uems;
    }

    /**
     * Returns a List of the UnManagedExportMasks associated with a given Cluster URI.
     *
     * @param clusterUri the Host URI to check
     * @param dbClient a reference to the database client
     * @return a List of the UnManagedExportMasks associated with a given Cluster URI
     */
    public static List<UnManagedExportMask> findUnManagedExportMasksForCluster(URI clusterUri, DbClient dbClient) {

        _logger.info("finding unmanaged export masks for cluster " + clusterUri);
        Set<URI> consistentUemUris = new HashSet<URI>();
        List<URI> hostUris = ComputeSystemHelper.getChildrenUris(dbClient, clusterUri, Host.class, "cluster");

        for (URI hostUri : hostUris) {
            _logger.info("   looking at host " + hostUri);
            List<Initiator> initiators = ComputeSystemHelper.queryInitiators(dbClient, hostUri);

            URIQueryResultList results = new URIQueryResultList();
            for (Initiator initiator : initiators) {
                _logger.info("      looking at initiator " + initiator.getInitiatorPort());
                dbClient.queryByConstraint(
                        AlternateIdConstraint.Factory.getUnManagedExportMaskKnownInitiatorConstraint(initiator.getInitiatorPort()),
                        results);
                if (results.iterator() != null) {
                    for (URI uri : results) {
                        _logger.info("      found UnManagedExportMask " + uri);
                        consistentUemUris.add(uri);
                    }
                }
            }
        }

        _logger.info("   found {} UnManagedExportMasks for this cluster", consistentUemUris.size());
        List<UnManagedExportMask> uems = new ArrayList<UnManagedExportMask>();
        for (URI uemUri : consistentUemUris) {
            UnManagedExportMask uem = dbClient.queryObject(UnManagedExportMask.class, uemUri);
            if (uem == null || uem.getInactive() == true) {
                continue;
            }
            uems.add(uem);
            _logger.info("      maskName: " + uem.getMaskName() + " nativeGuid: " + uem.getNativeGuid());
        }

        return uems;
    }

    /**
     * Check if the given native GUID String exists in the database for a BlockSnapshot object.
     *
     * @param nativeGuid the snapshot native GUID to check
     * @param dbClient a reference to the database client
     * @return a BlockSnapshot object for the given native GUID
     */
    public static BlockSnapshot checkSnapShotExistsInDB(String nativeGuid, DbClient dbClient) {
        List<BlockSnapshot> activeSnapshots = CustomQueryUtility.getActiveBlockSnapshotByNativeGuid(
                dbClient, nativeGuid);
        Iterator<BlockSnapshot> snapshotItr = activeSnapshots.iterator();
        return snapshotItr.hasNext() ? snapshotItr.next() : null;
    }

    /**
     * Validates if the given UnManagedVolume can be deleted safely.
     *
     * @param unManagedVolume the UnManagedVolume to check
     * @return true if the given UnManagedVolume can be deleted safely
     */
    public static boolean canDeleteUnManagedVolume(UnManagedVolume unManagedVolume) {
        boolean canDelete = null == unManagedVolume.getUnmanagedExportMasks()
                || unManagedVolume.getUnmanagedExportMasks().isEmpty();

        if (!canDelete) {
            _logger.info("cannot delete unmanaged volume {} because these unmanaged export masks are remaining to be ingested: {}",
                    unManagedVolume.forDisplay(), unManagedVolume.getUnmanagedExportMasks());
        }

        return canDelete;
    }

    /**
     * Check if the given native GUID String exists in the database for a Volume object.
     *
     * @param nativeGuid the Volume native GUID to check
     * @param dbClient a reference to the database client
     * @return a Volume object for the given native GUID
     */
    public static Volume checkIfVolumeExistsInDB(String volumeNativeGuid,
            DbClient dbClient) {
        List<Volume> activeVolumes = CustomQueryUtility.getActiveVolumeByNativeGuid(
                dbClient, volumeNativeGuid);
        Iterator<Volume> volumeItr = activeVolumes.iterator();
        return volumeItr.hasNext() ? volumeItr.next() : null;
    }

    /**
     * Check if the given native GUID String exists in the database for a BlockMirror object.
     *
     * @param nativeGuid the BlockMirror native GUID to check
     * @param dbClient a reference to the database client
     * @return a BlockMirror object for the given native GUID
     */
    public static BlockMirror checkIfBlockMirrorExistsInDB(String mirrorNativeGuid, DbClient dbClient) {
        List<BlockMirror> activeMirrors = CustomQueryUtility.getActiveBlockMirrorByNativeGuid(dbClient,
                mirrorNativeGuid);
        Iterator<BlockMirror> mirrorItr = activeMirrors.iterator();
        return mirrorItr.hasNext() ? mirrorItr.next() : null;
    }

    /**
     * Gets the URI of the high availability virtual array for the given
     * volume, or null if not available.
     *
     * @param volume the Volume to check
     * @param dbClient a database client instance
     *
     * @return the high availability virtual array URI, or null if not found
     */
    public static URI checkVplexHighAvailabilityArray(Volume volume, DbClient dbClient) {
        URI haVarray = null;
        VirtualPool vpool = dbClient.queryObject(VirtualPool.class, volume.getVirtualPool());
        if ((null != vpool) && VirtualPool.vPoolSpecifiesHighAvailability(vpool)) {
            _logger.info("volume {} virtual pool {} specifies high availability",
                    volume.getLabel(), vpool.getLabel());
            if (VirtualPool.HighAvailabilityType.vplex_distributed
                    .name().equals(vpool.getHighAvailability())) {
                haVarray = VPlexUtil.getHAVarray(vpool);
            }
        }
        return haVarray;
    }

    /**
     * Setup relationships between a snapshot and its parent BlockObject.
     *
     * @param snapshot the snapshot BlockObject
     * @param parentVolume the snapshot's parent BlockObject
     * @param dbClient a reference to the database client
     */
    public static void setupSnapParentRelations(BlockObject snapshot, BlockObject parentVolume, DbClient dbClient) {
        _logger.info("Setting up relationship between snapshot {} ({}) and parent {} ({})",
                new Object[] { snapshot.getLabel(), snapshot.getId(),
                        parentVolume.getLabel(), parentVolume.getId() });
        ((BlockSnapshot) snapshot).setSourceNativeId(parentVolume.getNativeId());
        ((BlockSnapshot) snapshot).setParent(new NamedURI(parentVolume.getId(), parentVolume.getLabel()));
        snapshot.setProtocol(new StringSet());
        snapshot.getProtocol().addAll(parentVolume.getProtocol());
        URI cgUri = parentVolume.getConsistencyGroup();
        // Do not associate parent's CG if it is a RP protected parent volume
        boolean isRP = (parentVolume instanceof Volume && ((Volume) parentVolume).checkForRp())
                || (parentVolume instanceof BlockSnapshot && ((BlockSnapshot) parentVolume).getProtectionController() != null);
        if (!isRP && cgUri != null) {
            snapshot.setConsistencyGroup(cgUri);
        }
        // TODO - check how to populate snapsetlabel if in consistency group
    }

    /**
     * Setup relationships between a mirror and its parent BlockObject.
     *
     * @param mirror the mirror BlockObject
     * @param parentVolume the mirror's parent BlockObject
     * @param dbClient a reference to the database client
     */
    public static void setupMirrorParentRelations(BlockObject mirror, BlockObject parent, DbClient dbClient) {
        _logger.info("Setting up relationship between mirror {} ({}) and parent {} ({})",
                new Object[] { mirror.getLabel(), mirror.getId(),
                        parent.getLabel(), parent.getId() });
        ((BlockMirror) mirror).setSource(new NamedURI(parent.getId(), parent.getLabel()));
        if (parent instanceof Volume) {
            StringSet mirrors = ((Volume) parent).getMirrors();
            if (mirrors == null) {
                mirrors = new StringSet();
            }
            mirrors.add(mirror.getId().toString());
            ((Volume) parent).setMirrors(mirrors);
        }
    }

    /**
     * Setup relationships between an SRDF BlockObject and its parent BlockObject.
     *
     * @param targetBlockObj the SRDF source BlockObject
     * @param sourceBlockObj the SRDF target BlockObject
     * @param dbClient a reference to the database client
     */
    public static void setupSRDFParentRelations(BlockObject targetBlockObj, BlockObject sourceBlockObj, DbClient dbClient) {
        _logger.info("Setting up relationship between SRDF mirror {} ({}) and parent {} ({})",
                new Object[] { targetBlockObj.getLabel(), targetBlockObj.getId(),
                        sourceBlockObj.getLabel(), sourceBlockObj.getId() });
        Volume targetVolume = (Volume) targetBlockObj;
        Volume sourceVolume = (Volume) sourceBlockObj;
        targetVolume.setSrdfParent(new NamedURI(sourceBlockObj.getId(), sourceBlockObj.getLabel()));
        targetVolume.setPersonality(PersonalityTypes.TARGET.toString());
        if (sourceBlockObj instanceof Volume) {
            StringSet srdfTargets = sourceVolume.getSrdfTargets();
            if (null == srdfTargets) {
                srdfTargets = new StringSet();
            }
            srdfTargets.add(targetVolume.getId().toString());
            sourceVolume.setSrdfTargets(srdfTargets);
            sourceVolume.setPersonality(PersonalityTypes.SOURCE.toString());
        }
    }

    /**
     * Setup relationships between a clone and its parent BlockObject.
     *
     * @param clone the mirror BlockObject
     * @param parent the mirror's parent BlockObject
     * @param dbClient a reference to the database client
     */
    public static void setupCloneParentRelations(BlockObject clone, BlockObject parent, DbClient dbClient) {
        _logger.info("Setting up relationship between clone {} ({}) and parent {} ({})",
                new Object[] { clone.getLabel(), clone.getId(),
                        parent.getLabel(), parent.getId() });
        ((Volume) clone).setAssociatedSourceVolume(parent.getId());
        if (parent instanceof Volume) {
            Volume sourceVolume = (Volume) parent;
            StringSet associatedFullCopies = sourceVolume.getFullCopies();
            if (associatedFullCopies == null) {
                associatedFullCopies = new StringSet();
                sourceVolume.setFullCopies(associatedFullCopies);
            }
            associatedFullCopies.add(clone.getId().toString());
        }
    }

    /**
     * Setup relationships between a VPLEX backend volume and its parent VPLEX virtual volume BlockObject.
     *
     * @param clone the mirror BlockObject
     * @param parent the mirror's parent BlockObject
     * @param dbClient a reference to the database client
     */
    public static void setupVplexParentRelations(BlockObject child, BlockObject parent, DbClient dbClient) {
        _logger.info("Setting up relationship between VPLEX backend volume {} ({}) and virtual volume {} ({})",
                new Object[] { child.getLabel(), child.getId(),
                        parent.getLabel(), parent.getId() });
        if (parent instanceof Volume) {
            Volume parentVolume = (Volume) parent;
            StringSet associatedVolumes = parentVolume.getAssociatedVolumes();
            if (associatedVolumes == null) {
                associatedVolumes = new StringSet();
                parentVolume.setAssociatedVolumes(associatedVolumes);
            }
            associatedVolumes.add(child.getId().toString());
        }
    }

    /**
     * This method will clear the internal flags set during ingestion.
     * Before clearing the flags check if there is any unmanaged volume corresponding to the block object.
     * If yes, then perform the below operations:
     * 1) Find all the export masks corresponding to the unmanaged export masks of the unmanaged volume.
     * For each export mask:
     * a) Remove the block object from existing volumes of the export mask
     * b) Add the block object to user created volumes of the export mask
     * 2) Get the export groups corresponding to the export masks and add the block object to the export groups
     * 3) Remove the unmanaged volume from the unmanaged export mask
     * 4) Remove the unmanaged export mask from the unmanaged volume
     *
     * @param blockObject the BlockObject to clear flags on
     * @param updatedObjects a Set of DataObjects being updated related to the given BlockObject
     * @param dbClient a reference to the database client
     */
    public static void clearInternalFlags(IngestionRequestContext requestContext,
            BlockObject blockObject, Set<DataObject> updatedObjects, DbClient dbClient) {
        // for each block object, get the corresponding unmanaged volume.
        _logger.info("clearInternalFlags for blockObject " + blockObject.forDisplay());

        boolean isVplexBackendVolume = false;
        boolean isRPVolume = false;
        UnManagedVolume unManagedVolume = getUnManagedVolumeForBlockObject(blockObject, dbClient);
        if (unManagedVolume != null) {
            UnManagedVolume loadedUnmanagedVolume = requestContext.findDataObjectByType(UnManagedVolume.class, unManagedVolume.getId(),
                    false);
            unManagedVolume = ((loadedUnmanagedVolume != null) ? loadedUnmanagedVolume : unManagedVolume);
        }

        if (unManagedVolume != null) {

            // Check if this is a VPLEX backend volume, which we need to treat a little differently
            isVplexBackendVolume = VolumeIngestionUtil.isVplexBackendVolume(unManagedVolume);

            // Check if this is a RP volume
            isRPVolume = VolumeIngestionUtil.checkUnManagedResourceIsRecoverPointEnabled(unManagedVolume);

            // Get the exportGroupType from the unManagedVolume
            String exportGroupType = unManagedVolume.getVolumeCharacterstics().get(
                    SupportedVolumeCharacterstics.EXPORTGROUP_TYPE.toString());

            Set<URI> supportedVirtualArrays = new HashSet<URI>();
            supportedVirtualArrays.add(blockObject.getVirtualArray());
            // If this is a MetroPoint volume we're going to have multiple ExportGroups which may belong to more than one
            // virtual array
            if (blockObject instanceof Volume && RPHelper.isMetroPointVolume(dbClient, (Volume) blockObject)) {
                StringSet vplexBackendVolumes = PropertySetterUtil.extractValuesFromStringSet(
                        SupportedVolumeInformation.VPLEX_BACKEND_VOLUMES.toString(), unManagedVolume.getVolumeInformation());
                if (vplexBackendVolumes != null && !vplexBackendVolumes.isEmpty()) {
                    StringSet vplexBackendVolumeGUIDs = getListofVolumeIds(vplexBackendVolumes);
                    List<BlockObject> associatedVolumes = getVolumeObjects(vplexBackendVolumeGUIDs, requestContext, dbClient);
                    for (BlockObject associatedVolume : associatedVolumes) {
                        supportedVirtualArrays.add(associatedVolume.getVirtualArray());
                    }
                }
            }

            // If there are unmanaged export masks, get the corresponding ViPR export masks
            StringSet unmanagedExportMasks = unManagedVolume.getUnmanagedExportMasks();
            if (null != unmanagedExportMasks && !unmanagedExportMasks.isEmpty()) {
                List<URI> unManagedMaskUris = new ArrayList<URI>(Collections2.transform(unmanagedExportMasks,
                        CommonTransformerFunctions.FCTN_STRING_TO_URI));
                List<UnManagedExportMask> unManagedMasks = new ArrayList<UnManagedExportMask>();
                for (URI uri : unManagedMaskUris) {
                    UnManagedExportMask uem = requestContext.findDataObjectByType(UnManagedExportMask.class, uri, true);
                    if (uem != null) {
                        unManagedMasks.add(uem);
                    }
                }
                for (UnManagedExportMask unManagedExportMask : unManagedMasks) {
                    Map<URI, ExportMask> exportMaskMap = new HashMap<URI, ExportMask>();
                    List<URI> initiatorUris = new ArrayList<URI>(Collections2.transform(
                            unManagedExportMask.getKnownInitiatorUris(), CommonTransformerFunctions.FCTN_STRING_TO_URI));
                    for (URI ini : initiatorUris) {
                        List<URI> exportMaskUris = dbClient.queryByConstraint(AlternateIdConstraint.Factory
                                .getExportMaskInitiatorConstraint(ini.toString()));
                        if (null == exportMaskUris) {
                            continue;
                        }
                        for (URI eMaskUri : exportMaskUris) {
                            ExportMask eMask = requestContext.findDataObjectByType(ExportMask.class, eMaskUri, true);
                            if ((null != eMask) && (null != eMask.getStorageDevice()) && 
                                    eMask.getStorageDevice().equals(unManagedExportMask.getStorageSystemUri())) {
                                if (!exportMaskMap.containsKey(eMaskUri)) {
                                    _logger.info("Found Mask {} with matching initiator and matching Storage System", eMaskUri);
                                    exportMaskMap.put(eMaskUri, eMask);
                                }
                            } else {
                                _logger.info("Found Mask {} with matching initiator and unmatched Storage System. Skipping mask", eMaskUri);
                            }
                        }
                    }

                    Set<ExportGroup> exportGroups = new HashSet<ExportGroup>();
                    // Remove the block object from existing volumes and add to the user created volumes of the export mask
                    for (ExportMask exportMask : exportMaskMap.values()) {
                        String normalizedWWN = BlockObject.normalizeWWN(blockObject.getWWN());
                        if (null == normalizedWWN) {
                            throw IngestionException.exceptions.exportedVolumeIsMissingWwn(unManagedVolume.getLabel());
                        }
                        boolean foundExportMask = false;
                        if (exportMask.hasAnyExistingVolumes() && exportMask.getExistingVolumes().containsKey(normalizedWWN)) {
                            _logger.info(
                                    "Removing block object {} from existing volumes and adding to user created volumes of export mask {}",
                                    blockObject.getNativeGuid(), exportMask.getMaskName());
                            exportMask.removeFromExistingVolumes(blockObject);
                            exportMask.addToUserCreatedVolumes(blockObject);
                            updatedObjects.add(exportMask);
                            foundExportMask = true;
                            exportGroups.addAll(ExportMaskUtils.getExportGroups(dbClient, exportMask));
                        }
                        if (foundExportMask) {
                            _logger.info("breaking relationship between UnManagedExportMask {} and UnManagedVolume {}",
                                    unManagedExportMask.getMaskName(), unManagedVolume.forDisplay());
                            unManagedVolume.getUnmanagedExportMasks().remove(unManagedExportMask.getId().toString());
                            unManagedExportMask.getUnmanagedVolumeUris().remove(unManagedVolume.getId().toString());
                            updatedObjects.add(unManagedExportMask);
                        }
                    }
                    // If the mask for ingested volume contains JOURNAL keyword, make sure we add it to
                    // the ExportGroup created for journals
                    boolean isJournalExport = false;
                    if (unManagedExportMask.getMaskName().toLowerCase().contains(RP_JOURNAL)) {
                        isJournalExport = true;
                    }
                    _logger.info("exportGroupType is " + exportGroupType);
                    URI computeResource = requestContext.getCluster() != null ? requestContext.getCluster() : requestContext.getHost();
                    _logger.info("computeResource is " + computeResource);
                    // Add the block object to the export groups corresponding to the export masks
                    for (ExportGroup exportGroup : exportGroups) {
                        _logger.info("Processing exportGroup {} to add block object", exportGroup.forDisplay());
                        // only add to those export groups whose project and varray matches the block object
                        _logger.info("exportGroup.getType() is " + exportGroup.getType());
                        boolean exportGroupTypeMatches = (null != exportGroupType)
                                && exportGroupType.equalsIgnoreCase(exportGroup.getType());
                        boolean isRPJournalExportGroup = exportGroup.checkInternalFlags(Flag.RECOVERPOINT_JOURNAL);
                        // do not add RP source or target volumes to export group meant only for journals
                        // If the mask for ingested volume contains JOURNAL keyword, make sure we add it to
                        // the ExportGroup created for journals
                        if (isJournalExport && !isRPJournalExportGroup) {
                            _logger.info(
                                    "Block object is associated with RP journal mask but export group is not marked for RP journals. Not adding to the export group");
                            continue;
                        } else if (!isJournalExport && isRPJournalExportGroup) {
                            _logger.info(
                                    "Block object is not associated with RP journal mask but export group is marked for RP journals. Not adding to the export group");
                            continue;
                        }

                        if (exportGroup.getProject().getURI().equals(getBlockProject(blockObject)) &&
                                supportedVirtualArrays.contains(exportGroup.getVirtualArray()) &&
                                (exportGroupTypeMatches || isVplexBackendVolume)) {
                            // check if this ExportGroup URI has already been loaded in this ingestion request
                            ExportGroup loadedExportGroup = requestContext.findDataObjectByType(ExportGroup.class, exportGroup.getId(),
                                    false);
                            // if it wasn't found for update, check if it's tied to any ingestion contexts
                            if (loadedExportGroup == null) {
                                loadedExportGroup = requestContext.findExportGroup(
                                        exportGroup.getLabel(), exportGroup.getProject().getURI(),
                                        exportGroup.getVirtualArray(), computeResource, exportGroup.getType());
                            }
                            // if an ExportGroup for the URI and params was found, use it
                            if (loadedExportGroup != null) {
                                _logger.info("Adding block object {} to already-loaded export group {}",
                                        blockObject.getNativeGuid(), loadedExportGroup.getLabel());
                                exportGroup = loadedExportGroup;
                            } else {
                                _logger.info("Adding block object {} to newly-loaded export group {}",
                                        blockObject.getNativeGuid(), exportGroup.getLabel());
                                updatedObjects.add(exportGroup);
                            }
                            exportGroup.addVolume(blockObject.getId(), ExportGroup.LUN_UNASSIGNED);
                        }
                    }
                }
            } else {
                _logger.info("No unmanaged export masks found for the unmanaged volume {}", unManagedVolume.getNativeGuid());
            }

            if (canDeleteUnManagedVolume(unManagedVolume)) {
                _logger.info("Set unmanaged volume inactive: {}", unManagedVolume.forDisplay());
                unManagedVolume.setInactive(true);
                requestContext.getUnManagedVolumesToBeDeleted().add(unManagedVolume);
            } else {
                updatedObjects.add(unManagedVolume);
            }
        } else {
            _logger.info("No unmanaged volume found for the block object {}", blockObject.getNativeGuid());
        }

        blockObject.clearInternalFlags(BlockIngestOrchestrator.INTERNAL_VOLUME_FLAGS);

        // snapshot sessions
        // Do not clear the flags for snapshot sessions associated with RP volumes till the RP CG is fully ingested.
        if (getRPUnmanagedVolume(unManagedVolume, dbClient) == null) {
            clearSnapshotSessionsFlags(blockObject, updatedObjects, dbClient);
        }

        if ((blockObject instanceof Volume) && (isVplexBackendVolume || isRPVolume)) {
            // VPLEX backend volumes and RP protected volumes should still have the INTERNAL_OBJECT flag.
            // Note that snapshots can also be VPLEX backend volumes so make sure
            // to also check the type of the block object. We don't want a
            // BlockSnapshot instance to be made internal. The ingestion process
            // will also create Volume instance to represent the backend volume
            // and this is what will be marked internal.

            // RP volumes will be made visible when the RP CG is fully ingested

            blockObject.addInternalFlags(Flag.INTERNAL_OBJECT);
        }
    }

    /**
     * Return a BlockObject for the given native GUID String, or null if none found.
     *
     * @param nativeGUID the native GUID to look for
     * @param dbClient a reference to the database client
     * @return a BlockObject for the given native GUID String, or null if none found
     */
    public static BlockObject getBlockObject(String nativeGUID, DbClient dbClient) {
        _logger.info("Checking for unmanagedvolume {} [Volume] ingestion status.", nativeGUID);
        URIQueryResultList results = new URIQueryResultList();
        dbClient.queryByConstraint(AlternateIdConstraint.Factory.getVolumeNativeGuidConstraint(nativeGUID), results);
        Iterator<URI> blockObjectUris = results.iterator();
        if (blockObjectUris.hasNext()) {
            BlockObject blockObject = BlockObject.fetch(dbClient, blockObjectUris.next());
            if (!blockObject.getInactive()) {
                _logger.info("Found volume {} ingested.", nativeGUID);
                return blockObject;
            }
        }

        _logger.info("Checking for unmanagedvolume {} [Snap] ingestion status", nativeGUID);
        results = new URIQueryResultList();
        dbClient.queryByConstraint(AlternateIdConstraint.Factory.getBlockSnapshotsByNativeGuid(nativeGUID), results);
        blockObjectUris = results.iterator();
        if (blockObjectUris.hasNext()) {
            BlockObject blockObject = BlockObject.fetch(dbClient, blockObjectUris.next());
            if (!blockObject.getInactive()) {
                _logger.info("Found snapshot {} ingested.", nativeGUID);
                return blockObject;
            }
        }

        _logger.info("Checking for unmanagedvolume {} [Mirror] ingestion status", nativeGUID);
        results = new URIQueryResultList();
        dbClient.queryByConstraint(AlternateIdConstraint.Factory.getMirrorByNativeGuid(nativeGUID), results);
        blockObjectUris = results.iterator();
        if (blockObjectUris.hasNext()) {
            BlockObject blockObject = BlockObject.fetch(dbClient, blockObjectUris.next());
            if (!blockObject.getInactive()) {
                _logger.info("Found mirror {} ingested.", nativeGUID);
                return blockObject;
            }
        }

        _logger.warn("no BlockObject found in the database for native guid {}", nativeGUID);
        return null;
    }

    /**
     * Return the BlockObject Class of a given UnManagedVolume.
     *
     * @param unManagedVolume the UnManagedVolume to check
     * @return the BlockObject Class of a given UnManagedVolume
     */
    @SuppressWarnings("rawtypes")
    public static Class getBlockObjectClass(UnManagedVolume unManagedVolume) {
        Class blockObjectClass = Volume.class;
        if ((VolumeIngestionUtil.isSnapshot(unManagedVolume)) && (!VolumeIngestionUtil.isVplexBackendVolume(unManagedVolume))) {
            blockObjectClass = BlockSnapshot.class;
        } else if (VolumeIngestionUtil.isMirror(unManagedVolume)) {
            blockObjectClass = BlockMirror.class;
        }

        return blockObjectClass;
    }

    /**
     * Return a Set of native GUIDs for any uningested replicas for a set of replica BlockObjects.
     *
     * @param replicaVolumeGUIDs a list of all replica native GUIDs
     * @param replicaObjects a list of ingested replica object GUIDs
     * @return a Set of native GUIDs for any uningested replicas for a set of replica BlockObjects
     */
    public static Set<String> getUnIngestedReplicas(StringSet replicaVolumeGUIDs, List<BlockObject> replicaObjects) {
        StringSet replicas = new StringSet();
        for (BlockObject replica : replicaObjects) {
            replicas.add(replica.getNativeGuid().replace(VolumeIngestionUtil.VOLUME, VolumeIngestionUtil.UNMANAGEDVOLUME));
        }

        return Sets.difference(replicaVolumeGUIDs, replicas);
    }

    /**
     * Gets the URI of the Project for the given BlockObject.
     *
     * @param block the BlockObject to check
     * @return the Projct URI of the given BlockObject or null
     */
    public static URI getBlockProject(BlockObject block) {
        if (block.getClass() == Volume.class) {
            return ((Volume) block).getProject().getURI();
        } else if (block.getClass() == BlockSnapshot.class) {
            return ((BlockSnapshot) block).getProject().getURI();
        } else if (block.getClass() == BlockMirror.class) {
            return ((BlockMirror) block).getProject().getURI();
        }

        return null;
    }

    /**
     * Checks if a volume was ingested virtual-volume-only. An exception will be
     * thrown if the given operation is not supported on volumes ingested without
     * backend volumes.
     *
     * @param volume the Volume in question
     * @param operation a text description of the operation
     *            (for use in the Exception message)
     * @param dbClient Reference to a database client
     */
    public static void checkOperationSupportedOnIngestedVolume(Volume volume,
            ResourceOperationTypeEnum operation, DbClient dbClient) {
        if (volume.isIngestedVolumeWithoutBackend(dbClient)) {
            switch (operation) {
                case CREATE_VOLUME_FULL_COPY:
                case CREATE_VOLUME_SNAPSHOT:
                case EXPAND_BLOCK_VOLUME:
                case CREATE_VOLUME_MIRROR:
                case CHANGE_BLOCK_VOLUME_VARRAY:
                case UPDATE_CONSISTENCY_GROUP:
                case CREATE_SNAPSHOT_SESSION:
                    _logger.error("Operation {} is not permitted on ingested VPLEX volumes without backend volumes.", operation.getName());
                    throw APIException.badRequests.operationNotPermittedOnIngestedVolume(
                            operation.getName(), volume.getLabel());
                default:
                    return;
            }
        }
    }

    /**
     * Returns a List of UnManagedVolumes that are snapshots of the given
     * source UnManagedVolume.
     *
     * @param unManagedVolume the volume to check for snapshots
     * @param dbClient a reference to the database client
     *
     * @return a List of UnManagedVolumes that are snapshots of the given
     *         source UnManagedVolume
     */
    public static List<UnManagedVolume> getUnManagedSnaphots(UnManagedVolume unManagedVolume, DbClient dbClient) {
        List<UnManagedVolume> snapshots = new ArrayList<UnManagedVolume>();
        _logger.info("checking for snapshots related to unmanaged volume " + unManagedVolume.getLabel());
        if (checkUnManagedVolumeHasReplicas(unManagedVolume)) {
            StringSet snapshotNativeIds = PropertySetterUtil.extractValuesFromStringSet(
                    SupportedVolumeInformation.SNAPSHOTS.toString(),
                    unManagedVolume.getVolumeInformation());
            List<URI> snapshotUris = new ArrayList<URI>();
            if (null != snapshotNativeIds && !snapshotNativeIds.isEmpty()) {
                for (String nativeId : snapshotNativeIds) {
                    _logger.info("   found snapshot native id " + nativeId);
                    URIQueryResultList unManagedVolumeList = new URIQueryResultList();
                    dbClient.queryByConstraint(AlternateIdConstraint.Factory
                            .getVolumeInfoNativeIdConstraint(nativeId), unManagedVolumeList);
                    if (unManagedVolumeList.iterator().hasNext()) {
                        snapshotUris.add(unManagedVolumeList.iterator().next());
                    }
                }
            }
            if (!snapshotUris.isEmpty()) {
                snapshots = dbClient.queryObject(UnManagedVolume.class, snapshotUris, true);
                _logger.info("   returning snapshot objects: " + snapshots);
            }

        }

        return snapshots;
    }

    /**
     * Returns a List of UnManagedVolumes that are clones of the given
     * source UnManagedVolume.
     *
     * @param unManagedVolume the volume to check for clones
     * @param dbClient a reference to the database client
     *
     * @return a List of UnManagedVolumes that are clones of the given
     *         source UnManagedVolume
     */
    public static List<UnManagedVolume> getUnManagedClones(UnManagedVolume unManagedVolume, DbClient dbClient) {
        List<UnManagedVolume> clones = new ArrayList<UnManagedVolume>();
        _logger.info("checking for clones (full copies) related to unmanaged volume " + unManagedVolume.getLabel());
        if (checkUnManagedVolumeHasReplicas(unManagedVolume)) {
            StringSet cloneNativeIds = PropertySetterUtil.extractValuesFromStringSet(
                    SupportedVolumeInformation.FULL_COPIES.toString(),
                    unManagedVolume.getVolumeInformation());
            List<URI> cloneUris = new ArrayList<URI>();
            if (null != cloneNativeIds && !cloneNativeIds.isEmpty()) {
                for (String nativeId : cloneNativeIds) {
                    _logger.info("   found clone native id " + nativeId);
                    URIQueryResultList unManagedVolumeList = new URIQueryResultList();
                    dbClient.queryByConstraint(AlternateIdConstraint.Factory
                            .getVolumeInfoNativeIdConstraint(nativeId), unManagedVolumeList);
                    if (unManagedVolumeList.iterator().hasNext()) {
                        cloneUris.add(unManagedVolumeList.iterator().next());
                    }
                }
            }
            if (!cloneUris.isEmpty()) {
                clones = dbClient.queryObject(UnManagedVolume.class, cloneUris, true);
                _logger.info("   returning clone objects: " + clones);
            }

        }

        return clones;
    }

    /**
     * Find the HLU for the given UnManagedVolume and ExportMask (potentially
     * an HLU could be different across ExportMasks). This method will check
     * the UnManagedVolume's HLU_TO_EXPORT_LABEL_MAP VolumeInformation. This
     * should be formatted as a StringSetMap where each StringSet is a collection
     * of Strings in the format "exportMaskName=hlu".
     *
     * @param unManagedVolume the UnManagedVolume to check
     * @param exportMaskName the ExportMask to check by maskName
     *
     * @return an Integer representing the LUN number for this volume in this mask
     */
    public static Integer findHlu(UnManagedVolume unManagedVolume, String exportMaskName) {

        // TODO currently only VPLEX, VMAX, XtremIO and Unity unmanaged discovery processes
        // are creating this HLU_TO_EXPORT_LABEL_MAP --- this should also be added to other
        // unmanaged volume discovery services if the HLU is found to be required.
        // By default, if no mapping is found, a LUN_UNASSIGNED (-1) will be returned.

        StringSet hluMapEntries = PropertySetterUtil.extractValuesFromStringSet(
                SupportedVolumeInformation.HLU_TO_EXPORT_MASK_NAME_MAP.toString(),
                unManagedVolume.getVolumeInformation());

        Integer hlu = ExportGroup.LUN_UNASSIGNED;
        if (null != hluMapEntries) {
            for (String hluEntry : hluMapEntries) {
                // should be in the format exportMaskName=hlu
                // (i.e., mask name to hlu/lun number)
                if (hluEntry.startsWith(exportMaskName)) {
                    String[] hluEntryParts = hluEntry.split("=");
                    if (hluEntryParts.length == 2) {
                        // double check it matched the full mask name
                        // just in case there some kind of overlap
                        if (hluEntryParts[0].equals(exportMaskName)) {
                            String hluStr = hluEntryParts[1];
                            if (null != hluStr && !hluStr.isEmpty()) {
                                try {
                                    hlu = Integer.valueOf(hluStr);
                                    _logger.info("found HLU {} for {} in export mask "
                                            + exportMaskName, hlu,
                                            unManagedVolume.getLabel());
                                } catch (NumberFormatException ex) {
                                    _logger.warn("could not parse HLU entry from " + hluEntry);
                                }
                            }
                        }
                    }
                }
            }
        }

        return hlu;
    }

    /**
     * Create a BlockConsistencyGroup Object based on the passed in UnManagedConsistencyGroup object
     *
     * @param unManagedCG - the UnManagedConsistencyGroup object
     * @param project - the project which the consistency group will belong to
     * @param tenant - the tenant which the consistency group will belong to
     * @param dbClient
     * @return - the newly created BlockConsistencyGroup
     */
    public static BlockConsistencyGroup createCGFromUnManagedCG(UnManagedConsistencyGroup unManagedCG, Project project, TenantOrg tenant,
            DbClient dbClient) {
        // Create Consistency Group in db
        BlockConsistencyGroup consistencyGroup = new BlockConsistencyGroup();
        consistencyGroup.setId(URIUtil.createId(BlockConsistencyGroup.class));
        consistencyGroup.setLabel(unManagedCG.getLabel());
        consistencyGroup.setProject(new NamedURI(project.getId(), project.getLabel()));
        consistencyGroup.setTenant(project.getTenantOrg());
        consistencyGroup.setStorageController(unManagedCG.getStorageSystemUri());
        consistencyGroup.addSystemConsistencyGroup(unManagedCG.getStorageSystemUri().toString(), consistencyGroup.getLabel());
        consistencyGroup.addConsistencyGroupTypes(Types.LOCAL.name());
        return consistencyGroup;
    }

    /**
     * Checks to see if there is unmanaged volume corresponding to the passed block object.
     * First checks in the DB and if found, checks in the passed unmanaged volumes which have been ingested
     * and will be marked inactive later.
     *
     * @param blockObject the BlockObject to check for a related UnManagedVolume
     * @param ingestedUnManagedVolumes a List of UnManagedVolumes that have already been ingested
     * @param dbClient a reference to the database client
     * @return true if the given BlockObject has an UnManagedVolume associated
     */
    public static boolean hasUnManagedVolume(BlockObject blockObject, List<UnManagedVolume> ingestedUnManagedVolumes,
            DbClient dbClient) {
        UnManagedVolume umVolume = getUnManagedVolumeForBlockObject(blockObject, dbClient);
        if (umVolume != null && !umVolume.getInactive()) {
            // Check in the list of ingested unmanaged volumes. If present, then it will be marked for deletion
            for (UnManagedVolume umv : ingestedUnManagedVolumes) {
                if (umv.getId().equals(umVolume.getId())) {
                    _logger.info("Found the unmanaged volume {} in the list of ingested unmanaged volumes", umVolume.getId()
                            .toString());
                    return false;
                }
            }
            return true;
        }

        return false;
    }

    /**
     * Utility method to check if all the volumes in an unmanaged protection set have been ingested
     *
     * @param ingestedUnManagedVolumes List of unmanaged volumes which have been ingested
     * @param umpset the UnManagedProtectionSet to check
     * @param requestContext the current IngestionRequestContext
     * @param dbClient a reference to the database client
     * @return boolean if the all the volumes in the unmanaged protection set have been ingested
     */
    public static boolean validateAllVolumesInCGIngested(List<UnManagedVolume> ingestedUnManagedVolumes,
            UnManagedProtectionSet umpset, IngestionRequestContext requestContext, DbClient dbClient) {
        if (umpset == null) {
            _logger.warn("INGEST VALIDATION: unmanaged protection set is null");
            return false;
        }

        _logger.info("Checking if all volumes in UnManagedProtectionSet {} have been ingested yet...", umpset.forDisplay());
        // Make sure that none of the managed volumes still have a corresponding unmanaged volume. This means that there is
        // some information left to be ingested.
        if (umpset.getManagedVolumeIds() != null && !umpset.getManagedVolumeIds().isEmpty()) {
            boolean noUmvsLeft = true;
            for (String managedVolumeId : umpset.getManagedVolumeIds()) {
                BlockObject managedVolume = requestContext.findDataObjectByType(Volume.class, URI.create(managedVolumeId), true);
                if (hasUnManagedVolume(managedVolume, ingestedUnManagedVolumes, dbClient)) {
                    _logger.info(String
                            .format(
                                    "INGEST VALIDATION: Managed volume %s (%s) still has a corresponding unmanaged volume left which means that there is still some info to be ingested",
                                    managedVolume.getId(), managedVolume.forDisplay()));
                    noUmvsLeft = false;
                }
            }
            if (!noUmvsLeft) {
                return false;
            }
        }

        // Make sure the managed volumes match the unmanaged volumes and WWN list
        if (umpset.getUnManagedVolumeIds() != null && umpset.getManagedVolumeIds() != null && umpset.getVolumeWwns() != null &&
                umpset.getUnManagedVolumeIds().size() == umpset.getManagedVolumeIds().size() &&
                umpset.getManagedVolumeIds().size() == umpset.getVolumeWwns().size()) {
            _logger.info("INGEST VALIDATION: Found that all volumes associated with the RP CG have been ingested: " + umpset.getCgName());
            return true;
        }

        // Extremely unlikely #1: No unmanaged volume IDs in the protection set. We wouldn't have stored the unmanaged protection set in
        // this case.
        if (umpset.getUnManagedVolumeIds() == null) {
            String msg = String.format("INGEST VALIDATION: No unmanaged volumes found in unmanaged protection set: " + umpset.getCgName());
            _logger.error(msg);
            return false;
        }

        // Extremely unlikely #2: Every ingest operation puts a volume in this list.
        if (umpset.getManagedVolumeIds() == null) {
            String msg = String.format("INGEST VALIDATION: No managed volumes found in unmanaged protection set: " + umpset.getCgName());
            _logger.error(msg);
            return false;
        }

        // Extremely unlikely #3: See #1. We would not have created the protection set if there weren't volumes.
        if (umpset.getVolumeWwns() == null) {
            String msg = String.format("INGEST VALIDATION: No volume WWNs found in unmanaged protection set: " + umpset.getCgName());
            _logger.error(msg);
            return false;
        }

        // Very likely: We haven't quite ingested everything yet.
        if (!umpset.getUnManagedVolumeIds().isEmpty()) {
            String msg = String.format(
                    "INGEST VALIDATION: Found that the unmanaged protection set: %s is not yet ingestable because there " +
                            "are %d volumes to be ingested, however only %d volume have been ingested.",
                    umpset.getCgName(), umpset
                            .getVolumeWwns().size(),
                    umpset.getManagedVolumeIds().size());
            _logger.info(msg);
            // TODO: Iterate over the unmanaged volumes that we haven't ingested yet and print them up.
            return false;
        }

        if (umpset.getManagedVolumeIds().size() != umpset.getVolumeWwns().size()) {
            String msg = String.format(
                    "INGEST VALIDATION: Found that the unmanaged protection set: %s is not yet ingestable because there " +
                            " are %d volumes in the RP CG that are on arrays that are not under management.",
                    umpset.getCgName(), umpset
                            .getVolumeWwns().size() - umpset.getManagedVolumeIds().size());
            _logger.info(msg);
            // TODO: Iterate over the volume WWNs (maybe the array serial number?) that aren't in our management.
            return false;
        }

        _logger.info("INGEST VALIDATION: All of the volumes associated with RP CG " + umpset.getCgName() + " have been ingested.");
        return true;

    }

    /**
     * Get the unmanaged protection set corresponding to the unmanaged volume
     *
     * @param unManagedVolume the UnManagedVolume to find an UnManagedProtectionSet for
     * @param dbClient a reference to the database client
     * @return unmanaged protection set
     */
    public static UnManagedProtectionSet getUnManagedProtectionSetForUnManagedVolume(
            IngestionRequestContext requestContext, UnManagedVolume unManagedVolume, DbClient dbClient) {
        UnManagedProtectionSet umpset = null;
        // Find the UnManagedProtectionSet associated with this unmanaged volume
        List<UnManagedProtectionSet> umpsets = CustomQueryUtility.getUnManagedProtectionSetByUnManagedVolumeId(dbClient,
                unManagedVolume.getId().toString());
        Iterator<UnManagedProtectionSet> umpsetsItr = umpsets.iterator();
        if (umpsetsItr.hasNext()) {
            umpset = umpsetsItr.next();
        }

        if (umpset != null) {
            UnManagedProtectionSet alreadyLoadedUmpset = requestContext.findDataObjectByType(UnManagedProtectionSet.class, umpset.getId(),
                    false);
            if (alreadyLoadedUmpset != null) {
                umpset = alreadyLoadedUmpset;
            }
        }

        return umpset;
    }

    /**
     * Get the unmanaged protection set corresponding to the managed volume
     *
     * @param managedVolume the Volume object to find an UnManagedProtectionSet for
     * @param dbClient a reference to the database client
     * @return unmanaged protection set
     */
    public static UnManagedProtectionSet getUnManagedProtectionSetForManagedVolume(
            IngestionRequestContext requestContext, BlockObject managedVolume, DbClient dbClient) {
        UnManagedProtectionSet umpset = null;
        // Find the UnManagedProtectionSet associated with this managed volume
        List<UnManagedProtectionSet> umpsets = CustomQueryUtility.getUnManagedProtectionSetByManagedVolumeId(dbClient,
                managedVolume.getId().toString());
        Iterator<UnManagedProtectionSet> umpsetsItr = umpsets.iterator();
        if (umpsetsItr.hasNext()) {
            umpset = umpsetsItr.next();
        }

        if (umpset != null) {
            DataObject alreadyLoadedUmpset = requestContext.findInUpdatedObjects(umpset.getId());
            if (alreadyLoadedUmpset != null && (alreadyLoadedUmpset instanceof UnManagedProtectionSet)) {
                umpset = (UnManagedProtectionSet) alreadyLoadedUmpset;
            }
        }

        return umpset;
    }

    /**
     * Creates a protection set for the given unmanaged protection set, or finds one first
     * if it has already been created in another volume context within the scope of this
     * ingestion request.
     *
     * @param requestContext the current IngestionRequestContext
     * @param unManagedVolume the currently ingesting UnManagedVolume
     * @param umpset Unmanaged protection set for which a protection set has to be created
     * @param dbClient a reference to the database client
     * @return newly created protection set
     */
    public static ProtectionSet findOrCreateProtectionSet(
            IngestionRequestContext requestContext,
            UnManagedVolume unManagedVolume,
            UnManagedProtectionSet umpset, DbClient dbClient) {

        ProtectionSet pset = null;
        StringSetMap unManagedCGInformation = umpset.getCGInformation();
        String rpProtectionId = PropertySetterUtil.extractValueFromStringSet(
                SupportedCGInformation.PROTECTION_ID.toString(), unManagedCGInformation);

        // if this is a recover point ingestion context, check for an existing PSET in memory
        RecoverPointVolumeIngestionContext rpContext = null;
        if (requestContext instanceof RecoverPointVolumeIngestionContext) {
            rpContext = (RecoverPointVolumeIngestionContext) requestContext;
        } else if (requestContext.getVolumeContext(unManagedVolume.getNativeGuid()) instanceof RecoverPointVolumeIngestionContext) {
            rpContext = (RecoverPointVolumeIngestionContext) requestContext.getVolumeContext(unManagedVolume.getNativeGuid());
        }
        if (rpContext != null) {
            pset = rpContext.findExistingProtectionSet(
                    umpset.getCgName(), rpProtectionId, umpset.getProtectionSystemUri(), umpset.getNativeGuid());
        }

        if (pset == null) {
            pset = new ProtectionSet();
            pset.setId(URIUtil.createId(ProtectionSet.class));
            pset.setLabel(umpset.getCgName());
            pset.setProtectionId(rpProtectionId);
            pset.setProtectionStatus(ProtectionStatus.ENABLED.toString());
            pset.setProtectionSystem(umpset.getProtectionSystemUri());
            pset.setNativeGuid(umpset.getNativeGuid());
        }

        if (umpset.getManagedVolumeIds() != null) {
            for (String volumeID : umpset.getManagedVolumeIds()) {

                // Add all volumes (managed only) to the new protection set
                if (pset.getVolumes() == null) {
                    pset.setVolumes(new StringSet());
                }

                pset.getVolumes().add(volumeID);

                Volume volume = null;
                BlockObject bo = requestContext.getRootIngestionRequestContext().findCreatedBlockObject(URI.create(volumeID));
                if (bo != null && bo instanceof Volume) {
                    volume = (Volume) bo;
                }

                if (volume == null) {
                    _logger.error("Unable to retrieve volume : " + volumeID
                            + " from database or created volumes.  Ignoring in protection set ingestion.");
                    // this will be the expected case for a newly-ingested Volume (because it hasn't been saved yet,
                    // so we make sure to add the volume in RecoverPointVolumeIngestionContext.commitBackend
                    continue;
                }

                // Set the project value
                if (pset.getProject() == null) {
                    pset.setProject(volume.getProject().getURI());
                }
            }
        }

        _logger.info("Created new protection set: " + pset.getId().toString());
        return pset;
    }

    /**
     * Creates a block consistency group for the given protection set, or finds one first
     * if it has already been created in another volume context within the scope of this
     * ingestion request.
     *
     * @param requestContext the current IngestionRequestContext
     * @param unManagedVolume the currently ingesting UnManagedVolume
     * @param pset the ProtectionSet
     * @param dbClient a reference to the database client
     * @return a BlockConsistencyGroup for the volume context and ProtectionSet
     */
    public static BlockConsistencyGroup findOrCreateRPBlockConsistencyGroup(
            IngestionRequestContext requestContext, UnManagedVolume unManagedVolume,
            ProtectionSet pset, DbClient dbClient) {

        BlockConsistencyGroup cg = null;
        Project project = dbClient.queryObject(Project.class, pset.getProject());
        NamedURI projectNamedUri = new NamedURI(pset.getProject(), project.getLabel());

        // if this is a recover point ingestion context, check for an existing CG in memory
        RecoverPointVolumeIngestionContext rpContext = null;
        if (requestContext instanceof RecoverPointVolumeIngestionContext) {
            rpContext = (RecoverPointVolumeIngestionContext) requestContext;
        } else if (requestContext.getVolumeContext(unManagedVolume.getNativeGuid()) instanceof RecoverPointVolumeIngestionContext) {
            rpContext = (RecoverPointVolumeIngestionContext) requestContext.getVolumeContext(unManagedVolume.getNativeGuid());
        }
        if (rpContext != null) {
            cg = rpContext.findExistingBlockConsistencyGroup(pset.getLabel(), projectNamedUri, project.getTenantOrg());
        }

        // Find the source volume in the protection set so we can set the virtual array in the consistency group
        URI varrayId = null;
        URI storageSystemId = null;
        if (pset.getVolumes() != null) {
            for (String volumeIdStr : pset.getVolumes()) {
                Volume volume = requestContext.findDataObjectByType(Volume.class, URI.create(volumeIdStr), true);
                if (volume != null) {
                    if (PersonalityTypes.SOURCE.name().equalsIgnoreCase(volume.getPersonality())) {
                        varrayId = volume.getVirtualArray();
                        if (volume.isVPlexVolume(dbClient)) {
                            storageSystemId = volume.getStorageController();
                        }
                    }
                }
            }
        }

        if (cg == null) {
            cg = new BlockConsistencyGroup();
            cg.setId(URIUtil.createId(BlockConsistencyGroup.class));
            cg.setLabel(pset.getLabel());
            cg.setProject(projectNamedUri);
            cg.addConsistencyGroupTypes(Types.RP.name());
            // By default, the array consistency is false. However later when we iterate over volumes in the BCG and we
            // see any replicationGroupInstance information, we'll flip this bit to true. (See decorateRPVolumesCGInfo())
            cg.setArrayConsistency(false);
            cg.setTenant(project.getTenantOrg());
            cg.setVirtualArray(varrayId);
            cg.setStorageController(storageSystemId);
            _logger.info("Created new block consistency group: " + cg.getId().toString());
        }

        cg.addSystemConsistencyGroup(pset.getProtectionSystem().toString(), pset.getLabel());
        return cg;
    }

    /**
     * Decorate the RP volumes with the protection set and consistency group info after the RP CG has been fully ingested
     *
     * @param rpVolumes RP Volumes
     * @param pset protection set
     * @param rpCG RP consistency group
     * @param updatedObjects Set of objects updated
     * @param dbClient a reference to the database client
     */
    public static void decorateRPVolumesCGInfo(List<Volume> rpVolumes, ProtectionSet pset, BlockConsistencyGroup rpCG,
            Set<DataObject> updatedObjects, DbClient dbClient, IngestionRequestContext requestContext) {
        for (Volume volume : rpVolumes) {
            // Set references to protection set/CGs properly in each volume
            volume.setConsistencyGroup(rpCG.getId());
            volume.setProtectionSet(new NamedURI(pset.getId(), pset.getLabel()));
            volume.clearInternalFlags(BlockRecoverPointIngestOrchestrator.INTERNAL_VOLUME_FLAGS);

            // Set the proper flags on the journal volumes.
            if (volume.getPersonality().equalsIgnoreCase(Volume.PersonalityTypes.METADATA.toString())) {
                volume.addInternalFlags(Flag.INTERNAL_OBJECT, Flag.SUPPORTS_FORCE);
            }

            _logger.info("Updating volume " + volume.getLabel() + " flags/settings to " + volume.getInternalFlags());

            // Find any backing volumes associated with vplex volumes and add the CG reference to them as well.
            if (volume.isVPlexVolume(dbClient)) {
                // volume may not have been ingested with backend volumes
                if (volume.getAssociatedVolumes() != null) {
                    for (String associatedVolumeIdStr : volume.getAssociatedVolumes()) {
                        // Find the associated volumes using the context maps or the db if they are already there
                        Volume associatedVolume = requestContext.findDataObjectByType(
                                Volume.class, URI.create(associatedVolumeIdStr), true);
                        if (associatedVolume != null) {
                            _logger.info("Setting BlockConsistencyGroup {} on VPLEX backend Volume {}",
                                    rpCG.forDisplay(), associatedVolume.forDisplay());
                            if (NullColumnValueGetter.isNotNullValue(associatedVolume.getReplicationGroupInstance())) {
                                _logger.info(String.format(
                                        "Turning on array consistency on the consistency group because CG info exists on volume %s",
                                        associatedVolume.getLabel()));
                                rpCG.setArrayConsistency(true);
                            }
                            associatedVolume.setConsistencyGroup(rpCG.getId());
                            updatedObjects.add(associatedVolume);
                        } else {
                            // This may not be a failure if we're not ingesting backing volumes. Put a warning to the log.
                            _logger.warn("Could not find the volume in DB or volume contexts: " + associatedVolumeIdStr);
                        }
                    }
                }
            }

            // Check for CG information, which tells us that this CG is array consistent (ignore VPLEX, it uses replicationGroupInstance
            // in a transient way during ingestion and will be cleared at the end of ingestion.
            if (!volume.isVPlexVolume(dbClient) && NullColumnValueGetter.isNotNullValue(volume.getReplicationGroupInstance())) {
                _logger.info(String.format("Turning on array consistency on the consistency group because CG info exists on volume %s",
                        volume.getLabel()));
                rpCG.setArrayConsistency(true);
            }

            updatedObjects.add(volume);
        }
    }

    /**
     * Checks whether we are ingesting the last UnManagedVolume in the UnManagedConsistencyGroup.
     *
     * @param unManagedCG - UnManaged CG to verify
     * @param unManagedVolume - unmanagedvolume to ingest.
     * @return true if we are ingesting the last UnManagedVolume in the UnManagedConsistencyGroup
     */
    public static boolean isLastUnManagedVolumeToIngest(UnManagedConsistencyGroup unManagedCG, UnManagedVolume unManagedVolume) {
        return unManagedCG.getUnManagedVolumesMap().containsKey(unManagedVolume.getNativeGuid())
                && unManagedCG.getUnManagedVolumesMap().size() == 1;
    }

    /**
     * Return the UnManagedConsistencyGroup in which the unManagedVolume belongs to.
     *
     * @param unManagedVolume - UnManagedVolume object.
     * @param dbClient - dbClient instance.
     * @return the UnManagedConsistencyGroup in which the unManagedVolume belongs to
     */
    public static UnManagedConsistencyGroup getUnManagedConsistencyGroup(UnManagedVolume unManagedVolume, DbClient dbClient) {
        UnManagedConsistencyGroup unManagedCG = null;
        String unManagedCGURI = PropertySetterUtil.extractValueFromStringSet(
                SupportedVolumeInformation.UNMANAGED_CONSISTENCY_GROUP_URI.toString(), unManagedVolume.getVolumeInformation());
        if (unManagedCGURI != null) {
            unManagedCG = dbClient.queryObject(UnManagedConsistencyGroup.class, URI.create(unManagedCGURI));
        }
        return unManagedCG;
    }

    /**
     * Creates a BlockConsistencyGroup if it doesn't exist only when we are ingesting the last volume in unmanaged consistencygroup.
     *
     * In case if the volume is protected by RP or VPLEX, we should not create CG.
     *
     * @param unManagedVolume - UnManagedVolume object.
     * @param blockObj - Ingested BlockObject
     * @param context - current unManagedVolume Ingestion context.
     * @param dbClient - dbClient instance.
     * @return BlockConsistencyGroup
     */
    public static BlockConsistencyGroup getBlockObjectConsistencyGroup(UnManagedVolume unManagedVolume, BlockObject blockObj,
            IngestionRequestContext context, DbClient dbClient) {
        UnManagedConsistencyGroup umcg = getUnManagedConsistencyGroup(unManagedVolume, dbClient);
        if (umcg != null) {
            // Check if the UnManagedConsistencyGroup is present in the volume context which should have the updated info
            UnManagedConsistencyGroup umcgInContext = context.findUnManagedConsistencyGroup(umcg.getLabel());
            if (umcgInContext != null) {
                umcg = umcgInContext;
            }
        }

        // In the case where IS_VOLUME_IN_CONSISTENCYGROUP flag is set to TRUE, but there is no UnManagedConsistencyGroup, we
        // can't perform this check fully and need to return null. This will occur with VMAX/VNX volumes in CGs until we have
        // CG ingestion support for such volumes.
        if (umcg == null || umcg.getUnManagedVolumesMap() == null) {
            _logger.info("There is no unmanaged consistency group associated with unmanaged volume {}, however " +
                    "the volume has the IS_VOLUME_IN_CONSISTENCYGROUP flag set to true.  Ignoring CG operation" +
                    " as there is not enough information to put this volume in a CG by itself.", unManagedVolume.getNativeGuid());
            return null;
        }
        List<UnManagedConsistencyGroup> umcgsToUpdate = context.getVolumeContext().getUmCGObjectsToUpdate();
        boolean isLastUmvToIngest = isLastUnManagedVolumeToIngest(umcg, unManagedVolume);
        boolean isVplexOrRPProtected = isRPOrVplexProtected(unManagedVolume);
        if (isVplexOrRPProtected || !isLastUmvToIngest) {
            _logger.info(
                    "Ignoring the CG creation as the volume is either isVplexRPProtected:{} or isLastUmvToIngest: {} exists to ingest.",
                    isLastUmvToIngest, isVplexOrRPProtected);
            _logger.info("Remaining volumes in CG to ingest: {}", umcg.getUnManagedVolumesMap());
            // set ReplicationGroupInstance in the block object.
            blockObj.setReplicationGroupInstance(umcg.getLabel());
            if (blockObj instanceof BlockSnapshot) {
                // Check if the unmanaged volume has SNAPSHOT_CONSISTENCY_GROUP_NAME property populated. If yes,
                // use that for replicationGroupInstance
                String snapsetName = PropertySetterUtil.extractValueFromStringSet(
                        SupportedVolumeInformation.SNAPSHOT_CONSISTENCY_GROUP_NAME.toString(), unManagedVolume.getVolumeInformation());
                if (snapsetName != null && !snapsetName.isEmpty()) {
                    blockObj.setReplicationGroupInstance(snapsetName);
                }
            }
            updateVolumeInUnManagedConsistencyGroup(umcg, unManagedVolume, blockObj);
            umcgsToUpdate.add(umcg);
            return null;
        }
        // If the UMV is last volume, mark the UnManagedConsistencyGroup inactive to true.
        if (isLastUmvToIngest) {
            umcg.setInactive(true);
            umcgsToUpdate.add(umcg);
        }

        if (null == umcg || null == umcg.getLabel()) {
            _logger.warn("UnManaged volume {} CG doesn't have label. Hence exiting",
                    unManagedVolume.getNativeGuid());
            return null;
        }

        String cgName = umcg.getLabel();
        StorageSystem storageSystem = dbClient.queryObject(StorageSystem.class,
                unManagedVolume.getStorageSystemUri());

        _logger.info("UnManagedVolume {} is added to consistency group {}",
                unManagedVolume.getLabel(), cgName);
        URI projectUri = context.getProject().getId();
        URI tenantUri = context.getTenant().getId();
        URI varrayUri = context.getVarray(unManagedVolume).getId();
        VirtualPool vpool = context.getVpool(unManagedVolume);
        if (!vpool.getMultivolumeConsistency()) {
            _logger.warn("The requested Virtual Pool {} does not have "
                    + "the Multi-Volume Consistency flag set, and this volume "
                    + "is part of a consistency group.", vpool.getLabel());
            throw IngestionException.exceptions
                    .unmanagedVolumeVpoolConsistencyGroupMismatch(vpool.getLabel(), unManagedVolume.getLabel());
        } else {

            List<BlockConsistencyGroup> groups = CustomQueryUtility
                    .queryActiveResourcesByConstraint(dbClient,
                            BlockConsistencyGroup.class, PrefixConstraint.Factory
                                    .getFullMatchConstraint(
                                            BlockConsistencyGroup.class, "label",
                                            cgName));

            BlockConsistencyGroup potentialUnclaimedCg = null;
            if (!groups.isEmpty()) {
                for (BlockConsistencyGroup cg : groups) {
                    if (validateCGProjectDetails(cg, storageSystem, projectUri, tenantUri, varrayUri, unManagedVolume.getLabel(), cgName,
                            dbClient)) {
                        return cg;
                    }
                    URI storageControllerUri = cg.getStorageController();
                    URI virtualArrayUri = cg.getVirtualArray();
                    if (NullColumnValueGetter.isNullURI(storageControllerUri) && NullColumnValueGetter.isNullURI(virtualArrayUri)) {
                        potentialUnclaimedCg = cg;
                    }
                }
            }

            // if not match on label, project, tenant, storage array, and virtual array
            // was found, then we can return the one found with null storage array and
            // virtual array. this would indicate the user created the CG, but hadn't
            // used it yet in creating a volume
            if (null != potentialUnclaimedCg) {
                potentialUnclaimedCg.addConsistencyGroupTypes(Types.LOCAL.name());
                potentialUnclaimedCg.setStorageController(storageSystem.getId());
                potentialUnclaimedCg.setVirtualArray(varrayUri);
                return potentialUnclaimedCg;
            }

            _logger.info(String
                    .format("Did not find an existing CG named %s that is associated already with the requested device %s and Virtual Array %s. ViPR will create a new one.",
                            cgName, storageSystem.getNativeGuid(), varrayUri));
            // create a new consistency group
            BlockConsistencyGroup cg = new BlockConsistencyGroup();
            cg.setId(URIUtil.createId(BlockConsistencyGroup.class));
            cg.setLabel(cgName);
            if (NullColumnValueGetter.isNotNullValue(umcg.getNativeId())) {
                cg.setNativeId(umcg.getNativeId());
            }

            cg.setProject(new NamedURI(projectUri, context.getProject().getLabel()));
            cg.setTenant(context.getProject().getTenantOrg());
            cg.addConsistencyGroupTypes(Types.LOCAL.name());
            cg.addSystemConsistencyGroup(storageSystem.getId().toString(), cgName);
            cg.setStorageController(storageSystem.getId());
            cg.setVirtualArray(varrayUri);
            cg.setArrayConsistency(false);
            return cg;
        }
    }

    /**
     * Returns true if the given UnManagedVolume is a Vplex Backend volume or RP Enable volume.
     *
     * @param unManagedVolume : UnManagedVolume to verify
     * @return - true if it is vplex backend or RP enabled volume
     *         - false in any other cases.
     */
    private static boolean isRPOrVplexProtected(UnManagedVolume unManagedVolume) {
        return isVplexBackendVolume(unManagedVolume) || checkUnManagedResourceIsRecoverPointEnabled(unManagedVolume);
    }

    /**
     * Returns true if the given UnManagedExportMask is for a RecoverPoint Export.
     *
     * @param uem the UnManagedExportMask to check
     * @param dbClient a reference to the database client
     * @return true if the given UnManagedExportMask is for a RecoverPoint Export
     */
    public static boolean isRpExportMask(UnManagedExportMask uem, DbClient dbClient) {
        for (String wwn : uem.getKnownInitiatorNetworkIds()) {
            List<URI> protectionSystemUris = dbClient.queryByType(ProtectionSystem.class, true);
            List<ProtectionSystem> protectionSystems = dbClient.queryObject(ProtectionSystem.class, protectionSystemUris);
            for (ProtectionSystem protectionSystem : protectionSystems) {
                for (Entry<String, AbstractChangeTrackingSet<String>> siteInitEntry : protectionSystem.getSiteInitiators().entrySet()) {
                    if (siteInitEntry.getValue().contains(wwn)) {
                        _logger.info("this is a RecoverPoint related UnManagedExportMask: " + uem.getMaskName());
                        return true;
                    }
                }
            }
        }

        return false;
    }

    /**
     * Validate the CG project & tenant details with the ingesting project & tenant uri.
     *
     * @param cg - Existing CG to compare.
     * @param storageSystem - UnManagedVolume system
     * @param projectUri - Project in which unmanagedvolume is getting ingested.
     * @param tenantUri - Project in which unmanagedvolume is getting ingested.
     * @param varrayUri - Project in which unmanagedvolume is getting ingested.
     * @param umvLabel - UnManagedVolume label.
     * @param cgName - UnManagedConsistencyGroup label.
     * @param dbClient - dbClient reference.
     * @return true if a matching BlockConsistencyGroup was found
     */
    private static boolean validateCGProjectDetails(BlockConsistencyGroup cg, StorageSystem storageSystem, URI projectUri, URI tenantUri,
            URI varrayUri, String umvLabel, String cgName, DbClient dbClient) {
        if (null != cg.getProject() && !NullColumnValueGetter.isNullURI(cg.getProject().getURI()) &&
                null != cg.getTenant() && !NullColumnValueGetter.isNullURI(cg.getTenant().getURI())) {
            // Check that the tenant and project are a match
            if (cg.getProject().getURI().equals(projectUri) &&
                    cg.getTenant().getURI().equals(tenantUri)) {
                URI storageControllerUri = cg.getStorageController();
                URI virtualArrayUri = cg.getVirtualArray();
                // need to check for several matching properties
                if (!NullColumnValueGetter.isNullURI(storageControllerUri) && !NullColumnValueGetter.isNullURI(virtualArrayUri)) {
                    if (storageControllerUri.equals(storageSystem.getId()) &&
                            virtualArrayUri.equals(varrayUri)) {
                        _logger.info("Found a matching BlockConsistencyGroup {} "
                                + "for volume {}.", cgName, umvLabel);
                        cg.addConsistencyGroupTypes(Types.LOCAL.name());
                        dbClient.updateObject(cg);
                        return true;
                    }
                }

            }
        }
        return false;
    }

    /**
     * Returns all the BlockObjects belong to the CG in the current context.
     *
     * @param cg - ConsistencyGroup object.
     * @param requestContext - current unManagedVolume Ingestion context.
     * @return - Collection BlockObjects in cg in given context.
     */
    public static Collection<BlockObject> getAllBlockObjectsInCg(BlockConsistencyGroup cg, IngestionRequestContext requestContext) {
        // Search for block objects with the CG's ID in our context and the database, if needed.
        Set<BlockObject> blockObjects = new HashSet<BlockObject>();

        for (BlockObject bo : requestContext.getBlockObjectsToBeCreatedMap().values()) {
            if (URIUtil.identical(bo.getConsistencyGroup(), cg.getId())) {
                blockObjects.add(bo);
            }
        }

        for (Set<DataObject> doList : requestContext.getDataObjectsToBeUpdatedMap().values()) {
            for (DataObject dobj : doList) {
                if (!(dobj instanceof BlockObject)) {
                    continue;
                }
                BlockObject bo = (BlockObject) dobj;
                if (URIUtil.identical(bo.getConsistencyGroup(), cg.getId())) {
                    blockObjects.add(bo);
                }
            }
        }

        if (requestContext.getVolumeContext() instanceof RecoverPointVolumeIngestionContext) {
            for (Set<DataObject> doList : ((RecoverPointVolumeIngestionContext) requestContext.getVolumeContext())
                    .getDataObjectsToBeUpdatedMap().values()) {
                for (DataObject dobj : doList) {
                    if (!(dobj instanceof BlockObject)) {
                        continue;
                    }
                    BlockObject bo = (BlockObject) dobj;
                    if (URIUtil.identical(bo.getConsistencyGroup(), cg.getId())) {
                        blockObjects.add(bo);
                    }
                }
            }

            // Add the RP protected volumes
            for (Entry<String, BlockObject> entry : ((RecoverPointVolumeIngestionContext) requestContext.getVolumeContext())
                    .getBlockObjectsToBeCreatedMap().entrySet()) {
                BlockObject boObj = entry.getValue();
                if (URIUtil.identical(boObj.getConsistencyGroup(), cg.getId())) {
                    blockObjects.add(boObj);
                }
            }
        }

        if (requestContext.getVolumeContext() instanceof VplexVolumeIngestionContext) {
            for (Set<DataObject> doList : ((VplexVolumeIngestionContext) requestContext.getVolumeContext()).getDataObjectsToBeUpdatedMap()
                    .values()) {
                for (DataObject dobj : doList) {
                    if (!(dobj instanceof BlockObject)) {
                        continue;
                    }
                    BlockObject bo = (BlockObject) dobj;
                    if (URIUtil.identical(bo.getConsistencyGroup(), cg.getId())) {
                        blockObjects.add(bo);
                    }
                }
            }

            // Add the VPLEX backend volumes
            for (Entry<String, BlockObject> entry : ((VplexVolumeIngestionContext) requestContext.getVolumeContext())
                    .getBlockObjectsToBeCreatedMap().entrySet()) {
                BlockObject boObj = entry.getValue();
                if (URIUtil.identical(boObj.getConsistencyGroup(), cg.getId())) {
                    blockObjects.add(boObj);
                }
            }
        }

        return blockObjects;
    }

    /**
     * Convenience method to find the a volume from the database or maps based
     * on ingestion.
     *
     * This is an alternative to using the context objects directly.
     *
     * @param dbClient DbClient reference
     * @param createdMap Map of created objects
     * @param updatedMap Map of updated objects
     * @param volumeId The id of the volume to find
     * @return The volume, or null if nothing can be found.
     */
    public static Volume findVolume(DbClient dbClient, Map<String, BlockObject> createdMap, Map<String, Set<DataObject>> updatedMap,
            String volumeId) {
        if (volumeId == null) {
            return null;
        }

        BlockObject blockObject = null;
        URI volumeURI = URI.create(volumeId);

        if (createdMap != null) {
            // Check the created map
            for (BlockObject bo : createdMap.values()) {
                if (bo.getId() != null && volumeURI.toString().equals(bo.getId().toString())) {
                    blockObject = bo;
                    break;
                }
            }
        }

        if (updatedMap != null) {
            // Check the updated map
            for (Set<DataObject> objectsToBeUpdated : updatedMap.values()) {
                for (DataObject o : objectsToBeUpdated) {
                    if (o.getId().equals(volumeURI)) {
                        blockObject = (BlockObject) o;
                        break;
                    }
                }
            }
        }

        if (dbClient != null) {
            // Lastly, check the db
            if (blockObject == null) {
                blockObject = (BlockObject) dbClient.queryObject(volumeURI);
            }
        }

        Volume volume = null;
        if (blockObject != null && blockObject instanceof Volume) {
            _logger.info("\t Found volume object: " + blockObject.forDisplay());
            volume = (Volume) blockObject;
        }

        return volume;
    }

    /**
     * Sets up the Recover Point CG by creating the protection set, block CG and associating the RP volumes
     * with the protection set and the block CG.
     * It also clears the RP volumes' replicas' flags.
     *
     * @param requestContext current unManagedVolume Ingestion context.
     * @param umpset Unmanaged protection set for which a protection set has to be created
     * @param unManagedVolume the current iterating UnManagedVolume
     * @param updatedObjects a Set of DataObjects to be updated in the database at the end of ingestion
     * @param dbClient - dbClient reference.
     */
    public static void setupRPCG(IngestionRequestContext requestContext, UnManagedProtectionSet umpset, UnManagedVolume unManagedVolume,
            Set<DataObject> updatedObjects, DbClient dbClient) {

        _logger.info("All volumes in UnManagedProtectionSet {} have been ingested, creating RecoverPoint Consistency Group now",
                umpset.forDisplay());

        ProtectionSet pset = VolumeIngestionUtil.findOrCreateProtectionSet(requestContext, unManagedVolume, umpset, dbClient);
        BlockConsistencyGroup cg = VolumeIngestionUtil.findOrCreateRPBlockConsistencyGroup(requestContext, unManagedVolume, pset, dbClient);

        List<Volume> volumes = new ArrayList<Volume>();
        StringSet managedVolumesInDB = new StringSet(pset.getVolumes());
        // First try to get the RP volumes from the updated objects list. This will have the latest info for
        // the RP volumes. If not found in updated objects list, get from the DB.
        for (String volumeId : pset.getVolumes()) {
            DataObject bo = requestContext.findInUpdatedObjects(URI.create(volumeId));
            if (null != bo && bo instanceof Volume) {
                _logger.info("\tadding volume object " + bo.forDisplay());
                volumes.add((Volume) bo);
                managedVolumesInDB.remove(bo.getId().toString());
            }
        }

        if (!managedVolumesInDB.isEmpty()) {
            Iterator<Volume> volumesItr = dbClient.queryIterativeObjects(Volume.class, URIUtil.toURIList(managedVolumesInDB));
            while (volumesItr.hasNext()) {
                Volume volume = volumesItr.next();
                _logger.info("\tadding volume object " + volume.forDisplay());
                volumes.add(volume);
                updatedObjects.add(volume);
                managedVolumesInDB.remove(volume.getId().toString());
            }

            for (String remainingVolumeId : managedVolumesInDB) {
                BlockObject bo = requestContext.getRootIngestionRequestContext().findCreatedBlockObject(URI.create(remainingVolumeId));
                if (null != bo && bo instanceof Volume) {
                    _logger.info("\tadding volume object " + bo.forDisplay());
                    volumes.add((Volume) bo);
                }
            }
        }

        VolumeIngestionUtil.decorateRPVolumesCGInfo(volumes, pset, cg, updatedObjects, dbClient, requestContext);
        clearPersistedReplicaFlags(requestContext, volumes, updatedObjects, dbClient);
        clearReplicaFlagsInIngestionContext(requestContext, volumes, dbClient);

        RecoverPointVolumeIngestionContext rpContext = null;

        // the RP volume ingestion context will take care of persisting the
        // new objects and deleting the old UnManagedProtectionSet
        if (requestContext instanceof RecoverPointVolumeIngestionContext) {
            rpContext = (RecoverPointVolumeIngestionContext) requestContext;
        } else if (requestContext.getVolumeContext() instanceof RecoverPointVolumeIngestionContext) {
            rpContext = (RecoverPointVolumeIngestionContext) requestContext.getVolumeContext();
        }

        if (rpContext != null) {
            _logger.info("setting managed BlockConsistencyGroup on RecoverPoint context {} to {}", rpContext, cg);
            rpContext.setManagedBlockConsistencyGroup(cg);
            rpContext.getCGObjectsToCreateMap().put(cg.getId().toString(), cg);
            _logger.info("setting managed ProtectionSet on RecoverPoint context {} to {}", rpContext, pset);
            rpContext.setManagedProtectionSet(pset);
        } else {
            // In case of replica ingested last, the ingestion context will not be RecoverPointVolumeIngestionContext
            if (requestContext.getVolumeContext() instanceof BlockVolumeIngestionContext) {
                // In order to decorate the CG properly with all system types, we need to add the CG to the context to be persisted later.
                _logger.info("Adding BlockConsistencyGroup {} to the BlockVolumeIngestContext (hash {})", cg.forDisplay(), cg.hashCode());
                ((BlockVolumeIngestionContext) requestContext.getVolumeContext()).getCGObjectsToCreateMap().put(cg.getId().toString(), cg);
            } else {
                _logger.info("Persisting BlockConsistencyGroup {} (hash {})", cg.forDisplay(), cg.hashCode());
                dbClient.createObject(cg);
            }

            _logger.info("Persisting ProtectionSet {} (hash {})", pset.forDisplay(), pset.hashCode());
            dbClient.createObject(pset);
            // the protection set was created, so delete the unmanaged one
            _logger.info("Deleting UnManagedProtectionSet {} (hash {})", umpset.forDisplay(), umpset.hashCode());
            dbClient.removeObject(umpset);
        }
    }

    /**
     * Make the snaps/mirrors/clones of the RP volume to be visible after the RP CG is fully ingested
     *
     * @param volumes a List of Volume Objects to check
     * @param updatedObjects a Set of DataObjects to be updated in the database at the end of ingestion
     * @param dbClient - dbClient reference.
     */
    public static void clearPersistedReplicaFlags(IngestionRequestContext requestContext, List<Volume> volumes,
            Set<DataObject> updatedObjects, DbClient dbClient) {
        for (Volume volume : volumes) {
            if (!Volume.PersonalityTypes.METADATA.toString().equals(volume.getPersonality())) {
                clearFullCopiesFlags(requestContext, volume, updatedObjects, dbClient);
                clearMirrorsFlags(requestContext, volume, updatedObjects, dbClient);
                clearSnapshotsFlags(requestContext, volume, updatedObjects, dbClient);
                clearSnapshotSessionsFlags(volume, updatedObjects, dbClient);
                clearAssociatedVolumesReplicaFlags(requestContext, volume, updatedObjects, dbClient);
                volume.clearInternalFlags(BlockIngestOrchestrator.INTERNAL_VOLUME_FLAGS);
            }
        }
    }

    /**
     * Clear the flags of the snapshot sessions of the RP volume
     *
     * @param blockObject the block Object to clear flags on
     * @param updatedObjects a Set of DataObjects to be updated in the database at the end of ingestion
     * @param dbClient dbClient reference.
     */
    public static void clearSnapshotSessionsFlags(BlockObject blockObject, Set<DataObject> updatedObjects, DbClient dbClient) {
        URIQueryResultList queryResults = new URIQueryResultList();
        dbClient.queryByConstraint(ContainmentConstraint.Factory.getParentSnapshotSessionConstraint(blockObject.getId()), queryResults);
        Iterator<URI> resultsIter = queryResults.iterator();
        while (resultsIter.hasNext()) {
            BlockSnapshotSession snapSession = dbClient.queryObject(BlockSnapshotSession.class, resultsIter.next());
            _logger.info("Clearing internal volume flag of snapshot session {} of RP volume {}", snapSession.getLabel(),
                    blockObject.getLabel());
            snapSession.clearInternalFlags(BlockIngestOrchestrator.INTERNAL_VOLUME_FLAGS);
            updatedObjects.add(snapSession);
        }
    }

    /**
     * Clear the internal flags of the replicas of associatedVolumes of RP volume
     *
     * @param requestContext current unManagedVolume Ingestion context.
     * @param volume the Volume Objects to clear flags on
     * @param updatedObjects a Set of DataObjects to be updated in the database at the end of ingestion
     * @param dbClient dbClient reference.
     */
    public static void clearAssociatedVolumesReplicaFlags(IngestionRequestContext requestContext, Volume volume,
            Set<DataObject> updatedObjects, DbClient dbClient) {
        List<Volume> associatedVolumes = new ArrayList<Volume>();
        if (volume.getAssociatedVolumes() != null) {
            for (String volumeId : volume.getAssociatedVolumes()) {
                BlockObject bo = requestContext.findDataObjectByType(Volume.class, URI.create(volumeId), true);
                if (null != bo && bo instanceof Volume) {
                    associatedVolumes.add((Volume) bo);
                }
            }
            _logger.info("Clearing internal volume flag of replicas of associatedVolumes {} of RP volume {}",
                    Joiner.on(",").join(associatedVolumes), volume.getLabel());
            clearPersistedReplicaFlags(requestContext, associatedVolumes, updatedObjects, dbClient);
        }
    }

    /**
     * Clear the flags of the snapshots of the RP volume
     *
     * @param requestContext current unManagedVolume Ingestion context.
     * @param volumes the Volume Objects to clear flags on
     * @param updatedObjects a Set of DataObjects to be updated in the database at the end of ingestion
     * @param dbClient - dbClient reference.
     */
    public static void clearSnapshotsFlags(IngestionRequestContext requestContext, Volume volume, Set<DataObject> updatedObjects,
            DbClient dbClient) {
        URIQueryResultList snapshotURIs = new URIQueryResultList();
        dbClient.queryByConstraint(ContainmentConstraint.Factory.getVolumeSnapshotConstraint(
                volume.getId()), snapshotURIs);
        Iterator<BlockSnapshot> snapshotsIterator = dbClient.queryIterativeObjects(BlockSnapshot.class, snapshotURIs);
        while (snapshotsIterator.hasNext()) {
            BlockSnapshot snap = snapshotsIterator.next();
            _logger.info("Clearing internal volume flag of snapshot {} of RP volume {}", snap.getLabel(), volume.getLabel());
            snap.clearInternalFlags(BlockIngestOrchestrator.INTERNAL_VOLUME_FLAGS);
            updatedObjects.add(snap);
        }

        // clear the flags of any snapshots created in the context
        for (BlockObject createdObject : requestContext.getBlockObjectsToBeCreatedMap().values()) {
            if (createdObject instanceof BlockSnapshot) {
                BlockSnapshot snapshot = (BlockSnapshot) createdObject;
                if (snapshot.getParent() != null && volume.getId().equals(snapshot.getParent().getURI())) {
                    _logger.info("Clearing internal volume flag of snapshot {} of RP volume {}", snapshot.getLabel(), volume.getLabel());
                    snapshot.clearInternalFlags(BlockIngestOrchestrator.INTERNAL_VOLUME_FLAGS);
                }
            }
        }
    }

    /**
     * Clear the flags of the mirrors of the RP volume
     *
     * @param requestContext current unManagedVolume Ingestion context.
     * @param volumes the Volume Objects to clear flags on
     * @param updatedObjects a Set of DataObjects to be updated in the database at the end of ingestion
     * @param dbClient - dbClient reference.
     */
    public static void clearMirrorsFlags(IngestionRequestContext requestContext, Volume volume, Set<DataObject> updatedObjects,
            DbClient dbClient) {
        if (volume.getMirrors() != null) {
            for (String volumeId : volume.getMirrors()) {
                BlockObject bo = requestContext.findDataObjectByType(BlockMirror.class, URI.create(volumeId), true);
                if (null != bo && bo instanceof BlockMirror) {
                    _logger.info("Clearing internal volume flag of mirror {} of RP volume {}", bo.getLabel(), volume.getLabel());
                    bo.clearInternalFlags(BlockIngestOrchestrator.INTERNAL_VOLUME_FLAGS);
                    updatedObjects.add(bo);
                }
            }
        }
    }

    /**
     * Clear the flags of the full copies of the RP volume
     *
     * @param requestContext current unManagedVolume Ingestion context.
     * @param volumes the Volume Objects to clear flags on
     * @param updatedObjects a Set of DataObjects to be updated in the database at the end of ingestion
     * @param dbClient - dbClient reference.
     */
    public static void clearFullCopiesFlags(IngestionRequestContext requestContext, Volume volume, Set<DataObject> updatedObjects,
            DbClient dbClient) {
        if (volume.getFullCopies() != null) {
            for (String volumeId : volume.getFullCopies()) {
                BlockObject bo = requestContext.findDataObjectByType(Volume.class, URI.create(volumeId), true);
                if (null != bo && bo instanceof Volume) {
                    _logger.info("Clearing internal volume flag of full copy {} of RP volume {}", bo.getLabel(), volume.getLabel());
                    bo.clearInternalFlags(BlockIngestOrchestrator.INTERNAL_VOLUME_FLAGS);
                    updatedObjects.add(bo);
                }
            }
        }
    }

    /**
     * Clear the flags of replicas which have been updated during the ingestion process
     *
     * @param requestContext current unManagedVolume Ingestion context.
     * @param volumes RP volumes
     * @param dbClient database client
     */
    public static void clearReplicaFlagsInIngestionContext(IngestionRequestContext requestContext, List<Volume> volumes,
            DbClient dbClient) {
        // We need to look for all snapshots and snapshot session in the contexts related to the rp volumes and its backend volumes and
        // clear their flags.
        _logger.info("Clearing flags of replicas in the context");
        List<String> rpVolumes = new ArrayList<String>();
        for (Volume volume : volumes) {
            rpVolumes.add(volume.getId().toString());
            if (RPHelper.isVPlexVolume(volume, dbClient) && volume.getAssociatedVolumes() != null && !volume.getAssociatedVolumes().isEmpty()) {
                StringSet associatedVolumes = volume.getAssociatedVolumes();
                rpVolumes.addAll(associatedVolumes);
            }
        }
        for (VolumeIngestionContext volumeIngestionContext : requestContext.getRootIngestionRequestContext()
                .getProcessedUnManagedVolumeMap().values()) {
            if (volumeIngestionContext instanceof IngestionRequestContext) {
                for (Set<DataObject> objectsToBeUpdated : ((IngestionRequestContext) volumeIngestionContext).getDataObjectsToBeUpdatedMap()
                        .values()) {
                    for (DataObject o : objectsToBeUpdated) {
                        boolean rpBlockSnapshot = (o instanceof BlockSnapshot
                                && rpVolumes.contains(((BlockSnapshot) o).getParent().getURI().toString()));
                        boolean rpBlockSnapshotSession = (o instanceof BlockSnapshotSession
                                && rpVolumes.contains(((BlockSnapshotSession) o).getParent().getURI().toString()));
                        if (rpBlockSnapshot || rpBlockSnapshotSession) {
                            _logger.info(String.format("Clearing internal volume flag of %s %s of RP volume ",
                                    (rpBlockSnapshot ? "BlockSnapshot" : "BlockSnapshotSession"), o.getLabel()));
                            o.clearInternalFlags(BlockIngestOrchestrator.INTERNAL_VOLUME_FLAGS);
                        }
                    }
                }
            }
        }
    }

    /**
     * Recursively find a VPLEX parent volume for the given UnManagedVolume. Will first check
     * VPLEX_PARENT_VOLUME and if not found, will check LOCAL_REPLICA_SOURCE_VOLUME recursively
     * for its VPLEX_PARENT_VOLUME.
     * 
     * @param unManagedVolume the UnManagedVolume to check
     * @param dbClient a reference to the database client
     * @param cache an optional cache of loaded VPLEX parent volume guids to UnManagedVolume object for that GUID
     * @return the root parent VPLEX virtual volume UnManagedVolume, or null if none found
     */
    public static UnManagedVolume findVplexParentVolume(UnManagedVolume unManagedVolume, DbClient dbClient, Map<String, UnManagedVolume> cache) {
        if (unManagedVolume == null || isVplexVolume(unManagedVolume)) {
            return unManagedVolume;
        }
        String vplexParentVolumeGuid = PropertySetterUtil.extractValueFromStringSet(
                SupportedVolumeInformation.VPLEX_PARENT_VOLUME.toString(),
                unManagedVolume.getVolumeInformation());
        if (vplexParentVolumeGuid != null) {
            if (cache != null && cache.containsKey(vplexParentVolumeGuid)) {
                return cache.get(vplexParentVolumeGuid);
            }
            URIQueryResultList unManagedVolumeList = new URIQueryResultList();
            dbClient.queryByConstraint(AlternateIdConstraint.Factory
                    .getVolumeInfoNativeIdConstraint(vplexParentVolumeGuid), unManagedVolumeList);
            if (unManagedVolumeList.iterator().hasNext()) {
                UnManagedVolume vplexParentVolume = dbClient.queryObject(UnManagedVolume.class, unManagedVolumeList.iterator().next());
                if (cache != null) {
                    cache.put(vplexParentVolumeGuid, vplexParentVolume);
                }
                return vplexParentVolume;
            }
        } else {
            String localReplicaSourceVolumeGuid =  PropertySetterUtil.extractValueFromStringSet(
                    SupportedVolumeInformation.LOCAL_REPLICA_SOURCE_VOLUME.toString(),
                    unManagedVolume.getVolumeInformation());
            if (localReplicaSourceVolumeGuid != null) {
                URIQueryResultList unManagedVolumeList = new URIQueryResultList();
                dbClient.queryByConstraint(AlternateIdConstraint.Factory
                        .getVolumeInfoNativeIdConstraint(localReplicaSourceVolumeGuid), unManagedVolumeList);
                if (unManagedVolumeList.iterator().hasNext()) {
                    UnManagedVolume localReplicaSourceVolume = 
                            dbClient.queryObject(UnManagedVolume.class, unManagedVolumeList.iterator().next());
                    return findVplexParentVolume(localReplicaSourceVolume, dbClient, cache);
                }
            }
        }

        return null;
    }

    /**
     * Gets the RP unmanaged volume corresponding to the passed in block object.
     * If the unmanaged volume corresponding to block object is RP protected, returns back the same unmanaged volume.
     *
     * If the unmanaged volume corresponding to block object is a VPLEX backend volume, returns the unmanaged volume
     * corresponding to the VPLEX virtual volume
     *
     * @param blockObject Block object
     * @param dbClient dbClient reference
     * @return RP protected unmanaged volume, null if not RP protected.
     */
    public static UnManagedVolume getRPUnmanagedVolume(BlockObject blockObject, DbClient dbClient) {
        if (blockObject != null) {
            UnManagedVolume umVolume = getUnManagedVolumeForBlockObject(blockObject, dbClient);
            return getRPUnmanagedVolume(umVolume, dbClient);
        }

        return null;
    }

    /**
     * Gets the RP unmanaged volume corresponding to the passed in unmanaged volume.
     * If the passed in unmanaged volume is RP protected, returns back the same unmanaged volume.
     *
     * If the passed in unmanaged volume is a VPLEX backend volume, returns the unmanaged volume
     * corresponding to the VPLEX virtual volume
     *
     * @param umVolume Unmanaged volume
     * @param dbClient dbClient reference
     * @return RP protected unmanaged volume, null if not RP protected.
     */
    public static UnManagedVolume getRPUnmanagedVolume(UnManagedVolume umVolume, DbClient dbClient) {
        if (umVolume != null && checkUnManagedResourceIsRecoverPointEnabled(umVolume)) {
            return umVolume;
        }

        // If this is a vplex backend volume, then check if the vplex virtual volume is RP enabled
        if (umVolume != null && isVplexBackendVolume(umVolume)) {
            String vplexParentVolume = PropertySetterUtil.extractValueFromStringSet(
                    SupportedVolumeInformation.VPLEX_PARENT_VOLUME.toString(),
                    umVolume.getVolumeInformation());
            URIQueryResultList unManagedVolumeList = new URIQueryResultList();
            dbClient.queryByConstraint(AlternateIdConstraint.Factory
                    .getVolumeInfoNativeIdConstraint(vplexParentVolume), unManagedVolumeList);
            if (unManagedVolumeList.iterator().hasNext()) {
                UnManagedVolume umv = dbClient.queryObject(UnManagedVolume.class, unManagedVolumeList.iterator().next());
                if (umv != null && checkUnManagedResourceIsRecoverPointEnabled(umv)) {
                    return umv;
                }
            }
        }

        return null;
    }

    /**
     * Gets the RP block object corresponding to the passed in block object.
     * The unamanged volume corresponding to the block object is retrieved to determine the RP properties
     * because the RP properties might not yet be set in the BlockObject.
     *
     * If the passed in block object is RP protected, returns back the same block object.
     *
     * If the passed in block object is a VPLEX backend volume, returns the block object
     * corresponding to the VPLEX virtual volume
     *
     * @param requestContext current unManagedVolume Ingestion context.
     * @param blockObject Block object
     * @param dbClient dbClient reference
     * @return RP protected block object, null if not RP protected.
     */
    public static BlockObject getRPVolume(IngestionRequestContext requestContext, BlockObject blockObject, DbClient dbClient) {
        UnManagedVolume umVolume = getUnManagedVolumeForBlockObject(blockObject, dbClient);
        if (umVolume != null && checkUnManagedResourceIsRecoverPointEnabled(umVolume)) {
            return blockObject;
        }

        // If this is a vplex backend volume, then check if the vplex virtual volume is RP enabled
        if (umVolume != null && isVplexBackendVolume(umVolume)) {
            String vplexParentVolumeGUID = PropertySetterUtil.extractValueFromStringSet(
                    SupportedVolumeInformation.VPLEX_PARENT_VOLUME.toString(),
                    umVolume.getVolumeInformation());
            String vplexParentBlockObjectGUID = vplexParentVolumeGUID.replace(VolumeIngestionUtil.UNMANAGEDVOLUME,
                    VolumeIngestionUtil.VOLUME);
            BlockObject vplexParentBlockObject = requestContext.findCreatedBlockObject(vplexParentBlockObjectGUID);
            if (vplexParentBlockObject == null) {
                vplexParentBlockObject = VolumeIngestionUtil.getBlockObject(vplexParentBlockObjectGUID, dbClient);
            }
            URIQueryResultList unManagedVolumeList = new URIQueryResultList();
            dbClient.queryByConstraint(AlternateIdConstraint.Factory
                    .getVolumeInfoNativeIdConstraint(vplexParentVolumeGUID), unManagedVolumeList);
            if (unManagedVolumeList.iterator().hasNext()) {
                UnManagedVolume umv = dbClient.queryObject(UnManagedVolume.class, unManagedVolumeList.iterator().next());
                if (umv != null && checkUnManagedResourceIsRecoverPointEnabled(umv)) {
                    return vplexParentBlockObject;
                }
            }
        }

        return null;
    }

    /**
     * Run Ingestion validation of RP by checking the vpool configurations and verifying the ingested volumes line up.
     * This method will ensure that the customer attempts to ingest volumes against the proper target virtual arrays
     * associated with the RP virtual pool that was selected during ingestion. Otherwise we may allow CDP-style targets
     * to be ingested into a CRR-style virtual pool.
     *
     * @param requestContext request context
     * @param umpset unmanaged protection set
     * @param _dbClient dbclient
     */
    public static void validateRPVolumesAlignWithIngestVpool(IngestionRequestContext requestContext, UnManagedProtectionSet umpset,
            DbClient dbClient) {

        VirtualPool sourceVirtualPool = null;
        List<URI> targetVarrays = null;
        if (umpset.getManagedVolumeIds() != null) {

            // Gather the RP source vpool and the target varrays
            for (String volumeID : umpset.getManagedVolumeIds()) {
                Volume volume = null;
                BlockObject bo = requestContext.getRootIngestionRequestContext().findCreatedBlockObject(URI.create(volumeID));
                if (bo != null && bo instanceof Volume) {
                    volume = (Volume) bo;
                }

                if (volume == null) {
                    _logger.error("Unable to retrieve volume : " + volumeID + " from database or created volumes.");
                    throw IngestionException.exceptions.validationFailedRPIngestionMissingVolume(volumeID, umpset.getCgName());
                }

                // Collect the vpool of the source volume(s)
                if (sourceVirtualPool == null && volume.checkPersonality(PersonalityTypes.SOURCE.name())) {
                    sourceVirtualPool = dbClient.queryObject(VirtualPool.class, volume.getVirtualPool());
                    targetVarrays = new ArrayList<URI>(Collections2.transform(sourceVirtualPool.getProtectionVarraySettings().keySet(),
                            CommonTransformerFunctions.FCTN_STRING_TO_URI));
                    break;
                }
            }

            // Verify the target volumes are in those target varrays
            List<URI> varraysCovered = new ArrayList<URI>(targetVarrays);
            for (String volumeID : umpset.getManagedVolumeIds()) {
                Volume volume = null;
                BlockObject bo = requestContext.getRootIngestionRequestContext().findCreatedBlockObject(URI.create(volumeID));
                if (bo != null && bo instanceof Volume) {
                    volume = (Volume) bo;
                }

                if (volume == null) {
                    _logger.error("Unable to retrieve volume : " + volumeID + " from database or created volumes.");
                    throw IngestionException.exceptions.validationFailedRPIngestionMissingVolume(volumeID, umpset.getCgName());
                }

                // Verify the target volume(s) are in a target varray of the RP source vpool
                if (volume.checkPersonality(PersonalityTypes.TARGET.name())) {
                    if (!targetVarrays.contains(volume.getVirtualArray())) {
                        Set<String> targetVarrayNames = new HashSet<String>();
                        for (URI targetVarrayId : targetVarrays) {
                            VirtualArray va = dbClient.queryObject(VirtualArray.class, targetVarrayId);
                            targetVarrayNames.add(va.forDisplay());
                        }
                        VirtualArray va = dbClient.queryObject(VirtualArray.class, volume.getVirtualArray());
                        throw IngestionException.exceptions.validationFailedRPIngestionVpoolMisalignment(
                                volume.forDisplay(), Joiner.on(",").join(targetVarrayNames), va.forDisplay());
                    } else {
                        varraysCovered.remove(volume.getVirtualArray());
                    }
                }
            }

            // Verify that all of the target volumes make up all of the target varrays
            if (!varraysCovered.isEmpty()) {
                Set<String> targetVarrayNames = new HashSet<String>();
                for (URI targetVarrayId : varraysCovered) {
                    VirtualArray va = dbClient.queryObject(VirtualArray.class, targetVarrayId);
                    targetVarrayNames.add(va.forDisplay());
                }
                throw IngestionException.exceptions.validationFailedRPIngestionMissingTargets(
                        Joiner.on(",").join(targetVarrayNames));
            }
        }
    }

    /**
     * Validates that the given UnManagedExportMask exists on the same VPLEX Cluster
     * as the VirtualArray in the ingestion request. The cluster name is actually
     * set by the BlockVplexIngestOrchestrator in order to re-use the cluster-id-to-name
     * cache, avoiding a expensive call to get cluster name info from the VPLEX API.
     *
     * @param requestContext the current IngestionRequestContext
     * @param unManagedVolume the current UnManagedVolume being processed for exports
     * @param unManagedExportMask the current UnManagdExportMask being processed
     *
     * @return true if the mask exists on the same VPLEX cluster as the ingestion request VirtualArray
     */
    public static boolean validateExportMaskMatchesVplexCluster(IngestionRequestContext requestContext,
            UnManagedVolume unManagedVolume, UnManagedExportMask unManagedExportMask) {
        VolumeIngestionContext volumeContext = requestContext.getRootIngestionRequestContext()
                .getProcessedVolumeContext(unManagedVolume.getNativeGuid());

        if (volumeContext == null) {
            // just get the current one
            volumeContext = requestContext.getVolumeContext();
        }

        if (volumeContext != null && volumeContext instanceof RpVplexVolumeIngestionContext) {
            volumeContext = ((RpVplexVolumeIngestionContext) volumeContext).getVplexVolumeIngestionContext();
        }

        if (volumeContext != null && volumeContext instanceof VplexVolumeIngestionContext) {
            String clusterName = ((VplexVolumeIngestionContext) volumeContext).getVirtualVolumeVplexClusterName();
            String maskingViewPath = unManagedExportMask.getMaskingViewPath();
            _logger.info("cluster name is {} and masking view path is {}", clusterName, maskingViewPath);
            if (clusterName != null && maskingViewPath != null) {
                String startOfPath = VPlexApiConstants.URI_CLUSTERS_RELATIVE + clusterName;
                // the start of the path would be like: /clusters/cluster-1 or /clusters/cluster-2
                // the masking view path would be like: /clusters/cluster-1/virtual-volumes/dd_V000195701351-021DA_V000198700412-030CF_vol
                // if the start of the path (as determined by getting the cluster name connected to the varray
                // for this ingestion request) overlaps the masking view path, then we are on the right vplex cluster
                if (maskingViewPath.startsWith(startOfPath)) {
                    _logger.info("\tUnManagedExportMask {} is on VPLEX cluster {} and will be processed now",
                            unManagedExportMask.getMaskName(), clusterName);
                    return true;
                }
            }
        }

        _logger.warn("\tUnManagedExportMask {} is not on the right VPLEX cluster for this ingestion request",
                unManagedExportMask.getMaskName());
        return false;
    }

    /**
     * Returns true if the given UnManagedVolume is a VPLEX distributed volume.
     *
     * @param unManagedVolume the UnManagedVolume to check
     * @return true if the given UnManagedVolume is a VPLEX distributed volume
     */
    public static boolean isVplexDistributedVolume(UnManagedVolume unManagedVolume) {
        if (isVplexVolume(unManagedVolume)) {
            String locality = PropertySetterUtil.extractValueFromStringSet(
                    SupportedVolumeInformation.VPLEX_LOCALITY.toString(),
                    unManagedVolume.getVolumeInformation());
            if (VPlexApiConstants.DISTRIBUTED_VIRTUAL_VOLUME.equalsIgnoreCase(locality)) {
                return true;
            }
        }

        return false;
    }

    /**
     * Validates that all the selected UnManagedExportMasks are compatible for ingestion.
     * 
     * Currently only VPLEX volumes are checked for presence of a 
     * single storage view per VPLEX cluster per host initiator. 
     * 
     * @param unManagedVolume the UnManagedVolume being ingested
     * @param unManagedMasks the UnManagedExportMasks being ingested
     * @param dbClient a reference to the database client
     */
    public static void validateUnManagedExportMasks(UnManagedVolume unManagedVolume, 
            List<UnManagedExportMask> unManagedMasks, DbClient dbClient) {
        if (isVplexVolume(unManagedVolume)) {
            Map<String, Set<String>> initToMaskMap = new HashMap<String, Set<String>>();

            // vplex brownfield requires initiators to only be in one storage view.
            // assemble a Set of all initiator ports being masked to the ingesting unmanaged volume 
            StringSet allInitiatorPortsBeingIngested = new StringSet();
            for (UnManagedExportMask mask : unManagedMasks) {
                allInitiatorPortsBeingIngested.addAll(mask.getKnownInitiatorNetworkIds());
                allInitiatorPortsBeingIngested.addAll(mask.getUnmanagedInitiatorNetworkIds());
            }
            URIQueryResultList result = new URIQueryResultList();
            dbClient.queryByConstraint(ContainmentConstraint.Factory
                    .getStorageSystemUnManagedExportMaskConstraint(unManagedVolume.getStorageSystemUri()), result);
            Set<URI> allMasksUrisForVplex = new HashSet<URI>();
            Iterator<URI> it = result.iterator();
            while (it.hasNext()) {
                allMasksUrisForVplex.add(it.next());
            }
            Iterator<UnManagedExportMask> allMasksForVplex = dbClient.queryIterativeObjects(UnManagedExportMask.class,
                    allMasksUrisForVplex, true);
            while (allMasksForVplex.hasNext()) {
                UnManagedExportMask mask = allMasksForVplex.next();
                if (null != mask) {
                    mapInitsToVplexStorageViews(initToMaskMap, mask, allInitiatorPortsBeingIngested);
                }
            }

            _logger.info("initiator to UnManagedExportMask map is " + initToMaskMap);
            // filter out any initiator to mask entries that satisfy the requirements of 1 storage view per initiator
            Iterator<Entry<String, Set<String>>> mapEntries = initToMaskMap.entrySet().iterator();
            while (mapEntries.hasNext()) {
                Entry<String, Set<String>> entry = mapEntries.next();
                if (entry.getValue().size() <= 1) {
                    mapEntries.remove();
                }
            }
            // if any are left in the map, they violate the single storage view per initiator rule
            if (!initToMaskMap.isEmpty()) {
                StringBuilder errorDetails = new StringBuilder();
                for (Entry<String, Set<String>> mapEntry : initToMaskMap.entrySet()) {
                    errorDetails.append("Initiator port ").append(mapEntry.getKey());
                    errorDetails.append(" is contained in the following storage views: ");
                    errorDetails.append(Joiner.on(", ").join(mapEntry.getValue())).append(". ");
                }
                _logger.error(errorDetails.toString());
                throw IngestionException.exceptions.invalidExportConfiguration(errorDetails.toString());
            }
        }
    }

    /**
     * Maps a set of initiator port wwns to VPLEX UnManagedExportMask masking view paths that contain
     * that port. Initiator ports can be in no more than one storage view on each VPLEX cluster to be valid 
     * for ingestion.
     * 
     * @param initToMaskMap a map of initiator port wwns to masking view paths that contain it
     * @param mask the UnManagedExportMask being processed
     * @param initPortList a set of initiator port wwns in the mask
     */
    private static void mapInitsToVplexStorageViews(Map<String, Set<String>> initToMaskMap, UnManagedExportMask mask, StringSet initPortList) {

        if (NullColumnValueGetter.isNullValue(mask.getMaskingViewPath()) 
                || !mask.getMaskingViewPath().startsWith(VPlexApiConstants.URI_CLUSTERS_RELATIVE.toString())) {
            _logger.warn("unexpected or invalid masking for validation: " + mask.getMaskingViewPath());
            return;
        }

        // path is in the format /clusters/cluster-1/exports/storage-views/V1_lglw7112lssemccom_001
        // where the second token will be the cluster name, which we can use to uniquely identify
        // the initiator port on each VPLEX cluster. trim off the leading / before splitting.
        String[] maskPathParts = mask.getMaskingViewPath().substring(1).split(VPlexApiConstants.SLASH);
        String maskClusterName = maskPathParts[1];

        StringSet allInitiatorForCurrentMask = new StringSet();
        allInitiatorForCurrentMask.addAll(mask.getKnownInitiatorNetworkIds());
        allInitiatorForCurrentMask.addAll(mask.getUnmanagedInitiatorNetworkIds());

        for (String initPort : initPortList) {
            if (allInitiatorForCurrentMask.contains(initPort)) {
                String initPortKey = maskClusterName + VPlexApiConstants.SLASH + initPort;
                Set<String> maskSet = initToMaskMap.get(initPortKey);
                if (null == maskSet) {
                    maskSet = new HashSet<String>();
                    initToMaskMap.put(initPortKey, maskSet);
                }
                maskSet.add(mask.getMaskingViewPath());
            }
        }
    }

}
