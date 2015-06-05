/**
* Copyright 2015 EMC Corporation
* All Rights Reserved
 */
/**
 *  Copyright (c) 2014 EMC Corporation
 * All Rights Reserved
 *
 * This software contains the intellectual property of EMC Corporation
 * or is licensed to EMC Corporation from third parties.  Use of this
 * software and the intellectual property contained therein is expressly
 * limited to the terms and conditions of the License Agreement under which
 * it is provided by or on behalf of EMC.
 */

package com.emc.storageos.customconfigcontroller;

import java.util.List;


/**
 * A string manipulation function that returns the n to m characters in a string 
 *
 */
public class SubstringCustomConfigMethod extends CustomConfigMethod {
   
    public String invoke(String str, List<String> args) {
        int start = Integer.parseInt(args.get(0));
        int end = Integer.parseInt(args.get(1));
        if (start < 0) {
            start = 0;
        }
        if (end >= str.length()) {
            end = str.length();
        }
        return str.substring(start, end);
    }

}
