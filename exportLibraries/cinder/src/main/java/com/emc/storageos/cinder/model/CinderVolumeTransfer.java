/* Copyright (c) 2015 EMC Corporation
 * All Rights Reserved
 *
 */
package com.emc.storageos.cinder.model;

import javax.xml.bind.annotation.XmlRootElement;

import org.codehaus.jackson.map.annotate.JsonRootName;

@JsonRootName(value="transfer")
@XmlRootElement(name="transfer")
public class CinderVolumeTransfer {
//ToDo addvolume transfer attributes
}