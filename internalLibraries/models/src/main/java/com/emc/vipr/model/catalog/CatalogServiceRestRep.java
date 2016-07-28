/*
 * Copyright (c) 2015 EMC Corporation
 * All Rights Reserved
 */
package com.emc.vipr.model.catalog;

import java.util.ArrayList;
import java.util.List;

import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlRootElement;

import com.emc.storageos.model.RelatedResourceRep;

@XmlRootElement(name = "catalog_service")
@XmlAccessorType(XmlAccessType.PROPERTY)
public class CatalogServiceRestRep extends SortedIndexRestRep {

    private String title;                                // Title of this category. Used as the title in the UI
    private String description;                          // Description of this category. Used as the description in the UI
    private String image;                                // Icon to show for this category.
    private boolean approvalRequired = false;            // Indicates if approval is required or not
    private boolean executionWindowRequired = false;     // Indicates if this service will run in an execution window
    private RelatedResourceRep defaultExecutionWindow; // ID of the execution window this service will run in
    private String baseService;                          // Engine Workflow that will be executed for this service
    private Integer maxSize;
    private RelatedResourceRep catalogCategory;
    private List<CatalogServiceFieldRestRep> catalogServiceFields;
    private ServiceDescriptorRestRep serviceDescriptor;
    private boolean recurringAllowed = false; 
    
    @XmlElement(name = "title")
    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    @XmlElement(name = "description")
    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    @XmlElement(name = "image")
    public String getImage() {
        return image;
    }

    public void setImage(String image) {
        this.image = image;
    }

    @XmlElement(name = "approval_required")
    public boolean isApprovalRequired() {
        return approvalRequired;
    }

    public void setApprovalRequired(boolean approvalRequired) {
        this.approvalRequired = approvalRequired;
    }

    @XmlElement(name = "execution_window_required")
    public boolean isExecutionWindowRequired() {
        return executionWindowRequired;
    }

    public void setExecutionWindowRequired(boolean executionWindowRequired) {
        this.executionWindowRequired = executionWindowRequired;
    }

    @XmlElement(name = "default_execution_window")
    public RelatedResourceRep getDefaultExecutionWindow() {
        return defaultExecutionWindow;
    }

    public void setDefaultExecutionWindow(RelatedResourceRep defaultExecutionWindow) {
        this.defaultExecutionWindow = defaultExecutionWindow;
    }

    @XmlElement(name = "base_service")
    public String getBaseService() {
        return baseService;
    }

    public void setBaseService(String baseService) {
        this.baseService = baseService;
    }

    @XmlElement(name = "max_size")
    public Integer getMaxSize() {
        return maxSize;
    }

    public void setMaxSize(Integer maxSize) {
        this.maxSize = maxSize;
    }

    @XmlElement(name = "catalog_category")
    public RelatedResourceRep getCatalogCategory() {
        return catalogCategory;
    }

    public void setCatalogCategory(RelatedResourceRep catalogCategory) {
        this.catalogCategory = catalogCategory;
    }

    @XmlElement(name = "catalog_service_fields")
    public List<CatalogServiceFieldRestRep> getCatalogServiceFields() {
        if (this.catalogServiceFields == null) {
            this.catalogServiceFields = new ArrayList<>();
        }
        return catalogServiceFields;
    }

    public void setCatalogServiceFields(List<CatalogServiceFieldRestRep> catalogServiceFields) {
        this.catalogServiceFields = catalogServiceFields;
    }

    @XmlElement(name = "service_descriptor")
    public ServiceDescriptorRestRep getServiceDescriptor() {
        return serviceDescriptor;
    }

    public void setServiceDescriptor(ServiceDescriptorRestRep descriptor) {
        this.serviceDescriptor = descriptor;
    }

    @XmlElement(name = "recurring_allowed")
    public boolean isRecurringAllowed() {
        return recurringAllowed;
    }
    
    public void setRecurringAllowed(boolean schedulerAllowed) {
        this.recurringAllowed = schedulerAllowed;
    }
    
}
