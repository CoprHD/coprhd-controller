/*
 * Copyright (c) 2014 EMC Corporation
 * All Rights Reserved
 */

package com.emc.storageos.cinder.model;

import java.util.List;
import java.util.Map;

public class VolumeAttachResponse {

    /**
     * Json model representation for volume attach response
     * 
     * for iSCSI
     * 
     * {"connection_info":
     * {
     * "driver_volume_type": "iscsi",
     * "data":
     * {
     * "target_discovered": false,
     * "qos_spec": null,
     * "target_iqn": "<TARGET_STORAGE_PORT_IQN>",
     * "target_portal": "<TARGET_ARRAY_IP_ADDRESS>:<PORT_NUMBER>",
     * "volume_id": "ad620215-9253-4192-bb2d-3b2b190ecebd",
     * "target_lun": 1,
     * "access_mode": "rw"
     * }
     * }
     * }
     * 
     * for FC (initiator and target wwns will be shown without colon)
     * 
     * {"connection_info":
     * {
     * "driver_volume_type": "fibre_channel",
     * "data":
     * {
     * "initiator_target_map":
     * {
     * "<INITIATOR_WWPN1>": ["<TARGET_STORAGE_PORT_WWN1>", "<TARGET_STORAGE_PORT_WWN2>"],
     * "<INITIATOR_WWPN2>": ["<TARGET_STORAGE_PORT_WWN1>", "<TARGET_STORAGE_PORT_WWN2>"]
     * },
     * "target_discovered": false,
     * "qos_spec": null,
     * "volume_id": "fe29d4a9-304e-4241-95fe-d9b6c31887f9",
     * "target_lun": 2,
     * "access_mode": "rw",
     * "target_wwn": ["<TARGET_STORAGE_PORT_WWN1>", "<TARGET_STORAGE_PORT_WWN2>"]
     * some drivers return target_wwn as string list, but some return it as string.
     * To handle it, we maintain two model classes (VolumeAttachResponse and VolumeAttachResponseAlt)
     * }
     * }
     * }
     */

    public ConnectionInfo connection_info;

    public class ConnectionInfo {
        public String driver_volume_type;
        public Data data;

        @Override
        public String toString() {
            StringBuffer response = new StringBuffer();
            response.append("ConnectionInfo [driver_volume_type=");
            response.append(driver_volume_type);
            response.append(", data=");
            response.append(data);
            response.append("]");
            return response.toString();
        }
    }

    public class Data {
        public boolean target_discovered;
        public String qos_spec;

        /** filled in for iSCSI attach response */
        public String target_iqn;
        /** filled in for iSCSI attach response */
        public String target_portal;

        /** filled in for FC attach response */
        public List<String> target_wwn;
        /** filled in for FC attach response */
        public Map<String, List<String>> initiator_target_map;

        public String volume_id;
        public int target_lun;
        public String access_mode;

        @Override
        public String toString() {
            StringBuffer response = new StringBuffer();
            response.append("Data [target_discovered=");
            response.append(target_discovered);
            response.append(", qos_spec=");
            response.append(qos_spec);
            response.append(", target_iqn=");
            response.append(target_iqn);
            response.append(", target_portal=");
            response.append(target_portal);
            response.append(", target_wwn=");
            response.append(target_wwn);
            response.append(", initiator_target_map=");
            response.append(initiator_target_map);
            response.append(", volume_id=");
            response.append(volume_id);
            response.append(", target_lun=");
            response.append(target_lun);
            response.append(", access_mode=");
            response.append(access_mode);
            response.append("]");
            return response.toString();
        }
    }
}
