/**
* Copyright 2012-2015 iWave Software LLC
* All Rights Reserved
 */
package com.emc.sa.service.aix;

import static com.emc.sa.service.ServiceParams.DO_FORMAT;
import static com.emc.sa.service.ServiceParams.FILE_SYSTEM_TYPE;
import static com.emc.sa.service.ServiceParams.MOUNT_POINT;
import static com.emc.sa.service.vipr.ViPRExecutionUtils.logInfo;

import java.util.List;

import com.emc.aix.AixSystem;
import com.emc.sa.engine.ExecutionUtils;
import com.emc.sa.engine.bind.BindingUtils;
import com.emc.sa.engine.bind.Param;
import com.emc.storageos.db.client.model.Initiator;
import com.emc.storageos.model.block.BlockObjectRestRep;

public final class MountBlockVolumeHelper {
    
    private String hostname;
    
    @Param(MOUNT_POINT)
    protected String mountPoint;
    
    @Param(FILE_SYSTEM_TYPE)
    protected String fsType;
    
    @Param(value=DO_FORMAT, required=false) 
    protected boolean doFormat = true;
    
    /** The flag which indicates whether we're using EMC PowerPath for multipathing or not.*/
    protected boolean usePowerPath;

    private AixSystem aix;
    
    private AixSupport aixSupport;
    
    private MountBlockVolumeHelper(AixSupport aixSupport) {
        this.aixSupport = aixSupport;
        this.aix = aixSupport.getTargetSystem();
        this.hostname = aixSupport.getHostName();
    }
    
    public static MountBlockVolumeHelper create(final AixSystem aix, List<Initiator> ports) {
        AixSupport aixSupport = new AixSupport(aix);
        MountBlockVolumeHelper mountBlockVolumeHelper = new MountBlockVolumeHelper(aixSupport);
        BindingUtils.bind(mountBlockVolumeHelper, ExecutionUtils.currentContext().getParameters());
        return mountBlockVolumeHelper;
    }
    
    public void precheck() {
        aixSupport.verifyMountPoint(mountPoint);
        usePowerPath = aixSupport.checkForPowerPath();
        if (usePowerPath) {
            logInfo("aix.mount.block.powerpath.detected");
        }else{
            logInfo("aix.mount.block.powerpath.not.detected");
        }
    }
    
    public void mount(final BlockObjectRestRep volume){
        
        aixSupport.rescanDevices();
        
        if (usePowerPath) {
            logInfo("UpdatePowerPathEntries.title");
            aixSupport.updatePowerPathEntries();
        }
        
        String hdisk = aixSupport.findHDisk(volume, usePowerPath);
        
        if (doFormat) {
            logInfo("aix.mount.block.create.filesystem", hostname, hdisk);
            aix.makeFilesystem(hdisk, fsType);
        }
        
        logInfo("aix.mount.block.mount.device", hostname, hdisk, mountPoint, fsType, volume.getWwn());
        aixSupport.createDirectory(mountPoint);
        aixSupport.addToFilesystemsConfig(hdisk, mountPoint, fsType);
        aixSupport.mount(mountPoint);
        
        aixSupport.setVolumeMountPointTag(volume, mountPoint);   
    }

}
