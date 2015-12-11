/* Copyright (c) 2015 EMC Corporation
 * All Rights Reserved
 *
 */
package com.emc.storageos.cinder.model;

import javax.xml.bind.annotation.XmlRootElement;

@XmlRootElement(name = "Quotas")
public class Quotas {
    public int snapshots;
    public int volumes;
    public int gigabytes;
}
