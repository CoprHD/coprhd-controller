package com.emc.storageos.hp3par.impl;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.commons.lang.mutable.MutableBoolean;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.emc.storageos.hp3par.command.FcPath;
import com.emc.storageos.hp3par.command.HostCommandResult;
import com.emc.storageos.hp3par.command.HostMember;
import com.emc.storageos.hp3par.command.HostSetDetailsCommandResult;
import com.emc.storageos.hp3par.command.ISCSIPath;
import com.emc.storageos.hp3par.command.Position;
import com.emc.storageos.hp3par.command.VirtualLun;
import com.emc.storageos.hp3par.command.VirtualLunsList;
import com.emc.storageos.hp3par.command.VlunResult;
import com.emc.storageos.hp3par.utils.CompleteError;
import com.emc.storageos.hp3par.utils.HP3PARConstants;
import com.emc.storageos.hp3par.utils.HP3PARUtil;
import com.emc.storageos.hp3par.utils.SanUtils;
import com.emc.storageos.storagedriver.DriverTask;
import com.emc.storageos.storagedriver.Registry;
import com.emc.storageos.storagedriver.model.Initiator;
import com.emc.storageos.storagedriver.model.StoragePort;
import com.emc.storageos.storagedriver.model.StorageVolume;
import com.emc.storageos.storagedriver.model.Initiator.HostOsType;
import com.emc.storageos.storagedriver.model.Initiator.Type;
import com.emc.storageos.storagedriver.model.StoragePool.Protocols;
import com.emc.storageos.storagedriver.storagecapabilities.StorageCapabilities;

/**
 * 
 * Implements export/unexport operations
 *
 */
public class HP3PARExpUnexpHelper {

    private static final Logger _log = LoggerFactory.getLogger(HP3PARExpUnexpHelper.class);
    private HP3PARUtil hp3parUtil;
    
    
    /*********USE CASES**********
    
    EXCLUSIVE EXPORT: Will include port number of host
    
    1 Export volume to existing host  
    2 Export volume to non-existing host  
    3 Add initiator to existing host 
    4 Remove initiator from host 
    5 Unexport volume 
    
    A 1-5 can be done with single/multiple volumes,initiators as applicable
    B Does not depend on host name
    C Adding an initiator in matched-set will not do anything further. 
      All volumes have to be exported to new initiator explicitly. 
      In host-sees 3PAR will automatically export the volumes to newly added initiator.
    -------------------------------------------
    SHARED EXPORT: Will not include port number, exported to all ports, the cluster can see
    
    1 Export volume to existing cluster
    2 Export volume to non-existing cluster 
    3 Add initiator to existing host in cluster 
    4 Remove initiator from host in cluster
    5 Unexport volume from cluster
    6 Export a private volume to a host in a cluster 
    7 Unexport a private volume from a host in a cluster
    8 Add a host to cluster 
    9 Remove a host from a cluster
    10 Add a host having private export 
    11 Remove a host having private export
    12 Move a host from one cluster to another
    
    A 1-12 can be done with single/multiple volumes,initiators,hosts as applicable
    B Cluster name in ViPR and 3PAR has to be identical with case
    C Adding a new host to host-set will automatically export all volumes to the new host(initial export must have been host-set)
   */

  /*
   * All volumes in the list will be exported to all initiators using recommended ports. If a volume can not be exported to 'n' 
   * initiators the same will be tried with available ports  
   */

