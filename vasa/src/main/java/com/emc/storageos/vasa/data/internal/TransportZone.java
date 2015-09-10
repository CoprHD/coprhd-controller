/*
 * Copyright (c) 2012 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.vasa.data.internal;

import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlRootElement;

@XmlRootElement(name = "TransportZone")
public class TransportZone {
    @XmlElement
    String id;
    @XmlElement
    boolean inactive;
    @XmlElement
    String label;
    @XmlElement
    Endpoint endpoints;
    @XmlElement
    String neighborhood;
    @XmlElement
    String transportType;

    @Override
    public String toString() {
        String s = new String();

        s += "TransportZone" + "\n";
        s += "\tid: " + id + "\n";
        s += "\tinactive: " + Boolean.toString(inactive) + "\n";
        s += "\tlabel: " + label + "\n";
        if (endpoints != null) {
            s += "\tendpoints:" + endpoints.toString() + "\n";
        }
        s += "\tneighborhood: " + neighborhood + "\n";
        s += "\ttransportType: " + transportType + "\n";
        return s;
    }

}
