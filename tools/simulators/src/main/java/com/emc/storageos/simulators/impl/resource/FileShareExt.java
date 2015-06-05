/**
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

import javax.ws.rs.*;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.UriInfo;
import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlRootElement;
import java.util.ArrayList;

@Path("/helper/1")
public class FileShareExt extends BaseResource {

    @Context
    UriInfo _uriInfo;

    @XmlAccessorType(XmlAccessType.FIELD)
    public static class Entry {
        @XmlElement
        private String name = null;
    }

    @XmlRootElement
    public static class GetFileSharesResp {
        @XmlElement(name = "entries")
        private ArrayList<Entry> resp = new ArrayList<Entry>();

        public void setResp(ArrayList<Entry> resp) {
            this.resp = resp;
        }

        ArrayList<Entry> getResp() {
            return resp;
        }
    }

    @XmlRootElement
    public static class GetCountResp {
        @XmlElement
        private int count;
    }

    @GET
    @Produces({MediaType.APPLICATION_JSON})
    @Path("/count/{subResources:.*}")
    public Response getFileSharesCount(@QueryParam("recursive") @DefaultValue("0") String recursive) {
        String dir = _uriInfo.getPath().substring("/helper/1/count".length());

        GetCountResp resp = new GetCountResp();
        if (_objectStore.checkPath(dir)) {
            resp.count = _objectStore.getDirCount(dir, recursive.equals("1"));
            return Response.ok(resp).build();
        } else {
            return Response.ok(resp).build();
        }
    }

    @GET
    @Produces({MediaType.APPLICATION_JSON})
    @Path("/list/{subResources:.*}")
    public Response getFileSharesRecursive() {
        String dir = _uriInfo.getPath().substring("/helper/1/list".length());

        GetFileSharesResp resp = new GetFileSharesResp();
        if (_objectStore.checkPath(dir)) {
            ArrayList<String> list = getListDirRecursive(dir);
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

    /**
     * Get directory list recursively
     * @param   dir  root directory
     * @return  sub directory list
     */
    private ArrayList<String> getListDirRecursive(String dir) {
        ArrayList<String> list = new ArrayList<String>();

        ArrayList<String> level1 = _objectStore.listDir(dir);
        for (int i = 0; i < level1.size(); i++) {
            String fullPath = dir + "/" + level1.get(i);
            list.add(fullPath);

            ArrayList<String> level2 = getListDirRecursive(fullPath);
            list.addAll(level2);
        }

        return list;
    }
}
