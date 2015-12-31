/* Copyright (c) 2015 EMC Corporation
 * All Rights Reserved
 *
 */
package com.emc.storageos.cinder.model;

public class ConsistencyGroupCreateRequest {

    /**
     * Json representation for creating consistency Grp
     * 
     * {"consistencygroup" : {
     * "status":"creating",
     * "user_id":null,
     * "name":"cg1",
     * "availability_zone":null,
     * "volume_types":null,
     * "project_id":null,
     * "description":null }
     * }
     * 
     */

    public Consistencygroup consistencygroup = new Consistencygroup();

    public class Consistencygroup {

        public String status;
        public String user_id;
        public String name;
        public String availability_zone;
        public String volume_types;
        public String project_id;
        public String description;
    }

}
