package com.emc.storageos.storagedriver.storagecapabilities;


import com.emc.storageos.storagedriver.model.ServiceOption;
import com.emc.storageos.storagedriver.model.Workload;

import java.util.ArrayList;
import java.util.List;

public class DataPerformanceServiceOption extends ServiceOption {

    List<Workload> workloads = new ArrayList<>();
}
