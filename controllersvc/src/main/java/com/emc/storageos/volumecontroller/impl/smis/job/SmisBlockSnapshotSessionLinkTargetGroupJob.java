package com.emc.storageos.volumecontroller.impl.smis.job;

import com.emc.storageos.db.client.DbClient;
import com.emc.storageos.db.client.model.BlockSnapshot;
import com.emc.storageos.volumecontroller.JobContext;
import com.emc.storageos.volumecontroller.TaskCompleter;
import com.emc.storageos.volumecontroller.impl.smis.CIMConnectionFactory;
import com.emc.storageos.volumecontroller.impl.smis.SmisConstants;
import com.google.common.collect.Iterators;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.cim.CIMObjectPath;
import javax.wbem.CloseableIterator;
import javax.wbem.WBEMException;
import javax.wbem.client.WBEMClient;
import java.net.URI;

/**
 *
 * ViPR Job created when an underlying CIM job is created to create
 * and link a new target volume group to a group snapshot point-in-time
 * copy represented in ViPR by a BlockSnapshotSession instance.
 *
 * @author Ian Bibby
 */
public class SmisBlockSnapshotSessionLinkTargetGroupJob extends SmisSnapShotJob {

    private static final Logger log = LoggerFactory.getLogger(SmisBlockSnapshotSessionLinkTargetGroupJob.class);
    private static final String JOB_NAME = SmisBlockSnapshotSessionLinkTargetGroupJob.class.getSimpleName();

    public SmisBlockSnapshotSessionLinkTargetGroupJob(CIMObjectPath cimJob, URI storageSystem, TaskCompleter taskCompleter) {
        super(cimJob, storageSystem, taskCompleter, JOB_NAME);
    }

    @Override
    public void updateStatus(JobContext jobContext) throws Exception {
        JobStatus jobStatus = getJobStatus();
        CloseableIterator<CIMObjectPath> repGrpIter = null;
        try {
            DbClient dbClient = jobContext.getDbClient();
            TaskCompleter completer = getTaskCompleter();
            BlockSnapshot snapshot = dbClient.queryObject(BlockSnapshot.class, completer.getId());
            if (jobStatus == JobStatus.IN_PROGRESS) {
                return;
            }

            CIMConnectionFactory cimConnectionFactory = jobContext.getCimConnectionFactory();
            WBEMClient client = getWBEMClient(dbClient, cimConnectionFactory);

            CIMObjectPath targetRepGrpPath = getAssociatedTargetReplicationGroupPath(client);
            log.info("Processing target replication group: {}", targetRepGrpPath);
            CIMObjectPath groupSynchronized = getAssociatedGroupSynchronized(client, targetRepGrpPath);
            log.info("Processing group synchronized instance: {}", groupSynchronized);

            /*
             * TODO
             * Determine the underlying StorageSynchronized instances.  Update the BlockSnapshot instances
             * with their respective nativeIds.
            */
        } catch (Exception e) {
            setPostProcessingErrorStatus("Internal error in link snapshot session target group job status processing: "
                    + e.getMessage());
            log.error("Internal error in link snapshot session target group job status processing", e);
        } finally {
            if (repGrpIter != null) {
                repGrpIter.close();
            }
            super.updateStatus(jobContext);
        }
    }

    private CIMObjectPath getAssociatedGroupSynchronized(WBEMClient client, CIMObjectPath targetRepGrpPath) throws WBEMException {
        CloseableIterator<CIMObjectPath> it = null;
        try {
            it = client.associatorNames(targetRepGrpPath, null, SmisConstants.SE_GROUP_SYNCHRONIZED_RG_RG, null, null);
            if (it.hasNext()) {
                // TODO Ensure the system element is the intended source group path.
                return it.next();
            }
        } finally {
            if (it != null) {
                it.close();
            }
        }
        throw new IllegalStateException("Expected a group synchronized instance but found none");
    }

    private CIMObjectPath getAssociatedTargetReplicationGroupPath(WBEMClient client) throws WBEMException {
        CloseableIterator<CIMObjectPath> it = null;
        try {
            it = client.associatorNames(getCimJob(), null, SmisConstants.SE_REPLICATION_GROUP, null, null);
            if (it.hasNext()) {
                return it.next();
            }
        } finally {
            if (it != null) {
                it.close();
            }
        }
        throw new IllegalStateException("Expected a single target replication group but found none");
    }
}