    public DriverTask exportVolumesToInitiators(List<Initiator> initiators, List<StorageVolume> volumes,
            Map<String, String> volumeToHLUMap, List<StoragePort> recommendedPorts, List<StoragePort> availablePorts,
            StorageCapabilities capabilities, MutableBoolean usedRecommendedPorts, List<StoragePort> selectedPorts,
            DriverTask task, Registry driverRegistry) {
        
        _log.info("3PARDriver:exportVolumesToInitiators enter");

        String host = null;
        host = doHostProcessing(initiators, volumes, driverRegistry);
        if (host == null ) {
            task.setMessage("exportVolumesToInitiators error: Processing hosts, Unable to export");
            task.setStatus(DriverTask.TaskStatus.FAILED);
            return task;
        }

        /*
         Export will be done keeping volumes as the starting point
         */
        Integer totalExport = recommendedPorts.size();
        for (StorageVolume vol : volumes) {
            Integer currExport = 0;
            Integer hlu = Integer.parseInt(volumeToHLUMap.get(vol.getNativeId()));

            try {
                // volume could belong to different storage system; get specific api client;
                HP3PARApi hp3parApi = hp3parUtil.getHP3PARDeviceFromNativeId(vol.getStorageSystemId(), driverRegistry);

                /*
                 export for INDIVIDUAL HOST=exclusive 
                 Some code is repeated with cluster for simplicity
                 */
                if (!host.startsWith("set:")) {
                    // try with recommended ports
                    for (StoragePort port : recommendedPorts) {
                        // verify volume and port belong to same storage
                        if (!vol.getStorageSystemId().equalsIgnoreCase(port.getStorageSystemId())) {
                            continue;
                        }

                        String message = String.format(
                                "3PARDriver:exportVolumesToInitiators using recommendedPorts for "
                                        + "storage system %s, volume %s host %s hlu %s port %s",
                                        port.getStorageSystemId(), vol.getNativeId(), host, hlu.toString(), port.getNativeId());
                        _log.info(message);

                        VlunResult vlunRes = hp3parApi.createVlun(vol.getNativeId(), hlu, host, port.getNativeId());
                        if (vlunRes != null && vlunRes.getStatus()) {
                            currExport++;
                            usedRecommendedPorts.setValue(true);
                            // update hlu obtained as lun from 3apr & add the selected port if required
                            volumeToHLUMap.put(vol.getNativeId(), vlunRes.getAssignedLun());
                            if (!selectedPorts.contains(port)) {
                                selectedPorts.add(port);
                            }
                        } else {
                            task.setStatus(DriverTask.TaskStatus.PARTIALLY_FAILED);
                            _log.warn("3PARDriver: Could not export " + message);
                        }
                    } // for recommended ports

                    // now try with available ports
                    for (StoragePort port : availablePorts) {
                        if (currExport == totalExport) {
                            task.setStatus(DriverTask.TaskStatus.READY);
                            break;
                        }
                        // verify volume and port belong to same storage
                        if (!vol.getStorageSystemId().equalsIgnoreCase(port.getStorageSystemId())) {
                            continue;
                        }

                        String message = String.format(
                                "3PARDriver:exportVolumesToInitiators using availablePorts for "
                                        + "storage system %s, volume %s host %s hlu %s port %s",
                                        port.getStorageSystemId(), vol.getNativeId(), host, hlu.toString(), port.getNativeId());
                        _log.info(message);

                        VlunResult vlunRes = hp3parApi.createVlun(vol.getNativeId(), hlu, host, port.getNativeId());
                        if (vlunRes != null && vlunRes.getStatus()) {
                            currExport++;
                            usedRecommendedPorts.setValue(false);
                            // update hlu obtained as lun from 3apr & add the selected port if required
                            volumeToHLUMap.put(vol.getNativeId(), vlunRes.getAssignedLun());
                            if (!selectedPorts.contains(port)) {
                                selectedPorts.add(port);
                            }
                        } else {
                            task.setStatus(DriverTask.TaskStatus.PARTIALLY_FAILED);
                            _log.warn("3PARDriver: Could not export " + message);
                        }
                    } // for available ports
                } else {
                    /*
                      export for CLUSTER=shared 
                      Some code is repeated with cluster for simplicity
                      
                      Cluster export will be done as host-set in 3APR for entire cluster in one go.
                      Hence requests coming for rest of the individual host exports should gracefully exit
                     */

                    //TBD: lock to be used once issue is resolved
                    synchronized(this) {
                        /*
                          If this is the first request key gets created with export operation. 
                          other requests will gracefully exit. key will be removed in unexport.
                         */

                        String message = String.format(
                                "3PARDriver:exportVolumesToInitiators "
                                        + "storage system %s, volume %s Cluster %s hlu %s ",
                                        vol.getStorageSystemId(), vol.getNativeId(), host, hlu.toString());
                        _log.info(message);

                        String exportPath = vol.getStorageSystemId() + vol.getNativeId() + host;
                        Map<String, List<String>> attributes = new HashMap<>();
                        List<String> expValue = new ArrayList<>();
                        List<String> lunValue = new ArrayList<>();
                        boolean doExport = true;

                        attributes = driverRegistry.getDriverAttributesForKey(HP3PARConstants.DRIVER_NAME,
                                exportPath);

                        if (attributes != null) {
                            expValue = attributes.get("EXPORT_PATH");
                            if (expValue != null && expValue.get(0).compareTo(exportPath) == 0) {
                                doExport = false;
                                // Already exported, make hlu, port details; gracefully exit
                                lunValue = attributes.get(vol.getNativeId());
                                volumeToHLUMap.put(vol.getNativeId(), lunValue.get(0));

                                String hstArray = get3parHostname(initiators, vol.getStorageSystemId(), driverRegistry);
                                HostMember hostRes = hp3parApi.getHostDetails(hstArray);

                                // get storage array ports for this host ports
                                List<StoragePort> clusterStoragePorts = new ArrayList<>();
                                getClusterStoragePorts(hostRes, availablePorts, vol.getStorageSystemId(),
                                        clusterStoragePorts);

                                for (StoragePort sp : clusterStoragePorts) {
                                    // assign all these ports as selected ports
                                    if (!selectedPorts.contains(sp)) {
                                        selectedPorts.add(sp);
                                    }
                                }

                                // go thru all slectedports. 
                                // if anyone is not part of the recommendedPorts set usedRecommendedPorts to false
                                usedRecommendedPorts.setValue(true);

                                for (StoragePort sp : selectedPorts) {
                                    if (!recommendedPorts.contains(sp)) {
                                        usedRecommendedPorts.setValue(false);
                                        break;
                                    }
                                }

                                task.setStatus(DriverTask.TaskStatus.READY);
                                _log.info("3PARDriver: Already exported, exiting" + message);
                            }
                        }

                        if (doExport) {
                            /*
                             for cluster use host set method, We cannot specify port; 
                             determine the individual host ports used
                             */
                            VlunResult vlunRes = hp3parApi.createVlun(vol.getNativeId(), hlu, host, null);
                            if (vlunRes != null && vlunRes.getStatus()) {

                                // update hlu obtained as lun from 3apr & add the selected port if required
                                volumeToHLUMap.put(vol.getNativeId(), vlunRes.getAssignedLun());

                                String hstArray = get3parHostname(initiators, vol.getStorageSystemId(), driverRegistry);
                                HostMember hostRes = hp3parApi.getHostDetails(hstArray);

                                // get storage array ports for this host ports
                                List<StoragePort> clusterStoragePorts = new ArrayList<>();
                                getClusterStoragePorts(hostRes, availablePorts, vol.getStorageSystemId(),
                                        clusterStoragePorts);

                                for (StoragePort sp : clusterStoragePorts) {
                                    // assign all these ports as selected ports
                                    if (!selectedPorts.contains(sp)) {
                                        selectedPorts.add(sp);
                                    }
                                }

                                usedRecommendedPorts.setValue(true);

                                for (StoragePort sp : selectedPorts) {
                                    if (!recommendedPorts.contains(sp)) {
                                        usedRecommendedPorts.setValue(false);
                                        break;
                                    }
                                }

                                // Everything is successful, Set as exported in registry
                                attributes = new HashMap<>();
                                expValue = new ArrayList<>();
                                lunValue = new ArrayList<>();

                                expValue.add(exportPath);
                                attributes.put("EXPORT_PATH", expValue);
                                lunValue.add(vlunRes.getAssignedLun());
                                attributes.put(vol.getNativeId(), lunValue);

                                attributes.put(vol.getNativeId(), lunValue);
                                driverRegistry.setDriverAttributesForKey(HP3PARConstants.DRIVER_NAME, exportPath,
                                        attributes);

                                task.setStatus(DriverTask.TaskStatus.READY);

                            } else { // end createVlun
                                task.setStatus(DriverTask.TaskStatus.PARTIALLY_FAILED);
                                _log.warn("3PARDriver: Could not export " + message);
                            }
                        } // doExport

                    }
                    
                } // end cluster export

            } catch (Exception e) {
                String msg = String.format("3PARDriver: Unable to export few volumes, error: %s", e);
                _log.error(CompleteError.getStackTrace(e));
                _log.error(msg);
                task.setMessage(msg);
                task.setStatus(DriverTask.TaskStatus.PARTIALLY_FAILED);
                e.printStackTrace();
            }
        } // for each volume

        _log.info("3PARDriver:exportVolumesToInitiators leave");
        return task;
    }

