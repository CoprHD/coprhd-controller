/*
 * Copyright (c) 2008-2011 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.volumecontroller.impl.plugins;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.emc.storageos.db.client.URIUtil;
import com.emc.storageos.db.client.constraint.AlternateIdConstraint;
import com.emc.storageos.db.client.constraint.Constraint;
import com.emc.storageos.db.client.constraint.ContainmentConstraint;
import com.emc.storageos.db.client.constraint.URIQueryResultList;
import com.emc.storageos.db.client.model.AbstractChangeTrackingSet;
import com.emc.storageos.db.client.model.BlockSnapshot;
import com.emc.storageos.db.client.model.DataObject.Flag;
import com.emc.storageos.db.client.model.DiscoveredDataObject;
import com.emc.storageos.db.client.model.DiscoveredDataObject.DataCollectionJobStatus;
import com.emc.storageos.db.client.model.DiscoveredDataObject.RegistrationStatus;
import com.emc.storageos.db.client.model.DiscoveredDataObject.Type;
import com.emc.storageos.db.client.model.Initiator;
import com.emc.storageos.db.client.model.Network;
import com.emc.storageos.db.client.model.NetworkSystem;
import com.emc.storageos.db.client.model.Operation;
import com.emc.storageos.db.client.model.ProtectionSet;
import com.emc.storageos.db.client.model.ProtectionSet.ProtectionStatus;
import com.emc.storageos.db.client.model.ProtectionSystem;
import com.emc.storageos.db.client.model.RPSiteArray;
import com.emc.storageos.db.client.model.StoragePool;
import com.emc.storageos.db.client.model.StoragePort;
import com.emc.storageos.db.client.model.StorageSystem;
import com.emc.storageos.db.client.model.StringMap;
import com.emc.storageos.db.client.model.StringSet;
import com.emc.storageos.db.client.model.StringSetMap;
import com.emc.storageos.db.client.model.Volume;
import com.emc.storageos.db.client.util.CustomQueryUtility;
import com.emc.storageos.db.client.util.NullColumnValueGetter;
import com.emc.storageos.db.exceptions.DatabaseException;
import com.emc.storageos.exceptions.DeviceControllerExceptions;
import com.emc.storageos.plugins.AccessProfile;
import com.emc.storageos.plugins.BaseCollectionException;
import com.emc.storageos.plugins.common.Executor;
import com.emc.storageos.plugins.common.domainmodel.NamespaceList;
import com.emc.storageos.plugins.metering.recoverpoint.RecoverPointCollectionException;
import com.emc.storageos.protectioncontroller.impl.recoverpoint.RPHelper;
import com.emc.storageos.recoverpoint.exceptions.RecoverPointException;
import com.emc.storageos.recoverpoint.impl.RecoverPointClient;
import com.emc.storageos.recoverpoint.objectmodel.RPBookmark;
import com.emc.storageos.recoverpoint.objectmodel.RPSite;
import com.emc.storageos.recoverpoint.objectmodel.SiteArrays;
import com.emc.storageos.recoverpoint.responses.GetBookmarksResponse;
import com.emc.storageos.recoverpoint.responses.RecoverPointStatisticsResponse;
import com.emc.storageos.recoverpoint.responses.RecoverPointVolumeProtectionInfo;
import com.emc.storageos.recoverpoint.utils.WwnUtils;
import com.emc.storageos.svcs.errorhandling.resources.InternalException;
import com.emc.storageos.util.ConnectivityUtil;
import com.emc.storageos.util.ExportUtils;
import com.emc.storageos.util.ConnectivityUtil.StorageSystemType;
import com.emc.storageos.util.VersionChecker;
import com.emc.storageos.volumecontroller.ControllerException;
import com.emc.storageos.volumecontroller.impl.BiosCommandResult;
import com.emc.storageos.volumecontroller.impl.NativeGUIDGenerator;
import com.emc.storageos.volumecontroller.impl.recoverpoint.RPUnManagedObjectDiscoverer;
import com.emc.storageos.volumecontroller.impl.utils.ImplicitPoolMatcher;

/**
 * Class for RecoverPoint discovery and collecting stats from RecoverPoint storage device
 */
public class RPCommunicationInterface extends ExtendedCommunicationInterfaceImpl {
    private static final String NON_ALPHA_NUMERICS = "[^A-Za-z0-9_]";
    private static final String RPA = "-rpa-";
    private static final String RP_INITIATOR_PREFIX = "50:01:24";

    private Logger _log = LoggerFactory.getLogger(RPCommunicationInterface.class);

    private NamespaceList namespaces;
    private Executor executor;

    private RPUnManagedObjectDiscoverer unManagedCGDiscoverer;

    public void setUnManagedObjectDiscoverer(
            RPUnManagedObjectDiscoverer cgDiscoverer) {
        this.unManagedCGDiscoverer = cgDiscoverer;
    }

    private RPStatisticsHelper _rpStatsHelper;

    public RPStatisticsHelper getRpStatsHelper() {
        return _rpStatsHelper;
    }

    public void setRpStatsHelper(RPStatisticsHelper rpStatsHelper) {
        this._rpStatsHelper = rpStatsHelper;
    }

    public void setExecutor(Executor executor) {
        this.executor = executor;
    }

    public Executor getExecutor() {
        return executor;
    }

    public void setNamespaces(NamespaceList namespaces) {
        this.namespaces = namespaces;
    }

    public NamespaceList getNamespaces() {
        return namespaces;
    }

    @Override
    public void cleanup() {
        _log.info("Stopping the Plugin Thread and clearing Resources");
        releaseResources();
    }

    /**
     * releaseResources
     */
    private void releaseResources() {
        _keyMap.clear();
        namespaces = null;
    }

