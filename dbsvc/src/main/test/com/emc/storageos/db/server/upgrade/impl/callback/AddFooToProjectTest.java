/*
 * Copyright (c) 2008-2013 EMC Corporation
 * All Rights Reserved
 */

package com.emc.storageos.db.server.upgrade.impl.callback;

import java.lang.reflect.Method;
import java.net.URI;
import java.util.Map;
import java.util.HashMap;
import java.util.List;

import org.junit.Assert;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.emc.storageos.db.TestDBClientUtils;
import com.emc.storageos.db.client.URIUtil;
import com.emc.storageos.db.client.model.Project;
import com.emc.storageos.db.client.constraint.PrefixConstraint;
import com.emc.storageos.db.server.upgrade.DbMigrationTest;
import com.emc.storageos.db.server.upgrade.util.DbSchemaChanger;

/*
 *  Add 'String foo;', its getter/setter and @Name and @PrefixIndex to Project.class
 */
public class AddFooToProjectTest extends DbMigrationTest {
    private static final Logger log = LoggerFactory.getLogger(AddFooToProjectTest.class);

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
        // attributes of @Prefix
        Map<String, Object> prefixAttrs  = new HashMap<>();
        prefixAttrs.put("cf", "foo1");

        // attributes of @Name
        String columnName = "foo";
        Map<String, Object> nameAttrs  = new HashMap<>();
        nameAttrs.put("value", columnName);

        changer = new DbSchemaChanger("com.emc.storageos.db.client.model.Project");
        changer.beginChange()
               .addBeanProperty("foo", String.class, columnName)
                //add @PrefixIndex to 'getFoo()'
               .addAnnotation("getFoo", "com.emc.storageos.db.client.model.PrefixIndex", prefixAttrs)
                //add @Name to 'getFoo()'
               .addAnnotation("getFoo", "com.emc.storageos.db.client.model.Name", nameAttrs)
               .endChange();
    }

    @Override
    public void verifySourceSchema() throws Exception {
    }

    @Override
    public void changeTargetSchema() throws Exception {
        changer.verifyAnnotation("getFoo", "com.emc.storageos.db.client.model.PrefixIndex"); 
        changer.verifyAnnotation("getFoo", "com.emc.storageos.db.client.model.Name"); 

        // test 'setter' and 'getter' methods
        Method method = Project.class.getMethod("setFoo", String.class);
        Project project = new Project();
        method.invoke(project, "hello");

        method = Project.class.getMethod("getFoo");
        Assert.assertEquals(method.invoke(project), "hello");
    } 

    @Override
    public void verifyTargetSchema() throws Exception {
    } 

    @Override
    protected void prepareData() throws Exception {
        // prepare a Project object for migration
        Project project  = new Project();
 
        project.setId(URIUtil.createId(Project.class));
        project.setLabel("project1");
        project.setOwner("foo1");

        Method method = Project.class.getMethod("setFoo", String.class);
        method.invoke(project, "hello");

        dbClient.createObject(project);
    }

    @Override
    protected void verifyPreparedData() throws Exception {
        //make sure the project is saved
        List<URI> ids = dbClient.queryByType(Project.class, true);
        Assert.assertEquals(1, TestDBClientUtils.size(ids));

        Project project = dbClient.queryObject(Project.class, ids.get(0));
        Assert.assertNotNull(project);
    }

    @Override
    protected void verifyResults() throws Exception {
        List<URI> ids = 
            dbClient.queryByConstraint(PrefixConstraint.Factory.getConstraint(Project.class, "foo", "he"));

        Assert.assertEquals(1, ids.size());
    }
}
