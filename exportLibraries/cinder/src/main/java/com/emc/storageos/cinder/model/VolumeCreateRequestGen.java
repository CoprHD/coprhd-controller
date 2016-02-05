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

//import VolumeCreateRequest;

public class VolumeCreateRequestGen extends VolumeCreateRequest {

    /**
     * Json model representation for volume
     * create request
     * 
     * This class is created as a generic volumecreaterequest class which can handle the inputs of
     * volume create in Cinder V1 and V2.
     */
    public VolumeGen volume = new VolumeGen();

    public class VolumeGen extends Volume
    {
        public String display_name;
        public String display_description;

    }

}
