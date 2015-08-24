/*
 * Copyright (c) 2013 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.api.service.impl.resource;

import static com.emc.storageos.api.mapper.DbObjectMapper.toNamedRelatedResource;
import static com.emc.storageos.api.mapper.TaskMapper.toTask;

import java.net.URI;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;

import javax.ws.rs.WebApplicationException;
import javax.ws.rs.core.Response;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.emc.storageos.Controller;
import com.emc.storageos.api.service.impl.placement.RPVPlexScheduler;
import com.emc.storageos.api.service.impl.placement.RPVPlexScheduler.RPVPlexVarrayVpool;
import com.emc.storageos.api.service.impl.placement.RecoverPointScheduler;
import com.emc.storageos.api.service.impl.resource.fullcopy.BlockFullCopyManager;
import com.emc.storageos.api.service.impl.resource.utils.VirtualPoolChangeAnalyzer;
import com.emc.storageos.blockorchestrationcontroller.BlockOrchestrationController;
import com.emc.storageos.blockorchestrationcontroller.VolumeDescriptor;
import com.emc.storageos.coordinator.client.service.CoordinatorClient;
import com.emc.storageos.db.client.URIUtil;
import com.emc.storageos.db.client.constraint.AlternateIdConstraint;
import com.emc.storageos.db.client.model.BlockConsistencyGroup;
import com.emc.storageos.db.client.model.BlockSnapshot;
import com.emc.storageos.db.client.model.DataObject;
import com.emc.storageos.db.client.model.DataObject.Flag;
import com.emc.storageos.db.client.model.DiscoveredDataObject;
import com.emc.storageos.db.client.model.NamedURI;
import com.emc.storageos.db.client.model.Operation;
import com.emc.storageos.db.client.model.Project;
import com.emc.storageos.db.client.model.ProtectionSystem;
import com.emc.storageos.db.client.model.RPSiteArray;
import com.emc.storageos.db.client.model.StoragePool;
import com.emc.storageos.db.client.model.StorageSystem;
import com.emc.storageos.db.client.model.StringSet;
import com.emc.storageos.db.client.model.VirtualArray;
import com.emc.storageos.db.client.model.VirtualPool;
import com.emc.storageos.db.client.model.VirtualPool.MetroPointType;
import com.emc.storageos.db.client.model.Volume;
import com.emc.storageos.db.client.model.VpoolProtectionVarraySettings;
import com.emc.storageos.db.client.model.util.BlockConsistencyGroupUtils;
import com.emc.storageos.db.client.util.CustomQueryUtility;
import com.emc.storageos.db.client.util.NullColumnValueGetter;
import com.emc.storageos.model.ResourceTypeEnum;
import com.emc.storageos.model.TaskList;
import com.emc.storageos.model.TaskResourceRep;
import com.emc.storageos.model.block.VirtualPoolChangeParam;
import com.emc.storageos.model.block.VolumeCreate;
import com.emc.storageos.model.systems.StorageSystemConnectivityList;
import com.emc.storageos.model.systems.StorageSystemConnectivityRestRep;
import com.emc.storageos.model.vpool.VirtualPoolChangeOperationEnum;
import com.emc.storageos.protectioncontroller.impl.recoverpoint.RPHelper;
import com.emc.storageos.recoverpoint.exceptions.RecoverPointException;
import com.emc.storageos.svcs.errorhandling.resources.APIException;
import com.emc.storageos.svcs.errorhandling.resources.InternalException;
import com.emc.storageos.util.ConnectivityUtil;
import com.emc.storageos.util.ConnectivityUtil.StorageSystemType;
import com.emc.storageos.volumecontroller.ControllerException;
import com.emc.storageos.volumecontroller.Protection;
import com.emc.storageos.volumecontroller.RPProtectionRecommendation;
import com.emc.storageos.volumecontroller.Recommendation;
import com.emc.storageos.volumecontroller.VPlexProtection;
import com.emc.storageos.volumecontroller.VPlexProtectionRecommendation;
import com.emc.storageos.volumecontroller.impl.utils.VirtualPoolCapabilityValuesWrapper;
import com.google.common.base.Joiner;

/**
 * Block Service subtask (parts of larger operations) RecoverPoint implementation.
 */
/**
 * @author sreekb
 * 
 */
public class RPVPlexBlockServiceApiImpl extends AbstractBlockServiceApiImpl<RPVPlexScheduler> {
    private static final Logger _log = LoggerFactory.getLogger(RPVPlexBlockServiceApiImpl.class);

    public RPVPlexBlockServiceApiImpl() {
        super(DiscoveredDataObject.Type.rpvplex.name());
    }

    protected final static String CONTROLLER_SVC = "controllersvc";
    protected final static String CONTROLLER_SVC_VER = "1";

    private static final String VIRTUAL_ARRAYS_CONSTRAINT = "virtualArrays";
    private static final String UNKNOWN_VOL_TYPE = "unknown";

    private static final String SRC_COPY_SUFFIX = " - Original Production";
    private static final String MP_ACTIVE_COPY_SUFFIX = " - Active Production";
    private static final String MP_STANDBY_COPY_SUFFIX = " - Standby Production";

    private static final String PRIMARY_SRC_JOURNAL_SUFFIX = "-primary-source-journal";
    private static final String SECONDARY_SRC_JOURNAL_SUFFIX = "-secondary-source-journal";
    private static final String TGT_VOL_SUFFIX = "-target-";
    private static final String TGT_JOURNAL = "-target-journal-";

    private RPHelper _rpHelper;

    public void setRpHelper(RPHelper rpHelper) {
        _rpHelper = rpHelper;
    }

    VPlexBlockServiceApiImpl vplexBlockServiceApiImpl;
    RPBlockServiceApiImpl rpBlockServiceApiImpl;

    public VPlexBlockServiceApiImpl getVplexBlockServiceApiImpl() {
        return vplexBlockServiceApiImpl;
    }

    public void setVplexBlockServiceApiImpl(
            VPlexBlockServiceApiImpl vplexBlockServiceApiImpl) {
        this.vplexBlockServiceApiImpl = vplexBlockServiceApiImpl;
    }

    public RPBlockServiceApiImpl getRpBlockServiceApiImpl() {
        return rpBlockServiceApiImpl;
    }

    public void setRpBlockServiceApiImpl(RPBlockServiceApiImpl rpBlockServiceApiImpl) {
        this.rpBlockServiceApiImpl = rpBlockServiceApiImpl;
    }

    protected CoordinatorClient _coordinator;

    @Override
    public void setCoordinator(CoordinatorClient locator) {
        _coordinator = locator;
    }

    /**
     * Looks up controller dependency for given hardware
     * 
     * @param clazz controller interface
     * @param hw hardware name
     * @param <T>
     * @return
     */
    @Override
    protected <T extends Controller> T getController(Class<T> clazz, String hw) {
        return _coordinator.locateService(clazz, CONTROLLER_SVC, CONTROLLER_SVC_VER, hw, clazz.getSimpleName());
    }