    public DriverTask unexportVolumesFromInitiators(List<Initiator> initiators, List<StorageVolume> volumes, 
            DriverTask task, Registry driverRegistry) {

        _log.info("3PARDriver:unexportVolumesFromInitiators enter");

        String host = null;
        Boolean fullSuccess = true;

        if (initiators.isEmpty() || volumes.isEmpty()) {
            String msg = "3PARDriver:unexportVolumesFromInitiators error blank initiator and/or volumes";
            _log.error(msg);
            task.setMessage(msg);
            task.setStatus(DriverTask.TaskStatus.FAILED);
            return task;
        }

        // unexport each volume
        for (StorageVolume volume : volumes) {
            try {
                // get Api client for volume specific array
                HP3PARApi hp3parApi = hp3parUtil.getHP3PARDeviceFromNativeId(volume.getStorageSystemId(),
                        driverRegistry);
                // TBD: Efficiency; use query method
                VirtualLunsList vlunRes = hp3parApi.getAllVlunDetails();

                for (Initiator init : initiators) {

                    // TBD: Efficiency; Initiator & host name to be stored in hash-map
                    ArrayList<Initiator> initList = new ArrayList<>();
                    initList.add(init);
                    host = get3parHostname(initList, volume.getStorageSystemId(), driverRegistry);
                    if (host == null) {
                        fullSuccess = false;
                        String message = String.format(
                                "3PARDriver:unexportVolumesFromInitiators for " + "storage system %s, volume %s initiator %s",
                                volume.getStorageSystemId(), volume.getNativeId(), init.getPort());
                        _log.warn(message);
                        task.setMessage(message);
                        task.setStatus(DriverTask.TaskStatus.PARTIALLY_FAILED);
                        continue;
                    }

                    if (init.getInitiatorType().equals(Type.Host)) {
                        // get vlun and port details on this export
                        Integer lun = -1;
                        Position pos = null;
                        String hostPortId = init.getPort();//host port
                        hostPortId = hostPortId.replace(":", "");

                        for (VirtualLun vLun:vlunRes.getMembers()) {

                            if (volume.getNativeId().compareTo(vLun.getVolumeName()) != 0 || (!vLun.isActive())
                                    || hostPortId.compareToIgnoreCase(vLun.getRemoteName()) != 0) {
                                continue;
                            }

                            lun = vLun.getLun();
                            pos = vLun.getPortPos();

                            String message = String.format(
                                    "3PARDriver:unexportVolumesFromInitiators for "
                                            + "storage system %s, volume %s host %s hlu %s port %s",
                                            volume.getStorageSystemId(), volume.getNativeId(), host, lun.toString(),
                                            pos.toString());
                            _log.info(message);

                            String posStr = String.format("%s:%s:%s", pos.getNode(), pos.getSlot(), pos.getCardPort());
                            hp3parApi.deleteVlun(volume.getNativeId(), lun.toString(), host, posStr);
                        }
                    } else if (init.getInitiatorType().equals(Type.Cluster)) {

                        // cluster unexport
                        String clusterName = "set:" + initiators.get(0).getClusterName();
                        String exportPath = volume.getStorageSystemId() + volume.getNativeId() + clusterName;
                        Map<String, List<String>> attributes = new HashMap<>();
                        List<String> expValue = new ArrayList<>();
                        List<String> lunValue = new ArrayList<>();
                        boolean regPresent = false;

                        String message = String.format(
                                "3PARDriver:unexportVolumesFromInitiators for " + "storage system %s, volume %s Cluster %s",
                                volume.getStorageSystemId(), volume.getNativeId(), clusterName);

                        attributes = driverRegistry.getDriverAttributesForKey(HP3PARConstants.DRIVER_NAME, exportPath);

                        if (attributes != null) {
                            expValue = attributes.get("EXPORT_PATH");
                            if (expValue != null && expValue.get(0).compareTo(exportPath) == 0) {
                                lunValue = attributes.get(volume.getNativeId());
                                regPresent = true;

                                _log.info(message);
                                /*
                                 * below operations are assumed to autonomic
                                 */
                                hp3parApi.deleteVlun(volume.getNativeId(), lunValue.get(0), clusterName, null);
                                driverRegistry.clearDriverAttributesForKey(HP3PARConstants.DRIVER_NAME, exportPath);
                            }
                        }

                        if (!regPresent) {
                            // gracefully exit, nothing to be done
                            _log.info("3PARDriver: Already unexported, exiting gracefully" + message);
                        }
                    } // if cluster
                } // for each initiator

            } catch (Exception e) {
                String msg = String.format("3PARDriver: Unable to unexport few volumes, error: %s", e);
                _log.error(msg);
                _log.error(CompleteError.getStackTrace(e));
                task.setMessage(msg);
                task.setStatus(DriverTask.TaskStatus.PARTIALLY_FAILED);
                e.printStackTrace();
                fullSuccess = false;
            }
        } // for each volume

        if (fullSuccess) {
            task.setStatus(DriverTask.TaskStatus.READY);
        }

        _log.info("3PARDriver:unexportVolumesFromInitiatorss leave");
        return task;
    }

    
    private void getClusterStoragePorts(HostMember hostRes, List<StoragePort> arrayPorts, String volStorageSystemId,
            List<StoragePort> clusterPorts) {

        for (StoragePort sp : arrayPorts) {
            if (volStorageSystemId.compareToIgnoreCase(sp.getStorageSystemId()) != 0) {
                continue;
            }

            String[] pos = sp.getNativeId().split(":");

            for (FcPath fc:hostRes.getFCPaths()) {

                if (fc.getPortPos() != null) {
                    if ((fc.getPortPos().getNode().toString().compareToIgnoreCase(pos[0]) == 0)
                            && (fc.getPortPos().getSlot().toString().compareToIgnoreCase(pos[1]) == 0)
                            && (fc.getPortPos().getCardPort().toString().compareToIgnoreCase(pos[2]) == 0)) {

                        // host connected array port
                        clusterPorts.add(sp);
                    }
                } // porPos != null
            } // for fc
        }
    }


