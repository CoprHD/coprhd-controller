/*
 * Copyright (c) 2015 EMC Corporation
 * All Rights Reserved
 */
package com.emc.vipr.model.catalog;

import java.io.*;
import java.net.URI;
import java.util.ArrayList;
import java.util.List;

import javax.xml.bind.annotation.XmlElement;
import org.codehaus.jackson.annotate.JsonProperty;

public class OrderCommonParam implements Serializable {
    static final long serialVersionUID = 2016081709567510155L;

    /**
     * Parameters to an order
     */
    private List<Parameter> parameters;

    /**
     * Service that this order will execute
     */
    private URI catalogService;

    @XmlElement(name = "parameters")
    public List<Parameter> getParameters() {
        if (parameters == null) {
            parameters = new ArrayList<>();
        }
        return parameters;
    }

    public void setParameters(List<Parameter> parameters) {
        this.parameters = parameters;
    }

    @JsonProperty("catalogService")
    @XmlElement(name = "catalog_service", required = true)
    public URI getCatalogService() {
        return catalogService;
    }

    @JsonProperty("catalogService")
    public void setCatalogService(URI catalogService) {
        this.catalogService = catalogService;
    }

    public Parameter findParameterByLabel(String label) {
        if (label != null) {
            for (Parameter parameter : getParameters()) {
                if (label.equals(parameter.getLabel())) {
                    return parameter;
                }
            }
        }
        return null;
    }

}
