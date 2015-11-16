package com.emc.storageos.db.client.model;

import com.emc.storageos.model.valid.EnumType;
import java.net.URI;
import java.util.Set;
import java.util.concurrent.TimeUnit;


@Cf("BackupPolicy")
public class BackupPolicy extends DiscoveredDataObject {

    // indicates the unit value of the period set for incremental backups
    private TimeUnit   incrementalPeriodUnit;
    // indicates the numerical value of the period set for incremental backups
    private Long       incrementalPeriodValue;
    // indicates the count of incremental backups after which full backup will occur
    private Integer    countForFullBackup;

    protected String    policyName;

    protected String    policyId;

/*    
    public BackupPolicy(){

        incrementalPeriodValue  = 0;
        incrementalPeriodUnit   = TimeUnit.SECONDS;
        countForFullBackup      = 0;
    }
    
    public void setBackupPolicy(long id, String name, long val1, TimeUnit val2, int val3){
        
        policyId                    = id;
        policyName                  = name;
        incrementalPeriodValue      = val1;
        incrementalPeriodUnit       = val2;
        countForFullBackup          = val3;
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

    public void setIncrementalPeriodUnit(TimeUnit incrementalPeriodUnit){
        
        this.incrementalPeriodUnit = incrementalPeriodUnit;
        setChanged("incrementalPeriodUnit");
    }

    public void setIncrementalPeriodValue(Long incrementalPeriodValue){
        
        this.incrementalPeriodValue = incrementalPeriodValue;
        setChanged("incrementalPeriodValue");
    }

    public void setCountForFullBackup(Integer countForFullBackup){

        this.countForFullBackup = countForFullBackup;
        setChanged("countForFullBackup");
    }
    
    @Name("policyName")
    public String getPolicyName(){
        
        return policyName;
    }
    
    @Name("policyId")
    public String getPolicyId(){
        
        return policyId;
    }

    @Name("incrementalPeriodUnit")
    public TimeUnit getIncrementalPeriodUnit(){
        
       return incrementalPeriodUnit; 
    }

    @Name("incrementalPeriodValue")
    public Long getIncrementalPeriodvalue(){
        
       return incrementalPeriodValue; 
    }

    @Name("countForFullBackup")
    public Integer getCountForFullBackup(){
        
        return countForFullBackup; 
     }

}
