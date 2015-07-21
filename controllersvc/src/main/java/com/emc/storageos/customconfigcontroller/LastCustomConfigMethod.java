/*
 * Copyright (c) 2014 EMC Corporation
 * All Rights Reserved
 */

package com.emc.storageos.customconfigcontroller;

import java.util.List;


/**
 * A string manipulation function that returns the last n characters in a string 
 *
 */
public class LastCustomConfigMethod extends CustomConfigMethod {
	
    public String invoke(String str, List<String> args) {
        int len = Integer.parseInt(args.get(0));
        if (len < str.length()) {
            str = str.substring(str.length() - len);
        }
        return str;
    }
}
