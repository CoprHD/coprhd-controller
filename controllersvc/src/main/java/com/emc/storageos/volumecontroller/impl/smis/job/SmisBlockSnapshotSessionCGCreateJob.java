package com.emc.storageos.volumecontroller.impl.smis.job;

import com.emc.storageos.db.client.DbClient;
import com.emc.storageos.db.client.model.BlockSnapshotSession;
import com.emc.storageos.plugins.common.Constants;
import com.emc.storageos.volumecontroller.JobContext;
import com.emc.storageos.volumecontroller.TaskCompleter;
import com.emc.storageos.volumecontroller.impl.smis.CIMConnectionFactory;
import com.emc.storageos.volumecontroller.impl.smis.SmisConstants;
import com.google.common.collect.Lists;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.cim.CIMObjectPath;
import javax.wbem.CloseableIterator;
import javax.wbem.client.WBEMClient;
import java.net.URI;
import java.util.ArrayList;
import java.util.Iterator;

/**
 * ViPR Job created when an underlying CIM job is created to create
 * a new array group snapshot point-in-time copy represented in ViPR by a
 * BlockSnapshotSession instance.
 *
 * @author Ian Bibby
 */
public class SmisBlockSnapshotSessionCGCreateJob extends SmisJob {

    private static Logger log = LoggerFactory.getLogger(SmisBlockSnapshotSessionCGCreateJob.class);
    private static String JOB_NAME = "SmisBlockSnapshotSessionCGCreateJob";

    public SmisBlockSnapshotSessionCGCreateJob(CIMObjectPath cimJob, URI storageSystem, TaskCompleter taskCompleter) {
        super(cimJob, storageSystem, taskCompleter, JOB_NAME);
    }

    @Override
    public void updateStatus(JobContext jobContext) throws Exception {
        JobStatus jobStatus = getJobStatus();
        CloseableIterator<CIMObjectPath> syncAspectIter = null;
        CloseableIterator<CIMObjectPath> settingsStateIter = null;
        try {
            DbClient dbClient = jobContext.getDbClient();
            if (jobStatus == JobStatus.IN_PROGRESS) {
                return;
            }
            if (jobStatus == JobStatus.SUCCESS) {
                log.info("Post-processing successful snapshot session group creation for task ", getTaskCompleter().getOpId());

                // Get the snapshot sessions.
                Iterator<BlockSnapshotSession> iterator = dbClient.queryIterativeObjects(BlockSnapshotSession.class,
                        getTaskCompleter().getIds(), true);
                ArrayList<BlockSnapshotSession> snapSessions = Lists.newArrayList(iterator);

                // Update Settings instance for the session.
                CIMConnectionFactory cimConnectionFactory = jobContext.getCimConnectionFactory();
                WBEMClient client = getWBEMClient(dbClient, cimConnectionFactory);
                syncAspectIter = client.associatorNames(getCimJob(), null,
                        SmisConstants.SYMM_SYNCHRONIZATION_ASPECT_FOR_SOURCE_GROUP, null, null);

                if (syncAspectIter.hasNext()) {
                    CIMObjectPath syncAspectPath = syncAspectIter.next();
                    String instanceId = syncAspectPath.getKeyValue(Constants.INSTANCEID).toString();
                    log.info("SynchronizationAspectForSourceGroup instance id is {}", instanceId);
                    for (BlockSnapshotSession snapSession : snapSessions) {
                        snapSession.setSessionInstance(instanceId);
                    }
                    dbClient.updateObject(snapSessions);
                }
            } else if (jobStatus == JobStatus.FAILED || jobStatus == JobStatus.FATAL_ERROR) {
                log.info("Failed to create snapshot session for task ", getTaskCompleter().getOpId());
            }
        } catch (Exception e) {
            setPostProcessingErrorStatus("Encountered an internal error in create snapshot session job status processing: "
                    + e.getMessage());
            log.error("Encountered an internal error in create snapshot session job status processing", e);
        } finally {
            if (syncAspectIter != null) {
                syncAspectIter.close();
            }
            if (settingsStateIter != null) {
                settingsStateIter.close();
            }
            super.updateStatus(jobContext);
        }
    }
}
