/*
 * Copyright 2015 EMC Corporation
 * All Rights Reserved
 */
/**
 *  Copyright (c) 2008-2011 EMC Corporation
 * All Rights Reserved
 *
 * This software contains the intellectual property of EMC Corporation
 * or is licensed to EMC Corporation from third parties.  Use of this
 * software and the intellectual property contained therein is expressly
 * limited to the terms and conditions of the License Agreement under which
 * it is provided by or on behalf of EMC.
 */

package com.emc.storageos.volumecontroller;


import java.io.Serializable;
import java.net.URI;
import java.util.List;

/**
 * Initiator param for adding jobs into ZK queues.
 */
public class AsyncTask implements Serializable {

    private static final long serialVersionUID = 1L;
    
    public Class _clazz;

    public String _opId;

    public URI _id;
    
    public String _namespace;
    
    //use this constructor if you want to discover specific namespaces [e.g. volume] within a Storage System
    public AsyncTask(Class clazz, URI id, String opId, String namespace) {
        _clazz = clazz;
        _id = id;
        _opId = opId;
        _namespace = namespace;
    }
    
    //use this constructor for all purposes other than the above.
    public AsyncTask(Class clazz, URI id, String opId) {
        _clazz = clazz;
        _id = id;
        _opId = opId;
       
    }
}
