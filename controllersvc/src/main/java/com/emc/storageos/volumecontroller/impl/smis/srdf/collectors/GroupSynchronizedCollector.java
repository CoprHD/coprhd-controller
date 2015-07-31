/*
 * Copyright (c) 2015 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.volumecontroller.impl.smis.srdf.collectors;

import com.emc.storageos.db.client.DbClient;
import com.emc.storageos.db.client.model.StorageSystem;
import com.emc.storageos.db.client.model.Volume;
import com.emc.storageos.volumecontroller.impl.smis.srdf.SRDFUtils;
import com.google.common.collect.Lists;

import javax.cim.CIMObjectPath;
import java.util.Collection;

import static java.util.Collections.EMPTY_LIST;

/**
 * Created by bibbyi1 on 3/24/2015.
 */
public class GroupSynchronizedCollector extends AbstractCollector {

    public GroupSynchronizedCollector(DbClient dbClient, SRDFUtils utils) {
        super(dbClient, utils);
    }

    @Override
    public Collection<CIMObjectPath> collect(StorageSystem provider, Volume targetVolume) {
        CIMObjectPath path = utils.getGroupSynchronized(targetVolume, provider);
        return (path == null) ? EMPTY_LIST : Lists.newArrayList(path);
    }
}
