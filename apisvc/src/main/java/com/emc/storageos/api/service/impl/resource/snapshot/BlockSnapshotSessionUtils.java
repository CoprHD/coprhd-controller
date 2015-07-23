/**
 *  Copyright (c) 2008-2013 EMC Corporation
 * All Rights Reserved
 *
 * This software contains the intellectual property of EMC Corporation
 * or is licensed to EMC Corporation from third parties.  Use of this
 * software and the intellectual property contained therein is expressly
 * limited to the terms and conditions of the License Agreement under which
 * it is provided by or on behalf of EMC.
 */
package com.emc.storageos.api.service.impl.resource.snapshot;

import java.net.URI;

import javax.ws.rs.core.UriInfo;

import com.emc.storageos.api.service.impl.resource.ArgValidator;
import com.emc.storageos.api.service.impl.resource.utils.BlockServiceUtils;
import com.emc.storageos.db.client.DbClient;
import com.emc.storageos.db.client.URIUtil;
import com.emc.storageos.db.client.model.BlockObject;
import com.emc.storageos.db.client.model.BlockSnapshot;
import com.emc.storageos.db.client.model.Project;
import com.emc.storageos.db.client.model.VirtualPool;
import com.emc.storageos.db.client.model.Volume;
import com.emc.storageos.svcs.errorhandling.resources.APIException;


/**
 * 
 */
public class BlockSnapshotSessionUtils {
    
    /**
     * Returns the volume or block snapshot instance for the passed URI.
     * 
     * @param sourceURI The URI for the Volume or BlockSnapshot instance.
     * @param uriInfo A reference to the URI information.
     * @param dbClient A reference to a database client.
     * 
     * @return A reference to the block object.
     */
    public static BlockObject validateSnapshotSessionSource(URI sourceURI, UriInfo uriInfo, DbClient dbClient) {
        ArgValidator.checkUri(sourceURI);
        if ((!URIUtil.isType(sourceURI, Volume.class)) && (!URIUtil.isType(sourceURI, BlockSnapshot.class))) {
            throw APIException.badRequests.invalidSnapshotSessionSource(sourceURI.toString());
        }

        BlockObject sourceObj = BlockObject.fetch(dbClient, sourceURI);
        ArgValidator.checkEntity(sourceObj, sourceURI, BlockServiceUtils.isIdEmbeddedInURL(sourceURI, uriInfo), true);
        return sourceObj;
    }
    
    /**
     * Returns the project for the snapshot session source.
     * 
     * @param sourceObj A reference to the Volume or BlockSnapshot instance.
     * @param dbClient A reference to a database client.
     * 
     * @return A reference to the project for the snapshot session source.
     */
    public static Project querySnapshotSessionSourceProject(BlockObject sourceObj, DbClient dbClient) {
        URI sourceURI = sourceObj.getId();
        URI projectURI = null;
        if (URIUtil.isType(sourceURI, Volume.class)) {
            projectURI = ((Volume) sourceObj).getProject().getURI();
        } else if (URIUtil.isType(sourceURI, BlockSnapshot.class)) {
            projectURI = ((BlockSnapshot) sourceObj).getProject().getURI();            
        }
        
        if (projectURI == null) {
            throw APIException.badRequests.invalidSnapshotSessionSource(sourceURI.toString());
        }
        
        Project project = dbClient.queryObject(Project.class, projectURI);
        return project;
    }
    
    /**
     * Returns the vpool for the snapshot session source.
     * 
     * @param sourceObj A reference to the Volume or BlockSnapshot instance.
     * @param dbClient A reference to a database client.
     * 
     * @return A reference to the vpool for the snapshot session source.
     */
    public static VirtualPool querySnapshotSessionSourceVPool(BlockObject sourceObj, DbClient dbClient) {
        URI sourceURI = sourceObj.getId();
        URI vpoolURI = null;
        if (URIUtil.isType(sourceURI, Volume.class)) {
            vpoolURI = ((Volume) sourceObj).getVirtualPool();
        } else if (URIUtil.isType(sourceURI, BlockSnapshot.class)) {
            // TBD VPLEX. Right now it does not matter because we snap only the source
            // side, which means the parent volume of the snapshot will have the same
            // vpool as the VPLEX volume itself. In Darth we may snap both sides, so
            // this then becomes an issue, as this method is used to determine the
            // platform specific snapshot session implementation, and we could miss
            // VPLEX in the case where the snap is of the HA side. It is also
            // used in validation during snapshot session creation.
            URI parentVolURI = ((BlockSnapshot) sourceObj).getParent().getURI();
            Volume parentVolume = dbClient.queryObject(Volume.class, parentVolURI);
            vpoolURI = parentVolume.getVirtualPool();
        }
        
        if (vpoolURI == null) {
            throw APIException.badRequests.invalidSnapshotSessionSource(sourceURI.toString());
        }
        
        VirtualPool vpool = dbClient.queryObject(VirtualPool.class, vpoolURI);
        return vpool;
    }    
}
