/*
 * Copyright (c) 2015 EMC Corporation
 * All Rights Reserved
 */
package com.emc.sa.model.suite;

import java.net.URI;
import java.util.List;

import org.apache.log4j.Logger;
import org.junit.Assert;
import org.junit.Test;

import com.emc.sa.model.BaseModelTest;
import com.emc.storageos.db.client.model.uimodels.CatalogCategory;
import com.emc.storageos.db.client.model.uimodels.CatalogService;
import com.emc.storageos.db.client.model.uimodels.ExecutionWindow;
import com.emc.sa.model.dao.ModelClient;
import com.emc.storageos.db.client.URIUtil;
import com.emc.storageos.db.client.model.NamedURI;

public class CatalogServiceTest extends BaseModelTest<CatalogService> {

    private static final Logger _logger = Logger.getLogger(CatalogServiceTest.class);
    
    public CatalogServiceTest() {
        super(CatalogService.class);
    }
    
    @Test
    public void testReparent() throws Exception {
        _logger.info("Starting reparent CatalogService test");
        
        ModelClient modelClient = getModelClient();
        
        CatalogCategory parent1 = createCategoryWithLabel("Parent 1");
        modelClient.save(parent1);
        
        CatalogCategory parent2 = createCategoryWithLabel("Parent 2");
        modelClient.save(parent2);
        
        CatalogService service = createWithLabel("Service");
        setParent(parent1, service);
        modelClient.save(service);
        
        List<CatalogService> parent1Children = modelClient.catalogServices().findByCatalogCategory(parent1.getId());
        Assert.assertEquals(1, parent1Children.size());
        Assert.assertEquals(service.getId(), parent1Children.get(0).getId());
        
        List<CatalogService> parent2Children = modelClient.catalogServices().findByCatalogCategory(parent2.getId());
        Assert.assertEquals(0, parent2Children.size());
        
        setParent(parent2, service);
        modelClient.save(service);
        
        parent1Children = modelClient.catalogServices().findByCatalogCategory(parent1.getId());
        Assert.assertEquals(0, parent1Children.size());
        
        parent2Children = modelClient.catalogServices().findByCatalogCategory(parent2.getId());
        Assert.assertEquals(1, parent2Children.size());
        Assert.assertEquals(service.getId(), parent2Children.get(0).getId());
    }
    
    @Test
    public void testPersistObject() throws Exception {
        _logger.info("Starting persist CatalogService test");

        CatalogService model = new CatalogService();
        model.setId(URIUtil.createId(CatalogService.class));
        model.setLabel("foo");
        model.setApprovalRequired(true);
        model.setBaseService("my service");
        URI ewUri = URIUtil.createId(ExecutionWindow.class);
        NamedURI ewId = new NamedURI(ewUri, "ewId");
        model.setDefaultExecutionWindowId(ewId);
        model.setDescription("my desc");
        model.setExecutionWindowRequired(true);
        model.setImage("my image");
        model.setMaxSize(42);
        model.setTitle("my title");
        
        save(model);
        model = findById(model.getId());
        
        Assert.assertNotNull(model);
        Assert.assertEquals("foo", model.getLabel());
        Assert.assertEquals(true, model.getApprovalRequired());
        Assert.assertEquals("my service", model.getBaseService());
        Assert.assertEquals(ewId, model.getDefaultExecutionWindowId());
        Assert.assertEquals("my desc", model.getDescription());
        Assert.assertEquals(true, model.getExecutionWindowRequired());
        Assert.assertEquals("my image", model.getImage());
        Assert.assertEquals(new Integer(42), model.getMaxSize());
        Assert.assertEquals("my title", model.getTitle());
        
    }
    
    @Test
    public void testFindByCatalogCategory() {
        
        _logger.info("Starting FindByCatalogCategory test");
        
        ModelClient modelClient = getModelClient();
        
        CatalogCategory root = createCategoryWithLabel("rooty");
        modelClient.save(root);
        
        CatalogService s1 = createWithLabel("s1");
        s1.setCatalogCategoryId(new NamedURI(root.getId(), root.getLabel()));
        modelClient.save(s1);
        
        CatalogCategory c1 = createCategoryWithLabel("asdf");
        c1.setCatalogCategoryId(new NamedURI(root.getId(), root.getLabel()));
        modelClient.save(c1);
        
        CatalogService s2 = createWithLabel("s2");
        s2.setCatalogCategoryId(new NamedURI(c1.getId(), c1.getLabel()));
        modelClient.save(s2);
        
        CatalogService s3 = createWithLabel("s3");
        s3.setCatalogCategoryId(new NamedURI(c1.getId(), c1.getLabel()));
        modelClient.save(s3);     

        CatalogCategory c2 = createCategoryWithLabel("asdf2");
        c2.setCatalogCategoryId(new NamedURI(root.getId(), root.getLabel()));
        modelClient.save(c2);

        CatalogService s4 = createWithLabel("s4");
        s4.setCatalogCategoryId(new NamedURI(c2.getId(), c2.getLabel()));
        modelClient.save(s4);
        
        CatalogService s5 = createWithLabel("s5");
        s5.setCatalogCategoryId(new NamedURI(c2.getId(), c2.getLabel()));
        modelClient.save(s5);        

        CatalogService s6 = createWithLabel("s6");
        s6.setCatalogCategoryId(new NamedURI(c2.getId(), c2.getLabel()));
        modelClient.save(s6);  
        
        List<CatalogService> results = modelClient.catalogServices().findByCatalogCategory(root.getId());
        Assert.assertNotNull(results);
        Assert.assertEquals(1, results.size());
        
        results = modelClient.catalogServices().findByCatalogCategory(c1.getId());
        Assert.assertNotNull(results);
        Assert.assertEquals(2, results.size());        

        results = modelClient.catalogServices().findByCatalogCategory(c2.getId());
        Assert.assertNotNull(results);
        Assert.assertEquals(3, results.size());        
        
    }    
    
    private CatalogCategory createCategoryWithLabel(String label) {
        CatalogCategory model = new CatalogCategory();
        model.setLabel(label);
        model.setDescription("my desc");
        model.setImage("my image");
        model.setTitle("my title");
        return model;
    }    
    
    private CatalogService createWithLabel(String label) {
        CatalogService model = new CatalogService();
        model.setLabel(label);
        model.setApprovalRequired(true);
        model.setBaseService("my service");
        URI ewUri = URIUtil.createId(ExecutionWindow.class);
        NamedURI ewId = new NamedURI(ewUri, "ewId");        
        model.setDefaultExecutionWindowId(ewId);
        model.setDescription("my desc");
        model.setExecutionWindowRequired(true);
        model.setImage("my image");
        model.setMaxSize(42);
        model.setTitle("my title");
        return model;
    }
    
    private void setParent(CatalogCategory parent, CatalogService child) {
        child.setCatalogCategoryId(new NamedURI(parent.getId(), parent.getLabel()));
    }
}