    /**
     * Prepare Recommended Volumes for Protected scenarios only.
     * 
     * This method is responsible for acting the same as the unprotected "prepareRecommendedVolumes" call,
     * however it needs to create multiple volumes per single volume requests in order to generate protection.
     * 
     * Those most typical scenario is, that for any one volume requested in a CRR configuration, we create:
     * 1. One Source Volume
     * 2. One Source Journal Volume (minimum 10GB, otherwise 2.5X source size)
     * 3. One Target Volume on protection varray
     * 4. One Target Journal Volume on protection varray
     * 
     * In a CLR configuration, there are additional volumes created for the Local Target and Local Target Journal.
     * 
     * This method will assemble a ProtectionSet object in Cassandra that will describe the Protection that
     * will be created on the Protection System.
     * 
     * When other protection mechanisms come on board, the RP-ness of this method will need to be pulled out.
     * 
     * @param param volume create request
     * @param task task from request or generated
     * @param taskList task list
     * @param project project from request
     * @param originalVarray varray from request
     * @param originalVpool vpool from request
     * @param numberOfVolumesInRequest volume count from the request
     * @param recommendations list of resulting recommendations from placement
     * @param consistencyGroup consistency group ID
     * @param capabilities
     * @return list of volume URIs created
     */
    private List<URI> prepareVPlexRecommendedVolumes(VolumeCreate param, String task, TaskList taskList,
            Project project, VirtualArray originalVarray, VirtualPool originalVpool, Integer numberOfVolumesInRequest,
            List<Recommendation> recommendations, String volumeLabel, VirtualPoolCapabilityValuesWrapper capabilities,
            List<VolumeDescriptor> descriptors) {
        List<URI> volumeURIs = new ArrayList<URI>();
        Volume primarySourceJournalVolume = null;
        // The secondary source journal volume is used for MetroPoint.
        Volume secondarySourceJournalVolume = null;
        Volume targetJournalVolume = null;

        boolean createStandbyTargetJournal = false;
        boolean createTargetJournal = false;
        boolean isChangeVpool = false;
        boolean isSrcAndHaSwapped = VirtualPool.isRPVPlexProtectHASide(originalVpool);

        String volumeName = param.getName();

        // Need to check if we should swap src and ha, call the block scheduler code to
        // find out. Nothing will be changed for MetroPoint.
        VirtualArray haVarray = null;
        VirtualPool haVpool = null;
        RPVPlexVarrayVpool container = this.getBlockScheduler().new RPVPlexVarrayVpool();
        container.setSrcVarray(originalVarray);
        container.setSrcVpool(originalVpool);
        container.setHaVarray(haVarray);
        container.setHaVpool(haVpool);
        container = RPVPlexScheduler.setActiveProtectionAtHAVarray(container, _dbClient);

        // Use the new references post swap
        VirtualArray varray = container.getSrcVarray();
        VirtualPool vpool = container.getSrcVpool();

        // Save a reference to the CG, we'll need this later
        BlockConsistencyGroup consistencyGroup = capabilities.getBlockConsistencyGroup() == null ? null : _dbClient
                .queryObject(BlockConsistencyGroup.class, capabilities.getBlockConsistencyGroup());

        // If the CG already contains RP volumes, then we need to check if new/additional journal volumes need to be created, based on the
        // journal policy specified.
        List<Volume> cgSourceVolumes = _rpHelper.getCgVolumes(consistencyGroup.getId(), Volume.PersonalityTypes.SOURCE.toString());
        boolean isAdditionalJournalRequired = false;
        if (!cgSourceVolumes.isEmpty()) {
            isAdditionalJournalRequired = _rpHelper.isAdditionalJournalRequiredForCG(vpool.getJournalSize(), consistencyGroup,
                    param.getSize(),
                    numberOfVolumesInRequest, Volume.PersonalityTypes.SOURCE.toString(), cgSourceVolumes.get(0).getInternalSiteName());
            if (!isAdditionalJournalRequired) {
                primarySourceJournalVolume = _rpHelper.selectExistingJournalForSourceVolume(cgSourceVolumes, false);
            }
        }

        // Create an entire Protection object for each recommendation result.
        Iterator<Recommendation> recommendationsIter = recommendations.iterator();

        while (recommendationsIter.hasNext()) {
            VPlexProtectionRecommendation primaryRecommendation =
                    (VPlexProtectionRecommendation) recommendationsIter.next();

            // Determine if MetroPoint is enabled. If this is the case we need to create an additional
            // RP source journal for the HA side.
            boolean metroPointEnabled = VirtualPool.vPoolSpecifiesMetroPoint(vpool);

            VPlexProtectionRecommendation secondaryRecommendation = null;

            // VPLEX Distributed requires two Recommendations. The first is the VPlexProtectionRecommendation
            // recommendation. The second is a VPlexRecommendation recommendation for cluster 2. The second
            // Recommendation is embedded in the VPlexProtectionRecommendation object.
            if (primaryRecommendation.getSourceVPlexHaRecommendations() != null
                    && !primaryRecommendation.getSourceVPlexHaRecommendations().isEmpty()) {

                // There will only ever be 1 HA recommendation.
                if (primaryRecommendation.getSourceVPlexHaRecommendations() != null
                        && !primaryRecommendation.getSourceVPlexHaRecommendations().isEmpty()) {
                    secondaryRecommendation =
                            (VPlexProtectionRecommendation) primaryRecommendation.getSourceVPlexHaRecommendations().get(0);
                }
            }

            MetroPointType metroPointType = MetroPointType.INVALID;

            if (metroPointEnabled) {
                metroPointType = primaryRecommendation.getMetroPointType();
                validateMetroPointType(metroPointType);
            }

            // Get the number of volumes needed to be created for this recommendation.
            int volumeCountInRec = primaryRecommendation.getResourceCount();

            // For an upgrade to MetroPoint, even though the user would have chosen 1 volume to update, we need to update ALL
            // the RSets in the CG. We can't just update one RSet / volume.
            // So let's get ALL the source volumes in the CG and we will update them all to MetroPoint.
            // Each source volume will be exported to the HA side of the VPLEX (for MetroPoint visibility).
            // All source volumes will share the same secondary journal.
            List<Volume> allVolumesInCG = null;
            if (primaryRecommendation.isVpoolChangeProtectionAlreadyExists()) {
                allVolumesInCG = BlockConsistencyGroupUtils.getActiveVplexVolumesInCG(consistencyGroup, _dbClient,
                        Volume.PersonalityTypes.SOURCE);
                volumeCountInRec = allVolumesInCG.size();
                _log.info(String.format(
                        "Upgrade to MetroPoint, we need to get all existing volumes in the CG. Number of volumes to upgrade: %d",
                        volumeCountInRec));
            }

            Map<URI, URI> protectionVarrayTgtJournal = new HashMap<URI, URI>();
            Map<URI, URI> secondaryProtectionVarrayTgtJournal = new HashMap<URI, URI>();

            for (int volumeCount = 0; volumeCount < volumeCountInRec; volumeCount++) {
                // This method will handle creation of multiple volumes in a single request if that is desired.
                // Even though vplexApiImpl can handle multiple volumes, we will always send down resourceCount
                // of 1 to the VPLEX layer. The reason for doing that is, we want to be able to associate
                // all the volumes we create to RP replication sets, if we pass down resourceCount of more than 1
                // to the VPLEX layer, then it becomes very tricky to associate the volume to RP replication sets.

                // Let's not get into multiple of multiples, this class will handle multi volume creates.
                // So force the incoming VolumeCreate param to be set to 1 always from here on.
                primaryRecommendation.setResourceCount(1);
                if (secondaryRecommendation != null) {
                    secondaryRecommendation.setResourceCount(1);
                }

                String newVolumeLabel = generateDefaultVolumeLabel(volumeName, volumeCount, numberOfVolumesInRequest);

                // Assemble a Replication Set; A Collection of volumes. One production, and any number of targets.
                String rsetName = null;
                if (numberOfVolumesInRequest > 1) {
                    rsetName = "RSet-" + newVolumeLabel + "-" + (volumeCount + 1);
                } else {
                    rsetName = "RSet-" + newVolumeLabel;
                }

                // param name is an important field in this class. This name has to remain unique, especially when the number of volumes
                // requested to be created is more than 1.
                param.setName(newVolumeLabel);

                // VPLEX needs to be aware of the CG
                capabilities.put(VirtualPoolCapabilityValuesWrapper.BLOCK_CONSISTENCY_GROUP, consistencyGroup.getId());
                capabilities.put(VirtualPoolCapabilityValuesWrapper.PERSONALITY, Volume.PersonalityTypes.SOURCE.toString());
                capabilities.put(VirtualPoolCapabilityValuesWrapper.RESOURCE_COUNT, 1);

                // /////// SOURCE ///////////
                String srcCopyName = varray.getLabel() + SRC_COPY_SUFFIX;
                String activeSourceCopyName = "";
                String standbySourceCopyName = "";
                if (metroPointEnabled) {
                    VPlexProtectionRecommendation secondaryProtectionRecommendation = (VPlexProtectionRecommendation) secondaryRecommendation;
                    VirtualArray recHaVarray = _dbClient.queryObject(VirtualArray.class,
                            secondaryProtectionRecommendation.getVirtualArray());
                    activeSourceCopyName = varray.getLabel() + MP_ACTIVE_COPY_SUFFIX;
                    standbySourceCopyName = recHaVarray.getLabel() + MP_STANDBY_COPY_SUFFIX;
                }

                Volume sourceVolume = null;
                if (primaryRecommendation.getVpoolChangeVolume() == null) {
                    // Construct the list of recommendations to build the source and HA (if applicable)
                    // volumes.
                    List<Recommendation> sourceRecommendations = new ArrayList<Recommendation>();
                    sourceRecommendations.add(primaryRecommendation);
                    if (secondaryRecommendation != null) {
                        sourceRecommendations.add(secondaryRecommendation);
                    }

                    List<URI> volumes = new ArrayList<URI>();
                    descriptors.addAll(vplexBlockServiceApiImpl.createVPlexVolumeDescriptors(param, project, varray, vpool,
                            sourceRecommendations, task, capabilities, taskList, volumes));
                    sourceVolume = this.getVPlexVirtualVolume(volumes);
                    sourceVolume = prepareVPlexVirtualVolume(sourceVolume, project, varray, vpool, param.getSize(),
                            newVolumeLabel,
                            consistencyGroup, task, false,
                            ((RPProtectionRecommendation) primaryRecommendation).getProtectionDevice(),
                            Volume.PersonalityTypes.SOURCE, rsetName,
                            ((RPProtectionRecommendation) primaryRecommendation).getSourceInternalSiteName(),
                            (metroPointEnabled ? activeSourceCopyName : srcCopyName), null, primaryRecommendation, secondaryRecommendation);
                    volumeURIs.add(sourceVolume.getId());
                } else {
                    // Check to see if the source volume doesn't already have Protection
                    isChangeVpool = true;
                    if (!primaryRecommendation.isVpoolChangeProtectionAlreadyExists()) {
                        sourceVolume = _dbClient.queryObject(Volume.class, primaryRecommendation.getVpoolChangeVolume());

                        // If we are using the HA as the RP source, swap the Source and HA recs for the VPlexBlockServiceApiImpl.
                        // This is because the VPlexBlockServiceApiImpl will be creating the migration descriptors for the backend
                        // volumes and it doesn't really care that we are swapping the Source and HA it just needs the correct
                        // recommendations.
                        List<Recommendation> swapRecommendations = new ArrayList<Recommendation>();
                        if (isSrcAndHaSwapped) {
                            swapRecommendations.add(0, secondaryRecommendation);
                            swapRecommendations.add(1, primaryRecommendation);
                            // If we had to swap, that's means we had to use the HA vpool as the Source vpool
                            // and Source Vpool as HA vpool for placement to happen correctly. This can lead to weird
                            // instances when calling code that doesn't understand the swap.
                            // VPLEX doesn't really care about swap so let's make sure
                            // we use the originalVpool here to correctly to get the change vpool
                            // artifacts we need.
                            vpool = originalVpool;
                        } else {
                            swapRecommendations.add(0, primaryRecommendation);
                            swapRecommendations.add(1, secondaryRecommendation);
                        }

                        StorageSystem vplexStorageSystem = _dbClient.queryObject(StorageSystem.class, sourceVolume.getStorageController());
                        descriptors.addAll(vplexBlockServiceApiImpl.createChangeVirtualPoolDescriptors(vplexStorageSystem, sourceVolume,
                                vpool, task, swapRecommendations, capabilities));

                        if (isSrcAndHaSwapped) {
                            // Return things back to the way they were.
                            vpool = container.getSrcVpool();
                        }

                        sourceVolume = prepareVPlexVirtualVolume(sourceVolume, project, varray, vpool, param.getSize(),
                                newVolumeLabel, consistencyGroup, task, false,
                                ((RPProtectionRecommendation) primaryRecommendation).getProtectionDevice(),
                                Volume.PersonalityTypes.SOURCE, rsetName,
                                ((RPProtectionRecommendation) primaryRecommendation).getSourceInternalSiteName(),
                                metroPointEnabled ? activeSourceCopyName : srcCopyName, null, primaryRecommendation,
                                secondaryRecommendation);
                    } else {
                        // Make sure that the source volumes have the correct internal site information for their backing volumes
                        sourceVolume = allVolumesInCG.get(volumeCount);
                    }
                    volumeURIs.add(sourceVolume.getId());
                }

                // MetroPoint and Distributed RP+VPLEX(HA as RP source) Source volumes need to have their backing volumes set with the
                // correct internal
                // site name. The reason for this is so we know later on where to export the volumes to.
                //
                // This is very evident with MetroPoint as we need to export BOTH sides of the VPLEX Distributed Volume.
                //
                // This is less evident with Distributed RP+VPLEX that has "HA as RP source" set.
                // In this case we need to set it on the HA volume as that is the side to export (not the source side).
                // To do this we need to pass in a hint...
                // We need the (unswapped) original vpool and we then check the getHaVarrayConnectedToRp() value which tells us which
                // side(varray) to export.
                // This value will only be used if isSrcAndHaSwapped == true.
                setInternalSitesForSourceBackingVolumes(primaryRecommendation, secondaryRecommendation,
                        sourceVolume, metroPointEnabled, isSrcAndHaSwapped, originalVpool.getHaVarrayConnectedToRp());

                logVolumeInfo(sourceVolume);

                // /////// PRIMARY SOURCE JOURNAL ///////////
                // Check to see if the source volume doesn't already have Protection
                if (!primaryRecommendation.isVpoolChangeProtectionAlreadyExists()) {
                    // If there's no Journal volumes created yet, let's do that now.
                    if (primarySourceJournalVolume == null) {
                        VirtualArray journalVarray = _dbClient.queryObject(VirtualArray.class,
                                primaryRecommendation.getSourceJournalVarray());
                        VirtualPool journalVpool = _dbClient.queryObject(VirtualPool.class, primaryRecommendation.getSourceJournalVpool());
                        String journalSize = String.valueOf(RPHelper.getJournalSizeGivenPolicy(param.getSize(), vpool.getJournalSize(),
                                volumeCountInRec));
                        String sourceJournalVolumeName = new StringBuilder(newVolumeLabel).append(PRIMARY_SRC_JOURNAL_SUFFIX).toString();
                        String sourceInternalSiteName = ((RPProtectionRecommendation) primaryRecommendation).getSourceInternalSiteName();
                        URI journalStoragePoolUri = primaryRecommendation.getSourceJournalStoragePool();
                        if (VirtualPool.vPoolSpecifiesHighAvailability(journalVpool)) {
                            _log.info("Create VPLEX Source Journal");
                            primarySourceJournalVolume = createJournalVolume(primaryRecommendation, task, taskList, param, project,
                                    primaryRecommendation.getVPlexStorageSystem(), journalVarray, journalVpool,
                                    sourceJournalVolumeName, capabilities, descriptors, consistencyGroup,
                                    metroPointEnabled ? activeSourceCopyName : srcCopyName,
                                    sourceInternalSiteName, primaryRecommendation.getSourceDevice(),
                                    journalStoragePoolUri, journalSize);

                        } else {
                            _log.info("Create non-VPLEX Source Journal");
                            primarySourceJournalVolume = rpBlockServiceApiImpl.prepareJournalVolume(project, journalVarray, journalVpool,
                                    journalSize, primaryRecommendation, journalStoragePoolUri,
                                    sourceJournalVolumeName, consistencyGroup, task, false,
                                    primaryRecommendation.getProtectionDevice(),
                                    Volume.PersonalityTypes.METADATA,
                                    rsetName, sourceInternalSiteName, metroPointEnabled ? activeSourceCopyName : srcCopyName,
                                    sourceVolume.getId());
                        }

                        volumeURIs.add(primarySourceJournalVolume.getId());
                        logVolumeInfo(primarySourceJournalVolume);
                    } else {
                        _log.info(String.format("Re-using existing journal for active site - %s",
                                ((RPProtectionRecommendation) primaryRecommendation).getSourceInternalSiteName()));
                    }

                    if (sourceVolume != null) {
                        // associate the journal with the source volume
                        if (primarySourceJournalVolume != null) {
                            sourceVolume.setRpJournalVolume(primarySourceJournalVolume.getId());
                            _dbClient.persistObject(sourceVolume);
                        }
                    }
                }

                // /////// SECONDARY SOURCE JOURNAL ///////////
                // If MetroPoint is enabled we need to create the secondary journal volume
                if (metroPointEnabled) {
                    if (secondarySourceJournalVolume == null) {
                        VPlexProtectionRecommendation secondaryProtectionRecommendation = (VPlexProtectionRecommendation) secondaryRecommendation;
                        String secondarySourceJournalVolumeName = new StringBuilder(newVolumeLabel).append(SECONDARY_SRC_JOURNAL_SUFFIX)
                                .toString();
                        String secondarySourceJournalInternalSiteName = ((RPProtectionRecommendation) secondaryProtectionRecommendation)
                                .getSourceInternalSiteName();
                        // Get the HA varray
                        haVarray = _dbClient.queryObject(VirtualArray.class, secondaryProtectionRecommendation.getVirtualArray());
                        // Check to see if we need to add a secondary journal or not
                        if (isChangeVpool || cgSourceVolumes.isEmpty() || isAdditionalJournalRequired) {
                            String journalSize = String.valueOf(RPHelper.getJournalSizeGivenPolicy(param.getSize(), vpool.getJournalSize(),
                                    volumeCountInRec));
                            VirtualArray standbyJournalVarray = _dbClient.queryObject(VirtualArray.class,
                                    secondaryRecommendation.getStandbySourceJournalVarray());
                            VirtualPool standbyJournalVpool = _dbClient.queryObject(VirtualPool.class,
                                    secondaryRecommendation.getStandbySourceJournalVpool());
                            URI journalStoragePoolUri = secondaryRecommendation.getSourceJournalStoragePool();
                            if (VirtualPool.vPoolSpecifiesHighAvailability(standbyJournalVpool)) {
                                _log.info("Create VPLEX Source Journal For Standby");
                                secondarySourceJournalVolume = createJournalVolume(secondaryRecommendation, task, taskList, param, project,
                                        secondaryRecommendation.getVPlexStorageSystem(), standbyJournalVarray, standbyJournalVpool,
                                        secondarySourceJournalVolumeName, capabilities, descriptors, consistencyGroup,
                                        metroPointEnabled ? standbySourceCopyName : srcCopyName,
                                        secondarySourceJournalInternalSiteName, secondaryRecommendation.getSourceDevice(),
                                        journalStoragePoolUri, journalSize);
                            } else {
                                _log.info("Create non-VPLEX Source Journal for Standby");
                                secondarySourceJournalVolume = rpBlockServiceApiImpl.prepareJournalVolume(project, standbyJournalVarray,
                                        standbyJournalVpool,
                                        journalSize, secondaryRecommendation, journalStoragePoolUri,
                                        secondarySourceJournalVolumeName, consistencyGroup, task, false,
                                        secondaryRecommendation.getProtectionDevice(),
                                        Volume.PersonalityTypes.METADATA,
                                        rsetName, secondarySourceJournalInternalSiteName, metroPointEnabled ? standbySourceCopyName
                                                : srcCopyName,
                                        sourceVolume.getId());
                            }

                            _log.info("Secondary source journal volume = " + secondarySourceJournalVolume.getId().toASCIIString()
                                    + " controller = " +
                                    secondarySourceJournalVolume.getStorageController().toString());
                            volumeURIs.add(secondarySourceJournalVolume.getId());

                            if (sourceVolume != null) {
                                // associate the journal with the source volume
                                if (secondarySourceJournalVolume != null) {
                                    sourceVolume.setSecondaryRpJournalVolume(secondarySourceJournalVolume.getId());
                                    _dbClient.persistObject(sourceVolume);
                                }
                            }
                        } else {
                            _log.info(String.format("Re-using existing journal on stand-by site - %s",
                                    secondarySourceJournalInternalSiteName));
                            secondarySourceJournalVolume = _rpHelper.selectExistingJournalForSourceVolume(
                                    _rpHelper.getCgVolumes(consistencyGroup.getId(),
                                            Volume.PersonalityTypes.SOURCE.toString()), true);
                            sourceVolume.setSecondaryRpJournalVolume(secondarySourceJournalVolume.getId());
                            _dbClient.persistObject(sourceVolume);
                        }
                    } else {
                        // We enter this case when multiple volumes are requested in this request, just re-use the one we have already
                        // created/identified
                        if (sourceVolume != null) {
                            sourceVolume.setSecondaryRpJournalVolume(secondarySourceJournalVolume.getId());
                            _dbClient.persistObject(sourceVolume);
                        }
                    }
                    logVolumeInfo(secondarySourceJournalVolume);
                }

                // /////// TARGET(S) ///////////

                List<URI> primaryProtectionTargets = new ArrayList<URI>();
                // Consolidate all VPLEX and non-VPLEX targets
                List<URI> allPrimaryTargetVarrayURIs = new ArrayList<URI>();
                allPrimaryTargetVarrayURIs.addAll(primaryRecommendation.getVirtualArrayProtectionMap().keySet());
                allPrimaryTargetVarrayURIs.addAll(primaryRecommendation.getVarrayVPlexProtection().keySet());
                _log.info(String.format("Creating target copies and corresponding journals on %s",
                        Joiner.on("--").join(allPrimaryTargetVarrayURIs)));

                for (URI tgtVirtualArrayURI : allPrimaryTargetVarrayURIs) {
                    VirtualArray tgtVirtualArray = _dbClient.queryObject(VirtualArray.class, tgtVirtualArrayURI);

                    // Check to see if there is a change vpool of a already protected source, if so, we could potentially not need
                    // to provision this target.
                    if (primaryRecommendation.isVpoolChangeProtectionAlreadyExists()) {
                        Volume changeVpoolVolume = _dbClient.queryObject(Volume.class, primaryRecommendation.getVpoolChangeVolume());
                        Volume alreadyProvisionedTarget = RPHelper.findAlreadyProvisionedTargetVolume(changeVpoolVolume,
                                tgtVirtualArrayURI, _dbClient);
                        if (alreadyProvisionedTarget != null) {
                            _log.info(String.format("Existing target volume [%s] found for varray [%s].",
                                    alreadyProvisionedTarget.getLabel(), tgtVirtualArray.getLabel()));

                            // No need to go further, continue on to the next target varray
                            continue;
                        }
                    }

                    VpoolProtectionVarraySettings settings = _rpHelper.getProtectionSettings(vpool, tgtVirtualArray);

                    // By default, the target VirtualPool is the source VirtualPool
                    VirtualPool targetVirtualPool = vpool;

                    // If there's a VirtualPool in the protection settings that is different, use it instead.
                    if (settings.getVirtualPool() != null) {
                        targetVirtualPool = _dbClient.queryObject(VirtualPool.class, settings.getVirtualPool());
                    }

                    // Update the VolumeCreate param with the correct info for this Target
                    String targetVolumeName = new StringBuilder(newVolumeLabel).append(TGT_VOL_SUFFIX + tgtVirtualArray.getLabel())
                            .toString();
                    param.setName(targetVolumeName);
                    param.setVpool(targetVirtualPool.getId());
                    param.setVarray(tgtVirtualArray.getId());
                    param.setConsistencyGroup(consistencyGroup.getId());

                    List<URI> volumes = new ArrayList<URI>();
                    String targetCopyName = tgtVirtualArray.getLabel();
                    Volume targetVolume = null;
                    String copyInternalSiteName = "";

                    if (VirtualPool.vPoolSpecifiesHighAvailability(targetVirtualPool)) {
                        _log.info("Create VPLEX Target");
                        VPlexProtection vplexProtection = primaryRecommendation.getVarrayVPlexProtection().get(tgtVirtualArrayURI);
                        copyInternalSiteName = vplexProtection.getTargetInternalSiteName();

                        // Create a recommendation object to use the correct values for Target
                        VPlexProtectionRecommendation tgtRec = createTempRecommendation(primaryRecommendation,
                                vplexProtection.getTargetVplexDevice(),
                                tgtVirtualArray.getId(),
                                targetVirtualPool,
                                vplexProtection.getTargetDevice(),
                                vplexProtection.getTargetStoragePool());

                        // VPLEX Distributed requires two Recommendations.
                        // The first is the VPlexProtectionRecommendation recommendation.
                        // The second is a VPlexRecommendation recommendation for cluster 2.
                        // The second Recommendation is embedded in VPlexProtectionRecommendation object.
                        // Pass these two Recommendations to vplexBlockServiceApiImpl (if both exist).
                        List<Recommendation> targetRecommendations = new ArrayList<Recommendation>();
                        targetRecommendations.add(tgtRec);

                        if (vplexProtection.getTargetVPlexHaRecommendations() != null
                                && !vplexProtection.getTargetVPlexHaRecommendations().isEmpty()) {
                            targetRecommendations.addAll(vplexProtection.getTargetVPlexHaRecommendations());
                        }

                        // Set the resource count to 1 in all the recommendations to make sure the targets being HA volume are accounted
                        // during multi-volume create
                        for (Recommendation targetRecommendation : targetRecommendations) {
                            targetRecommendation.setResourceCount(1);
                        }

                        // VPLEX needs to be aware of the CG
                        capabilities.put(VirtualPoolCapabilityValuesWrapper.BLOCK_CONSISTENCY_GROUP, consistencyGroup.getId());
                        capabilities.put(VirtualPoolCapabilityValuesWrapper.PERSONALITY, Volume.PersonalityTypes.TARGET.toString());
                        capabilities.put(VirtualPoolCapabilityValuesWrapper.RESOURCE_COUNT, 1);

                        // If the target is RP+VPLEX/MetroPoint we need to engage the VPLEX Block Service
                        // to create the correct descriptors we need.
                        descriptors.addAll(vplexBlockServiceApiImpl.createVPlexVolumeDescriptors(param, project, tgtVirtualArray,
                                targetVirtualPool,
                                targetRecommendations, task, capabilities, taskList, volumes));
                        targetVolume = this.getVPlexVirtualVolume(volumes);

                        targetVolume = prepareVPlexVirtualVolume(targetVolume, project, tgtVirtualArray, targetVirtualPool,
                                param.getSize(),
                                targetVolumeName,
                                consistencyGroup, task, true,
                                primaryRecommendation.getProtectionDevice(),
                                Volume.PersonalityTypes.TARGET, rsetName,
                                vplexProtection.getTargetInternalSiteName(),
                                targetCopyName, sourceVolume.getId(), null, null);
                    } else {
                        _log.info("Create non-VPLEX Target");
                        Protection protection = primaryRecommendation.getVirtualArrayProtectionMap().get(tgtVirtualArrayURI);
                        copyInternalSiteName = protection.getTargetInternalSiteName();

                        // If the target is not RP+VPLEX/MetroPoint leverage the regular RP Block Service
                        // to create the target volume
                        // WJEIV -- review this use case
                        targetVolume = rpBlockServiceApiImpl.prepareVolume(null, project, tgtVirtualArray,
                                targetVirtualPool, param.getSize(),
                                primaryRecommendation,
                                targetVolumeName, consistencyGroup, task,
                                true,
                                primaryRecommendation.getProtectionDevice(),
                                Volume.PersonalityTypes.TARGET, rsetName,
                                protection.getTargetInternalSiteName(), targetCopyName, sourceVolume.getId(), null);

                    }

                    logVolumeInfo(targetVolume);
                    volumeURIs.add(targetVolume.getId());

                    // Check whether additional journals are required computation needs to happen only once per copy,
                    // irrespective of number of volumes requested
                    if (volumeCount == 0 && (cgSourceVolumes.isEmpty()
                            || _rpHelper.isAdditionalJournalRequiredForCG(settings.getJournalSize(), consistencyGroup, param.getSize(),
                            numberOfVolumesInRequest, Volume.PersonalityTypes.TARGET.toString(), copyInternalSiteName))) {
                        createTargetJournal = true;
                    }

                    // /////// TARGET JOURNAL///////////
                    if (createTargetJournal) {
                        String targetJournalVolumeName = new StringBuilder(newVolumeLabel).append(TGT_JOURNAL + tgtVirtualArray.getLabel())
                                .toString();
                        String journalSize = String.valueOf(RPHelper.getJournalSizeGivenPolicy(param.getSize(), settings.getJournalSize(),
                                volumeCountInRec));
                        VirtualArray targetJournalVarray = _dbClient.queryObject(VirtualArray.class,
                                settings.getJournalVarray() != null ? settings.getJournalVarray() : tgtVirtualArrayURI);
                        VirtualPool targetJournalVpool = _dbClient.queryObject(VirtualPool.class,
                                settings.getJournalVpool() != null ? settings.getJournalVpool() : settings.getVirtualPool());

                        URI targetJournalStoragePool;
                        if (VirtualPool.vPoolSpecifiesHighAvailability(targetVirtualPool)) {
                            targetJournalStoragePool = primaryRecommendation.getVarrayVPlexProtection().get(tgtVirtualArrayURI)
                                    .getTargetJournalStoragePool();
                        } else {
                            targetJournalStoragePool = primaryRecommendation.getVirtualArrayProtectionMap().get(tgtVirtualArrayURI)
                                    .getTargetJournalStoragePool();
                        }

                        if (VirtualPool.vPoolSpecifiesHighAvailability(targetJournalVpool)) {
                            _log.info("Create VPLEX Target Journal");
                            VPlexProtection vplexProtection = primaryRecommendation.getVarrayVPlexProtection().get(tgtVirtualArrayURI);
                            targetJournalVolume = createJournalVolume(primaryRecommendation, task, taskList, param, project,
                                    vplexProtection.getTargetVplexDevice(), tgtVirtualArray, targetVirtualPool,
                                    targetJournalVolumeName, capabilities, descriptors, consistencyGroup, targetCopyName,
                                    vplexProtection.getTargetInternalSiteName(), vplexProtection.getTargetJournalDevice(),
                                    vplexProtection.getTargetJournalStoragePool(), journalSize);
                        } else {
                            _log.info("Create non-VPLEX Target Journal");
                            targetJournalVolume = rpBlockServiceApiImpl.prepareJournalVolume(project, targetJournalVarray,
                                    targetJournalVpool,
                                    journalSize, primaryRecommendation, targetJournalStoragePool,
                                    targetJournalVolumeName, consistencyGroup, task, true,
                                    primaryRecommendation.getProtectionDevice(),
                                    Volume.PersonalityTypes.METADATA,
                                    null, copyInternalSiteName,
                                    targetCopyName, null);
                        }
                        volumeURIs.add(targetJournalVolume.getId());
                        protectionVarrayTgtJournal.put(tgtVirtualArray.getId(), targetJournalVolume.getId());
                        // Set the journal volume reference for the target
                        targetVolume.setRpJournalVolume(targetJournalVolume.getId());
                        _dbClient.persistObject(targetVolume);
                    } else {
                        _log.info(String.format("Re-use existing journal for target site %s", copyInternalSiteName));
                        // We are creating multiple resources so grab the journal we already created for this
                        // protection virtual array and re-use it.
                        targetJournalVolume = _rpHelper.selectExistingJournalForTargetVolume(
                                _rpHelper.getCgVolumes(consistencyGroup.getId(),
                                        Volume.PersonalityTypes.TARGET.toString()), protectionVarrayTgtJournal, tgtVirtualArray.getId(),
                                copyInternalSiteName);
                        targetVolume.setRpJournalVolume(targetJournalVolume.getId());
                        _dbClient.persistObject(targetVolume);
                    }
                    logVolumeInfo(targetJournalVolume);
                    primaryProtectionTargets.add(tgtVirtualArrayURI);
                    createTargetJournal = false;
                }

                // /////// METROPOINT TARGET(S) ///////////
                // If metropoint is chosen and two local copies are configured, one copy on each side
                // then we need to create targets for the second (stand-by) leg.
                if (metroPointEnabled && metroPointType != MetroPointType.SINGLE_REMOTE) {
                    // Consolidate all VPLEX and non-VPLEX targets
                    List<URI> allSecondaryTargetVarrayURIs = new ArrayList<URI>();
                    allSecondaryTargetVarrayURIs.addAll(secondaryRecommendation.getVirtualArrayProtectionMap().keySet());
                    allSecondaryTargetVarrayURIs.addAll(secondaryRecommendation.getVarrayVPlexProtection().keySet());

                    for (URI tgtVirtualArrayURI : allSecondaryTargetVarrayURIs) {
                        // Skip over protection targets that have already been created as part of the
                        // primary recommendation.
                        if (primaryProtectionTargets.contains(tgtVirtualArrayURI)) {
                            continue;
                        }

                        VirtualArray tgtVirtualArray = _dbClient.queryObject(VirtualArray.class, tgtVirtualArrayURI);

                        // Check to see if there is a change vpool of a already protected source, if so, we could potentially not need
                        // to provision this target. This would sit on the primary recommendation, not the secondary
                        if (primaryRecommendation.isVpoolChangeProtectionAlreadyExists()) {
                            Volume changeVpoolVolume = _dbClient.queryObject(Volume.class, primaryRecommendation.getVpoolChangeVolume());
                            Volume alreadyProvisionedTarget = RPHelper.findAlreadyProvisionedTargetVolume(changeVpoolVolume,
                                    tgtVirtualArrayURI, _dbClient);
                            if (alreadyProvisionedTarget != null) {
                                _log.info(String.format("Existing target volume [%s] found for varray [%s].",
                                        alreadyProvisionedTarget.getLabel(), tgtVirtualArray.getLabel()));

                                // No need to go further, continue on to the next target varray
                                continue;
                            }
                        }

                        VpoolProtectionVarraySettings settings = _rpHelper.getProtectionSettings(vpool, tgtVirtualArray);

                        // By default, the target VirtualPool is the source VirtualPool
                        VirtualPool targetVirtualPool = vpool;

                        // If there's a VirtualPool in the protection settings that is different, use it instead.
                        if (settings.getVirtualPool() != null) {
                            targetVirtualPool = _dbClient.queryObject(VirtualPool.class, settings.getVirtualPool());
                        }

                        // Update the VolumeCreate param with the correct info for this Target
                        String targetVolumeName = new StringBuilder(newVolumeLabel).append(TGT_VOL_SUFFIX + tgtVirtualArray.getLabel())
                                .toString();
                        param.setName(targetVolumeName);
                        param.setVpool(targetVirtualPool.getId());
                        param.setVarray(tgtVirtualArray.getId());
                        param.setConsistencyGroup(consistencyGroup.getId());

                        List<URI> volumes = new ArrayList<URI>();
                        String targetCopyName = tgtVirtualArray.getLabel();
                        Volume targetVolume = null;

                        if (VirtualPool.vPoolSpecifiesHighAvailability(targetVirtualPool)) {
                            _log.info("Create VPLEX Secondary Target");
                            VPlexProtection vplexProtection = secondaryRecommendation.getVarrayVPlexProtection().get(tgtVirtualArrayURI);

                            // Create a recommendation object to use the correct values for Target
                            VPlexProtectionRecommendation tgtRec = createTempRecommendation(secondaryRecommendation,
                                    vplexProtection.getTargetVplexDevice(),
                                    tgtVirtualArray.getId(),
                                    targetVirtualPool,
                                    vplexProtection.getTargetDevice(),
                                    vplexProtection.getTargetStoragePool());

                            // VPLEX Distributed requires two Recommendations.
                            // The first is the VPlexProtectionRecommendation recommendation.
                            // The second is a VPlexRecommendation recommendation for cluster 2.
                            // The second Recommendation is embedded in VPlexProtectionRecommendation object.
                            // Pass these two Recommendations to vplexBlockServiceApiImpl (if both exist).
                            List<Recommendation> targetRecommendations = new ArrayList<Recommendation>();
                            targetRecommendations.add(tgtRec);

                            if (vplexProtection.getTargetVPlexHaRecommendations() != null
                                    && !vplexProtection.getTargetVPlexHaRecommendations().isEmpty()) {
                                targetRecommendations.addAll(vplexProtection.getTargetVPlexHaRecommendations());
                            }

                            // Set the resource count to 1 in all the recommendations to make sure the targets being HA volume are accounted
                            // during multi-volume create
                            for (Recommendation targetRecommendation : targetRecommendations) {
                                targetRecommendation.setResourceCount(1);
                            }

                            // VPLEX needs to be aware of the CG
                            capabilities.put(VirtualPoolCapabilityValuesWrapper.BLOCK_CONSISTENCY_GROUP, consistencyGroup.getId());
                            capabilities.put(VirtualPoolCapabilityValuesWrapper.PERSONALITY, Volume.PersonalityTypes.TARGET.toString());
                            capabilities.put(VirtualPoolCapabilityValuesWrapper.RESOURCE_COUNT, 1);

                            descriptors.addAll(vplexBlockServiceApiImpl.createVPlexVolumeDescriptors(param, project, tgtVirtualArray,
                                    targetVirtualPool, targetRecommendations, task, capabilities, taskList, volumes));
                            targetVolume = this.getVPlexVirtualVolume(volumes);
                            targetVolume = prepareVPlexVirtualVolume(targetVolume, project, tgtVirtualArray, targetVirtualPool,
                                    param.getSize(),
                                    targetVolumeName,
                                    consistencyGroup, task, true,
                                    secondaryRecommendation.getProtectionDevice(),
                                    Volume.PersonalityTypes.TARGET, rsetName,
                                    vplexProtection.getTargetInternalSiteName(),
                                    targetCopyName, sourceVolume.getId(), null, null);
                        } else {
                            _log.info("Create non-VPLEX Secondary Target");
                            Protection protection = secondaryRecommendation.getVirtualArrayProtectionMap().get(tgtVirtualArrayURI);

                            // If the target is not RP+VPLEX/MetroPoint we treat leverage the regular RP Block Service
                            // to create the target volume
                            // WJEIV -- Review this use case
                            targetVolume = rpBlockServiceApiImpl.prepareVolume(null, project, tgtVirtualArray,
                                    targetVirtualPool, param.getSize(),
                                    secondaryRecommendation,
                                    targetVolumeName, consistencyGroup, task,
                                    true,
                                    secondaryRecommendation.getProtectionDevice(),
                                    Volume.PersonalityTypes.TARGET, rsetName,
                                    protection.getTargetInternalSiteName(), targetCopyName, sourceVolume.getId(), settings);
                        }

                        volumeURIs.add(targetVolume.getId());
                        logVolumeInfo(targetVolume);
                        // whether additional journals are required computation needs to happen only once per copy, irrespective of number
                        // of volumes requested
                        if (volumeCount == 0
                                && (cgSourceVolumes.isEmpty() || _rpHelper.isAdditionalJournalRequiredForCG(settings.getJournalSize(),
                                        consistencyGroup, param.getSize(),
                                        numberOfVolumesInRequest, Volume.PersonalityTypes.TARGET.toString(),
                                        targetVolume.getInternalSiteName()))) {
                            createStandbyTargetJournal = true;
                        }

                        // /////// METROPOINT TARGET JOURNAL///////////
                        // RP VPLEX Journals are always VPLEX local.
                        if (createStandbyTargetJournal) {
                            String targetJournalVolumeName = new StringBuilder(newVolumeLabel).append(
                                    TGT_JOURNAL + tgtVirtualArray.getLabel()).toString();
                            String journalSize = String.valueOf(RPHelper.getJournalSizeGivenPolicy(param.getSize(),
                                    settings.getJournalSize(), volumeCountInRec));
                            VirtualArray targetJournalVarray = _dbClient.queryObject(VirtualArray.class,
                                    settings.getJournalVarray() != null ? settings.getJournalVarray() : tgtVirtualArrayURI);

                            VirtualPool targetJournalVpool = null;
                            if (settings.getJournalVpool() != null) {
                                targetJournalVpool = _dbClient.queryObject(VirtualPool.class, settings.getJournalVpool());
                            } else {
                                // If no target journal vpool is specified, default to the target vpool.
                                targetJournalVpool = targetVirtualPool;
                            }

                            URI targetJournalStoragePool;
                            String targetInternalSiteName = "";
                            if (VirtualPool.vPoolSpecifiesHighAvailability(targetVirtualPool)) {
                                targetJournalStoragePool = secondaryRecommendation.getVarrayVPlexProtection().get(tgtVirtualArrayURI)
                                        .getTargetJournalStoragePool();
                                targetInternalSiteName = secondaryRecommendation.getVarrayVPlexProtection().get(tgtVirtualArrayURI)
                                        .getTargetInternalSiteName();
                            } else {
                                targetJournalStoragePool = secondaryRecommendation.getVirtualArrayProtectionMap().get(tgtVirtualArrayURI)
                                        .getTargetJournalStoragePool();
                                targetInternalSiteName = secondaryRecommendation.getVirtualArrayProtectionMap().get(tgtVirtualArrayURI)
                                        .getTargetInternalSiteName();
                            }

                            if (VirtualPool.vPoolSpecifiesHighAvailability(targetJournalVpool)) {
                                _log.info("Create VPLEX Secondary Target Journal");
                                VPlexProtection vplexProtection = secondaryRecommendation.getVarrayVPlexProtection()
                                        .get(tgtVirtualArrayURI);
                                targetJournalVolume = createJournalVolume(secondaryRecommendation, task, taskList, param, project,
                                        vplexProtection.getTargetVplexDevice(), targetJournalVarray, targetJournalVpool,
                                        targetJournalVolumeName, capabilities, descriptors, consistencyGroup, targetCopyName,
                                        targetInternalSiteName, vplexProtection.getTargetJournalDevice(),
                                        targetJournalStoragePool, journalSize);
                            } else {
                                _log.info("Create non-VPLEX Secondary Target Journal");
                                targetJournalVolume = rpBlockServiceApiImpl.prepareJournalVolume(project, targetJournalVarray,
                                        targetJournalVpool,
                                        journalSize, secondaryRecommendation, targetJournalStoragePool,
                                        targetJournalVolumeName, consistencyGroup, task, true,
                                        secondaryRecommendation.getProtectionDevice(),
                                        Volume.PersonalityTypes.METADATA,
                                        null, targetInternalSiteName,
                                        tgtVirtualArray.getLabel(), null);
                            }

                            volumeURIs.add(targetJournalVolume.getId());

                            // Persist the journal created for this target virtual array. We will need to re-use it if
                            // we are creating multiple resources.
                            secondaryProtectionVarrayTgtJournal.put(tgtVirtualArray.getId(), targetJournalVolume.getId());
                            // Set the journal volume reference for the target
                            targetVolume.setRpJournalVolume(targetJournalVolume.getId());
                            _dbClient.persistObject(targetVolume);
                        } else {
                            _log.info("Re-use existing Secondary Target Journal");
                            targetJournalVolume = _rpHelper.selectExistingJournalForTargetVolume(
                                    _rpHelper.getCgVolumes(consistencyGroup.getId(),
                                            Volume.PersonalityTypes.TARGET.toString()),
                                    secondaryProtectionVarrayTgtJournal, tgtVirtualArray.getId(), targetVolume.getInternalSiteName());
                            targetVolume.setRpJournalVolume(targetJournalVolume.getId());
                            _dbClient.persistObject(targetVolume);
                        }
                        logVolumeInfo(targetJournalVolume);
                    }
                    createStandbyTargetJournal = false;
                }
            }
        }

        // Reset the capabilities object
        if (consistencyGroup != null) {
            capabilities.put(VirtualPoolCapabilityValuesWrapper.BLOCK_CONSISTENCY_GROUP, consistencyGroup.getId());
            capabilities.put(VirtualPoolCapabilityValuesWrapper.PERSONALITY, Volume.PersonalityTypes.SOURCE.toString());
        }

        return volumeURIs;
    }

