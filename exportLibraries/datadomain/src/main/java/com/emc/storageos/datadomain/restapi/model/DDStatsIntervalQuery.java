/**
* Copyright 2015 EMC Corporation
* All Rights Reserved
 */
package com.emc.storageos.datadomain.restapi.model;

public enum DDStatsIntervalQuery {
	
    hour, day, week;
    
    public static boolean isMember(String interval) {
        DDStatsIntervalQuery[] intervals = DDStatsIntervalQuery.values();
        for (DDStatsIntervalQuery intervalValue : intervals) {
            if (intervalValue.toString().equals(interval))
                return true;
        }
        return false;
    }
    
    public static boolean isMember(DDStatsIntervalQuery interval) {
        DDStatsIntervalQuery[] intervals = DDStatsIntervalQuery.values();
        for (DDStatsIntervalQuery intervalValue : intervals) {
            if (intervalValue.equals(interval)) {
                return true;
            }
        }
        return false;
    }

}
