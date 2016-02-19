/*
 * Copyright (c) 2016 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.model.dr;

import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlRootElement;

@XmlRootElement(name = "switchover_precheck_error")
@XmlAccessorType(XmlAccessType.PROPERTY)
public class SwitchoverPrecheckResponse extends SiteErrorResponse {
}