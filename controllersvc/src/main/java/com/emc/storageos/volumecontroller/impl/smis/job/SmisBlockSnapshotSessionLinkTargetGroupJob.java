package com.emc.storageos.volumecontroller.impl.smis.job;

import com.emc.storageos.db.client.DbClient;
import com.emc.storageos.db.client.model.BlockObject;
import com.emc.storageos.db.client.model.BlockSnapshot;
import com.emc.storageos.db.client.model.StorageSystem;
import com.emc.storageos.volumecontroller.JobContext;
import com.emc.storageos.volumecontroller.TaskCompleter;
import com.emc.storageos.volumecontroller.impl.NativeGUIDGenerator;
import com.emc.storageos.volumecontroller.impl.smis.CIMConnectionFactory;
import com.emc.storageos.volumecontroller.impl.smis.CIMPropertyFactory;
import com.emc.storageos.volumecontroller.impl.smis.SmisConstants;
import com.google.common.collect.Lists;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.cim.CIMInstance;
import javax.cim.CIMObjectPath;
import javax.wbem.CloseableIterator;
import javax.wbem.WBEMException;
import javax.wbem.client.WBEMClient;
import java.net.URI;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import static com.emc.storageos.volumecontroller.impl.smis.SmisConstants.CP_DEVICE_ID;
import static com.emc.storageos.volumecontroller.impl.smis.SmisConstants.CP_EMC_RG_SOURCE_INSTANCE_ID;
import static com.emc.storageos.volumecontroller.impl.smis.SmisConstants.CP_EMC_RG_TARGET_INSTANCE_ID;
import static com.emc.storageos.volumecontroller.impl.smis.SmisConstants.CP_SV_SOURCE_DEVICE_ID;
import static com.emc.storageos.volumecontroller.impl.smis.SmisConstants.CP_SV_TARGET_DEVICE_ID;
import static com.emc.storageos.volumecontroller.impl.smis.SmisConstants.PS_REPLICA_PAIR_VIEW;

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

    private Map<String, URI> nativeIdToSnapshotMap;
    private String sourceGroupName;
    private String targetGroupName;
    private String snapSessionInstance;

    public SmisBlockSnapshotSessionLinkTargetGroupJob(CIMObjectPath cimJob, URI storageSystem, TaskCompleter taskCompleter) {
        super(cimJob, storageSystem, taskCompleter, JOB_NAME);
    }

    public void setNativeIdToSnapshotMap(Map<String, URI> nativeIdToSnapshotMap) {
        this.nativeIdToSnapshotMap = nativeIdToSnapshotMap;
    }

    public void setSourceGroupName(String sourceGroupName) {
        this.sourceGroupName = sourceGroupName;
    }

    public void setTargetGroupName(String targetGroupName) {
        this.targetGroupName = targetGroupName;
    }

    public void setSnapSessionInstance(String snapSessionInstance) {
        this.snapSessionInstance = snapSessionInstance;
    }

    @Override
    public void updateStatus(JobContext jobContext) throws Exception {
        JobStatus jobStatus = getJobStatus();
        try {
            switch(jobStatus) {
                case IN_PROGRESS:
                    return;
                case SUCCESS:
                    processJobSuccess(jobContext);
                    break;
                case FAILED:
                case FATAL_ERROR:
                    processJobFailure(jobContext);
                    break;
            }
        } finally {
            super.updateStatus(jobContext);
        }
    }

    private void processJobSuccess(JobContext jobContext) throws Exception {
        DbClient dbClient = jobContext.getDbClient();
        try {
            CIMConnectionFactory cimConnectionFactory = jobContext.getCimConnectionFactory();
            WBEMClient client = getWBEMClient(dbClient, cimConnectionFactory);

            CIMObjectPath targetRepGrpPath = getAssociatedTargetReplicationGroupPath(client);
            log.info("Processing target replication group: {}", targetRepGrpPath);
            List<CIMObjectPath> replicaPairViews = getAssociatedReplicaPairViews(client, targetRepGrpPath);

            for (CIMObjectPath replicaPairViewPath : replicaPairViews) {
                log.info("Processing replica pair view instance: {}", replicaPairViewPath);
                CIMInstance replicaPairView = client.getInstance(replicaPairViewPath, false, false, PS_REPLICA_PAIR_VIEW);

                if (!replicaPairView.getPropertyValue(CP_EMC_RG_SOURCE_INSTANCE_ID).equals(sourceGroupName) ||
                        !replicaPairView.getPropertyValue(CP_EMC_RG_TARGET_INSTANCE_ID).equals(targetGroupName)) {
                    continue;
                }

                String srcIdProp = (String) replicaPairView.getPropertyValue(CP_SV_SOURCE_DEVICE_ID);
                String tgtIdProp = (String) replicaPairView.getPropertyValue(CP_SV_TARGET_DEVICE_ID);
                // Relies on the BlockSnapshot model being initially created with its nativeID set to its parents.
                if (nativeIdToSnapshotMap.containsKey(srcIdProp)) {
                    URI blockSnapshotURI = nativeIdToSnapshotMap.get(srcIdProp);
                    BlockSnapshot snapshot = dbClient.queryObject(BlockSnapshot.class, blockSnapshotURI);
                    BlockObject sourceObj = BlockObject.fetch(dbClient, snapshot.getParent().getURI());

                    CIMObjectPath volumePath = getAssociatedTargetVolume(client, replicaPairViewPath, tgtIdProp);
                    CIMInstance volume = client.getInstance(volumePath, false, false, null);

                    String volumeElementName = CIMPropertyFactory.getPropertyValue(volume, SmisConstants.CP_ELEMENT_NAME);
                    log.info("volumeElementName: {}", volumeElementName);
                    String volumeWWN = CIMPropertyFactory.getPropertyValue(volume, SmisConstants.CP_WWN_NAME);
                    log.info("volumeWWN: {}", volumeWWN);
                    String volumeAltName = CIMPropertyFactory.getPropertyValue(volume, SmisConstants.CP_NAME);
                    log.info("volumeAltName: {}", volumeAltName);
                    StorageSystem system = dbClient.queryObject(StorageSystem.class, getStorageSystemURI());
                    snapshot.setNativeId(tgtIdProp);
                    snapshot.setNativeGuid(NativeGUIDGenerator.generateNativeGuid(system, snapshot));
                    snapshot.setDeviceLabel(volumeElementName);
                    snapshot.setInactive(false);
                    snapshot.setIsSyncActive(Boolean.TRUE);
                    snapshot.setCreationTime(Calendar.getInstance());
                    snapshot.setWWN(volumeWWN.toUpperCase());
                    snapshot.setAlternateName(volumeAltName);
                    snapshot.setSettingsInstance(snapSessionInstance);
                    commonSnapshotUpdate(snapshot, volume, client, system, sourceObj.getNativeId(), tgtIdProp, false, dbClient);
                    log.info(String
                            .format("For target volume path %1$s, going to set blocksnapshot %2$s nativeId to %3$s (%4$s). Associated volume is %5$s (%6$s)",
                                    volumePath.toString(), snapshot.getId().toString(), tgtIdProp,
                                    volumeElementName, sourceObj.getNativeId(), sourceObj.getDeviceLabel()));

                    dbClient.updateObject(snapshot);
                }
            }
        } catch (Exception e) {
            setPostProcessingErrorStatus("Internal error in link snapshot session target group job status processing: "
                    + e.getMessage());
            log.error("Internal error in link snapshot session target group job status processing", e);
            throw e;
        }
    }

    private void processJobFailure(JobContext jobContext) {
        log.info("Failed to link target group {} to source snap session group {}", targetGroupName, sourceGroupName);
        DbClient dbClient = jobContext.getDbClient();
        Iterator<BlockSnapshot> iterator = dbClient.queryIterativeObjects(BlockSnapshot.class,
                nativeIdToSnapshotMap.values());
        ArrayList<BlockSnapshot> snapshots = Lists.newArrayList(iterator);
        for (BlockSnapshot snapshot : snapshots) {
            snapshot.setInactive(true);
        }
        dbClient.updateObject(snapshots);
    }

    private List<CIMObjectPath> getAssociatedReplicaPairViews(WBEMClient client, CIMObjectPath targetRepGrpPath) throws WBEMException {
        CloseableIterator<CIMObjectPath> it = null;
        List<CIMObjectPath> result = new ArrayList<>();
        try {
            it = client.associatorNames(targetRepGrpPath, null, SmisConstants.SE_REPLICA_PAIR_VIEW, null, null);
            while (it.hasNext()) {
                result.add(it.next());
            }
            return result;
        } finally {
            if (it != null) {
                it.close();
            }
        }
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

    private CIMObjectPath getAssociatedTargetVolume(WBEMClient client, CIMObjectPath replicaPairView,
                                                    String targetDeviceId) throws WBEMException {
        CloseableIterator<CIMObjectPath> it = null;
        try {
            it = client.associatorNames(replicaPairView, null, SmisConstants.CIM_STORAGE_VOLUME, null, null);
            if (it.hasNext()) {
                CIMObjectPath volume = it.next();
                String deviceID = (String) volume.getKeyValue(CP_DEVICE_ID);
                if (targetDeviceId.equals(deviceID)) {
                    return volume;
                }
            }
        } finally {
            if (it != null) {
                it.close();
            }
        }
        throw new IllegalStateException(
                String.format("Expected an associated volume with nativeID %s but found none", targetDeviceId));
    }
}
