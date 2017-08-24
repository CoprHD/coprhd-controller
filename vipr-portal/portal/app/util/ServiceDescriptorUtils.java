/*
 * Copyright (c) 2015 EMC Corporation
 * All Rights Reserved
 */
package util;

import static util.BourneUtil.getCatalogClient;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Set;

import com.emc.vipr.client.ViPRCatalogClient2;
import com.emc.vipr.client.catalog.AssetOptions;
import com.emc.vipr.model.catalog.ServiceDescriptorRestRep;
import com.emc.vipr.model.catalog.ServiceFieldGroupRestRep;
import com.emc.vipr.model.catalog.ServiceFieldModalRestRep;
import com.emc.vipr.model.catalog.ServiceFieldRestRep;
import com.emc.vipr.model.catalog.ServiceFieldTableRestRep;
import com.emc.vipr.model.catalog.ServiceItemRestRep;

public class ServiceDescriptorUtils {

    public static ServiceDescriptorRestRep getDescriptor(String baseServiceName) {
        ViPRCatalogClient2 catalog = getCatalogClient();
        return catalog.serviceDescriptors().getServiceDescriptor(baseServiceName);
    }

    public static List<ServiceDescriptorRestRep> getDescriptors() {
        ViPRCatalogClient2 catalog = getCatalogClient();
        return catalog.serviceDescriptors().getServiceDescriptors();
    }

    public static ServiceFieldRestRep getField(ServiceDescriptorRestRep serviceDescriptor, String name) {
        return findField(serviceDescriptor.getItems(), name);
    }

    public static List<ServiceFieldRestRep> getAssetFields(List<ServiceItemRestRep> items) {
        List<ServiceFieldRestRep> assetFields = new ArrayList<ServiceFieldRestRep>();
        for (ServiceItemRestRep item : items) {
            if (item.isField() && ((ServiceFieldRestRep) item).isAsset()) {
                assetFields.add(((ServiceFieldRestRep) item));
            }
        }
        return assetFields;
    }

    public static Set<String> getAllAssetTypes(ServiceDescriptorRestRep serviceDescriptor) {
        return AssetOptions.getAllAssetTypes(serviceDescriptor);
    }

    public static List<ServiceFieldRestRep> getAllFieldList(List<? extends ServiceItemRestRep> items) {
        return AssetOptions.getAllFields(items);
    }

    public static ServiceFieldRestRep findField(Collection<? extends ServiceItemRestRep> items, String name) {
        ServiceItemRestRep value = findItemByName(items, name);
        if (value != null && value.isField()) {
            return (ServiceFieldRestRep) value;
        }

        // Search for a nested field
        for (ServiceItemRestRep item : items) {
            if (item instanceof ServiceFieldGroupRestRep) {
                ServiceFieldGroupRestRep group = (ServiceFieldGroupRestRep) item;
                ServiceFieldRestRep field = findField(group.getItems(), name);
                if (field != null) {
                    return field;
                }
            }
            else if (item instanceof ServiceFieldTableRestRep) {
                ServiceFieldTableRestRep table = (ServiceFieldTableRestRep) item;
                ServiceFieldRestRep field = findField(table.getItems(), name);
                if (field != null) {
                    return field;
                }
            }
            else if (item instanceof ServiceFieldModalRestRep) {
                ServiceFieldModalRestRep modal = (ServiceFieldModalRestRep) item;
                ServiceFieldRestRep field = findField(modal.getItems(), name);
                if (field != null) {
                    return field;
                }
            }
        }
        // No field found
        return null;
    }

    public static ServiceItemRestRep findItemByName(Collection<? extends ServiceItemRestRep> items, String name) {
        if (items != null && name != null) {
            for (ServiceItemRestRep item : items) {
                if (item != null && name.equals(item.getName())) {
                    return item;
                }
            }
        }
        return null;
    }

}