    /**
     * Helper method to display volume attributes as they are prepared.
     * 
     * @param volume
     */
    private void logVolumeInfo(Volume volume) {
        if (null != volume && !NullColumnValueGetter.isNullURI(volume.getId())) {
            StringBuilder buff = new StringBuilder();
            buff.append(String.format("%nPreparing Volume:%n"));
            buff.append(String.format("\t VolumePersonality : [%s]%n", volume.getPersonality()));
            buff.append(String.format("\t Volume Internal Site : [%s]%n", volume.getInternalSiteName()));
            buff.append(String.format("\t URI : [%s] - Name : [%s]%n", volume.getId(), volume.getLabel()));
            if (!NullColumnValueGetter.isNullURI(volume.getStorageController())) {
                StorageSystem storageSystem = _dbClient.queryObject(StorageSystem.class, volume.getStorageController());
                buff.append(String.format("\t StorageSystem : [%s]%n", storageSystem.getLabel()));

            }

            if (!NullColumnValueGetter.isNullURI(volume.getPool())) {
                StoragePool pool = _dbClient.queryObject(StoragePool.class, volume.getPool());
                buff.append(String.format("\t StoragePool : [%s]%n", pool.getLabel()));
            }

            _log.info(buff.toString());
        }
    }

    /**
     * Validates and logs the MetroPointType. An exception is thrown for invalid
     * MetroPoint types.
     * 
     * @param metroPointType the MetroPoint type to validate.
     */
    private void validateMetroPointType(MetroPointType metroPointType) {
        StringBuffer sb = new StringBuffer();

        if (metroPointType == MetroPointType.SINGLE_REMOTE) {
            sb.append("Preparing volumes for a MetroPoint configuration with a single remote copy.");
        } else if (metroPointType == MetroPointType.LOCAL_ONLY) {
            sb.append("Preparing volumes for a MetroPoint configuration with local only copies.");
        } else if (metroPointType == MetroPointType.ONE_LOCAL_REMOTE) {
            sb.append("Preparing volumes for a MetroPoint configuration with one local and one remote copy.");
        } else if (metroPointType == MetroPointType.TWO_LOCAL_REMOTE) {
            sb.append("Preparing volumes for a MetroPoint configuration with two local copies and one remote copy.");
        } else if (metroPointType == MetroPointType.INVALID) {
            throw APIException.badRequests.invalidMetroPointConfiguration();
        }

        _log.info(sb.toString());
    }