    private String doHostProcessing(List<Initiator> initiators, List<StorageVolume> volumes, 
            Registry driverRegistry) {
        String host = null;

        for (StorageVolume vol : volumes) {
            // If required host/cluster should get created in all arrays to which volume belongs
            String hostArray = null;
            String clustArray = null;

            try {
                // all initiators belong to same host
                if (initiators.get(0).getInitiatorType().equals(Type.Host)) {
                    // Exclusive-Host export
                    // Some code is repeated with cluster for simplicity
                    hostArray = get3parHostname(initiators, vol.getStorageSystemId(), driverRegistry);
                    if (hostArray == null) {
                        // create a new host or add initiator to existing host
                        HP3PARApi hp3parApi = hp3parUtil.getHP3PARDeviceFromNativeId(vol.getStorageSystemId(),
                                driverRegistry);

                        ArrayList<String> portIds = new ArrayList<>();
                        for (Initiator init : initiators) {
                            portIds.add(init.getPort());
                        }

                        Integer persona = getPersona(initiators.get(0).getHostOsType());
                        hp3parApi.createHost(initiators.get(0).getHostName(), portIds, persona);
                        host = initiators.get(0).getHostName();
                    } else {
                        host = hostArray;
                    }
                    // Host available

                } else if (initiators.get(0).getInitiatorType().equals(Type.Cluster)) {
                    // Shared-Cluster export
                    clustArray = initiators.get(0).getClusterName();
                    HP3PARApi hp3parApi = hp3parUtil.getHP3PARDeviceFromNativeId(vol.getStorageSystemId(),
                            driverRegistry);

                    // Check if host exists, otherwise create
                    hostArray = get3parHostname(initiators, vol.getStorageSystemId(), driverRegistry);
                    if (hostArray == null) {
                        // create a new host or add initiator to existing host
                        ArrayList<String> portIds = new ArrayList<>();
                        for (Initiator init : initiators) {
                            portIds.add(init.getPort());
                        }

                        Integer persona = getPersona(initiators.get(0).getHostOsType());
                        hp3parApi.createHost(initiators.get(0).getHostName(), portIds, persona);
                        hostArray = initiators.get(0).getHostName();
                    }

                    // only one thread across all nodes should create cluster; 
                    // TBD: lock acquisition is having issues, synch is used
                    //String lockName = vol.getStorageSystemId() + vol.getNativeId() + hostArray;
                    //if (this.lockManager.acquireLock(lockName, 10, TimeUnit.MINUTES)) {
                    synchronized (this) {
                        // Check if cluster exists, otherwise create
                        HostSetDetailsCommandResult hostsetRes = hp3parApi.getHostSetDetails(clustArray);
                        if (hostsetRes == null) {
                            hp3parApi.createHostSet(clustArray, initiators.get(0).getHostName());
                        } else {
                            // if this host is not part of the cluster add it
                            boolean present = false;
                            for (String setMember:hostsetRes.getSetmembers()) {
                                if (hostArray.compareTo(setMember) == 0) {
                                    present = true;
                                    break;
                                }
                            }

                            if (!present) {
                                // update cluster with this host
                                hp3parApi.updateHostSet(clustArray, hostArray);
                            }
                        }

                        // Cluster available
                        host = "set:" + clustArray;
                        //this.lockManager.releaseLock(lockName);
                    }

                } else {
                    _log.error("3PARDriver:exportVolumesToInitiators error: Host/Cluster type not supported");
                    throw new HP3PARException(
                            "3PARDriver:exportVolumesToInitiators error: Host/Cluster type not supported");
                }
            } catch (Exception e) {
                String msg = String.format("3PARDriver: Unable to export, error: %s", e);
                _log.error(msg);
                _log.error(CompleteError.getStackTrace(e));
                return null;
            }
        } // for each volume

        return host;
    }

