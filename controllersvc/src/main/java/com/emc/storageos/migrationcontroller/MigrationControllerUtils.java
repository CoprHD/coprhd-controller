package com.emc.storageos.migrationcontroller;

import java.net.URI;
import java.util.Collection;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.emc.storageos.db.client.DbClient;
import com.emc.storageos.db.client.model.DataObject;
import com.emc.storageos.db.client.model.StorageSystem;
import com.emc.storageos.db.client.model.Volume;
import com.emc.storageos.db.exceptions.DatabaseException;
import com.emc.storageos.exceptions.DeviceControllerException;
import com.emc.storageos.volumecontroller.ControllerException;

public class MigrationControllerUtils {
    private static final Logger log = LoggerFactory
            .getLogger(MigrationControllerUtils.class);

    protected static <T extends DataObject> T getDataObject(Class<T> clazz, URI id, DbClient dbClient)
            throws DeviceControllerException {
        try {
            T object = null;

            if (id != null) {
                object = dbClient.queryObject(clazz, id);
                if (object == null) {
                    throw MigrationControllerException.exceptions.getDataObjectFailedNotFound(
                            clazz.getSimpleName(), id.toString());
                }
                if (object.getInactive()) {
                    log.info("database object is inactive: " + object.getId().toString());
                    throw MigrationControllerException.exceptions.getDataObjectFailedInactive(id.toString());
                }
            }
            return object;
        } catch (DatabaseException ex) {
            throw MigrationControllerException.exceptions.getDataObjectFailedExc(id.toString(), ex);
        }
    }

    protected static URI getVolumesVarray(StorageSystem array, Collection<Volume> volumes)
            throws ControllerException {
        URI varray = null;
        for (Volume volume : volumes) {
            if (volume.getStorageController().equals(array.getId())) {
                if (varray == null) {
                    varray = volume.getVirtualArray();
                } else if (!varray.equals(volume.getVirtualArray())) {
                    DeviceControllerException ex = DeviceControllerException.exceptions.multipleVarraysInVPLEXExportGroup(
                            array.getId().toString(), varray.toString(), volume.getVirtualArray().toString());
                    log.error("Multiple varrays connecting VPLEX to array", ex);
                    throw ex;
                }
            }
        }
        return varray;
    }
}
