/*
 * Copyright (c) 2017 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.driver.univmax.rest.type.system;

/**
 * @author fengs5
 *
 */
public class TaskType {
    private int execution_order;
    private String description;

    /**
     * @return the execution_order
     */
    public int getExecution_order() {
        return execution_order;
    }

    /**
     * @param execution_order the execution_order to set
     */
    public void setExecution_order(int execution_order) {
        this.execution_order = execution_order;
    }

    /**
     * @return the description
     */
    public String getDescription() {
        return description;
    }

    /**
     * @param description the description to set
     */
    public void setDescription(String description) {
        this.description = description;
    }

    /*
     * (non-Javadoc)
     * 
     * @see java.lang.Object#toString()
     */
    @Override
    public String toString() {
        return "TaskType [execution_order=" + execution_order + ", description=" + description + "]";
    }

}
