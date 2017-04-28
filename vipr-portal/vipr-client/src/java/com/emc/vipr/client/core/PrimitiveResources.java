/*
 * Copyright (c) 2015 EMC Corporation
 * All Rights Reserved
 */
package com.emc.vipr.client.core;

import java.net.URI;
import java.util.List;
import java.util.Properties;

import com.emc.storageos.model.BulkIdParam;
import com.emc.storageos.model.NamedRelatedResourceRep;
import com.emc.storageos.model.auth.ACLAssignmentChanges;
import com.emc.storageos.model.auth.ACLEntry;
import com.emc.storageos.model.customservices.CustomServicesPrimitiveResourceList;
import com.emc.storageos.model.customservices.CustomServicesPrimitiveResourceRestRep;
import com.emc.vipr.client.ViPRCoreClient;
import com.emc.vipr.client.core.filters.ResourceFilter;
import com.emc.vipr.client.core.impl.PathConstants;
import com.emc.vipr.client.core.util.ResourceUtils;
import com.emc.vipr.client.impl.RestClient;

public class PrimitiveResources extends AbstractCoreBulkResources<CustomServicesPrimitiveResourceRestRep> implements
        TopLevelResources<CustomServicesPrimitiveResourceRestRep>, ACLResources {
	
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
    protected List<CustomServicesPrimitiveResourceRestRep> getBulkResources(BulkIdParam input) {
        return null;
    }

    /**
     * Gets the list of all inventory files. This is a convenience method for: <tt>getByRefs(list())</tt>.
     * 
     * @return the list of inventory files.
     */
    @Override
    public List<CustomServicesPrimitiveResourceRestRep> getAll() {
        return getAll(null);
    }

    /**
     * Gets the list of all inventory files, optionally filtering the results. This is a convenience method for:
     * <tt>getByRefs(list(), filter)</tt>.
     * 
     * @param filter
     *            the resource filter to apply to the results as they are returned (optional).
     * @return the list of inventory files.
     */
	@Override
	public List<CustomServicesPrimitiveResourceRestRep> getAll(ResourceFilter<CustomServicesPrimitiveResourceRestRep> filter) {
		return getByRefs(list(), filter);
	}

    /**
     * Lists all primitive resource ansible packages.
     * <p>
     * API Call: <tt>GET /primitives/resource?type="ANSIBLE"</tt>
     * 
     * @return the list of inventory files references.
     */
    @Override
    public List<NamedRelatedResourceRep> list() {
    	CustomServicesPrimitiveResourceList response = client.get(CustomServicesPrimitiveResourceList.class, baseUrl, ANSIBLE_PACKAGE_RESOURCE_TYPE);
        return ResourceUtils.defaultList(response.getResources());
    }

    @Override
    public List<ACLEntry> getACLs(URI id) {
        return doGetACLs(id);
    }

    @Override
    public List<ACLEntry> updateACLs(URI id, ACLAssignmentChanges aclChanges) {
        return doUpdateACLs(id, aclChanges);
    }

    /**
     * Get all primitive resource ansible packages
     * 
     * @param filter
     * @return
     */
    public List<CustomServicesPrimitiveResourceRestRep> getAnsiblePackages(ResourceFilter<CustomServicesPrimitiveResourceRestRep> filter) {
    	return getByRefs(listAnsiblePackages(), filter);
    }
    
    public List<NamedRelatedResourceRep> listAnsiblePackages() {
    	Properties prop = new Properties();
    	prop.setProperty("type", ANSIBLE_PACKAGE_RESOURCE_TYPE);
    	CustomServicesPrimitiveResourceList response = client.get(CustomServicesPrimitiveResourceList.class, baseUrl, prop);
        return ResourceUtils.defaultList(response.getResources());
    }
    	
    /**
     * Get all primitive resource inventory files.
     * 
     * @param filter
     * @return
     */
    public List<CustomServicesPrimitiveResourceRestRep> getInventoryFiles(ResourceFilter<CustomServicesPrimitiveResourceRestRep> filter) {
    	return getByRefs(listInventoryFiles(), filter);
    }
    
    public List<NamedRelatedResourceRep> listInventoryFiles() {
    	Properties prop = new Properties();
    	prop.setProperty("type", INVENTORY_FILE_RESOURCE_TYPE);
    	CustomServicesPrimitiveResourceList response = client.get(CustomServicesPrimitiveResourceList.class, baseUrl, prop);
        return ResourceUtils.defaultList(response.getResources());
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