    /**
     * Convenience method to create RP+VPLEX Journals.
     * 
     * @param recommendation The current recommendation
     * @param task The task
     * @param taskList The task list
     * @param param Create Param object
     * @param project The project
     * @param vplexURI The VPLEX to use
     * @param varray The varray
     * @param vpool The vpool
     * @param journalVolumeName Generated journal name
     * @param capabilities The capabilities, modified for VPLEXBlockServiceApiImpl
     * @param descriptors The main descriptors object used
     * @param consistencyGroup The current CG
     * @param copyName The name of the copy
     * @param internalSiteName The RP site used, determined by placement
     * @param backendStorageSystemURI The backing storage system
     * @param storagePool The pool for the Journal
     * @param journalSize The calculated Journal size
     * @return new VPLEX Journal Volume
     */
    private Volume createJournalVolume(VPlexProtectionRecommendation recommendation, String task, TaskList taskList,
            VolumeCreate param, Project project, URI vplexURI, VirtualArray varray, VirtualPool vpool,
            String journalVolumeName, VirtualPoolCapabilityValuesWrapper capabilities, List<VolumeDescriptor> descriptors,
            BlockConsistencyGroup consistencyGroup, String copyName, String internalSiteName, URI backendStorageSystemURI, URI storagePool,
            String journalSize) {

        // Create a recommendation object.
        VPlexProtectionRecommendation journalRec = createTempRecommendation(recommendation, vplexURI, varray.getId(),
                vpool, backendStorageSystemURI, storagePool);

        // Build the list of Recommendations for the journal
        List<Recommendation> journalRecommendations = new ArrayList<Recommendation>();
        journalRecommendations.add(journalRec);

        // VPLEX needs to be aware of the CG
        capabilities.put(VirtualPoolCapabilityValuesWrapper.BLOCK_CONSISTENCY_GROUP, consistencyGroup.getId());
        capabilities.put(VirtualPoolCapabilityValuesWrapper.PERSONALITY, Volume.PersonalityTypes.METADATA.toString());
        capabilities.put(VirtualPoolCapabilityValuesWrapper.RESOURCE_COUNT, 1);

        // Tweak the VolumeCreate slightly for Journal
        VolumeCreate journalParam = new VolumeCreate();
        journalParam.setConsistencyGroup(consistencyGroup.getId());
        journalParam.setCount(param.getCount());
        journalParam.setName(journalVolumeName);
        journalParam.setProject(param.getProject());
        journalParam.setSize(journalSize);
        journalParam.setVarray(varray.getId());
        journalParam.setVpool(vpool.getId());

        List<URI> volumes = new ArrayList<URI>();
        descriptors.addAll(vplexBlockServiceApiImpl.createVPlexVolumeDescriptors(journalParam, project, varray, vpool,
                journalRecommendations, task, capabilities, taskList, volumes));
        Volume journalVolume = this.getVPlexVirtualVolume(volumes);
        journalVolume = prepareVPlexVirtualVolume(journalVolume, project, varray, vpool,
                journalSize, journalVolumeName,
                consistencyGroup, task, false,
                ((RPProtectionRecommendation) recommendation).getProtectionDevice(),
                Volume.PersonalityTypes.METADATA, null,
                internalSiteName,
                copyName, null, null, null);
        journalVolume.setPool(storagePool);

        return journalVolume;
    }

