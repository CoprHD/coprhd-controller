/* Copyright (c) 2015 EMC Corporation
 * All Rights Reserved
 *
 */
package com.emc.storageos.cinder.model;

import javax.xml.bind.annotation.XmlRootElement;

@XmlRootElement(name = "attachment")
public class Attachment {
    public String host_name;
    public String device;
    public String server_id;
    public String id;
    public String volume_id;
}
