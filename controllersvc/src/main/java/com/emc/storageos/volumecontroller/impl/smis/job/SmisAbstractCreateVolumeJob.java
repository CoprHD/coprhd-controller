/*
 * Copyright (c) 2012 EMC Corporation
 * All Rights Reserved
 */

package com.emc.storageos.volumecontroller.impl.smis.job;

import java.io.IOException;
import java.net.URI;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Calendar;
import java.util.List;

import javax.cim.CIMArgument;
import javax.cim.CIMDataType;
import javax.cim.CIMInstance;
import javax.cim.CIMObjectPath;
import javax.cim.CIMProperty;
import javax.cim.UnsignedInteger16;
import javax.wbem.CloseableIterator;
import javax.wbem.WBEMException;
import javax.wbem.client.WBEMClient;

import com.emc.storageos.volumecontroller.impl.smis.CIMObjectPathFactory;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.emc.storageos.db.client.DbClient;
import com.emc.storageos.db.client.model.BlockConsistencyGroup;
import com.emc.storageos.db.client.model.StoragePool;
import com.emc.storageos.db.client.model.StorageSystem;
import com.emc.storageos.db.client.model.StringMap;
import com.emc.storageos.db.client.model.Volume;
import com.emc.storageos.db.client.model.Volume.PersonalityTypes;
import com.emc.storageos.exceptions.DeviceControllerErrors;
import com.emc.storageos.exceptions.DeviceControllerException;
import com.emc.storageos.protectioncontroller.impl.recoverpoint.RPHelper;
import com.emc.storageos.svcs.errorhandling.model.ServiceError;
import com.emc.storageos.volumecontroller.JobContext;
import com.emc.storageos.volumecontroller.TaskCompleter;
import com.emc.storageos.volumecontroller.impl.ControllerServiceImpl;
import com.emc.storageos.volumecontroller.impl.NativeGUIDGenerator;
import com.emc.storageos.volumecontroller.impl.smis.CIMConnectionFactory;
import com.emc.storageos.volumecontroller.impl.smis.CIMPropertyFactory;
import com.emc.storageos.volumecontroller.impl.smis.SmisCommandHelper;
import com.emc.storageos.volumecontroller.impl.smis.SmisConstants;
import com.emc.storageos.volumecontroller.impl.smis.SmisStorageDevice;
import com.emc.storageos.volumecontroller.impl.smis.SmisUtils;
import com.emc.storageos.workflow.WorkflowException;

/**
 * This class will have a base implementation of the
 * updateStatus for create volume operations.
 */
public abstract class SmisAbstractCreateVolumeJob extends SmisReplicaCreationJobs {
    private static final Logger _log = LoggerFactory.getLogger(SmisAbstractCreateVolumeJob.class);
    private URI _storagePool;

    private CIMObjectPathFactory _cimPath;

    public void setCimPath(CIMObjectPathFactory _cimPath) {
        this._cimPath = _cimPath;
    }

    public SmisAbstractCreateVolumeJob(CIMObjectPath cimJob, URI storageSystem, URI storagePool, TaskCompleter taskCompleter, String name) {
        super(cimJob, storageSystem, taskCompleter, name);

        _storagePool = storagePool;
    }

