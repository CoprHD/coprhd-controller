package com.emc.sa.model.suite;

import java.net.URI;
import java.util.List;

import org.apache.log4j.Logger;
import org.junit.Assert;
import org.junit.Test;

import com.emc.sa.model.BaseModelTest;
import com.emc.storageos.db.client.model.uimodels.CatalogCategory;
import com.emc.sa.model.dao.ModelClient;
import com.emc.storageos.db.client.URIUtil;
import com.emc.storageos.db.client.model.NamedURI;

public class CatalogCategoryTest extends BaseModelTest<CatalogCategory> {

    private static final Logger _logger = Logger.getLogger(CatalogCategoryTest.class);
    
    public CatalogCategoryTest() {
        super(CatalogCategory.class);
    }
    
    @Test
    public void testReparentCategory() throws Exception {
        ModelClient modelClient = getModelClient();
        
        CatalogCategory parent1 = createCategory("Parent1");
        modelClient.save(parent1);
        
        CatalogCategory parent2 = createCategory("Parent2");
        modelClient.save(parent2);
        
        CatalogCategory child = createCategory("Child");
        setParent(parent1, child);
        modelClient.save(child);
        
        List<CatalogCategory> parent1Children =  modelClient.catalogCategories().findSubCatalogCategories(parent1.getId());
        Assert.assertEquals(1, parent1Children.size());
        Assert.assertEquals(child.getId(), parent1Children.get(0).getId());
        List<CatalogCategory> parent2Children = modelClient.catalogCategories().findSubCatalogCategories(parent2.getId());
        Assert.assertEquals(0, parent2Children.size());
        
        setParent(parent2, child);
        modelClient.save(child);
        
        parent1Children =  modelClient.catalogCategories().findSubCatalogCategories(parent1.getId());
        Assert.assertEquals(0, parent1Children.size());
        parent2Children = modelClient.catalogCategories().findSubCatalogCategories(parent2.getId());
        Assert.assertEquals(1, parent2Children.size());
        Assert.assertEquals(child.getId(), parent2Children.get(0).getId());
    }
    
    @Test
    public void testPersistObject() throws Exception {
        _logger.info("Starting persist CatalogCategory test");

        ModelClient modelClient = getModelClient();

        CatalogCategory model = new CatalogCategory();
        model.setId(URIUtil.createId(CatalogCategory.class));
        model.setLabel("my label");
        URI parentUri = URIUtil.createId(CatalogCategory.class);
        NamedURI parentId = new NamedURI(parentUri, "my parent");
        model.setCatalogCategoryId(parentId);
        model.setDescription("my desc");
        model.setImage("my image");
        model.setTitle("my title");
        model.setTenant(DEFAULT_TENANT);
        
        modelClient.save(model);

        model = modelClient.catalogCategories().findById(model.getId());
        
        Assert.assertNotNull(model);
        Assert.assertEquals("my label", model.getLabel());
        Assert.assertEquals(parentId, model.getCatalogCategoryId());
        Assert.assertEquals("my desc", model.getDescription());
        Assert.assertEquals("my image", model.getImage());
        Assert.assertEquals("my title", model.getTitle());
        Assert.assertEquals(DEFAULT_TENANT, model.getTenant());
        
    }

    @Test
    public void testFindByLabel() throws Exception {
        _logger.info("Starting FindByLabel test");
        
        ModelClient modelClient = getModelClient();
        
        CatalogCategory root = createWithLabel("root");
        
        CatalogCategory catalogCategory = createWithLabel("foo");
        catalogCategory.setCatalogCategoryId(new NamedURI(root.getId(), root.getLabel()));
        modelClient.save(catalogCategory);

        catalogCategory = createWithLabel("barOne");
        catalogCategory.setCatalogCategoryId(new NamedURI(root.getId(), root.getLabel()));
        modelClient.save(catalogCategory);
        
        catalogCategory = createWithLabel("barTwo");
        catalogCategory.setCatalogCategoryId(new NamedURI(root.getId(), root.getLabel()));
        modelClient.save(catalogCategory);        
        
        modelClient.save(root);
        
        List<CatalogCategory> results = modelClient.catalogCategories().findByLabel(DEFAULT_TENANT, "fo");
        Assert.assertNotNull(results);
        Assert.assertEquals(1, results.size());
        
        results = modelClient.catalogCategories().findByLabel(DEFAULT_TENANT, "bar");
        Assert.assertNotNull(results);
        Assert.assertEquals(2, results.size());    

        results = modelClient.catalogCategories().findByLabel(DEFAULT_TENANT, "foobar");
        Assert.assertNotNull(results);        
        Assert.assertEquals(0, results.size());
        
    }
    
