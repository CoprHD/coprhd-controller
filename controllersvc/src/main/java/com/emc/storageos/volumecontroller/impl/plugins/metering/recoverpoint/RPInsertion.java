/*
 * Copyright 2015 EMC Corporation
 * All Rights Reserved
 */
/**
 *  Copyright (c) 2013 EMC Corporation
 * All Rights Reserved
 *
 * This software contains the intellectual property of EMC Corporation
 * or is licensed to EMC Corporation from third parties.  Use of this
 * software and the intellectual property contained therein is expressly
 * limited to the terms and conditions of the License Agreement under which
 * it is provided by or on behalf of EMC.
 */
package com.emc.storageos.volumecontroller.impl.plugins.metering.recoverpoint;

import java.net.URI;
import java.util.Iterator;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.emc.storageos.db.client.DbClient;
import com.emc.storageos.db.client.constraint.ContainmentConstraint;
import com.emc.storageos.db.client.constraint.URIQueryResultList;
import com.emc.storageos.db.client.model.ProtectionSystem;
import com.emc.storageos.db.client.model.Stat;
import com.emc.storageos.db.client.model.Volume;
import com.emc.storageos.plugins.BaseCollectionException;
import com.emc.storageos.plugins.metering.smis.SMIPluginException;
import com.emc.storageos.volumecontroller.impl.plugins.metering.CassandraInsertion;

public class RPInsertion extends CassandraInsertion {

    private Logger _logger = LoggerFactory.getLogger(RPInsertion.class);

    @Override
    public void injectColumnsDetails(Stat statObj, DbClient client) throws Exception {
        ProtectionSystem protectionObj = client.queryObject(ProtectionSystem.class, statObj.getResourceId());
        // Given a protection system, find a volume protected by this protection system,
        // and then extract the project and vpool
        Volume protectedVolume = null;
        URIQueryResultList resultList = new URIQueryResultList();
        client.queryByConstraint(
                ContainmentConstraint.Factory.getProtectionSystemVolumesConstraint(protectionObj.getId()), resultList);
        for (Iterator<URI> volumeItr = resultList.iterator(); volumeItr.hasNext();) {
            Volume volume = client.queryObject(Volume.class, volumeItr.next());
            if (volume.getProtectionController().equals(protectionObj.getId())) {
                protectedVolume = volume;
                break;
            }
        }
        if (protectedVolume != null) {
            _logger.info("Found volume " + protectedVolume.getWWN()
                    + " protected by this protection controller.  Get the Cos/Project/Tenant.");
            statObj.setProject(protectedVolume.getProject().getURI());
            statObj.setVirtualPool(protectedVolume.getVirtualPool());
            statObj.setTenant(protectedVolume.getTenant().getURI());
        } else {
            statObj.setProject(null);
            statObj.setVirtualPool(null);
            statObj.setTenant(null);
            throw new SMIPluginException("Cassandra Database Insertion Error.  Cannot identify Project/CoS/Tenant for ProtectionSystem", -1);
        }
    }

    @Override
    public void throwException(Exception e) throws BaseCollectionException {
        _logger.error("Cassandra Database Injection Error: ", e);
        throw new SMIPluginException("Cassandra Database Injection Error", -1);
    }
}
