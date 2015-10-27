package com.emc.storageos.systemservices.impl.resource;

import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;

import com.emc.storageos.coordinator.client.model.DbConsistencyStatus;
import com.emc.storageos.coordinator.client.service.DistributedQueue;
import com.emc.storageos.model.db.DbConsistencyStatusRestRep;
import com.emc.storageos.security.geo.GeoServiceJob;
import com.emc.storageos.svcs.errorhandling.resources.APIException;
import com.emc.storageos.systemservices.impl.upgrade.CoordinatorClientExt;



/**
 * Db consistency service is used to trigger db consistency checker and
 * query db consistency status
 */
@Path("/db/")
public class DbConsistencyService {
    private static final Logger log = LoggerFactory.getLogger(DbConsistencyService.class);
    private DistributedQueue<GeoServiceJob> queue;
    @Autowired
    private CoordinatorClientExt coordinator;

    
    @POST
    @Path("consistency")
    @Produces({ MediaType.APPLICATION_JSON })
    public Response checkDbConsistency() {
        if (isDbConsistencyCheckInProgress()) {
            throw APIException.badRequests.dbConsistencyCheckAlreadyProgress();
        }
        
        triggerDbConsistencyCheck();
        return Response.ok().build();
    }
    
    private void triggerDbConsistencyCheck() {
        
    }

    private boolean isDbConsistencyCheckInProgress() {
        return false;
    }

    @GET
    @Path("consistency")
    @Produces({ MediaType.APPLICATION_JSON })
    public DbConsistencyStatusRestRep getDbConsistencyStatus() {
        DbConsistencyStatus status = getStatusFromZk();
        return toStatusRestRep(status);
    }

    private DbConsistencyStatusRestRep toStatusRestRep(DbConsistencyStatus status) {
        return null;
    }

    private DbConsistencyStatus getStatusFromZk() {
        return null;
    }

}
