/*
 * Copyright 2015 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.model.host.vcenter;

import javax.xml.bind.annotation.XmlRootElement;

/**
 * Request POST parameter for vcenter data center creation.
 */
@XmlRootElement(name = "vcenter_data_center_create")
public class VcenterDataCenterCreate extends VcenterDataCenterParam {
}
