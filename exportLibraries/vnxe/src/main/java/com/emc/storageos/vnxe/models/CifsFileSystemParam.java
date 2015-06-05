/**
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

package com.emc.storageos.vnxe.models;

import javax.xml.bind.annotation.XmlRootElement;

import org.codehaus.jackson.map.annotate.JsonSerialize;

@XmlRootElement
@JsonSerialize(include=JsonSerialize.Inclusion.NON_NULL)
public class CifsFileSystemParam extends ParamBase {
    private boolean isCIFSSyncWritesEnabled;
    private boolean isCIFSOpLocksEnabled;
    private boolean isCIFSNotifyOnWriteEnabled;
    private boolean isCIFSNotifyOnAccessEnabled;
    private int cifsNotifyOnChangeDirDepth;
   
    public boolean getIsCIFSSyncWritesEnabled() {
        return isCIFSSyncWritesEnabled;
    }
    public void setIsCIFSSyncWritesEnabled(boolean isCIFSSyncWritesEnabled) {
        this.isCIFSSyncWritesEnabled = isCIFSSyncWritesEnabled;
    }
    public boolean getIsCIFSOpLocksEnabled() {
        return isCIFSOpLocksEnabled;
    }
    public void setIsCIFSOpLocksEnabled(boolean isCIFSOpLocksEnabled) {
        this.isCIFSOpLocksEnabled = isCIFSOpLocksEnabled;
    }
    public boolean getIsCIFSNotifyOnWriteEnabled() {
        return isCIFSNotifyOnWriteEnabled;
    }
    public void setIsCIFSNotifyOnWriteEnabled(boolean isCIFSNotifyOnWriteEnabled) {
        this.isCIFSNotifyOnWriteEnabled = isCIFSNotifyOnWriteEnabled;
    }
    public boolean getIsCIFSNotifyOnAccessEnabled() {
        return isCIFSNotifyOnAccessEnabled;
    }
    public void setIsCIFSNotifyOnAccessEnabled(boolean isCIFSNotifyOnAccessEnabled) {
        this.isCIFSNotifyOnAccessEnabled = isCIFSNotifyOnAccessEnabled;
    }
    public int getCifsNotifyOnChangeDirDepth() {
        return cifsNotifyOnChangeDirDepth;
    }
    public void setCifsNotifyOnChangeDirDepth(int cifsNotifyOnChangeDirDepth) {
        this.cifsNotifyOnChangeDirDepth = cifsNotifyOnChangeDirDepth;
    }
    

}