    /**
     * Called to update the job status when the volume create job completes.
     * <p/>
     * This is common update code for volume create operations.
     * 
     * @param jobContext The job context.
     */
    @Override
    public void updateStatus(JobContext jobContext) throws Exception {
        CloseableIterator<CIMObjectPath> iterator = null;
        DbClient dbClient = jobContext.getDbClient();
        JobStatus jobStatus = getJobStatus();
        try {
            if (jobStatus == JobStatus.IN_PROGRESS) {
                return;
            }
            int volumeCount = 0;
            String opId = getTaskCompleter().getOpId();
            StringBuilder logMsgBuilder = new StringBuilder(String.format("Updating status of job %s to %s", opId, jobStatus.name()));
            CIMConnectionFactory cimConnectionFactory = jobContext.getCimConnectionFactory();
            WBEMClient client = getWBEMClient(dbClient, cimConnectionFactory);
            iterator = client.associatorNames(getCimJob(), null, SmisConstants.CIM_STORAGE_VOLUME, null, null);
            Calendar now = Calendar.getInstance();

            // If terminal state update storage pool capacity and remove reservation for volumes capacity
            // from pool's reserved capacity map.
            if (jobStatus == JobStatus.SUCCESS || jobStatus == JobStatus.FAILED || jobStatus == JobStatus.FATAL_ERROR) {
                SmisUtils.updateStoragePoolCapacity(dbClient, client, _storagePool);
                StoragePool pool = dbClient.queryObject(StoragePool.class, _storagePool);
                StringMap reservationMap = pool.getReservedCapacityMap();
                for (URI volumeId : getTaskCompleter().getIds()) {
                    // remove from reservation map
                    reservationMap.remove(volumeId.toString());
                }
                dbClient.persistObject(pool);
            }

            if (jobStatus == JobStatus.SUCCESS) {
                List<URI> volumes = new ArrayList<URI>();
                while (iterator.hasNext()) {
                    CIMObjectPath volumePath = iterator.next();
                    CIMProperty<String> deviceID = (CIMProperty<String>) volumePath
                            .getKey(SmisConstants.CP_DEVICE_ID);
                    String nativeID = deviceID.getValue();
                    URI volumeId = getTaskCompleter().getId(volumeCount++);
                    volumes.add(volumeId);
                    persistVolumeNativeID(dbClient, volumeId, nativeID, now);
                    processVolume(jobContext, volumePath, nativeID, volumeId, client, dbClient, logMsgBuilder, now);
                }

                // Add Volumes to Consistency Group (if needed)
                addVolumesToConsistencyGroup(jobContext, volumes);

            } else if (jobStatus == JobStatus.FAILED) {
                if (iterator.hasNext()) {
                    while (iterator.hasNext()) {
                        CIMObjectPath volumePath = iterator.next();
                        CIMProperty<String> deviceID = (CIMProperty<String>) volumePath
                                .getKey(SmisConstants.CP_DEVICE_ID);
                        String nativeID = deviceID.getValue();
                        URI volumeId = getTaskCompleter().getId(volumeCount++);
                        if ((nativeID != null) && (nativeID.length() != 0)) {
                            persistVolumeNativeID(dbClient, volumeId, nativeID, now);
                            processVolume(jobContext, volumePath, nativeID, volumeId,
                                    client, dbClient, logMsgBuilder, now);
                        } else {
                            logMsgBuilder.append("\n");
                            logMsgBuilder.append(String.format(
                                    "Task %s failed to create volume: %s", opId, volumeId));
                            Volume volume = dbClient.queryObject(Volume.class, volumeId);
                            volume.setInactive(true);
                            dbClient.persistObject(volume);
                        }
                    }
                } else {
                    for (URI id : getTaskCompleter().getIds()) {
                        logMsgBuilder.append("\n");
                        logMsgBuilder.append(String.format(
                                "Task %s failed to create volume: %s", opId, id.toString()));
                        Volume volume = dbClient.queryObject(Volume.class, id);
                        volume.setInactive(true);
                        dbClient.persistObject(volume);
                    }
                }
            }
            _log.info(logMsgBuilder.toString());
        } catch (Exception e) {
            _log.error("Caught an exception while trying to updateStatus for SmisCreateVolumeJob", e);
            setPostProcessingErrorStatus("Encountered an internal error during volume create job status processing : " + e.getMessage());
        } finally {
            super.updateStatus(jobContext);
            if (iterator != null) {
                iterator.close();
            }
        }
    }

