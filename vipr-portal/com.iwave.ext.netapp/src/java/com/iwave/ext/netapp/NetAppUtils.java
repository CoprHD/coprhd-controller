/**
* Copyright 2012-2015 iWave Software LLC
* All Rights Reserved
 */
package com.iwave.ext.netapp;

import java.util.List;

import netapp.manage.NaElement;

import org.apache.commons.lang.StringUtils;

public class NetAppUtils {
    
    public static void output(NaElement elem) {
        System.out.println("Results: " + elem.getName());
        output(elem, 0);
    }
    
    public static void output(NaElement elem, int level) {
        List<NaElement> children = elem.getChildren();
        if (children != null && children.size() > 0) {
            for (NaElement child : children) {
                output(child, (level + 1));
            }
        }
        else {
            System.out.println(StringUtils.repeat("-", level) + elem.getName() + ": " + elem.getContent());
        }
    }   
}
