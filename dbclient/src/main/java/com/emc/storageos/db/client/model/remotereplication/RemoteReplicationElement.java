/*
 * Copyright (c) 2016 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.db.client.model.remotereplication;


import com.emc.storageos.db.client.model.Cf;
import com.emc.storageos.db.client.model.DataObject;
import com.emc.storageos.db.client.model.Name;

import java.net.URI;

/**
 * Wrapper for remotely replicated data object.
 */
@Cf("RemoteReplicationElement")
public class RemoteReplicationElement extends DataObject {

    public enum ElementType {
        VOLUME,
        FILE_SYSTEM
    }

    // uri of backing data object
    private URI dataObjectUri;

    // Element type
    private ElementType elementType;


    @Name("dataObjectUri")
    public URI getDataObjectUri() {
        return dataObjectUri;
    }

    public void setDataObjectUri(URI dataObjectUri) {
        this.dataObjectUri = dataObjectUri;
        setChanged("dataObjectUri");
    }

    @Name("elementType")
    public ElementType getElementType() {
        return elementType;
    }

    public void setElementType(ElementType elementType) {
        this.elementType = elementType;
        setChanged("elementType");
    }
}
