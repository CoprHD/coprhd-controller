/*
 * Copyright (c) 2015 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.model.pools;

import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlRootElement;
import java.util.ArrayList;
import java.util.List;

@XmlRootElement(name = "storage_pool_recommendations")
public class StoragePoolRecommendations {
    private List<StoragePoolRecommendation> poolRecommedations;

    public StoragePoolRecommendations() {
    }

    public StoragePoolRecommendations(List<StoragePoolRecommendation> recs) {
        this.poolRecommedations = recs;
    }

    /**
     * List of storage pool recommendations
     * 
     */
    @XmlElement(name = "storage_pool_recommendation")
    public List<StoragePoolRecommendation> getPoolRecommendations() {
        if (poolRecommedations == null) {
        	poolRecommedations = new ArrayList<StoragePoolRecommendation>();
        }
        return poolRecommedations;
    }

    public void setPoolRecommendations(List<StoragePoolRecommendation> poolRecommedations) {
        this.poolRecommedations = poolRecommedations;
    }
    
    public void addPoolRecommendations(StoragePoolRecommendation poolRecommedation) {
    	if (this.poolRecommedations == null) {
        	this.poolRecommedations = new ArrayList<StoragePoolRecommendation>();
        }
        this.poolRecommedations.add(poolRecommedation);
    }
}
