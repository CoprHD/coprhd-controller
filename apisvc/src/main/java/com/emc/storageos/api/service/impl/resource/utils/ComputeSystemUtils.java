/*
 * Copyright 2015 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.api.service.impl.resource.utils;

import java.net.URI;

import com.emc.storageos.api.service.impl.resource.ArgValidator;
import com.emc.storageos.db.client.DbClient;
import com.emc.storageos.db.client.model.ComputeSystem;
import com.emc.storageos.db.client.model.DiscoveredDataObject.RegistrationStatus;
import com.emc.storageos.svcs.errorhandling.resources.APIException;
import com.emc.storageos.svcs.errorhandling.resources.BadRequestException;

public class ComputeSystemUtils {

    /**
     * Gets the compute system with the passed id from the database.
     * 
     * @param id
     *            the URN of a ViPR compute system
     * 
     * @return A detailed representation of the registered ComputeSystem.
     * 
     * @throws BadRequestException
     *             When the compute system is not registered.
     */
    public static ComputeSystem queryRegisteredSystem(URI id, DbClient _dbClient, boolean isIdEmbeddedInURL) {
        ArgValidator.checkUri(id);
        ComputeSystem system = _dbClient.queryObject(ComputeSystem.class, id);
        ArgValidator.checkEntityNotNull(system, id, isIdEmbeddedInURL);
        if (!RegistrationStatus.REGISTERED.toString().equalsIgnoreCase(system.getRegistrationStatus())) {
            throw APIException.badRequests.resourceNotRegistered(ComputeSystem.class.getSimpleName(), id);
        }
        return system;
    }
}
