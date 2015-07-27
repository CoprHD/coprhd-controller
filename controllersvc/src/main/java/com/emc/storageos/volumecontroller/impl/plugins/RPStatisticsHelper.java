/*
 * Copyright 2015 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.volumecontroller.impl.plugins;

import java.util.List;
import java.util.Set;

import com.emc.storageos.db.client.DbClient;
import com.emc.storageos.db.client.model.ProtectionSystem;
import com.emc.storageos.db.client.model.StringMap;
import com.emc.storageos.recoverpoint.objectmodel.RPSite;
import com.emc.storageos.recoverpoint.responses.RecoverPointStatisticsResponse;
import com.emc.storageos.recoverpoint.responses.RecoverPointStatisticsResponse.ProtectionSystemParameters;

/**
 * Helper class used for collecting RecoverPoint statistics.
 * 
 */
public class RPStatisticsHelper {
    
    private static final String NumberOfGroups = "NUMBER_OF_GROUPS";
    private static final String NumberOfReplicatingSets = "NUMBER_OF_REPLICATING_SETS";
    private static final String RemoteReplicatedArray = "REMOTE_REPLICATED_ARRAY";
    private static final String LocalReplicatedArray = "LOCAL_REPLICATED_ARRAY";
    private static final String NumberOfWLPS = "NUMBER_OF_WLPS";
    private static final String NumberOfClariionHosts = "NUMBER_OF_CLARIION_HOSTS";
    private static final String NumberOfGUIDS = "NUMBER_OF_GUIDS";
    private static final String TotalNumberOfSplitterLUNs = "TOTAL_NUMBER_OF_SPLITTER_LUNS";
    
	public enum CurrentOrMax {
        CURRENT_VALUE,
        MAX_VALUE,
    }
    
