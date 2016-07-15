/*
 * Copyright (c) 2015 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.model.host;

import javax.xml.bind.annotation.XmlRootElement;

/**
 * Request PUT parameter for host update operation.
 */
@XmlRootElement(name = "virtual_machine_update")
public class VirtualMachineUpdateParam extends VirtualMachineParam {
}
