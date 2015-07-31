/*
 * Copyright (c) 2015 EMC Corporation
 * All Rights Reserved
 */
package com.emc.vipr.model.catalog;

import java.net.URI;
import java.util.ArrayList;
import java.util.List;

import javax.xml.bind.annotation.XmlElement;

import com.emc.storageos.model.valid.Length;

public class CatalogServiceCommonParam {

    private String name;
    private String title;
    private String description;
    private String image;
    private Boolean approvalRequired;
    private Boolean executionWindowRequired;
    private URI defaultExecutionWindow;
    private String baseService;
    private Integer maxSize;
    private URI catalogCategory;
    private List<CatalogServiceFieldParam> catalogServiceFields;

    @XmlElement(name = "catalog_category")
    public URI getCatalogCategory() {
        return catalogCategory;
    }

    public void setCatalogCategory(URI catalogCategory) {
        this.catalogCategory = catalogCategory;
    }

    @XmlElement(required = true, name = "title")
    @Length(min = 2, max = 128)
    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    @XmlElement(required = true, name = "description")
    @Length(max = 255)
    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    @XmlElement(required = true, name = "image")
    @Length(max = 255)
    public String getImage() {
        return image;
    }

    public void setImage(String image) {
        this.image = image;
    }

    @XmlElement(name = "approval_required")
    public Boolean getApprovalRequired() {
        return approvalRequired;
    }

    public void setApprovalRequired(Boolean approvalRequired) {
        this.approvalRequired = approvalRequired;
    }

    @XmlElement(name = "execution_window_required")
    public Boolean getExecutionWindowRequired() {
        return executionWindowRequired;
    }

    public void setExecutionWindowRequired(Boolean executionWindowRequired) {
        this.executionWindowRequired = executionWindowRequired;
    }

    @XmlElement(name = "default_execution_window")
    public URI getDefaultExecutionWindow() {
        return defaultExecutionWindow;
    }

    public void setDefaultExecutionWindow(URI defaultExecutionWindow) {
        this.defaultExecutionWindow = defaultExecutionWindow;
    }

    @XmlElement(required = true, name = "base_service")
    @Length(max = 255)
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

    @XmlElement(required = true, name = "name")
    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    @XmlElement(name = "catalog_service_fields")
    public List<CatalogServiceFieldParam> getCatalogServiceFields() {
        if (this.catalogServiceFields == null) {
            this.catalogServiceFields = new ArrayList<>();
        }
        return catalogServiceFields;
    }

    public void setCatalogServiceFields(List<CatalogServiceFieldParam> catalogServiceFields) {
        this.catalogServiceFields = catalogServiceFields;
    }

}
