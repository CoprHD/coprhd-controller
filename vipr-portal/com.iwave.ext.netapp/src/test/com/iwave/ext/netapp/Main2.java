/*
 * Copyright 2012-2015 iWave Software LLC
 * All Rights Reserved
 */
package com.iwave.ext.netapp;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.iwave.ext.netapp.model.CifsAccess;
import com.iwave.ext.netapp.model.CifsAcl;
import com.iwave.ext.netapp.model.ExportsRuleInfo;

public class Main2 {

    static private NetAppFacade netAppFacade = null;
    private static Server server = null;

    public static void main(String[] args) {

        // Simulator info/creds
        String arrayIp = null;
        int arrayPort = 443;
        String arrayUser = null, arrayPassword = null;
        String vFiler = "vfiler3";
        String host1 = null, host2 = null;

        if (args.length == 5) {
            arrayIp = args[0];
            arrayUser = args[1];
            arrayPassword = args[2];
            host1 = args[3];
            host2 = args[4];
        }
        System.out.println("Entering OnTap Test Client");

        // netAppFacade = new NetAppFacade(arrayIp, arrayPort, arrayUser, arrayPassword, vFiler, true);
        netAppFacade = new NetAppFacade(arrayIp, arrayPort, arrayUser, arrayPassword, true);
        server = netAppFacade.server;

        List<AggregateInfo> temp = netAppFacade.listAggregates(null);
        String volumeName = "/vol/vol4";
        String volumeFullPath = "/vol/vol4";
        String volumeSize = "101m";
        String volumeGuarantee = "none"; // Or, "file" or "volume"

        List<ExportsRuleInfo> temp2 = netAppFacade.listNFSExportRules(volumeName);

        // if (netAppFacade.createFlexibleVolume(volumeName,
        // "aggr0", false, null, volumeSize, null,
        // volumeGuarantee, false, null)) {
        // System.out.println("Created a new volume: " + volumeName +
        //
        // " within aggregate: " + temp.get(0).getName());
        // } else {
        // System.out.println("Volume creation failed...");
        // }

        // List all volumes
        // List all volumes
        Map<VolumeOptionType, String> options = new HashMap<VolumeOptionType, String>();
        options.put(VolumeOptionType.convert_ucode, "off");
        options.put(VolumeOptionType.create_ucode, "off");
        netAppFacade.setVolumeOptions(volumeName, options);

        // Create an NFS file-share
        System.out.println("");
        System.out.println("Adding NFS share...");
        String mountPath = "/vol/vol4";
        String exportPath = "/vol/vol4";
        int anonymousUid = -1;
        List<String> roHosts = new ArrayList<String>();
        List<String> rwHosts = new ArrayList<String>();
        List<String> rootHosts = new ArrayList<String>();
        boolean roAddAll = false;
        ;
        boolean rwAddAll = false;
        boolean rootAddAll = false;
        List<NFSSecurityStyle> securityStyle = new ArrayList<NFSSecurityStyle>();
        // rwHosts.add("");
        rwHosts.add(host1);

        // rootHosts.add("");
        securityStyle.add(NFSSecurityStyle.sys);
        // List<String> share =
        //
        // netAppFacade.addNFSShare2(null, volumeName, 0, roHosts,
        // roAddAll, rwHosts, rwAddAll, rootHosts, rootAddAll, securityStyle);

        System.out.println("");
        System.out.println("Adding NFS share...");

        List<String> roHosts2 = new ArrayList<String>();
        List<String> rwHosts2 = new ArrayList<String>();
        List<String> rootHosts2 = new ArrayList<String>();
        boolean roAddAll2 = false;
        ;
        boolean rwAddAll2 = false;
        boolean rootAddAll2 = false;
        List<NFSSecurityStyle> securityStyle2 = new ArrayList<NFSSecurityStyle>();
        securityStyle2.add(NFSSecurityStyle.sys);
        securityStyle2.add(NFSSecurityStyle.krb5);
        roHosts2.add(host2);
        // roHosts2.add("");
        // rootHosts.add("");
        netAppFacade.addNFSShare(null, volumeName, 0, roHosts2,
                roAddAll2, rwHosts2, rwAddAll2, rootHosts2, rootAddAll2, securityStyle2);

        System.out.println("Shares: ");
        // for (String str: share) {
        // System.out.println(str);
        // }

        // netAppFacade.setQTreeSecurityStyle(volumeFullPath, "ntfs");

        // netAppFacade.setQTreeSecurityStyle(volumeFullPath, "unix");

        // netAppFacade.setQTreeSecurityStyle(volumeFullPath, "mixed");

        System.out.println("All volumes:");
        List<String> volumes3 = netAppFacade.listVolumes();
        List<ExportsRuleInfo> exports = netAppFacade.listNFSExportRules(null);
        // ArrayList<String> snapshotList = (ArrayList<String>) netAppFacade.listSnapshots("auto_bourne");
        // for (String vols: volumes) {
        // System.out.println(vols);
        // }

        if (null != volumes3) {
            for (int i = 0; i < volumes3.size(); i++) {
                Collection<String> attrs = new ArrayList<String>();
                String volumeName2 = "xyz";
                attrs.add("name");
                attrs.add("size-total");
                attrs.add("size-used");
                ArrayList<String> arg0 = null;
                List<Map<String, String>> volumeAttributes = netAppFacade.listVolumeInfo(volumes3.get(i), attrs);

                // boolean snapshotAttributes2 = netAppFacade.listSnapshot(volumes3.get(i), "hello");
                String hello = "world";
            }
        }

        // netAppFacade.setVolumeOffline(volumeName, 1);
        // Create a volume

        // List snapshots for the volume
        // Collection<String> attrs = new ArrayList<String>();
        // netAppFacade.listSnapshotInfo(volumeName, attrs);

        // if (netAppFacade.createFlexibleVolume(volumeName,
        // temp.get(1).getName(), false, null, volumeSize, null,
        // volumeGuarantee, false, null)) {
        // System.out.println("Created a new volume: " + volumeName +
        //
        // " within aggregate: " + temp.get(0).getName());
        // } else {
        // System.out.println("Volume creation failed...");
        // }

        // //List snapshots for the volume
        // Collection<String> attrs = new ArrayList<String>();
        // netAppFacade.listSnapshotInfo(volumeName, attrs);

        System.out.println("All volumes:");
        List<String> volumes = netAppFacade.listVolumes();

        // List aggregates - including all available info
        temp = netAppFacade.listAggregates(null);

        System.out.println("Aggregates:");
        for (AggregateInfo agi : temp) {
            System.out.println(" ");
            System.out.println("Name: " + agi.getName() + ", " +
                    "Disk count: " + agi.getDiskCount() +
                    ", State: " + agi.getState() +
                    ", RAID status: " + agi.getRaidStatus() +
                    ", Size available: " + agi.getSizeAvailable() +
                    ", Size total: " + agi.getSizeTotal() +
                    ", Size used: " + agi.getSizeUsed() +
                    ", Volume count: " + agi.getVolumeCount() +
                    "");
            System.out.println("Volumes and sizes:");
            volumes = agi.getVolumes();
            for (String vol : volumes) {
                System.out.println(vol + ": " + netAppFacade.getVolumeSize(vol));
            }

            System.out.println("Disk types:");
            for (String dt : agi.getDiskTypes()) {
                System.out.println(dt);
            }

            System.out.println("Disk speeds:");
            for (String dt : agi.getDiskSpeeds()) {
                System.out.println(dt);
            }
        }
        System.out.println(" ");

        // Now for CIFS

        // Create a new volume
        // temp = netAppFacade.listAggregates(null);
        // volumeName = "auto_bourne_cifs";
        // volumeSize = "102m";
        // volumeGuarantee = "none"; //Or, "file" or "volume"
        // if (netAppFacade.createFlexibleVolume(volumeName,
        // temp.get(0).getName(), false, null, volumeSize, null,
        // volumeGuarantee, false, null)) {
        // System.out.println("Created a new volume for CIFS: " + volumeName +
        // " within aggregate: " + temp.get(0).getName());
        // } else {
        // System.out.println("Volume creation for CIFS failed...");
        // }

        String fileShareName = "auto_bourneShare_cifs";
        String volumePath = "/vol/" + volumeName;
        System.out.println("Creating a CIFS file Share: ");
        if (netAppFacade.addCIFSShare(volumePath, fileShareName, fileShareName,
                0, null)) {
            System.out.println("created a CIFS file Share: " + fileShareName
                    + " successfully");
        } else {
            System.out.println("Failed to create a CIFS file Share: "
                    + fileShareName);

        }
        //
        // //List the contents of the sub-shares.
        // List<Map<String, String>> fileShares = netAppFacade.listCIFSShares("*");
        // for (Map<String, String> fs: fileShares) {
        // for (String key: fs.keySet()) {
        // System.out.println("Key: " + key + ", Value: " + fs.get(key));
        // }
        // }

        // st<String> volumes = netAppFacade.listVolumes();

        if (null != volumes) {
            for (int i = 0; i < volumes.size(); i++) {
                Collection<String> attrs = new ArrayList<String>();
                String volumeName2 = "xyz";
                attrs.add("name");
                attrs.add("size-total");
                attrs.add("size-used");
                ArrayList<String> arg0 = null;
                attrs.addAll(arg0);
                List<Map<String, String>> volumeAttributes = netAppFacade.listVolumeInfo(volumeName2, attrs);
                // boolean snapshotAttributes3 = netAppFacade.listSnapshot(volumeName2, "hello");
                String hello = "world";
            }
        }

        // //TODO: Change the attributes of CIFS
        // List<Map<String, String>> myAttrMap = netAppFacade.listCIFSShares(fileShareName);
        //
        // List<CifsAcl> aclList = netAppFacade.listCIFSAcls(fileShareName);
        // CifsAcl acl = new CifsAcl();
        // acl.setShareName(fileShareName);
        // acl.setAccess(CifsAccess.change);
        // acl.setUserName("everyone");
        //
        // //HashMap<String, String> attrs =
        // //netAppFacade.changeCIFSShare(fileShareName, attrs);
        // netAppFacade.setCIFSAcl(acl);
        // String hello = "world";
        // netAppFacade.changeCIFSShare(fileShareName, attrs)
    }
}