    /**
     * This method can be implemented by the derived class for
     * specific updates or processing for a derived class.
     *
     * Default behavior simply updates the deviceLabel name for the single volume that was created.
     *
     * @param storageSystem [in] - StorageSystem for the Volume
     * @param dbClient [in] - Client for reading/writing from/to database.
     * @param client [in] - WBEMClient for accessing SMI-S provider data
     * @param volume [in] - Reference to Bourne's Volume object
     * @param volumePath [in] - Name reference to the SMI-S side volume object
     */
    protected void specificProcessing(StorageSystem storageSystem, DbClient dbClient, WBEMClient client, Volume volume, CIMInstance volumeInstance,
            CIMObjectPath volumePath) {
        String elementName = CIMPropertyFactory.getPropertyValue(volumeInstance, SmisConstants.CP_ELEMENT_NAME);
        volume.setDeviceLabel(elementName);
    }

    /**
     * Processes a newly created volume.
     * 
     * @param jobContext The job context.
     * @param volumePath The CIM object path for the volume.
     * @param nativeID The native volume identifier.
     * @param volumeId The Bourne volume id.
     * @param client The CIM client.
     * @param dbClient the database client.
     * @param logMsgBuilder Holds a log message.
     * @param creationTime Holds the date-time for the volume creation
     * @throws java.io.IOException When an error occurs querying the database.
     * @throws WorkflowException
     */
    private void processVolume(JobContext jobContext, CIMObjectPath volumePath, String nativeID,
            URI volumeId, WBEMClient client, DbClient dbClient,
            StringBuilder logMsgBuilder, Calendar creationTime) throws Exception, IOException, DeviceControllerException, WBEMException {
        Volume volume = dbClient.queryObject(Volume.class, volumeId);
        CIMInstance volumeInstance = commonVolumeUpdate(dbClient, client, volume, volumePath);
        URI storageSystemURI = volume.getStorageController();
        StorageSystem storageSystem = dbClient.queryObject(StorageSystem.class, storageSystemURI);
        if (volume.getIsComposite() && _cimPath != null) {
            // need to set meta volume member size (required for volume expansion).
            // this call is context dependent --- this is why we check for cimPath be set.
            // first, get meta members list;
            // second, get second member and get its size (the first member is a head and it will show size of meta volume itself);
            // third, set this size as meta volume size in vipr volume
            ArrayList<CIMArgument> list = new ArrayList<CIMArgument>();
            CIMArgument<CIMObjectPath> volumeReference = new CIMArgument<CIMObjectPath>(SmisConstants.CP_THE_ELEMENT,
                    CIMDataType.getDataType(volumePath), volumePath);
            // set request type to "children"
            CIMArgument<UnsignedInteger16> requestType = new CIMArgument<UnsignedInteger16>("RequestType", CIMDataType.UINT16_T,
                    new UnsignedInteger16(2));
            list.add(volumeReference);
            list.add(requestType);
            CIMArgument[] inArgs = {};
            inArgs = list.toArray(inArgs);

            CIMArgument[] outArgs = new CIMArgument[5];
            CIMObjectPath elementCompositionServicePath = _cimPath.getElementCompositionSvcPath(storageSystem);

            SmisCommandHelper helper = jobContext.getSmisCommandHelper();
            StorageSystem forProvider = helper.getStorageSystemForProvider(storageSystem, volume);
            helper.invokeMethod(forProvider, elementCompositionServicePath,
                    "GetCompositeElements", inArgs, outArgs);

            // get member volumes from output
            CIMObjectPath[] metaMembersPaths = (CIMObjectPath[]) _cimPath.getFromOutputArgs(outArgs, "OutElements");
            // get meta member size. use second member --- the first member will show size of meta volume itself.
            CIMObjectPath metaMemberPath = metaMembersPaths[1];
            CIMInstance cimVolume = helper.getInstance(forProvider, metaMemberPath, false,
                    false, new String[] { SmisConstants.CP_CONSUMABLE_BLOCKS, SmisConstants.CP_BLOCK_SIZE });

            CIMProperty consumableBlocks = cimVolume.getProperty(SmisConstants.CP_CONSUMABLE_BLOCKS);
            CIMProperty blockSize = cimVolume.getProperty(SmisConstants.CP_BLOCK_SIZE);
            // calculate size = consumableBlocks * block size
            Long size =
                    Long.valueOf(consumableBlocks.getValue().toString()) * Long.valueOf(blockSize.getValue().toString());

            // set member size for meta volume (required for volume expansion)
            volume.setMetaMemberSize(size);
            _log.info(String.format("Meta member info: blocks --- %s, block size --- %s, size --- %s .", consumableBlocks.getValue()
                    .toString(),
                    blockSize.getValue().toString(), size));
        }

        specificProcessing(storageSystem, dbClient, client, volume, volumeInstance, volumePath);
        dbClient.updateObject(volume);
        if (logMsgBuilder.length() != 0) {
            logMsgBuilder.append("\n");
        }
        logMsgBuilder.append(String.format(
                "Created volume successfully .. NativeId: %s, URI: %s", nativeID,
                getTaskCompleter().getId()));
    }

