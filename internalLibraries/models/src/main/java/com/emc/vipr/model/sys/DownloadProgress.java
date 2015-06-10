/*
 * Copyright 2015 EMC Corporation
 * All Rights Reserved
 */
/**
 * Copyright (c) 2014 EMC Corporation
 * All Rights Reserved
 *
 * This software contains the intellectual property of EMC Corporation
 * or is licensed to EMC Corporation from third parties.  Use of this
 * software and the intellectual property contained therein is expressly
 * limited to the terms and conditions of the License Agreement under which
 * it is provided by or on behalf of EMC.
 */

package com.emc.vipr.model.sys;

import java.util.Map;
import java.util.TreeMap;

import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlElementWrapper;
import javax.xml.bind.annotation.XmlRootElement;

/**
 * DownloadProgress is a class used by REST API to represent the image downloading progress of each node in the cluster
 */
@XmlRootElement(name = "image_download_progress")
public class DownloadProgress {
    Map<String, NodeProgress> progress;
    long imageSize;
    public DownloadProgress(){
        progress = new TreeMap<String, NodeProgress>();
    }
    @XmlElement (name = "imageSize")
    public long getImageSize() {
        return imageSize;
    }

    public void setImageSize(long size) {
        this.imageSize = size;
    }
    
    @XmlElementWrapper(name = "progress")
    public Map<String, NodeProgress> getProgress() {
        return progress;
    }

    public void setProgress(Map<String, NodeProgress> progress) {
        this.progress = progress;
    }
    
    public void addNodeProgress(String nodeId, NodeProgress nodeProgress){
        progress.put(nodeId, nodeProgress);
    }
}
