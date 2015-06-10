/*
 * Copyright 2015 EMC Corporation
 * All Rights Reserved
 */
package com.emc.vipr.model.catalog;

import javax.xml.bind.annotation.XmlRootElement;

@Deprecated
@XmlRootElement
public class ServiceInfo extends ModelInfo {
    
    /**
     * Name of this category
     */
    private String name;                               
    
    /**
     * Title of this category. Used as the title in the UI
     */
    private String title;                              
    
    /**
     * Description of this category. Used as the description in the UI
     */
    private String description;                         
    
    /**
     * Icon to show for this category.
     */
    private String image;                               
    
    /**
     * Indicates if approval is required or not
     */
    private boolean approvalRequired = false;           
    
    /**
     * Indicates if this service will run in an execution window
     */
    private boolean executionWindowRequired = false;    
    
    /**
     * ID of the execution window this service will run in
     */
    private String defaultExecutionWindowId;           
    
    /**
     * Engine Workflow that will be executed for this service
     */
    private String baseService;                        
    
    private Integer maxSize;

    public boolean isApprovalRequired() {
        return approvalRequired;
    }

    public void setApprovalRequired(boolean approvalRequired) {
        this.approvalRequired = approvalRequired;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getBaseService() {
        return baseService;
    }

    public void setBaseService(String baseService) {
        this.baseService = baseService;
    }

    public String getDefaultExecutionWindowId() {
        return defaultExecutionWindowId;
    }

    public void setDefaultExecutionWindowId(String defaultExecutionWindowId) {
        this.defaultExecutionWindowId = defaultExecutionWindowId;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public boolean isExecutionWindowRequired() {
        return executionWindowRequired;
    }

    public void setExecutionWindowRequired(boolean executionWindowRequired) {
        this.executionWindowRequired = executionWindowRequired;
    }

    public String getImage() {
        return image;
    }

    public void setImage(String image) {
        this.image = image;
    }

    public Integer getMaxSize() {
        return maxSize;
    }

    public void setMaxSize(Integer maxSize) {
        this.maxSize = maxSize;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    @Override
    public String toString() {
        return String.format("Service %s (%s), '%s' '%s'", name, id, title, description);
    }
}
