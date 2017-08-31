package com.emc.storageos.volumecontroller.impl.externaldevice.taskcompleters.utils;

import java.net.URI;
import java.util.Collections;

import com.emc.storageos.db.client.model.BlockConsistencyGroup;
import com.emc.storageos.db.client.model.remotereplication.RemoteReplicationGroup;
import com.emc.storageos.db.client.model.remotereplication.RemoteReplicationPair;
import com.emc.storageos.storagedriver.model.remotereplication.RemoteReplicationSet;
import com.emc.storageos.volumecontroller.TaskCompleter;

public class RemoteReplicationTaskCompleterUtils {

    private RemoteReplicationTaskCompleterUtils() {}

    /**
     * Configure fields of task completer: _opId, _ids and _clazz, based on given element type and URI
     */
    public static void configTaskCompleter(TaskCompleter taskCompleter, RemoteReplicationSet.ElementType elementType,
            URI elementURI, String opId) {
        taskCompleter.setOpId(opId);
        taskCompleter.addIds(Collections.singletonList(elementURI));
        switch (elementType) {
            case REPLICATION_GROUP:
                taskCompleter.setType(RemoteReplicationGroup.class);
                break;
            case REPLICATION_PAIR:
                taskCompleter.setType(RemoteReplicationPair.class);
                break;
            case CONSISTENCY_GROUP:
                taskCompleter.setType(BlockConsistencyGroup.class);
                break;
            case REPLICATION_SET:
                taskCompleter.setType(com.emc.storageos.db.client.model.remotereplication.RemoteReplicationSet.class);
                break;
            default:
                throw new RuntimeException(String.format("Undefined element type: %s", elementType.toString()));
        }
    }
}
