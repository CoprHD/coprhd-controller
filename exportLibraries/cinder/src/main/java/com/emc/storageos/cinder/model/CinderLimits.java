/* Copyright (c) 2015 EMC Corporation
 * All Rights Reserved
 *
 */
package com.emc.storageos.cinder.model;

import java.util.Map;

import javax.xml.bind.annotation.XmlRootElement;

import org.codehaus.jackson.map.annotate.JsonRootName;

@JsonRootName(value="limits")
@XmlRootElement(name="limits")
public class CinderLimits {	
    	//public absoluteStats absolute = new absoluteStats();
		public Map<String, Integer> absolute;
    	public int rate[];
    	
    /*public class absoluteStats{
        int totalSnapshotsUsed;
        int maxTotalVolumeGigabytes;
        int totalGigabytesUsed;
        int maxTotalSnapshots;
        int totalVolumesUsed;
        int maxTotalVolumes;
    }*/	
}
