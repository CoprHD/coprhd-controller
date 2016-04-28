/* Copyright (c) 2015 EMC Corporation
 * All Rights Reserved
 *
 */
package com.emc.storageos.cinder.model;

import javax.xml.bind.annotation.XmlAttribute;
import javax.xml.bind.annotation.XmlRootElement;

import org.codehaus.jackson.map.annotate.JsonRootName;

@JsonRootName(value = "extension")
@XmlRootElement(name = "extension")
public class CinderExtension {
    @XmlAttribute
    public String updated;
    @XmlAttribute
    public String namespace;
    @XmlAttribute
    public String name;
    @XmlAttribute
    public String alias;
    public String description;
    public String links[];
}