    @Override
    public void discover(AccessProfile accessProfile) throws BaseCollectionException {

        URI storageSystemId = null;
        ProtectionSystem protectionSystem = null;
        boolean discoverySuccess = true;
        StringBuffer errMsgBuilder = new StringBuffer();
        String detailedStatusMessage = "Unknown Status";
        boolean isNewlyCreated = false;

        try {
            _log.info("Access Profile Details :  IpAddress : {}, PortNumber : {}", accessProfile.getIpAddress(),
                    accessProfile.getPortNumber());
            storageSystemId = accessProfile.getSystemId();
            protectionSystem = _dbClient.queryObject(ProtectionSystem.class, storageSystemId);
            if (protectionSystem.getDiscoveryStatus().equals(DiscoveredDataObject.DataCollectionJobStatus.CREATED.toString())) {
                isNewlyCreated = true;
            }

            if (StorageSystem.Discovery_Namespaces.UNMANAGED_CGS.toString().equalsIgnoreCase(accessProfile.getnamespace())) {
                try {
                    unManagedCGDiscoverer.discoverUnManagedObjects(accessProfile, _dbClient, _partitionManager);
                } catch (RecoverPointException rpe) {
                    discoverySuccess = false;
                    String msg = "Discover RecoverPoint Unmanaged CGs failed. Protection system: " + storageSystemId;
                    buildErrMsg(errMsgBuilder, rpe, msg);
                }
            } else {

                try {
                    discoverCluster(protectionSystem);
                } catch (RecoverPointException rpe) {
                    discoverySuccess = false;
                    String msg = "Discover RecoverPoint cluster failed. Protection system: " + storageSystemId;
                    buildErrMsg(errMsgBuilder, rpe, msg);
                }

                // get RP array mappings
                try {
                    if (discoverySuccess) {
                        discoverRPSiteArrays(protectionSystem);
                        _dbClient.persistObject(protectionSystem);
                    }
                } catch (Exception rpe) {
                    discoverySuccess = false;
                    String msg = "Discover RecoverPoint site/cluster failed. Protection system: " + storageSystemId;
                    buildErrMsg(errMsgBuilder, rpe, msg);
                }

                try {
                    if (discoverySuccess) {
                        discoverConnectivity(protectionSystem);
                    }
                } catch (Exception rpe) {
                    discoverySuccess = false;
                    String msg = "Discover RecoverPoint connectivity failed. Protection system: " + storageSystemId;
                    buildErrMsg(errMsgBuilder, rpe, msg);
                }

                // Perform maintenance on the RP bookmarks; some may no longer be valid
                try {
                    if (discoverySuccess) {
                        cleanupSnapshots(protectionSystem);
                    }
                } catch (Exception rpe) {
                    discoverySuccess = false;
                    String msg = "Snapshot maintenance failed. Protection system: " + storageSystemId;
                    buildErrMsg(errMsgBuilder, rpe, msg);
                }

                // Rematch storage pools for RP virtual pools
                try {
                    if (discoverySuccess && isNewlyCreated) {
                        matchVPools(protectionSystem.getId());
                    }
                } catch (Exception rpe) {
                    discoverySuccess = false;
                    String msg = "Virtual Pool matching failed. Protection system: " + storageSystemId;
                    buildErrMsg(errMsgBuilder, rpe, msg);
                }

                // Discover the Connected-via-RP-itself Storage Systems
                try {
                    if (discoverySuccess) {
                        discoverVisibleStorageSystems(protectionSystem);
                    }
                } catch (Exception rpe) {
                    discoverySuccess = false;
                    String msg = "RP-visible storage system discovery failed. Protection system: " + storageSystemId;
                    buildErrMsg(errMsgBuilder, rpe, msg);
                }

                // Discover the Connected-via-NetworkStorage Systems
                try {
                    if (discoverySuccess) {
                        discoverAssociatedStorageSystems(protectionSystem);
                    }
                } catch (Exception rpe) {
                    discoverySuccess = false;
                    String msg = "Storage system discovery failed. Protection system: " + storageSystemId;
                    buildErrMsg(errMsgBuilder, rpe, msg);
                }

                // Discover the protection sets
                try {
                    if (discoverySuccess) {
                        discoverProtectionSets(protectionSystem);
                    }
                } catch (Exception rpe) {
                    discoverySuccess = false;
                    String msg = "Discovery of protection sets failed. Protection system: " + storageSystemId;
                    buildErrMsg(errMsgBuilder, rpe, msg);
                }

                // Discover the protection system cluster connectivity topology information
                try {
                    if (discoverySuccess) {
                        discoverTopology(protectionSystem);
                    }
                } catch (Exception rpe) {
                    discoverySuccess = false;
                    String msg = "Discovery of topology failed. Protection system: " + storageSystemId;
                    buildErrMsg(errMsgBuilder, rpe, msg);
                }
            }
            
            if (!discoverySuccess) {
                throw DeviceControllerExceptions.recoverpoint.discoveryFailure(errMsgBuilder.toString());
            }
            else {
                detailedStatusMessage = String.format("Discovery completed successfully for Protection System: %s",
                        storageSystemId.toString());
            }
        } catch (Exception e) {
            detailedStatusMessage = String.format("Discovery failed for Protection System %s because %s",
                    storageSystemId.toString(), e.getLocalizedMessage());
            _log.error(detailedStatusMessage, e);
            throw DeviceControllerExceptions.recoverpoint.discoveryFailure(detailedStatusMessage);
        } finally {
            releaseResources();
            if (null != protectionSystem) {
                try {
                    // set detailed message
                    protectionSystem.setLastDiscoveryStatusMessage(detailedStatusMessage);
                    _dbClient.persistObject(protectionSystem);
                } catch (DatabaseException ex) {
                    _log.error("Error while persisting object to DB", ex);
                }
            }
        }
    }

    /**
     * Convenience method for building the error messages during discover.
     * 
     * @param errMsgBuilder string buffer
     * @param re exception to add in
     * @param msg message to add in
     */
    private void buildErrMsg(StringBuffer errMsgBuilder, Exception re, String msg) {
        if (errMsgBuilder.length() != 0) {
            errMsgBuilder.append(", ");
        }
        errMsgBuilder.append(msg);
        errMsgBuilder.append(", ");
        errMsgBuilder.append(re.getMessage());
        _log.error(msg, re);
    }

    /**
     * Recalculate all virtual pools matching storage pools that have RP protection as creation
     * of a protection system creates new relationships and constraints on the matching pools
     * of an RP system.
     */
    private void matchVPools(URI rpSystemId) {
        List<URI> storagePoolIds = ConnectivityUtil.getRPSystemStoragePools(_dbClient, rpSystemId);
        if (storagePoolIds != null && !storagePoolIds.isEmpty()) {
            List<StoragePool> storagePools = _dbClient.queryObject(StoragePool.class, storagePoolIds);
            ImplicitPoolMatcher.matchModifiedStoragePoolsWithAllVirtualPool(storagePools, _dbClient, _coordinator);
        }
    }

    /**
     * Discover the state of each protection set (CG) on the protection system
     * 
     * @param protectionSystem protection system
     * @throws RecoverPointException
     */
    private void discoverProtectionSets(ProtectionSystem protectionSystem) throws RecoverPointException {
        URIQueryResultList list = new URIQueryResultList();
        Constraint constraint = ContainmentConstraint.Factory.getProtectionSystemProtectionSetConstraint(protectionSystem.getId());
        _dbClient.queryByConstraint(constraint, list);
        Iterator<URI> it = list.iterator();
        while (it.hasNext()) {
            URI protectionSetId = it.next();
            discoverProtectionSet(protectionSystem, protectionSetId);
        }
    }

    /**
     * Discover the topology of the RP system's clusters
     * 
     * @param protectionSystem protection system
     * @throws RecoverPointException
     */
    private void discoverTopology(ProtectionSystem protectionSystem) throws RecoverPointException {
        RecoverPointClient rp = RPHelper.getRecoverPointClient(protectionSystem);
        StringSet topologySet = new StringSet();
        topologySet.addAll(rp.getClusterTopology());
        protectionSystem.setClusterTopology(topologySet);
    }

