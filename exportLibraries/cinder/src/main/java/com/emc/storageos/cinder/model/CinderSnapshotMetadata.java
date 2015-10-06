package com.emc.storageos.cinder.model;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlRootElement;
import javax.xml.bind.annotation.XmlTransient;

import com.emc.storageos.cinder.model.SnapshotCreateRequest.Snapshot;
import com.emc.storageos.model.NamedRelatedResourceRep;

@XmlRootElement(name = "snapshots")
public class CinderSnapshotMetadata {
	public Map<String, String> metadata;
}

