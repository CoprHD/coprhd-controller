package com.emc.storageos.cinder.model;

import java.util.ArrayList;
import java.util.List;

import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlRootElement;
import javax.xml.bind.annotation.XmlElementRef;
import javax.xml.bind.annotation.XmlTransient;

import org.codehaus.jackson.map.annotate.JsonRootName;

import com.emc.storageos.model.NamedRelatedResourceRep;

@JsonRootName(value="snapshots")
@XmlRootElement(name="snapshots")
public class CinderSnapshotListRestResp {
    private List<CinderSnapshot> snapshots;

    /**
     * List of snapshots that make up this entry.  Used primarily to report to cinder.  
     */

    @XmlElement(name="snapshot")
    public List<CinderSnapshot> getSnapshots() {
        if (snapshots == null) {
        	snapshots = new ArrayList<CinderSnapshot>();
        }
        return snapshots;
    }

    public void setSnapshots(List<CinderSnapshot> snapshots) {
        this.snapshots = snapshots;
    }
       
}