    /**
     * Update the protection system with the metrics associated with making smart 
     * placement decisions.
     * <p>
     * Collects the RP System and Site metrics from within the 
     * <code>RecoverPointStatisticsResponse</code> object.
     *
     * @param rpSystem protection system to update
     * @param rpStat stat object to reflect in the protection system
     */
    public void updateProtectionSystemMetrics(ProtectionSystem rpSystem, Set<RPSite> rpSites, RecoverPointStatisticsResponse response, DbClient dbClient) {
        // Update the metrics per cluster
    	for (RPSite site : rpSites) {
	    	rpSystem.setCgCount(getParameter(response.getParamList(), NumberOfGroups, CurrentOrMax.CURRENT_VALUE));
	        rpSystem.setCgCapacity(getParameter(response.getParamList(), NumberOfGroups, CurrentOrMax.MAX_VALUE));
	        rpSystem.setRsetCount(getParameter(response.getParamList(),NumberOfReplicatingSets , CurrentOrMax.CURRENT_VALUE));
	        rpSystem.setRsetCapacity(getParameter(response.getParamList(), NumberOfReplicatingSets, CurrentOrMax.MAX_VALUE));
	        rpSystem.setRemoteReplicationGBCount(getParameter(response.getParamList(), RemoteReplicatedArray, CurrentOrMax.CURRENT_VALUE));
	        rpSystem.setRemoteReplicationGBCapacity(getParameter(response.getParamList(), RemoteReplicatedArray, CurrentOrMax.MAX_VALUE));
	
	        // Site specific entries are in a string map for easy database use
	        // Key: Site ID, Value: Long->String
	        StringMap localRepCountStringMap = new StringMap();
	        localRepCountStringMap.put(site.getInternalSiteName(), "" + getParameter(response.getParamList(), 
	        		LocalReplicatedArray, CurrentOrMax.CURRENT_VALUE, site.getSiteUID()));
	        rpSystem.setSiteLocalReplicationGBCount(localRepCountStringMap);
	
	        StringMap localRepCapacityStringMap = new StringMap();
	        localRepCapacityStringMap.put(site.getInternalSiteName(), "" + getParameter(response.getParamList(), 
	        		LocalReplicatedArray, CurrentOrMax.MAX_VALUE, site.getSiteUID()));
	        rpSystem.setSiteLocalReplicationGBCapacity(localRepCapacityStringMap);
	
	        StringMap sitePathCountStringMap = new StringMap();
	        sitePathCountStringMap.put(site.getInternalSiteName(), "" + getParameter(response.getParamList(), 
	        		NumberOfWLPS, CurrentOrMax.CURRENT_VALUE, site.getSiteUID()));
	        rpSystem.setSitePathCount(sitePathCountStringMap);
	
	        StringMap sitePathCapacityStringMap = new StringMap();
	        sitePathCapacityStringMap.put(site.getInternalSiteName(), "" + getParameter(response.getParamList(), 
	        		NumberOfWLPS, CurrentOrMax.MAX_VALUE, site.getSiteUID()));
	        rpSystem.setSitePathCapacity(sitePathCapacityStringMap);
	
	        StringMap siteVNXSplitterCountStringMap = new StringMap();
	        siteVNXSplitterCountStringMap.put(site.getInternalSiteName(), "" + getParameter(response.getParamList(), 
	        		NumberOfClariionHosts, CurrentOrMax.CURRENT_VALUE, site.getSiteUID()));
	        rpSystem.setSiteVNXSplitterCount(siteVNXSplitterCountStringMap);
	
	        StringMap siteVNXSplitterCapacityStringMap = new StringMap();
	        siteVNXSplitterCapacityStringMap.put(site.getInternalSiteName(), "" + getParameter(response.getParamList(), 
	        		NumberOfClariionHosts, CurrentOrMax.MAX_VALUE, site.getSiteUID()));
	        rpSystem.setSiteVNXSplitterCapacity(siteVNXSplitterCapacityStringMap);
	
	        StringMap siteVolumeCountStringMap = new StringMap();
	        siteVolumeCountStringMap.put(site.getInternalSiteName(), "" + getParameter(response.getParamList(), 
	        		NumberOfGUIDS, CurrentOrMax.CURRENT_VALUE, site.getSiteUID()));
	        rpSystem.setSiteVolumeCount(siteVolumeCountStringMap);
	
	        StringMap siteVolumeCapacityStringMap = new StringMap();
	        siteVolumeCapacityStringMap.put(site.getInternalSiteName(), "" + getParameter(response.getParamList(), 
	        		NumberOfGUIDS, CurrentOrMax.MAX_VALUE, site.getSiteUID()));
	        rpSystem.setSiteVolumeCapacity(siteVolumeCapacityStringMap);
	
	        StringMap siteVolumesAttachedToSplitterCountStringMap = new StringMap();
	        siteVolumesAttachedToSplitterCountStringMap.put(site.getInternalSiteName(), "" + getParameter(response.getParamList(), 
	        		TotalNumberOfSplitterLUNs, CurrentOrMax.CURRENT_VALUE, site.getSiteUID()));
	        rpSystem.setSiteVolumesAttachedToSplitterCount(siteVolumesAttachedToSplitterCountStringMap);
	
	        StringMap siteVolumesAttachedToSplitterCapacityStringMap = new StringMap();
	        siteVolumesAttachedToSplitterCapacityStringMap.put(site.getInternalSiteName(), "" + getParameter(response.getParamList(), 
	        		TotalNumberOfSplitterLUNs, CurrentOrMax.MAX_VALUE, site.getSiteUID()));
	        rpSystem.setSiteVolumesAttachedToSplitterCapacity(siteVolumesAttachedToSplitterCapacityStringMap);
	        
	        dbClient.persistObject(rpSystem);
    	}
    }      

    /**
     * Get a parameter value (current or max) from a RecoverPointStatisticsResponse parameter list
     *
     * @param params - list of parameters
     * @param whichParam - parameter we are interested in
     * @param whichValue - which value, max or current?
     *
     */
    private long getParameter(List<RecoverPointStatisticsResponse.ProtectionSystemParameters> params,
            String whichParam,
            CurrentOrMax whichValue) {
        return getParameter(params, whichParam, whichValue, -1);
    }
   /**
     * Get a parameter value (current or max) from a RecoverPointStatisticsResponse parameter list
     *
     * @param params - list of parameters
     * @param whichParam - parameter we are interested in
     * @param whichValue - which value, max or current?
     *
     */
    private static long getParameter(List<RecoverPointStatisticsResponse.ProtectionSystemParameters> params,
            String whichParam,
            CurrentOrMax whichValue,
            long siteID) {
    	
        for (ProtectionSystemParameters monitoredParameter : params) {
            if (monitoredParameter.parameterName.equalsIgnoreCase(whichParam)) {
                if (CurrentOrMax.CURRENT_VALUE.equals(whichValue)) {
                    if (siteID == monitoredParameter.siteID || siteID == -1) {
                        return monitoredParameter.currentParameterValue;
                    }
                } else {
                    return monitoredParameter.parameterLimit;
                }
            }
        }

        return 0;

    }
    
}
