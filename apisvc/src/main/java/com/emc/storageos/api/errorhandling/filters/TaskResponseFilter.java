/*
 * Copyright 2015 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.api.errorhandling.filters;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.emc.storageos.model.TaskList;
import com.emc.storageos.model.TaskResourceRep;
import com.sun.jersey.spi.container.ContainerRequest;
import com.sun.jersey.spi.container.ContainerResponse;
import com.sun.jersey.spi.container.ContainerResponseFilter;

public class TaskResponseFilter implements ContainerResponseFilter {

    private static final Logger _log = LoggerFactory.getLogger(TaskResponseFilter.class);

    @Override
    public ContainerResponse filter(ContainerRequest request,
            ContainerResponse response) {

        if (!request.getMethod().equals("POST")
                && !request.getMethod().equals("DELETE")) {
            return response;
        }

        if (response.getEntity() == null) {
            return response;
        }
        if (response.getStatus() != 200) {
            return response;
        }

        if (response.getEntity() instanceof TaskResourceRep || response.getEntity() instanceof TaskList) {
            response.setStatus(202);
            _log.info("Changed the status of the response for task/task list : " + response.getEntity());
        }
        return response;

    }

}
