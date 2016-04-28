/* Copyright (c) 2015 EMC Corporation
 * All Rights Reserved
 *
 */
package com.emc.storageos.cinder.model;

import java.util.Map;

import javax.xml.bind.annotation.XmlRootElement;

@XmlRootElement(name = "snapshots")
public class CinderSnapshotMetadata {
    public Map<String, String> metadata;
}
