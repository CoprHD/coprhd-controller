/**
* Copyright 2015 EMC Corporation
* All Rights Reserved
 */
/**
 *  Copyright (c) 2008-2015 EMC Corporation
 * All Rights Reserved
 *
 * This software contains the intellectual property of EMC Corporation
 * or is licensed to EMC Corporation from third parties.  Use of this
 * software and the intellectual property contained therein is expressly
 * limited to the terms and conditions of the License Agreement under which
 * it is provided by or on behalf of EMC.
 */

package com.emc.storageos.db.client.util;


public class ExceptionUtils {
    
    public static final String EMPTY_STR = "";
    public static final String HIPHEN_STR = "-";

    /**
     * get Message of Exception.
     * 
     * @param ex
     *            Exception.
     * @return String.
     */
    public static String getExceptionMessage(Exception ex) {
        String cause = ex.getCause() != null ? ex.getCause().toString() : EMPTY_STR;
        String message = ex.getLocalizedMessage() != null ? ex.getLocalizedMessage() : EMPTY_STR;
        String error = EMPTY_STR;
        if (!cause.isEmpty()) {
            error = cause;
        }
        if (!message.isEmpty()) {
            error = error + HIPHEN_STR + message;
        }
        return error;
    }
}
