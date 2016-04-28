/* Copyright (c) 2015 EMC Corporation
 * All Rights Reserved
 *
 */
package com.emc.storageos.cinder.model;

import java.util.Map;

import javax.xml.bind.annotation.XmlAccessOrder;
import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorOrder;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlRootElement;

import org.codehaus.jackson.map.annotate.JsonRootName;

@JsonRootName(value = "volume_type")
@XmlRootElement(name = "volume_type")
@XmlAccessorType(XmlAccessType.PUBLIC_MEMBER)
@XmlAccessorOrder(XmlAccessOrder.ALPHABETICAL)
public class VolumeType {
    public String name;
    public String id;
    public Map<String, String> extra_specs;
}
