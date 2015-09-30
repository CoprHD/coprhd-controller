/*
 * Copyright (c) 2012 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.vasa.data.internal;

import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlRootElement;

@XmlRootElement(name = "StringMap")
public class StringMap {

    @XmlElement
    Entry entry[];

    @Override
    public String toString() {
        String s = "";
        for (int i = 0; i < entry.length; i++) {
            s += "\t" + entry[i].toString();
        }
        return s;
    }

}