    private String search3parHostName(List<Initiator> initiators, HostCommandResult hostRes) {
        String hp3parHost = null;

        // for each host in 3par
        for(HostMember hostMemb:hostRes.getMembers()) {
            // for each host initiator sent
            for (Initiator init : initiators) {

                // Is initiator FC
                if (init.getProtocol().toString().compareToIgnoreCase(Protocols.FC.toString()) == 0 ) {
                    // verify in all FC ports with host
                    for(FcPath fcPath: hostMemb.getFCPaths()) {                         
                        if (SanUtils.formatWWN(fcPath.getWwn()).compareToIgnoreCase(init.getPort()) == 0) {
                            hp3parHost = hostMemb.getName();
                            _log.info("3PARDriver: get3parHostname initiator {} host {}", init.getPort(),
                                    hp3parHost);
                            return hp3parHost;
                        }
                    }
                } else if (init.getProtocol().toString().compareToIgnoreCase(Protocols.iSCSI.toString()) == 0 ){
                    // verify in all iSCSI ports with host
                    for (ISCSIPath scsiPath:hostMemb.getiSCSIPaths()) {
                        if (scsiPath.getName().compareToIgnoreCase(init.getPort()) == 0) {
                            hp3parHost = hostMemb.getName();
                            _log.info("3PARDriver: get3parHostname initiator {} host {}", init.getPort(),
                                    hp3parHost);
                            return hp3parHost;
                        }
                    }

                } // if FC or iSCSI
            } // each initiator
        } // each host

        return null;
    }

