/*
 * Copyright (c) 2015 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.db.client.model;

import java.net.URI;
import java.util.Date;
import java.util.List;

import org.junit.Assert;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit4.SpringJUnit4ClassRunner;

import com.emc.storageos.db.client.DbClient;
import com.emc.storageos.db.client.URIUtil;
import com.emc.storageos.db.client.constraint.DecommissionedConstraint;
import com.emc.storageos.db.client.constraint.URIQueryResultList;

@RunWith(SpringJUnit4ClassRunner.class)
@ContextConfiguration(locations = { "classpath:dbclient-conf.xml" })
public class TimeConstraintTest {

    @Autowired
    DbClient dbclient;

    @Test
    public void testTimeConstraint() {
        Date before = new Date();
        VirtualArray varray = new VirtualArray();
        varray.setId(URIUtil.createId(VirtualArray.class));
        varray.setLabel("dummy");
        varray.setInactive(true);
        dbclient.createObject(varray);
        List<URI> allVArrays = dbclient.queryByType(VirtualArray.class, false);
        Assert.assertNotNull("allVArrays should not be null", allVArrays);
        Assert.assertTrue("allVArrays should show the item we created", allVArrays.iterator().hasNext());

        // test time constraint against the decommissioned index on the inactive field
        Date after = new Date();
        DecommissionedConstraint timeConstraint =
                DecommissionedConstraint.Factory.getTimeConstraint(VirtualArray.class, "inactive", before, after);
        URIQueryResultList results = new URIQueryResultList();
        dbclient.queryByConstraint(timeConstraint, results);
        Assert.assertTrue("time constraint query failed to find our test record", results.iterator().hasNext());
    }
}
