/* Copyright (c) 2015 EMC Corporation
 * All Rights Reserved
 *
 */
package com.emc.storageos.cinder.model;

import javax.xml.bind.annotation.XmlRootElement;

@XmlRootElement(name = "limits")
public class CinderLimitsDetail {
    public CinderLimits limits = new CinderLimits();
}
