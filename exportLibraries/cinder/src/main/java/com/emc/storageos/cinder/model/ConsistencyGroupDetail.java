/* Copyright (c) 2015 EMC Corporation
 * All Rights Reserved
 *
 */
package com.emc.storageos.cinder.model;

import javax.xml.bind.annotation.XmlRootElement;

import org.codehaus.jackson.map.annotate.JsonRootName;

@XmlRootElement(name = "consistencygroup")
@JsonRootName(value = "consistencygroup")
public class ConsistencyGroupDetail {
    public String description;
    public String created_at;
    public String availability_zone;
    public String id;
    public String name;
    public String status;
}
