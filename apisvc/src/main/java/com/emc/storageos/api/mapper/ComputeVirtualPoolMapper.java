/*
 * Copyright (c) 2015 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.api.mapper;

import static com.emc.storageos.api.mapper.DbObjectMapper.mapDataObjectFields;
import static com.emc.storageos.api.mapper.DbObjectMapper.toRelatedResource;

import java.net.URI;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import com.emc.storageos.api.service.impl.resource.ArgValidator;
import com.emc.storageos.db.client.DbClient;
import com.emc.storageos.db.client.model.ComputeElement;
import com.emc.storageos.db.client.model.ComputeVirtualPool;
import com.emc.storageos.db.client.model.StringSet;
import com.emc.storageos.db.client.model.UCSServiceProfileTemplate;
import com.emc.storageos.model.NamedRelatedResourceRep;
import com.emc.storageos.model.RelatedResourceRep;
import com.emc.storageos.model.ResourceTypeEnum;
import com.emc.storageos.model.vpool.ComputeVirtualPoolRestRep;
import com.emc.storageos.svcs.errorhandling.resources.APIException;

public class ComputeVirtualPoolMapper {

    public static ComputeVirtualPoolRestRep toComputeVirtualPool(ComputeVirtualPool from, boolean inUse) {
        return toComputeVirtualPool(null, from, inUse);
    }

    private static Integer translateDbValueToClientValue(Integer param) {
        return (param == null || param.equals(0) || param.equals(-1)) ? null : param;  // Cannot null attribute on update so use 0 as null
    }

    public static ComputeVirtualPoolRestRep toComputeVirtualPool(DbClient dbClient, ComputeVirtualPool from, boolean inUse) {
        if (from == null) {
            return null;
        }
        ComputeVirtualPoolRestRep to = new ComputeVirtualPoolRestRep();
        to.setDescription(from.getDescription());
        to.setSystemType(from.getSystemType());
        to.setMinProcessors(translateDbValueToClientValue(from.getMinProcessors()));
        to.setMaxProcessors(translateDbValueToClientValue(from.getMaxProcessors()));
        to.setMinTotalCores(translateDbValueToClientValue(from.getMinTotalCores()));
        to.setMaxTotalCores(translateDbValueToClientValue(from.getMaxTotalCores()));
        to.setMinTotalThreads(translateDbValueToClientValue(from.getMinTotalThreads()));
        to.setMaxTotalThreads(translateDbValueToClientValue(from.getMaxTotalThreads()));
        to.setMinCpuSpeed(translateDbValueToClientValue(from.getMinCpuSpeed()));
        to.setMaxCpuSpeed(translateDbValueToClientValue(from.getMaxCpuSpeed()));
        to.setMinMemory(translateDbValueToClientValue(from.getMinMemory()));
        to.setMaxMemory(translateDbValueToClientValue(from.getMaxMemory()));
        to.setMinNics(translateDbValueToClientValue(from.getMinNics()));
        to.setMaxNics(translateDbValueToClientValue(from.getMaxNics()));
        to.setMinHbas(translateDbValueToClientValue(from.getMinHbas()));
        to.setMaxHbas(translateDbValueToClientValue(from.getMaxHbas()));

        if (from.getVirtualArrays() != null) {
            for (String varray : from.getVirtualArrays()) {
                to.getVirtualArrays().add(toRelatedResource(ResourceTypeEnum.VARRAY, URI.create(varray)));
            }
        }

        to.setUseMatchedElements(from.getUseMatchedElements());
        to.setInUse(inUse);

        if (from.getMatchedComputeElements() != null) {
            for (String ce : from.getMatchedComputeElements()) {
                to.getMatchedComputeElements().add(toRelatedResource(ResourceTypeEnum.COMPUTE_ELEMENT, URI.create(ce)));
            }
        }

        if (dbClient != null) {
            if (from.getServiceProfileTemplates() != null) {
                for (String spt : from.getServiceProfileTemplates()) {
                    URI sptURI = URI.create(spt);
                    UCSServiceProfileTemplate template = dbClient.queryObject(UCSServiceProfileTemplate.class, sptURI);
                    NamedRelatedResourceRep sptNamedRelatedResource = new NamedRelatedResourceRep();
                    sptNamedRelatedResource.setId(template.getId());
                    sptNamedRelatedResource.setName(template.getLabel());
                    to.getServiceProfileTemplates().add(sptNamedRelatedResource);
                }
            }

            if (from.getMatchedComputeElements() != null) {
                to.getAvailableMatchedComputeElements().addAll(getAvailableComputeElements(dbClient, from.getMatchedComputeElements()));
            }
        }

        mapDataObjectFields(from, to);
        return to;
    }

    private static Set<RelatedResourceRep> getAvailableComputeElements(DbClient dbClient, StringSet matchedComputeElements) {

        Set<RelatedResourceRep> returnSet = new HashSet<RelatedResourceRep>();

        Collection<ComputeElement> computeElements = dbClient.queryObject(ComputeElement.class, toUriList(matchedComputeElements));

        for (ComputeElement computeElement : computeElements) {
            if (computeElement.getAvailable()) {
                returnSet.add(toRelatedResource(ResourceTypeEnum.COMPUTE_ELEMENT, computeElement.getId()));
            }
        }

        return returnSet;
    }

    public static List<URI> toUriList(Collection<String> uris) throws APIException {
        List<URI> uriList = new ArrayList<URI>();
        if (uris == null || uris.isEmpty()) {
            return uriList;
        }
        for (String uriString : uris) {
            URI uri = URI.create(uriString);
            ArgValidator.checkUri(uri);
            uriList.add(uri);
        }
        return uriList;
    }

}
