/* Copyright (c) 2015 EMC Corporation
 * All Rights Reserved
 *
 */
package com.emc.storageos.cinder.model;

import java.util.Map;

import javax.xml.bind.annotation.XmlAttribute;
import javax.xml.bind.annotation.XmlRootElement;

import org.codehaus.jackson.map.annotate.JsonRootName;

@JsonRootName(value = "snapshot")
@XmlRootElement(name = "snapshot")
public class CinderSnapshot {
    @XmlAttribute(name = "status")
    public String status;
    @XmlAttribute(name = "description")
    public String description;
    @XmlAttribute(name = "created_at")
    public String created_at;
    @XmlAttribute(name = "volume_id")
    public String volume_id;
    @XmlAttribute(name = "size")
    public int size;
    @XmlAttribute(name = "id")
    public String id;
    @XmlAttribute(name = "name")
    public String name;
    @XmlAttribute(name = "display_name")
    public String display_name;
    @XmlAttribute(name = "display_description")
    public String display_description;
    @XmlAttribute(name = "project_id")
    public String project_id;
    @XmlAttribute(name = "progress")
    public String progress;

    public Map<String, String> metadata;
    // public String os-extended-snapshot-attributes:progress;
    // os-extended-snapshot-attributes:project_id"
}
