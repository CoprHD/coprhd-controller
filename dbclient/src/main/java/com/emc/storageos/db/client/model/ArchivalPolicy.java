package com.emc.storageos.db.client.model;

import com.emc.storageos.model.valid.EnumType;
import java.net.URI;
import java.util.Set;
import java.util.concurrent.TimeUnit;


@Cf("ArchivalPolicy")
public class ArchivalPolicy extends DiscoveredDataObject {

    // indicates the numerical value of the unattached period for a volume that the policy will use to check for archival
    private Long        unattachedPeriodValue;
    // indicates the unit value of the unattached period for a volume that the policy will use to check for archival
    private TimeUnit    unattachedPeriodUnit;
    // indicates the minimum percentage/ratio of storage space that a volume must occupy for it to be archived
    private Integer     minSizeAllowed;
    // indicates the maximum percentage/ratio of storage space that a volume must occupy for it to be archived
    private Integer     maxSizeAllowed;

    protected String    policyName;

    protected String    policyId;

/*    public ArchivalPolicy(){
        
        unattachedPeriodValue   = 0;
        unattachedPeriodUnit    = TimeUnit.SECONDS;
        minSizeAllowed          = 0;
        maxSizeAllowed          = 0;
    }
    
    public void setArchivalPolicy(long id, String name, long val11, TimeUnit val12, Integer val2, Integer val3){
        
        policyId                    = id;
        policyName                  = name;
        unattachedPeriodValue       = val11;
        unattachedPeriodUnit        = val12;
        minSizeAllowed              = val2;
        maxSizeAllowed              = val3;
    }
*/    
    public void setPolicyName(String name){
        
        this.policyName = name;
        setChanged("policyName");
  }

    public void setPolicyId(String id){
        
        this.policyId = id;
        setChanged("policyId");
    }

    public void setUnattachedPeriodValue(Long val){

        unattachedPeriodValue   = val;
        setChanged("unattachedPeriodValue");

    }
    
    public void setUnattachedPeriodUnit(TimeUnit units){

        unattachedPeriodUnit    = units;
        setChanged("unattachedPeriodUnit");
    }


    public void setMinSizeAllowed(Integer val){
        
        minSizeAllowed     = val;
        setChanged("minSizeAllowed");
    }

    public void setMaxSizeAllowed(Integer val){
        
        maxSizeAllowed     = val;
        setChanged("maxSizeAllowed");
    }
    
    @Name("policyName")
    public String getPolicyName(){
        
        return policyName;
    }
    
    @Name("policyId")
    public String getPolicyId(){
        
        return policyId;
    }

    @Name("unattachedPeriodvalue")
    public Long getUnattachedPeriodValue(){
        
        return unattachedPeriodValue;
    }
    
    @Name("unattachedPeriodUnit")
    public TimeUnit getUnattachedPeriodUnit(){
        
        return unattachedPeriodUnit;
    }
    
    @Name("minSizeAllowed")
    public Integer getMinSizeAllowed(){
        
        return minSizeAllowed;
    }
    
    @Name("maxSizeAllowed")
    public Integer getMaxSizeAllowed(){
        
        return maxSizeAllowed;
    }
    
}