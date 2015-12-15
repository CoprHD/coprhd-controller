/* Copyright (c) 2015 EMC Corporation
 * All Rights Reserved
 *
 */
package com.emc.storageos.cinder.model;

import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlRootElement;

@XmlRootElement
public class CinderInitConnectionResponse {
    @XmlElement(name = "connection_info")
    public CinderInitConnection connection_info = new CinderInitConnection();

    public class CinderInitConnection {
        public String driver_volume_type;
        public Data data = new Data();

        public class Data {
            public String target_discovered;
            public String qos_specs;
            public String target_iqn;
            public String target_portal;
            public String volume_id;
            public int target_lun;
            public String access_mode;
        }

    }
}