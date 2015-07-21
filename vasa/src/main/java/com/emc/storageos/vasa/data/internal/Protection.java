/*
 * Copyright (c) 2012 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.vasa.data.internal;

import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlRootElement;

@XmlRootElement(name = "protections")
public class Protection {

     @XmlElement
        String[] protections;

        @Override
        public String toString(){
            String s = "";
            for (int i=0;i<protections.length;i++){
                s+="\t"+protections[i];
            }
            return s;
        }


}