    /**
     * This method saves the native ID info and creation time for the volume object. The native ID is the key
     * identifier for the volume instance on the SMI-S side. We need to immediately persist it, so that if
     * when further post-processing of the volume encounters some error, we would be able to have some
     * reference to the volume and we could attempt to delete it.
     *
     * @param volumeID - [IN] URI of Volume
     */
    private void persistVolumeNativeID(DbClient dbClient, URI volumeId, String nativeID, Calendar creationTime) throws IOException {
        Volume volume = dbClient.queryObject(Volume.class, volumeId);
        volume.setCreationTime(creationTime);
        volume.setNativeId(nativeID);
        volume.setNativeGuid(NativeGUIDGenerator.generateNativeGuid(dbClient, volume));
        dbClient.updateObject(volume);
    }

    /**
     * This is common volume attribute update code. Consider this the place to make updates
     * for the Bourne's volume object based on the references to the array based object
     * irrespective of what type of volume creation it is.
     * 
     * @param dbClient [in] - Client for reading/writing from/to database.
     * @param client [in] - WBEMClient for accessing SMI-S provider data
     * @param volume [in] - Reference to Bourne's Volume object
     * @param volumePath [in] - Name reference to the SMI-S side volume object
     * @param nativeID [in] - NativeID extracted from create, will be set on volume
     * @param creationTime [in] - Create time of the volume, will be set on volume
     * @return CIMInstance - Reference to SMI-S side volume that can be used to retrieve
     *         data about the volume from the array side.
     */
    private CIMInstance commonVolumeUpdate(DbClient dbClient, WBEMClient client, Volume volume, CIMObjectPath volumePath) {
        CIMInstance volumeInstance = null;
        try {
            volumeInstance = client.getInstance(volumePath, true, false, null);
            if (volumeInstance != null) {
                String alternateName = CIMPropertyFactory.getPropertyValue(volumeInstance, SmisConstants.CP_NAME);
                volume.setAlternateName(alternateName);
                String wwn = CIMPropertyFactory.getPropertyValue(volumeInstance, SmisConstants.CP_WWN_NAME);
                volume.setWWN(wwn.toUpperCase());
                volume.setProvisionedCapacity(getProvisionedCapacityInformation(client, volumeInstance));
                volume.setAllocatedCapacity(getAllocatedCapacityInformation(client, volumeInstance));
                String accessState = CIMPropertyFactory.getPropertyValue(volumeInstance, SmisConstants.CP_ACCESS);
                String[] statusDescriptions = CIMPropertyFactory.getPropertyArray(volumeInstance, SmisConstants.CP_STATUS_DESCRIPTIONS);
                // Look for NOT_READY in status descriptions
                List<String> statusDescriptionList = Arrays.asList(statusDescriptions);
                // If this volume is managed by RP, RP owns the volume access field.
                if (!volume.checkForRp()) {
                    volume.setAccessState(SmisUtils.generateAccessState(accessState, statusDescriptionList));
                }
            }

            volume.setInactive(false);
        } catch (Exception e) {
            _log.error("Caught an exception while trying to update volume attributes", e);
            // If we could not get the common attributes, for whatever reason, we will mark it as a non-retryable failure
            setPostProcessingFailedStatus("Caught an exception while trying to update volume attributes: " + e.getMessage());
        }
        return volumeInstance;
    }

