/*
 * Copyright (c) 2008-2012 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.api.service.impl.placement;

import com.emc.storageos.db.client.model.Project;
import com.emc.storageos.db.client.model.VirtualArray;
import com.emc.storageos.db.client.model.VirtualPool;
import com.emc.storageos.db.client.model.VolumeTopology;
import com.emc.storageos.volumecontroller.Recommendation;
import com.emc.storageos.volumecontroller.impl.utils.VirtualPoolCapabilityValuesWrapper;

import java.util.List;
import java.util.Map;

public interface Scheduler {
    List getRecommendationsForResources(VirtualArray vArray, Project project, VirtualPool vPool,
            VolumeTopology volumeTopology, VirtualPoolCapabilityValuesWrapper capabilities);
    
    /**
     * Returns the String Name of a scheduler.
     * @return String name
     */
    public String getSchedulerName();
    
    /**
     * Returns true if this specific scheduler can schedule storage for the indicated
     * vpool and vpool use.
     * @param vPool -- Virtual Pool
     * @param vPoolUse - VpoolUse (indicates what type of Vpool it is)
     * @return -- true if this scheduler can schedules storage for this vpool
     */
    public boolean handlesVpool(VirtualPool vPool, VpoolUse vPoolUse);
    
    /**
     * Gets a List of Recommendations for a given VirtualPool and capabilities in specified varray.
     * The VpoolUse must be specified (ROOT Vpool or alternate like SRDF_COPY or VPLEX_HA).
     * @param vArray -- Virtual Array
     * @param project -- Project
     * @param vPool -- Virtual Pool
     * @param performanceParams -- The performance parameters.
     * @param vPoolUse -- Use of Virtual Pool (i.e. whether this Virtual Pool is nested inside
     *    the ROOT virtual pool, e.g. a VPLEX_HA or SRDF_COPY virtual pool within the outer ROOT.)
     * @param capabilities -- the capabilites needed
     * @param currentRecommendations -- any existing recommendations in the current solution set.
     * This is used to let recommendations be based on previously generated recommendations, e.g.
     * SRDF_COPY recommendations are generated from the SRDF source recommendation.
     * @return -- A scheduling solution containing a List of Recommendations.
     * The Recommendations may be any subclass of Recommendation.
     */
    public List<Recommendation> getRecommendationsForVpool(VirtualArray vArray, Project project,
            VirtualPool vPool, VolumeTopology volumeTopology, VpoolUse vPoolUse,
            VirtualPoolCapabilityValuesWrapper capabilities, 
            Map<VpoolUse, List<Recommendation>> currentRecommendations);
}
