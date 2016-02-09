/*
 * Copyright (c) 2008-2013 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.api.service.impl.resource.utils;

import java.math.BigInteger;
import java.net.URI;
import java.util.ArrayList;
import java.util.Arrays;
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
import com.emc.storageos.api.service.impl.resource.blockingestorchestration.context.impl.RecoverPointVolumeIngestionContext;
import com.emc.storageos.api.service.impl.resource.blockingestorchestration.context.impl.RpVplexVolumeIngestionContext;
import com.emc.storageos.api.service.impl.resource.blockingestorchestration.context.impl.VplexVolumeIngestionContext;
import com.emc.storageos.api.service.impl.resource.utils.PropertySetterUtil.VolumeObjectProperties;
import com.emc.storageos.computesystemcontroller.impl.ComputeSystemHelper;
import com.emc.storageos.db.client.DbClient;
import com.emc.storageos.db.client.URIUtil;
import com.emc.storageos.db.client.constraint.AlternateIdConstraint;
import com.emc.storageos.db.client.constraint.ContainmentConstraint;
import com.emc.storageos.db.client.constraint.PrefixConstraint;
import com.emc.storageos.db.client.constraint.URIQueryResultList;
import com.emc.storageos.db.client.impl.DbModelClientImpl;
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
import com.emc.storageos.db.client.util.CommonTransformerFunctions;
import com.emc.storageos.db.client.util.CustomQueryUtility;
import com.emc.storageos.db.client.util.NullColumnValueGetter;
import com.emc.storageos.db.client.util.ResourceAndUUIDNameGenerator;
import com.emc.storageos.db.client.util.StringSetUtil;
import com.emc.storageos.model.ResourceOperationTypeEnum;
import com.emc.storageos.recoverpoint.responses.GetCopyResponse.GetCopyAccessStateResponse;
import com.emc.storageos.svcs.errorhandling.resources.APIException;
import com.emc.storageos.util.ConnectivityUtil;
import com.emc.storageos.util.VPlexUtil;
import com.emc.storageos.volumecontroller.impl.plugins.discovery.smis.processor.detailedDiscovery.RemoteMirrorObject;
import com.emc.storageos.volumecontroller.impl.utils.ExportMaskUtils;
import com.emc.storageos.volumecontroller.placement.BlockStorageScheduler;
import com.emc.storageos.vplexcontroller.VPlexControllerUtils;
import com.google.common.base.Joiner;
import com.google.common.collect.Collections2;
import com.google.common.collect.Sets;
import com.google.common.collect.Sets.SetView;

public class VolumeIngestionUtil {
    private static Logger _logger = LoggerFactory.getLogger(VolumeIngestionUtil.class);
    public static final String UNMANAGEDVOLUME = "UNMANAGEDVOLUME";
    public static final String VOLUME = "VOLUME";
    public static final String VOLUME_TEXT = "Volume";
    public static final String FALSE = "false";
    public static final String TRUE = "true";

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
                targetUriList.addAll(targetUris);
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
                        targetUriList.add(bo);
                        break;
                    }
                }
            } else {
                _logger.info("Volume not ingested yet {}. Checking in the created object map", targetId);
                // check in the created object map
                BlockObject blockObject = requestContext.findCreatedBlockObject(targetId);
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
                BlockObject blockObject = requestContext.findCreatedBlockObject(targetId);
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
                BlockObject blockObject = requestContext.findCreatedBlockObject(targetId);
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
        if (!(context instanceof RecoverPointVolumeIngestionContext)) {
            return isRPProtectingVplexVolumes;
        }
        RecoverPointVolumeIngestionContext rpContext = (RecoverPointVolumeIngestionContext) context;
        ProtectionSet pset = rpContext.getManagedProtectionSet();

        if (pset == null) {
            return isRPProtectingVplexVolumes;
        }

        // Iterate thru protection set volumes.
        for (String volumeIdStr : pset.getVolumes()) {
            for (List<DataObject> dataObjList : rpContext.getObjectsToBeUpdatedMap().values()) {
                for (DataObject dataObj : dataObjList) {
                    if (URIUtil.identical(dataObj.getId(), URI.create(volumeIdStr))) {
                        Volume volume = (Volume) dataObj;
                        if (volume.checkForVplexVirtualVolume(dbClient)) {
                            isRPProtectingVplexVolumes = true;
                            break;
                        }
                    }
                }
            }
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
                GetCopyAccessStateResponse.VIRTUAL_ACCESS_ROLLING_IMAGE.name().equals(rpAccessState)) {
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
        _logger.info("Determining if the unmanaged volume {} is belongs to an unmanaged consistency group", unManagedVolume.getLabel());
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

        throw IngestionException.exceptions.unmanagedVolumeNotIngestable(unManagedVolume.getLabel());
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
     * @param projectUri the Project URI
     * @param tenantUri the Tenant URI
     * @param varrayUri the VirtualArray URI
     * @param _dbClient the ViPR database client
     * @return a BlockConsistencyGroup, or null if none could be found or created
     */
    public static BlockConsistencyGroup getVplexConsistencyGroup(UnManagedVolume unManagedVolume, BlockObject blockObj, VirtualPool vpool,
            URI projectUri, URI tenantUri, URI varrayUri, DbClient _dbClient) {

        String cgName = PropertySetterUtil.extractValueFromStringSet(
                SupportedVolumeInformation.VPLEX_CONSISTENCY_GROUP_NAME.toString(),
                unManagedVolume.getVolumeInformation());

        // Don't create CG if the vplex is behind RP. Add a check here.
        if (VolumeIngestionUtil.isRpVplexVolume(unManagedVolume)) {
            blockObj.setReplicationGroupInstance(cgName);
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
                        if (cg.getProject().getURI().equals(projectUri) &&
                                cg.getTenant().getURI().equals(tenantUri)) {
                            // need to check for several matching properties
                            URI storageControllerUri = cg.getStorageController();
                            URI virtualArrayUri = cg.getVirtualArray();
                            if (null != storageControllerUri && null != virtualArrayUri) {
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
                if (null != potentialUnclaimedCg) {
                    return potentialUnclaimedCg;
                }

                _logger.info("Did not find an existing Consistency Group named {} that is associated "
                        + "already with the requested VPLEX device and Virtual Array. "
                        + "ViPR will create a new one.", cgName);

                // create a new consistency group
                BlockConsistencyGroup cg = new BlockConsistencyGroup();
                cg.setId(URIUtil.createId(BlockConsistencyGroup.class));
                cg.setLabel(cgName);
                cg.setProject(new NamedURI(projectUri, cgName));
                cg.setTenant(new NamedURI(tenantUri, cgName));
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
            @SuppressWarnings("deprecation")
            List<URI> maskUris = dbClient.queryByConstraint(AlternateIdConstraint.Factory
                    .getExportMaskByNameConstraint(mask.getLabel()));
            if (null == maskUris || maskUris.isEmpty()) {
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
        ExportMask exportMask = null;
        @SuppressWarnings("deprecation")
        List<URI> maskUris = dbClient.queryByConstraint(AlternateIdConstraint.Factory
                .getExportMaskByNameConstraint(mask.getMaskName()));
        if (null != maskUris && !maskUris.isEmpty()) {
            return dbClient.queryObject(ExportMask.class, maskUris.get(0));
        }

        return exportMask;
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
     * @param system the StorageSystem for the ExportMask
     * @param unManagedVolume the UnManagedVolume being ingested
     * @param exportGroup the ExportGroup for the ExportMask
     * @param volume the Volume object for the ExportMask
     * @param dbClient a reference to the database client
     * @param hosts a List of Hosts for the ExportMask
     * @param cluster a Cluster for the ExportMask
     * @param exportMaskLabel the name of the ExportMask
     * @throws Exception
     */
    public static <T extends BlockObject> void createExportMask(UnManagedExportMask eligibleMask, StorageSystem system,
            UnManagedVolume unManagedVolume,
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

        ExportMaskUtils.initializeExportMaskWithVolumes(system, exportGroup, eligibleMask.getMaskName(), exportMaskLabel, allInitiators,
                null, storagePortUris, eligibleMask.getZoningMap(), volume, eligibleMask.getUnmanagedInitiatorNetworkIds(),
                eligibleMask.getNativeId(), userAddedInis, dbClient, wwnToHluMap);

        // remove unmanaged mask if created if the block object is not marked as internal
        if (!volume.checkInternalFlags(Flag.NO_PUBLIC_ACCESS)) {
            _logger.info("breaking relationship between UnManagedExportMask {} and UnManagedVolume {}",
                    eligibleMask.getMaskName(), unManagedVolume.getLabel());
            unManagedVolume.getUnmanagedExportMasks().remove(eligibleMask.getId().toString());
            eligibleMask.getUnmanagedVolumeUris().remove(unManagedVolume.getId().toString());
        }

        updateExportGroup(exportGroup, volume, dbClient, allInitiators, hosts, cluster);

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
        List<UnManagedVolume> unManagedVolumes = dbClient.queryObject(
                UnManagedVolume.class, Collections2.transform(
                        unManagedExportMask.getUnmanagedVolumeUris(), CommonTransformerFunctions.FCTN_STRING_TO_URI));
        for (UnManagedVolume vol : unManagedVolumes) {
            String wwn = vol.getWwn();
            if (wwn != null) {
                wwnToHluMap.put(wwn, findHlu(vol, unManagedExportMask.getMaskName()));
            }
        }
        _logger.info("wwn to hlu map for {} is " + wwnToHluMap, unManagedExportMask.getMaskName());
        return wwnToHluMap;
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
        List<URI> storagePortUris = dbClient.queryByConstraint(AlternateIdConstraint.Factory
                .getVirtualArrayStoragePortsConstraint(varray.toString()));
        storagePortUris = filterOutUnregisteredPorts(dbClient, storagePortUris);
        Set<String> storagePortUriStr = new HashSet<String>((Collections2.transform(storagePortUris,
                CommonTransformerFunctions.FCTN_URI_TO_STRING)));
        SetView<String> diff = Sets.difference(portsInUnManagedMask, storagePortUriStr);
        // Temporary relaxation of storage port restriction for XIO:
        // With XIO we do not have the ability to remove specific (and possibly unavailable) storage ports
        // from the LUN maps. So a better check specifically for XIO is to ensure that we at least have one
        // storage port in the varray.
        StorageSystem storageSystem = dbClient.queryObject(StorageSystem.class, mask.getStorageSystemUri());
        boolean portsValid = true;
        if (storageSystem != null) {
            if (storageSystem.getSystemType().equalsIgnoreCase(SystemType.xtremio.toString())) {
                portsValid = diff.size() < portsInUnManagedMask.size();
            } else {
                portsValid = diff.isEmpty();
            }
        }

        if (!portsValid) {
            _logger.warn("Storage Ports {} in unmanaged mask {} is not available in VArray {}", new Object[] {
                    Joiner.on(",").join(diff), mask.getMaskName(), varray });
            if (volume instanceof Volume) {
                Volume vol = (Volume) volume;
                URI haVarray = checkVplexHighAvailabilityArray(vol, dbClient);
                if (null != haVarray) {
                    _logger.info("found high availabilty virtual array {}, " + "so checking for storage ports over there",
                            haVarray);
                    storagePortUris = dbClient.queryByConstraint(AlternateIdConstraint.Factory
                            .getVirtualArrayStoragePortsConstraint(haVarray.toString()));
                    storagePortUris = filterOutUnregisteredPorts(dbClient, storagePortUris);
                    storagePortUriStr = new HashSet<String>((Collections2.transform(storagePortUris,
                            CommonTransformerFunctions.FCTN_URI_TO_STRING)));
                    diff = Sets.difference(portsInUnManagedMask, storagePortUriStr);
                    if (!diff.isEmpty()) {
                        _logger.warn("Storage Ports {} in unmanaged mask {} are not available in high "
                                + "availability varray {}, matching fails",
                                new Object[] { Joiner.on(",").join(diff), mask.getMaskName(), haVarray });
                        StringBuffer errorMessage = new StringBuffer("Storage Port(s) ");
                        errorMessage.append(Joiner.on(", ").join(getStoragePortNames((Collections2.transform(diff,
                                CommonTransformerFunctions.FCTN_STRING_TO_URI)), dbClient)));
                        errorMessage.append(" in unmanaged export mask ").append(mask.getMaskName());
                        errorMessage.append(" are available neither in source Virtual Array ");
                        errorMessage.append(getVarrayName(varray, dbClient));
                        errorMessage.append(" nor in high availability Virtual Array ");
                        errorMessage.append(getVarrayName(haVarray, dbClient));
                        errorMessages.add(errorMessage.toString());
                        return false;
                    } else {
                        _logger.info("Storage Ports {} in unmanaged mask {} found in "
                                + "high availability varray {}, so this mask is okay", new Object[] { Joiner.on(",").join(diff),
                                mask.getMaskName(), haVarray });
                        return true;
                    }
                }
            }
            StringBuffer errorMessage = new StringBuffer("Storage Port(s) ");
            errorMessage.append(Joiner.on(", ").join(getStoragePortNames((Collections2.transform(diff,
                    CommonTransformerFunctions.FCTN_STRING_TO_URI)), dbClient)));
            errorMessage.append(" in unmanaged export mask ").append(mask.getMaskName());
            errorMessage.append(" are not available in Virtual Array ").append(getVarrayName(varray, dbClient));
            errorMessages.add(errorMessage.toString());
            return false;
        }
        return true;
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
                    storagePortNames.add(storagePort.getPortName());
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
    private static String getVarrayName(URI virtualArrayUri, DbClient dbClient) {
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
     * @param dbClient a reference to the database client
     * @param allInitiators a List of all initiators for the ExportGroup
     * @param hosts a List of Hosts for the ExportGroup
     * @param cluster a Cluster for the ExportGroup
     */
    public static <T extends BlockObject> void updateExportGroup(ExportGroup exportGroup, T volume, DbClient dbClient,
            List<Initiator> allInitiators,
            List<Host> hosts, Cluster cluster) {

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

        // Do not add the block object to the export group if it has no public access
        if (!volume.checkInternalFlags(Flag.NO_PUBLIC_ACCESS)) {
            exportGroup.addVolume(volume.getId(), ExportGroup.LUN_UNASSIGNED);
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
     * @param initiatorsPartOfCluster boolean indicating if the Initiators are part of a Cluster
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
                continue;
            }
            if (null != mask.getKnownInitiatorUris() && !mask.getKnownInitiatorUris().isEmpty()) {

                _logger.info("Group initiators by protocol {}",
                        Joiner.on(",").join(iniByProtocol.entrySet()));
                // group Initiators by Protocol
                for (Entry<String, Set<String>> entry : iniByProtocol.entrySet()) {
                    _logger.info("Processing Initiators by Protocol {} Group", entry.getValue());
                    if (hostPartOfCluster) {
                        /**
                         * If Host is part of a Cluster, then
                         * 
                         * ViPR ini || Existing Mask in Array
                         * case 1: I1,I2 I1,I3,I4,I2 [Verify whether I3,I4 are part of then same Cluster, then skip it]
                         * case 2: I1,I2,I3 I1,I2 -- mask selected
                         * case 3: I1,I3 I1,I2 -- not selected
                         * 
                         */
                        _logger.info("Host part of a Cluster- Comparing discovered [{}] with unmanaged [{}] ", Joiner.on(",")
                                .join(entry.getValue()), Joiner.on(",").join(mask.getKnownInitiatorUris()));
                        Set<String> ViPRDiscToExistingKnownInisDiff = Sets.difference(entry.getValue(),
                                mask.getKnownInitiatorUris());

                        if (ViPRDiscToExistingKnownInisDiff.isEmpty()) {

                            // check whether remaining existing initiators on
                            // mask are part of the cluster
                            Set<String> remainingInis = Sets.difference(mask.getKnownInitiatorUris(), entry.getValue());
                            Set<String> iniPartOfCluster = Sets.difference(remainingInis, initiatorsPartOfCluster);
                            _logger.info(
                                    "ViPR initiators are a complete subset of unmanaged mask's known initiators. Trying to find whether the other initiators {}"
                                            + " in the unmanaged mask are actually owned by the same cluster {} this host belongs to.",
                                    Joiner.on(",").join(iniPartOfCluster), clusterUri);
                            if (iniPartOfCluster.size() == remainingInis.size()) {
                                _logger.info(
                                        "Matched Mask Found {}, as there are no other initiators in existing mask owned by the cluster, this host belongs to.",
                                        mask.getMaskName());
                                if (verifyNumPath(Collections.singletonList(initiatorUris), mask.getZoningMap(),
                                        volume, vPoolURI, dbClient)) {
                                    eligibleMasks.add(mask);
                                }
                                itr.remove();
                            } else {
                                _logger.info(
                                        "Even though Existing UnManaged Mask {} contains subset of ViPR initiators, it can't be used as there are other initiators [{}] "
                                                + "in the mask which are owned by a different Hosts in the same cluster {} as this host belongs.",
                                        new Object[] { mask.getMaskName(), Joiner.on(",").join(iniPartOfCluster), clusterUri });
                            }
                        } else {

                            Set<String> existingknownInisToViprDiscDiff = Sets.difference(mask.getKnownInitiatorUris(),
                                    entry.getValue());

                            if (existingknownInisToViprDiscDiff.isEmpty()) {
                                _logger.info(
                                        "Matched Mask Found {}, as existing ViPR known initiators are a complete subset of ViPR discovered.",
                                        mask.getMaskName());
                                if (verifyNumPath(Collections.singletonList(initiatorUris), mask.getZoningMap(),
                                        volume, vPoolURI, dbClient)) {
                                    eligibleMasks.add(mask);
                                }
                                itr.remove();
                            } else {
                                _logger.info(
                                        "Existing ViPR known Initiators are not a complete subset of ViPR discovered, skipping the unmanaged mask {}",
                                        mask.getMaskName());
                            }

                        }

                    } else {
                        _logger.info("Host not part of any Cluster- Comparing discovered [{}] with unmanaged [{}] ",
                                Joiner.on(",").join(entry.getValue()), Joiner.on(",").join(mask.getKnownInitiatorUris()));
                        Set<String> existingknownInisToViprDiscDiff = Sets.difference(mask.getKnownInitiatorUris(),
                                entry.getValue());

                        if (existingknownInisToViprDiscDiff.isEmpty()) {
                            _logger.info("Matched Mask Found after Grouping by Protocol {}", mask.getMaskName());
                            if (verifyNumPath(Collections.singletonList(initiatorUris), mask.getZoningMap(),
                                    volume, vPoolURI, dbClient)) {
                                eligibleMasks.add(mask);
                            }
                            itr.remove();
                        } else {
                            _logger.info(
                                    "Existing Unmanaged mask initiators have other ViPR known initiators {} from a different Host, where in the given host is not part of any cluster",
                                    Joiner.on(",").join(existingknownInisToViprDiscDiff));
                        }
                    }

                }

            }

        }

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
                    continue;
                }

                // if required initiators are available in the mask, then choose it,
                // irrespective of actual initiators on the MV on Array
                if (null != mask.getKnownInitiatorUris() && !mask.getKnownInitiatorUris().isEmpty()) {

                    for (Entry<String, Set<String>> entry : clusterIniByProtocol.entrySet()) {
                        _logger.info("Processing Initiators by Protocol {} Group", entry.getValue());
                        _logger.info("Cluster- Comparing discovered [{}] with unmanaged [{}] ", Joiner.on(",").join(entry.getValue()),
                                Joiner.on(",").join(mask.getKnownInitiatorUris()));
                        Set<String> existingknownInisToViprDiscDiff = Sets.difference(mask.getKnownInitiatorUris(),
                                entry.getValue());
                        /**
                         * ViPR initiators || Existing Mask in Array
                         * case 1: I1,I2,I3,I4 I1,I2 -- mask skipped ,as I1,i2 are initiators of 1 single Node in cluster (exlusive export
                         * mode)
                         * case 2: I1,I2 I1,I2,I3 -- mask selected
                         * case 3: I1,I3 I1,I2 -- not selected
                         */
                        if (existingknownInisToViprDiscDiff.isEmpty()) {
                            _logger.info(
                                    "Mask Found {}, as existing ViPR known initiators are a complete subset of ViPR discovered. try to find whether the subset is actually corresponds to 1"
                                            +
                                            "Node in a cluster, if yes then skip this, as the existing mask is meant for Exclusive mode exports.",
                                    mask.getMaskName());
                            if (groupInitiatorsByHost(mask.getKnownInitiatorUris(), dbClient).size() == 1) {
                                _logger.info(
                                        "Skip this unmanaged mask {}, as the mask has only initiators from 1 Node in a cluster, probably meant for exclusive export",
                                        mask.getMaskName());
                            } else {
                                _logger.info("Mask Found {} with a subset of initiators from more than 1 node in a cluster",
                                        mask.getMaskName());
                                if (verifyNumPath(initiatorUris, mask.getZoningMap(), volume, vPoolURI, dbClient)) {
                                    eligibleMasks.add(mask);
                                }
                                itr.remove();
                            }
                        } else {
                            _logger.info("Existing ViPR known Initiators are not a complete subset of ViPR discovered, check whether ViPR discovered are a subset of existing");
                            Set<String> ViPRDiscToExistingKnownInisDiff = Sets.difference(entry.getValue(),
                                    mask.getKnownInitiatorUris());
                            if (ViPRDiscToExistingKnownInisDiff.isEmpty()) {
                                _logger.info("Mask Found {} with a subset of ViPR initiators in existing mask.", mask.getMaskName());
                                if (verifyNumPath(initiatorUris, mask.getZoningMap(), volume, vPoolURI, dbClient)) {
                                    eligibleMasks.add(mask);
                                }
                                itr.remove();
                            }
                        }
                    }
                }
            }

            if (eligibleMasks.isEmpty() && !unManagedMasks.isEmpty()) {
                _logger.info("Unable to find a MV/SG with all the cluster initiators, now trying to group initiators by Host and start the search");
                // return individual Host MVs if found any for each Cluster Node as
                // well, to support exclusive mode volume export.
                for (Set<String> initiatorUriList : initiatorUris) {
                    // if mask is already selected, the no need to run this again
                    if (unManagedMasks.isEmpty()) {
                        break;
                    }
                    _logger.info("Looking a Mask for initiators {} belonging to a cluster node", Joiner.on(",").join(initiatorUriList));
                    Map<String, Set<String>> iniByProtocol = groupInitiatorsByProtocol(initiatorUriList, dbClient);
                    eligibleMasks.addAll(findMatchingExportMaskForHost(volume, unManagedMasks, initiatorUriList,
                            iniByProtocol, dbClient, vArray, vPoolURI, true, clusterInitiators, cluster, errorMessages));
                }
            } else {
                _logger.info("Either masks already found or there are no unmanaged masks available");
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

        return eligibleMasks;
    }

    /**
     * Given a ZoneInfoMap, check that the hosts in a cluster have a number of
     * paths that is compliant with the vpool specifications.
     * 
     * @param initiatorUris
     *            a list of initiators sets, each set belongs to one host in the
     *            cluster
     * @param zoningMap
     *            the ZoneInfoMap that has the zone mapping between the
     *            UnManagedExportMask initiators and ports
     * @param block
     *            the volume or snapshot for which the zoning are verified
     * @param vPoolURI
     *            - URI of the VPool to ingest blockObject.
     * @param dbClient
     *            an instance of dbclient
     * @return true if the number of paths is valid.
     */
    private static boolean verifyNumPath(List<Set<String>> initiatorUris, ZoneInfoMap zoningMap, BlockObject block,
            URI vPoolURI, DbClient dbClient) {
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

            if (hasFCInitiators(initiators) && !avoidNumPathCheck) {
                return verifyHostNumPath(pathParams, initiators, zoningMap, dbClient);
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
        if (initiators == null || initiators.isEmpty()) {
            _logger.error("Host has no initiators configured.");
            throw IngestionException.exceptions.hostHasNoInitiators();
        }
        int unassignedInitiators = 0;
        int totalPorts = 0;
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
                _logger.error("Initiator {} of host {} has a different number of ports ({}) than " +
                        "what is required according to the virtual pool ({})", new Object[] { initiator.getInitiatorPort(),
                        hostName, ports.size(), pathParams.getPathsPerInitiator() });
                throw IngestionException.exceptions.hostZoningHasDifferentPortCount(
                        initiator.getInitiatorPort(), hostName,
                        String.valueOf(ports.size()), String.valueOf(pathParams.getPathsPerInitiator()));
            } else {
                totalPorts += ports.size();
                _logger.info("Initiator {} of host {} has {} paths", new Object[] { initiator.getInitiatorPort(),
                        hostName, ports.size(), ports.size() });
            }

        }
        if (totalPorts < pathParams.getMinPaths()) {
            _logger.error(String.format("Host %s (%s) has fewer ports assigned %d than min_paths %d",
                    hostName, hostURI.toString(), totalPorts, pathParams.getMinPaths()));
            throw IngestionException.exceptions.hostZoningHasFewerPorts(hostName,
                    String.valueOf(totalPorts), String.valueOf(pathParams.getMinPaths()));
        }
        if (totalPorts > pathParams.getMaxPaths()) {
            _logger.error(String.format("Host %s (%s) has more ports assigned %d than max_paths %d",
                    hostName, hostURI.toString(), totalPorts, pathParams.getMaxPaths()));
            throw IngestionException.exceptions.hostZoningHasMorePorts(hostName,
                    String.valueOf(totalPorts), String.valueOf(pathParams.getMaxPaths()));
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
     * Verify a matching ExportGroup exists for the given parameters.
     * 
     * @param project the Project URI
     * @param computeResource the ComputeResource URI
     * @param vArray the VirtualArray URI
     * @param resourceType the resource type (Host or Cluster)
     * @param dbClient a reference to the database client
     * @return an ExportGroup if already available in the database
     */
    public static ExportGroup verifyExportGroupExists(URI project, URI computeResource, URI vArray, String resourceType,
            DbClient dbClient) {

        List<URI> exportGroupUris = dbClient.queryByConstraint(ContainmentConstraint.Factory.getProjectExportGroupConstraint(project));
        List<ExportGroup> exportGroups = dbClient.queryObject(ExportGroup.class, exportGroupUris);

        if (null == exportGroups || exportGroups.isEmpty()) {
            return null;
        }
        for (ExportGroup exportGroup : exportGroups) {
            if (exportGroup.getVirtualArray().equals(vArray)) {

                if (ExportGroup.ExportGroupType.Host.toString().equalsIgnoreCase(resourceType)) {
                    if (exportGroup.hasHost(computeResource) &&
                            !ExportGroup.ExportGroupType.Cluster.toString().equalsIgnoreCase(exportGroup.getType())) {
                        _logger.info("Export Groups {} matching Varray/Project/ComputeResource exists", exportGroup.getId());
                        return exportGroup;
                    }
                } else if (ExportGroup.ExportGroupType.Cluster.toString().equalsIgnoreCase(resourceType)) {
                    if (exportGroup.hasCluster(computeResource)) {
                        _logger.info("Export Groups {} matching Varray/Project/ComputeResource exists", exportGroup.getId());
                        return exportGroup;
                    }
                }

            }
        }

        return null;
    }

    /**
     * Get the export group associated with initiator URIs
     * 
     * Note: Once it finds an export group associated with any initiator, it returns that export group. This may not
     * be what the caller wants.
     * 
     * @param project project
     * @param knownInitiatorUris initiators list
     * @param vArray virtual array
     * @param dbClient dbclient
     * @return export group
     */
    public static ExportGroup verifyExportGroupExists(URI project, StringSet knownInitiatorUris, URI vArray, DbClient dbClient) {
        for (String initiatorIdStr : knownInitiatorUris) {
            AlternateIdConstraint constraint = AlternateIdConstraint.Factory.
                    getExportGroupInitiatorConstraint(initiatorIdStr);
            URIQueryResultList egUris = new URIQueryResultList();
            dbClient.queryByConstraint(constraint, egUris);
            List<ExportGroup> queryExportGroups = dbClient.queryObject(ExportGroup.class, egUris);
            for (ExportGroup exportGroup : queryExportGroups) {
                if (!exportGroup.getProject().getURI().equals(project)) {
                    continue;
                }

                if (!exportGroup.getVirtualArray().equals(vArray)) {
                    continue;
                }

                // TODO: This code will likely need to be rethought when we consider multiple masks to RP (VMAX2)
                if (queryExportGroups.size() > 1) {
                    _logger.error("More than one export group contains the initiator(s) requested.  Choosing the first one: "
                            + exportGroup.getId().toString());
                }
                return exportGroup;
            }

        }
        return null;
    }

    /**
     * Get UnManagedVolumes associated with a Host.
     * 
     * @param hostUri the URI of the Host to check
     * @param dbClient a reference to the database client
     * @return a List of UnManagedVolume associated with the given Host
     */
    public static List<UnManagedVolume> findUnManagedVolumesForHost(URI hostUri, DbClient dbClient) {

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
            dbClient.queryByConstraint(AlternateIdConstraint.
                    Factory.getUnManagedVolumeInitiatorNetworkIdConstraint(initiator.getInitiatorPort()), results);
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
            Set<String> inisOfunManagedMask = getInitiatorsOfUnmanagedExportMask(unmanagedVolume, cache, dbClient);
            Set<String> interSection = Sets.intersection(clusterInis, inisOfunManagedMask);
            if (interSection.isEmpty()) {
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
                dbClient.queryByConstraint(AlternateIdConstraint.
                        Factory.getUnManagedVolumeInitiatorNetworkIdConstraint(initiator.getInitiatorPort()), results);
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
            dbClient.queryByConstraint(AlternateIdConstraint.
                    Factory.getUnManagedExportMaskKnownInitiatorConstraint(initiator.getInitiatorPort()), results);
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
                dbClient.queryByConstraint(AlternateIdConstraint.
                        Factory.getUnManagedExportMaskKnownInitiatorConstraint(initiator.getInitiatorPort()), results);
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
        return null == unManagedVolume.getUnmanagedExportMasks()
                || unManagedVolume.getUnmanagedExportMasks().isEmpty();
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
                _logger.info("   the high availability virtual array URI is " + haVarray);
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
     * @param updatedObjects a List of DataObjects being updated related to the given BlockObject
     * @param dbClient a reference to the database client
     */
    public static void clearInternalFlags(BlockObject blockObject, List<DataObject> updatedObjects, DbClient dbClient) {
        // for each block object, get the corresponding unmanaged volume.
        _logger.info("clearInternalFlags for blockObject " + blockObject.forDisplay());

        List<UnManagedExportMask> uemsToPersist = new ArrayList<UnManagedExportMask>();
        String unmanagedVolumeGUID = blockObject.getNativeGuid().replace(VOLUME, UNMANAGEDVOLUME);
        List<URI> unmanagedVolumeUris = dbClient.queryByConstraint(AlternateIdConstraint.Factory
                .getVolumeInfoNativeIdConstraint(unmanagedVolumeGUID));
        List<UnManagedVolume> unManagedVolumes = dbClient.queryObject(UnManagedVolume.class, unmanagedVolumeUris);
        boolean isVplexBackendVolume = false;
        boolean isRPVolume = false;
        if (unManagedVolumes != null && !unManagedVolumes.isEmpty()) {
            UnManagedVolume unManagedVolume = unManagedVolumes.get(0);

            // Check if this is a VPLEX backend volume, which we need to treat a little differently
            isVplexBackendVolume = VolumeIngestionUtil.isVplexBackendVolume(unManagedVolume);

            // Check if this is a RP volume
            isRPVolume = VolumeIngestionUtil.checkUnManagedResourceIsRecoverPointEnabled(unManagedVolume);

            // Get the exportGroupType from the unManagedVolume
            String exportGroupType = unManagedVolume.getVolumeCharacterstics().get(
                    SupportedVolumeCharacterstics.EXPORTGROUP_TYPE.toString());

            // If there are unmanaged export masks, get the corresponding ViPR export masks
            StringSet unmanagedExportMasks = unManagedVolume.getUnmanagedExportMasks();
            if (null != unmanagedExportMasks && !unmanagedExportMasks.isEmpty()) {
                List<URI> unManagedMaskUris = new ArrayList<URI>(Collections2.transform(unmanagedExportMasks,
                        CommonTransformerFunctions.FCTN_STRING_TO_URI));
                List<UnManagedExportMask> unManagedMasks = dbClient.queryObject(UnManagedExportMask.class, unManagedMaskUris);
                for (UnManagedExportMask unManagedExportMask : unManagedMasks) {
                    Set<ExportMask> exportMasks = new HashSet<ExportMask>();
                    List<URI> initiatorUris = new ArrayList<URI>(Collections2.transform(
                            unManagedExportMask.getKnownInitiatorUris(), CommonTransformerFunctions.FCTN_STRING_TO_URI));
                    for (URI ini : initiatorUris) {
                        List<URI> exportMaskUris = dbClient.queryByConstraint(AlternateIdConstraint.Factory
                                .getExportMaskInitiatorConstraint(ini.toString()));
                        if (null == exportMaskUris) {
                            continue;
                        }
                        for (URI eMaskUri : exportMaskUris) {
                            ExportMask eMask = dbClient.queryObject(ExportMask.class, eMaskUri);
                            if (null != eMask && eMask.getStorageDevice().equals(unManagedExportMask.getStorageSystemUri())) {
                                _logger.info("Found Mask {} with matching initiator and matching Storage System", eMaskUri);
                                exportMasks.add(eMask);
                            } else {
                                _logger.info("Found Mask {} with matching initiator and unmatched Storage System. Skipping mask", eMaskUri);
                            }
                        }
                    }

                    Set<ExportGroup> exportGroups = new HashSet<ExportGroup>();
                    // Remove the block object from existing volumes and add to the user created volumes of the export mask
                    for (ExportMask exportMask : exportMasks) {
                        String normalizedWWN = BlockObject.normalizeWWN(blockObject.getWWN());
                        if (null == normalizedWWN) {
                            throw IngestionException.exceptions.exportedVolumeIsMissingWwn(unManagedVolume.getLabel());
                        }
                        if (exportMask.hasAnyExistingVolumes() && exportMask.getExistingVolumes().containsKey(normalizedWWN)) {
                            _logger.info(
                                    "Removing block object {} from existing volumes and adding to user created volumes of export mask {}",
                                    blockObject.getNativeGuid(), exportMask.getMaskName());
                            exportMask.removeFromExistingVolumes(blockObject);
                            exportMask.addToUserCreatedVolumes(blockObject);
                            exportGroups.addAll(ExportMaskUtils.getExportGroups(dbClient, exportMask));
                        }
                    }
                    updatedObjects.addAll(exportMasks);

                    // Add the block object to the export groups corresponding to the export masks
                    for (ExportGroup exportGroup : exportGroups) {
                        _logger.info("Processing exportGroup {} to add block object", exportGroup.getId());
                        // only add to those export groups whose project and varray matches the block object
                        boolean exportGroupTypeMatches = (null != exportGroupType)
                                && exportGroupType.equalsIgnoreCase(exportGroup.getType());
                        if (exportGroup.getProject().getURI().equals(getBlockProject(blockObject)) &&
                                exportGroup.getVirtualArray().equals(blockObject.getVirtualArray()) &&
                                (exportGroupTypeMatches || isVplexBackendVolume)) {
                            _logger.info("Adding block object {} to export group {}", blockObject.getNativeGuid(), exportGroup.getLabel());
                            exportGroup.addVolume(blockObject.getId(), ExportGroup.LUN_UNASSIGNED);
                        }
                    }
                    updatedObjects.addAll(exportGroups);
                    _logger.info("breaking relationship between UnManagedExportMask {} and UnManagedVolume {}",
                            unManagedExportMask.getMaskName(), unManagedVolume.forDisplay());
                    unManagedVolume.getUnmanagedExportMasks().remove(unManagedExportMask.getId().toString());
                    unManagedExportMask.getUnmanagedVolumeUris().remove(unManagedVolume.getId().toString());
                }
            } else {
                _logger.info("No unmanaged export masks found for the unmanaged volume {}", unManagedVolumes.get(0).getNativeGuid());
            }
            updatedObjects.addAll(uemsToPersist);

            if (canDeleteUnManagedVolume(unManagedVolume)) {
                _logger.info("Set unmanaged volume inactive: {}", unManagedVolume.forDisplay());
                unManagedVolume.setInactive(true);
            }
            updatedObjects.add(unManagedVolume);
        } else {
            _logger.info("No unmanaged volume found for the object {}", blockObject.getNativeGuid());
        }

        blockObject.clearInternalFlags(BlockIngestOrchestrator.INTERNAL_VOLUME_FLAGS);

        // snapshot sessions
        URIQueryResultList queryResults = new URIQueryResultList();
        dbClient.queryByConstraint(ContainmentConstraint.Factory.getParentSnapshotSessionConstraint(blockObject.getId()), queryResults);
        Iterator<URI> resultsIter = queryResults.iterator();
        while (resultsIter.hasNext()) {
            BlockSnapshotSession snapSession = dbClient.queryObject(BlockSnapshotSession.class, resultsIter.next());
            snapSession.clearInternalFlags(BlockIngestOrchestrator.INTERNAL_VOLUME_FLAGS);
            updatedObjects.add(snapSession);
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
     * Return a BlockObject for the given native GUID String.
     * 
     * @param nativeGUID the native GUID to look for
     * @param dbClient a reference to the database client
     * @return a BlockObject for the given native GUID String
     */
    public static BlockObject getBlockObject(String nativeGUID, DbClient dbClient) {
        BlockObject blockObject = null;
        _logger.info("Checking for unmanagedvolume {} [Volume] ingestion status.", nativeGUID);
        List<URI> blockObjectUris = dbClient.queryByConstraint(AlternateIdConstraint.Factory.getVolumeNativeGuidConstraint(nativeGUID));
        if (!blockObjectUris.isEmpty()) {
            _logger.info("Found volume {} ingested.", nativeGUID);
            return BlockObject.fetch(dbClient, blockObjectUris.get(0));
        }
        _logger.info("Checking for unmanagedvolume {} [Snap] ingestion status", nativeGUID);
        blockObjectUris = dbClient.queryByConstraint(AlternateIdConstraint.Factory.getBlockSnapshotsByNativeGuid(nativeGUID));

        if (!blockObjectUris.isEmpty()) {
            _logger.info("Found snapshot {} ingested.", nativeGUID);
            return BlockObject.fetch(dbClient, blockObjectUris.get(0));
        }

        _logger.info("Checking for unmanagedvolume {} [Mirror] ingestion status", nativeGUID);
        blockObjectUris = dbClient.queryByConstraint(AlternateIdConstraint.Factory.getMirrorByNativeGuid(nativeGUID));
        if (!blockObjectUris.isEmpty()) {
            _logger.info("Found mirror {} ingested.", nativeGUID);
            return BlockObject.fetch(dbClient, blockObjectUris.get(0));
        }

        return blockObject;
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
     * Checks if a volume was ingested. An exception will be
     * thrown if the given operation is not supported on ingested volumes.
     * 
     * @param volume the Volume in question
     * @param operation a text description of the operation
     *            (for use in the Exception message)
     * @param dbClient Reference to a database client
     */
    public static void checkOperationSupportedOnIngestedVolume(Volume volume,
            ResourceOperationTypeEnum operation, DbClient dbClient) {
        if (volume.isIngestedVolume(dbClient)) {
            switch (operation) {
                case CREATE_VOLUME_FULL_COPY:
                case CREATE_VOLUME_SNAPSHOT:
                case EXPAND_BLOCK_VOLUME:
                case CREATE_VOLUME_MIRROR:
                case CHANGE_BLOCK_VOLUME_VARRAY:
                case UPDATE_CONSISTENCY_GROUP:
                case CREATE_SNAPSHOT_SESSION:
                    _logger.error("Operation {} is not permitted on ingested volumes.", operation.getName());
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

        // TODO currently only the VPLEX discovery process is creating
        // this HLU_TO_EXPORT_LABEL_MAP --- this should also be added to other
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
                                    _logger.info("found hlu {} for {} in export mask "
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
        consistencyGroup.setProject(new NamedURI(project.getId(), unManagedCG.getLabel()));
        consistencyGroup.setTenant(new NamedURI(project.getTenantOrg().getURI(), unManagedCG.getLabel()));
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
     * @param dbClient a reference to the database client
     * @return boolean if the all the volumes in the unmanaged protection set have been ingested
     */
    public static boolean validateAllVolumesInCGIngested(List<UnManagedVolume> ingestedUnManagedVolumes,
            UnManagedProtectionSet umpset, DbClient dbClient) {
        if (umpset == null) {
            _logger.warn("INGEST VALIDATION: unmanaged protection set is null");
            return false;
        }
        // Make sure that none of the managed volumes still have a corresponding unmanaged volume. This means that there is
        // some information left to be ingested.
        if (umpset.getManagedVolumeIds() != null && !umpset.getManagedVolumeIds().isEmpty()) {
            List<URI> managedVolumesURIList = new ArrayList<URI>(Collections2.transform(umpset.getManagedVolumeIds(),
                    CommonTransformerFunctions.FCTN_STRING_TO_URI));
            Iterator<Volume> managedVolumeIdsIterator = dbClient.queryIterativeObjects(Volume.class, managedVolumesURIList);
            while (managedVolumeIdsIterator.hasNext()) {
                Volume managedVolume = managedVolumeIdsIterator.next();
                if (hasUnManagedVolume(managedVolume, ingestedUnManagedVolumes, dbClient)) {
                    _logger.info(
                            "Managed volume {} still has a corresponding unmanaged volume left which means that there is still some info to be ingested",
                            managedVolume.getId());
                    return false;
                }
            }
        }

        // Make sure the managed volumes match the unmanaged volumes and WWN list
        if (umpset.getUnManagedVolumeIds() != null && umpset.getManagedVolumeIds() != null && umpset.getVolumeWwns() != null &&
                umpset.getUnManagedVolumeIds().size() == umpset.getManagedVolumeIds().size() &&
                umpset.getManagedVolumeIds().size() == umpset.getVolumeWwns().size()) {
            _logger.info("Found that all volumes associated with the RP CG have been ingested: " + umpset.getCgName());
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
                            "are %d volumes to be ingested, however only %d volume have been ingested.", umpset.getCgName(), umpset
                            .getVolumeWwns().size(), umpset.getManagedVolumeIds().size());
            _logger.info(msg);
            // TODO: Iterate over the unmanaged volumes that we haven't ingested yet and print them up.
            return false;
        }

        if (umpset.getManagedVolumeIds().size() != umpset.getVolumeWwns().size()) {
            String msg = String.format(
                    "INGEST VALIDATION: Found that the unmanaged protection set: %s is not yet ingestable because there " +
                            " are %d volumes in the RP CG that are on arrays that are not under management.", umpset.getCgName(), umpset
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
        List<UnManagedProtectionSet> umpsets =
                CustomQueryUtility.getUnManagedProtectionSetByUnManagedVolumeId(dbClient, unManagedVolume.getId().toString());
        Iterator<UnManagedProtectionSet> umpsetsItr = umpsets.iterator();
        if (umpsetsItr.hasNext()) {
            umpset = umpsetsItr.next();
        }

        DataObject alreadyLoadedUmpset = requestContext.findInUpdatedObjects(umpset.getId());
        if (alreadyLoadedUmpset != null && (alreadyLoadedUmpset instanceof UnManagedProtectionSet)) {
            umpset = (UnManagedProtectionSet) alreadyLoadedUmpset;
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
        List<UnManagedProtectionSet> umpsets =
                CustomQueryUtility.getUnManagedProtectionSetByManagedVolumeId(dbClient, managedVolume.getId().toString());
        Iterator<UnManagedProtectionSet> umpsetsItr = umpsets.iterator();
        if (umpsetsItr.hasNext()) {
            umpset = umpsetsItr.next();
        }

        DataObject alreadyLoadedUmpset = requestContext.findInUpdatedObjects(umpset.getId());
        if (alreadyLoadedUmpset != null && (alreadyLoadedUmpset instanceof UnManagedProtectionSet)) {
            umpset = (UnManagedProtectionSet) alreadyLoadedUmpset;
        }

        return umpset;
    }

    /**
     * Creates a protection set for the given unmanaged protection set
     * 
     * @param umpset Unmanaged protection set for which a protection set has to be created
     * @param dbClient a reference to the database client
     * @return newly created protection set
     */
    public static ProtectionSet createProtectionSet(
            IngestionRequestContext requestContext, UnManagedProtectionSet umpset, DbClient dbClient) {
        StringSetMap unManagedCGInformation = umpset.getCGInformation();
        String rpProtectionId = PropertySetterUtil.extractValueFromStringSet(
                SupportedCGInformation.PROTECTION_ID.toString(), unManagedCGInformation);

        ProtectionSet pset = new ProtectionSet();
        pset.setId(URIUtil.createId(ProtectionSet.class));
        pset.setLabel(umpset.getCgName());
        pset.setProtectionId(rpProtectionId);
        pset.setProtectionStatus(ProtectionStatus.ENABLED.toString());
        pset.setProtectionSystem(umpset.getProtectionSystemUri());
        pset.setNativeGuid(umpset.getNativeGuid());

        if (umpset.getManagedVolumeIds() != null) {
            for (String volumeID : umpset.getManagedVolumeIds()) {

                // Add all volumes (managed only) to the new protection set
                if (pset.getVolumes() == null) {
                    pset.setVolumes(new StringSet());
                }

                pset.getVolumes().add(volumeID);

                Volume volume = null;
                BlockObject bo = requestContext.findCreatedBlockObject(URI.create(volumeID));
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
     * Create a block consistency group for the given protection set
     * 
     * @param pset protection set
     * @param dbClient
     * 
     * @return BlockConsistencyGroup
     */
    public static BlockConsistencyGroup createRPBlockConsistencyGroup(ProtectionSet pset, DbClient dbClient) {
        BlockConsistencyGroup cg = new BlockConsistencyGroup();
        cg.setId(URIUtil.createId(BlockConsistencyGroup.class));
        cg.setLabel(pset.getLabel());
        Project project = dbClient.queryObject(Project.class, pset.getProject());
        cg.setProject(new NamedURI(pset.getProject(), project.getLabel()));
        StringSet types = new StringSet();
        types.add(BlockConsistencyGroup.Types.RP.toString());
        cg.setRequestedTypes(types);
        cg.setTypes(types);
        cg.setTenant(project.getTenantOrg());
        cg.addSystemConsistencyGroup(pset.getProtectionSystem().toString(), pset.getLabel());
        _logger.info("Created new block consistency group: " + cg.getId().toString());
        return cg;
    }

    /**
     * Decorate the RP volumes with the protection set and consistency group info after the RP CG has been fully ingested
     * 
     * @param rpVolumes RP Volumes
     * @param pset protection set
     * @param rpCG RP consistency group
     * @param updatedObjects List of objects updated
     * @param dbClient a reference to the database client
     */
    public static void decorateRPVolumesCGInfo(List<Volume> rpVolumes, ProtectionSet pset, BlockConsistencyGroup rpCG,
            List<DataObject> updatedObjects, DbClient dbClient, IngestionRequestContext requestContext) {
        for (Volume volume : rpVolumes) {
            // Set references to protection set/CGs properly in each volume
            volume.setConsistencyGroup(rpCG.getId());
            volume.setProtectionSet(new NamedURI(pset.getId(), pset.getLabel()));
            volume.clearInternalFlags(BlockRecoverPointIngestOrchestrator.RP_INTERNAL_VOLUME_FLAGS);

            // Set the proper flags on the journal volumes.
            if (volume.getPersonality().equalsIgnoreCase(Volume.PersonalityTypes.METADATA.toString())) {
                volume.addInternalFlags(Flag.INTERNAL_OBJECT, Flag.SUPPORTS_FORCE);
            }

            _logger.info("Updating volume " + volume.getLabel() + " flags/settings to " + volume.getInternalFlags());

            // Find any backing volumes associated with vplex volumes and add the CG reference to them as well.
            if (volume.checkForVplexVirtualVolume(dbClient)) {
                // Find associated volumes
                for (String associatedVolumeIdStr : volume.getAssociatedVolumes()) {
                    // First look in created block objects for the associated volumes. This would be the latest version.
                    BlockObject blockObject = requestContext.findCreatedBlockObject(associatedVolumeIdStr);
                    if (blockObject == null) {
                        // Next look in the updated objects.
                        blockObject = (BlockObject) requestContext.findInUpdatedObjects(URI.create(associatedVolumeIdStr));
                    }
                    if (blockObject == null) {
                        // Finally look in the DB itself. It may be from a previous ingestion operation.
                        blockObject = dbClient.queryObject(Volume.class, URI.create(associatedVolumeIdStr));
                        // Since I pulled this in from the database, we need to add it to the list of objects to update.
                        ((RecoverPointVolumeIngestionContext) requestContext.getVolumeContext()).getObjectsToBeUpdatedMap().put(
                                blockObject.getNativeGuid(), Arrays.asList(blockObject));
                    }
                    if (blockObject != null) {
                        blockObject.setConsistencyGroup(rpCG.getId());
                        updatedObjects.add(blockObject);
                    }
                }
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
        String unManagedCGURI = PropertySetterUtil.extractValueFromStringSet
                (SupportedVolumeInformation.UNMANAGED_CONSISTENCY_GROUP_URI.toString(), unManagedVolume.getVolumeInformation());
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
     * @param vpool - VirtualPool in which unManagedVolume is getting ingested.
     * @param projectUri - Project in which unManagedVolume is getting ingested.
     * @param tenantUri - Tenant in which unManagedVolume is getting ingested.
     * @param varrayUri - Varray in which unManagedVolume is getting ingested.
     * @param umcgsToUpdate - UnManagedConsistencyGroup's to update.
     * @param dbClient - dbClient instance.
     * @return BlockConsistencyGroup
     */
    public static BlockConsistencyGroup getBlockObjectConsistencyGroup(UnManagedVolume unManagedVolume, BlockObject blockObj,
            VirtualPool vpool, URI projectUri, URI tenantUri, URI varrayUri, List<UnManagedConsistencyGroup> umcgsToUpdate,
            DbClient dbClient) {
        UnManagedConsistencyGroup umcg = getUnManagedConsistencyGroup(unManagedVolume, dbClient);

        boolean isLastUmvToIngest = isLastUnManagedVolumeToIngest(umcg, unManagedVolume);
        boolean isVplexOrRPProtected = isRPOrVplexProtected(unManagedVolume);
        if (isVplexOrRPProtected || !isLastUmvToIngest) {
            _logger.info(
                    "Ignoring the CG creation as the volume is either isVplexRPProtected:{} or isLastUmvToIngest: {} exists to ingest.",
                    isLastUmvToIngest, isVplexOrRPProtected);
            _logger.info("Remaining volumes in CG to ingest: {}", umcg.getUnManagedVolumesMap());
            // set ReplicationGroupInstance in the block object.
            blockObj.setReplicationGroupInstance(umcg.getLabel());
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
                    if (validateCGProjectDetails(cg, storageSystem, projectUri, tenantUri, varrayUri, unManagedVolume.getLabel(),
                            cgName, dbClient)) {
                        return cg;
                    }
                    URI storageControllerUri = cg.getStorageController();
                    URI virtualArrayUri = cg.getVirtualArray();
                    if (null == storageControllerUri && null == virtualArrayUri) {
                        potentialUnclaimedCg = cg;
                    }
                }
            }

            // if not match on label, project, tenant, storage array, and virtual array
            // was found, then we can return the one found with null storage array and
            // virtual array. this would indicate the user created the CG, but hadn't
            // used it yet in creating a volume
            if (null != potentialUnclaimedCg) {
                return potentialUnclaimedCg;
            }

            _logger.info(String
                    .format("Did not find an existing CG named %s that is associated already with the requested device %s and Virtual Array %s. ViPR will create a new one.",
                            cgName, storageSystem.getNativeGuid(), varrayUri));
            // create a new consistency group
            BlockConsistencyGroup cg = new BlockConsistencyGroup();
            cg.setId(URIUtil.createId(BlockConsistencyGroup.class));
            cg.setLabel(cgName);
            cg.setProject(new NamedURI(projectUri, cgName));
            cg.setTenant(new NamedURI(tenantUri, cgName));
            cg.addConsistencyGroupTypes(Types.LOCAL.name());
            cg.addSystemConsistencyGroup(storageSystem.getId().toString(), cgName);
            cg.setStorageController(storageSystem.getId());
            cg.setVirtualArray(varrayUri);
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
                if (null != storageControllerUri && null != virtualArrayUri) {
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

        for (BlockObject bo : requestContext.getObjectsToBeCreatedMap().values()) {
            if (URIUtil.identical(bo.getConsistencyGroup(), cg.getId())) {
                blockObjects.add(bo);
            }
        }

        for (List<DataObject> doList : requestContext.getObjectsToBeUpdatedMap().values()) {
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
            for (List<DataObject> doList : ((RecoverPointVolumeIngestionContext) requestContext.getVolumeContext())
                    .getObjectsToBeUpdatedMap().values()) {
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
                    .getObjectsToBeCreatedMap().entrySet()) {
                BlockObject boObj = entry.getValue();
                if (URIUtil.identical(boObj.getConsistencyGroup(), cg.getId())) {
                    blockObjects.add(boObj);
                }
            }
        }

        if (requestContext.getVolumeContext() instanceof VplexVolumeIngestionContext) {
            for (List<DataObject> doList : ((VplexVolumeIngestionContext) requestContext.getVolumeContext()).getObjectsToBeUpdatedMap()
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
                    .getObjectsToBeCreatedMap().entrySet()) {
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
    public static Volume findVolume(DbClient dbClient, Map<String, BlockObject> createdMap, Map<String, List<DataObject>> updatedMap, String volumeId) {
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
            for (List<DataObject> objectsToBeUpdated : updatedMap.values()) {
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
}
