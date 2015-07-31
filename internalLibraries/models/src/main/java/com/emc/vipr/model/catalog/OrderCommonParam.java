/*
 * Copyright (c) 2015 EMC Corporation
 * All Rights Reserved
 */
package com.emc.vipr.model.catalog;

import java.net.URI;
import java.util.ArrayList;
import java.util.List;

import javax.xml.bind.annotation.XmlElement;

public class OrderCommonParam {

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

    @XmlElement(name = "catalog_service", required = true)
    public URI getCatalogService() {
        return catalogService;
    }

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
