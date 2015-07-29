/*
 * Copyright (c) 2012-2015 iWave Software LLC
 * All Rights Reserved
 */
package com.iwave.ext.netappc;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.apache.log4j.Logger;

import netapp.manage.NaElement;
import netapp.manage.NaServer;

import com.google.common.collect.Maps;
import com.google.common.collect.Sets;
import com.iwave.ext.netapp.AggregateInfo;

public class NetAppCAggregate {

    private Logger log = Logger.getLogger(getClass());

    private String name = "";
    private NaServer server = null;

    public NetAppCAggregate(NaServer server, String name)
    {
        this.name = name;
        this.server = server;
    }

    public List<AggregateInfo> listAllAggregates(boolean listAll)
    {
        ArrayList<AggregateInfo> aggrs = new ArrayList<AggregateInfo>();
        NaElement result = null;
        List aggrList = null;
        Iterator iter = null;
        String tag = null;

        /*
         * list disks, so we can add the disk-type to AggregateInfo
         */

        NaElement elem = new NaElement("storage-disk-get-iter");
        NaElement diskAttributes = null;
        List storageDiskList = null;
        Iterator storageDiskIter = null;
        Map<String, Set<String>> aggrDiskTypes = Maps.newHashMap();
        Map<String, Set<String>> aggrDiskSpeeds = Maps.newHashMap();

        try {
            do {
                NaElement results = server.invokeElem(elem);
                tag = results.getChildContent("next-tag");
                diskAttributes = results.getChildByName("attributes-list");
                if (diskAttributes != null) {
                    storageDiskList = diskAttributes.getChildren();
                    storageDiskIter = storageDiskList.iterator();
                    while (storageDiskIter.hasNext()) {
                        NaElement storageDisk = (NaElement) storageDiskIter.next();
                        NaElement raidInfo = storageDisk.getChildByName("disk-raid-info");
                        NaElement inventoryInfo = storageDisk.getChildByName("disk-inventory-info");
                        String diskType = inventoryInfo.getChildContent("disk-type");
                        String serialNumber = inventoryInfo.getChildContent("serial-number");
                        int rpmNumber = inventoryInfo.getChildIntValue("rpm", 0);
                        // Prettify the RPM
                        String rpm = formatRPMs(rpmNumber);
                        String uid = storageDisk.getChildContent("disk-uid");
                        String name = storageDisk.getChildContent("disk-name");
                        String containerType = raidInfo.getChildContent("container-type");
                        NaElement diskAggrInfo = raidInfo.getChildByName("disk-aggregate-info");
                        if (diskAggrInfo != null) {
                            String aggregate = diskAggrInfo.getChildContent("aggregate-name");
                            System.out.println("Aggregate Name      : " + aggregate);
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

                            diskTypes.add(diskType);
                            diskSpeeds.add(rpm);
                        }
                    }
                }
                if (tag != null && !tag.isEmpty()) {
                    elem = new NaElement("storage-disk-get-iter");
                    elem.addNewChild("tag", tag);
                }
            } while (tag != null && !tag.isEmpty());
        } catch (Exception e) {
            String msg = "Failed to get disk list info.";
            log.error(msg);
            throw new NetAppCException(msg, e);
        }

        elem = new NaElement("aggr-get-iter");
        if (!listAll) {
            elem.addNewChild("aggregate", name);
        }

        try {
            do {
                NaElement results = server.invokeElem(elem);
                if (results.getChildIntValue("num-records", 0) == 0) {
                    return aggrs;
                }
                tag = results.getChildContent("next-tag");
                result = results.getChildByName("attributes-list");
                if (result != null) {
                    aggrList = result.getChildren();
                    iter = aggrList.iterator();
                    while (iter.hasNext()) {
                        NaElement aggr = (NaElement) iter.next();
                        NaElement aggrSizeAttrs = aggr.getChildByName("aggr-space-attributes");
                        NaElement aggrRaidAttrs = aggr.getChildByName("aggr-raid-attributes");
                        NaElement aggrVolumeCountAttrs = aggr.getChildByName("aggr-volume-count-attributes");
                        AggregateInfo info = new AggregateInfo();
                        info.setName(aggr.getChildContent("aggregate-name"));
                        info.setSizeAvailable(aggrSizeAttrs.getChildLongValue("size-available", -1));
                        info.setSizeTotal(aggrSizeAttrs.getChildLongValue("size-total", -1));
                        info.setSizeUsed(aggrSizeAttrs.getChildLongValue("size-used", -1));

                        info.setDiskCount(aggrRaidAttrs.getChildIntValue("disk-count", -1));
                        info.setRaidStatus(aggrRaidAttrs.getChildContent("raid-status"));
                        info.setState(aggrRaidAttrs.getChildContent("state"));

                        info.setVolumeCount(aggrVolumeCountAttrs.getChildIntValue("flexvol-count", -1));

                        info.setDiskTypes(aggrDiskTypes.get(info.getName()));
                        info.setDiskSpeeds(aggrDiskSpeeds.get(info.getName()));
                        aggrs.add(info);
                    }
                }
                if (tag != null && !tag.isEmpty()) {
                    elem = new NaElement("aggr-get-iter");
                    elem.addNewChild("tag", tag);
                }
            } while (tag != null && !tag.isEmpty());
        } catch (Exception e) {
            String msg = "Failed to get Aggregate list info.";
            log.error(msg);
            throw new NetAppCException(msg, e);
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
