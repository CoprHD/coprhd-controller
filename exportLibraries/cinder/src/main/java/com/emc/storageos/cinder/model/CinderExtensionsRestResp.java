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

@JsonRootName(value="extensions")
@XmlRootElement(name="extensions")
public class CinderExtensionsRestResp {
    private List<CinderExtension> extensions;

    /**
     * List of snapshots that make up this entry.  Used primarily to report to cinder.  
     */

    //
    @XmlElement(name="extension")
    public List<CinderExtension> getExtensions() {
        if (extensions== null) {
        	extensions = new ArrayList<CinderExtension>();
        }
        return extensions;
    }

    public void setExtensions(List<CinderExtension> lstextensions) {
        this.extensions= lstextensions;
    }
       
}