    /**
     * Modifies the passed in recommendation object to be re-used by VPlexBlockServiceApiImpl for
     * RP src, RP src-jrnl, RP tgt, and RP tgt-jrnl creation on VPlex.
     * 
     * @param recommendation
     * @param vplexId
     * @param varrayId
     * @param vpool
     * @param storageSystemId
     * @param storagePoolId
     * @return Modified recommendation object.
     */
    private VPlexProtectionRecommendation createTempRecommendation(VPlexProtectionRecommendation recommendation,
            URI vplexId,
            URI varrayId,
            VirtualPool vpool,
            URI storageSystemId,
            URI storagePoolId) {

        VPlexProtectionRecommendation newRecommendation = new VPlexProtectionRecommendation(recommendation);
        newRecommendation.setVPlexStorageSystem(vplexId);
        newRecommendation.setVirtualArray(varrayId);
        newRecommendation.setVirtualPool(vpool);
        newRecommendation.setSourceDevice(storageSystemId);
        newRecommendation.setSourcePool(storagePoolId);
        newRecommendation.setResourceCount(1);
        return newRecommendation;
    }

    /**
     * Prep work to call the orchestrator to create the volume descriptors
     * 
     * @param recommendation recommendation object from RPRecommendation
     * @param volumeURIs volumes already prepared
     * @param capabilities vpool capabilities
     * @return list of volume descriptors
     * @throws ControllerException
     */
    private List<VolumeDescriptor> createVolumeDescriptors(RPProtectionRecommendation recommendation, List<URI> volumeURIs,
            VirtualPoolCapabilityValuesWrapper capabilities) {

        List<Volume> preparedVolumes = _dbClient.queryObject(Volume.class, volumeURIs);
        List<VolumeDescriptor> descriptors = new ArrayList<VolumeDescriptor>();

        // Package up the Volume descriptors
        for (Volume volume : preparedVolumes) {
            VirtualPool vpool = _dbClient.queryObject(VirtualPool.class, volume.getVirtualPool());
            VolumeDescriptor.Type volumeType = VolumeDescriptor.Type.RP_SOURCE;
            if (VirtualPool.vPoolSpecifiesHighAvailability(vpool) || volume.getAssociatedVolumes() != null) {
                volumeType = VolumeDescriptor.Type.RP_VPLEX_VIRT_SOURCE;
            }

            VolumeDescriptor desc = null;
            // Vpool Change flow, mark the production volume as already existing, so it doesn't get created
            if (recommendation != null && (recommendation.getVpoolChangeVolume() != null) &&
                    (recommendation.getVpoolChangeVolume().equals(volume.getId()))) {
                if (recommendation.isVpoolChangeProtectionAlreadyExists()) {
                    volumeType = VolumeDescriptor.Type.RP_EXISTING_PROTECTED_SOURCE;
                } else {
                    volumeType = VolumeDescriptor.Type.RP_EXISTING_SOURCE;
                }
                desc = new VolumeDescriptor(volumeType, volume.getStorageController(), volume.getId(),
                        volume.getPool(), null, capabilities, volume.getCapacity());
                Map<String, Object> volumeParams = new HashMap<String, Object>();
                volumeParams.put(VolumeDescriptor.PARAM_VPOOL_CHANGE_VOLUME_ID, recommendation.getVpoolChangeVolume());
                volumeParams.put(VolumeDescriptor.PARAM_VPOOL_CHANGE_VPOOL_ID, recommendation.getVpoolChangeVpool());
                volumeParams.put(VolumeDescriptor.PARAM_VPOOL_OLD_VPOOL_ID, volume.getVirtualPool());

                desc.setParameters(volumeParams);
                descriptors.add(desc);
            } else {
                // Normal create-from-scratch flow
                if (volume.getPersonality() == null) {
                    throw APIException.badRequests.missingPersonalityAttribute(String.valueOf(volume.getId()));
                }

                if (volume.getPersonality().equals(Volume.PersonalityTypes.TARGET.toString())) {
                    if (VirtualPool.vPoolSpecifiesHighAvailability(vpool) || volume.getAssociatedVolumes() != null) {
                        volumeType = VolumeDescriptor.Type.RP_VPLEX_VIRT_TARGET;
                    } else {
                        volumeType = VolumeDescriptor.Type.RP_TARGET;
                    }
                } else if (volume.getPersonality().equals(Volume.PersonalityTypes.METADATA.toString())) {
                    if (VirtualPool.vPoolSpecifiesHighAvailability(vpool) || volume.getAssociatedVolumes() != null) {
                        volumeType = VolumeDescriptor.Type.RP_VPLEX_VIRT_JOURNAL;
                    } else {
                        volumeType = VolumeDescriptor.Type.RP_JOURNAL;
                    }
                }
                desc = new VolumeDescriptor(volumeType, volume.getStorageController(), volume.getId(), volume.getPool(),
                        null, capabilities, volume.getCapacity());
                descriptors.add(desc);
            }
            _log.info(String.format("%n%nAdding volume descriptor %n\t [%s] - [%s] %n\t type [%s]%n", desc.toString(), volume.getLabel(),
                    volume.getPersonality()));
        }
        return descriptors;
    }

    /**
     * Prepares the pre-created VPlex Virtual Volume being passed in to be used with Recoverpoint.
     * 
     * @param volume
     * @param project
     * @param varray
     * @param vpool
     * @param size
     * @param label
     * @param vplexConsistencyGroup
     * @param token
     * @param remote
     * @param protectionStorageSystem
     * @param personality
     * @param rsetName
     * @param internalSiteName
     * @param rpCopyName
     * @param srcVolumeId
     * @param secondaryRecommendation
     * @param primaryRecommendation
     * @return Volume that is ready to be used for Recoverpoint protection.
     */
    private Volume prepareVPlexVirtualVolume(Volume volume, Project project, VirtualArray varray,
            VirtualPool vpool, String size, String label,
            BlockConsistencyGroup consistencyGroup,
            String token, boolean remote, URI protectionStorageSystem,
            Volume.PersonalityTypes personality, String rsetName,
            String internalSiteName, String rpCopyName, URI srcVolumeId,
            VPlexProtectionRecommendation primaryRecommendation,
            VPlexProtectionRecommendation secondaryRecommendation) {

        volume.setRpCopyName(rpCopyName);
        volume.setPersonality(personality.toString());

        // Set all Journal Volumes to have the INTERNAL_OBJECT flag.
        if (personality.equals(Volume.PersonalityTypes.METADATA)) {
            volume.addInternalFlags(Flag.INTERNAL_OBJECT);
            volume.addInternalFlags(Flag.SUPPORTS_FORCE);
            volume.setAccessState(Volume.VolumeAccessState.NOT_READY.name());
        } else if (personality.equals(Volume.PersonalityTypes.SOURCE)) {
            volume.setAccessState(Volume.VolumeAccessState.READWRITE.name());
        } else if (personality.equals(Volume.PersonalityTypes.TARGET)) {
            volume.setAccessState(Volume.VolumeAccessState.NOT_READY.name());
        }

        volume.setProtectionController(protectionStorageSystem);
        volume.setRSetName(rsetName);
        volume.setInternalSiteName(internalSiteName);

        if (consistencyGroup != null) {
            volume.setConsistencyGroup(consistencyGroup.getId());
        }

        _dbClient.persistObject(volume);

        // Keep track of target volumes associated with the source volume
        if (srcVolumeId != null) {
            Volume srcVolume = _dbClient.queryObject(Volume.class, srcVolumeId);
            if (srcVolume.getRpTargets() == null) {
                srcVolume.setRpTargets(new StringSet());
            }
            srcVolume.getRpTargets().add(volume.getId().toString());
            _dbClient.persistObject(srcVolume);
        }

        return volume;
    }

