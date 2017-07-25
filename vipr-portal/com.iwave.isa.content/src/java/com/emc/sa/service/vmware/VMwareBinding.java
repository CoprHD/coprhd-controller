/*
 * Copyright (c) 2016 EMC Corporation
 * All Rights Reserved
 */

package com.emc.sa.service.vmware;

import static com.emc.sa.service.ServiceParams.CONSISTENCY_GROUP;
import static com.emc.sa.service.ServiceParams.DATASTORE_NAME;
import static com.emc.sa.service.ServiceParams.HLU;
import static com.emc.sa.service.ServiceParams.HOST;
import static com.emc.sa.service.ServiceParams.NAME;
import static com.emc.sa.service.ServiceParams.NUMBER_OF_VOLUMES;
import static com.emc.sa.service.ServiceParams.PORT_GROUP;
import static com.emc.sa.service.ServiceParams.PROJECT;
import static com.emc.sa.service.ServiceParams.SIZE_IN_GB;
import static com.emc.sa.service.ServiceParams.VIRTUAL_ARRAY;
import static com.emc.sa.service.ServiceParams.VIRTUAL_POOL;

import java.net.URI;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.emc.sa.engine.bind.Param;

public class VMwareBinding {
    /**
     * Class that holds all params for volume creation. These params will be added
     * to the createBlockVolumeHelper for each pair of Datastore / volumes. This class
     * is needed since all params listed are single instance on the form while the
     * Datastore / Volume can have multiple.
     * 
     * @author cormij4
     * 
     */
    public static class DatastoreToVolumeParams {
        @Param(VIRTUAL_POOL)
        protected URI virtualPool;
        @Param(VIRTUAL_ARRAY)
        protected URI virtualArray;
        @Param(PROJECT)
        protected URI project;
        @Param(HOST)
        protected URI hostId;
        @Param(value = NUMBER_OF_VOLUMES, required = false)
        protected Integer count;
        @Param(value = CONSISTENCY_GROUP, required = false)
        protected URI consistencyGroup;
        @Param(value = HLU, required = false)
        protected Integer hlu;
        @Param(value = PORT_GROUP, required = false)
        protected URI portGroup;

        @Override
        public String toString() {
            return "Virtual Pool=" + virtualPool + ", Virtual Array=" + virtualArray + ", Project=" + project
                    + ", Host Id=" + hostId + ", Volume Count=" + count + ", Consistency Group=" + consistencyGroup
                    + ", HLU=" + hlu;
        }

        public Map<String, Object> getParams(int hluInc) {
            Map<String, Object> map = new HashMap<String, Object>();
            map.put(VIRTUAL_POOL, virtualPool);
            map.put(VIRTUAL_ARRAY, virtualArray);
            map.put(PROJECT, project);
            map.put(HOST, hostId);
            map.put(NUMBER_OF_VOLUMES, count);
            map.put(CONSISTENCY_GROUP, consistencyGroup);
            if (hlu == -1) {
                map.put(HLU, hlu);
            } else {
                map.put(HLU, hlu + hluInc);
            }
            map.put(PORT_GROUP, portGroup);
            return map;
        }
    }

    /**
     * Class to hold params of all pair of Datastore / Volume.
     * 
     * @author cormij4
     * 
     */
    public static class DatastoreToVolumeTable {
        @Param(DATASTORE_NAME)
        protected String datastoreName;
        @Param(NAME)
        protected String nameParam;
        @Param(SIZE_IN_GB)
        protected Double sizeInGb;

        @Override
        public String toString() {
            return "Datastore Name=" + datastoreName + ", Volume=" + nameParam + ", size=" + sizeInGb;
        }

        public Map<String, Object> getParams() {
            Map<String, Object> map = new HashMap<String, Object>();
            map.put(DATASTORE_NAME, datastoreName);
            map.put(NAME, nameParam);
            map.put(SIZE_IN_GB, sizeInGb);
            return map;
        }
    }

    /**
     * Helper method for creating a list of all the params for the createBlockVolumesHelper.
     * 
     * @param table of Datastore to Volumes
     * @param params for volume creation
     * @return Map of all params
     */
    public static Map<String, Object> createDatastoreVolumeParam(DatastoreToVolumeTable table, DatastoreToVolumeParams params, int hluInc) {
        Map<String, Object> map = new HashMap<String, Object>();
        map.putAll(table.getParams());
        map.putAll(params.getParams(hluInc));
        return map;
    }

    /**
     * Loop through all the Datastore / Volumes pair and return all the Datastore names.
     * 
     * @param datastoreToVolume list
     * @return list of datastore names
     */
    public static List<String> getDatastoreNamesFromDatastoreToVolume(DatastoreToVolumeTable[] datastoreToVolume) {
        List<String> dataStoreNames = new ArrayList<String>();
        for (DatastoreToVolumeTable value : datastoreToVolume) {
            dataStoreNames.add(value.datastoreName);
        }
        return dataStoreNames;
    }

    /**
     * Loop through all the Datastore / Volumes pair and return all the Volume names.
     * 
     * @param datastoreToVolume list
     * @return list of volume names
     */
    public static List<String> getVolumeNamesFromDatastoreToVolume(DatastoreToVolumeTable[] datastoreToVolume) {
        List<String> volumeNames = new ArrayList<String>();
        for (DatastoreToVolumeTable value : datastoreToVolume) {
            volumeNames.add(value.nameParam);
        }
        return volumeNames;
    }
}
