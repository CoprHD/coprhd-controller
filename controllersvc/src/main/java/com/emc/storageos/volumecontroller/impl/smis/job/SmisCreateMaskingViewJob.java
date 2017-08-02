/*
 * Copyright (c) 2015 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.volumecontroller.impl.smis.job;

import static com.emc.storageos.volumecontroller.impl.smis.SmisConstants.SYMM_LUNMASKINGVIEW;

import java.net.URI;
import java.util.ArrayList;
import java.util.List;

import javax.cim.CIMInstance;
import javax.cim.CIMObjectPath;
import javax.cim.CIMProperty;
import javax.wbem.CloseableIterator;
import javax.wbem.WBEMException;
import javax.wbem.client.WBEMClient;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.emc.storageos.cimadapter.connections.cim.CimConnection;
import com.emc.storageos.db.client.DbClient;
import com.emc.storageos.db.client.URIUtil;
import com.emc.storageos.db.client.model.BlockObject;
import com.emc.storageos.db.client.model.BlockSnapshot;
import com.emc.storageos.db.client.model.ExportMask;
import com.emc.storageos.db.client.model.StorageSystem;
import com.emc.storageos.db.client.model.Volume;
import com.emc.storageos.db.exceptions.DatabaseException;
import com.emc.storageos.volumecontroller.JobContext;
import com.emc.storageos.volumecontroller.TaskCompleter;
import com.emc.storageos.volumecontroller.impl.ControllerServiceImpl;
import com.emc.storageos.volumecontroller.impl.VolumeURIHLU;
import com.emc.storageos.volumecontroller.impl.smis.CIMConnectionFactory;
import com.emc.storageos.volumecontroller.impl.smis.CIMPropertyFactory;
import com.emc.storageos.volumecontroller.impl.smis.ExportMaskOperationsHelper;
import com.emc.storageos.volumecontroller.impl.smis.SmisCommandHelper;
import com.emc.storageos.volumecontroller.impl.smis.SmisConstants;
import com.emc.storageos.volumecontroller.impl.smis.vmax.VmaxExportOperationContext;
import com.emc.storageos.volumecontroller.impl.utils.ExportOperationContext;

/**
 * A VMAX Create Masking View job
 */
public class SmisCreateMaskingViewJob extends SmisJob
{
    private static final Logger _log = LoggerFactory.getLogger(SmisCreateMaskingViewJob.class);
    private final URI _exportMaskURI;
    private VolumeURIHLU[] _volumeURIHLUs;
    private final CIMObjectPath _deviceGroupMaskingPath;

    public SmisCreateMaskingViewJob(CIMObjectPath cimJob,
            URI storageSystem,
            URI exportMaskURI,
            VolumeURIHLU[] volumeURIHLUs,
            CIMObjectPath deviceGroupMaskingPath,
            TaskCompleter taskCompleter) {
        super(cimJob, storageSystem, taskCompleter, "CreateMaskingView");
        _exportMaskURI = exportMaskURI;
        if (volumeURIHLUs != null) {
            _volumeURIHLUs = volumeURIHLUs.clone();
        }
        _deviceGroupMaskingPath = deviceGroupMaskingPath;
    }

    @Override
    public void updateStatus(JobContext jobContext) throws Exception {
        CloseableIterator<CIMObjectPath> iterator = null;
        DbClient dbClient = jobContext.getDbClient();
        JobStatus jobStatus = getJobStatus();
        _log.info("Updating status of SmisCreateMaskingViewJob");
        try {
            if (jobStatus == JobStatus.SUCCESS) {
                StorageSystem storageSystem = dbClient.queryObject(StorageSystem.class, getStorageSystemURI());
                CimConnection cimConnection = jobContext.getCimConnectionFactory().getConnection(storageSystem);
                CIMConnectionFactory cimConnectionFactory = jobContext.getCimConnectionFactory();
                WBEMClient client = getWBEMClient(dbClient, cimConnectionFactory);
                List<CIMObjectPath> maskingViews = new ArrayList<CIMObjectPath>();
                iterator = client.associatorNames(getCimJob(), null, SYMM_LUNMASKINGVIEW, null, null);
                while (iterator.hasNext()) {
                    CIMObjectPath cimObjectPath = iterator.next();
                    maskingViews.add(cimObjectPath);
                    ExportMask mask =
                            dbClient.queryObject(ExportMask.class, _exportMaskURI);
                    // Capture the device ID associated with the masking view. This is
                    // necessary for future reference to the MV.
                    if (mask != null) {
                        String deviceId =
                                cimObjectPath.getKey(SmisConstants.CP_DEVICE_ID)
                                        .getValue().toString();
                        mask.setNativeId(deviceId);
                        dbClient.persistObject(mask);
                        ExportOperationContext.insertContextOperation(getTaskCompleter(),
                                VmaxExportOperationContext.OPERATION_CREATE_MASKING_VIEW, mask.getMaskName());
                    }
                }

                // Now perform RP protection tagging, if required, on the storage group
                if (storageSystem.getUsingSmis80()) {
                    _log.info("Set RP tag on all volumes within SG for 8.0.3 Providers");
                    enableRecoverPointTagOn803(dbClient, client, storageSystem, jobContext);
                } else {
                    _log.info("Set RP tag on SG for 4.6.2 Providers");
                    enableRecoverPointTag(dbClient, client, _deviceGroupMaskingPath);
                }

                // Now set the HLU on the volume URIs, if they haven't been set by user.
                ExportMaskOperationsHelper.
                        setHLUFromProtocolControllers(dbClient, cimConnection, _exportMaskURI,
                                _volumeURIHLUs, maskingViews, getTaskCompleter());
            }
        } catch (Exception e) {
            _log.error("Caught an exception while trying to updateStatus for SmisCreateMaskingViewJob", e);
            setPostProcessingErrorStatus("Encountered an internal error during masking view create job status processing : "
                    + e.getMessage());
        } finally {
            super.updateStatus(jobContext);
            if (iterator != null) {
                iterator.close();
            }
        }
    }

