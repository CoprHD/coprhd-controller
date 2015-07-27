/*
 * Copyright (c) 2012-2015 iWave Software LLC
 * All Rights Reserved
 */
/**
 * 
 */
package com.iwave.ext.netapp;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

import netapp.manage.NaElement;
import netapp.manage.NaServer;

import org.apache.log4j.Logger;
import com.google.common.collect.Maps;
import com.google.common.collect.Sets;

/**
 * @author sdorcas
 * Model of a a NetApp aggregate (storage pool)
 */
public class Aggregate {

	private Logger log = Logger.getLogger(getClass());
	
	private String name = "";
	private NaServer server = null;
	
	public Aggregate (NaServer server, String name)
	{
		this.name = name;
		this.server = server;
	}
	
	List<AggregateInfo> listAllAggregates(boolean listAll)
	{
		ArrayList<AggregateInfo> aggrs = new ArrayList<AggregateInfo>();
		
		NaElement elem = new NaElement("aggr-list-info");
		if( !listAll ) {
			elem.addNewChild("aggregate", name);
		}
		
		NaElement result = null;
		try {
			result = server.invokeElem(elem).getChildByName("aggregates");
		} catch( Exception e ) {
			String msg = "Failed to get Aggregate list info.";
			log.error(msg);
			throw new NetAppException(msg, e);
		}
		
		/*
		 * list disks, so we can add the disk-type to AggregateInfo
		 * we assume that all disks in an aggregate are the same type,
		 * rather than explicitly listing the type of each disk.
		 */

        elem = new NaElement("disk-list-info");
        NaElement diskResult = null;
        try {
            diskResult = server.invokeElem(elem).getChildByName("disk-details");
        }
        catch (Exception e) {
            String msg = "Failed to get disk list info.";
			log.error(msg);
			throw new NetAppException(msg, e);
        }
        
        Map<String,Set<String>> aggrDiskTypes = Maps.newHashMap();
        Map<String,Set<String>> aggrDiskSpeeds = Maps.newHashMap();
        
        for (NaElement disk : (List<NaElement>) diskResult.getChildren()) {
            String aggregate = disk.getChildContent("aggregate");
            String dtype = disk.getChildContent("disk-type");
            int rpmNumber = disk.getChildIntValue("rpm", 0);
            // Prettify the RPM
            String rpm = formatRPMs(rpmNumber);
            
            if (aggregate != null) {
                Set<String> diskTypes = aggrDiskTypes.get(aggregate);
                if (diskTypes == null) {
                    diskTypes = Sets.newLinkedHashSet();
                    aggrDiskTypes.put(aggregate, diskTypes);
                }
                Set<String> diskSpeeds = aggrDiskSpeeds.get(aggregate);
                if (diskSpeeds == null) {
                    diskSpeeds = Sets.newLinkedHashSet();
                    aggrDiskSpeeds.put(aggregate, diskSpeeds);
                }
                
                diskTypes.add(dtype);
                diskSpeeds.add(rpm);
            }
        }
		
		
		for (NaElement aggr: (List<NaElement>)result.getChildren()) {
			AggregateInfo info = new AggregateInfo();
			info.setDiskCount(aggr.getChildIntValue("disk-count", -1));
			info.setName(aggr.getChildContent("name"));
			info.setRaidStatus(aggr.getChildContent("raid-status"));
			info.setSizeAvailable(aggr.getChildLongValue("size-available", -1));
			info.setSizeTotal(aggr.getChildLongValue("size-total", -1));
			info.setSizeUsed(aggr.getChildLongValue("size-used", -1));
			info.setState(aggr.getChildContent("state"));
			info.setVolumeCount(aggr.getChildIntValue("volume-count", -1));
			
			ArrayList<String> volNames = new ArrayList<String>();
            for (NaElement vol : (List<NaElement>) aggr.getChildByName("volumes").getChildren()) {
                volNames.add(vol.getChildContent("name"));
            }
            info.setVolumes(volNames);
            
            info.setDiskTypes(aggrDiskTypes.get(info.getName()));
            info.setDiskSpeeds(aggrDiskSpeeds.get(info.getName()));
			aggrs.add(info);
		}
		return aggrs;
	}
	
    private String formatRPMs(int rpms) {
        if ((rpms % 1000) == 0) {
            return String.format("%dk", (rpms / 1000));
        }
        else {
            return String.format("%.1fk", (rpms / 1000.0));
        }
    }
}
