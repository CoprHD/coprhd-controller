/*
 * Copyright (c) 2015 EMC Corporation
 * All Rights Reserved
 */
package util;

import com.emc.storageos.model.TaskResourceRep;
import com.emc.storageos.model.event.EventParameters;

public class SupportUtils {

    public static TaskResourceRep submitSupportRequest(String userEmail, String userComment, Long startDate, Long endDate) {
        return BourneUtil.getSysClient().callHome().sendAlert(String.valueOf(startDate),String.valueOf(endDate),new EventParameters(userComment,userEmail));
    }
}