    /**
     * Discover a single protection set.
     * 
     * @param protectionSystem protection system
     * @param protectionSetId protection set
     * @throws RecoverPointException
     */
    private void discoverProtectionSet(ProtectionSystem protectionSystem, URI protectionSetId) throws RecoverPointException {
        ProtectionSet protectionSet = _dbClient.queryObject(ProtectionSet.class, protectionSetId);
        if (protectionSet == null || protectionSet.getInactive()) {
            return;
        }

        StringSet protectionVolumes = protectionSet.getVolumes();
        RecoverPointVolumeProtectionInfo protectionVolume = null;
        RecoverPointClient rp = RPHelper.getRecoverPointClient(protectionSystem);
        boolean changed = false;
        _log.info("ProtectionSet discover in the RPDeviceController called for protection set: " + protectionSet.getLabel());
        for (String volume : protectionVolumes) {
            Volume protectionVolumeWWN = _dbClient.queryObject(Volume.class, URI.create(volume));

            if (protectionVolumeWWN == null || protectionVolumeWWN.getInactive()) {
                continue;
            }
                       
            try {
                protectionVolume = rp.getProtectionInfoForVolume(RPHelper.getRPWWn(protectionVolumeWWN.getId(), _dbClient));
            } catch (RecoverPointException re) {
                StringBuffer errMsgBuilder = new StringBuffer();
                String msg = "Discovery of protection set failed. Protection system: " + protectionSystem.getId() + ", ";
                errMsgBuilder.append(msg);
                errMsgBuilder.append(re.getMessage());
                _log.warn(errMsgBuilder.toString());
            }

            if (protectionVolume == null) {
                continue;
            }

            // If the volume is a source volume, let's check to see if the personality changed.
            if ((!changed)
                    &&
                    (((protectionVolume.getRpVolumeCurrentProtectionStatus() == RecoverPointVolumeProtectionInfo.volumeProtectionStatus.PROTECTED_SOURCE) &&
                    (protectionVolumeWWN.getPersonality().equalsIgnoreCase(Volume.PersonalityTypes.TARGET.toString()))))
                    ||
                    ((protectionVolume.getRpVolumeCurrentProtectionStatus() == RecoverPointVolumeProtectionInfo.volumeProtectionStatus.PROTECTED_TARGET) &&
                    (protectionVolumeWWN.getPersonality().equalsIgnoreCase(Volume.PersonalityTypes.SOURCE.toString())))) {
                _log.info("Changing personality of volume {} due to RP condition on consistency group", protectionVolumeWWN.getLabel());
                updatePostFailoverPersonalities(protectionVolumeWWN);
                changed = true;
            }

            if (protectionVolume.getRpVolumeCurrentProtectionStatus() == RecoverPointVolumeProtectionInfo.volumeProtectionStatus.PROTECTED_SOURCE) {
                switch (rp.getCGState(protectionVolume)) {
                    case DELETED:
                        protectionSet.setProtectionStatus(ProtectionStatus.DELETED.toString());
                        break;
                    case STOPPED:
                        protectionSet.setProtectionStatus(ProtectionStatus.DISABLED.toString());
                        break;
                    case PAUSED:
                        protectionSet.setProtectionStatus(ProtectionStatus.PAUSED.toString());
                        break;
                    case MIXED:
                        protectionSet.setProtectionStatus(ProtectionStatus.MIXED.toString());
                        break;
                    case READY:
                        protectionSet.setProtectionStatus(ProtectionStatus.ENABLED.toString());
                        break;
                }
                _dbClient.persistObject(protectionSet);
                break;
            }
        }
    }

    /**
     * After a failover, we need to swap personalities of source and target volumes,
     * and reset the target lists in each volume.
     * 
     * @param volume any volume in a protection set
     * @throws InternalException
     */
    private void updatePostFailoverPersonalities(Volume volume) throws InternalException {
        _log.info("Changing personality of source and targets");
        ProtectionSet protectionSet = _dbClient.queryObject(ProtectionSet.class, volume.getProtectionSet());
        List<URI> volumeIDs = new ArrayList<URI>();
        for (String volumeString : protectionSet.getVolumes()) {
            URI volumeURI;
            try {
                volumeURI = new URI(volumeString);
                volumeIDs.add(volumeURI);
            } catch (URISyntaxException e) {
                _log.error("URI syntax incorrect: ", e);
            }
        }

        // Changing personalities means that the source was on "Copy Name A" and it's now on "Copy Name B":
        // 1. a. Any previous TARGET volume that matches the copy name of the incoming volume is now a SOURCE volume
        // b. That voume needs its RP Targets volumes list filled-in as well; it's all of the devices that are
        // the same replication set name that aren't the new SOURCE volume itself.
        // 2. All SOURCE volumes are now TARGET volumes and their RP Target lists need to be null'd out
        //
        for (URI protectionVolumeID : volumeIDs) {
            Volume protectionVolume = _dbClient.queryObject(Volume.class, protectionVolumeID);
            if ((protectionVolume.getPersonality().equals(Volume.PersonalityTypes.TARGET.toString())) &&
                    (protectionVolume.getRpCopyName().equals(volume.getRpCopyName()))) {
                // This is a TARGET we failed over to. We need to build up all of its targets
                for (URI potentialTargetVolumeID : volumeIDs) {
                    Volume potentialTargetVolume = _dbClient.queryObject(Volume.class, potentialTargetVolumeID);
                    if (potentialTargetVolume.getRSetName() != null &&
                            potentialTargetVolume.getRSetName().equals(protectionVolume.getRSetName()) &&
                            !potentialTargetVolumeID.equals(protectionVolume.getId())) {
                        if (protectionVolume.getRpTargets() == null) {
                            protectionVolume.setRpTargets(new StringSet());
                        }
                        protectionVolume.getRpTargets().add(String.valueOf(potentialTargetVolume.getId()));
                    }
                }

                _log.info("Change personality of failover target " + protectionVolume.getWWN() + " to source");
                protectionVolume.setPersonality(Volume.PersonalityTypes.SOURCE.toString());
                volume.setAccessState(Volume.VolumeAccessState.READWRITE.name());
                _dbClient.persistObject(protectionVolume);
            } else if (protectionVolume.getPersonality().equals(Volume.PersonalityTypes.SOURCE.toString())) {
                _log.info("Change personality of source volume " + protectionVolume.getWWN() + " to target");
                protectionVolume.setPersonality(Volume.PersonalityTypes.TARGET.toString());
                volume.setAccessState(Volume.VolumeAccessState.NOT_READY.name());
                protectionVolume.setRpTargets(null);
                _dbClient.persistObject(protectionVolume);
            } else if (!protectionVolume.getPersonality().equals(Volume.PersonalityTypes.METADATA.toString())) {
                _log.info("Target " + protectionVolume.getWWN() + " is a target that remains a target");
                // TODO: Handle failover to CRR. Need to remove the CDP volumes (including journals)
            }
        }
    }

