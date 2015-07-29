/*
 * Copyright (c) 2014 EMC Corporation
 * All Rights Reserved
 */

package com.emc.storageos.customconfigcontroller;

import java.util.List;

/**
 * A string manipulation function that trims the beginning or trailing chars in a string
 * 
 */
public class TrimCustomConfigMethod extends CustomConfigMethod {

    public String invoke(String str, List<String> args) {
        String chars = args.get(0);
        String regex = "^(" + chars + ")+|(" + chars + ")+$";
        return str.replaceAll(regex, "");
    }

}
