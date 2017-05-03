/*
 * Copyright (c) 2015 EMC Corporation
 * All Rights Reserved
 */
package com.emc.vipr.client.core;

import static com.emc.vipr.client.core.util.ResourceUtils.defaultList;

import java.net.URI;
import java.util.List;
import java.util.Properties;

import com.emc.storageos.model.NamedRelatedResourceRep;
import com.emc.storageos.model.customservices.CustomServicesPrimitiveResourceList;
import com.emc.storageos.model.customservices.CustomServicesPrimitiveResourceRestRep;
import com.emc.vipr.client.ViPRCoreClient;
import com.emc.vipr.client.core.filters.ResourceFilter;
import com.emc.vipr.client.core.impl.PathConstants;
import com.emc.vipr.client.core.util.ResourceUtils;
import com.emc.vipr.client.impl.RestClient;

/**
 * CustomServicePrimitiveResource resources.
 * <p>
 * Base URL: <tt>/primitive/resources</tt>
 */
public class PrimitiveResources extends AbstractCoreResources<CustomServicesPrimitiveResourceRestRep>  implements
	TopLevelResources<CustomServicesPrimitiveResourceRestRep> {

	private static final String ANSIBLE_PACKAGE_RESOURCE_TYPE = "ANSIBLE";
	private static final String INVENTORY_FILE_RESOURCE_TYPE = "ANSIBLE_inventory";
	
    public PrimitiveResources(ViPRCoreClient parent, RestClient client) {
        super(parent, client, CustomServicesPrimitiveResourceRestRep.class, PathConstants.PRIMITIVE_RESOURCE_GET_URL);
    }

    @Override
    public PrimitiveResources withInactive(boolean inactive) {
        return (PrimitiveResources) super.withInactive(inactive);
    }

    @Override
    public PrimitiveResources withInternal(boolean internal) {
        return (PrimitiveResources) super.withInternal(internal);
    }

	@Override
	public List<? extends NamedRelatedResourceRep> list() {
    	Properties prop = new Properties();
    	prop.setProperty("type", ANSIBLE_PACKAGE_RESOURCE_TYPE);
		CustomServicesPrimitiveResourceList response = client.get(CustomServicesPrimitiveResourceList.class, baseUrl, prop);
        return ResourceUtils.defaultList(response.getResources());
	}

	@Override
	public List<CustomServicesPrimitiveResourceRestRep> getAll() {
		return getAll(null);
	}

    @Override
    public List<CustomServicesPrimitiveResourceRestRep> getAll(ResourceFilter<CustomServicesPrimitiveResourceRestRep> filter) {
        List<NamedRelatedResourceRep> refs = listAnsiblePackages();
        return getByRefs(refs);
    }
	
    /**
     * Get all primitive resource ansible packages
     * 
     * @param filter
     * @return
     */
    public List<CustomServicesPrimitiveResourceRestRep> getAnsiblePackages(ResourceFilter<CustomServicesPrimitiveResourceRestRep> filter) {
        List<NamedRelatedResourceRep> refs = listAnsiblePackages();
        return getByRefs(refs);
    }
    
    public List<NamedRelatedResourceRep> listAnsiblePackages() {
    	Properties prop = new Properties();
    	prop.setProperty("type", ANSIBLE_PACKAGE_RESOURCE_TYPE);
    	CustomServicesPrimitiveResourceList response = client.get(CustomServicesPrimitiveResourceList.class, baseUrl, prop);
        return ResourceUtils.defaultList(response.getResources());
    }	
    
    /**
     * Get all primitive resource inventory files
     * 
     * @param filter
     * @return
     */
    public List<CustomServicesPrimitiveResourceRestRep> getInventoryFiles(ResourceFilter<CustomServicesPrimitiveResourceRestRep> filter) {
        List<NamedRelatedResourceRep> refs = listInventoryFiles();
        return super.getByRefs(refs);
    }

    public List<NamedRelatedResourceRep> listInventoryFiles() {
    	Properties prop = new Properties();
    	prop.setProperty("type", INVENTORY_FILE_RESOURCE_TYPE);
    	CustomServicesPrimitiveResourceList response = client.get(CustomServicesPrimitiveResourceList.class, baseUrl, prop);
        return defaultList(response.getResources());
    }
    
    /**
     * Get all primitive resource inventory files by ansible package
     * 
     * @param filter
     * @param ansiblePackageId
     * @return
     */
    public List<CustomServicesPrimitiveResourceRestRep> getInventoryFilesByAnsiblePackage(ResourceFilter<CustomServicesPrimitiveResourceRestRep> filter, URI ansiblePackageId) {
    	return getByRefs(listInventoryFilesByAnsiblePackage(ansiblePackageId), filter);
    }
    
    public List<NamedRelatedResourceRep> listInventoryFilesByAnsiblePackage(URI ansiblePackageId) {
    	Properties prop = new Properties();
    	prop.setProperty("type", INVENTORY_FILE_RESOURCE_TYPE);
    	prop.setProperty("parentId", ansiblePackageId.toString());
    	CustomServicesPrimitiveResourceList response = client.get(CustomServicesPrimitiveResourceList.class, baseUrl, prop);
        return ResourceUtils.defaultList(response.getResources());
    }
    
}
