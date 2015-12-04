/*
 * Copyright (c) 2012-2015 iWave Software LLC
 * All Rights Reserved
 */
/**
 * 
 */
package com.iwave.ext.netappc;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import netapp.manage.NaElement;

import org.apache.commons.lang.StringUtils;
import org.apache.log4j.Logger;

import com.google.common.collect.Maps;
import com.iwave.ext.netapp.AggregateInfo;
import com.iwave.ext.netappc.NFSSecurityStyle;
import com.iwave.ext.netapp.QuotaCommands;
import com.iwave.ext.netappc.NetAppCException;
import com.iwave.ext.netappc.model.CifsAcl;
import com.iwave.ext.netapp.Server;
import com.iwave.ext.netapp.model.ExportsRuleInfo;
import com.iwave.ext.netapp.model.NetAppDevice;
import com.iwave.ext.netapp.model.Qtree;
import com.iwave.ext.netapp.model.Quota;
import com.iwave.ext.netapp.utils.ExportRule;
import com.iwave.ext.netappc.FlexVolume;
import com.iwave.ext.netappc.StorageVirtualMachine;
import com.iwave.ext.netappc.StorageVirtualMachineInfo;

public class NetAppClusterFacade {

    private Logger log = Logger.getLogger(getClass());
    Server server = null;
    String _svmName = null;

    public NetAppClusterFacade(String host, int port, String username, String password, boolean useHTTPS) {
        this(host, port, username, password, useHTTPS, false, null);
    }

    public NetAppClusterFacade(String host, int port, String username, String password, boolean useHTTPS, boolean isSVM, String svmName)
    {
        if (log.isDebugEnabled()) {
            String svm = (svmName != null ? svmName : "");
            log.debug("Connecting to NetApp server: " + host + ":" + port + ":" + svm);
        }
        _svmName = svmName;
        log.info("Connecting to NetApp server: " + host + ":" + port + ":" + svmName);
        server = new Server(host, port, username, password, useHTTPS, isSVM, svmName, true);
    }

    public static NetAppClusterFacade create(Map<String, String> connectionParams) {
        return new NetAppClusterFacade(connectionParams.get(NetAppDevice.IP_KEY),
                Integer.parseInt(connectionParams.get(NetAppDevice.PORT_KEY)),
                connectionParams.get(NetAppDevice.USR_KEY),
                connectionParams.get(NetAppDevice.PWD_KEY),
                Boolean.parseBoolean(connectionParams.get(NetAppDevice.SECURE_KEY)));
    }

    /***** Aggregate Ops ********/

    /**
     * Returns a list of aggregates.
     * 
     * @param name - Optional. If provided only the aggregate info for the
     *            the name aggregate is returned. If null or empty all aggregates are
     *            returned.
     * @return - list of aggregates.
     */
    public List<AggregateInfo> listAggregates(String name)
    {
        if (log.isDebugEnabled()) {
            log.debug("Listing Aggregates. Params [name]: " + name);
        }

        NetAppCAggregate aggr = new NetAppCAggregate(server.getNaServer(), name);
        boolean listAll = false;
        if (name == null || name.isEmpty()) {
            listAll = true;
        }
        return aggr.listAllAggregates(listAll);
    }

    /**** Volume operations *****/

    /**
     * Returns the volumes size as string.
     * 
     * @param volumeName - Name of the volume.
     * @return - Size with unit. For example, "100g, 1t, 2048m".
     */
    public String getVolumeSize(String volumeName)
    {
        if (log.isDebugEnabled()) {
            log.debug("Retrieving volume size. Volume: " + volumeName);
        }
        FlexVolume vol = new FlexVolume(server.getNaServer(), volumeName);
        String size = vol.getVolumeSize();
        return size;
    }

    /**
     * Takes a volume offline. Note this call does *not* wait for the specified
     * number of minutes. The delay occurs on the device.
     * 
     * @param volumeName - name of volume to offline
     * @param delayInMinutes - number of minutes to wait on the device before
     *            the volume is offline.
     */
    public void setVolumeOffline(String volumeName, int delayInMinutes)
    {
        if (log.isDebugEnabled()) {
            log.debug("Taking volume offline with params[name,delayInMinutes]: " +
                    volumeName + "," + delayInMinutes);
        }
        FlexVolume vol = new FlexVolume(server.getNaServer(), volumeName);
        vol.setVolumeOffline(delayInMinutes);
    }

