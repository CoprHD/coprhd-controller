/*
 * Copyright 2015 EMC Corporation
 * All Rights Reserved
 */
/**
 *  Copyright (c) 2014 EMC Corporation
 * All Rights Reserved
 *
 * This software contains the intellectual property of EMC Corporation
 * or is licensed to EMC Corporation from third parties.  Use of this
 * software and the intellectual property contained therein is expressly
 * limited to the terms and conditions of the License Agreement under which
 * it is provided by or on behalf of EMC.
 */

package com.emc.storageos.geo.service.impl.resource;

import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.core.MediaType;

import org.springframework.beans.factory.annotation.Autowired;

import com.emc.storageos.coordinator.client.model.RepositoryInfo;
import com.emc.storageos.coordinator.client.service.CoordinatorClient;
import com.emc.storageos.svcs.errorhandling.resources.APIException;

import static com.emc.storageos.security.geo.GeoServiceClient.VERSION_URI;

@Path(value=VERSION_URI)
public class VersionService {
    @Autowired
    private CoordinatorClient coordinator;

    /**
     * Return the version of the ViPR software running on this system
     * 
     * @return the version string (e.g. vipr-1.0.0.1.1)
     */
    @GET
    @Produces({ MediaType.TEXT_PLAIN })    
    public String getVersion() {
        try {
            RepositoryInfo info = coordinator.getTargetInfo(RepositoryInfo.class);
            return info.getCurrentVersion().toString();
        } catch (Exception ex) {
            throw APIException.internalServerErrors.genericApisvcError("error retrieving ViPR version", ex);
        }
    }
}
