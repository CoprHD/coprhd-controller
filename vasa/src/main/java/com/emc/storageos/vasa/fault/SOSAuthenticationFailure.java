/*
 * Copyright 2015 EMC Corporation
 * All Rights Reserved
 */
/* 
 Copyright (c) 2012 EMC Corporation
 All Rights Reserved

 This software contains the intellectual property of EMC Corporation
 or is licensed to EMC Corporation from third parties.  Use of this
 software and the intellectual property contained therein is expressly
 imited to the terms and conditions of the License Agreement under which
 it is provided by or on behalf of EMC.
 */
package com.emc.storageos.vasa.fault;

public class SOSAuthenticationFailure extends Exception {

    private static final long serialVersionUID = 1L;

    public SOSAuthenticationFailure() {
        super();
    }

    public SOSAuthenticationFailure(String arg0, Throwable arg1) {
        super(arg0, arg1);
    }

    public SOSAuthenticationFailure(String arg0) {
        super(arg0);
    }

    public SOSAuthenticationFailure(Throwable arg0) {
        super(arg0);
    }

}
