/*
 * Copyright (c) 2014 EMC Corporation
 * All Rights Reserved
 */

package com.emc.storageos.cinder.model;

import com.google.gson.annotations.SerializedName;

public class VolumeDetachRequest {
    
    /**
     * Json model representation for volume detach request
     * 
     * for iSCSI
     * 
     * {"os-terminate_connection": 
            {"connector": 
                {
                    "initiator": "<IQN>",
                    "host": "<HOST FQDN or IP ADDRESS>"
                }
            }
       }
     *
     * for FC (wwpn without colon)
     * 
     * {"os-terminate_connection": 
            {"connector": 
                {
                    "wwpns": ["<WWPN1>", "<WWPN2>"],
                    "host": "<HOST FQDN or IP ADDRESS>"
                }
            }
       }
     */

    @SerializedName("os-terminate_connection")
    public TerminateConnection terminateConnection = new TerminateConnection();

    public class TerminateConnection
    {
        public Connector connector = new Connector();
    }

    public class Connector
    {
        /** to be filled in for iSCSI detach */
        public String initiator;
        /** to be filled in for FC detach */
        public String[] wwpns;
        public String host;
    }
}