    /**
     * Creates a new flexible volume
     * Only parameters for flexible volumes are provided.
     * Note the volume may not be operational immediately after this method returns. Use
     * getVolumeInfo() to query the status of the new volume.
     * 
     * @param volName - Required. Volume name.
     * @param containingAggrName - Optional. Name of the aggregate in which to create the volume. Must
     *            be used in conjunction with the size parameter.
     * @param size - Optional. Size (with unit) of the new volume. Ex: 10g, 2000m, 1t. Must be used
     *            in conjunction with containingAggrName.
     * @param spaceReserve - Optional. Type of volume guarantee new volume will use. Valid
     *            values are "none", "file", "volume".
     * @param permission - Optional. Unix permission bits in octal string format.
     * @return
     */
    public boolean createFlexibleVolume(String volName, String containingAggrName, String path, String size, String spaceReserve,
            String permission)
    {
        if (log.isDebugEnabled()) {
            StringBuilder sb = new StringBuilder("Creating new flexible volume offline with params" +
                    "[volName,aggrName,path,size,spaceReserve]:");
            sb.append(volName).append(", ");
            sb.append(containingAggrName).append(", ");
            sb.append(path).append(", ");
            sb.append(size).append(", ");
            sb.append(spaceReserve).append(", ");
            sb.append(permission).append(", ");
            log.debug(sb.toString());
        }
        // First create the volume
        FlexVolume vol = new FlexVolume(server.getNaServer(), volName);
        boolean result = vol.createFlexibleVolume(containingAggrName, path, size, spaceReserve, permission);
        return result;
    }

    /**
     * Unmounts a volume.
     * 
     * @param volumeName
     * @param force
     * @return
     */
    public boolean unmountVolume(String volumeName, boolean force)
    {
        if (log.isDebugEnabled()) {
            log.debug("Unmounting volume with params[name,force]: " +
                    volumeName + "," + force);
        }
        FlexVolume vol = new FlexVolume(server.getNaServer(), volumeName);
        boolean result = vol.unmountVolume(force);

        return result;
    }

    /**
     * Destroys a volume.
     * 
     * @param volumeName
     * @param force
     * @return
     */
    public boolean destroyVolume(String volumeName, boolean force)
    {
        if (log.isDebugEnabled()) {
            log.debug("Deleting volume with params[name,force]: " +
                    volumeName + "," + force);
        }
        FlexVolume vol = new FlexVolume(server.getNaServer(), volumeName);
        boolean result = vol.destroyVolume(force);

        return result;
    }

    /**
     * Sets a new size of a volume.
     * 
     * @param newSize - Size (with unit) of the new volume. Ex: 10g, 2000m, 1t.
     * 
     * @return - The new size of the volume.
     */
    public String setVolumeSize(String volumeName, String newSize) {

        FlexVolume vol = new FlexVolume(server.getNaServer(), volumeName);
        return vol.setVolumeSize(newSize);
    }

    /**
     * Takes a snapshot of the volume. This operation waits for the snapshot to complete on the device.
     * 
     * @param volumeName - Name of the volume
     * @param snapshotName - Name of the snapshot
     * @return - true/false if successful
     */
    public boolean createVolumeSnapshot(String volumeName, String snapshotName)
    {
        if (log.isDebugEnabled()) {
            log.debug("Creating snapshot on volume with params[volName,snapshotName]: " +
                    volumeName + "," + snapshotName);
        }
        log.info("Creating snapshot on volume with params[volName,snapshotName]: " +
                volumeName + "," + snapshotName);
        FlexVolume vol = new FlexVolume(server.getNaServer(), volumeName);
        // Take a snapshot (synchronous).
        boolean result = vol.createSnapshot(snapshotName, false);

        return result;
    }

