/*
 * Copyright 2015 EMC Corporation
 * All Rights Reserved
 */
package com.emc.vipr.model.catalog;

import javax.xml.bind.annotation.XmlAnyElement;
import javax.xml.bind.annotation.XmlRootElement;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

@XmlRootElement(name = "list")
public class ApiList<T> implements Serializable {
    private List<T> list;

    public ApiList() {
    }

    public ApiList(Collection<T> collection) {
        this.list = new ArrayList<T>(collection);
    }

    @XmlAnyElement(lax=true)
    public List<T> getList() {
        if (list == null) {
            list = new ArrayList<T>();
        }
        return this.list;
    }
}
