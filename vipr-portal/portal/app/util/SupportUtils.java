/*
 * Copyright 2015 EMC Corporation
 * All Rights Reserved
 */
package util;

import com.emc.storageos.model.TaskResourceRep;
import com.emc.storageos.model.event.EventParameters;
import com.emc.vipr.model.sys.SysSvcTask;

import java.util.List;

public class SupportUtils {

    public static TaskResourceRep submitSupportRequest(String userEmail, String userComment, Long startDate, Long endDate) {
        SysSvcTask sysTask = BourneUtil.getSysClient().callHome().sendAlert(String.valueOf(startDate), String.valueOf(endDate),
                new EventParameters(userComment, userEmail));

        // Should only be one task
        List<TaskResourceRep> tasks = BourneUtil.getViprClient().tasks().findByResource(sysTask.getResource().getId());
        if (tasks.isEmpty()) {
            throw new IllegalStateException("No Task after submitting Send Alert");
        }

        return tasks.get(0);
    }
}