    /**
     * Deletes a snapshot from a volume
     * 
     * @param volumeName - Name of the volume containing the snapshot
     * @param snapshotName - Name of the snapshot to be deleted
     * @return - true/false if the operation was successful.
     */
    public boolean deleteVolumeSnapshot(String volumeName, String snapshotName)
    {
        if (log.isDebugEnabled()) {
            log.debug("Deleting snapshot on volume with params[volName,snapshotName]: " +
                    volumeName + "," + snapshotName);
        }
        FlexVolume vol = new FlexVolume(server.getNaServer(), volumeName);
        boolean result = vol.deleteSnapshot(snapshotName);
        return result;
    }

    /**
     * Restores a volume to a snapshot.
     * 
     * @param volumeName - Volume to be restored
     * @param snapshotName - Snapshot to restore from
     * @return - true/false if operation was successful
     */
    public boolean restoreVolumeFromSnapshot(String volumeName, String snapshotName)
    {
        if (log.isDebugEnabled()) {
            log.debug("Restoring volume from snapshot with params[volName,snapshotName]: " +
                    volumeName + "," + snapshotName);
        }
        FlexVolume vol = new FlexVolume(server.getNaServer(), volumeName);
        boolean result = vol.restoreVolumeFromSnapshot(snapshotName);
        return result;
    }

    /**
     * Lists all volumes on a svm.
     * 
     * @return - list of volumes
     */
    public List<String> listVolumes()
    {
        if (log.isDebugEnabled()) {
            log.debug("List all volumes");
        }
        FlexVolume vol = new FlexVolume(server.getNaServer(), ""); // Vol name is not needed for this
        List<String> volumes = vol.listVolumes();
        return volumes;
    }

    /**
     * Lists all volumes on a svm.
     * 
     * @return - list of volumes
     */
    public List<Map<String, String>> listVolumeInfo(String volume, Collection<String> attrs)
    {
        if (log.isDebugEnabled()) {
            log.debug("List all volumes with attributes");
        }

        FlexVolume vol = new FlexVolume(server.getNaServer(), volume);
        return vol.listVolumeInfo(attrs);
    }

    public boolean addCIFSShare(String mountPath, String shareName, String comment, int maxusers, String forcegroup)
    {
        if (log.isDebugEnabled()) {
            log.debug("Add CIFS share to volume with params[volname,shareName,comment,maxusers]" +
                    mountPath + "," + shareName + "," + comment + "," + maxusers);
        }
        FlexFileShare share = new FlexFileShare(server.getNaServer(), mountPath);
        return share.addCIFSShare(shareName, comment, maxusers, forcegroup);
    }

    public void changeCIFSShare(String shareName, Map<String, String> attrs) {
        FlexFileShare share = new FlexFileShare(server.getNaServer(), null);
        share.changeCIFSShare(shareName, attrs);
    }

    public void changeCIFSShare(String shareName, String attr, String value) {
        Map<String, String> attrs = Maps.newHashMap();
        attrs.put(attr, value);
        changeCIFSShare(shareName, attrs);
    }

    /**
     * set CIFS Access Control List
     * 
     * @param acl
     */
    public void setCIFSAcl(CifsAcl acl) {
        FlexFileShare share = new FlexFileShare(server.getNaServer(), null);
        share.setCIFSAcl(acl);
    }

    /**
     * add CIFS Access Control List
     * 
     * @param acl
     */
    public void addCIFSAcl(CifsAcl acl) {
        FlexFileShare share = new FlexFileShare(server.getNaServer(), null);
        share.addCIFSAcl(acl);
    }

    /**
     * Lists CIFS Access Control Lists.
     * 
     * @param shareName - can contain wildcard * or ?
     * @return
     */
    public List<CifsAcl> listCIFSAcls(String shareName) {
        FlexFileShare share = new FlexFileShare(server.getNaServer(), null);
        return share.listCIFSAcls(shareName);
    }

    /**
     * delete CIFS Access Control List
     * 
     * @param acl
     */
    public void deleteCIFSAcl(CifsAcl acl) {
        FlexFileShare share = new FlexFileShare(server.getNaServer(), null);
        share.deleteCIFSAcl(acl);
    }

    public List<ExportsRuleInfo> listNFSExportRules(String pathName)
    {
        FlexFileShare share = new FlexFileShare(server.getNaServer(), null);
        return share.listNFSExportRules(pathName);
    }

