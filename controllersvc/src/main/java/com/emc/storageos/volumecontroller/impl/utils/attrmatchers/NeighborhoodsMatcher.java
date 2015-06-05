/**
* Copyright 2015 EMC Corporation
* All Rights Reserved
 */
/*
 * Copyright (c) 2013. EMC Corporation
 * All Rights Reserved
 *
 * This software contains the intellectual property of EMC Corporation
 * or is licensed to EMC Corporation from third parties.  Use of this
 * software and the intellectual property contained therein is expressly
 * limited to the terms and conditions of the License Agreement under which
 * it is provided by or on behalf of EMC.
 */
package com.emc.storageos.volumecontroller.impl.utils.attrmatchers;

import java.net.URI;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.emc.storageos.db.client.constraint.AlternateIdConstraint;
import com.emc.storageos.db.client.constraint.URIQueryResultList;
import com.emc.storageos.db.client.model.StoragePool;
import com.emc.storageos.volumecontroller.AttributeMatcher;
import com.google.common.base.Joiner;

/**
 * NeighborhoodsMatcher is responsible to filter out storage pools which do not belong to the specified vArrays.
 *
 */
public class NeighborhoodsMatcher extends AttributeMatcher {
    

    private static final Logger _logger = LoggerFactory.getLogger(NeighborhoodsMatcher.class);
    


    /**
     * Filters out storage pools which do not belong to the specified vArrays.
     *
     * @param pools  : storage pools
     * @param attributeMap : map of attributes which includes set of vArrays
     *
     * @return list of pools in the specified vArrays
     */
    @Override
    public List<StoragePool> matchStoragePoolsWithAttributeOn(List<StoragePool> pools, Map<String, Object> attributeMap){
        List<StoragePool> matchedPools = new ArrayList<StoragePool>();
        
        Set<String> vArrays = (Set<String>) attributeMap.get(Attributes.varrays.toString());
        if(vArrays != null && !vArrays.isEmpty()){
        _logger.info("Pools Matching vArrays Started {}, {} :", vArrays, Joiner.on("\t").join( getNativeGuidFromPools(pools)));
        List<URI> vArrayPools = getVarrayPools(vArrays);
        Iterator<StoragePool> poolIterator = pools.iterator();
        while(poolIterator.hasNext()) {
            StoragePool pool = poolIterator.next();
            // check if the storage pool is part varray pool list.
            // if it is not in the list then remove it
            if(vArrayPools.contains(pool.getId())) {
                matchedPools.add(pool);
            }
        }
        }
        _logger.info("Pools Matching vArrays Ended: {}", Joiner.on("\t").join( getNativeGuidFromPools(matchedPools)));
        return matchedPools;
    }
    

    /**
     * Get storage pools which belong to given vArrays.
     * @param vArrays
     * @return
     */

    private List<URI> getVarrayPools(Set<String> vArrays) {
        List<URI> poolURIs = new ArrayList<URI>();
        Iterator<String> vArrayItr = vArrays.iterator();
        while (vArrayItr.hasNext()) {
            URIQueryResultList vArrayPoolsQueryResult = new URIQueryResultList();
            _objectCache.getDbClient().queryByConstraint(AlternateIdConstraint.Factory
                    .getVirtualArrayStoragePoolsConstraint(vArrayItr.next()),
                    vArrayPoolsQueryResult);
            Iterator<URI> poolIterator = vArrayPoolsQueryResult.iterator();
            while (poolIterator.hasNext()) {
                poolURIs.add(poolIterator.next());
            }
        }
        return poolURIs;
    }

    @Override
    protected boolean isAttributeOn(Map<String, Object> attributeMap) {
    	//this should run always.
        return true;
    }
}