    @Override
    public TaskList createVolumes(VolumeCreate param, Project project, VirtualArray srcVarray,
            VirtualPool srcVpool, List<Recommendation> recommendations, TaskList taskList,
            String task, VirtualPoolCapabilityValuesWrapper capabilities) throws InternalException {

        if (taskList == null) {
            taskList = new TaskList();
        }
        
        // Prepare the Bourne Volumes to be created and associated
        // with the actual storage system volumes created. Also create
        // a BlockTaskList containing the list of task resources to be
        // returned for the purpose of monitoring the volume creation
        // operation for each volume to be created.
        String volumeLabel = param.getName();

        // List to store the volume descriptors for the Block Orchestration
        List<VolumeDescriptor> volumeDescriptors = new ArrayList<VolumeDescriptor>();

        // Store capabilities of the CG, so they make it down to the controller
        if (srcVpool.getRpCopyMode() != null) {
            capabilities.put(VirtualPoolCapabilityValuesWrapper.RP_COPY_MODE, srcVpool.getRpCopyMode());
        }
        if (srcVpool.getRpRpoType() != null
                && NullColumnValueGetter.isNotNullValue(srcVpool.getRpRpoType())) {
            capabilities.put(VirtualPoolCapabilityValuesWrapper.RP_RPO_TYPE, srcVpool.getRpRpoType());
        }
        if (srcVpool.checkRpRpoValueSet()) {
            capabilities.put(VirtualPoolCapabilityValuesWrapper.RP_RPO_VALUE, srcVpool.getRpRpoValue());
        }

        // Prepare the volumes
        List<URI> volumeURIs = prepareVPlexRecommendedVolumes(param, task, taskList, project,
                srcVarray, srcVpool,
                capabilities.getResourceCount(),
                recommendations, volumeLabel, capabilities,
                volumeDescriptors);

        // Execute the volume creations requests for each recommendation.
        Iterator<Recommendation> recommendationsIter = recommendations.iterator();
        while (recommendationsIter.hasNext()) {
            Recommendation recommendation = recommendationsIter.next();
            try {
                volumeDescriptors.addAll(createVolumeDescriptors((RPProtectionRecommendation) recommendation, volumeURIs, capabilities));
                BlockOrchestrationController controller = getController(BlockOrchestrationController.class,
                        BlockOrchestrationController.BLOCK_ORCHESTRATION_DEVICE);

                // Check to see if this is a regular RP+VPLEX volume create or a RP+VPLEX change virtual pool
                URI changeVirtualPoolVolumeURI = VolumeDescriptor.getVirtualPoolChangeVolume(volumeDescriptors);
                boolean isChangeVpool = (changeVirtualPoolVolumeURI != null);

                // TODO might be able to use param.getSize() instead of the below code to find requestedVolumeCapactity
                Long requestedVolumeCapactity = 0L;
                for (URI volumeURI : volumeURIs) {
                    Volume volume = _dbClient.queryObject(Volume.class, volumeURI);
                    if (Volume.PersonalityTypes.SOURCE.name().equalsIgnoreCase(volume.getPersonality())) {
                        requestedVolumeCapactity = volume.getCapacity();
                        break;
                    }
                }

                rpBlockServiceApiImpl.computeProtectionCapacity(volumeURIs, requestedVolumeCapactity, false, isChangeVpool, null);

                if (isChangeVpool) {
                    _log.info("RP+VPLEX Change Virtual Volume...");
                    controller.changeVirtualPool(volumeDescriptors, task);
                } else {
                    controller.createVolumes(volumeDescriptors, task);
                }
            } catch (InternalException e) {
                if (_log.isErrorEnabled()) {
                    _log.error("Controller error", e);
                }

                String errorMsg = String.format("Controller error: %s", e.getMessage());
                if (volumeURIs != null) {
                    for (URI volumeURI : volumeURIs) {
                        Volume volume = _dbClient.queryObject(Volume.class, volumeURI);
                        if (volume != null) {
                            TaskResourceRep volumeTask = toTask(volume, task);
                            volumeTask.setState(Operation.Status.error.name());
                            volumeTask.setMessage(errorMsg);
                            Operation statusUpdate = new Operation(Operation.Status.error.name(), errorMsg);
                            _dbClient.updateTaskOpStatus(Volume.class, volumeTask.getResource()
                                    .getId(), task, statusUpdate);
                            if (Volume.PersonalityTypes.SOURCE.name().equalsIgnoreCase(volume.getPersonality())) {
                                taskList.getTaskList().add(volumeTask);
                            }
                        }
                    }
                    throw e;
                }

                // If there was a controller error creating the volumes,
                // throw an internal server error and include the task
                // information in the response body, which will inform
                // the user what succeeded and what failed.
                throw new WebApplicationException(Response
                        .status(Response.Status.INTERNAL_SERVER_ERROR).entity(taskList).build());
            }
            // TODO Remove
            break;
        }

        return taskList;
    }

    private Volume getVPlexVirtualVolume(List<URI> volumes) {
        Volume vplexVirtualVolume = null;
        for (URI volumeURI : volumes) {
            Volume vplexVolume = _dbClient.queryObject(Volume.class, volumeURI);

            if (vplexVolume.getAssociatedVolumes() != null
                    && !vplexVolume.getAssociatedVolumes().isEmpty()) {
                vplexVirtualVolume = vplexVolume;
                break;
            }
        }

        return vplexVirtualVolume;
    }

    @Override
    protected List<VolumeDescriptor> getDescriptorsForVolumesToBeDeleted(URI systemURI, List<URI> volumeURIs) {

        List<VolumeDescriptor> volumeDescriptors = _rpHelper.getDescriptorsForVolumesToBeDeleted(systemURI, volumeURIs);

        List<VolumeDescriptor> filteredDescriptors = VolumeDescriptor.filterByType(volumeDescriptors,
                new VolumeDescriptor.Type[] { VolumeDescriptor.Type.BLOCK_DATA,
                        VolumeDescriptor.Type.VPLEX_VIRT_VOLUME },
                new VolumeDescriptor.Type[] {});

        for (VolumeDescriptor descriptor : filteredDescriptors) {
            URI volumeURI = descriptor.getDeviceURI();
            Volume volume = _dbClient.queryObject(Volume.class, volumeURI);
            if (volume != null && !volume.getInactive()) {
                addDescriptorsForMirrors(volumeDescriptors, volume);
            }
        }
        return volumeDescriptors;
    }

    /**
     * {@inheritDoc}
     * 
     * @throws InternalException
     */
    @Override
    public void deleteVolumes(URI systemURI, List<URI> volumeURIs, final String deletionType, String task)
            throws InternalException {
        _log.info("Request to delete {} VPLEX volume(s) with RP Protection", volumeURIs.size());
        try {
            super.deleteVolumes(systemURI, volumeURIs, deletionType, task);
        } catch (Exception e) {
            throw RecoverPointException.exceptions.deletingRPVolume(e);
        }
    }

    /**
     * {@inheritDoc}
     * 
     * @throws InternalException
     */
    @Override
    public <T extends DataObject> String checkForDelete(T object) throws InternalException {
        return rpBlockServiceApiImpl.checkForDelete(object);
    }

    @Override
    public TaskResourceRep deactivateMirror(StorageSystem device, URI mirror, String task) {
        throw APIException.methodNotAllowed.notSupported();
    }

