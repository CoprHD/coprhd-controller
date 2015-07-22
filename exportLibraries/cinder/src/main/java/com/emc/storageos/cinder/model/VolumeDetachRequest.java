/*
 * Copyright 2015 EMC Corporation
 * All Rights Reserved
 */
/**
 * Copyright (c) 2014 EMC Corporation
 * All Rights Reserved
 *
 * This software contains the intellectual property of EMC Corporation
 * or is licensed to EMC Corporation from third parties.  Use of this
 * software and the intellectual property contained therein is expressly
 * limited to the terms and conditions of the License Agreement under which
 * it is provided by or on behalf of EMC.
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
        public String[] wwnns;
        public String host;
    }
}
