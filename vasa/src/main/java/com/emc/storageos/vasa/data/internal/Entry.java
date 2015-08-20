/*
 * Copyright (c) 2012 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.vasa.data.internal;

import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlRootElement;

@XmlRootElement(name = "Entry")
public class Entry {
    @XmlElement
    String key;
    @XmlElement
    String value;

    @Override
    public String toString() {
        String s = "\t" + key + ":\t" + value + "\n";
        return s;
    }

}
