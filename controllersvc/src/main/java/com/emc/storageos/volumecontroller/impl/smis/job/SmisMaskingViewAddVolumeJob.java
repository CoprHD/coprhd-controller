/*
 * Copyright (c) 2015 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.volumecontroller.impl.smis.job;

import java.net.URI;
import java.util.ArrayList;
import java.util.List;

import javax.cim.CIMObjectPath;
import javax.wbem.WBEMException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.emc.storageos.cimadapter.connections.cim.CimConnection;
import com.emc.storageos.db.client.DbClient;
import com.emc.storageos.db.client.model.BlockObject;
import com.emc.storageos.db.client.model.StorageSystem;
import com.emc.storageos.db.client.model.Volume;
import com.emc.storageos.exceptions.DeviceControllerException;
import com.emc.storageos.svcs.errorhandling.model.ServiceError;
import com.emc.storageos.volumecontroller.JobContext;
import com.emc.storageos.volumecontroller.TaskCompleter;
import com.emc.storageos.volumecontroller.impl.VolumeURIHLU;
import com.emc.storageos.volumecontroller.impl.smis.CIMObjectPathFactory;
import com.emc.storageos.volumecontroller.impl.smis.ExportMaskOperationsHelper;
import com.emc.storageos.volumecontroller.impl.smis.SmisCommandHelper;
import com.emc.storageos.volumecontroller.impl.utils.ExportMaskUtils;

/**
 * A VMAX Add Volume job
 */
public class SmisMaskingViewAddVolumeJob extends SmisJob
{
    private static final Logger _log = LoggerFactory.getLogger(SmisCreateMaskingViewJob.class);
    private final URI _exportMaskURI;
    private VolumeURIHLU[] _volumeURIHLUs;
    private CIMObjectPathFactory _cimPath;
    private final CIMObjectPath _newCreatedGroup;  // newly created storage group path.

    public SmisMaskingViewAddVolumeJob(CIMObjectPath cimJob,
            URI storageSystem,
            URI exportMaskURI,
            VolumeURIHLU[] volumeURIHLUs,
            CIMObjectPath newCreatedGroup,
            TaskCompleter taskCompleter) {
        super(cimJob, storageSystem, taskCompleter, "AddVolumeToMaskingView");
        _exportMaskURI = exportMaskURI;
        if (volumeURIHLUs != null) {
            _volumeURIHLUs = volumeURIHLUs.clone();
        }
        _newCreatedGroup = newCreatedGroup;
    }

    public void setCIMObjectPathfactory(CIMObjectPathFactory cimPath) {
        _cimPath = cimPath;
    }

    @Override
    public void updateStatus(JobContext jobContext) throws Exception {
        DbClient dbClient = jobContext.getDbClient();
        JobStatus jobStatus = getJobStatus();
        _log.info("Updating status of SmisMaskingViewAddVolumeJob");
        try {
            if (jobStatus == JobStatus.SUCCESS) {
                StorageSystem storageSystem = dbClient.queryObject(
                        StorageSystem.class, getStorageSystemURI());
                CimConnection cimConnection = jobContext
                        .getCimConnectionFactory().getConnection(storageSystem);
                List<URI> volumeUriList = new ArrayList<URI>();
                // Now perform RP protection tagging, if required for the
                // objects being added.
                SmisCommandHelper helper = jobContext.getSmisCommandHelper();
                for (VolumeURIHLU volumeUriHlu : _volumeURIHLUs) {
                    BlockObject bo = Volume.fetchExportMaskBlockObject(dbClient,
                            volumeUriHlu.getVolumeURI());
                    if (bo != null && bo instanceof Volume) {
                        Volume volume = (Volume) bo;
                        if (volume != null && volume.checkForRp()) {
                            List<CIMObjectPath> volumePathList = new ArrayList<CIMObjectPath>();
                            volumePathList.add(helper.getVolumeMember(storageSystem, volume));
                            helper.setRecoverPointTag(storageSystem, volumePathList, true);
                        }
                    }
                    volumeUriList.add(volumeUriHlu.getVolumeURI());
                }

                // update Host IO Limit properties for child storage group if applicable.
                // NOTE: this need to be done after addGroupsToCascadedVolumeGroup, because the child groups must need to be associated to a
                // parent
                // for proper roll back , that is volume removal, if exception is thrown during update
                if (_newCreatedGroup != null) {
                    helper.setHostIOLimits(cimConnection.getCimClient(), _newCreatedGroup, _volumeURIHLUs);
                }

                String[] volumeNames = ExportMaskUtils.getBlockObjectAlternateNames(volumeUriList, dbClient);
                CIMObjectPath[] volumes = _cimPath.getVolumePaths(storageSystem, volumeNames);

                _log.info("{} volumes processed for HLU updation", volumes.length);
                // Now set the HLU on the volume URIs, if they haven't been set// by user.
                ExportMaskOperationsHelper.
                        setHLUFromProtocolControllersOnAddVolume(dbClient, cimConnection, _exportMaskURI, _volumeURIHLUs,
                                volumes, getTaskCompleter());
            }
        } catch (WBEMException e) {
            _log.error(String.format("updateHostIOLimits failed - new created group: %s", _newCreatedGroup.toString()), e);
            ServiceError serviceError = DeviceControllerException.errors.jobFailed(e);
            getTaskCompleter().error(dbClient, serviceError);
        } catch (Exception e) {
            _log.error("Caught an exception while trying to updateStatus for SmisMaskingViewAddVolumeJob", e);
            setPostProcessingErrorStatus("Encountered an internal error during add volume to masking view job status processing : "
                    + e.getMessage());
        } finally {
            super.updateStatus(jobContext);
        }
    }

}