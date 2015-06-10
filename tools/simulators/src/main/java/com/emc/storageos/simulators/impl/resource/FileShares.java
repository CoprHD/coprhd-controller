/*
 * Copyright 2015 EMC Corporation
 * All Rights Reserved
 */
/**
 *  Copyright (c) 2008-2011 EMC Corporation
 * All Rights Reserved
 *
 * This software contains the intellectual property of EMC Corporation
 * or is licensed to EMC Corporation from third parties.  Use of this
 * software and the intellectual property contained therein is expressly
 * limited to the terms and conditions of the License Agreement under which
 * it is provided by or on behalf of EMC.
 */

package com.emc.storageos.simulators.impl.resource;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.ws.rs.*;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.UriInfo;
import javax.xml.bind.annotation.*;
import java.util.ArrayList;

@Path("/namespace")
public class FileShares extends BaseResource {
    private static Logger _log = LoggerFactory.getLogger(FileShares.class);

    @Context
    UriInfo     _uriInfo;

    @XmlAccessorType(XmlAccessType.FIELD)
    public static class Entry {
        @XmlElement
        private String name = null;
    }

    @XmlRootElement
    public static class GetFileSharesResp {
        @XmlElement(name = "children")
        private ArrayList<Entry> resp = new ArrayList<Entry>();

        public void setResp(ArrayList<Entry> resp) {
            this.resp = resp;
        }

        ArrayList<Entry> getResp() {
            return resp;
        }
    }

    /*
    * listDir
    */
    @GET
    @Produces({MediaType.APPLICATION_JSON})
    @Path("{subResources:.*}")
    public Response getFileShares() {
        String dir = _uriInfo.getPath().substring("/namespace".length());
        GetFileSharesResp resp = new GetFileSharesResp();
        if (_objectStore.checkPath(dir)) {
            ArrayList<String> list = _objectStore.listDir(dir);
            if (list.size() != 0)
                for (String dirStr : list) {
                    Entry fs = new Entry();
                    fs.name = dirStr;
                    resp.getResp().add(fs);
                }
            else
                resp.getResp().add(new Entry());

            return Response.ok(resp).build();
        } else {
            return Response.noContent().build();
        }
    }

    @PUT
    @Path("{subResources:.*}")
    public Response createFileShare(@QueryParam("recursive") @DefaultValue("0") String recursive) {
        String dir = _uriInfo.getPath().substring("/namespace".length());

        try {
            _objectStore.createDir(dir, recursive.equals("1"));

            _log.info("FileShare put: " + dir);

            return Response.ok().build();
        } catch (Exception e) {
            _log.error("createFileShare exception. path : " + dir + ", recursive : " + recursive, e);
            return Response.serverError().build();    
        }           
    }

    @DELETE
    @Path("{subResources:.*}")
    public Response deleteFileShare(@QueryParam("recursive")@DefaultValue("0") String recursive) {
        String dir = _uriInfo.getPath().substring("/namespace".length());

        try {
            if (_objectStore.checkQuotaExist(dir, recursive.equals("1"))) {
                throw new Exception("Cannot delete directory as Quota exist.");
            }
            _objectStore.deleteDir(dir, recursive.equals("1"));

            _log.info("FileShare delete: " + dir);

            return Response.ok().build();
        } catch (Exception e) {
            _log.error("deleteFileShare exception. path : " + dir + ", recursive : " + recursive, e);
            return Response.serverError().build();
        }
    }


}
