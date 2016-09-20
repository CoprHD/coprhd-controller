/*
 * Copyright (c) 2015 EMC Corporation
 * All Rights Reserved
 */
package com.emc.sa.catalog;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.URL;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.MissingResourceException;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;

import org.apache.commons.codec.digest.DigestUtils;
import org.apache.commons.io.IOUtils;
import org.apache.commons.lang.StringUtils;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import com.emc.sa.descriptor.ServiceDescriptor;
import com.emc.sa.descriptor.ServiceDescriptors;
import com.emc.storageos.db.client.model.uimodels.CatalogCategory;
import com.emc.storageos.db.client.model.uimodels.CatalogService;
import com.emc.storageos.db.client.model.uimodels.CatalogServiceField;
import com.emc.sa.model.dao.ModelClient;
import com.emc.sa.util.Messages;
import com.emc.storageos.db.client.model.NamedURI;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

public class CatalogBuilder {

    private ModelClient models;
    private ServiceDescriptors descriptors;
    private Messages MESSAGES = new Messages(CatalogBuilder.class, "default-catalog");

    private int sortedIndexCounter = 1;

    public CatalogBuilder(ModelClient models, ServiceDescriptors descriptors) {
        this.models = models;
        this.descriptors = descriptors;
    }

    public CatalogCategory buildCatalog(String tenant, URL resource) throws IOException {
        return buildCatalog(tenant, resource.openStream());
    }

    public CatalogCategory buildCatalog(String tenant, File f) throws IOException {
        return buildCatalog(tenant, new FileInputStream(f));
    }

    public CatalogCategory buildCatalog(String tenant, InputStream in) throws IOException {
        CategoryDef root = readCatalogDef(in);
        return saveCatalog(tenant, root);
    }

    public void clearCategory(CatalogCategory category) {
        models.delete(models.catalogServices().findByCatalogCategory(category.getId()));
        List<CatalogCategory> children = models.catalogCategories().findSubCatalogCategories(category.getId());
        for (CatalogCategory child : children) {
            clearCategory(child);
        }
        models.delete(category);
    }

    public static CategoryDef readCatalogDef(InputStream in) throws IOException {
        try {
            String catalog = IOUtils.toString(in);

            Gson gson = new GsonBuilder().create();
            CategoryDef root = gson.fromJson(catalog, CategoryDef.class);
            
            CategoryDef extCategory=loadCustomCategories();
            if (extCategory != null){
            	root.categories.add(extCategory);
            }
            root.version = DigestUtils.sha1Hex(catalog);
            return root;
        } finally {
            IOUtils.closeQuietly(in);
        }
    }

    private static CategoryDef loadCustomCategories() {
		try {
		   	InputStream customCategories = new FileInputStream("/opt/storageos/customFlow/custom-catalog.json");
	    	String catalog;
			catalog = IOUtils.toString(customCategories);
	        Gson gson = new GsonBuilder().create();
	        CategoryDef extCatorories = gson.fromJson(catalog, CategoryDef.class);
	        IOUtils.closeQuietly(customCategories);
			return extCatorories;
		} catch (IOException e) {
			return null;
		} 

	}

	public static String getCatalogHash(InputStream in) throws IOException {
        return DigestUtils.sha1Hex(in);
    }

    protected CatalogCategory saveCatalog(String tenant, CategoryDef def) {
        NamedURI rootId = new NamedURI(URI.create(CatalogCategory.NO_PARENT), def.label);
        return createCategory(tenant, def, rootId);
    }

    public CatalogCategory createCategory(String tenant, CategoryDef def, NamedURI parentId) {
        String label = getMessage(getLabel(def));
        String title = getMessage(def.title);
        String description = getMessage(def.description);

        CatalogCategory category = new CatalogCategory();
        category.setTenant(tenant);
        category.setLabel(StringUtils.deleteWhitespace(label));
        category.setTitle(title);
        category.setDescription(description);
        category.setImage(def.image);
        category.setCatalogCategoryId(parentId);
        category.setSortedIndex(sortedIndexCounter++);
        category.setVersion(def.version);
        models.save(category);

        NamedURI myId = new NamedURI(category.getId(), category.getLabel());
        if (def.categories != null) {
            for (CategoryDef categoryDef : def.categories) {
                createCategory(tenant, categoryDef, myId);
            }
        }
        if (def.services != null) {
            for (ServiceDef serviceDef : def.services) {
                createService(serviceDef, myId);
            }
        }
        return category;
    }

    public CatalogService createService(ServiceDef def, NamedURI parentId) {
        ServiceDescriptor descriptor = descriptors.getDescriptor(Locale.getDefault(), def.baseService);
        String label = StringUtils.defaultString(getMessage(getLabel(def)), descriptor.getTitle());
        String title = StringUtils.defaultString(getMessage(def.title), descriptor.getTitle());
        String description = StringUtils.defaultString(getMessage(def.description), descriptor.getDescription());

        CatalogService service = new CatalogService();
        service.setBaseService(def.baseService);
        service.setLabel(StringUtils.deleteWhitespace(label));
        service.setTitle(title);
        service.setDescription(description);
        service.setImage(def.image);
        service.setCatalogCategoryId(parentId);
        service.setSortedIndex(sortedIndexCounter++);
        models.save(service);

        if (def.lockFields != null) {
            for (Map.Entry<String, String> lockField : def.lockFields.entrySet()) {
                CatalogServiceField field = new CatalogServiceField();
                field.setLabel(lockField.getKey());
                field.setValue(lockField.getValue());
                field.setCatalogServiceId(new NamedURI(service.getId(), service.getLabel()));
                models.save(field);
            }
        }

        return service;
    }

    protected String getLabel(CategoryDef def) {
        return StringUtils.defaultString(def.label, def.title);
    }

    protected String getLabel(ServiceDef def) {
        return StringUtils.defaultString(def.label, def.title);
    }

    protected String getMessage(String key) {
        try {
            return (key != null) ? MESSAGES.get(key) : null;
        } catch (MissingResourceException e) {
            return key;
        }
    }
}
