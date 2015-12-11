/*
 * Copyright (c) 2014 EMC Corporation
 * All Rights Reserved
 */

package com.emc.storageos.cinder.model;

import javax.xml.bind.annotation.XmlElement;
import com.google.gson.annotations.SerializedName;

public class VolumeAttachRequest {
    /**
     * Json model representation for volume attach request
     * 
     * for iSCSI
     * 
     * {"os-initialize_connection":
     * {"connector":
     * {
     * "initiator": "<IQN>",
     * "host": "<HOST FQDN or IP ADDRESS>"
     * }
     * }
     * }
     * 
     * for FC (wwpn without colon)
     * 
     * {"os-initialize_connection":
     * {"connector":
     * {
     * "wwpns": ["<WWPN1>", "<WWPN2>"],
     * "host": "<HOST FQDN or IP ADDRESS>"
     * }
     * }
     * }
     */

    @SerializedName("os-initialize_connection")
    @XmlElement(name = "os-initialize-connection")
    public InitializeConnection initializeConnection = new InitializeConnection();

    public class InitializeConnection
    {
        public Connector connector = new Connector();
    }

    public class Connector
    {
        /** to be filled in for iSCSI attach */
        public String initiator;
        /** to be filled in for FC attach */
        public String[] wwpns;
        public String[] wwnns;
        public String host;
    }
}
