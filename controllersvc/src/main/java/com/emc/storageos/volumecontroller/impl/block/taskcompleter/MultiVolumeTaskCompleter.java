/*
 * Copyright (c) 2012 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.volumecontroller.impl.block.taskcompleter;

import java.net.URI;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.emc.storageos.db.client.DbClient;
import com.emc.storageos.db.client.model.Operation;
import com.emc.storageos.db.client.model.Volume;
import com.emc.storageos.exceptions.DeviceControllerException;
import com.emc.storageos.svcs.errorhandling.model.ServiceCoded;
import com.emc.storageos.volumecontroller.TaskCompleter;

/**
 * MultiVolumeTaskCompleter is a completer for volume jobs that support multiple
 * volumes such as volume creation and volume deletion.
 */
@SuppressWarnings("serial")
public class MultiVolumeTaskCompleter extends TaskCompleter {

    // A list of associated volume task completers.
    private Map<URI, VolumeTaskCompleter> _volumeTaskCompleterMap = new HashMap<>();

    public MultiVolumeTaskCompleter(List<URI> ids, String opId) {
        super(Volume.class, ids, opId);
    }

    /**
     * Constructor.
     * 
     * @param volumeTaskCompleters A list of volume task completers.
     * @param opId The operation id.
     */
    public MultiVolumeTaskCompleter(List<URI> ids, List<VolumeTaskCompleter> volumeTaskCompleters, String opId) {
        super(Volume.class, ids, opId);
        for (VolumeTaskCompleter tc : volumeTaskCompleters) {
            addVolumeCompleter(tc);
        }
    }

    /**
     * For the same volume contained its its own single completer and in this multi-volume completer,
     * only one completer should delegate to inform the workflow service of completion. This disables
     * the single completer in the case of a multi-volume completer.
     * 
     * @param completer
     *            single volume completer
     * @return completer to add to the
     */
    public void addVolumeCompleter(VolumeTaskCompleter completer) {
        completer.setNotifyWorkflow(false); // This completer will take care of notifying workflow
        _volumeTaskCompleterMap.put(completer.getId(), completer);
    }

    /**
     * Implements TaskCompleter interface by invoking the the like method for
     * the associated volume task completer instances.
     * 
     * @param dbClient A reference to the database client.
     * @param status The completion status.
     */
    @Override
    protected void complete(DbClient dbClient, Operation.Status status, ServiceCoded coded) throws DeviceControllerException {
        for (VolumeTaskCompleter volumeTaskCompleter : _volumeTaskCompleterMap.values()) {
            volumeTaskCompleter.complete(dbClient, status, coded);
        }
        updateWorkflowStatus(status, coded);
    }

    public VolumeTaskCompleter skipTaskCompleter(URI volumeURI) {
        return _volumeTaskCompleterMap.remove(volumeURI);
    }

    /**
     * check whether VolumeTaskCompleters left out
     * 
     * @return
     */
    public boolean isVolumeTaskCompletersEmpty() {
        return (null == _volumeTaskCompleterMap || _volumeTaskCompleterMap.isEmpty());
    }

    public Map<URI, VolumeTaskCompleter> getVolumeTaskCompleterMap() {
        return _volumeTaskCompleterMap;
    }
}
