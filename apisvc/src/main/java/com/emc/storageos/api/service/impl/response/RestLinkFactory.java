/*
 * Copyright (c) 2008-2011 EMC Corporation
 * All Rights Reserved
 */

package com.emc.storageos.api.service.impl.response;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.concurrent.ConcurrentMap;

import com.emc.storageos.db.client.DbClient;
import com.emc.storageos.db.client.model.BlockMirror;
import com.emc.storageos.db.client.model.DataObject;
import com.emc.storageos.db.client.model.ProtectionSet;
import com.emc.storageos.db.client.model.RemoteDirectorGroup;
import com.emc.storageos.db.client.model.StoragePool;
import com.emc.storageos.db.client.model.StoragePort;
import com.emc.storageos.db.client.model.StringSet;
import com.emc.storageos.db.client.model.VplexMirror;
import com.emc.storageos.db.client.util.NullColumnValueGetter;
import com.emc.storageos.db.exceptions.DatabaseException;
import com.emc.storageos.model.ResourceTypeEnum;
import com.googlecode.concurrentlinkedhashmap.ConcurrentLinkedHashMap;

public final class RestLinkFactory
{
    static private volatile DbClient _dbClient;
    static private ConcurrentMap<URI, URI> _linkCache = new ConcurrentLinkedHashMap.Builder<URI, URI>()
            .maximumWeightedCapacity(1000)  // max 1000 entries
            .build();

    /* Public Interfaces */
    public static URI newLink(DataObject resource) {
        if (resource == null) {
            throw new NullPointerException();
        }
        ResourceTypeEnum res = ResourceTypeMapping.getResourceType(resource);
        try {
            URI parentId;
            if (res == null) {
                return new URI("/");
            }
            switch (res) {
                case STORAGE_POOL:
                    parentId = ((StoragePool) resource).getStorageDevice();
                    return secondaryResourceLink(res.getService(), resource.getId(), parentId);
                case STORAGE_PORT:
                    parentId = ((StoragePort) resource).getStorageDevice();
                    return secondaryResourceLink(res.getService(), resource.getId(), parentId);
                case RDF_GROUP:
                    parentId = ((RemoteDirectorGroup) resource).getSourceStorageSystemUri();
                    return secondaryResourceLink(res.getService(), resource.getId(), parentId);
                case BLOCK_MIRROR:
                    parentId = ((BlockMirror) resource).getSource().getURI();
                    return secondaryResourceLink(res.getService(), resource.getId(), parentId);
                case VPLEX_MIRROR:
                    parentId = ((VplexMirror) resource).getSource().getURI();
                    return secondaryResourceLink(res.getService(), resource.getId(), parentId);
                case PROTECTION_SET:
                    // any volume in the volume string set is valid
                    StringSet volumeIDs = ((ProtectionSet) resource).getVolumes();
                    if (volumeIDs != null && !volumeIDs.isEmpty()) {
                        for (String volumeID : volumeIDs) {
                            // No .get(), same as iterator.
                            return secondaryResourceLink(res.getService(), resource.getId(), new URI(volumeID));
                        }
                    }
                    // This will not produce a good URI, but it's impossible to get here with the dependent data model.
                    return simpleServiceLink(res.getService(), resource.getId());
                default:
                    return simpleServiceLink(res, resource.getId());
            }
        } catch (URISyntaxException ex) {
            return null;   // impossible;
        }
    }

    public static URI newLink(ResourceTypeEnum res, URI resource_id) {
        try {
            if (resource_id == null) {
                return new URI("/");
            }

            if (res == null) {
                return new URI("/" + resource_id);
            }

            if (res == ResourceTypeEnum.STORAGE_POOL ||
                    res == ResourceTypeEnum.STORAGE_PORT ||
                    res == ResourceTypeEnum.BLOCK_MIRROR ||
                    res == ResourceTypeEnum.RDF_GROUP ||
                    res == ResourceTypeEnum.VPLEX_MIRROR) {
                URI link = _linkCache.get(resource_id);
                if (link == null) {
                    DataObject resource = _dbClient.queryObject(ResourceTypeMapping.getDataObjectClass(res), resource_id);
                    if (resource != null) {
                        link = newLink(resource);
                        _linkCache.put(resource_id, link);
                    } else {
                        link = NullColumnValueGetter.getNullURI();
                    }
                }
                return link;
            }
            else {
                return simpleServiceLink(res, resource_id);
            }
        } catch (URISyntaxException ex) {
            return null; // impossible;
        } catch (DatabaseException ex) {
            return null;
        }
    }

    public static URI newLink(ResourceTypeEnum res, URI resourceId, URI parentId) {
        try {
            if (resourceId == null) {
                return new URI("/");
            }

            if (res == ResourceTypeEnum.STORAGE_POOL ||
                    res == ResourceTypeEnum.STORAGE_PORT ||
                    res == ResourceTypeEnum.RDF_GROUP ||
                    res == ResourceTypeEnum.BLOCK_MIRROR ||
                    res == ResourceTypeEnum.VPLEX_MIRROR ||
                    res == ResourceTypeEnum.PROTECTION_SET) {
                return secondaryResourceLink(res.getService(), resourceId, parentId);
            }
            else {
                return simpleServiceLink(res, resourceId);
            }
        } catch (URISyntaxException ex) {
            return null;   // impossible;
        }

    }

    public static URI newTaskLink(DataObject resource, String op_id)
    {
        try {
            StringBuilder build = (new StringBuilder()).
                    append(newLink(resource)).
                    append("/tasks/").
                    append(op_id);
            return new URI(build.toString());
        } catch (URISyntaxException ex) {
            return null;        // imposssible
        }
    }
    
    public static URI simpleServiceLink(ResourceTypeEnum res, URI resourceId)
            throws URISyntaxException {
        return simpleServiceLink(res.getService(), resourceId);
    }

    private static URI simpleServiceLink(String service, URI resourceId) throws URISyntaxException {
        StringBuilder build = (new StringBuilder(service)).
                append('/').
                append(resourceId);
        return new URI(build.toString());
    }

    private static URI secondaryResourceLink(String service, URI resourceId, URI parentId) throws URISyntaxException {

        StringBuilder build = (new StringBuilder(String.format(service, parentId))).
                append('/').
                append(resourceId);
        return new URI(build.toString());
    }
    
    /**
     * Set db client
     * 
     * @param dbClient
     */
    public void setDbClient(DbClient dbClient) {
        _dbClient = dbClient;
    }
}
