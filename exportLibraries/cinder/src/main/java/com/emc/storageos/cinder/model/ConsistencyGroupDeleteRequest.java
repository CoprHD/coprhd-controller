/* Copyright (c) 2015 EMC Corporation
 * All Rights Reserved
 *
 */
package com.emc.storageos.cinder.model;

public class ConsistencyGroupDeleteRequest {

    /**
     * JSON representation for consistency group delete request
     * {"consistencygroup": {"force": true}}
     */

    public Consistencygroup consistencygroup = new Consistencygroup();

    public class Consistencygroup {

        public boolean force;
    }
}
