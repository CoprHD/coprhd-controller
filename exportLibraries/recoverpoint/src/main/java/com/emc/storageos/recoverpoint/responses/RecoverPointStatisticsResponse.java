/*
 * Copyright (c) 2013 EMC Corporation
 * All Rights Reserved
 */

package com.emc.storageos.recoverpoint.responses;

import java.util.List;
import java.util.Map;

public class RecoverPointStatisticsResponse {
    private Map<Long, Double> siteAvgCPUUsageMap;
    private Map<Long, Long> siteInputAvgIncomingWrites;
    private Map<Long, Long> siteInputAvgThroughput;
    private Map<Long, Long> siteOutputAvgThroughput;

    public class ProtectionSystemParameters {
        public String parameterName;
        public long parameterLimit;
        public long currentParameterValue;
        public long siteID;
    }

    private List<ProtectionSystemParameters> paramList;

    public Map<Long, Double> getSiteAvgCPUUsageMap() {
        return siteAvgCPUUsageMap;
    }

    public void setSiteCPUUsageMap(Map<Long, Double> siteAvgCPUUsageMap) {
        this.siteAvgCPUUsageMap = siteAvgCPUUsageMap;
    }

    public Map<Long, Long> getSiteInputAvgThroughput() {
        return siteInputAvgThroughput;
    }

    public void setSiteInputAvgThroughput(Map<Long, Long> siteInputThroughput) {
        siteInputAvgThroughput = siteInputThroughput;
    }

    public Map<Long, Long> getSiteOutputAvgThroughput() {
        return siteOutputAvgThroughput;
    }

    public void setSiteOutputAvgThroughput(Map<Long, Long> siteOutputThroughput) {
        siteOutputAvgThroughput = siteOutputThroughput;
    }

    public Map<Long, Long> getSiteInputAvgIncomingWrites() {
        return siteInputAvgIncomingWrites;
    }

    public void setSiteInputAvgIncomingWrites(
            Map<Long, Long> siteInputAvgIncomingWrites) {
        this.siteInputAvgIncomingWrites = siteInputAvgIncomingWrites;
    }

    public List<ProtectionSystemParameters> getParamList() {
        return paramList;
    }

    public void setParamList(List<ProtectionSystemParameters> protectionSystemParameterList) {
        this.paramList = protectionSystemParameterList;
    }
}