    /**
     * Validate Block snapshots that correspond to RP bookmarks. Some may no longer exist in the RP system, and we
     * need to mark them as invalid.
     * 
     * The strategy is as follows:
     * 1. Get all of the protection sets associated with the protection system
     * 2. Are there any Block Snapshots of type RP? (if not, don't bother cleaning up)
     * 3. Query the RP Appliance for all bookmarks for that CG (protection set)
     * 4. Find each block snapshot of type RP for each site
     * 5. If you can't find the bookmark in the RP list, move the block snapshot to inactive
     * 
     * @param protectionSystem Protection System
     */
    private void cleanupSnapshots(ProtectionSystem protectionSystem) throws RecoverPointException {
        // 1. Get all of the protection sets associated with the protection system
        Set<URI> protectionSetIDs = new HashSet<URI>();
        Set<Integer> cgIDs = new HashSet<Integer>();
        URIQueryResultList list = new URIQueryResultList();
        Constraint constraint = ContainmentConstraint.Factory.getProtectionSystemProtectionSetConstraint(protectionSystem.getId());
        _dbClient.queryByConstraint(constraint, list);
        Iterator<URI> it = list.iterator();
        while (it.hasNext()) {
            URI protectionSetId = it.next();

            // Get all snapshots that are part of this protection set.
            URIQueryResultList plist = new URIQueryResultList();
            Constraint pconstraint = ContainmentConstraint.Factory.getProtectionSetBlockSnapshotConstraint(protectionSetId);
            _dbClient.queryByConstraint(pconstraint, plist);
            if (plist.iterator().hasNext()) {
                // OK, we know there are snapshots for this protection set/CG.
                // Retrieve all of the bookmarks associated with this protection set/CG later on by adding to the list now
                ProtectionSet protectionSet = _dbClient.queryObject(ProtectionSet.class, protectionSetId);
                if (protectionSet != null && !protectionSet.getInactive()) {
                    protectionSetIDs.add(protectionSet.getId());
                    cgIDs.add(Integer.valueOf(protectionSet.getProtectionId()));
                }
            }
        }

        // 2. No reason to bother the RPAs if there are no protection sets for this protection system.
        if (protectionSetIDs.isEmpty()) {
            _log.info("Block Snapshot of RP Bookmarks cleanup not run for this protection system. No Protections or RP Block Snapshots found on protection system: "
                    + protectionSystem.getLabel());
            return;
        }

        // 3. Query the RP appliance for all of the bookmarks for these CGs in one call
        BiosCommandResult result = getRPBookmarks(protectionSystem, cgIDs);
        GetBookmarksResponse bookmarkMap = (GetBookmarksResponse) result.getObjectList().get(0);

        // 4. Go through each protection set's snapshots and see if they're there.
        it = protectionSetIDs.iterator();
        while (it.hasNext()) {
            URI protectionSetId = it.next();
            ProtectionSet protectionSet = _dbClient.queryObject(ProtectionSet.class, protectionSetId);

            // Now find this snapshot in the returned list of snapshots
            // The map should have an entry for that CG with an empty list if it looked and couldn't find any. (a successful empty set)
            if (protectionSet.getProtectionId() != null &&
                    bookmarkMap.getCgBookmarkMap() != null &&
                    bookmarkMap.getCgBookmarkMap().get(Integer.valueOf(protectionSet.getProtectionId())) != null) {

                // Get all snapshots that are part of this protection set.
                URIQueryResultList plist = new URIQueryResultList();
                Constraint pconstraint = ContainmentConstraint.Factory.getProtectionSetBlockSnapshotConstraint(protectionSetId);
                _dbClient.queryByConstraint(pconstraint, plist);
                Iterator<URI> snapshotIter = plist.iterator();
                while (snapshotIter.hasNext()) {
                    URI snapshotId = snapshotIter.next();
                    BlockSnapshot snapshot = _dbClient.queryObject(BlockSnapshot.class, snapshotId);
                    boolean deleteSnapshot = true;

                    if (snapshot.getInactive()) {
                        // Don't bother deleting or processing if the snapshot is already on its way out.
                        deleteSnapshot = false;
                    } else if (snapshot.getEmCGGroupCopyId() == null) {
                        // If something bad happened and we weren't able to get the site information off of the snapshot
                        _log.info("Found that ViPR Snapshot corresponding to RP Bookmark is missing Site information, thus not analyzing for automated deletion. "
                                + snapshot.getId() +
                                " - " + protectionSet.getLabel() + ":" + snapshot.getEmInternalSiteName() + ":" + snapshot.getEmName());
                        deleteSnapshot = false;
                    } else if (!bookmarkMap.getCgBookmarkMap().get(Integer.valueOf(protectionSet.getProtectionId())).isEmpty()) {
                        for (RPBookmark bookmark : bookmarkMap.getCgBookmarkMap().get(Integer.valueOf(protectionSet.getProtectionId()))) {
                            // bookmark (from RP) vs. snapshot (from ViPR)
                            if (snapshot.getEmName().equalsIgnoreCase(bookmark.getBookmarkName()) &&
                                    snapshot.getEmCGGroupCopyId().equals(bookmark.getCGGroupCopyUID().getGlobalCopyUID().getCopyUID())) {
                                deleteSnapshot = false;
                                _log.info("Found that ViPR Snapshot corresponding to RP Bookmark still exists, thus saving in ViPR: "
                                        + snapshot.getId() +
                                        " - " + protectionSet.getLabel() + ":" + snapshot.getEmInternalSiteName() + ":"
                                        + snapshot.getEmCGGroupCopyId() + ":" + snapshot.getEmName());
                            }
                        }
                    } else {
                        // Just for debugging, otherwise useless
                        _log.debug("Found that ViPR Snapshot corresponding to RP Bookmark doesn't exist, thus going to delete from ViPR: "
                                + snapshot.getId() +
                                " - " + protectionSet.getLabel() + ":" + snapshot.getEmInternalSiteName() + ":"
                                + snapshot.getEmCGGroupCopyId() + ":" + snapshot.getEmName());
                    }

                    if (deleteSnapshot) {
                        // 5. We couldn't find the bookmark, and the query for it was successful, so it's time to mark it as gone
                        _log.info("Found that ViPR Snapshot corresponding to RP Bookmark no longer exists, thus deleting in ViPR: "
                                + snapshot.getId() +
                                " - " + protectionSet.getLabel() + ":" + snapshot.getEmInternalSiteName() + ":"
                                + snapshot.getEmCGGroupCopyId() + ":" + snapshot.getEmName());
                        _dbClient.markForDeletion(snapshot);
                    }
                }
            } else if (protectionSet.getProtectionId() == null) {
                _log.error("Can not determine the consistency group ID of protection set: " + protectionSet.getLabel()
                        + ", can not perform any cleanup of snapshots.");
            } else {
                _log.info("No consistency groups were found associated with protection system: " + protectionSystem.getLabel()
                        + ", can not perform cleanup of snapshots.");
            }
        }
    }