    /**
     * Adds a new NFS share.
     * 
     * @param fsName - File System name
     * @param qtreeName - Qtree name
     * @param mountPath - The mount path
     * @param exportPath - The exported path of the share.
     * @param anonymousUid - All hosts with this uid have root access to the directory. Use
     *            -1 to leave 'unspecified'
     * @param roHosts - List of hosts with read-only access
     * @param rwHosts - List of hosts with read-write access
     * @param rootHosts - List of hosts with root access
     * @param securityStyle - List of security styles this share supports.
     */
    public void addNFSShare(String fsName, String qtreeName, String mountPath,
            String exportPath, int anonymousUid, List<String> roHosts,
            List<String> rwHosts, List<String> rootHosts, List<NFSSecurityStyle> securityStyle) {
        FlexFileShare share = new FlexFileShare(server.getNaServer(), mountPath);
        share.addNFSShare(fsName, qtreeName, exportPath, anonymousUid, roHosts,
                rwHosts, rootHosts, securityStyle);
    }

    public void addNFSShare(String fsName, String qtreeName, String exportPath,
            ExportRule newRule)
    {
        FlexFileShare share = new FlexFileShare(server.getNaServer(), exportPath);
        share.changeNFSShare(fsName, qtreeName, null, newRule, exportPath);
    }

    /**
     * Deletes a CIFS share, but not the underlying storage
     * 
     * @param shareName
     */
    public void deleteCIFSShare(String shareName)
    {
        if (log.isDebugEnabled()) {
            log.debug("Delete CIFS share with params[shareName]" + shareName);
        }
        FlexFileShare share = new FlexFileShare(server.getNaServer(), "");
        share.deleteCIFSShare(shareName);
    }

    /**
     * Lists CIFS Shares.
     * Most keys in returned Maps are optional. For example, description and maxusers are only returned if they are set.
     * For exaample: {description=Testing, maxusers=5, share-name=demotest, mount-point=/vol/volscott}
     * 
     * @param shareName - can contain wildcard * or ?
     * @return
     */
    public List<Map<String, String>> listCIFSShares(String shareName) {
        FlexFileShare share = new FlexFileShare(server.getNaServer(), null);
        return share.listCIFSInfo(shareName);
    }

    /**
     * Deletes a named NFS mounted share. As a convenience this function can also delete all
     * NFS shares.
     * 
     * @param mountPath - Path of share to delete
     * @param deleteAll - If true *ALL* mounted NFS shares will be removed. Be careful!
     * @return - Returns a list of deleted paths.
     */
    public List<String> deleteNFSShare(String mountPath, boolean deleteAll)
    {
        FlexFileShare share = new FlexFileShare(server.getNaServer(), mountPath);
        return share.deleteNFSShare(deleteAll);
    }

    /**
     * Deletes a named NFS mounted share. As a convenience this function can also delete all
     * NFS shares.
     * 
     * @param fsName - File System name
     * @param qtreeName - Qtree name
     * @param oldRule - Export Rule to be modified
     * @param mountPath - Path of share to delete
     */
    public void deleteNFSShare(String fsName, String qtreeName, ExportRule oldRule,
            String mountPath) {
        FlexFileShare share = new FlexFileShare(server.getNaServer(), mountPath);
        share.deleteNFSShare(fsName, qtreeName, oldRule, mountPath);
    }

