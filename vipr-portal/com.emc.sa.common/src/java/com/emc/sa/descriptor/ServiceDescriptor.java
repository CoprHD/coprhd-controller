/*
 * Copyright 2015-2016 Dell Inc. or its subsidiaries.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 */
package com.emc.sa.descriptor;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.MissingResourceException;
import java.util.Set;

import org.apache.commons.lang.StringUtils;
import org.apache.commons.lang.builder.ToStringBuilder;
import org.apache.commons.lang.builder.ToStringStyle;

import com.emc.sa.util.Messages;

/**
 */
public class ServiceDescriptor implements ServiceItemContainer, Serializable {
    private static final long serialVersionUID = 7165847559597979106L;
    
    private static Messages MESSAGES = new Messages("com.emc.sa.descriptor.ServiceDescriptors");

    /** The ID of the service. */
    private String serviceId;

    /** Allow the descriptors to be put into categories for organization. */
    private String category;

    /** The service title. */
    private String title;

    /** The service description. */
    private String description;
    
    /** The ID of the workflow */
    private String workflowId;

    /** The items in the service. */
    private Map<String, ServiceItem> items = new LinkedHashMap<>();

    /**
     * Optional roles to limit the service visibility to (User must have one). No restriction is required as this is
     * more intended to limit things already restricted by the services that run or asset options required for them.
     */
    private List<String> roles = new ArrayList<>();

    /** Indicates if the operation can result in data loss (Warn the user) */
    private boolean destructive = false;
    
    /** Indicated if the order will display a modal option */
    private boolean useModal = false;
    
    /** The service modal title. */
    private String modalTitle;

    /** Whether the service will display a modal window for the service with a order button instead of the default Preview. */
    private boolean useOrderModal = false;

    public String getServiceId() {
        return serviceId;
    }

    public void setServiceId(String serviceId) {
        this.serviceId = serviceId;
    }

    public String getCategory() {
        return category;
    }

    public void setCategory(String category) {
        this.category = category;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public String getWorkflowId() {
        return workflowId;
    }
    
    public void setWorkflowId(final String workflowId) {
        this.workflowId = workflowId;
    }
    
    @Override
    public Map<String, ServiceItem> getItems() {
        return items;
    }

    public void setItems(Map<String, ServiceItem> items) {
        this.items = items;
    }

    public void addItem(ServiceItem item) {
        items.put(item.getName(), item);
    }

    public List<String> getRoles() {
        return roles;
    }

    public void setRoles(List<String> roles) {
        this.roles = roles;
    }

    public void addRole(String role) {
        roles.add(role);
    }

    public void addRoles(Collection<String> roles) {
        this.roles.addAll(roles);
    }

    public boolean isDestructive() {
        return destructive;
    }

    public void setDestructive(boolean destructive) {
        this.destructive = destructive;
    }
    
    public boolean isUseModal() {
        return useModal;
    }
    
    public void setUseModal(boolean useModal) {
        this.useModal = useModal;
    }
    
    public String getModalTitle() {
        if (modalTitle == null) {
            return modalTitle;
        }
        return getMessage(modalTitle);
    }
    
    public void setModalTitle(String modalTitle) {
        this.modalTitle = modalTitle;
    }

    public ServiceField getField(String name) {
        return ServiceField.findField(this, name);
    }

    public void removeField(String name) {
        ServiceField.removeField(this, name);
    }

    public List<ServiceField> getFieldList() {
        return ServiceField.getFieldList(this);
    }

    public List<ServiceField> getAllFieldList() {
        return ServiceField.getAllFieldList(this);
    }

    public List<ServiceField> getAssetFields() {
        return ServiceField.getAssetFields(this);
    }

    public Set<String> getAssetTypes() {
        return ServiceField.getAssetTypes(this);
    }

    public Set<String> getAllAssetTypes() {
        Set<String> allAvailableAssets = new HashSet<>();
        for (ServiceField field : getAllFieldList()) {
            if (field.isAsset()) {
                allAvailableAssets.add(field.getAssetType());
            }
        }
        return allAvailableAssets;
    }

    @Override
    public String toString() {
        ToStringBuilder builder = new ToStringBuilder(this, ToStringStyle.SHORT_PREFIX_STYLE);
        builder.append("serviceId", serviceId);
        builder.append("title", title);
        builder.append("description", description);
        builder.append("category", category);
        builder.append("destructive", destructive);
        builder.append("useModal", useModal);
        builder.append("modalTitle", modalTitle);
        builder.append("roles", roles);
        builder.append("items", items);
        builder.append("useOrderModal", useOrderModal);
        return builder.toString();
    }
    
    public static String getMessage(String key, Object... args) {
        try {
            String message = MESSAGES.get(key, args);
            if (StringUtils.isNotBlank(message)) {
                return message;
            }
        } catch (MissingResourceException e) {
            // fall out and return the original key
        }
        return key;
    }

    public boolean isUseOrderModal() {
        return useOrderModal;
    }

    public void setUseOrderModal(boolean useOrderModal) {
        this.useOrderModal = useOrderModal;
    }
}
