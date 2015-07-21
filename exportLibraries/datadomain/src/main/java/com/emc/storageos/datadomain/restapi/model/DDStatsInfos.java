/*
 * Copyright (c) 2015 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.datadomain.restapi.model;

import java.util.List;

import org.codehaus.jackson.map.annotate.JsonRootName;

import com.google.gson.Gson;

@JsonRootName(value="stats_infos")
public class DDStatsInfos {
	
    private List<DDStatsInfo> stats;

    public List<DDStatsInfo> getStats() {
        return stats;
    }

    public void setStats(List<DDStatsInfo> stats) {
        this.stats = stats;
    }

    public String toString() {
        return new Gson().toJson(this).toString();
    }

}

