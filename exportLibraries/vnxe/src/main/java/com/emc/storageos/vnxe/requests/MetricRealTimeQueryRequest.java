/*
 * Copyright (c) 2014 EMC Corporation
 * All Rights Reserved
 */

package com.emc.storageos.vnxe.requests;

import java.util.List;

import com.emc.storageos.vnxe.models.MetricRealTimeQueryCreateParam;
import com.emc.storageos.vnxe.models.VNXeCommandResult;
import com.emc.storageos.vnxe.models.VNXeMetricRealTimeQuery;

public class MetricRealTimeQueryRequest extends KHRequests<VNXeMetricRealTimeQuery> {
    private static final String URL_RESOURCE = "/api/instances/metricRealTimeQuery/";
    private static final String URL_INSTANCES = "/api/types/metricRealTimeQuery/instances";
    private static final String FIELDS = "paths,interval,expiration";

    public MetricRealTimeQueryRequest(KHClient client) {
        super(client);
        _fields = FIELDS;
    }

    /**
     * Get all VNXeMetricRealTimeQuery in the array
     * 
     * @return
     */
    public List<VNXeMetricRealTimeQuery> get() {
        _url = URL_INSTANCES;
        return getDataForObjects(VNXeMetricRealTimeQuery.class);
    }

    /**
     * Get a VNXeMetricRealTimeQuery's detail using its id.
     * 
     * @param lunId
     * @return
     */
    public VNXeMetricRealTimeQuery getMetricRealTimeQuery(String queryId) {
        _url = URL_RESOURCE + queryId;
        return getDataForOneObject(VNXeMetricRealTimeQuery.class);
    }

    public VNXeCommandResult createMetricRealTimeQuery(MetricRealTimeQueryCreateParam param) {
        _url = URL_INSTANCES;

        VNXeCommandResult result = postRequestSync(param);
        result.setSuccess(true);
        return result;
    }
}
