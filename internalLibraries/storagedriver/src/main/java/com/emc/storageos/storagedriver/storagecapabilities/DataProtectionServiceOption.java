/*
 * Copyright (c) 2016 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.storagedriver.storagecapabilities;


import java.util.List;

import com.emc.storageos.storagedriver.model.Schedule;
import com.emc.storageos.storagedriver.model.ServiceOption;

public class DataProtectionServiceOption extends ServiceOption {

    List<Schedule> schedule;
}

