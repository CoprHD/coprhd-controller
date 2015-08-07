/*
 * Copyright (c) 2012 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.vasa.data.internal;

import java.util.List;

import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlRootElement;

public class Resource {
    @XmlElement
    private String id;
    @XmlElement(name = "resource_type")
    private String type;
    @XmlElement(name = "name")
    private String label;

    /**
     * @return the id
     */
    public String getId() {
        return id;
    }

    /**
     * @return the type
     */
    public String getType() {
        return type;
    }

    /**
     * @return the label
     */
    public String getLabel() {
        return label;
    }

    /*
     * (non-Javadoc)
     * 
     * @see java.lang.Object#toString()
     */
    @Override
    public String toString() {
        StringBuilder builder = new StringBuilder();
        builder.append("Resource [id=");
        builder.append(id);
        builder.append(", type=");
        builder.append(type);
        builder.append(", label=");
        builder.append(label);
        builder.append("]");
        return builder.toString();
    }

    @XmlRootElement(name = "project_resources")
    public static class ResourceList {

        @XmlElement(name = "project_resource")
        private List<Resource> resourceList;

        /**
         * @return the resource
         */
        public List<Resource> getResourceList() {
            return resourceList;
        }

        /*
         * (non-Javadoc)
         * 
         * @see java.lang.Object#toString()
         */
        @Override
        public String toString() {
            StringBuilder builder = new StringBuilder();
            builder.append("ResourceList [resourceList=");
            builder.append(resourceList);
            builder.append("]");
            return builder.toString();
        }

    }
}
