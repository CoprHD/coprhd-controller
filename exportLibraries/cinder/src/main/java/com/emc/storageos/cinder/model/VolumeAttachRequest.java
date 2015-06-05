/**
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

public class VolumeAttachRequest {
    /**
     * Json model representation for volume attach request
     * 
     * for iSCSI
     * 
     * {"os-initialize_connection": 
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
     * {"os-initialize_connection": 
            {"connector": 
                {
                    "wwpns": ["<WWPN1>", "<WWPN2>"],
                    "host": "<HOST FQDN or IP ADDRESS>"
                }
            }
       }
     */

    @SerializedName("os-initialize_connection")
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
        public String host;
    }
}