    /**
     * For an RP configuration, determine the arrays that each site can see.
     * 
     * @param system RP system
     * @return command result object, object list [0] has List<SiteArrays>
     * @throws RecoverPointException
     */
    public BiosCommandResult getRPArrayMappings(ProtectionSystem system) throws RecoverPointException {
        _log.info("getRPArrayMappings {} - start", system.getId());
        RecoverPointClient rp = RPHelper.getRecoverPointClient(system);

        Set<RPSite> sites = rp.getAssociatedRPSites();

        // For determining which storage system to use with the RPA, we will look at the Network created as a result of
        // a NetworkSystem (aka switch or Fabric Manager) discovery. We look at each NetworkSystem to check if the RPA WWN(aka RP Initiator)
        // is in one of the
        // Networks/VSANs and if we do find a match then we look at the arrays in that Network/VSAN and determine which one to use.
        // For now, this code assumes there are Networks/VSANs created on the switch, might need to tweak this if there are no
        // Networks/VSANs.

        // One more important note : Networks/VSANs have be configured on the switch in order for zoning to work and NetworkSystem to
        // discover.
        // No Networks/VSANs on the switch means, none of this will work and zoning has to be done by the end-user prior to using StorageOS.

        // Get all the NetworkSystems already in the system (if there are any NetworkSystems configured).
        // Possible zoning candidates can be identified only if there is a NetworkSystem configured.
        List<SiteArrays> rpSiteArrays = new ArrayList<SiteArrays>();
        List<URI> networkSystemList = _dbClient.queryByType(NetworkSystem.class, true);
        boolean isNetworkSystemConfigured = false;
        if (networkSystemList.iterator().hasNext()) {
            List<URI> allNetworks = _dbClient.queryByType(Network.class, true);

            // Transfer to a reset-able list
            List<URI> networks = new ArrayList<>();
            for (URI networkURI : allNetworks) {
                networks.add(networkURI);
            }

            for (RPSite site : sites) {
                // Get the initiators for this site.
                Map<String, Map<String, String>> rpaWWNs = rp.getInitiatorWWNs(site.getInternalSiteName());
                SiteArrays siteArrays = new SiteArrays();
                siteArrays.setArrays(new HashSet<String>());
                siteArrays.setSite(site);

                // Add an RP site -> initiators entry to the protection system
                StringSet siteInitiators = new StringSet();
                for(String rpaId : rpaWWNs.keySet()) {
                    siteInitiators.addAll(rpaWWNs.get(rpaId).keySet());                	
                }
                system.putSiteIntitiatorsEntry(site.getInternalSiteName(), siteInitiators);

                // Check to see if the RP initiator is in any Network - Based on which Network the RP initiator is in,
                // we can look for the arrays in that Network that are potential candidates for connectivity.
                for (String rpaId : rpaWWNs.keySet()) {

                    boolean foundNetworkForRPCluster = false;
                    
                    for(Map.Entry<String, String> rpaWWN : rpaWWNs.get(rpaId).entrySet()) {

                        String wwn = rpaWWN.getKey();

                        Initiator initiator = new Initiator();
                        initiator.addInternalFlags(Flag.RECOVERPOINT);
                        // Remove all non alpha-numeric characters, excluding "_", from the hostname
                        String rpClusterName = site.getSiteName().replaceAll(NON_ALPHA_NUMERICS, "");
                        _log.info(String.format("Setting RP initiator cluster name : %s", rpClusterName));
                        initiator.setClusterName(rpClusterName);
                        initiator.setProtocol("FC");
                        initiator.setIsManualCreation(false);

                        // Group RP initiators by their RPA. This will ensure that separate IGs are created for each RPA
                        // A child RP IG will be created containing all the RPA IGs
                        String hostName = rpClusterName + RPA + rpaId;
                        hostName = hostName.replaceAll(NON_ALPHA_NUMERICS, "");
                        _log.info(String.format("Setting RP initiator host name : %s", hostName));
                        initiator.setHostName(hostName);

                        _log.info(String.format("Setting Initiator port WWN : %s, nodeWWN : %s", rpaWWN.getKey(), rpaWWN.getValue()));
                        initiator.setInitiatorPort(rpaWWN.getKey());
                        initiator.setInitiatorNode(rpaWWN.getValue());

                        // Either get the existing initiator or create a new if needed
                        initiator = getOrCreateNewInitiator(initiator);

                        _log.info("Examining RP WWN: " + wwn.toUpperCase());
                        // Find the network associated with this wwn
                        for (URI networkURI : networks) {
                            Network network = _dbClient.queryObject(Network.class, networkURI);
                            _log.info("Examining Network: " + network.getLabel());
                            StringMap discoveredEndpoints = network.getEndpointsMap();

                            if (discoveredEndpoints.containsKey(rpaWWN.getKey().toUpperCase())) {
                                _log.info("WWN " + rpaWWN.getKey() + " is in Network : " + network.getLabel());
                                // Set this to true as we found the RP initiators in a Network on the Network system
                                isNetworkSystemConfigured = true;
                                foundNetworkForRPCluster = true;
                                for (String discoveredEndpoint : discoveredEndpoints.keySet()) {
                                    // Ignore the RP endpoints - RP WWNs have a unique prefix. We want to only return back non RP initiators in
                                    // that NetworkVSAN.
                                    if (discoveredEndpoint.startsWith(RP_INITIATOR_PREFIX)) {
                                        continue;
                                    }

                                    // Add the found endpoints to the list
                                    siteArrays.getArrays().add(discoveredEndpoint);
                                }
                            }
                        }
                    }
                    
                    if (!foundNetworkForRPCluster) {
                        // This is not an error to the end-user. When they add a network system, everything will rediscover correctly.
                        _log.warn(String.format("Network systems are required when configuring RecoverPoint.  RP Cluster %s initiators are not seen in any configured network.", 
                                rpaId));
                    }
                    
                }
                // add to the list
                rpSiteArrays.add(siteArrays);
            }
        }

        // It is possible that the RP is already zoned to an array, try to get the list of available targets by querying the RPA.
        // If the RPA is 4.0+, then the operation is not supported, thrown an error.
        // Ideal way to use for an RP4.0 system is to come via the switch configuration.
        // If its a non RP4.0 system, then query the normal way and find out the targets/arrays from the RPAs.
        if (!rp.getAssociatedRPSites().isEmpty()) {
            _log.info("site1 version: " + rp.getAssociatedRPSites().iterator().next().getSiteVersion());
        }

        if (!isNetworkSystemConfigured) {
            // This is not an error to the end-user. When they add a network system, everything will rediscover correctly.
            _log.warn("Network systems are required when configuring RecoverPoint.");
        }

        _log.info("getRPArrayMappings {} - complete", system.getId());
        BiosCommandResult result = new BiosCommandResult();
        result.setCommandSuccess(true);
        result.setCommandStatus(Operation.Status.ready.name());
        List<Object> returnList = new ArrayList<Object>();
        returnList.add(rpSiteArrays);
        result.setObjectList(returnList);
        return result;
    }

    /**
     * Get an initiator as specified by the passed initiator data. First checks
     * if an initiator with the specified port already exists in the database,
     * and simply returns that initiator, otherwise creates a new initiator.
     *
     * @param initiatorParam The data for the initiator.
     *
     * @return A reference to an initiator.
     *
     * @throws InternalException When an error occurs querying the database.
     */
    private Initiator getOrCreateNewInitiator(Initiator initiatorParam)
            throws InternalException {
        Initiator initiator = null;
        URIQueryResultList resultsList = new URIQueryResultList();
        _dbClient.queryByConstraint(AlternateIdConstraint.Factory.getInitiatorPortInitiatorConstraint(
                initiatorParam.getInitiatorPort()), resultsList);
        Iterator<URI> resultsIter = resultsList.iterator();
        if (resultsIter.hasNext()) {
            initiator = _dbClient.queryObject(Initiator.class, resultsIter.next());
            // If the hostname has been changed then we need to update the
            // Initiator object to reflect that change.
            if (NullColumnValueGetter.isNotNullValue(initiator.getHostName())
                    && !initiator.getHostName().equals(initiatorParam.getHostName())) {
                initiator.setHostName(initiatorParam.getHostName());
                _dbClient.updateObject(initiator);
            }
        } else {
            initiatorParam.setId(URIUtil.createId(Initiator.class));
            _dbClient.createObject(initiatorParam);
            initiator = initiatorParam;
        }

        return initiator;
    }

    /**
     * For an RP configuration, get all RP bookmarks for the CGs provided
     * 
     * @param system RP system
     * @param cgIDs IDs of the consistency groups to get the bookmarks
     * @return command result object, object list [0] has GetBookmarksResponse
     * @throws RecoverPointException
     */
    public BiosCommandResult getRPBookmarks(ProtectionSystem system, Set<Integer> cgIDs) throws RecoverPointException {
        _log.info("getRPBookmarks {} - start", system.getId());
        RecoverPointClient rp = RPHelper.getRecoverPointClient(system);
        GetBookmarksResponse bookmarkResponse = rp.getRPBookmarks(cgIDs);
        _log.info("getRPBookmarks {} - complete", system.getId());
        BiosCommandResult result = new BiosCommandResult();
        result.setCommandSuccess(true);
        result.setCommandStatus(Operation.Status.ready.name());
        List<Object> returnList = new ArrayList<Object>();
        returnList.add(bookmarkResponse);
        result.setObjectList(returnList);
        return result;
    }

