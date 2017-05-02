/*
 * Copyright (c) 2011-2014 EMC Corporation
 * All Rights Reserved
 */

package com.emc.storageos.geo.service.impl.resource;

import java.io.*;
import java.lang.reflect.InvocationTargetException;
import java.util.Iterator;
import java.util.List;
import java.net.URI;
import javax.ws.rs.*;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.StreamingOutput;

import com.emc.storageos.api.service.impl.resource.ArgValidator;
import com.emc.storageos.db.client.constraint.ConstraintDescriptor;
import com.emc.storageos.db.client.util.KeyspaceUtil;
import com.emc.storageos.db.common.DependencyChecker;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.emc.storageos.db.client.constraint.Constraint;
import com.emc.storageos.db.client.constraint.QueryResultList;
import com.emc.storageos.db.client.model.DataObject;
import com.emc.storageos.db.client.DbClient;
import com.emc.storageos.db.exceptions.DatabaseException;

import com.emc.storageos.security.geo.GeoServiceClient;
import com.emc.storageos.svcs.errorhandling.resources.APIException;

import com.emc.storageos.model.BulkIdParam;

import com.emc.storageos.geomodel.ResourcesResponse;

/**
 * query "GeoVisibilty" objects
 */
@Path(GeoServiceClient.INTERVDC_URI)
public class QueryService {
    private static final Logger log = LoggerFactory.getLogger(QueryService.class);

    private DbClient dbClient;
    private DependencyChecker dependencyChecker;

    public DbClient getDbClient() {
        return dbClient;
    }

    public void setDbClient(DbClient dbClient) {
        this.dbClient = dbClient;
    }

    public void setDependencyChecker(DependencyChecker dependencyChecker) {
        this.dependencyChecker = dependencyChecker;
    }

    private StreamingOutput getStreamingOutput(final Object obj) {

        return new StreamingOutput() {
            @Override
            public void write(OutputStream outputStream) {
                ObjectOutputStream out = null;
                try {
                    out = new ObjectOutputStream(outputStream);
                    out.writeObject(obj);
                } catch (Exception e) {
                    log.error("Failed to write ResourceIDsResponse", e);
                } finally {
                    try {
                        if (out != null) {
                            out.close();
                        }
                    } catch (IOException e) {
                        log.error("stream close error", e);
                    }
                }
            }
        };
    }

    @GET
    @Path(GeoServiceClient.GEO_VISIBLE + "{name}")
    @Produces({ MediaType.APPLICATION_OCTET_STREAM })
    public Response queryByType(@PathParam("name") String className, @QueryParam("active_only") boolean activeOnly,
            @QueryParam("start_id") URI startId, @QueryParam("max_count") Integer maxCount) throws DatabaseException {

        ArgValidator.checkFieldNotNull(className, "name");

        try {
            Class clazz = Class.forName(className);
            List<URI> ids;
            if (maxCount == null) {
                ids = dbClient.queryByType(clazz, activeOnly);
            } else {
                ids = dbClient.queryByType(clazz, activeOnly, startId, maxCount);
            }

            return genResourcesResponse(ids.iterator());
        } catch (ClassNotFoundException e) {
            log.error("e=", e);
            throw APIException.badRequests.invalidParameter("name", className);
        }
    }

    @GET
    @Path(GeoServiceClient.GEO_VISIBLE + "{name}/object/{id}")
    @Produces({ MediaType.APPLICATION_OCTET_STREAM })
    public Response queryObject(@PathParam("name") String className, @PathParam("id") URI id) throws DatabaseException {
        ArgValidator.checkFieldNotNull(className, "name");
        ArgValidator.checkUri(id);

        try {
            Class clazz = Class.forName(className);
            DataObject obj = dbClient.queryObject(clazz, id);
            return Response.ok(getStreamingOutput(obj), MediaType.APPLICATION_OCTET_STREAM).build();
        } catch (ClassNotFoundException e) {
            log.error("e=", e);
            throw APIException.badRequests.invalidParameter("name", className);
        }
    }

