/*
 * Copyright (c) 2015 EMC Corporation
 * All Rights Reserved
 */
package com.emc.sa.api.mapper;

import java.util.Collection;
import java.util.List;

import com.emc.sa.descriptor.ServiceDescriptor;
import com.emc.sa.descriptor.ServiceField;
import com.emc.sa.descriptor.ServiceFieldGroup;
import com.emc.sa.descriptor.ServiceFieldTable;
import com.emc.sa.descriptor.ServiceItem;
import com.emc.vipr.model.catalog.Option;
import com.emc.vipr.model.catalog.ServiceDescriptorRestRep;
import com.emc.vipr.model.catalog.ServiceFieldGroupRestRep;
import com.emc.vipr.model.catalog.ServiceFieldRestRep;
import com.emc.vipr.model.catalog.ServiceFieldTableRestRep;
import com.emc.vipr.model.catalog.ServiceItemRestRep;
import com.google.common.collect.Lists;

public class ServiceDescriptorMapper {

    public static ServiceDescriptorRestRep map(ServiceDescriptor from) {
        if (from == null) {
            return null;
        }
        ServiceDescriptorRestRep to = new ServiceDescriptorRestRep();

        to.setCategory(from.getCategory());
        to.setDescription(from.getDescription());
        to.setDestructive(from.isDestructive());
        to.setServiceId(from.getServiceId());
        to.setTitle(from.getTitle());
        to.setRoles(from.getRoles());
        to.getItems().addAll(map(from.getItems().values()));

        return to;
    }

    public static ServiceFieldRestRep map(ServiceField from) {
        if (from == null) {
            return null;
        }

        ServiceFieldRestRep to = new ServiceFieldRestRep();

        mapServiceItemCommon(to, from);

        to.setFailureMessage(from.getValidation().getError());
        to.setInitialValue(from.getInitialValue());
        to.setLockable(from.isLockable());
        to.setDisableEmpty(from.isDisableEmpty());
        to.setMax(from.getValidation().getMax());
        to.setMin(from.getValidation().getMin());
        to.setRegEx(from.getValidation().getRegEx());
        to.setRequired(from.isRequired());
        to.setSelect(from.getSelect());

        for (String key : from.getOptions().keySet()) {
            to.getOptions().add(new Option(key, from.getOptions().get(key)));
        }

        return to;
    }

    public static ServiceFieldGroupRestRep map(ServiceFieldGroup from) {
        if (from == null) {
            return null;
        }

        ServiceFieldGroupRestRep to = new ServiceFieldGroupRestRep();

        mapServiceItemCommon(to, from);

        to.setCollapsed(from.isCollapsed());
        to.setCollapsible(from.isCollapsible());
        to.getItems().addAll(map(from.getItems().values()));

        return to;
    }

    public static ServiceFieldTableRestRep map(ServiceFieldTable from) {
        if (from == null) {
            return null;
        }

        ServiceFieldTableRestRep to = new ServiceFieldTableRestRep();

        mapServiceItemCommon(to, from);

        to.getItems().addAll((List<ServiceFieldRestRep>) map(from.getItems().values()));

        return to;
    }

    public static List<? extends ServiceItemRestRep> map(Collection<? extends ServiceItem> items) {
        List<ServiceItemRestRep> itemRestReps = Lists.newArrayList();
        for (ServiceItem item : items) {
            if (item instanceof ServiceField) {
                itemRestReps.add(map((ServiceField) item));
            }
            else if (item instanceof ServiceFieldGroup) {
                itemRestReps.add(map((ServiceFieldGroup) item));
            }
            else if (item instanceof ServiceFieldTable) {
                itemRestReps.add(map((ServiceFieldTable) item));
            }
        }
        return itemRestReps;
    }

    private static <T extends ServiceItemRestRep> T mapServiceItemCommon(T restRep, ServiceItem serviceItem) {
        restRep.setDescription(serviceItem.getDescription());
        restRep.setLabel(serviceItem.getLabel());
        restRep.setName(serviceItem.getName());
        restRep.setType(serviceItem.getType());
        return restRep;
    }

}