    @Override
    public StorageSystemConnectivityList getStorageSystemConnectivity(StorageSystem system) {
        // Connectivity list to return
        StorageSystemConnectivityList connectivityList = new StorageSystemConnectivityList();
        // Set used to ensure unique values are added to the connectivity list
        Set<String> existing = new HashSet<String>();

        // Get all the RPSiteArrays that contain this Storage System
        List<RPSiteArray> rpSiteArrays = CustomQueryUtility
                .queryActiveResourcesByConstraint(_dbClient, RPSiteArray.class,
                        AlternateIdConstraint.Factory.getConstraint(RPSiteArray.class,
                                "storageSystem",
                                system.getId().toString()));

        // For each of the RPSiteArrays get the Protection System
        for (RPSiteArray rpSiteArray : rpSiteArrays) {
            if ((rpSiteArray.getRpProtectionSystem() != null)) {
                ProtectionSystem protectionSystem = _dbClient.queryObject(ProtectionSystem.class, rpSiteArray.getRpProtectionSystem());
                // Loop through the associated Storage Systems for this Protection System to build the
                // connectivity response list. Only store unique responses.
                for (String associatedStorageSystemStr : protectionSystem.getAssociatedStorageSystems()) {
                    URI associatedStorageSystemURI = ConnectivityUtil.findStorageSystemBySerialNumber(
                            ProtectionSystem.getAssociatedStorageSystemSerialNumber(associatedStorageSystemStr), _dbClient,
                            StorageSystemType.BLOCK);
                    StorageSystem associatedStorageSystem = _dbClient.queryObject(StorageSystem.class, associatedStorageSystemURI);

                    if (associatedStorageSystem == null || associatedStorageSystem.getInactive()
                            || ConnectivityUtil.isAVPlex(associatedStorageSystem)) {
                        continue;
                    }

                    StorageSystemConnectivityRestRep connection = new StorageSystemConnectivityRestRep();
                    connection.getConnectionTypes().add(ProtectionSystem._RP);
                    connection.setProtectionSystem(toNamedRelatedResource(ResourceTypeEnum.PROTECTION_SYSTEM,
                            rpSiteArray.getRpProtectionSystem(),
                            protectionSystem.getLabel()));
                    connection.setStorageSystem(toNamedRelatedResource(ResourceTypeEnum.STORAGE_SYSTEM,
                            associatedStorageSystem.getId(),
                            associatedStorageSystem.getSerialNumber()));

                    // The key is a transient unique ID, since none of the actual fields guarantee uniqueness.
                    // We use this to make sure we don't add the same storage system more than once for the same
                    // protection system and connection type.
                    String key = connection.getProtectionSystem().toString() + connection.getConnectionTypes()
                            + connection.getStorageSystem().toString();
                    if (!existing.contains(key)) {
                        existing.add(key);
                        connectivityList.getConnections().add(connection);
                    }
                }
            }
        }

        return connectivityList;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void verifyVarrayChangeSupportedForVolumeAndVarray(Volume volume,
            VirtualArray newVarray) throws APIException {
        throw APIException.badRequests.changesNotSupportedFor("VirtualArray",
                "RP protected volumes");
    }

    @Override
    protected Set<URI> getConnectedVarrays(URI varrayUID) {

        Set<URI> varrays = new HashSet<URI>();

        List<ProtectionSystem> protectionSystems = CustomQueryUtility
                .queryActiveResourcesByConstraint(_dbClient, ProtectionSystem.class,
                        AlternateIdConstraint.Factory.getConstraint(ProtectionSystem.class,
                                VIRTUAL_ARRAYS_CONSTRAINT,
                                varrayUID.toString()));

        // Create and return the result.
        for (ProtectionSystem protectionSystem : protectionSystems) {
            if (protectionSystem.getVirtualArrays() != null) {
                for (String varrayURIStr : protectionSystem.getVirtualArrays()) {
                    varrays.add(URI.create(varrayURIStr));
                }
            }
        }

        return varrays;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected void cleanupForViPROnlyDelete(List<VolumeDescriptor> volumeDescriptors) {
        // For a VIPR only deletion make sure to clean up the export
        // groups and mask so that they no longer reference associated
        // volumes.
        List<VolumeDescriptor> sourceVolumeDescriptors = VolumeDescriptor
                .getDescriptors(volumeDescriptors, VolumeDescriptor.Type.RP_SOURCE);
        List<URI> sourceVolumeURIs = new ArrayList<URI>();
        for (URI sourceVolumeURI : VolumeDescriptor.getVolumeURIs(sourceVolumeDescriptors)) {
            Volume sourceVolume = _dbClient.queryObject(Volume.class, sourceVolumeURI);
            if (sourceVolume != null && !sourceVolume.getInactive() &&
                    !sourceVolume.isIngestedVolume(_dbClient)) { // Keeping this in here for when we do RP ingest.
                sourceVolumeURIs.add(sourceVolumeURI);
            }
            for (URI assocVolumeURI : _rpHelper.getVolumesToDelete(sourceVolumeURIs)) {
                cleanVolumeFromExports(assocVolumeURI, true);
            }
        }

        // For a VIPR only deletion make sure to clean up the export
        // groups and mask so that they no longer reference associated
        // volumes.
        List<VolumeDescriptor> assocVolumeDescriptors = VolumeDescriptor
                .getDescriptors(volumeDescriptors, VolumeDescriptor.Type.BLOCK_DATA);
        List<URI> assocVolumeURIs = VolumeDescriptor.getVolumeURIs(assocVolumeDescriptors);
        for (URI assocVolumeURI : assocVolumeURIs) {
            cleanVolumeFromExports(assocVolumeURI, true);
        }
    }

    /**
     * {@inheritDoc}
     * 
     * @throws InternalException
     */
    @Override
    public void changeVolumeVirtualPool(URI systemURI, Volume volume, VirtualPool newVpool,
            VirtualPoolChangeParam vpoolChangeParam, String taskId) throws InternalException {
        _log.info("Volume {} VirtualPool change.", volume.getId());

        ArrayList<Volume> volumes = new ArrayList<Volume>();
        volumes.add(volume);

        // Check for common Vpool updates handled by generic code. It returns true if handled.
        if (checkCommonVpoolUpdates(volumes, newVpool, taskId)) {
            return;
        }

        // Get the storage system. This vmax, or vnxblock storage system.
        StorageSystem storageSystem = _dbClient.queryObject(StorageSystem.class, systemURI);
        String systemType = storageSystem.getSystemType();
        if ((DiscoveredDataObject.Type.vplex.name().equals(systemType))) {
            // Get the current vpool
            VirtualPool currentVpool = _dbClient.queryObject(VirtualPool.class, volume.getVirtualPool());

            // Now that we have a handle on the current vpool, let's set the new vpool on the volume.
            // The volume will not be persisted just yet but we need to have the new vpool to
            // properly make placement decisions and to add reference to the new vpool to the
            // recommendation objects that will be created.
            volume.setVirtualPool(newVpool.getId());

            if (volume.checkForRp()
                    && !VirtualPool.vPoolSpecifiesMetroPoint(currentVpool)
                    && VirtualPool.vPoolSpecifiesRPVPlex(currentVpool)
                    && VirtualPool.vPoolSpecifiesMetroPoint(newVpool)) {
                upgradeToMetroPointVolume(volume, newVpool, vpoolChangeParam, taskId);
            } else {
                _log.info("Protection VirtualPool change for VPLEX to RP+VPLEX volume.");
                upgradeToProtectedVolume(volume, newVpool, vpoolChangeParam, taskId);
            }
        }
    }

    @Override
    public void changeVolumeVirtualPool(List<Volume> volumes, VirtualPool newVpool,
            VirtualPoolChangeParam vpoolChangeParam, String taskId) throws InternalException {
        // For now we only support changing the virtual pool for a single volume at a time
        // until CTRL-1347 and CTRL-5609 are fixed.
        if (volumes.size() == 1) {
            changeVolumeVirtualPool(volumes.get(0).getStorageController(), volumes.get(0), newVpool, vpoolChangeParam, taskId);
        } else {
            throw APIException.methodNotAllowed
                    .notSupportedWithReason("Multiple volume change virtual pool is currently not supported for RecoverPoint. Please select one volume at a time.");
        }
    }

    /**
     * Upgrade a local block volume to a protected RP volume
     * 
     * @param volume the existing volume being protected.
     * @param newVpool the requested virtual pool
     * @param taskId the task identifier
     * @throws InternalException
     */
    private void upgradeToProtectedVolume(Volume volume, VirtualPool newVpool,
            VirtualPoolChangeParam vpoolChangeParam, String taskId) throws InternalException {

        Project project = _dbClient.queryObject(Project.class, volume.getProject());

        if (VirtualPool.vPoolSpecifiesProtection(newVpool)) {

            // The volume can not already be in a CG for RP+VPLEX. This may change in the future, but
            // for now we can not allow it. Inform the user that they will first need to remove
            // the volume from the existing CG before they can proceed.
            if (!NullColumnValueGetter.isNullURI(volume.getConsistencyGroup())) {
                BlockConsistencyGroup cg = _dbClient.queryObject(BlockConsistencyGroup.class, volume.getConsistencyGroup());
                throw APIException.badRequests.cannotCreateRPVolumesInCG(volume.getLabel(), cg.getLabel());
            }

            // CTRL-2792: This is a workaround for the UI not providing a field for
            // consistency group. We are basing the CG name off the volume
            // name until this support is added. In the future, the cg should
            // never be null.
            if (vpoolChangeParam.getConsistencyGroup() == null) {
                // create a consistency group corresponding to volume name
                BlockConsistencyGroup cg = new BlockConsistencyGroup();
                String modifiedCGName = volume.getLabel().replaceAll("\\s+", "").replaceAll("[^A-Za-z0-9]", "");
                // Make sure the name doesn't start with a number
                if (modifiedCGName.substring(0, 1).matches("[0-9]")) {
                    modifiedCGName = "cg_" + modifiedCGName;
                }
                cg.setProject(new NamedURI(project.getId(), modifiedCGName));
                cg.setTenant(new NamedURI(project.getTenantOrg().getURI(), modifiedCGName));
                cg.setId(URIUtil.createId(BlockConsistencyGroup.class));
                cg.setLabel(modifiedCGName);

                _dbClient.createObject(cg);

                vpoolChangeParam.setConsistencyGroup(cg.getId());
            }
        }

        List<Recommendation> recommendations =
                getRecommendationsForVirtualPoolChangeRequest(volume, newVpool, vpoolChangeParam);

        if (recommendations.isEmpty()) {
            throw APIException.badRequests.noStorageFoundForVolume();
        }

        // Get the volume's varray
        VirtualArray varray = _dbClient.queryObject(
                VirtualArray.class, volume.getVirtualArray());

        // Generate a VolumeCreate object that contains the information that createVolumes likes to consume.
        VolumeCreate param = new VolumeCreate(
                volume.getLabel(), String.valueOf(volume.getCapacity()), 1, newVpool.getId(),
                volume.getVirtualArray(), volume.getProject().getURI());

        VirtualPoolCapabilityValuesWrapper capabilities = new VirtualPoolCapabilityValuesWrapper();
        capabilities.put(VirtualPoolCapabilityValuesWrapper.RESOURCE_COUNT, new Integer(1));
        capabilities.put(VirtualPoolCapabilityValuesWrapper.BLOCK_CONSISTENCY_GROUP, vpoolChangeParam.getConsistencyGroup());
        // WJEIV -- Check to see if upgrading this volume requires any pre-creation.
        createVolumes(param, project, varray, newVpool, recommendations, null, taskId, capabilities);
    }

    /**
     * Recommendations for change vpool
     * 
     * @param volume Volume to be moved
     * @param newVpool The new vpool
     * @param vpoolChangeParam The change vpool param
     * @return List of recommendations for change vpool
     */
    private List<Recommendation> getRecommendationsForVirtualPoolChangeRequest(Volume volume, VirtualPool newVpool,
            VirtualPoolChangeParam vpoolChangeParam) {
        Project project = _dbClient.queryObject(Project.class, volume.getProject());

        List<Recommendation> recommendations = null;
        if (volume.checkForRp()) {
            recommendations = getBlockScheduler().scheduleStorageForVpoolChangeProtected(volume, newVpool,
                    RecoverPointScheduler.getProtectionVirtualArraysForVirtualPool(project, newVpool,
                            _dbClient, super.getPermissionsHelper()),
                    vpoolChangeParam);

        } else {
            recommendations = getBlockScheduler().scheduleStorageForVpoolChangeUnprotected(volume, newVpool,
                    RecoverPointScheduler.getProtectionVirtualArraysForVirtualPool(project, newVpool,
                            _dbClient, super.getPermissionsHelper()),
                    vpoolChangeParam);
        }

        // Protection volume placement is requested.
        return recommendations;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void verifyVolumeExpansionRequest(Volume volume, long newSize) {
        _log.debug("Verify if RP volume {} can be expanded", volume.getId());

        // Look at all source and target volumes and make sure they can all be expanded
        vplexBlockServiceApiImpl.verifyVolumeExpansionRequest(volume, newSize);
        if (volume.getRpTargets() != null) {
            for (String volumeID : volume.getRpTargets()) {
                Volume targetVolume = _dbClient.queryObject(Volume.class, URI.create(volumeID));
                if (targetVolume.getAssociatedVolumes() != null && !targetVolume.getAssociatedVolumes().isEmpty()) {
                    vplexBlockServiceApiImpl.verifyVolumeExpansionRequest(_dbClient.queryObject(Volume.class, URI.create(volumeID)),
                            newSize);
                }
            }
        } else {
            throw APIException.badRequests.notValidRPSourceVolume(volume.getLabel());
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void expandVolume(Volume volume, long newSize, String taskId)
            throws InternalException {
        Long originalVolumeSize = volume.getCapacity();
        List<URI> replicationSetVolumes = _rpHelper.getReplicationSetVolumes(volume);
        Map<URI, StorageSystem> volumeStorageSystems = new HashMap<URI, StorageSystem>();
        // Step1 : Determine if either the backing volume of the VPLEX RP source or target is on VMAX. If yes, then set the requested volume
        // size to the size as determined by if the VMAX volume is meta volume or not.
        // In the case of meta volumes, the actual provisioned volume size/capacity will be different than the requested size. If either the
        // VPLEX+RP source/target is on VMAX/VNX and the target/source is
        // on VNX/VMAX, then the size of the VNX volume must match the size of the VMAX volume after expand is done.
        // TODO: Move this segment of the code that computes if one of the volumes is VMAX and determines the potential provisioned capacity
        // into
        // the computeProtectionAllocation Capacity.
        for (URI rpVolumeURI : replicationSetVolumes) {
            Volume rpVolume = _dbClient.queryObject(Volume.class, rpVolumeURI);
            Volume vmaxVolume = null;
            StorageSystem vmaxStorageSystem = null;
            if (rpVolume.getAssociatedVolumes() != null
                    && !rpVolume.getAssociatedVolumes().isEmpty()) {
                // Check backend volumes for VPLEX
                for (String backingVolumeStr : rpVolume.getAssociatedVolumes()) {
                    Volume backingVolume = _dbClient.queryObject(Volume.class, URI.create(backingVolumeStr));
                    StorageSystem storageSystem = _dbClient.queryObject(StorageSystem.class, backingVolume.getStorageController());
                    if (storageSystem.getSystemType().equalsIgnoreCase(DiscoveredDataObject.Type.vmax.toString())) {
                        vmaxVolume = backingVolume;
                        vmaxStorageSystem = storageSystem;
                        break;
                    }
                }
            } else {
                StorageSystem storageSystem = _dbClient.queryObject(StorageSystem.class, rpVolume.getStorageController());
                if (storageSystem.getSystemType().equalsIgnoreCase(DiscoveredDataObject.Type.vmax.toString())) {
                    vmaxVolume = rpVolume;
                    vmaxStorageSystem = storageSystem;
                }
            }

            if (vmaxVolume != null && vmaxStorageSystem != null) {
                // Set the requested size to what the VMAX Meta utils determines will possibly be the provisioned capacity.
                // All the other volumes, will need to be the same size as well or else RP will have a fit.
                newSize = _rpHelper.computeVmaxVolumeProvisionedCapacity(newSize, vmaxVolume, vmaxStorageSystem);
                _log.info(String.format("VMAX volume detected, expand size re-calculated to [%d]", newSize));
                // No need to continue, newSize has been calculated.
                break;
            }
        }

        try {
            List<Volume> allVolumesToUpdateCapacity = new ArrayList<Volume>();
            Map<URI, String> associatedVolumePersonalityMap = new HashMap<URI, String>();
            for (URI rpVolumeURI : replicationSetVolumes) {
                Volume rpVolume = _dbClient.queryObject(Volume.class, rpVolumeURI);
                if (rpVolume.getAssociatedVolumes() != null
                        && !rpVolume.getAssociatedVolumes().isEmpty()) {
                    // Check backend volumes for VPLEX
                    for (String backingVolumeStr : rpVolume.getAssociatedVolumes()) {
                        Volume backingVolume = _dbClient.queryObject(Volume.class, URI.create(backingVolumeStr));
                        allVolumesToUpdateCapacity.add(backingVolume);
                        associatedVolumePersonalityMap.put(backingVolume.getId(), rpVolume.getPersonality());
                        rpBlockServiceApiImpl.addVolumeStorageSystem(volumeStorageSystems, backingVolume);
                    }
                } else {
                    allVolumesToUpdateCapacity.add(rpVolume);
                    rpBlockServiceApiImpl.addVolumeStorageSystem(volumeStorageSystems, rpVolume);
                }
            }

            if (!rpBlockServiceApiImpl.capacitiesCanMatch(volumeStorageSystems)) {
                Map<Volume.PersonalityTypes, Long> capacities = rpBlockServiceApiImpl.setUnMatchedCapacities(allVolumesToUpdateCapacity,
                        associatedVolumePersonalityMap, true, newSize);
                _log.info("Capacities for source and target of the Volume Expand request cannot match due to the differences in array types");
                _log.info("Expand Volume requested size : {}", newSize);
                _log.info("Expand source calculated size : {}", capacities.get(Volume.PersonalityTypes.SOURCE));
                _log.info("Expand target calcaluted size : {}", capacities.get(Volume.PersonalityTypes.TARGET));
                List<VolumeDescriptor> volumeDescriptors = createVolumeDescriptors(null, replicationSetVolumes, null);

                for (VolumeDescriptor volDesc : volumeDescriptors) {
                    if (volDesc.getType() == VolumeDescriptor.Type.RP_VPLEX_VIRT_SOURCE) {
                        volDesc.setVolumeSize(capacities.get(Volume.PersonalityTypes.SOURCE));
                    } else if ((volDesc.getType() == VolumeDescriptor.Type.RP_TARGET) ||
                            (volDesc.getType() == VolumeDescriptor.Type.RP_VPLEX_VIRT_TARGET)) {
                        volDesc.setVolumeSize(capacities.get(Volume.PersonalityTypes.TARGET));
                    }
                }

                BlockOrchestrationController controller = getController(BlockOrchestrationController.class,
                        BlockOrchestrationController.BLOCK_ORCHESTRATION_DEVICE);
                controller.expandVolume(volumeDescriptors, taskId);

            } else {
                // Step 2: Just because we have a RP source/target on VMAX and have a size calculated doesn't mean VNX can honor it.
                // The trick is that the size of the volume must be a multiple of 512 for VNX and 520 for VMAX because of the different
                // block sizes.
                // We will find a number (in bytes) that is greater than the requested size and meets the above criteria and use that our
                // final expanded volume size.
                long normalizedRequestSize = rpBlockServiceApiImpl.computeProtectionCapacity(replicationSetVolumes, newSize, true, false,
                        null);

                // Step 3: Call the controller to do the expand.
                _log.info("Expand volume request size : {}", normalizedRequestSize);
                List<VolumeDescriptor> volumeDescriptors = createVolumeDescriptors(null, replicationSetVolumes, null);

                for (VolumeDescriptor volDesc : volumeDescriptors) {
                    volDesc.setVolumeSize(normalizedRequestSize);
                }

                BlockOrchestrationController controller = getController(BlockOrchestrationController.class,
                        BlockOrchestrationController.BLOCK_ORCHESTRATION_DEVICE);
                controller.expandVolume(volumeDescriptors, taskId);
            }
        } catch (ControllerException e) {
            // Set the volume size back to original size before the expand request
            for (URI volumeURI : replicationSetVolumes) {
                Volume rpVolume = _dbClient.queryObject(Volume.class, volumeURI);
                rpVolume.setCapacity(originalVolumeSize);
                _dbClient.persistObject(rpVolume);

                // Reset the backing volumes as well, if they exist
                if (rpVolume.getAssociatedVolumes() != null
                        && !rpVolume.getAssociatedVolumes().isEmpty()) {
                    // Check backend volumes for VPLEX
                    for (String backingVolumeStr : rpVolume.getAssociatedVolumes()) {
                        Volume backingVolume = _dbClient.queryObject(Volume.class, URI.create(backingVolumeStr));
                        backingVolume.setCapacity(originalVolumeSize);
                        _dbClient.persistObject(backingVolume);
                    }
                }
            }
            throw APIException.badRequests.volumeNotExpandable(volume.getLabel());
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void validateCreateSnapshot(Volume reqVolume, List<Volume> volumesToSnap,
            String snapshotType, String snapshotName, BlockFullCopyManager fcManager) {
        rpBlockServiceApiImpl.validateCreateSnapshot(reqVolume, volumesToSnap, snapshotType, snapshotName, fcManager);
    }

    /**
     * Prepares the snapshots for a snapshot request.
     * 
     * @param volumes The volumes for which snapshots are to be created.
     * @param snapShotType The snapshot technology type.
     * @param snapshotName The snapshot name.
     * @param snapshotURIs [OUT] The URIs for the prepared snapshots.
     * @param taskId The unique task identifier
     * 
     * @return The list of snapshots
     */
    @Override
    public List<BlockSnapshot> prepareSnapshots(List<Volume> volumes, String snapshotType,
            String snapshotName, List<URI> snapshotURIs, String taskId) {
        return rpBlockServiceApiImpl.prepareSnapshots(volumes, snapshotType, snapshotName, snapshotURIs, taskId);
    }

    /**
     * Uses the appropriate controller to delete the snapshot.
     * 
     * @param snapshot The snapshot to delete
     * @param taskId The unique task identifier
     */
    @Override
    public void deleteSnapshot(BlockSnapshot snapshot, String taskId) {
        rpBlockServiceApiImpl.deleteSnapshot(snapshot, taskId);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public List<Volume> getVolumesToSnap(Volume reqVolume, String snapshotType) {
        return rpBlockServiceApiImpl.getVolumesToSnap(reqVolume, snapshotType);
    }

    /**
     * Uses the appropriate controller to create the snapshots.
     * 
     * @param reqVolume The volume from the snapshot request.
     * @param snapshotURIs The URIs of the prepared snapshots.
     * @param snapshotType The snapshot technology type.
     * @param createInactive true if the snapshots should be created but not
     *            activated, false otherwise.
     * @param taskId The unique task identifier.
     */
    @Override
    public void createSnapshot(Volume reqVolume, List<URI> snapshotURIs,
            String snapshotType, Boolean createInactive, String taskId) {
        rpBlockServiceApiImpl.createSnapshot(reqVolume, snapshotURIs, snapshotType, createInactive, taskId);
    }

    /**
     * Validates a restore snapshot request.
     * 
     * @param snapshot The snapshot to restore.
     * @param parent The parent of the snapshot
     */
    public void validateRestoreSnapshot(BlockSnapshot snapshot, Volume parent) {
        rpBlockServiceApiImpl.validateRestoreSnapshot(snapshot, parent);
    }

    /**
     * Restore the passed parent volume from the passed snapshot of that parent volume.
     * 
     * @param snapshot The snapshot to restore
     * @param parentVolume The volume to be restored.
     * @param taskId The unique task identifier.
     */
    @Override
    public void restoreSnapshot(BlockSnapshot snapshot, Volume parentVolume, String taskId) {
        rpBlockServiceApiImpl.restoreSnapshot(snapshot, parentVolume, taskId);
    }

    /**
     * Get the snapshots for the passed volume.
     * 
     * @param volume A reference to a volume.
     * 
     * @return The snapshots for the passed volume.
     */
    @Override
    public List<BlockSnapshot> getSnapshots(Volume volume) {
        return rpBlockServiceApiImpl.getSnapshots(volume);
    }

    @Override
    protected List<VirtualPoolChangeOperationEnum> getVirtualPoolChangeAllowedOperations(Volume volume, VirtualPool currentVpool,
            VirtualPool newVpool, StringBuffer notSuppReasonBuff) {

        List<VirtualPoolChangeOperationEnum> allowedOperations = new ArrayList<VirtualPoolChangeOperationEnum>();

        // Get the varray for the volume.
        URI volumeVarrayURI = volume.getVirtualArray();
        StringSet newVirtualPoolVarrays = newVpool.getVirtualArrays();
        if ((newVirtualPoolVarrays != null) && (!newVirtualPoolVarrays.isEmpty())
                && (!newVirtualPoolVarrays.contains(volumeVarrayURI.toString()))) {
            // The VirtualPool is not allowed because it is not available in the
            // volume varray.
            notSuppReasonBuff.append("The VirtualPool is not available to the volume's varray");
        } else if (VirtualPool.vPoolSpecifiesRPVPlex(currentVpool)
                && VirtualPool.vPoolSpecifiesRPVPlex(newVpool)
                && VirtualPoolChangeAnalyzer.isSupportedRPChangeProtectionVirtualPoolChange(volume, currentVpool, newVpool,
                        _dbClient, notSuppReasonBuff)) {
            // Allow the RP change protection operation
            allowedOperations.add(VirtualPoolChangeOperationEnum.RP_PROTECTED_CHANGE);
        } else if (VirtualPool.vPoolSpecifiesRPVPlex(newVpool)
                && VirtualPoolChangeAnalyzer.isSupportedRPVPlexVolumeVirtualPoolChange(volume, currentVpool, newVpool,
                        _dbClient, notSuppReasonBuff)) {
            // Allow the RP Protection add operation
            allowedOperations.add(VirtualPoolChangeOperationEnum.RP_PROTECTED);
        }

        return allowedOperations;
    }

    /**
     * {@inheritDoc}
     */
    public void validateConsistencyGroupName(BlockConsistencyGroup consistencyGroup) {
        rpBlockServiceApiImpl.validateConsistencyGroupName(consistencyGroup);
        vplexBlockServiceApiImpl.validateConsistencyGroupName(consistencyGroup);
    }

    /**
     * Upgrade a local block volume to a protected RP volume
     * 
     * @param volume the existing volume being protected.
     * @param newVpool the requested virtual pool
     * @param taskId the task identifier
     * @throws InternalException
     */
    private void upgradeToMetroPointVolume(Volume volume, VirtualPool newVpool,
            VirtualPoolChangeParam vpoolChangeParam, String taskId) throws InternalException {
        _log.info(String.format("Upgrade [%s] to MetroPoint", volume.getLabel()));

        Project project = _dbClient.queryObject(Project.class, volume.getProject());

        List<Recommendation> recommendations =
                getRecommendationsForVirtualPoolChangeRequest(volume, newVpool, vpoolChangeParam);

        if (recommendations.isEmpty()) {
            throw APIException.badRequests.noStorageFoundForVolume();
        }

        // Get the volume's varray
        VirtualArray varray = _dbClient.queryObject(
                VirtualArray.class, volume.getVirtualArray());

        // Generate a VolumeCreate object that contains the information that createVolumes likes to consume.
        VolumeCreate param = new VolumeCreate(
                volume.getLabel(), String.valueOf(volume.getCapacity()), 1, newVpool.getId(),
                volume.getVirtualArray(), volume.getProject().getURI());

        VirtualPoolCapabilityValuesWrapper capabilities = new VirtualPoolCapabilityValuesWrapper();
        capabilities.put(VirtualPoolCapabilityValuesWrapper.RESOURCE_COUNT, new Integer(1));
        capabilities.put(VirtualPoolCapabilityValuesWrapper.BLOCK_CONSISTENCY_GROUP, volume.getConsistencyGroup());
        // Check to see if upgrading this volume requires precreation.
        createVolumes(param, project, varray, newVpool, recommendations, null, taskId, capabilities);
    }

    /**
     * Add the internal site found by the scheduler (stored in the rec objects) to the VPLEX SOURCE volumes
     * backing volumes should this be a MP or Export to HA side request.
     * 
     * @param vpool The virtual pool for this volume
     * @param primaryRecommendation The primary rec from the scheduler
     * @param secondaryRecommendation The secondary rec from the scheduler
     * @param sourceVolume The volume that should be checked for it's backing volumes to be updated
     * @param haVarrayConnectedToRp only be used if isSrcAndHaSwapped == true, tells us which varray is the HA one
     */
    private void setInternalSitesForSourceBackingVolumes(VPlexProtectionRecommendation primaryRecommendation,
            VPlexProtectionRecommendation secondaryRecommendation, Volume sourceVolume,
            boolean exportForMetroPoint, boolean exportToHASideOnly, String haVarrayConnectedToRp) {
        if (exportForMetroPoint) {
            // If this is MetroPoint request and we're looking at the SOURCE volume we need to ensure the
            // backing volumes are aware of which internal site they have been assigned (needed for exporting in
            // RPDeviceController).

            _log.info(String.format("MetroPoint export, update backing volumes for [%s] " +
                    "with correct internal site", sourceVolume.getLabel()));

            // Iterate over each backing volume...
            Iterator<String> it = sourceVolume.getAssociatedVolumes().iterator();
            while (it.hasNext()) {
                Volume backingVolume = _dbClient.queryObject(Volume.class, URI.create(it.next()));
                VirtualArray backingVolumeVarray = _dbClient.queryObject(VirtualArray.class, backingVolume.getVirtualArray());
                String rpSite = "";
                // If this backing volume's varray is equal to the MetroPoint Source virtual volume's varray,
                // then we're looking at the primary leg. Otherwise, it's the
                // secondary leg. Set the InternalSiteName accordingly.
                if (backingVolume.getVirtualArray().equals(sourceVolume.getVirtualArray())) {
                    rpSite = primaryRecommendation.getSourceInternalSiteName();
                    backingVolume.setRpCopyName(backingVolumeVarray.getLabel() + MP_ACTIVE_COPY_SUFFIX);
                } else {
                    rpSite = secondaryRecommendation.getSourceInternalSiteName();
                    backingVolume.setRpCopyName(backingVolumeVarray.getLabel() + MP_STANDBY_COPY_SUFFIX);
                }
                // Save the internal site name to the backing volume, will need this for exporting later
                backingVolume.setInternalSiteName(rpSite);
                _dbClient.persistObject(backingVolume);
                _log.info(String.format("Backing volume [%s] internal site name set to [%s]", backingVolume.getLabel(), rpSite));
            }
        } else if (exportToHASideOnly) {
            // If this is RP+VPLEX request and we're looking at exporting to the HA side only,
            // we need to set the internal site name on the HA backing volume.
            _log.info(String.format("RP+VPLEX HA side export, update HA backing volume for [%s] " +
                    "with correct internal site", sourceVolume.getLabel()));

            // Iterate over each backing volume...
            Iterator<String> it = sourceVolume.getAssociatedVolumes().iterator();
            while (it.hasNext()) {
                Volume backingVolume = _dbClient.queryObject(Volume.class, URI.create(it.next()));

                if (backingVolume.getVirtualArray().toString().equals(haVarrayConnectedToRp)) {
                    // Save the internal site name to the HA backing volume, this will be needed
                    // for exporting the HA side/leg later in RPDeviceController
                    backingVolume.setInternalSiteName(primaryRecommendation.getSourceInternalSiteName());
                    _dbClient.persistObject(backingVolume);
                    _log.info(String.format("Backing volume [%s] internal site name set to [%s]",
                            backingVolume.getLabel(), primaryRecommendation.getSourceInternalSiteName()));
                    break;
                }
            }
        }
    }
}
