/*
 * Copyright (c) 2015 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.api.mapper;

import static com.emc.storageos.api.mapper.DbObjectMapper.mapDataObjectFields;
import static com.emc.storageos.api.mapper.DbObjectMapper.mapDiscoveredSystemObjectFields;
import static com.emc.storageos.api.mapper.DbObjectMapper.toRelatedResource;

import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.TimeUnit;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.emc.storageos.api.service.impl.response.RestLinkFactory;
import com.emc.storageos.db.client.DbClient;
import com.emc.storageos.db.client.URIUtil;
import com.emc.storageos.db.client.model.ComputeElement;
import com.emc.storageos.db.client.model.ComputeImage;
import com.emc.storageos.db.client.model.ComputeImageServer;
import com.emc.storageos.db.client.model.ComputeSystem;
import com.emc.storageos.model.NamedRelatedResourceRep;
import com.emc.storageos.model.ResourceTypeEnum;
import com.emc.storageos.model.RestLinkRep;
import com.emc.storageos.model.compute.ComputeElementRestRep;
import com.emc.storageos.model.compute.ComputeImageRestRep;
import com.emc.storageos.model.compute.ComputeImageServerRestRep;
import com.emc.storageos.model.compute.ComputeSystemRestRep;

public class ComputeMapper {
    private static final Logger LOG = LoggerFactory
            .getLogger(ComputeMapper.class);

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
        if (from.getComputeImageServer() != null) {
            to.setComputeImageServer(from.getComputeImageServer().toString());
        } else {
            to.setComputeImageServer("");
        }

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
        if (vlanStr != null) { // cannot set null
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
        to.setComputeSystem(toRelatedResource(ResourceTypeEnum.COMPUTE_SYSTEM,
                from.getComputeSystem()));
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
        List<NamedRelatedResourceRep> availableServersList = new ArrayList<NamedRelatedResourceRep>();
        List<NamedRelatedResourceRep> failedServersList = new ArrayList<NamedRelatedResourceRep>();
        to.setAvailableImageServers(availableServersList);
        to.setFailedImageServers(failedServersList);

        return to;
    }

    public static ComputeImageRestRep map(ComputeImage from,
            List<ComputeImageServer> availableServers,
            List<ComputeImageServer> failedServers) {
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
        List<NamedRelatedResourceRep> availableServersList = new ArrayList<NamedRelatedResourceRep>();
        List<NamedRelatedResourceRep> failedServersList = new ArrayList<NamedRelatedResourceRep>();
        for (ComputeImageServer server : availableServers) {
            NamedRelatedResourceRep serverRep = new NamedRelatedResourceRep();
            serverRep.setId(server.getId());
            serverRep.setName(server.getLabel());
            availableServersList.add(serverRep);
        }
        for (ComputeImageServer server : failedServers) {
            NamedRelatedResourceRep serverRep = new NamedRelatedResourceRep();
            serverRep.setId(server.getId());
            serverRep.setName(server.getLabel());
            failedServersList.add(serverRep);
        }

        to.setAvailableImageServers(availableServersList);
        to.setFailedImageServers(failedServersList);

        return to;
    }

    /**
     * Utility mapper method to map fields of {@link ComputeImageServer} columnFamily to {@link ComputeImageServerRestRep} rest
     * representation.
     * 
     * @param dbclient
     *            {@link DbClient} instance
     * @param from
     *            {@link ComputeImageServer} instance that has to be mapped.
     * @return {@link ComputeImageServerRestRep}
     */
    public static ComputeImageServerRestRep map(DbClient dbclient,
            ComputeImageServer from) {
        if (from == null) {
            return null;
        }
        ComputeImageServerRestRep to = new ComputeImageServerRestRep();
        mapDataObjectFields(from, to);

        try {
            to.setLink(new RestLinkRep("self", RestLinkFactory
                    .simpleServiceLink(ResourceTypeEnum.COMPUTE_IMAGESERVER,
                            from.getId())));
        } catch (URISyntaxException e) {
            LOG.warn("Error while creating self link URI.", e);
        }
        to.setImageServerIp(from.getImageServerIp());
        to.setImageServerSecondIp(from.getImageServerSecondIp());
        to.setTftpBootDir(from.getTftpBootDir());
        to.setComputeImageServerStatus(from.getComputeImageServerStatus());
        to.setImageServerUser(from.getImageServerUser());
        to.setOsInstallTimeout(new Long(TimeUnit.MILLISECONDS.toSeconds(from
                .getOsInstallTimeoutMs())).intValue());
        to.setComputeImages(new ArrayList<NamedRelatedResourceRep>());
        to.setFailedImages(new ArrayList<NamedRelatedResourceRep>());
        if (from.getComputeImages() != null) {
            for (String computeimage : from.getComputeImages()) {
                ComputeImage image = dbclient.queryObject(ComputeImage.class,
                        URIUtil.uri(computeimage));
                to.getComputeImages().add(
                        DbObjectMapper.toNamedRelatedResource(
                                ResourceTypeEnum.COMPUTE_IMAGE, image.getId(),
                                image.getLabel()));
            }
        }
        if (from.getFailedComputeImages() != null) {
            for (String failedImageID : from.getFailedComputeImages()) {
                ComputeImage failedImage = dbclient.queryObject(
                        ComputeImage.class, URIUtil.uri(failedImageID));
                to.getFailedImages().add(
                        DbObjectMapper.toNamedRelatedResource(
                                ResourceTypeEnum.COMPUTE_IMAGE,
                                failedImage.getId(), failedImage.getLabel()));
            }
        }
        return to;
    }

}
