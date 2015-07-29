/*
 * Copyright (c) 2014 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.datadomain.restapi.model;

import org.codehaus.jackson.map.annotate.JsonRootName;

import java.util.List;

/**
 * Created by zeldib on 2/10/14.
 */
@JsonRootName(value = "mtrees")
public class DDMTreeList {

    public List<DDMTreeInfo> mtree;

}
