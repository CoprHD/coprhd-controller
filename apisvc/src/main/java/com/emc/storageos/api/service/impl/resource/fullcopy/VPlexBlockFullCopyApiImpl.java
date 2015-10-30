/*
 * Copyright (c) 2015 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.api.service.impl.resource.fullcopy;

import static com.emc.storageos.api.mapper.TaskMapper.toTask;

import java.net.URI;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.emc.storageos.api.mapper.BlockMapper;
import com.emc.storageos.api.mapper.TaskMapper;
import com.emc.storageos.api.service.impl.placement.Scheduler;
import com.emc.storageos.api.service.impl.placement.StorageScheduler;
import com.emc.storageos.api.service.impl.placement.VPlexScheduler;
import com.emc.storageos.api.service.impl.placement.VolumeRecommendation;
import com.emc.storageos.api.service.impl.resource.TenantsService;
import com.emc.storageos.api.service.impl.resource.VPlexBlockServiceApiImpl;
import com.emc.storageos.api.service.impl.resource.utils.BlockServiceUtils;
import com.emc.storageos.blockorchestrationcontroller.VolumeDescriptor;
import com.emc.storageos.coordinator.client.service.CoordinatorClient;
import com.emc.storageos.db.client.DbClient;
import com.emc.storageos.db.client.URIUtil;
import com.emc.storageos.db.client.constraint.AlternateIdConstraint;
import com.emc.storageos.db.client.constraint.PrefixConstraint;
import com.emc.storageos.db.client.constraint.URIQueryResultList;
import com.emc.storageos.db.client.model.BlockConsistencyGroup;
import com.emc.storageos.db.client.model.BlockConsistencyGroup.Types;
import com.emc.storageos.db.client.model.BlockObject;
import com.emc.storageos.db.client.model.BlockSnapshot;
import com.emc.storageos.db.client.model.DataObject.Flag;
import com.emc.storageos.db.client.model.DiscoveredDataObject;
import com.emc.storageos.db.client.model.Operation;
import com.emc.storageos.db.client.model.Project;
import com.emc.storageos.db.client.model.StoragePool;
import com.emc.storageos.db.client.model.StorageSystem;
import com.emc.storageos.db.client.model.StringSet;
import com.emc.storageos.db.client.model.VirtualArray;
import com.emc.storageos.db.client.model.VirtualPool;
import com.emc.storageos.db.client.model.Volume;
import com.emc.storageos.db.client.model.util.BlockConsistencyGroupUtils;
import com.emc.storageos.db.client.util.CustomQueryUtility;
import com.emc.storageos.db.client.util.NullColumnValueGetter;
import com.emc.storageos.model.ResourceOperationTypeEnum;
import com.emc.storageos.model.TaskList;
import com.emc.storageos.model.TaskResourceRep;
import com.emc.storageos.model.block.VolumeRestRep;
import com.emc.storageos.svcs.errorhandling.resources.APIException;
import com.emc.storageos.svcs.errorhandling.resources.InternalException;
import com.emc.storageos.util.VPlexUtil;
import com.emc.storageos.volumecontroller.Recommendation;
import com.emc.storageos.volumecontroller.VPlexRecommendation;
import com.emc.storageos.volumecontroller.impl.utils.VirtualPoolCapabilityValuesWrapper;
import com.emc.storageos.vplexcontroller.VPlexController;

/**
 * The VPLEX storage system implementation for the block full copy API.
 */
public class VPlexBlockFullCopyApiImpl extends AbstractBlockFullCopyApiImpl {

    // A reference to the tenants service or null.
    private TenantsService _tenantsService = null;

    // A reference to a logger.
    private static final Logger s_logger = LoggerFactory.getLogger(VPlexBlockFullCopyApiImpl.class);