    /**
     * get array system-info.
     * 
     * @return map of name/values as returned from system-get-node-info-iter API
     */
    public Map<String, String> systemInfo() {
        HashMap<String, String> info = new HashMap<String, String>();
        NaElement elem = new NaElement("system-get-node-info-iter");
        NaElement attributesList = null;

        /*
         * For example: {partner-system-id=0142226671, board-speed=1666, system-name=iWaveTST0,
         * cpu-serial-number=8006251, system-serial-number=700000567389, memory-size=4096,
         * number-of-processors=2, system-id=0142223979, partner-system-name=iWaveTST1,
         * system-revision=B0, controller-address=A, vendor-id=NetApp, system-machine-type=FAS2040,
         * system-model=FAS2040, board-type=System Board XVII, cpu-processor-id=0x6ec, cpu-part-number=110-00133,
         * supports-raid-array=false, cpu-revision=A4, cpu-firmware-release=6.1, cpu-microcode-version=85}
         */

        try {
            List outputElements = (List) server.getNaServer().invokeElem(elem).getChildByName("attributes-list").getChildren();
            Iterator iter = outputElements.iterator();
            while (iter.hasNext()) {
                attributesList = (NaElement) iter.next();
                for (NaElement child : (List<NaElement>) attributesList.getChildren()) {
                    String name = child.getName();
                    info.put(name, child.getContent());
                }
            }
            return info;
        } catch (Exception e) {
            String msg = "Failed to get array system info";
            log.error(msg, e);
            throw new NetAppCException(msg, e);
        }
    }

    /**
     * get array system-info.
     * 
     * @return map of name/values as returned from system-get-info API
     */
    public Map<String, String> systemVersion() {
        HashMap<String, String> info = new HashMap<String, String>();
        NaElement elem = new NaElement("system-get-version");
        NaElement result = null;

        try {
            result = server.getNaServer().invokeElem(elem).getChildByName("version");
            if (result != null) {
                String name = result.getName();
                info.put(name, result.getContent());
            }
            result = server.getNaServer().invokeElem(elem).getChildByName("is-clustered");
            if (result != null) {
                String name = result.getName();
                info.put(name, result.getContent());
            }
            return info;
        } catch (Exception e) {
            String msg = "Failed to get array system version " + e.getMessage();
            log.error(msg, e);
            throw new NetAppCException(msg, e);
        }
    }

    /**
     * Sets the security style of a QTree
     * 
     * @param path full path of the QTree (i.e. /vol/MyVolume/MyTree)
     * @param type either ntfs, unix, mixed
     */
    public void setQTreeSecurityStyle(String path, String type) {
        String volName = path.substring(5);
        String output = invokeCliCommand(new String[] { "volume", "qtree", "security", "vserver", _svmName,
                "-qtree-path", path, "-security-style", type });

        // Either blank, or a Quota warning message, means we're successful
        if (!StringUtils.isBlank(output) && !output.startsWith("qtree: Changing the security style")) {
            throw new NetAppCException(output);
        }
    }

    public void setQTreeOplocks(String qtreePath, String oplocks) {
        String infoString = "NetAppFacade::setQTreeOplocks -> Trying to set oplocks = " + oplocks + " on qtree = " + qtreePath;
        log.info(infoString);
        NaElement elem = new NaElement("system-cli");
        NaElement argsarray = new NaElement("args");
        argsarray.addChildElem(new NaElement("arg", "qtree"));
        argsarray.addChildElem(new NaElement("arg", "oplocks"));
        argsarray.addChildElem(new NaElement("arg", qtreePath));
        argsarray.addChildElem(new NaElement("arg", oplocks));
        elem.addChildElem(argsarray);
        try {
            server.getNaServer().invokeElem(elem);
        } catch (Exception e) {
            String msg = "NetAppFacade::setQTreeOplocks -> Failed to invoke CLI command ";
            log.error(msg, e);
            throw new NetAppCException(msg, e);
        }
    }

    /**
     * Invokes a CLI command through the API
     * 
     * @param args
     * @return
     */
    public String invokeCliCommand(String args[]) {
        String cliResult;
        NaElement elem = new NaElement("system-cli");
        NaElement argsarray = new NaElement("args");
        for (int i = 0; i < args.length; i++) {
            argsarray.addNewChild("arg", args[i]);
        }
        elem.addChildElem(argsarray);
        // Call the NetApp API
        try {
            NaElement result = server.getNaServer().invokeElem(elem);
            cliResult = result.getChildContent("cli-output");
            return cliResult;
        } catch (Exception e) {
            String msg = "Failed to invoke CLI command ";
            log.error(msg, e);
            throw new NetAppCException(msg, e);
        }
    }

    public List<Qtree> listQtrees(String volume) {
        if (log.isDebugEnabled()) {
            log.debug("Retrieving qtrees");
        }

        ClusterQtreeCommands qtreeCommands = new ClusterQtreeCommands(this.server.getNaServer());
        return qtreeCommands.listQtree(volume);
    }

