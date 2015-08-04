/*
 * Copyright (c) 2014 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.datadomain.restapi.model;

import org.codehaus.jackson.map.annotate.JsonRootName;

import java.util.ArrayList;
import java.util.List;

/**
 * Created by zeldib on 2/10/14.
 */
@JsonRootName(value = "networks")
public class DDNetworkList {

    public List<DDNetworkInfo> network = new ArrayList<DDNetworkInfo>();

}
