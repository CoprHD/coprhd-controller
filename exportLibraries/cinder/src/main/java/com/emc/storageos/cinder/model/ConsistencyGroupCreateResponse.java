/* Copyright (c) 2015 EMC Corporation
 * All Rights Reserved
 *
 */
package com.emc.storageos.cinder.model;

import javax.xml.bind.annotation.XmlAttribute;
import javax.xml.bind.annotation.XmlRootElement;

import org.codehaus.jackson.map.annotate.JsonRootName;

@XmlRootElement(name = "consistencygroup")
@JsonRootName(value = "consistencygroup")
public class ConsistencyGroupCreateResponse {

    @XmlAttribute
    public String id;

    @XmlAttribute
    public String name;

}
