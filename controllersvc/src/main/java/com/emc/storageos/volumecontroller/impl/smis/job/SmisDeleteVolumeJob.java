/*
 * Copyright (c) 2012 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.volumecontroller.impl.smis.job;

import java.net.URI;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import javax.cim.CIMObjectPath;
import javax.wbem.client.WBEMClient;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.emc.storageos.db.client.DbClient;
import com.emc.storageos.db.client.model.NamedURI;
import com.emc.storageos.db.client.model.Volume;
import com.emc.storageos.db.client.util.NullColumnValueGetter;
import com.emc.storageos.volumecontroller.JobContext;
import com.emc.storageos.volumecontroller.TaskCompleter;
import com.emc.storageos.volumecontroller.impl.smis.CIMConnectionFactory;
import com.emc.storageos.volumecontroller.impl.smis.SmisUtils;

/**
 * A VMAX Volume Delete job
 */
@SuppressWarnings("serial")
public class SmisDeleteVolumeJob extends SmisJob
{
    private static final Logger _log = LoggerFactory.getLogger(SmisDeleteVolumeJob.class);

    public SmisDeleteVolumeJob(CIMObjectPath cimJob,
            URI storageSystem,
            TaskCompleter taskCompleter) {
        super(cimJob, storageSystem, taskCompleter, "DeleteVolume");
    }

    /**
     * Called to update the job status when the volume delete job completes.
     * 
     * @param jobContext The job context.
     */
    public void updateStatus(JobContext jobContext) throws Exception {
        DbClient dbClient = jobContext.getDbClient();
        JobStatus jobStatus = getJobStatus();
        try {
            if (jobStatus == JobStatus.IN_PROGRESS) {
                return;
            }

            CIMConnectionFactory cimConnectionFactory = jobContext.getCimConnectionFactory();
            WBEMClient client = getWBEMClient(dbClient, cimConnectionFactory);

            // Get list of volumes; get set of storage pool ids to which they belong.
            List<Volume> volumes = new ArrayList<Volume>();
            Set<URI> poolURIs = new HashSet<URI>();
            for (URI id : getTaskCompleter().getIds()) {
                Volume volume = dbClient.queryObject(Volume.class, id);
                if (volume != null && !volume.getInactive()) {
                    volumes.add(volume);
                    poolURIs.add(volume.getPool());
                }
            }

            // If terminal job state update storage pool capacity
            if (jobStatus == JobStatus.SUCCESS || jobStatus == JobStatus.FAILED || jobStatus == JobStatus.FATAL_ERROR) {
                // Update capacity of storage pools.
                for (URI poolURI : poolURIs) {
                    SmisUtils.updateStoragePoolCapacity(dbClient, client, poolURI);
                }
            }

            StringBuilder logMsgBuilder = new StringBuilder();
            if (jobStatus == JobStatus.SUCCESS) {
                for (Volume volume : volumes) {
                    if (logMsgBuilder.length() != 0) {
                        logMsgBuilder.append("\n");
                    }
                    logMsgBuilder.append(String.format("Successfully deleted volume %s", volume.getId()));
                }
            } else if (jobStatus == JobStatus.FAILED || jobStatus == JobStatus.FATAL_ERROR) {
                for (URI id : getTaskCompleter().getIds()) {
                    if (logMsgBuilder.length() != 0) {
                        logMsgBuilder.append("\n");
                    }
                    logMsgBuilder.append(String.format("Failed to delete volume: %s", id));
                }
                // if SRDF Protected Volume, then change it to a normal device.
                // in case of array locks, target volume deletions fail some times.
                // This fix, converts a RDF device to non-rdf device in ViPr, so that this volume is exposed to UI for deletion again.
                for (Volume volume : volumes) {
                    if (volume.checkForSRDF()) {
                        volume.setPersonality(NullColumnValueGetter.getNullStr());
                        volume.setAccessState(Volume.VolumeAccessState.READWRITE.name());
                        volume.setLinkStatus(NullColumnValueGetter.getNullStr());
                        if (!NullColumnValueGetter.isNullNamedURI(volume.getSrdfParent())) {
                            volume.setSrdfParent(new NamedURI(NullColumnValueGetter.getNullURI(), NullColumnValueGetter.getNullStr()));
                            volume.setSrdfCopyMode(NullColumnValueGetter.getNullStr());
                            volume.setSrdfGroup(NullColumnValueGetter.getNullURI());
                        } else if (null != volume.getSrdfTargets()) {
                            volume.getSrdfTargets().clear();
                        }
                    }
                }
                dbClient.updateObject(volumes);
            }
            if (logMsgBuilder.length() > 0) {
                _log.info(logMsgBuilder.toString());
            }
        } catch (Exception e) {
            setPostProcessingErrorStatus("Encountered an internal error during delete volume job status processing: " + e.getMessage());
            _log.error("Caught exception while handling updateStatus for delete volume job.", e);
        } finally {
            super.updateStatus(jobContext);
        }
    }

}