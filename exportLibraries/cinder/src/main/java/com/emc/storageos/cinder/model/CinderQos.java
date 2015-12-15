/* Copyright (c) 2015 EMC Corporation
 * All Rights Reserved
 *
 */
package com.emc.storageos.cinder.model;

import java.util.Map;

import javax.xml.bind.annotation.XmlRootElement;

@XmlRootElement(name = "qos_spec")
public class CinderQos {
    public String consumer;
    public String name;
    public String id;
    public Map<String, String> specs;
}