    public boolean isQtree(String volume, String qtreeName) {
        if (log.isDebugEnabled()) {
            log.debug("Checking if " + qtreeName + " is a qtree on filesystem " + volume);
        }

        ClusterQtreeCommands qtreeCommands = new ClusterQtreeCommands(this.server.getNaServer());
        return qtreeCommands.isQtree(volume, qtreeName);
    }

    /**
     * Creates a Qtree rooted in the specified volume.
     * 
     * @param qtree the Qtree name.
     * @param volume the volume name.
     * @param oplocks the true for enabled; false for disabled.
     * @param security style - unix, ntfs or mixed.
     */

    public void createQtree(String qtree, String volume, Boolean opLocks, String securityStyle) {
        if (log.isDebugEnabled()) {
            log.debug("Creating Qtree " + qtree + " in volume " + volume);
        }
        ClusterQtreeCommands commands = new ClusterQtreeCommands(server.getNaServer());
        commands.createQtree(qtree, volume, opLocks, securityStyle);
    }

    /**
     * Update a Qtree rooted in the specified volume.
     * 
     * @param qtree the Qtree name.
     * @param volume the volume name.
     * @param oplocks the true for enabled; false for disabled.
     * @param security style - unix, ntfs or mixed.
     */

    public void updateQtree(String qtree, String volume, Boolean opLocks, String securityStyle) {
        if (log.isDebugEnabled()) {
            log.debug("Creating Qtree " + qtree + " in volume " + volume);
        }
        ClusterQtreeCommands commands = new ClusterQtreeCommands(server.getNaServer());
        commands.updateQtree(qtree, volume, opLocks, securityStyle);
    }

    /**
     * Deletes the qtree at the given path (/vol/&lt;volume&gt;/&lt;qtree&gt;)
     * 
     * @param path the qtree path.
     * @param force whether to force deletion even if the qtree is not empty.
     */
    public void deleteQtree(String path, boolean force) {
        if (log.isDebugEnabled()) {
            log.debug("Deleting Qtree " + path);
        }
        ClusterQtreeCommands commands = new ClusterQtreeCommands(server.getNaServer());
        commands.deleteQtree(path, force);
    }

    /**
     * Creates a Qtree rooted in the specified volume.
     * 
     * @param qtree the Qtree name.
     * @param volume the volume name.
     * @param mode the permissions on the qtree (similar to unix file permissions, 0755 for example)
     * @param oplocks the true for enabled; false for disabled.
     * @param security style - unix, ntfs or mixed.
     */
    public void createQtree(String qtree, String volume, String mode, Boolean opLocks, String securityStyle) {
        if (log.isDebugEnabled()) {
            log.debug("Creating Qtree " + qtree + " in volume " + volume + " [mode=" + mode + "]");
        }
        ClusterQtreeCommands commands = new ClusterQtreeCommands(server.getNaServer());
        commands.createQtree(qtree, volume, mode, opLocks, securityStyle);
    }

    /**
     * Gets a tree quota for the given volume and path.
     * 
     * @param volume the volume name or volume path.
     * @param path the path for the quota.
     * @return the quota.
     */

    public Quota getTreeQuota(String volume, String path) {
        if (log.isDebugEnabled()) {
            log.debug("Getting tree quota for volume " + volume + ", path " + path);
        }
        QuotaCommands commands = new QuotaCommands(server.getNaServer());
        return commands.getTreeQuota(volume, path);
    }

    /**
     * Sets a disk-limit tree quota. If the tree quota doesn't already exist, it is created. The
     * volume can be specified as a name only, or as the volume path (/vol/<volume-name>). The disk
     * limit and threshold values are specified in KB.
     * 
     * @param volume the volume name or volume path containing the path to apply quota.
     * @param path the path which will have a disk limit applied.
     * @param diskLimitInKB the disk limit in KB.
     * @param thresholdInKB the threshold in KB after which a message will be logged. The threshold
     *            is only set if it is greater than 0.
     */
    public void setDiskLimitTreeQuota(String volume, String path, long diskLimitInKB,
            long thresholdInKB) {
        if (log.isDebugEnabled()) {
            log.debug("Setting disk limit tree quota of " + diskLimitInKB + " KB to " + path);
        }
        QuotaCommands commands = new QuotaCommands(server.getNaServer());
        commands.setDiskLimitTreeQuota(volume, path, diskLimitInKB, thresholdInKB);
    }