    /**
     * Method will set the EMCRecoverPointEnabled flag on the device masking group for VMAX.
     *
     * @param dbClient [in] - Client instance for reading/writing from/to DB
     * @param client [in] - WBEMClient used for reading/writing from/to SMI-S
     * @param deviceGroupPath [in] - CIMObjectPath referencing the volume
     */
    private void enableRecoverPointTag(DbClient dbClient, WBEMClient client, CIMObjectPath deviceGroupPath) {
        try {
            boolean isRPTagNeeded = false;

            // Check if the volumes being protected are RP volumes
            for (VolumeURIHLU volUriHlu : _volumeURIHLUs) {
                URI volumeURI = volUriHlu.getVolumeURI();

                BlockObject bo = null;

                if (URIUtil.isType(volumeURI, BlockSnapshot.class)) {
                    bo = dbClient.queryObject(BlockSnapshot.class, volumeURI);
                } else if (URIUtil.isType(volumeURI, Volume.class)) {
                    bo = dbClient.queryObject(Volume.class, volumeURI);
                }

                if (bo != null && BlockObject.checkForRP(dbClient, bo.getId())) {
                    isRPTagNeeded = true;
                    break;
                }
            }

            // Do nothing and return from if none of the volumes are RP protected
            if (isRPTagNeeded) {
                _log.info("Attempting to enable RecoverPoint tag on Device Group : " + deviceGroupPath.toString());
                CIMPropertyFactory factoryRef = (CIMPropertyFactory) ControllerServiceImpl.getBean("CIMPropertyFactory");
                CIMInstance toUpdate = new CIMInstance(deviceGroupPath,
                        new CIMProperty[] {
                                factoryRef.bool(SmisConstants.EMC_RECOVERPOINT_ENABLED, true)
                        });

                _log.debug("Params: " + toUpdate.toString());
                client.modifyInstance(toUpdate, SmisConstants.CP_EMC_RECOVERPOINT_ENABLED);
                _log.info(String.format("Device group has been successfully set with RecoverPoint tag "));
            }
        } catch (WBEMException e) {
            _log.error("Encountered an error while trying to set the RecoverPoint tag", e);
        } catch (DatabaseException e) {
            _log.error("Encountered an error while trying to set the RecoverPoint tag", e);
        }
    }

    /**
     * Method will set the EMCRecoverPointEnabled flag on all the volumes within 8.0.3 Provider.
     * 8.0.3 Provider doesnt support setting RP tag on Storage Groups.
     *
     * @param dbClient [in] - Client instance for reading/writing from/to DB
     * @param client [in] - WBEMClient used for reading/writing from/to SMI-S
     * @param deviceGroupPath [in] - CIMObjectPath referencing the volume
     */
    private void enableRecoverPointTagOn803(DbClient dbClient, WBEMClient client, StorageSystem storage,
            JobContext jobContext) {
        try {
            boolean isRPTagNeeded = false;
            List<URI> blockObjectUris = new ArrayList<URI>();

            // Check if the volumes being protected are RP volumes

            for (VolumeURIHLU volUriHlu : _volumeURIHLUs) {
                URI volumeURI = volUriHlu.getVolumeURI();

                BlockObject bo = null;

                if (URIUtil.isType(volumeURI, BlockSnapshot.class)) {
                    bo = dbClient.queryObject(BlockSnapshot.class, volumeURI);
                } else if (URIUtil.isType(volumeURI, Volume.class)) {
                    bo = dbClient.queryObject(Volume.class, volumeURI);
                }

                if (bo != null) {
                    blockObjectUris.add(bo.getId());

                    if (BlockObject.checkForRP(dbClient, bo.getId())) {
                        isRPTagNeeded = true;
                    }
                }
            }

            // Do nothing and return from if none of the volumes are RP protected
            if (isRPTagNeeded) {
                SmisCommandHelper helper = jobContext.getSmisCommandHelper();
                helper.setRecoverPointTag(storage, helper.getVolumeMembers(storage, blockObjectUris), true);
            }
        } catch (WBEMException e) {
            _log.error("Encountered an error while trying to set the RecoverPoint tag", e);
        } catch (DatabaseException e) {
            _log.error("Encountered an error while trying to set the RecoverPoint tag", e);
        } catch (Exception e) {
            _log.error("Encountered an error while trying to set the RecoverPoint tag", e);
        }
    }

}