    private String get3parHostname(List<Initiator> initiators, String storageId,
            Registry driverRegistry) throws Exception {
        // Since query works this implementation can be changed
        String hp3parHost = null;
        _log.info("3PARDriver: get3parHostname enter");

        try {
            HP3PARApi hp3parApi = hp3parUtil.getHP3PARDeviceFromNativeId(storageId, driverRegistry);
            HostCommandResult hostRes = hp3parApi.getAllHostDetails();

            hp3parHost = search3parHostName(initiators, hostRes);
            
            _log.info("3PARDriver: get3parHostname leave");
            return hp3parHost;
        } catch (Exception e) {
            _log.error("3PARDriver:get3parHostname could not get 3par registered host name");
            _log.error(CompleteError.getStackTrace(e));
            return null;
        }
    }

    private Integer getPersona(HostOsType hostType) {
        Integer persona = 0;
        
        // Supporting from lower OS versions; 
        switch (hostType) {
            case Windows:
            case Linux:
            case SUNVCS:
                persona = 1;
                break;

            case HPUX:
                persona = 7;
                break;

            case Esx:
                persona = 11;
                break;

            case AIX:
            case AIXVIO:
                persona = 8;
                break;

                // persona 3 is by experimentation, doc is not up-to-date
            case No_OS:
            case Other:
            default:
                persona = 3;
                break;
        }
        return persona;
    }
    
    public HP3PARUtil getHp3parUtil() {
        return hp3parUtil;
    }

    public void setHp3parUtil(HP3PARUtil hp3parUtil) {
        this.hp3parUtil = hp3parUtil;
    }

}
