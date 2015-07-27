/*
 * Copyright (c) 2015 EMC Corporation
 * All Rights Reserved
 */
package com.emc.sa.model;

import java.net.URI;
import java.util.ArrayList;
import java.util.List;

import org.junit.Assert;
import org.junit.Test;

import com.emc.storageos.db.client.model.uimodels.TenantDataObject;
import com.emc.storageos.db.client.URIUtil;
import com.emc.storageos.db.client.constraint.NamedElementQueryResultList.NamedElement;
import com.emc.storageos.db.client.model.DataObject;

public abstract class BaseModelTest<T extends DataObject> extends DBClientTestBase {
    private Class<T> type;

    public BaseModelTest(Class<T> type) {
        this.type = type;
    }

    @Test
    public void testSave() throws Exception {
        T model = type.newInstance();
        model.setLabel("First");
        save(model);

        T model2 = findById(model.getId());
        Assert.assertEquals("First", model2.getLabel());

        model2.setLabel("Second");
        save(model2);

        T model3 = findById(model.getId());
        Assert.assertEquals("Second", model3.getLabel());

        getModelClient().delete(model);
    }

    @Test
    public void testFindByType() throws Exception {
        T model = type.newInstance();
        model.setLabel("First");
        save(model);

        List<URI> ids = findByType();
        Assert.assertTrue("Could not find " + model.getId() + " by type", ids.contains(model.getId()));
        getModelClient().delete(model);
    }

    @Test
    public void testFindByTypeWithId() throws Exception {
        // Set the ID before saving instead of letting it be auto-generated
        T model = type.newInstance();
        model.setId(URIUtil.createId(type));
        model.setLabel("First");
        save(model);

        List<URI> ids = findByType();
        Assert.assertTrue("Could not find " + model.getId() + " by type", ids.contains(model.getId()));
        getModelClient().delete(model);
    }

    @Test
    public void testFindByTenant() throws Exception {
        if (TenantDataObject.class.isAssignableFrom(type)) {
            T model = type.newInstance();
            model.setLabel("First");
            ((TenantDataObject) model).setTenant(DEFAULT_TENANT);
            save(model);

            List<URI> ids = findByAlternateId(TenantDataObject.TENANT_COLUMN_NAME, DEFAULT_TENANT);
            Assert.assertTrue("Could not find " + model.getId() + " by tenant", ids.contains(model.getId()));

            getModelClient().delete(model);
        }
    }

    /**
     * Saves the model object and ensures that some dependent fields were updated.
     * 
     * @param model
     *        the model to save.
     */
    protected void save(T model) {
        getModelClient().save(model);
        Assert.assertNotNull("ID was not set", model.getId());
        Assert.assertNotNull("CreationTime was not set", model.getCreationTime());
        Assert.assertNotNull("Inactive was not set", model.getInactive());
    }

    protected T findById(URI id) {
        T model = getModelClient().findById(id);
        Assert.assertNotNull("Could not find " + type + " by ID: " + id, model);
        return model;
    }

    protected List<URI> findByAlternateId(String columnName, String value) {
        List<NamedElement> results = getModelClient().findByAlternateId(type, columnName, value);
        List<URI> ids = new ArrayList<URI>();
        for (NamedElement result : results) {
            ids.add(result.getId());
        }
        return ids;
    }

    protected List<URI> findByType() {
        List<URI> ids = new ArrayList<URI>();
        for (URI id : getModelClient().findByType(type)) {
            ids.add(id);
        }
        return ids;
    }
}