    @Test
    public void testFindSubCatalogCategories() {
        
        _logger.info("Starting FindByLabel test");
        
        ModelClient modelClient = getModelClient();
        
        CatalogCategory root = createWithLabel("rooty");
        
        CatalogCategory catalogCategory = createWithLabel("asdf");
        catalogCategory.setCatalogCategoryId(new NamedURI(root.getId(), root.getLabel()));
        modelClient.save(catalogCategory);

        catalogCategory = createWithLabel("asdf2");
        catalogCategory.setCatalogCategoryId(new NamedURI(root.getId(), root.getLabel()));
        modelClient.save(catalogCategory);
        
        catalogCategory = createWithLabel("asdf3");
        catalogCategory.setCatalogCategoryId(new NamedURI(root.getId(), root.getLabel()));
        modelClient.save(catalogCategory);        
        
        modelClient.save(root);
        
        List<CatalogCategory> subCatalogCategories = modelClient.catalogCategories().findSubCatalogCategories(root.getId());
        Assert.assertNotNull(subCatalogCategories);
        Assert.assertEquals(3, subCatalogCategories.size());

    }
    
    @Test
    public void testMultiTenant() throws Exception {
        _logger.info("Starting multi tenant CatalogCategory test");
        
        ModelClient modelClient = getModelClient();
        
        CatalogCategory root1 = createRoot("t1", "Home1");
        modelClient.save(root1);

        CatalogCategory root2 = createRoot("t2", "Home2");
        modelClient.save(root2);
        
        CatalogCategory root3 = createRoot("t3", "Home3");
        modelClient.save(root3);        
        
        CatalogCategory root = modelClient.catalogCategories().getRootCategory("t1");
        Assert.assertNotNull(root);
        Assert.assertEquals("Home1", root.getLabel());

        root = modelClient.catalogCategories().getRootCategory("t2");
        Assert.assertNotNull(root);
        Assert.assertEquals("Home2", root.getLabel());        

        root = modelClient.catalogCategories().getRootCategory("t3");
        Assert.assertNotNull(root);
        Assert.assertEquals("Home3", root.getLabel());
        
    }
    
    private CatalogCategory createCategory(String value) {
        CatalogCategory model = new CatalogCategory();
        model.setId(URIUtil.createId(CatalogCategory.class));
        model.setLabel(value);
        model.setDescription(String.format("%s description", value));
        model.setImage(String.format("%s image", value));
        model.setTitle(String.format("%s title", value));
        model.setTenant(DEFAULT_TENANT);
        return model;
    }
    
    private void setParent(CatalogCategory parent, CatalogCategory child) {
        child.setCatalogCategoryId(new NamedURI(parent.getId(), parent.getLabel()));
    }
    
    private CatalogCategory createWithLabel(String label) {
        CatalogCategory model = new CatalogCategory();
        model.setId(URIUtil.createId(CatalogCategory.class));
        model.setLabel(label);
        URI parentUri = URIUtil.createId(CatalogCategory.class);
        NamedURI parentId = new NamedURI(parentUri, "my parent");
        model.setCatalogCategoryId(parentId);
        model.setDescription("my desc");
        model.setImage("my image");
        model.setTitle("my title");
        model.setTenant(DEFAULT_TENANT);
        return model;
    }
    
    
    private CatalogCategory createRoot(String tenant, String label) {
        CatalogCategory model = new CatalogCategory();
        model.setId(URIUtil.createId(CatalogCategory.class));
        model.setLabel(label);
        NamedURI parentId = new NamedURI(URI.create(CatalogCategory.NO_PARENT), "Home");
        model.setCatalogCategoryId(parentId);
        model.setDescription("my desc");
        model.setImage("my image");
        model.setTitle("my title");
        model.setTenant(tenant);
        return model;
    }    
}
