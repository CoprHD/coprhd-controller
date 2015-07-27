/*
 * Copyright 2015 EMC Corporation
 * All Rights Reserved
 */
package com.emc.vipr.client.catalog;

import static com.emc.vipr.client.core.util.ResourceUtils.uri;
import java.net.URI;
import java.util.*;

import com.emc.vipr.client.ViPRCatalogClient2;
import com.emc.vipr.client.catalog.impl.PathConstants;
import com.emc.vipr.client.impl.RestClient;
import com.emc.vipr.model.catalog.AssetDependencyRequest;
import com.emc.vipr.model.catalog.AssetDependencyResponse;
import com.emc.vipr.model.catalog.AssetOption;
import com.emc.vipr.model.catalog.AssetOptionsRequest;
import com.emc.vipr.model.catalog.AssetOptionsResponse;
import com.emc.vipr.model.catalog.CatalogServiceRestRep;
import com.emc.vipr.model.catalog.ServiceDescriptorRestRep;
import com.emc.vipr.model.catalog.ServiceFieldGroupRestRep;
import com.emc.vipr.model.catalog.ServiceFieldRestRep;
import com.emc.vipr.model.catalog.ServiceFieldTableRestRep;
import com.emc.vipr.model.catalog.ServiceItemRestRep;


public class AssetOptions  {

    protected final ViPRCatalogClient2 parent;
    protected final RestClient client;
    
    public AssetOptions(ViPRCatalogClient2 parent, RestClient client) {
        this.parent = parent;
        this.client = client;
    }

    public List<AssetOption> getAssetOptions(String assetType) {
        return getAssetOptions(assetType, new HashMap<String, String>());
    }

    public List<AssetOption> getAssetOptions(String assetType, Map<String, String> assetParameters) {
        AssetOptionsRequest request = new AssetOptionsRequest();
        request.setTenantId(uri(parent.getUserInfo().getTenant()));
        request.setAvailableAssets(assetParameters);
        return getAssetOptions(assetType, request);
    }

    public List<AssetOption> getAssetOptions(String assetType, AssetOptionsRequest request) {
        AssetOptionsResponse response = client.post(AssetOptionsResponse.class, request, PathConstants.ASSET_OPTIONS2_OPTIONS_URL, cleanAssetType(assetType));
        return response.getOptions();
    }
    
    public List<String> getAssetDependencies(String assetType, String serviceDescriptorName) {
        ServiceDescriptorRestRep serviceDescriptor = parent.serviceDescriptors().getServiceDescriptor(serviceDescriptorName);
        return getAssetDependencies(assetType, serviceDescriptor);
    }
    
    public List<String> getAssetDependencies(String assetType, URI catalogServiceId) {
        CatalogServiceRestRep catalogService = parent.services().get(catalogServiceId);
        ServiceDescriptorRestRep serviceDescriptor = catalogService.getServiceDescriptor();
        return getAssetDependencies(assetType, serviceDescriptor);
    }
    
    public List<String> getAssetDependencies(String assetType, ServiceDescriptorRestRep serviceDescriptor) {
        return getAssetDependencies(assetType, getAllAssetTypes(serviceDescriptor));
    }    
    
    public List<String> getAssetDependencies(String assetType, Set<String> availableAssetTypes) { 
        AssetDependencyRequest request = new AssetDependencyRequest();
        request.setTenantId(uri(parent.getUserInfo().getTenant()));
        request.setAvailableAssetTypes(availableAssetTypes);
        return getAssetDependencies(assetType, request);
    }
    
    public List<String> getAssetDependencies(String assetType, AssetDependencyRequest request) {
        AssetDependencyResponse response = client.post(AssetDependencyResponse.class, request, PathConstants.ASSET_OPTIONS2_DEP_URL, cleanAssetType(assetType));
        return response.getAssetDependencies();    
    }
    
    private String cleanAssetType(String assetType) {
        return assetType.replaceFirst("assetType\\.", "");
    }    
    
    public static Set<String> getAllAssetTypes(ServiceDescriptorRestRep serviceDescriptor) {
        Set<String> allAvailableAssets = new HashSet<>();
        for (ServiceFieldRestRep field : getAllFields(serviceDescriptor.getItems())) {
            if (field.isAsset()) {
                allAvailableAssets.add(field.getAssetType());
            }
        }
        return allAvailableAssets;
    }    
    
    public static List<ServiceFieldRestRep> getAllFields(List<? extends ServiceItemRestRep> items) {
        List<ServiceFieldRestRep> allFields = new ArrayList<>();
        for (ServiceItemRestRep item : items) {
            if (item.isField()) {
                allFields.add((ServiceFieldRestRep) item);
            }
            else if (item.isGroup()) {
                allFields.addAll(getAllFields(((ServiceFieldGroupRestRep) item).getItems()));
            }
            else if (item.isTable()) {
                allFields.addAll(getAllFields(((ServiceFieldTableRestRep) item).getItems()));
            }
        }
        return allFields;
    }        
    
}
