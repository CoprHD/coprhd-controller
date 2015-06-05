/**
* Copyright 2015 EMC Corporation
* All Rights Reserved
 */
package com.emc.storageos.api.service.impl.resource;

import java.net.URI;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.core.MediaType;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.emc.storageos.api.service.impl.resource.utils.CapacityUtils;
import com.emc.storageos.db.client.constraint.ContainmentConstraint;
import com.emc.storageos.db.client.constraint.URIQueryResultList;
import com.emc.storageos.db.client.model.VirtualArray;
import com.emc.storageos.db.client.model.VirtualPool;
import com.emc.storageos.model.vpool.VDCCapacities;
import com.emc.storageos.model.vpool.VirtualArrayVirtualPoolCapacity;
import com.emc.storageos.model.vpool.VirtualPoolCapacity;
import com.emc.storageos.security.authorization.CheckPermission;
import com.emc.storageos.security.authorization.DefaultPermissions;
import com.emc.storageos.security.authorization.Role;
import com.emc.storageos.volumecontroller.impl.utils.AttributeMatcherFramework;

@Path("/vdc/capacities")
@DefaultPermissions( read_roles = {Role.SYSTEM_ADMIN, Role.SYSTEM_MONITOR},
                     write_roles = {Role.SYSTEM_ADMIN, Role.RESTRICTED_SYSTEM_ADMIN})
public class VirtualDataCenterCapacityService extends ResourceService {

    private final Logger logger = LoggerFactory.getLogger(VirtualDataCenterCapacityService.class);
    
    protected AttributeMatcherFramework _matcherFramework;
    
    public void setMatcherFramework(AttributeMatcherFramework matcherFramework) {
        _matcherFramework = matcherFramework;
    }
    
    /**     
     * List all VirtualPool capacities under the zone grouped by varray
     * @brief List VirtualPool capacities in the zone
     * @return List of VirtualPool capacities
     */
    @GET
    @Produces({ MediaType.APPLICATION_XML, MediaType.APPLICATION_JSON })
    @CheckPermission(roles = {Role.SYSTEM_ADMIN, Role.SYSTEM_MONITOR})
    public VDCCapacities getZoneCapacities() {

        VDCCapacities zoneCap = new VDCCapacities();
        zoneCap.setArrayCapacities(new ArrayList<VirtualArrayVirtualPoolCapacity>());

        // get all varray ids
        final List<URI> ids = _dbClient.queryByType(VirtualArray.class, true);
        for(URI id : ids) {
            zoneCap.getArrayCapacities().add(getVirtualArrayVirtualPoolCapacities(id));
        }

        return zoneCap;
    }
    
    private VirtualArrayVirtualPoolCapacity getVirtualArrayVirtualPoolCapacities(URI vArrayId) {
        VirtualArrayVirtualPoolCapacity vArrayCap = new VirtualArrayVirtualPoolCapacity();
        vArrayCap.setId(vArrayId);
        vArrayCap.setVpoolCapacities(new ArrayList<VirtualPoolCapacity>());

        URIQueryResultList resultList = new URIQueryResultList();
        _dbClient.queryByConstraint(
                ContainmentConstraint.Factory.getVirtualArrayVirtualPoolConstraint(vArrayId),
                resultList);
        
        Iterator<URI> vPoolIterator = resultList.iterator();
        int c = 0;
        while (vPoolIterator.hasNext()) {
            URI vPoolId = vPoolIterator.next();
            VirtualPool vPool = _permissionsHelper.getObjectById(vPoolId, VirtualPool.class);
            vArrayCap.getVpoolCapacities().add(getVirtualPoolCapacities(vArrayId, vPool));
            c++;
        }
        
        logger.info("{} vpool in varray {}", c, vArrayId);
        
        return vArrayCap;
    }

    private VirtualPoolCapacity getVirtualPoolCapacities(URI vArrayId, VirtualPool vPool) {
        
        VirtualPoolCapacity vPoolCap = new VirtualPoolCapacity();
        
        vPoolCap.setId(vPool.getId());
         
        vPoolCap.setCapacity(CapacityUtils.getCapacityForVirtualPoolAndVirtualArray(vPool, vArrayId, _dbClient, _coordinator));
        
        return vPoolCap;
    }
}
