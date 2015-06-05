/**
* Copyright 2015 EMC Corporation
* All Rights Reserved
 */
package com.emc.storageos.datadomain.restapi.model;

public enum DDStatsDataViewQuery {
	
	absolute, delta, absolute_and_delta;
	
	public static boolean isMember(String dataView) {
		DDStatsDataViewQuery[] views = DDStatsDataViewQuery.values();
        for (DDStatsDataViewQuery view : views) {
            if (view.toString().equals(dataView))
                return true;
        }
        return false;
    }
    
    public static boolean isMember(DDStatsDataViewQuery dataView) {
    	DDStatsDataViewQuery[] views = DDStatsDataViewQuery.values();
        for (DDStatsDataViewQuery view : views) {
            if (view.equals(dataView)) {
                return true;
            }
        }
        return false;
    }

}
