/*
 * Copyright (c) 2015 EMC Corporation
 * All Rights Reserved
 */
package com.emc.sa.model.suite;

import org.apache.log4j.Logger;
import org.junit.Assert;
import org.junit.Test;

import com.emc.sa.model.BaseModelTest;
import com.emc.storageos.db.client.model.uimodels.CatalogServiceField;
import com.emc.storageos.db.client.URIUtil;

public class CatalogServiceFieldTest extends BaseModelTest<CatalogServiceField> {

    private static final Logger _logger = Logger.getLogger(CatalogServiceFieldTest.class);
    
    public CatalogServiceFieldTest() {
        super(CatalogServiceField.class);
    }
    
    @Test
    public void testPersistObject() throws Exception {
        _logger.info("Starting persist CatalogServiceField test");

        CatalogServiceField model = create("foo", "my value");
        model.setId(URIUtil.createId(CatalogServiceField.class));
        model.setOverride(true);
        
        save(model);
        model = findById(model.getId());
        
        Assert.assertNotNull(model);
        Assert.assertEquals("foo", model.getLabel());
        Assert.assertEquals("my value", model.getValue());
        Assert.assertTrue(model.getOverride());
        
    }
    
    protected static CatalogServiceField create(String label, String value) {
        CatalogServiceField model = new CatalogServiceField();
        model.setLabel("foo");
        model.setValue("my value");
        return model;
    }
    
}
