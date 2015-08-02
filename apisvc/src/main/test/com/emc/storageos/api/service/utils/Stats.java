/*
 * Copyright (c) 2015 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.api.service.utils;

import java.util.List;

import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlElements;
import javax.xml.bind.annotation.XmlRootElement;

import com.emc.storageos.db.client.model.Stat;

@XmlRootElement(name = "stats")
public class Stats {

    @XmlElements(@XmlElement(name = "stat"))
    public List<Stat> stats;

}