    /**
     * For an RP configuration, get some basic discovery information
     * 
     * @param protectionSystem RP system
     * @return command result object, object list [0] has List<SiteArrays>
     * @throws RecoverPointException
     */
    public BiosCommandResult discoverRPSystem(ProtectionSystem protectionSystem) throws RecoverPointException {
        _log.info("discoverRPSystem {} - start", protectionSystem.getId());
        RecoverPointClient rp = RPHelper.getRecoverPointClient(protectionSystem);
        Set<RPSite> rpSites = rp.getAssociatedRPSites();

        // If there are no associated RP Sites for the this IP, throw an exception.
        if (rpSites == null || rpSites.isEmpty()) {
            throw DeviceControllerExceptions.recoverpoint.noAssociatedRPSitesFound(protectionSystem
                    .getIpAddress());
        }

        RPSite firstRpSite = rpSites.iterator().next();

        // Update the protection system metrics
        RecoverPointStatisticsResponse response = rp.getRPSystemStatistics();
        _rpStatsHelper.updateProtectionSystemMetrics(protectionSystem, rpSites, response, _dbClient);

        _log.info("discoverRPSystem {} - complete", protectionSystem.getId());
        BiosCommandResult result = new BiosCommandResult();
        result.setCommandSuccess(true);
        result.setCommandStatus(Operation.Status.ready.name());
        List<Object> returnList = new ArrayList<Object>();
        String serialNumber = firstRpSite.getSiteGUID();
        // Don't include the site id at the end of the serial number for the RP System serial number.
        protectionSystem.setInstallationId(serialNumber.substring(0, serialNumber.lastIndexOf(":")));
        protectionSystem.setNativeGuid(NativeGUIDGenerator.generateNativeGuid(
                ProtectionSystem._RP, protectionSystem.getInstallationId()));

        // Clear out the existing cluster management IPs so they can
        // be re-populated below.
        _log.info("Clear out existing management IPs. The list will be repopulated...");
        protectionSystem.getClusterManagementIPs().clear();

        // Keep a map of the site names
        StringMap rpSiteNamesMap = new StringMap();

        for (RPSite rpSite : rpSites) {
            if (!rpSite.getSiteManagementIPv4().equals(protectionSystem.getIpAddress())
                    && !protectionSystem.getClusterManagementIPs().contains(rpSite.getSiteManagementIPv4())) {
                // Add cluster management IP if it's not the one we registered with, the one
                // we register is stored in ProtectionSystem.getIpAddress() and is the default
                // ip to use.
                _log.info(String.format("Adding management ip [%s] for cluster [%s] " +
                        "to valid cluster management ip addresses.", rpSite.getSiteManagementIPv4(), rpSite.getSiteName()));
                protectionSystem.getClusterManagementIPs().add(rpSite.getSiteManagementIPv4());
            }
            rpSiteNamesMap.put(rpSite.getInternalSiteName(), rpSite.getSiteName());
        }

        protectionSystem.setRpSiteNames(rpSiteNamesMap);

        // Set the version, but verify that it is supported. If it's not an exception will be
        // thrown.
        protectionSystem.setMajorVersion(firstRpSite.getSiteVersion());
        this.verifyMinimumSupportedFirmwareVersion(protectionSystem);

        returnList.add(protectionSystem);
        result.setObjectList(returnList);
        return result;
    }

    @SuppressWarnings("unchecked")
    private void discoverRPSiteArrays(ProtectionSystem rpSystem) throws RecoverPointCollectionException {
        _log.info("BEGIN RecoverPointProtection.discoveryProtectionSystem()");
        // Retrieve the storage device info from the database.
        ProtectionSystem storageObj = rpSystem;

        // Wait for any storage system discovery to complete
        waitForStorageSystemDiscovery();

        // Get the rp system's array mappings from the RP client
        BiosCommandResult result = getRPArrayMappings(storageObj);
        _log.info("discoverProtectionSystem(): after rpa array mappings with result" + result.getCommandStatus());

        RPSiteArray rpSiteArray = null;
        if (result.getCommandSuccess()) {
            // Current implementation:
            // 1. Clear out any of its entries regarding associations
            // 2. For each RPSite object, there is an associated RP storage system
            // 3. Find the storage system in the database
            // 4. Fill in associations
            List<URI> ids = _dbClient.queryByType(RPSiteArray.class, true);
            for (URI id : ids) {
                _log.info("discoverProtectionSystem(): reading rpsitearray: " + id.toASCIIString());
                rpSiteArray = _dbClient.queryObject(RPSiteArray.class, id);
                if (rpSiteArray == null) {
                    continue;
                }

                if ((rpSiteArray.getRpProtectionSystem() != null) && (rpSiteArray.getRpProtectionSystem().equals(storageObj.getId()))) {
                    _log.info("discoverProtectionSystem(): removing rpsitearray: " + id.toASCIIString() + " : " + rpSiteArray.toString());
                    _dbClient.markForDeletion(rpSiteArray);
                } else if (rpSiteArray.getRpProtectionSystem() == null) {
                    _log.error("RPSiteArray " + id.toASCIIString() + " does not have a parent assigned, therefore it is an orphan.");
                }
            }

            // Map the information from the RP client to information in our database
            for (SiteArrays siteArray : (List<SiteArrays>) result.getObjectList().get(0)) {
                for (String arrayWWN : siteArray.getArrays()) {
                    // Find the array that corresponds to the wwn endpoint we found
                    URIQueryResultList storagePortList = new URIQueryResultList();
                    StoragePort storagePort = null;
                    _dbClient.queryByConstraint(AlternateIdConstraint.Factory.getStoragePortEndpointConstraint(WwnUtils.convertWWN(
                            arrayWWN, WwnUtils.FORMAT.COLON)),
                            storagePortList);
                    List<URI> storagePortURIs = new ArrayList<URI>();
                    for (URI uri : storagePortList) {
                        storagePort = _dbClient.queryObject(StoragePort.class, uri);
                        if (storagePort != null && !storagePort.getInactive()
                                && storagePort.getRegistrationStatus().equals(RegistrationStatus.REGISTERED.name())) {
                            // ignore cinder managed storage system's port
                            StorageSystem system = _dbClient.queryObject(StorageSystem.class, storagePort.getStorageDevice());
                            if (!DiscoveredDataObject.Type.openstack.name().equals(system.getSystemType())) {
                                storagePortURIs.add(uri);
                            }
                        }
                    }
                    if (!storagePortURIs.isEmpty()) {
                        storagePort = _dbClient.queryObject(StoragePort.class, storagePortURIs).get(0);
                        rpSiteArray = new RPSiteArray();
                        rpSiteArray.setInactive(false);
                        rpSiteArray.setLabel(siteArray.getSite().getSiteName() + ":" + arrayWWN);
                        rpSiteArray.setRpProtectionSystem(storageObj.getId());
                        rpSiteArray.setStorageSystem(storagePort.getStorageDevice());
                        rpSiteArray.setArraySerialNumber(_dbClient.queryObject(StorageSystem.class, storagePort.getStorageDevice())
                                .getSerialNumber());
                        rpSiteArray.setRpInternalSiteName(siteArray.getSite().getInternalSiteName());
                        rpSiteArray.setRpSiteName(siteArray.getSite().getSiteName());
                        rpSiteArray.setId(URIUtil.createId(RPSiteArray.class));
                        _log.info("discoverProtectionSystem(): adding rpsitearray: " + rpSiteArray.getId().toASCIIString() + " : "
                                + rpSiteArray.toString());
                        _dbClient.createObject(rpSiteArray);
                    } else {
                        _log.warn("RecoverPoint found array endpoint " + arrayWWN + " however the endpoint could " +
                                "not be found in existing configured arrays.  Register arrays before registering RecoverPoint " +
                                "or rerun RecoverPoint discover after registering arrays.");
                    }
                }
            }

        }
        _log.info("END RecoverPointProtection.discoveryProtectionSystem()");
    }

    /**
     * Check to see if this storageport already exists
     * 
     * @param nativeGuid native guid of the storage port
     * @return StoragePort object
     */
    protected StoragePort checkPortExistsInDB(String nativeGuid) {
        StoragePort port = null;
        // use NativeGuid to lookup Pools in DB
        List<StoragePort> portInDB = CustomQueryUtility.getActiveStoragePortByNativeGuid(_dbClient, nativeGuid);
        if (portInDB != null && !portInDB.isEmpty()) {
            port = portInDB.get(0);
        }
        return port;
    }

