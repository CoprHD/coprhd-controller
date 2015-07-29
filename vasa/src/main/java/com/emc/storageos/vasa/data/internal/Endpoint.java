/*
 * Copyright (c) 2012 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.vasa.data.internal;

import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlRootElement;

@XmlRootElement(name = "endpoints")
public class Endpoint {

    @XmlElement
    String[] endpoints;

    @Override
    public String toString() {
        String s = "";
        for (int i = 0; i < endpoints.length; i++) {
            s += "\t" + endpoints[i];
        }
        return s;
    }

}
