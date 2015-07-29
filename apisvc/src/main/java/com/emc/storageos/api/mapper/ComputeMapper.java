/*
 * Copyright 2015 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.api.mapper;

import static com.emc.storageos.api.mapper.DbObjectMapper.mapDataObjectFields;
import static com.emc.storageos.api.mapper.DbObjectMapper.mapDiscoveredSystemObjectFields;
import static com.emc.storageos.api.mapper.DbObjectMapper.toRelatedResource;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import com.emc.storageos.db.client.model.ComputeElement;
import com.emc.storageos.db.client.model.ComputeImage;
import com.emc.storageos.db.client.model.ComputeSystem;
import com.emc.storageos.model.ResourceTypeEnum;
import com.emc.storageos.model.compute.ComputeElementRestRep;
import com.emc.storageos.model.compute.ComputeImageRestRep;
import com.emc.storageos.model.compute.ComputeSystemRestRep;

public class ComputeMapper {
    public static ComputeSystemRestRep map(ComputeSystem from) {
        if (from == null) {
            return null;
        }
        ComputeSystemRestRep to = new ComputeSystemRestRep();
        mapDiscoveredSystemObjectFields(from, to);
        to.setIpAddress(from.getIpAddress());
        to.setPortNumber(from.getPortNumber());
        to.setUseSSL(from.getSecure());
        to.setUsername(from.getUsername());
        to.setVersion(from.getVersion());
        to.setOsInstallNetwork(from.getOsInstallNetwork());

        // sort vlans as numbers
        List<Integer> vlanIds = new ArrayList<Integer>();
        if (from.getVlans() != null) {
            for (String vlan : from.getVlans()) {
                try {
                    vlanIds.add(Integer.parseInt(vlan));
                } catch (NumberFormatException e) {
                    // skip
                }
            }
        }
        Collections.sort(vlanIds);
        StringBuilder vlanStr = null;
        for (int vlanId : vlanIds) {
            if (vlanStr == null) {
                vlanStr = new StringBuilder();
            } else {
                vlanStr.append(",");
            }
            vlanStr.append(vlanId);
        }
        if (vlanStr != null) {  // cannot set null
            to.setVlans(vlanStr.toString());
        }
        return to;
    }

    public static ComputeElementRestRep map(ComputeElement from) {
        if (from == null) {
            return null;
        }
        ComputeElementRestRep to = new ComputeElementRestRep();
        mapDiscoveredSystemObjectFields(from, to);
        to.setRam(from.getRam());
        to.setNumOfCores(from.getNumOfCores());
        to.setNumOfProcessors(from.getNumberOfProcessors());
        to.setNumOfThreads(from.getNumberOfThreads());
        to.setProcessorSpeed(from.getProcessorSpeed());
        to.setUuid(from.getUuid());
        to.setOriginalUuid(from.getOriginalUuid());
        to.setAvailable(from.getAvailable());
        to.setModel(from.getModel());
        to.setComputeSystem(toRelatedResource(ResourceTypeEnum.COMPUTE_SYSTEM, from.getComputeSystem()));
        to.setRegistrationStatus(from.getRegistrationStatus());
        return to;
    }

    public static ComputeImageRestRep map(ComputeImage from) {
        if (from == null) {
            return null;
        }
        ComputeImageRestRep to = new ComputeImageRestRep();
        mapDataObjectFields(from, to);
        to.setImageName(from.getImageName());
        to.setImageUrl(from.getImageUrl());
        to.setImageType(from.getImageType());
        to.setComputeImageStatus(from.getComputeImageStatus());
        to.setLastImportStatusMessage(from.getLastImportStatusMessage());
        return to;
    }

}
