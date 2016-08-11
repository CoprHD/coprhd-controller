/*
 * Copyright (c) 2014 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.systemservices.impl.resource;

import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.List;

import javax.servlet.http.HttpServletRequest;
import javax.ws.rs.Consumes;
import javax.ws.rs.FormParam;
import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.emc.storageos.coordinator.client.model.DriverInfo;
import com.emc.storageos.coordinator.client.service.CoordinatorClient;
import com.emc.storageos.coordinator.common.Service;
import com.emc.storageos.security.authorization.CheckPermission;
import com.emc.storageos.security.authorization.Role;
import com.emc.storageos.systemservices.impl.upgrade.CoordinatorClientExt;

/**
 * Defines the API for making requests to the backup service.
 */
@Path("/storagedriver/")
public class DriverService {
    private static final Logger log = LoggerFactory.getLogger(DriverService.class);
    private static final String UPLOAD_DEVICE_DRIVER = "/tmp/";

    private CoordinatorClient coordinator;
    private Service service;
    private CoordinatorClientExt coordinatorExt;

    public void setCoordinator(CoordinatorClient coordinator) {
        this.coordinator = coordinator;
    }

    public void setCoordinatorExt(CoordinatorClientExt coordinatorExt) {
        this.coordinatorExt = coordinatorExt;
    }

    public void setService(Service service) {
        this.service = service;
    }
    @POST
    @Path("upload/")
    @CheckPermission(roles = { Role.SYSTEM_ADMIN, Role.RESTRICTED_SYSTEM_ADMIN })
    @Consumes({ MediaType.APPLICATION_OCTET_STREAM })
    @Produces({ MediaType.APPLICATION_XML, MediaType.APPLICATION_JSON })
    public Response installDriver(@Context HttpServletRequest request, @QueryParam("filename") String name) throws Exception {
        log.info("upload driver ...");
        InputStream driver = request.getInputStream();
        File f = new File(UPLOAD_DEVICE_DRIVER + name);
        OutputStream os = new BufferedOutputStream(new FileOutputStream(f));
        int bytesRead = 0;
        while (true) {
            byte[] buffer = new byte[0x10000];
            bytesRead = driver.read(buffer);
            if (bytesRead == -1) {
                break;
            }
            os.write(buffer, 0, bytesRead);
        }
        driver.close();
        os.close();
        // set target info
        String localNode = coordinatorExt.getNodeEndpointForSvcId(service.getNodeId()).toString();
        List<String> drivers = coordinator.getTargetInfo(DriverInfo.class).getDrivers();
        drivers.add(name);
        DriverInfo info = new DriverInfo(localNode, drivers);
        coordinator.setTargetInfo(info);
        log.info("set target info successfully");
        return Response.ok().build();
    }

    @GET
    @Path("internal/download/")
    @Produces({ MediaType.APPLICATION_OCTET_STREAM })
    public Response getDriver(@QueryParam("name") String name) throws FileNotFoundException {
        log.info("download driver {} ...", name);
        InputStream in = new FileInputStream(UPLOAD_DEVICE_DRIVER + "/" + name);
        return Response.ok(in).type(MediaType.APPLICATION_OCTET_STREAM).build();
    }
    
    // add another internal API, the last node will access to delete tmp file who finished downloading
}
