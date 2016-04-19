/*
 * Copyright (c) 2016 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.model.schedulepolicy;

import java.net.URI;

import javax.xml.bind.annotation.XmlRootElement;

import com.emc.storageos.model.NamedRelatedResourceRep;
import com.emc.storageos.model.RestLinkRep;

/**
 * SchedulePolicyResp will contain the schedule policy response.
 * 
 * @author prasaa9
 * 
 */

@XmlRootElement(name = "tenant_schedule_policy")
public class SchedulePolicyResp extends NamedRelatedResourceRep {

    public SchedulePolicyResp() {
    }

    public SchedulePolicyResp(URI id, RestLinkRep selfLink, String name) {
        super(id, selfLink, name);
    }
}