    /**
     * Wait for any RP-supported storage system discovery to complete.
     * This helps us maintain good connectivity data and makes pool matching
     * work better.
     */
    private void waitForStorageSystemDiscovery() {
        final int maxWaitTimeSeconds = 30 * 60; // 30 minutes
        final int sleepIntervalSeconds = 15;
        int totalSleep = 0;
        List<URI> storageSystemIds = _dbClient.queryByType(StorageSystem.class, true);
        if (!storageSystemIds.iterator().hasNext()) {
            return;
        }

        // Copy the IDs to a real list so we can reset/reuse
        List<URI> allStorageSystemIds = new ArrayList<>();
        for (URI storageSystemId : storageSystemIds) {
            allStorageSystemIds.add(storageSystemId);
        }

        while (totalSleep < maxWaitTimeSeconds) {
            List<StorageSystem> storageSystems = _dbClient.queryObject(StorageSystem.class, allStorageSystemIds);
            boolean slept = false;
            for (StorageSystem storageSystem : storageSystems) {
                if (storageSystem.getDiscoveryStatus().equals(DataCollectionJobStatus.IN_PROGRESS.name())) {
                    _log.info("Sleeping due to discovery running on storage system: " +
                            (storageSystem.getSerialNumber() != null ? storageSystem.getSerialNumber() : storageSystem.getId()));
                    try {
                        slept = true;
                        totalSleep += sleepIntervalSeconds;
                        Thread.sleep(sleepIntervalSeconds * 1000);
                        // No reason to query for other storage systems. By the time we're done with this sleep,
                        // those others may already be done. So just start from the beginning again.
                        break;
                    } catch (InterruptedException e) {
                        // Ignore
                    }
                }
            }

            // We didn't get tripped up by discovers running in the system, return.
            if (!slept) {
                _log.info("Found no Storage Systems that are currently in progress with discovery");
                return;
            }
        }

        _log.info("RP discovery is going to proceed even though storage systems seem to still be in discovery mode");
    }

    private void discoverCluster(ProtectionSystem protectionSystem) {

        URI protectionSystemURI = protectionSystem.getId();

        _log.info("discoverCluster information for protection system {} - start", protectionSystemURI);

        // Get the rp system's array mappings from the RP client
        // TODO: Other methods do their BL in here. This one seems to do all of its BL up in the bios method.
        BiosCommandResult result = discoverRPSystem(protectionSystem);
        if (result.getCommandSuccess()) {
            _log.info("discoverCluster information for protection system {} - successful", protectionSystemURI);
            ProtectionSystem system = (ProtectionSystem) result.getObjectList().get(0);

            // Look to see if it's a duplicate of another entry.
            URIQueryResultList list = new URIQueryResultList();
            // check for duplicate ProtectionSystem.
            _dbClient.queryByConstraint(AlternateIdConstraint.Factory
                    .getProtectionSystemByNativeGuidConstraint(system.getNativeGuid()), list);
            for (URI systemID : list) {
                if (!systemID.equals(system.getId())) {
                    ProtectionSystem persistedSystem = _dbClient.queryObject(ProtectionSystem.class, systemID);
                    if ((persistedSystem != null) && (!persistedSystem.getInactive())) {
                        // The new system violates constraints that it can not contain IP addresses of other protection systems.
                        // This is usually caught much much higher, however in the case where discover catches it, we need to
                        // mark this object for deletion.
                        _dbClient.persistObject(system); // not sure if its necessary to mark it as unregistered
                        _dbClient.markForDeletion(system);
                        throw DeviceControllerExceptions.recoverpoint
                                .duplicateProtectionSystem(system.getLabel(), system.getId());
                    }
                }
            }

            // Persist the fields that you got during discovery
            _dbClient.persistObject(system);
        }

        _log.info("discoverCluster information for protection system {} - complete", protectionSystemURI);
    }

    /**
     * Discovers the Virtual Arrays associated to the Protection System.
     * 
     * @param protectionSystem A reference to the Protection System
     */
    private void discoverConnectivity(ProtectionSystem protectionSystem) {
        // Retrieve the Virtual Arrays for this Protection System
        ConnectivityUtil.updateRpSystemConnectivity(protectionSystem, _dbClient);
    }

    /**
     * Discovers the Storage Systems associated to the Protection System.
     * 
     * @param protectionSystem A reference to the Protection System
     */
    private void discoverAssociatedStorageSystems(ProtectionSystem protectionSystem) {
        // Find all the RPSiteArrays that are associated to this protection system
        List<RPSiteArray> siteArrays = CustomQueryUtility
                .queryActiveResourcesByConstraint(_dbClient, RPSiteArray.class,
                        AlternateIdConstraint.Factory.getConstraint(RPSiteArray.class,
                                "rpProtectionSystem",
                                protectionSystem.getId().toString()));

        // For each RPSiteArray, if there is a Storage System, add it to the list of
        // associated Storage Systems for this Protection System

        // TODO: May not be the most efficient way to do this; suggested that we have a way to determine
        // which objects in the StringSet are new, modified, or old and go from there. It could be that
        // nothing really changed.

        // But for now, force the associatedStorageSystems StringSet to be cleared and
        // use the setter so that setChanged(true) is invoked for Cassandra.
        StringSet associatedStorageSystems = protectionSystem.getAssociatedStorageSystems();
        associatedStorageSystems.clear();
        protectionSystem.setAssociatedStorageSystems(associatedStorageSystems);

        for (RPSiteArray siteArray : siteArrays) {
            if (siteArray != null && siteArray.getStorageSystem() != null) {
                StorageSystem storageSystem = _dbClient.queryObject(StorageSystem.class, siteArray.getStorageSystem());
                String serialNumber = storageSystem.getSerialNumber();

                // The visible storage arrays map has more precise serial number info, leverage it if we can, otherwise just use the
                // serial number from the storage system.
                if (protectionSystem.getSiteVisibleStorageArrays() != null) {
                    for (Map.Entry<String, AbstractChangeTrackingSet<String>> clusterStorageSystemSerialNumberEntry : protectionSystem
                            .getSiteVisibleStorageArrays().entrySet()) {
                        if (siteArray.getRpInternalSiteName().equals(clusterStorageSystemSerialNumberEntry.getKey())) {
                            for (String clusterSerialNumber : clusterStorageSystemSerialNumberEntry.getValue()) {
                                // Helper method to load the storage system by serial number
                                URI foundStorageSystemURI = ConnectivityUtil.findStorageSystemBySerialNumber(clusterSerialNumber,
                                        _dbClient, StorageSystemType.BLOCK);
                                if (storageSystem.getId().equals(foundStorageSystemURI)) {
                                    serialNumber = clusterSerialNumber;
                                    break;
                                }
                            }
                        }
                    }
                }

                // One last check, maybe we were not able to leverage the visible storage arrays, if that's the case and we have visible
                // VPLEXs with the serial number concatenated together, we will artificially create 2 separate entries. One for each leg of
                // the VPLEX.
                if (ConnectivityUtil.isAVPlex(storageSystem) && serialNumber.contains(":")) {
                    String[] splitSerialNumber = serialNumber.split(":");

                    String firstHalf = splitSerialNumber[0];
                    String secondHalf = splitSerialNumber[1];

                    // Check the network connectivity between the RP site and the storage array
                    if (isNetworkConnected(firstHalf, siteArray)) {
                        // Add first half
                        protectionSystem.getAssociatedStorageSystems()
                                .add(ProtectionSystem.generateAssociatedStorageSystem(siteArray.getRpInternalSiteName(),
                                        String.valueOf(firstHalf)));
                    }
                        
                    // Second half to be added next
                    serialNumber = secondHalf;
                }

                // Check the network connectivity between the RP site and the storage array
                if (isNetworkConnected(serialNumber, siteArray)) {
                    protectionSystem.getAssociatedStorageSystems()
                    .add(ProtectionSystem.generateAssociatedStorageSystem(siteArray.getRpInternalSiteName(),
                            String.valueOf(serialNumber)));
                }
            }
        }

        _dbClient.updateObject(protectionSystem);
    }

