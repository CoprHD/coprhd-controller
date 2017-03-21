/**
 * Copyright (c) 2017 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.model.collectdata;

import java.util.Arrays;
import java.util.List;
import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlElementWrapper;
import javax.xml.bind.annotation.XmlRootElement;

@XmlRootElement(name = "scaleio_system")
@XmlAccessorType(XmlAccessType.PROPERTY)
public class ScaleIOSystemDataRestRep {
    private List<ScaleIOSDCDataRestRep> sdcList;
    private List<ScaleIOSDSDataRestRep> sdsList;
    private String mdmMode;
    private String[] primaryMdmActorIpList;
    private String[] secondaryMdmActorIpList;
    private String[] tiebreakerMdmIpList;
    private String systemVersionName;
    private String mdmClusterState;
    private String id;
    private String name;
    private String installId;
    private MdmClusterDataRestRep mdmCluster;

    public MdmClusterDataRestRep getMdmCluster() {
        return mdmCluster;
    }

    public void setMdmCluster(MdmClusterDataRestRep mdmCluster) {
        this.mdmCluster = mdmCluster;
    }

    public String getMdmMode() {
        return mdmMode;
    }

    public void setMdmMode(String mdmMode) {
        this.mdmMode = mdmMode;
    }

    public String[] getPrimaryMdmActorIpList() {
        if(null == primaryMdmActorIpList){
            return null;
        }
        return Arrays.copyOf(primaryMdmActorIpList,primaryMdmActorIpList.length);
    }

    public void setPrimaryMdmActorIpList(String[] primaryMdmActorIpList) {
        if(null == primaryMdmActorIpList){
            return;
        }
        this.primaryMdmActorIpList = Arrays.copyOf(primaryMdmActorIpList,primaryMdmActorIpList.length);
    }

    public String[] getSecondaryMdmActorIpList() {
        if(null == secondaryMdmActorIpList){
            return null;
        }
        return Arrays.copyOf(secondaryMdmActorIpList,secondaryMdmActorIpList.length);
    }

    public void setSecondaryMdmActorIpList(String[] secondaryMdmActorIpList) {
        if(null == secondaryMdmActorIpList){
            return;
        }
        this.secondaryMdmActorIpList = Arrays.copyOf(secondaryMdmActorIpList,secondaryMdmActorIpList.length);
    }

    public String[] getTiebreakerMdmIpList() {
        if(null == tiebreakerMdmIpList){
            return null;
        }
        return Arrays.copyOf(tiebreakerMdmIpList,tiebreakerMdmIpList.length);
    }

    public void setTiebreakerMdmIpList(String[] tiebreakerMdmIpList) {
        if(null == tiebreakerMdmIpList){
            return;
        }
        this.tiebreakerMdmIpList = Arrays.copyOf(tiebreakerMdmIpList,tiebreakerMdmIpList.length);
    }

    public String getSystemVersionName() {
        return systemVersionName;
    }

    public void setSystemVersionName(String systemVersionName) {
        this.systemVersionName = systemVersionName;
    }

    public String getMdmClusterState() {
        return mdmClusterState;
    }

    public void setMdmClusterState(String mdmClusterState) {
        this.mdmClusterState = mdmClusterState;
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getInstallId() {
        return installId;
    }

    public void setInstallId(String installId) {
        this.installId = installId;
    }

    public String getVersion() {
        return systemVersionName;
    }

    @XmlElementWrapper(name = "sdsList")
    @XmlElement(name = "sds")
    public List<ScaleIOSDSDataRestRep> getSdsList() {
        return sdsList;
    }

    public void setSdsList(List<ScaleIOSDSDataRestRep> sdsList) {
        this.sdsList = sdsList;
    }

    @XmlElementWrapper(name = "sdcList")
    @XmlElement(name = "sdc")
    public List<ScaleIOSDCDataRestRep> getSdcList() {
        return sdcList;
    }

    public void setSdcList(List<ScaleIOSDCDataRestRep> sdcList) {
        this.sdcList = sdcList;
    }


}
