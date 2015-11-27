/**
 *  Copyright (c) 2015 EMC Corporation
 * All Rights Reserved
 *
 * This software contains the intellectual property of EMC Corporation
 * or is licensed to EMC Corporation from third parties.  Use of this
 * software and the intellectual property contained therein is expressly
 * limited to the terms and conditions of the License Agreement under which
 * it is provided by or on behalf of EMC.
 */
package com.emc.storageos.cinder.model;

import javax.xml.bind.annotation.XmlAccessOrder;
import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorOrder;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlRootElement;

import org.codehaus.jackson.map.annotate.JsonRootName;

@JsonRootName(value = "volume_type_encryption")
@XmlRootElement(name = "volume_type_encryption")
@XmlAccessorType(XmlAccessType.PUBLIC_MEMBER)
@XmlAccessorOrder(XmlAccessOrder.ALPHABETICAL)
public class VolumeTypeEncryption {
    public String volume_type_id;
    public String provider;
    public String cipher;
    public String control_location;
    public int key_size;


}
