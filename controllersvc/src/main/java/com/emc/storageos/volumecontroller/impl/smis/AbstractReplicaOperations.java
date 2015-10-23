/*
 * Copyright (c) 2015 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.volumecontroller.impl.smis;

import static com.emc.storageos.volumecontroller.impl.smis.ReplicationUtils.callEMCRefreshIfRequired;
import static com.emc.storageos.volumecontroller.impl.smis.SmisConstants.CREATE_LIST_REPLICA;
import static com.emc.storageos.volumecontroller.impl.smis.SmisConstants.JOB;
import static java.text.MessageFormat.format;

import java.net.URI;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.cim.CIMArgument;
import javax.cim.CIMInstance;
import javax.cim.CIMObjectPath;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.emc.storageos.db.client.DbClient;
import com.emc.storageos.db.client.URIUtil;
import com.emc.storageos.db.client.model.BlockMirror;
import com.emc.storageos.db.client.model.BlockObject;
import com.emc.storageos.db.client.model.BlockSnapshot;
import com.emc.storageos.db.client.model.StorageSystem;
import com.emc.storageos.db.client.model.Volume;
import com.emc.storageos.db.client.model.Volume.ReplicationState;
import com.emc.storageos.db.client.util.NameGenerator;
import com.emc.storageos.db.client.util.NullColumnValueGetter;
import com.emc.storageos.exceptions.DeviceControllerErrors;
import com.emc.storageos.exceptions.DeviceControllerException;
import com.emc.storageos.svcs.errorhandling.model.ServiceError;
import com.emc.storageos.volumecontroller.ReplicaOperations;
import com.emc.storageos.volumecontroller.TaskCompleter;
import com.emc.storageos.volumecontroller.impl.ControllerServiceImpl;
import com.emc.storageos.volumecontroller.impl.job.QueueJob;
import com.emc.storageos.volumecontroller.impl.smis.job.SmisCreateListReplicaJob;

public abstract class AbstractReplicaOperations implements ReplicaOperations {
    private static final Logger _log = LoggerFactory.getLogger(AbstractReplicaOperations.class);

    protected DbClient _dbClient;
    protected SmisCommandHelper _helper;
    protected CIMObjectPathFactory _cimPath;
    protected NameGenerator _nameGenerator;

    public void setCimObjectPathFactory(CIMObjectPathFactory cimObjectPathFactory) {
        _cimPath = cimObjectPathFactory;
    }

    public void setDbClient(DbClient dbClient) {
        _dbClient = dbClient;
    }

    public void setSmisCommandHelper(SmisCommandHelper smisCommandHelper) {
        _helper = smisCommandHelper;
    }

    public void setNameGenerator(NameGenerator nameGenerator) {
        _nameGenerator = nameGenerator;
    }

    @Override
    public void createListReplica(StorageSystem storage, List<URI> replicaList, Boolean createInactive,
            TaskCompleter taskCompleter) {
        _log.info("createListReplica operation START");

        try {
            List<String> sourceIds = new ArrayList<String>();
            List<String> labels = new ArrayList<String>();
            Map<String, URI> srcNativeIdToReplicaUriMap = new HashMap<String, URI>();
            for (URI replicaURI : replicaList) {
                BlockObject replica = BlockObject.fetch(_dbClient, replicaURI);
                labels.add(replica.getLabel());
                BlockObject source = _helper.getSource(replica);
                String sourceNativeId = source.getNativeId();
                sourceIds.add(sourceNativeId);
                srcNativeIdToReplicaUriMap.put(sourceNativeId, replica.getId());
            }

            int syncType = getSyncType(replicaList.get(0));
            CIMObjectPath[] sourceVolumePaths = _cimPath.getVolumePaths(storage, sourceIds.toArray(new String[sourceIds.size()]));
            CIMArgument[] inArgs = _helper.getCreateListReplicaInputArguments(storage, sourceVolumePaths, labels, syncType);
            CIMArgument[] outArgs = new CIMArgument[5];
            CIMObjectPath replicationSvc = _cimPath.getControllerReplicationSvcPath(storage);
            _helper.invokeMethod(storage, replicationSvc, CREATE_LIST_REPLICA, inArgs, outArgs);
            CIMObjectPath job = _cimPath.getCimObjectPathFromOutputArgs(outArgs, JOB);
            ControllerServiceImpl.enqueueJob(
                    new QueueJob(new SmisCreateListReplicaJob(job,
                            storage.getId(), srcNativeIdToReplicaUriMap, syncType, !createInactive, taskCompleter)));

        } catch (Exception e) {
            final String errMsg = format(
                    "An exception occurred when trying to create list replica on storage system {0}", storage.getId());
            _log.error(errMsg, e);
            List<? extends BlockObject> replicas = BlockObject.fetch(_dbClient, replicaList);
            for (BlockObject replica : replicas) {
                replica.setInactive(true);
            }
            _dbClient.persistObject(replicas);
            ServiceError error = DeviceControllerErrors.smis.methodFailed("createListReplica", e.getMessage());
            taskCompleter.error(_dbClient, error);
        }
        _log.info("createListReplica operation END");
    }

    @Override
    public void detachListReplica(StorageSystem storage, List<URI> replicaList, TaskCompleter taskCompleter) {
        _log.info("detachListReplica operation START");

        try {
            List<? extends BlockObject> replicas = BlockObject.fetch(_dbClient, replicaList);
            modifyListReplica(storage, replicaList, replicas, SmisConstants.DETACH_VALUE, SmisConstants.NON_COPY_STATE);
            for (BlockObject replica : replicas) {
                if (replica instanceof BlockMirror) {
                    BlockMirror mirror = (BlockMirror) replica;
                    mirror.setReplicaState(ReplicationState.DETACHED.name());
                    mirror.setConsistencyGroup(NullColumnValueGetter.getNullURI());
                    mirror.setReplicationGroupInstance(NullColumnValueGetter.getNullStr());
                    mirror.setSynchronizedInstance(NullColumnValueGetter.getNullStr());
                    mirror.setSyncState(NullColumnValueGetter.getNullStr());

                    Volume volume = _dbClient.queryObject(Volume.class, mirror.getSource());
                    if (volume.getMirrors() != null) {
                        volume.getMirrors().remove(mirror.getId().toString());
                        _dbClient.persistObject(volume);
                    }
                } else if (replica instanceof Volume) {
                    Volume clone = (Volume) replica;
                    ReplicationUtils.removeDetachedFullCopyFromSourceFullCopiesList(clone, _dbClient);
                    clone.setAssociatedSourceVolume(NullColumnValueGetter.getNullURI());
                    clone.setReplicaState(ReplicationState.DETACHED.name());
                }
            }

            _dbClient.persistObject(replicas);
            taskCompleter.ready(_dbClient);
        } catch (Exception e) {
            _log.error("Problem making SMI-S call", e);
            ServiceError error = DeviceControllerException.errors.jobFailed(e);
            taskCompleter.error(_dbClient, error);
        }

        _log.info("detachListReplica operation END");
    }

    /**
     * Invoke modifyListSynchronization for synchronized operations, e.g. fracture, detach, etc.
     *
     * @param storage
     * @param replicaList
     * @param operation
     * @param copyState
     * @throws Exception
     */
    @SuppressWarnings("rawtypes")
    private void modifyListReplica(StorageSystem storage, List<URI> replicaList, List<? extends BlockObject> replicas, int operation,
            int copyState)
                    throws Exception {

        callEMCRefreshIfRequired(_dbClient, _helper, storage, replicaList);
        List<CIMObjectPath> syncPaths = new ArrayList<CIMObjectPath>();
        for (BlockObject replica : replicas) {
            BlockObject source = _helper.getSource(replica);
            CIMObjectPath syncObject = _cimPath.getStorageSynchronized(storage, source, storage, replica);
            if (_helper.checkExists(storage, syncObject, false, false) == null) {
                _log.error("Storage synchronized instance is not available for replica {}", replica.getLabel());
                throw DeviceControllerException.exceptions.synchronizationInstanceNull(replica.getLabel());
            }

            syncPaths.add(syncObject);
        }

        CIMArgument[] inArgs = _helper.getModifyListReplicaInputArguments(syncPaths.toArray(new CIMObjectPath[] {}),
                operation, copyState);
        _helper.callModifyListReplica(storage, inArgs);
    }

    private int getSyncType(URI uri) {
        int syncType;
        if (URIUtil.isType(uri, BlockMirror.class)) {
            syncType = SmisConstants.MIRROR_VALUE;
        } else if (URIUtil.isType(uri, BlockSnapshot.class)) {
            syncType = SmisConstants.SNAPSHOT_VALUE;
        } else {
            syncType = SmisConstants.CLONE_VALUE;
        }

        return syncType;
    }
}
