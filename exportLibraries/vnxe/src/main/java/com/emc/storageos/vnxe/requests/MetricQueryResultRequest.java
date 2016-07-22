/*
 * Copyright (c) 2014 EMC Corporation
 * All Rights Reserved
 */

package com.emc.storageos.vnxe.requests;

import java.util.List;

import com.emc.storageos.vnxe.VNXeConstants;
import com.emc.storageos.vnxe.models.VNXeMetricQueryResult;

public class MetricQueryResultRequest extends KHRequests<VNXeMetricQueryResult> {
    private static final String URL_INSTANCES = "/api/types/metricQueryResult/instances";
    private static final String FIELDS = "paths,timestamp,values";

    public MetricQueryResultRequest(KHClient client) {
        super(client);
        _fields = FIELDS;
    }

    /**
     * Get all VNXeMetricQueryResult in the array
     * 
     * @return
     */
    public List<VNXeMetricQueryResult> get() {
        _url = URL_INSTANCES;
        return getDataForObjects(VNXeMetricQueryResult.class);
    }
    
    public List<VNXeMetricQueryResult> getMetricQueryResult(String queryId) {
        _url = URL_INSTANCES;
        String filter = VNXeConstants.ID_FILTER+"\""+queryId+"\"";
        setFilter(filter);
        List<VNXeMetricQueryResult> results = getDataForObjects(VNXeMetricQueryResult.class);
        return results;
    }
}