    /**
     * 
     * This method will redirect to the appropriate SmiStorageDevice object to make the
     * call to add the volumes to the consistency group. This operation should be done
     * after the volumes has been successfully created (i.e., there's a deviceNativeId
     * for the volumes).
     * 
     * @param jobContext [required] - JobContext object
     * @param volumesIds [required] - Volumes to add
     * @throws DeviceControllerException
     */
    private void addVolumesToConsistencyGroup(JobContext jobContext, List<URI> volumesIds) throws DeviceControllerException {
        if (volumesIds == null || volumesIds.isEmpty()) {
            return;
        }

        try {
            final DbClient dbClient = jobContext.getDbClient();

            // Get volumes from database
            final List<Volume> volumes = dbClient.queryObject(Volume.class, volumesIds);

            // All the volumes will be in the same consistency group
            final URI consistencyGroupId = volumes.get(0).getConsistencyGroup();

            BlockConsistencyGroup consistencyGroup = null;
            if (consistencyGroupId != null) {
                // Get consistency group and storage system from database
                consistencyGroup = dbClient
                        .queryObject(BlockConsistencyGroup.class, consistencyGroupId);
            }

            if (consistencyGroup == null) {
                _log.info(String.format("Skipping step addVolumesToConsistencyGroup: volumes %s do not reference a consistency group.",
                        volumesIds.toString()));
                return;
            } else {
                for (Volume volume : volumes) {
                    String cgName =
                            consistencyGroup.getCgNameOnStorageSystem(volume.getStorageController());
                    if (cgName == null) {
                        _log.info(String.format(
                                "Skipping step addVolumesToConsistencyGroup: Volume %s (%s) does not reference an existing consistency group on array %s.",
                                volume.getLabel(), volume.getId(), volume.getStorageController()));
                        return;
                    }
                }
            }

            final StorageSystem storage = dbClient.queryObject(StorageSystem.class,
                    getStorageSystemURI());

            final SmisStorageDevice storageDevice = (SmisStorageDevice) ControllerServiceImpl.
                    getBean(SmisCommandHelper.getSmisStorageDeviceName(storage));

            // Add all the new volumes to the consistency group except for RP+VPlex target/journal backing volumes
            List<URI> updatedVolumeIds = new ArrayList<URI>();

            for (URI volumeId : volumesIds) {
                Volume volume = dbClient.queryObject(Volume.class, volumeId);

                if (!RPHelper.isAssociatedToRpVplexType(volume, dbClient, PersonalityTypes.TARGET, PersonalityTypes.METADATA)) {
                    updatedVolumeIds.add(volumeId);
                }
            }

            if (updatedVolumeIds.isEmpty()) {
                _log.info("Skipping step addVolumesToConsistencyGroup: Volumes are not part of a consistency group");
                return;
            }

            storageDevice.addVolumesToConsistencyGroup(storage, consistencyGroup, volumes, getTaskCompleter());
        } catch (Exception e) {
            _log.error("Problem making SMI-S call: ", e);
            ServiceError error = DeviceControllerErrors.smis.unableToCallStorageProvider(e.getMessage());
            getTaskCompleter().error(jobContext.getDbClient(), error);
        }
    }
}