    @GET
    @Path(GeoServiceClient.DEPENDENCIES + "{name}/{id}/")
    @Produces({ MediaType.APPLICATION_XML, MediaType.APPLICATION_JSON })
    public String checkDependencies(@PathParam("name") String className, @PathParam("id") URI id,
            @QueryParam("active_only") boolean activeOnly) throws DatabaseException {
        ArgValidator.checkFieldNotNull(className, "name");
        ArgValidator.checkUri(id);

        try {
            Class clazz = Class.forName(className);

            if (!KeyspaceUtil.isGlobal(clazz)) {
                Throwable cause = new Throwable(className + " is not in geodb");
                throw APIException.badRequests.invalidParameterWithCause("name", className, cause);
            }

            String dependency = dependencyChecker.checkDependencies(id, clazz, activeOnly);

            if (dependency != null) {
                return dependency;
            }

            return "";

        } catch (ClassNotFoundException e) {
            log.error("e=", e);
            throw APIException.badRequests.invalidParameter("name", className);
        }
    }

    @POST
    @Path("geo-visible/{name}/objects")
    @Consumes({ MediaType.APPLICATION_XML, MediaType.APPLICATION_JSON })
    @Produces({ MediaType.APPLICATION_OCTET_STREAM })
    public Response queryObjects(@PathParam("name") String className, BulkIdParam ids) throws DatabaseException {
        ArgValidator.checkFieldNotNull(className, "name");

        try {
            Class clazz = Class.forName(className);
            Iterator<DataObject> it = dbClient.queryIterativeObjects(clazz, ids.getIds());

            return genResourcesResponse(it);
        } catch (ClassNotFoundException e) {
            log.error("e=", e);
            throw APIException.badRequests.invalidParameter("name", className);
        }
    }

    @POST
    @Path("geo-visible/{name}/field/{fieldName}")
    @Consumes({ MediaType.APPLICATION_XML, MediaType.APPLICATION_JSON })
    @Produces({ MediaType.APPLICATION_OCTET_STREAM })
    public Response queryObjectsField(@PathParam("name") String className, @PathParam("fieldName") String fieldName,
            BulkIdParam ids) throws DatabaseException {
        List<URI> resourceIds = ids.getIds();

        try {
            Class clazz = Class.forName(className);
            Iterator<DataObject> it = dbClient.queryIterativeObjectField(clazz, fieldName, resourceIds);

            return genResourcesResponse(it);
        } catch (ClassNotFoundException e) {
            log.error("e=", e);
            throw APIException.badRequests.invalidParameter("name", className);
        }
    }

    @POST
    @Path("geo-visible/constraint/{className}")
    @Consumes({ MediaType.APPLICATION_XML, MediaType.APPLICATION_JSON })
    @Produces({ MediaType.APPLICATION_OCTET_STREAM })
    public Response queryByConstraint(@PathParam("className") String className, ConstraintDescriptor constraintDescriptor,
            @QueryParam("start_id") URI startId, @QueryParam("max_count") Integer maxCount) throws DatabaseException {
        Constraint condition;

        try {
            condition = constraintDescriptor.toConstraint();
        } catch (ClassNotFoundException | IllegalAccessException
                | InvocationTargetException | NoSuchMethodException | InstantiationException e) {
            throw APIException.badRequests.invalidParameterWithCause(constraintDescriptor.getClass().getName(), constraintDescriptor.toString(), e);
        }

        try {
            Class clazz = Class.forName(className);

            QueryResultList<?> resultList = (QueryResultList<?>) clazz.newInstance();

            if (maxCount == null) {
                dbClient.queryByConstraint(condition, resultList);
            } else {
                dbClient.queryByConstraint(condition, resultList, startId, maxCount);
            }

            return genResourcesResponse(resultList);
        } catch (ClassNotFoundException | InstantiationException | IllegalAccessException e) {
            log.error("Can't find the class e=", e);
            throw APIException.badRequests.invalidParameter("className", className);
        }
    }

    private <T> Response genResourcesResponse(Iterator<T> objs) {
        ResourcesResponse<T> resp = new ResourcesResponse();
        int count = 0;

        while (objs.hasNext()) {
            count++;
            resp.add(objs.next());
        }
        resp.setSize(count);

        return Response.ok(getStreamingOutput(resp), MediaType.APPLICATION_OCTET_STREAM).build();
    }

    private Response genResourcesResponse(QueryResultList<?> queryResult) {
        ResourcesResponse<Object> resp = new ResourcesResponse();
        int count = 0;
        Iterator<?> it = queryResult.iterator();

        while (it.hasNext()) {
            count++;
            resp.add(it.next());
        }

        resp.setSize(count);

        return Response.ok(getStreamingOutput(resp), MediaType.APPLICATION_OCTET_STREAM).build();
    }
}
