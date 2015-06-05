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

package com.emc.storageos.db.server.upgrade.impl.callback;

import java.net.URI;
import java.net.URL;
import java.util.Map;
import java.util.HashMap;
import java.util.List;

import org.junit.Assert;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.emc.storageos.db.client.DbClient;
import com.emc.storageos.db.client.URIUtil;
import com.emc.storageos.db.client.constraint.PrefixConstraint;
import com.emc.storageos.db.client.model.Project;
import com.emc.storageos.db.server.upgrade.DbMigrationTest;
import com.emc.storageos.db.server.upgrade.util.DbSchemaChanger;

/**
 * Add "@PrefixIndex" to Project.getOwner() 
 */
public class AddPrefixToProjectOwner extends DbMigrationTest {
    private static final Logger log = LoggerFactory.getLogger(AddPrefixToProjectOwner.class);

    @Override
    public String getSourceSchemaVersion() {
        return "1.0";
    }

    @Override
    public String getTargetSchemaVersion() {
        return "1.1";
    }

    @Override
    public void changeSourceSchema() throws Exception {
    }

    @Override
    public void verifySourceSchema() throws Exception {
    }

    @Override
    public void changeTargetSchema() throws Exception {
        Map<String, Object> values = new HashMap();
        values.put("cf", "foo");

        changer = new DbSchemaChanger("com.emc.storageos.db.client.model.Project");
        changer.beginChange()
               .addAnnotation("getOwner","com.emc.storageos.db.client.model.PrefixIndex", values)
               .endChange();
    }

    @Override
    public void verifyTargetSchema() throws Exception {
        changer.verifyAnnotation("getOwner", "com.emc.storageos.db.client.model.PrefixIndex");
    }

    @Override
    protected void prepareData() throws Exception {
        // prepare a Project object for migration
        Project project  = new Project();
 
        project.setId(URIUtil.createId(Project.class));
        project.setLabel("project1");
        project.setOwner("foo1");

        dbClient.createObject(project);
    }

    @Override
    protected void verifyPreparedData() throws Exception {
        // make sure that the Project object is persisted
        List<URI> ids = dbClient.queryByType(Project.class, true);

        Project project = null;
        int count = 0;

        for (URI id : ids) {
            project = dbClient.queryObject(Project.class, id);
            count++;
        }

        Assert.assertNotNull(project);
        Assert.assertEquals(count, 1);
    }

    @Override
    protected void verifyResults() throws Exception {
        // Check results after migration
        Class clazz = Class.forName("com.emc.storageos.db.client.model.Project");
        List<URI> ids = 
                dbClient.queryByConstraint(PrefixConstraint.Factory.getConstraint(clazz, "owner", "fo"));

        Assert.assertEquals(1, ids.size());
    }
}
