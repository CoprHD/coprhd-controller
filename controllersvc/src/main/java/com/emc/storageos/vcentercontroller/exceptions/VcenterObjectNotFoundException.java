/**
* Copyright 2015 EMC Corporation
* All Rights Reserved
 */
package com.emc.storageos.vcentercontroller.exceptions;

/**
 * Created with IntelliJ IDEA.
 * User: alaplante
 * Date: 10/22/14
 * Time: 11:05 AM
 * To change this template use File | Settings | File Templates.
 */
public class VcenterObjectNotFoundException extends VcenterException {
    public VcenterObjectNotFoundException(String message) {
        super(message);
    }
}
