/* Copyright (c) 2015 EMC Corporation
 * All Rights Reserved
 *
 */
package com.emc.storageos.cinder.model;

import java.util.List;
import java.util.Map;

import javax.xml.bind.annotation.XmlAttribute;
import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlRootElement;

import org.codehaus.jackson.map.annotate.JsonRootName;
import org.codehaus.jackson.annotate.JsonProperty;
import com.emc.storageos.model.RestLinkRep;

@XmlRootElement(name = "volume")
@JsonRootName(value = "volume")
public class VolumeDetail {
    @XmlAttribute
    public String status;
    @XmlAttribute
    public String name;

    public List<Attachment> attachments;

    @XmlAttribute
    public String availability_zone;
    @XmlAttribute
    public boolean bootable;
    @XmlAttribute
    public String created_at;
    @XmlAttribute
    public String description;
    @XmlAttribute
    public String volume_type;
    @XmlAttribute
    public String snapshot_id;
    @XmlAttribute
    public String source_volid;

    public Map<String, String> metadata;

    @XmlAttribute
    public String id;
    @XmlAttribute
    public int size;

    private RestLinkRep selfLink;

    @XmlAttribute
    public String display_name;
    @XmlAttribute
    public String display_description;
    public String host_name;

    @JsonProperty(value = "os-vol-tenant-attr:tenant_id")
    public String tenant_id;

    @XmlElement(name = "links")
    public RestLinkRep getLink() {
        return selfLink;
    }

    public void setLink(RestLinkRep link) {
        selfLink = link;
    }

    @XmlAttribute
    public String consistencygroup_id;

}