    /**
     * Constructor
     * 
     * @param dbClient A reference to a database client.
     * @param coordinator A reference to the coordinator client.
     * @param scheduler A reference to a scheduler.
     */
    public VPlexBlockFullCopyApiImpl(DbClient dbClient, CoordinatorClient coordinator,
            Scheduler scheduler, TenantsService tenantsService) {
        super(dbClient, coordinator, scheduler);
        _tenantsService = tenantsService;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public List<BlockObject> getAllSourceObjectsForFullCopyRequest(BlockObject fcSourceObj) {

        // Treats full copies of snapshots as is done in base class.
        if (URIUtil.isType(fcSourceObj.getId(), BlockSnapshot.class)) {
            return super.getAllSourceObjectsForFullCopyRequest(fcSourceObj);
        }

        // By default, if the passed volume is in a consistency group
        // all volumes in the consistency group should be copied.
        List<BlockObject> fcSourceObjList = new ArrayList<BlockObject>();
        URI cgURI = fcSourceObj.getConsistencyGroup();
        if (!NullColumnValueGetter.isNullURI(cgURI)) {
            BlockConsistencyGroup cg = _dbClient.queryObject(BlockConsistencyGroup.class, cgURI);
            // If there is no corresponding native CG for the VPLEX
            // CG, then this is a CG created prior to 2.2 and in this
            // case we want full copies treated like snapshots, which
            // is only create a copy of the passed object.
            if (!cg.checkForType(Types.LOCAL)) {
                fcSourceObjList.add(fcSourceObj);
            } else {
                fcSourceObjList.addAll(getActiveCGVolumes(cg));
            }
        } else {
            fcSourceObjList.add(fcSourceObj);
        }

        return fcSourceObjList;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Map<URI, Volume> getFullCopySetMap(BlockObject fcSourceObj,
            Volume fullCopyVolume) {
        Map<URI, Volume> fullCopyMap = new HashMap<URI, Volume>();

        // Get the source side backend volume of the VPLEX source Volume.
        Volume sourceVolume = (Volume) fcSourceObj;
        Volume srcBackendSrcVolume = VPlexUtil.getVPLEXBackendVolume(
                sourceVolume, true, _dbClient, true);

        // Get the source side backend volume of the VPLEX volume copy.
        // This is the backend volume full copy.
        Volume fcBackendSrcVolume = VPlexUtil.getVPLEXBackendVolume(
                fullCopyVolume, true, _dbClient, true);

        // Get the backend full copy set.
        Map<URI, Volume> backendFullCopyMap = super.getFullCopySetMap(
                srcBackendSrcVolume, fcBackendSrcVolume);

        // Now we need to get the VPLEX volumes for these backend volumes.
        // These will be the VPLEX full copy volumes in the set.
        Iterator<URI> backendCopyIter = backendFullCopyMap.keySet().iterator();
        while (backendCopyIter.hasNext()) {
            URIQueryResultList queryResults = new URIQueryResultList();
            _dbClient.queryByConstraint(
                    AlternateIdConstraint.Factory
                            .getVolumeByAssociatedVolumesConstraint(backendCopyIter.next()
                                    .toString()), queryResults);
            URI vplexCopyVolumeURI = queryResults.iterator().next();
            fullCopyMap.put(vplexCopyVolumeURI,
                    _dbClient.queryObject(Volume.class, vplexCopyVolumeURI));
        }

        return fullCopyMap;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected List<Volume> getActiveCGVolumes(BlockConsistencyGroup cg) {
        return BlockConsistencyGroupUtils.getActiveVplexVolumesInCG(cg, _dbClient, null);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void validateFullCopyCreateRequest(List<BlockObject> fcSourceObjList, int count) {
        if (!fcSourceObjList.isEmpty()) {
            
            URI fcSourceObjURI = fcSourceObjList.get(0).getId();
            if (URIUtil.isType(fcSourceObjURI, BlockSnapshot.class) &&
                    !BlockServiceUtils.isSnapshotFullCopySupported(fcSourceObjURI, _dbClient)) {
                // Snapshot full copy is supported only for OpenStack, VNXBlock, VMAX and IBMXIV
                throw APIException.badRequests.cantCreateFullCopyForVPlexSnapshot();
            }

            // Call super first.
            super.validateFullCopyCreateRequest(fcSourceObjList, count);

            // If there are more than one volume in the consistency group, and they are on
            // different backend storage systems, return error.
            if (fcSourceObjList.size() > 1) {
                List<Volume> volumes = new ArrayList<Volume>();
                for (BlockObject fcSource : fcSourceObjList) {
                    volumes.add((Volume) fcSource);
                }
                if (!VPlexUtil.isVPLEXCGBackendVolumesInSameStorage(volumes, _dbClient)) {
                    throw APIException.badRequests.fullCopyNotAllowedWhenCGAcrossMultipleSystems();
                }
            }

            // Check if the source volume is an ingested CG, without any back end CGs yet. if yes, throw error
            if (fcSourceObjList.get(0) instanceof Volume) {
                Volume srcVol = (Volume) fcSourceObjList.get(0);
                if (VPlexUtil.isVolumeInIngestedCG(srcVol, _dbClient)) {
                    throw APIException.badRequests.fullCopyNotAllowedForIngestedCG(srcVol.getId().toString());
                }
            }

            // Platform specific checks.
            for (BlockObject fcSourceObj : fcSourceObjList) {

                if (fcSourceObj instanceof Volume) {
                    Volume fcSourceVolume = (Volume) fcSourceObj;

                    // If the volume is a VPLEX volume created on a block snapshot,
                    // we don't support creation of a full copy.
                    if (VPlexUtil.isVolumeBuiltOnBlockSnapshot(_dbClient, fcSourceVolume)) {
                        throw APIException.badRequests.fullCopyNotAllowedVolumeIsExposedSnapshot(fcSourceVolume.getId().toString());
                    }

                    StorageSystem system = _dbClient.queryObject(StorageSystem.class, fcSourceObj.getStorageController());
                    if (DiscoveredDataObject.Type.vplex.name().equals(system.getSystemType())) {
                        // If the volume is a VPLEX volume, then we need to be sure that
                        // storage pool of the source backend volume of the VPLEX volume,
                        // which is volume used to create the native full copy, supports
                        // full copy.
                        Volume srcBackendVolume = VPlexUtil.getVPLEXBackendVolume(
                                fcSourceVolume, true, _dbClient, true);
                        StoragePool storagePool = _dbClient.queryObject(StoragePool.class,
                                srcBackendVolume.getPool());
                        verifyFullCopySupportedForStoragePool(storagePool);

                        // If the full copy source is itself a full copy, it is not
                        // detached, and the native full copy i.e., the source side
                        // backend volume, is VNX, then creating a full copy of the
                        // volume will fail. As such, we prevent it.
                        if ((BlockFullCopyUtils.isVolumeFullCopy(fcSourceVolume, _dbClient)) &&
                                (!BlockFullCopyUtils.isFullCopyDetached(fcSourceVolume, _dbClient))) {
                            URI backendSystemURI = srcBackendVolume.getStorageController();
                            StorageSystem backendSystem = _dbClient.queryObject(
                                    StorageSystem.class, backendSystemURI);
                            if (DiscoveredDataObject.Type.vnxblock.name().equals(backendSystem.getSystemType())) {
                                throw APIException.badRequests.cantCreateFullCopyOfVPlexFullCopyUsingVNX();
                            }
                        }
                    }
                }
            }
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public TaskList create(List<BlockObject> fcSourceObjList, VirtualArray varray,
            String name, boolean createInactive, int count, String taskId) {

        // Populate the descriptors list with all volumes required
        // to create the VPLEX volume copies.
        int sourceCounter = 0;
        URI vplexSrcSystemId = null;
        TaskList taskList = new TaskList();
        List<Volume> vplexCopyVolumes = new ArrayList<Volume>();
        List<VolumeDescriptor> volumeDescriptors = new ArrayList<VolumeDescriptor>();
        List<BlockObject> sortedSourceObjectList = sortFullCopySourceList(fcSourceObjList);
        for (BlockObject fcSourceObj : sortedSourceObjectList) {
            URI fcSourceURI = fcSourceObj.getId();
            String copyName = name + (sortedSourceObjectList.size() > 1 ? "-" + ++sourceCounter : "");
            
            vplexSrcSystemId = fcSourceObj.getStorageController();            
            if(fcSourceObj instanceof Volume) {
                // DO IT ONLY FOR VOLUME CLONE - In case of snapshot new VPLEX volume needs to be created
                
                // Create a volume descriptor for the source VPLEX volume being copied.
                // and add it to the descriptors list. Be sure to identify this VPLEX
                // volume as the source volume being copied.
                VolumeDescriptor vplexSrcVolumeDescr = new VolumeDescriptor(
                        VolumeDescriptor.Type.VPLEX_VIRT_VOLUME, vplexSrcSystemId, fcSourceURI,
                        null, null);
                Map<String, Object> descrParams = new HashMap<String, Object>();
                descrParams.put(VolumeDescriptor.PARAM_IS_COPY_SOURCE_ID, Boolean.TRUE);
                vplexSrcVolumeDescr.setParameters(descrParams);
                volumeDescriptors.add(vplexSrcVolumeDescr);
            } else {
                
                BlockSnapshot sourceSnapshot = (BlockSnapshot)fcSourceObj;
                
                URIQueryResultList queryResults = new URIQueryResultList();
                _dbClient.queryByConstraint(AlternateIdConstraint.Factory
                        .getVolumeByAssociatedVolumesConstraint(sourceSnapshot.getParent().getURI()
                                .toString()), queryResults);
                URI vplexVolumeURI = queryResults.iterator().next();                
                if(null!=vplexVolumeURI) {
                    Volume vplexVolume = _dbClient.queryObject(Volume.class, vplexVolumeURI);
                    vplexSrcSystemId = vplexVolume.getStorageController();
                }
            }
            

            // Get some info about the VPLEX volume being copied and its storage system.
            Project vplexSrcProject = BlockFullCopyUtils.queryFullCopySourceProject(fcSourceObj, _dbClient);
            StorageSystem vplexSrcSystem = _dbClient.queryObject(StorageSystem.class, vplexSrcSystemId);
            Project vplexSystemProject = VPlexBlockServiceApiImpl.getVplexProject(
                    vplexSrcSystem, _dbClient, _tenantsService);

            Volume vplexSrcPrimaryVolume = null;
            Volume vplexSrcHAVolume = null;
            Volume vplexSrcVolume = null;
            if(fcSourceObj instanceof Volume) {
                // For the VPLEX volume being copied, determine which of the associated
                // backend volumes is the primary and, for distributed volumes, which
                // is the HA volume. The primary volume will be natively copied and we
                // we need to place and prepare a volume to hold the copy. This copy
                // will be the primary backend volume for the VPLEX volume copy. For
                // a distributed virtual volume, we will need to place and prepare
                // a volume to hold the HA volume of the VPLEX volume copy.
                vplexSrcVolume = (Volume) fcSourceObj;
                StringSet assocVolumeURIs = vplexSrcVolume.getAssociatedVolumes();
                Iterator<String> assocVolumeURIsIter = assocVolumeURIs.iterator();
                while (assocVolumeURIsIter.hasNext()) {
                    URI assocVolumeURI = URI.create(assocVolumeURIsIter.next());
                    Volume assocVolume = _dbClient.queryObject(Volume.class, assocVolumeURI);
                    if (assocVolume.getVirtualArray().toString()
                            .equals(varray.getId().toString())) {
                        vplexSrcPrimaryVolume = assocVolume;
                    } else {
                        vplexSrcHAVolume = assocVolume;
                    }
                }
                
            }
            

            // Get the capabilities
            VirtualPool vpool = BlockFullCopyUtils.queryFullCopySourceVPool(fcSourceObj, _dbClient);
            VirtualPoolCapabilityValuesWrapper capabilities = getCapabilitiesForFullCopyCreate(
                    fcSourceObj, vpool, count);

            // Get the number of copies to create and the size of the volumes.
            // Note that for the size, we must use the actual provisioned size
            // of the source side backend volume. The size passed in the
            // capabilities will be the size of the VPLEX volume. When the
            // source side backend volume for the copy is provisioned, you
            // might not get that actual size. On VMAX, the size will be slightly
            // larger while for VNX the size will be exactly what is requested.
            // So, if the source side is a VMAX, the source side for the copy
            // will be slightly larger than the size in the capabilities. If the HA
            // side is VNX and we use the size in the capabilities, then you will
            // get exactly that size for the HA backend volume. As a result, source
            // side backend volume for the copy will be slightly larger than the
            // HA side. Now the way a VPLEX copy is made is it uses native full
            // copy to create a native full copy of the source side backend
            // volume. It then provisions the HA side volume. The new source side
            // backend copy is then imported into VPLEX in the same way as is done
            // for a vpool change that imports a volume to VPLEX. This code in the
            // VPLEX controller creates a local VPLEX volume using the source side
            // copy and for a distributed volume it then attaches as a remote
            // mirror the HA backend volume that is provisioned. If the HA volume
            // is slightly smaller, then this will fail on the VPLEX. So, we must
            // ensure that HA side volume is big enough by using the provisioned
            // capacity of the source side backend volume of the VPLEX volume being
            // copied.
            long size = 0L;
            List<Volume> vplexCopyPrimaryVolumes = null;
            if(null!=vplexSrcPrimaryVolume) {
                size = vplexSrcPrimaryVolume.getProvisionedCapacity();
                // Place and prepare a volume for each copy to serve as a native
                // copy of a VPLEX backend volume. The VPLEX backend volume that
                // is copied is the backend volume in the same virtual array as the
                // VPLEX volume i.e, the primary backend volume. Create
                // descriptors for these prepared volumes and add them to the list.
                vplexCopyPrimaryVolumes = prepareFullCopyPrimaryVolumes(copyName,
                        count, vplexSrcPrimaryVolume, capabilities, volumeDescriptors, vpool);
            } else {
                // Get the provisioned capacity of the snapshot
                size = ((BlockSnapshot)fcSourceObj).getProvisionedCapacity();
                //Place and prepare a back-end volume for each block snapshot
                vplexCopyPrimaryVolumes = prepareFullCopyPrimaryVolumes(copyName,
                        count, fcSourceObj, capabilities, volumeDescriptors, vpool);
            }
            

            

            // If the VPLEX volume being copied is distributed, then the VPLEX
            // HA volume should be non-null. We use the VPLEX scheduler to place
            // and then prepare volumes for the HA volumes of the VPLEX volume
            // copies. This should be done in the same manner as is done for the
            // import volume routine. This is because to form the VPLEX volume
            // copy we import the copy of the primary backend volume.
            List<Volume> vplexCopyHAVolumes = new ArrayList<Volume>();
            if (vplexSrcHAVolume != null) {
                vplexCopyHAVolumes.addAll(prepareFullCopyHAVolumes(copyName, count, size,
                        vplexSrcSystem, vplexSystemProject, varray, vplexSrcHAVolume,
                        taskId, volumeDescriptors));
            }

            // For each copy to be created, place and prepare a volume for the
            // primary backend volume copy. When copying a distributed VPLEX
            // volume, we also must place and prepare a volume for the HA
            // backend volume copy. Lastly, we must prepare a volume for the
            // VPLEX volume copy. Create descriptors for these prepared volumes
            // and add them to the volume descriptors list.
            for (int i = 0; i < count; i++) {
                // Prepare a new VPLEX volume for each copy.
                Volume vplexCopyPrimaryVolume = vplexCopyPrimaryVolumes.get(i);
                Volume vplexCopyHAVolume = null;
                if (!vplexCopyHAVolumes.isEmpty()) {
                    vplexCopyHAVolume = vplexCopyHAVolumes.get(i);
                }
                Volume vplexCopyVolume = prepareFullCopyVPlexVolume(copyName, count, i, size,
                        fcSourceObj, vplexSrcProject, varray, vpool,
                        vplexSrcSystemId, vplexCopyPrimaryVolume, vplexCopyHAVolume, taskId,
                        volumeDescriptors);
                vplexCopyVolumes.add(vplexCopyVolume);

                // Create task for each copy.
                Operation op = vplexCopyVolume.getOpStatus().get(taskId);
                TaskResourceRep task = toTask(vplexCopyVolume, taskId, op);
                taskList.getTaskList().add(task);
            }
        }

        // Invoke the VPLEX controller to create the copies.
        try {
            s_logger.info("Getting VPlex controller {}.", taskId);
            VPlexController controller = getController(VPlexController.class,
                    DiscoveredDataObject.Type.vplex.toString());
            // TBD controller needs to be updated to handle CGs.
            controller.createFullCopy(vplexSrcSystemId, volumeDescriptors, taskId);
            s_logger.info("Successfully invoked controller.");
        } catch (InternalException e) {
            s_logger.error("Controller error", e);

            // Update the status for the VPLEX volume copies and their
            // corresponding tasks.
            for (Volume vplexCopyVolume : vplexCopyVolumes) {
                Operation op = vplexCopyVolume.getOpStatus().get(taskId);
                if (op != null) {
                    op.error(e);
                    vplexCopyVolume.getOpStatus().updateTaskStatus(taskId, op);
                    _dbClient.persistObject(vplexCopyVolume);
                    for (TaskResourceRep task : taskList.getTaskList()) {
                        if (task.getResource().getId().equals(vplexCopyVolume.getId())) {
                            task.setState(op.getStatus());
                            task.setMessage(op.getMessage());
                            break;
                        }
                    }
                }
            }

            // Mark all volumes inactive, except for the VPLEX volume
            // we were trying to copy.
            for (VolumeDescriptor descriptor : volumeDescriptors) {
                if (descriptor.getParameters().get(
                        VolumeDescriptor.PARAM_IS_COPY_SOURCE_ID) == null) {
                    Volume volume = _dbClient.queryObject(Volume.class, descriptor.getVolumeURI());
                    volume.setInactive(true);
                    _dbClient.persistObject(volume);
                }
            }
        }

        return taskList;
    }

    /**
     * Places and prepares the HA volumes when copying a distributed VPLEX
     * volume.
     * 
     * @param name The base name for the volume.
     * @param copyCount The number of copies to be made.
     * @param size The size for the HA volume.
     * @param vplexSystem A reference to the VPLEX storage system.
     * @param vplexSystemProject A reference to the VPLEX system project.
     * @param srcVarray The virtual array for the VPLEX volume being copied.
     * @param srcHAVolume The HA volume of the VPLEX volume being copied.
     * @param taskId The task identifier.
     * @param volumeDescriptors The list of descriptors.
     * 
     * @return A list of the prepared HA volumes for the VPLEX volume copy.
     */
    private List<Volume> prepareFullCopyHAVolumes(String name, int copyCount, Long size,
            StorageSystem vplexSystem, Project vplexSystemProject, VirtualArray srcVarray,
            Volume srcHAVolume, String taskId, List<VolumeDescriptor> volumeDescriptors) {

        List<Volume> copyHAVolumes = new ArrayList<Volume>();

        // Get the storage placement recommendations for the volumes.
        // Placement must occur on the same VPLEX system
        Set<URI> vplexSystemURIS = new HashSet<URI>();
        vplexSystemURIS.add(vplexSystem.getId());
        VirtualArray haVarray = _dbClient.queryObject(VirtualArray.class, srcHAVolume.getVirtualArray());
        VirtualPool haVpool = _dbClient.queryObject(VirtualPool.class, srcHAVolume.getVirtualPool());
        VirtualPoolCapabilityValuesWrapper haCapabilities = new
                VirtualPoolCapabilityValuesWrapper();
        haCapabilities.put(VirtualPoolCapabilityValuesWrapper.SIZE, size);
        haCapabilities.put(VirtualPoolCapabilityValuesWrapper.RESOURCE_COUNT, copyCount);
        VirtualPool vpool = BlockFullCopyUtils.queryFullCopySourceVPool(srcHAVolume, _dbClient);
        if (VirtualPool.ProvisioningType.Thin.toString().equalsIgnoreCase(
                vpool.getSupportedProvisioningType())) {
            haCapabilities.put(VirtualPoolCapabilityValuesWrapper.THIN_PROVISIONING, Boolean.TRUE);
            // To guarantee that storage pool for a copy has enough physical
            // space to contain current allocated capacity of thin source volume
            haCapabilities.put(VirtualPoolCapabilityValuesWrapper.THIN_VOLUME_PRE_ALLOCATE_SIZE,
                    BlockFullCopyUtils.getAllocatedCapacityForFullCopySource(srcHAVolume, _dbClient));
        }
        List<Recommendation> recommendations = ((VPlexScheduler) _scheduler)
                .scheduleStorageForImport(srcVarray, vplexSystemURIS, haVarray, haVpool,
                        haCapabilities);
        if (recommendations.isEmpty()) {
            throw APIException.badRequests.noStorageForHaVolumesForVplexVolumeCopies();
        }

        // Prepare the HA volumes for the VPLEX volume copy.
        int copyIndex = 1;
        for (Recommendation recommendation : recommendations) {
            VPlexRecommendation haRecommendation = (VPlexRecommendation) recommendation;
            for (int i = 0; i < haRecommendation.getResourceCount(); i++) {
                // Determine the name for the HA volume copy.
                StringBuilder nameBuilder = new StringBuilder(name);
                nameBuilder.append("-1");
                if (copyCount > 1) {
                    nameBuilder.append("-");
                    nameBuilder.append(copyIndex++);
                }

                // Prepare the volume.
                Volume volume = VPlexBlockServiceApiImpl.prepareVolumeForRequest(size,
                        vplexSystemProject, haVarray, haVpool,
                        haRecommendation.getSourceStorageSystem(), haRecommendation.getSourceStoragePool(),
                        nameBuilder.toString(), null, taskId, _dbClient);
                volume.addInternalFlags(Flag.INTERNAL_OBJECT);
                _dbClient.persistObject(volume);
                copyHAVolumes.add(volume);

                // Create the volume descriptor and add it to the passed list.
                VolumeDescriptor volumeDescriptor = new VolumeDescriptor(
                        VolumeDescriptor.Type.BLOCK_DATA, volume.getStorageController(),
                        volume.getId(), volume.getPool(), haCapabilities);
                volumeDescriptors.add(volumeDescriptor);
            }
        }

        return copyHAVolumes;
    }

    /**
     * Prepares the VPLEX volume copies.
     * 
     * @param name The base name for the volume.
     * @param copyCount The total number of copies.
     * @param copyIndex The index for this copy.
     * @param size The size for the HA volume.
     * @param fcSourceObject The VPLEX volume or the snapshot being copied.
     * @param srcProject The project for the VPLEX volume being copied.
     * @param srcVarray The virtual array for the VPLEX volume being copied.
     * @param srcVpool The virtual pool for the VPLEX volume being copied.
     * @param srcSystemURI The VPLEX system URI.
     * @param primaryVolume The primary volume for the copy.
     * @param haVolume The HA volume for the copy, or null.
     * @param taskId The task identifier.
     * @param volumeDescriptors The list of descriptors.
     * 
     * @return A reference to the prepared VPLEX volume copy.
     */
    private Volume prepareFullCopyVPlexVolume(String name, int copyCount, int copyIndex,
            long size, BlockObject fcSourceObject, Project srcProject, VirtualArray srcVarray,
            VirtualPool srcVpool, URI srcSystemURI, Volume primaryVolume, Volume haVolume,
            String taskId, List<VolumeDescriptor> volumeDescriptors) {

        // Determine the VPLEX volume copy name.
        StringBuilder nameBuilder = new StringBuilder(name);
        if (copyCount > 1) {
            nameBuilder.append("-");
            nameBuilder.append(copyIndex + 1);
        }

        // Prepare the VPLEX volume copy.
        Volume vplexCopyVolume = VPlexBlockServiceApiImpl.prepareVolumeForRequest(size,
                srcProject, srcVarray, srcVpool, srcSystemURI,
                NullColumnValueGetter.getNullURI(), nameBuilder.toString(),
                ResourceOperationTypeEnum.CREATE_VOLUME_FULL_COPY, taskId, _dbClient);

        // Create a volume descriptor and add it to the passed list.
        VolumeDescriptor vplexCopyVolumeDescr = new VolumeDescriptor(
                VolumeDescriptor.Type.VPLEX_VIRT_VOLUME, srcSystemURI,
                vplexCopyVolume.getId(), null, null);
        volumeDescriptors.add(vplexCopyVolumeDescr);

        // Set the associated volumes for this new VPLEX volume copy to
        // the copy of the backend primary and the newly prepared HA
        // volume if the VPLEX volume being copied is distributed.
        vplexCopyVolume.setAssociatedVolumes(new StringSet());
        StringSet assocVolumes = vplexCopyVolume.getAssociatedVolumes();
        assocVolumes.add(primaryVolume.getId().toString());
        if (haVolume != null) {
            assocVolumes.add(haVolume.getId().toString());
        }

        // Set the VPLEX source volume or the snapshot for the copy.
        vplexCopyVolume.setAssociatedSourceVolume(fcSourceObject.getId());

        //Except for the Openstack, all Copies always created active.
        if(VPlexUtil.isOpenStackBackend(fcSourceObject, _dbClient)) {
            vplexCopyVolume.setSyncActive(Boolean.FALSE);
        } else {
            vplexCopyVolume.setSyncActive(Boolean.TRUE);
        }
            
        

        // Persist the copy.
        _dbClient.persistObject(vplexCopyVolume);

        return vplexCopyVolume;
    }

    /**
     * Places and prepares the primary copy volumes when copying a VPLEX virtual
     * volume.
     * 
     * @param name The base name for the volume.
     * @param copyCount The number of copies to be made.
     * @param srcBlockObject The primary volume of the VPLEX volume or snapshot being copied.
     * @param srcCapabilities The capabilities of the primary volume.
     * @param volumeDescriptors The list of descriptors.
     * @param vPool The vPool to which the source object belongs to.
     * 
     * @return A list of the prepared primary volumes for the VPLEX volume copy.
     */
    private List<Volume> prepareFullCopyPrimaryVolumes(String name, int copyCount,
            BlockObject srcBlockObject, VirtualPoolCapabilityValuesWrapper srcCapabilities,
            List<VolumeDescriptor> volumeDescriptors, VirtualPool vPool) {

        List<Volume> copyPrimaryVolumes = new ArrayList<Volume>();

        // Get the placement recommendations for the primary volume copies.
        // Use the same method as is done for native volume copy.
        VirtualArray vArray = _dbClient.queryObject(VirtualArray.class, srcBlockObject.getVirtualArray());
        List<VolumeRecommendation> recommendations = ((VPlexScheduler) _scheduler).getBlockScheduler()
                .getRecommendationsForVolumeClones(vArray, vPool, srcBlockObject,
                        srcCapabilities);
        if (recommendations.isEmpty()) {
            throw APIException.badRequests.noStorageForPrimaryVolumesForVplexVolumeCopies();
        }

        // Prepare the copy volumes for each recommendation. Again,
        // use the same manner as is done for native volume copy.
        StringBuilder nameBuilder = new StringBuilder(name);
        nameBuilder.append("-0");
        int copyIndex = (copyCount > 1) ? 1 : 0;
        for (VolumeRecommendation recommendation : recommendations) {
            Volume volume = StorageScheduler.prepareFullCopyVolume(_dbClient,
                    nameBuilder.toString(), srcBlockObject, recommendation, copyIndex++,
                    srcCapabilities);
            volume.addInternalFlags(Flag.INTERNAL_OBJECT);
            _dbClient.persistObject(volume);
            copyPrimaryVolumes.add(volume);

            // Create the volume descriptor and add it to the passed list.
            VolumeDescriptor volumeDescriptor = new VolumeDescriptor(
                    VolumeDescriptor.Type.VPLEX_IMPORT_VOLUME, volume.getStorageController(),
                    volume.getId(), volume.getPool(), srcCapabilities);
            volumeDescriptors.add(volumeDescriptor);
        }

        return copyPrimaryVolumes;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public TaskList activate(BlockObject fcSourceObj, Volume fullCopyVolume) {
        // VPLEX volumes are created active, so we can just call super
        // and take the is already activated path.
        return super.activate(fcSourceObj, fullCopyVolume);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public TaskList detach(BlockObject fcSourceObj, Volume fullCopyVolume) {
        // If full copy volume is already detached or was never
        // activated, return detach action is completed successfully
        // as done in base class. Otherwise, send detach full copy
        // request to controller.
        TaskList taskList = new TaskList();
        String taskId = UUID.randomUUID().toString();
        if ((BlockFullCopyUtils.isFullCopyDetached(fullCopyVolume, _dbClient)) ||
                (BlockFullCopyUtils.isFullCopyInactive(fullCopyVolume, _dbClient))) {
            super.detach(fcSourceObj, fullCopyVolume);
        } else {
            // You cannot create a full copy of a VPLEX snapshot, so
            // the source will be a volume.
            Volume sourceVolume = (Volume) fcSourceObj;

            // If the source is in a CG, then we will detach the corresponding
            // full copies for all the volumes in the CG. Since we did not allow
            // full copies for volumes or snaps in CGs prior to Jedi, there should
            // be a full copy for all volumes in the CG.
            Map<URI, Volume> fullCopyMap = getFullCopySetMap(sourceVolume, fullCopyVolume);
            Set<URI> fullCopyURIs = fullCopyMap.keySet();

            // Get the storage system for the source volume.
            StorageSystem sourceSystem = _dbClient.queryObject(StorageSystem.class,
                    sourceVolume.getStorageController());
            URI sourceSystemURI = sourceSystem.getId();

            // Create the detach task on the full copy volumes.
            for (URI fullCopyURI : fullCopyURIs) {
                Operation op = _dbClient.createTaskOpStatus(Volume.class, fullCopyURI,
                        taskId, ResourceOperationTypeEnum.DETACH_VOLUME_FULL_COPY);
                fullCopyMap.get(fullCopyURI).getOpStatus().put(taskId, op);
                TaskResourceRep fullCopyVolumeTask = TaskMapper.toTask(
                        fullCopyMap.get(fullCopyURI), taskId, op);
                taskList.getTaskList().add(fullCopyVolumeTask);
            }

            // Invoke the controller.
            try {
                VPlexController controller = getController(VPlexController.class,
                        DiscoveredDataObject.Type.vplex.toString());
                controller.detachFullCopy(sourceSystemURI, new ArrayList<URI>(
                        fullCopyURIs), taskId);
            } catch (InternalException ie) {
                s_logger.error("Controller error", ie);

                // Update the status for the VPLEX volume copies and their
                // corresponding tasks.
                for (Volume vplexFullCopy : fullCopyMap.values()) {
                    Operation op = vplexFullCopy.getOpStatus().get(taskId);
                    if (op != null) {
                        op.error(ie);
                        vplexFullCopy.getOpStatus().updateTaskStatus(taskId, op);
                        _dbClient.persistObject(vplexFullCopy);
                        for (TaskResourceRep task : taskList.getTaskList()) {
                            if (task.getResource().getId().equals(vplexFullCopy.getId())) {
                                task.setState(op.getStatus());
                                task.setMessage(op.getMessage());
                                break;
                            }
                        }
                    }
                }
            }
        }
        return taskList;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public TaskList restoreSource(Volume sourceVolume, Volume fullCopyVolume) {
        // Create the task list.
        TaskList taskList = new TaskList();

        // Create a unique task id.
        String taskId = UUID.randomUUID().toString();

        // If the source is in a CG, then we will restore the corresponding
        // full copies for all the volumes in the CG. Since we did not allow
        // full copies for volumes or snaps in CGs prior to Jedi, there should
        // be a full copy for all volumes in the CG.
        Map<URI, Volume> fullCopyMap = getFullCopySetMap(sourceVolume, fullCopyVolume);
        Set<URI> fullCopyURIs = fullCopyMap.keySet();

        // Get the storage system for the source volume.
        StorageSystem sourceSystem = _dbClient.queryObject(StorageSystem.class,
                sourceVolume.getStorageController());
        URI sourceSystemURI = sourceSystem.getId();

        // Create the restore task on the full copy volumes.
        // The controller expects the task to be on the full
        // copy even though the source is being restored.
        // Not really sure why.
        for (URI fullCopyURI : fullCopyURIs) {
            Operation op = _dbClient.createTaskOpStatus(Volume.class, fullCopyURI,
                    taskId, ResourceOperationTypeEnum.RESTORE_VOLUME_FULL_COPY);
            fullCopyMap.get(fullCopyURI).getOpStatus().put(taskId, op);
            TaskResourceRep fullCopyVolumeTask = TaskMapper.toTask(
                    fullCopyMap.get(fullCopyURI), taskId, op);
            taskList.getTaskList().add(fullCopyVolumeTask);
        }

        // Invoke the controller.
        try {
            VPlexController controller = getController(VPlexController.class,
                    DiscoveredDataObject.Type.vplex.toString());
            controller.restoreFromFullCopy(sourceSystemURI, new ArrayList<URI>(
                    fullCopyURIs), taskId);
        } catch (InternalException ie) {
            s_logger.error("Controller error", ie);

            // Update the status for the VPLEX volume copies and their
            // corresponding tasks.
            for (Volume vplexFullCopy : fullCopyMap.values()) {
                Operation op = vplexFullCopy.getOpStatus().get(taskId);
                if (op != null) {
                    op.error(ie);
                    vplexFullCopy.getOpStatus().updateTaskStatus(taskId, op);
                    _dbClient.persistObject(vplexFullCopy);
                    for (TaskResourceRep task : taskList.getTaskList()) {
                        if (task.getResource().getId().equals(vplexFullCopy.getId())) {
                            task.setState(op.getStatus());
                            task.setMessage(op.getMessage());
                            break;
                        }
                    }
                }
            }
        }
        return taskList;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public TaskList resynchronizeCopy(Volume sourceVolume, Volume fullCopyVolume) {
        // Create the task list.
        TaskList taskList = new TaskList();

        // Create a unique task id.
        String taskId = UUID.randomUUID().toString();

        // If the source is in a CG, then we will resynchronize the corresponding
        // full copies for all the volumes in the CG. Since we did not allow
        // full copies for volumes or snaps in CGs prior to Jedi, there should
        // be a full copy for all volumes in the CG.
        Map<URI, Volume> fullCopyMap = getFullCopySetMap(sourceVolume, fullCopyVolume);
        Set<URI> fullCopyURIs = fullCopyMap.keySet();

        // Get the storage system for the source volume.
        StorageSystem sourceSystem = _dbClient.queryObject(StorageSystem.class,
                sourceVolume.getStorageController());
        URI sourceSystemURI = sourceSystem.getId();

        // Create the resynchronize task on the full copy volumes.
        for (URI fullCopyURI : fullCopyURIs) {
            Operation op = _dbClient.createTaskOpStatus(Volume.class, fullCopyURI,
                    taskId, ResourceOperationTypeEnum.RESYNCHRONIZE_VOLUME_FULL_COPY);
            fullCopyMap.get(fullCopyURI).getOpStatus().put(taskId, op);
            TaskResourceRep fullCopyVolumeTask = TaskMapper.toTask(
                    fullCopyMap.get(fullCopyURI), taskId, op);
            taskList.getTaskList().add(fullCopyVolumeTask);
        }

        // Invoke the controller.
        try {
            VPlexController controller = getController(VPlexController.class,
                    DiscoveredDataObject.Type.vplex.toString());
            controller.resyncFullCopy(sourceSystemURI, new ArrayList<URI>(
                    fullCopyURIs), taskId);
        } catch (InternalException ie) {
            s_logger.error("Controller error", ie);

            // Update the status for the VPLEX volume copies and their
            // corresponding tasks.
            for (Volume vplexFullCopy : fullCopyMap.values()) {
                Operation op = vplexFullCopy.getOpStatus().get(taskId);
                if (op != null) {
                    op.error(ie);
                    vplexFullCopy.getOpStatus().updateTaskStatus(taskId, op);
                    _dbClient.persistObject(vplexFullCopy);
                    for (TaskResourceRep task : taskList.getTaskList()) {
                        if (task.getResource().getId().equals(vplexFullCopy.getId())) {
                            task.setState(op.getStatus());
                            task.setMessage(op.getMessage());
                            break;
                        }
                    }
                }
            }
        }
        return taskList;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public VolumeRestRep checkProgress(URI sourceURI, Volume fullCopyVolume) {
        // Get the native backend full copy volume for this VPLEX
        // full copy volume.
        Volume nativeFullCopyVolume = VPlexUtil.getVPLEXBackendVolume(fullCopyVolume,
                true, _dbClient);

        // Call super to check the progress of the backend full
        // copy volume.
        Integer percentSynced = getSyncPercentage(
                nativeFullCopyVolume.getAssociatedSourceVolume(), nativeFullCopyVolume);

        // The synchronization progress of the VPLEX full copy is that
        // of the backend full copy.
        VolumeRestRep volumeRestRep = BlockMapper.map(_dbClient, fullCopyVolume);
        volumeRestRep.getProtection().getFullCopyRep().setPercentSynced(percentSynced);
        return volumeRestRep;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected void verifyFullCopyRequestCount(BlockObject fcSourceObj, int count) {
        // Verify the requested copy count. You can only
        // have as many as is allowed by the source backend volume.
        
        if(fcSourceObj instanceof Volume) {
            Volume fcSourceVolume = (Volume) fcSourceObj;
            fcSourceObj = VPlexUtil.getVPLEXBackendVolume(fcSourceVolume, true, _dbClient, true);
        }
        
        // Verify if the source backend volume supports full copy
        URI systemURI = fcSourceObj.getStorageController();
        StorageSystem system = _dbClient.queryObject(StorageSystem.class, systemURI);
        int maxCount = Integer.MAX_VALUE;
        if (system != null) {
            maxCount = BlockFullCopyManager.getMaxFullCopiesForSystemType
                    (system.getSystemType());
        }
        // If max count is 0, then the operation is not supported
        if (maxCount == 0) {
            throw APIException.badRequests.fullCopyNotSupportedByBackendSystem(fcSourceObj.getId());
        }

        BlockFullCopyUtils.validateActiveFullCopyCount(fcSourceObj, count, _dbClient);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public TaskList establishVolumeAndFullCopyGroupRelation(Volume sourceVolume, Volume fullCopyVolume) {

        // Create the task list.
        TaskList taskList = new TaskList();

        // Create a unique task id.
        String taskId = UUID.randomUUID().toString();

        // Get the id of the source volume.
        URI sourceVolumeURI = sourceVolume.getId();

        // Get the id of the full copy volume.
        URI fullCopyURI = fullCopyVolume.getId();

        // Get the storage system for the source volume.
        StorageSystem sourceSystem = _dbClient.queryObject(StorageSystem.class,
                sourceVolume.getStorageController());
        URI sourceSystemURI = sourceSystem.getId();

        // Create the task on the full copy volume.
        Operation op = _dbClient.createTaskOpStatus(Volume.class, fullCopyURI,
                taskId, ResourceOperationTypeEnum.ESTABLISH_VOLUME_FULL_COPY);
        fullCopyVolume.getOpStatus().put(taskId, op);
        TaskResourceRep fullCopyVolumeTask = TaskMapper.toTask(
                fullCopyVolume, taskId, op);
        taskList.getTaskList().add(fullCopyVolumeTask);

        // Invoke the controller.
        try {
            VPlexController controller = getController(VPlexController.class,
                    DiscoveredDataObject.Type.vplex.toString());
            controller.establishVolumeAndFullCopyGroupRelation(sourceSystemURI, sourceVolumeURI,
                    fullCopyURI, taskId);
        } catch (InternalException ie) {
            s_logger.error("Controller error", ie);

            // Update the status for the VPLEX volume copies and their
            // corresponding tasks.
            if (op != null) {
                op.error(ie);
                fullCopyVolume.getOpStatus().updateTaskStatus(taskId, op);
                _dbClient.persistObject(fullCopyVolume);
                fullCopyVolumeTask.setState(op.getStatus());
                fullCopyVolumeTask.setMessage(op.getMessage());
            }
        }
        return taskList;
    }
    
    /**
     * {@inheritDoc}
     */
    @Override
    public boolean volumeCanBeDeleted(Volume volume) {
        
        // For OpenStack, not required to do the detach checks
        if(VPlexUtil.isOpenStackBackend(volume, _dbClient)) {
            return true;
        }
        
        return super.volumeCanBeDeleted(volume);
        
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean volumeCanBeExpanded(Volume volume) {
        
        // For OpenStack, not required to do the detach checks
        if(VPlexUtil.isOpenStackBackend(volume, _dbClient)) {
            return true;
        }
        
        return super.volumeCanBeExpanded(volume);
    }

}
