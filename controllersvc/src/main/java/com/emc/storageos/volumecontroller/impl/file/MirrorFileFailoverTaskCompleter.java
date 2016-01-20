/*
 * Copyright (c) 2015 EMC Corporation
 * All Rights Reserved
 */

package com.emc.storageos.volumecontroller.impl.file;

import java.net.URI;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class MirrorFileFailoverTaskCompleter extends MirrorFileTaskCompleter {
    private static final Logger _log = LoggerFactory.getLogger(MirrorFileFailoverTaskCompleter.class);
    public MirrorFileFailoverTaskCompleter(Class clazz, List<URI> ids, String opId) {
        super(clazz, ids, opId);
        // TODO Auto-generated constructor stub
    }

    public MirrorFileFailoverTaskCompleter(Class clazz, URI id, String opId) {
        super(clazz, id, opId);
        // TODO Auto-generated constructor stub
    }

    public MirrorFileFailoverTaskCompleter(URI sourceURI, URI targetURI, String opId) {
        super(sourceURI, targetURI, opId);
      
    }

}