    /**
     * Addss a disk-limit tree quota. The volume can be specified as a name only,
     * or as the volume path (/vol/<volume-name>). The disk limit and threshold values are specified
     * in KB.
     * 
     * @param volume the volume name or volume path containing the path to apply quota.
     * @param path the path which will have a disk limit applied.
     * @param diskLimitInKB the disk limit in KB.
     * @param thresholdInKB the threshold in KB after which a message will be logged. The threshold
     *            is only set if it is greater than 0.
     */
    public void addDiskLimitTreeQuota(String volume, String path, long diskLimitInKB,
            long thresholdInKB) {
        if (log.isDebugEnabled()) {
            log.debug("Setting disk limit tree quota of " + diskLimitInKB + " KB to " + path);
        }
        QuotaCommands commands = new QuotaCommands(server.getNaServer());
        commands.addDiskLimitTreeQuota(volume, path, diskLimitInKB, thresholdInKB);
    }

    public void modifyDiskLimitTreeQuota(String volume, String path, long diskLimitInKB,
            long thresholdInKB) {
        if (log.isDebugEnabled()) {
            log.debug("Modifying disk limit tree quota of " + diskLimitInKB + " KB to " + path);
        }

        QuotaCommands commands = new QuotaCommands(server.getNaServer());
        commands.modifyDiskLimitTreeQuota(volume, path, diskLimitInKB, thresholdInKB);
    }

    public void deleteTreeQuota(String volume, String path) {
        if (log.isDebugEnabled()) {
            log.debug("Deleting tree quota for " + path);
        }
        QuotaCommands commands = new QuotaCommands(server.getNaServer());
        commands.deleteTreeQuota(volume, path);
    }

    /**
     * Returns the current status of Quotas on the specified volume
     */
    public QuotaCommands.QuotaStatus getQuotaStatus(String volume) {
        QuotaCommands commands = new QuotaCommands(server.getNaServer());
        return commands.getQuotaStatus(volume);
    }

    /**
     * Starts to turn quotas on for a volume. A successful return from this API does not mean that quotas are on,
     * merely that an attempt to start it has been triggered
     */
    public void turnQuotaOn(String volume) {
        QuotaCommands commands = new QuotaCommands(server.getNaServer());
        commands.turnQuotaOn(volume);
    }

    /**
     * Reinitialize quota subsystem off for a volume
     */
    public void reintializeQuota(String volume) {
        QuotaCommands commands = new QuotaCommands(server.getNaServer());
        commands.turnQuotaOff(volume);
        commands.turnQuotaOn(volume);
    }

    /**
     * List of snapshots for a volume
     * 
     * @param volumeName - Name of the volume containing the snapshots
     * @return - Collection containing snapshots .
     */
    public Collection<String> listSnapshots(String volumeName)
    {
        if (log.isDebugEnabled()) {
            log.debug("Listing snapshots on volume with params[volName]: " +
                    volumeName);
        }
        FlexVolume vol = new FlexVolume(server.getNaServer(), volumeName);
        Collection<String> attrs = new ArrayList<String>();
        attrs.add("name");
        return vol.listSnapshots(attrs);
    }

    public List<StorageVirtualMachineInfo> listSVM()
    {
        if (log.isDebugEnabled()) {
            log.debug("Listing svms");
        }

        StorageVirtualMachine svm = new StorageVirtualMachine(server.getNaServer(), null);
        return svm.listSVMs(true);
    }

    public void modifyNFSShare(String fsName, String qtreeName, String exportPath,
            ExportRule oldRule, ExportRule newRule)
    {
        FlexFileShare share = new FlexFileShare(server.getNaServer(), exportPath);
        share.changeNFSShare(fsName, qtreeName, oldRule, newRule, exportPath);
    }
}