/**
 *  Copyright (c) 2008-2015 EMC Corporation
 * All Rights Reserved
 *
 * This software contains the intellectual property of EMC Corporation
 * or is licensed to EMC Corporation from third parties.  Use of this
 * software and the intellectual property contained therein is expressly
 * limited to the terms and conditions of the License Agreement under which
 * it is provided by or on behalf of EMC.
 */

package com.emc.storageos.db.client.model;

import java.net.URI;

import com.emc.storageos.db.client.model.DbKeyspace.Keyspaces;



/**
 * QuotaOfCinder data object
 */
@Cf("QuotaOfCinder")
@DbKeyspace(Keyspaces.LOCAL)
public class QuotaOfCinder extends DataObject {
    /**
	 * 
	 */
	private URI project;
    private URI vpool;
    private Long   snapLimit;
    private Long   totalGB;
    private Long   volumeLimit;
    //private Boolean _quotaEnabled;

    @Name("volumeLimit")
    public Long getVolumesLimit() {
    	return (null == volumeLimit) ? 0L : volumeLimit;
	}

	public void setVolumesLimit(Long _volumes) {
		this.volumeLimit = _volumes;
        setChanged("volumeLimit");
	}

	@RelationIndex(cf = "RelationIndex", type = Project.class)
    @Name("project")
    public URI getProject() {
        return project;
    }

    public void setProject(URI project) {
    	this.project = project;
        setChanged("project");
    }

    @RelationIndex(cf = "RelationIndex", type = VirtualPool.class)
    @Name("vpool")
    public URI getVpool() {
        return vpool;
    }

    
    public void setVpool(URI vpool) {
    	this.vpool = vpool;
        setChanged("vpool");
    }
        
    @Name("totalGB")
    public Long getTotalQuota(){
        return (null == totalGB) ? 0L : totalGB;
    }

    public void setTotalQuota(Long quota) {
    	totalGB = quota;
        setChanged("totalGB");
    }
    
    @Name("snapLimit")
    public Long getSnapshotsLimit(){
        return (null == snapLimit) ? 0L : snapLimit;
    }

    public void setSnapshotsLimit(Long snapsLimit) {
    	snapLimit = snapsLimit;
        setChanged("snapLimit");
    }
    
    public String toString(){
    	StringBuffer buf = new StringBuffer();
    	buf.append("Id:"+this.getId().toString()+"\n");
    	buf.append("Project:"+this.getProject().toString()+"\n");
    	buf.append("VPool:"+this.getVpool()+ "\n");
    	buf.append("snapshots:"+ this.getSnapshotsLimit().toString() +"\n");
    	buf.append("totalGB:"+this.getTotalQuota().toString()+"\n");
    	buf.append("volumes:"+this.getVolumesLimit().toString()+ "\n");    	
    	return buf.toString();
    }
    
}