    /**
     * Check the connectivity of a storage system to the RP cluster
     * 
     * @param serialNumber serial number of the storage system
     * @param siteArray RPSiteArray object
     * @return true if the storage array has network connectivity to the RP cluster
     */
    private boolean isNetworkConnected(String serialNumber, RPSiteArray siteArray) {
        // Get the storage system object associated with the serial number sent in.
        URI foundStorageSystemURI = ConnectivityUtil.findStorageSystemBySerialNumber(serialNumber,
                _dbClient, StorageSystemType.BLOCK);
        if (foundStorageSystemURI == null) {
            _log.info(String.format("Could not find a registered storage system associated with serial number %s", serialNumber));
            _log.info(String.format("No registered network connectivity found between storage system %s and RP site %s", serialNumber, siteArray.getRpInternalSiteName()));
            return false;
        }
        
        // Find all of the initiators associated with the RP Cluster in the site array object
        ProtectionSystem rpSystem = _dbClient.queryObject(ProtectionSystem.class, siteArray.getRpProtectionSystem());
        if (rpSystem == null) {
            _log.error(String.format("Could not find a registered protection system associated with URI %s", siteArray.getRpProtectionSystem()));
            _log.info(String.format("No registered network connectivity found between storage system %s and RP site %s", serialNumber, siteArray.getRpInternalSiteName()));
            return false;
        }
        
        // Make sure initiators are loaded for the entire protection system
        if (rpSystem.getSiteInitiators() == null) {
            _log.error(String.format("Could not find initiators associated with protection system %s", rpSystem.getLabel()));
            _log.info(String.format("No registered network connectivity found between storage system %s and RP site %s", serialNumber, siteArray.getRpInternalSiteName()));
            return false;
        }
        
        // Make sure initiators are loaded for the RP cluster
        if (rpSystem.getSiteInitiators().get(siteArray.getRpInternalSiteName()) == null) {
            _log.error(String.format("Could not find initiators associated with protection system %s on RP cluster %s", rpSystem.getLabel(), siteArray.getRpInternalSiteName()));
            _log.info(String.format("No registered network connectivity found between storage system %s and RP site %s", serialNumber, siteArray.getRpInternalSiteName()));
            return false;
        }
        
        // For each initiator associated with the RP cluster in this RPSiteArray, see if we can find a route to the storage system
        for (String portWwn : rpSystem.getSiteInitiators().get(siteArray.getRpInternalSiteName())) {
            Initiator initiator = ExportUtils.getInitiator(portWwn, _dbClient);                    
            if (initiator == null) {
                // This is a database inconsistency issue.  Report an error and continue.
                _log.error(String.format("Could not find initiator %s in the database, even though ProtectionSystem %s references it.", portWwn, rpSystem.getLabel()));
            }
            
            StorageSystem storageSystem = _dbClient.queryObject(StorageSystem.class, foundStorageSystemURI);
            if (storageSystem == null) {
                // This is a database inconsistency issue.  Report an error and continue.
                _log.error(String.format("Could not find storage system %s in the database, even though ProtectionSystem %s references it.", foundStorageSystemURI, rpSystem.getLabel()));
            }

            // If we can at least find one initiator that is connected to the storage system, we can return true.
            if (ConnectivityUtil.isInitiatorConnectedToStorageSystem(initiator, storageSystem, null, _dbClient)) {
                _log.info(String.format("Found initiator %s can be connected to storage system %s", initiator.getInitiatorPort(), serialNumber));
                return true;
            }
        }
        
        return false;
    }

    /**
     * Discovers the Storage Systems associated to the Protection System.
     * 
     * @param protectionSystem A reference to the Protection System
     */
    private void discoverVisibleStorageSystems(ProtectionSystem protectionSystem) {
        RecoverPointClient rp = RPHelper.getRecoverPointClient(protectionSystem);

        Map<String, Set<String>> siteStorageSystems = rp.getArraysForClusters();
        if (protectionSystem.getSiteVisibleStorageArrays() != null) {
            protectionSystem.getSiteVisibleStorageArrays().clear();
        } else {
            protectionSystem.setSiteVisibleStorageArrays(new StringSetMap());
        }

        List<URI> storageSystemIDs = _dbClient.queryByType(StorageSystem.class, true);
        if (storageSystemIDs == null) {
            return;
        }

        List<StorageSystem> storageSystems = _dbClient.queryObject(StorageSystem.class, storageSystemIDs);
        if (storageSystems == null) {
            return;
        }

        // Assemble the storage systems map for the protection systems object. The RP client only
        // knows the storage system serial number, so we need to translate them to storage system IDs
        // (for the arrays ViPR knows about)
        if (siteStorageSystems != null) {
            for (Map.Entry<String, Set<String>> clusterEntry : siteStorageSystems.entrySet()) {
                if (clusterEntry.getValue() == null || clusterEntry.getValue().isEmpty()) {
                    continue;
                }

                for (String serialNumber : clusterEntry.getValue()) {
                    if (serialNumber == null || serialNumber.isEmpty()) {
                        continue;
                    }

                    // Find the storage system ID associated with this serial number

                    // We have a serial number from the RP appliances, and for the most part, that works
                    // with a Constraint Query, but in the case of VPLEX, the serial number object for distributed
                    // VPLEX clusters will contain two serial numbers, not just one. So we need a long-form
                    // way of finding those VPLEXs as well.
                    Iterator<StorageSystem> activeSystemListItr = storageSystems.iterator();
                    StorageSystem foundStorageSystem = null;
                    while (activeSystemListItr.hasNext() && foundStorageSystem == null) {
                        StorageSystem system = activeSystemListItr.next();
                        if (NullColumnValueGetter.isNotNullValue(system.getSerialNumber())
                                && system.getSerialNumber().contains(serialNumber)) {
                            foundStorageSystem = system;
                        }
                    }

                    if (foundStorageSystem != null) {
                        protectionSystem.addSiteVisibleStorageArrayEntry(clusterEntry.getKey(), serialNumber);
                        _log.info(String.format(
                                "RP Discovery found RP cluster %s is configured to use a registered storage system: %s, %s",
                                clusterEntry.getKey(), serialNumber, foundStorageSystem.getNativeGuid()));
                    } else {
                        _log.info(String
                                .format("RP Discovery found RP cluster %s is configured to use a storage system: %s, but it is not configured for use in ViPR",
                                        clusterEntry.getKey(), serialNumber));
                    }
                }
            }
        }

        _dbClient.persistObject(protectionSystem);
    }

    /**
     * Verifies the firmware version of the RP Site Appliance is supported,
     * otherwise aborts the discovery.
     * 
     * @param system - The Protection System we are trying to create using the RP Site Appliance
     * @throws ControllerException thrown if firmware version is not supported
     */
    private void verifyMinimumSupportedFirmwareVersion(ProtectionSystem system) throws ControllerException {
        // Validate the minimum supported version of the RP Site Appliance using the VersionChecker support class
        try {
            String version = system.getMajorVersion();
            String minimumSupportedVersion = VersionChecker.getMinimumSupportedVersion(Type.valueOf(system.getSystemType()));
            _log.info("Verifying version details : Minimum Supported Version {} - Discovered Firmware Version {}",
                    minimumSupportedVersion, version);

            if (VersionChecker.verifyVersionDetails(minimumSupportedVersion, version) < 0) {
                system.setCompatibilityStatus(DiscoveredDataObject.CompatibilityStatus.INCOMPATIBLE.name());
                throw DeviceControllerExceptions.recoverpoint.versionNotSupported(version, minimumSupportedVersion);
            } else {
                system.setCompatibilityStatus(DiscoveredDataObject.CompatibilityStatus.COMPATIBLE.name());
            }
        } catch (Exception ex) {
            throw DeviceControllerExceptions.recoverpoint.verifyVersionFailed(ex.getMessage(), ex);
        }
    }

    @Override
    public void scan(AccessProfile accessProfile)
            throws BaseCollectionException {
        // TODO Auto-generated method stub
    }

    @Override
    public void collectStatisticsInformation(AccessProfile accessProfile)
            throws BaseCollectionException {
        // TODO Auto-generated method stub
    }
}
