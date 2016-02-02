/*
 * Copyright (c) 2016 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.storagedriver.storagecapabilities;


import java.util.ArrayList;
import java.util.List;

import com.emc.storageos.storagedriver.model.ServiceOption;
import com.emc.storageos.storagedriver.model.Workload;

public class DataPerformanceServiceOption extends ServiceOption {

    List<Workload> workloads = new ArrayList<>();
}
