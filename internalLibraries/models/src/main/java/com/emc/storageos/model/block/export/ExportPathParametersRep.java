/*
 * Copyright 2015 EMC Corporation
 * All Rights Reserved
 */

package com.emc.storageos.model.block.export;

import java.util.ArrayList;
import java.util.List;
import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlElementWrapper;
import javax.xml.bind.annotation.XmlRootElement;


@XmlRootElement(name = "path_param")
public class ExportPathParametersRep extends ExportPathParameters {
    List<String> blockObjects;

    @XmlElementWrapper(name = "block_objects", required = false)
    @XmlElement(name = "block_object")
    public List<String> getBlockObjects() {
        if (blockObjects == null) {
            blockObjects = new ArrayList<String>();
        }
        return blockObjects;
    }

    public void setBlockObjects(List<String> blockObjects) {
        this.blockObjects = blockObjects;
    }

}
