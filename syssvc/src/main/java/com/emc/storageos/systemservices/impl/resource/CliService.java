/**
* Copyright 2015 EMC Corporation
* All Rights Reserved
 */
/**
 * Copyright (c) 2012 EMC Corporation
 * All Rights Reserved
 *
 * This software contains the intellectual property of EMC Corporation
 * or is licensed to EMC Corporation from third parties.  Use of this
 * software and the intellectual property contained therein is expressly
 * limited to the terms and conditions of the License Agreement under which
 * it is provided by or on behalf of EMC.
 */
package com.emc.storageos.systemservices.impl.resource;

import com.emc.storageos.systemservices.impl.security.AnonymousAccessFilter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.MediaType;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.InputStream;

@Path(AnonymousAccessFilter.CLI_PATH)
public class CliService {
    private static final Logger _log = LoggerFactory.getLogger(CliService.class);
    private String _filePath;

    public void setFilePath(String filePath) {
        _filePath = filePath;
    }

    /**
     * Get ViPR cli
     * @brief Get ViPR CLI
     * @prereq none
     * @return cli details.
     */
    @GET
    @Produces({ MediaType.APPLICATION_OCTET_STREAM })
    public Response getCli() throws FileNotFoundException{
        _log.debug("getCli(): start");

        final InputStream in = new FileInputStream(_filePath);
        return Response.ok(in).header("content-disposition","attachment; filename = ViPR-cli.tar.gz").build();
    }
}
