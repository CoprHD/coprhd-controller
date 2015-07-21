/*
 * Copyright (c) 2015 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.model;

import javax.xml.bind.annotation.XmlRootElement;

/**
 * Base class of bulk resource representations.
 * 
 * An actual resource type extends this class and have getter with
 * proper XML element name on elements.
 * As an example, refer to the VolumeBulkRep defined in BlockService.
 */
@XmlRootElement
public class BulkRestRep {
}
