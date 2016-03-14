/*
 * Copyright (c) 2008-2012 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.api.service.impl.placement;

import com.emc.storageos.db.client.model.Project;
import com.emc.storageos.db.client.model.VirtualArray;
import com.emc.storageos.db.client.model.VirtualPool;
import com.emc.storageos.volumecontroller.Recommendation;
import com.emc.storageos.volumecontroller.impl.utils.VirtualPoolCapabilityValuesWrapper;

import java.util.List;
import java.util.Map;
import java.util.Set;

public interface Scheduler {
    List getRecommendationsForResources(VirtualArray vArray, Project project, VirtualPool vPool,
            VirtualPoolCapabilityValuesWrapper capabilities);
    
    /**
     * Get sets of recommendations for a Vpool. Each set is a possible independent solution.
     * At first only one set may be supported (i.e. a scheduler may only calculate one possible
     * solution); but this interface allows for multiple possible solutions.
     * @param vArray -- Virtual Array
     * @param project -- Project
     * @param vPool -- Virtual Pool
     * @param vPoolUse -- Use of Virtual Pool (i.e. whether this Virtual Pool is nested inside
     *    the ROOT virtual pool, e.g. a VPLEX_HA or SRDF_COPY virtual pool within the outer ROOT.)
     * @param capabilities -- the capabilites needed
     * @param currentRecommendations -- any existing recommendations in the current solution set
     * @return -- A scheduling solution containing a List of Recommendations.
     * The Recommendations may be any subclass of Recommendation.
     */
    List<Recommendation> getRecommendationsForVpool(
            VirtualArray vArray, Project project, 
            VirtualPool vPool, VpoolUse vPoolUse,
            VirtualPoolCapabilityValuesWrapper capabilities, 
            Map<VpoolUse, List<Recommendation>> currentRecommendations);
}
