/*
 * Copyright (c) 2012-2015 iWave Software LLC
 * All Rights Reserved
 */
package com.iwave.ext.netapp;

import static org.junit.Assert.assertNotNull;

import java.util.List;
import java.util.Map;

import netapp.manage.NaElement;

import org.junit.BeforeClass;
import org.junit.Test;

import com.iwave.ext.netapp.model.DiskDetailInfo;
import com.iwave.ext.netapp.model.ExportsHostnameInfo;
import com.iwave.ext.netapp.model.ExportsRuleInfo;
import com.iwave.ext.netapp.model.Qtree;
import com.iwave.ext.netapp.model.Quota;
import com.iwave.ext.netapp.model.SecurityRuleInfo;
import com.emc.storageos.services.util.EnvConfig;


@SuppressWarnings({"findbugs:WMI_WRONG_MAP_ITERATOR"})
/*
 * Code change for iterator will be made in future release 
 */

public class MiscTests {

    static private NetAppFacade netAppFacade = null;
    static private Server server = null;
    private static String host = EnvConfig.get("sanity", "netapp.host");
    private static volatile String portNumber = EnvConfig.get("sanity", "netapp.port");
    private static volatile String userName = EnvConfig.get("sanity", "netapp.username");
    private static String password = EnvConfig.get("sanity", "netapp.password");
    
    @BeforeClass
    public static void setUpBeforeClass() throws Exception {
        netAppFacade = new NetAppFacade(host, Integer.parseInt(portNumber), userName, password, false);
        server = netAppFacade.server;
    }  
    
    @Test
    public void testGetFcWWNN() {
        String nodeName = netAppFacade.getFcWWNN();
        assertNotNull(nodeName);
        System.out.println("WWNN: " + nodeName);
    }
    
    @Test
    public void testDiskListInfo() {
        List<DiskDetailInfo> disks = netAppFacade.listDiskInfo(null, null);
        for (DiskDetailInfo disk : disks) {
            assertNotNull(disk.getName());
            System.out.println("Disk Name: " + disk.getName());
        }
    }
    
    @Test
    public void testListAggregates() {
        List<AggregateInfo> aggregates = netAppFacade.listAggregates(null);
        for (AggregateInfo aggregateInfo : aggregates) {
            assertNotNull(aggregateInfo.getName());
        }
    }
    
    @Test
    public void testSystemApiList() {
        NaElement elem = new NaElement("system-api-list");

        NaElement resultElem = server.invoke(elem);
        
        List<NaElement> systemApiList = resultElem.getChildByName("apis").getChildren();
        for (NaElement systemApiInfo : systemApiList) {
            System.out.println("API " + systemApiInfo.getChildContent("name") + " License: " + systemApiInfo.getChildContent("license"));
        }

    }
    
    @Test
    public void testSystemGetInfo() {
        NaElement result = server.invoke("system-get-info");
        NetAppUtils.output(result);        
    }
    
    @Test
    public void iscsiNodeGetName() {
        NaElement result = server.invoke("iscsi-node-get-name");
        NetAppUtils.output(result);        
    } 
    
    @Test
    public void testInvokeCliCommand() {
    	String[] args = new String[4];
    	args[0] = "qtree";
    	args[1] = "security";
    	args[2] = "/vol/scottfs";
    	args[3] = "unix";
    	
    	String result = netAppFacade.invokeCliCommand(args);
    	System.out.println("QTree Status: " + result);
    }
    
    @Test
    public void test2() {
//        NaElement result = server.invoke("nfs-exportfs-list-rules");
//        NetAppUtils.output(result);
        
        List<ExportsRuleInfo> bla = netAppFacade.listNFSExportRules(null);
        for (ExportsRuleInfo exportsRuleInfo : bla) {
            System.out.println("Pathname: " + exportsRuleInfo.getPathname());
            System.out.println("Actual Pathname: " + exportsRuleInfo.getActualPathname());
            for (SecurityRuleInfo securityRuleInfo : exportsRuleInfo.getSecurityRuleInfos()) {
                System.out.println(" -- anon: " + securityRuleInfo.getAnon());
                System.out.println(" -- nosuid: " + securityRuleInfo.getNosuid());
                System.out.println(" -- sec-flavor: " + securityRuleInfo.getSecFlavor());
                for (ExportsHostnameInfo exportsHostnameInfo : securityRuleInfo.getReadOnly()) {
                    System.out.println(" ---- ReadOnly: " + exportsHostnameInfo.getAllHosts() + " " + exportsHostnameInfo.getName() + " " + exportsHostnameInfo.getNegate());
                }
                for (ExportsHostnameInfo exportsHostnameInfo : securityRuleInfo.getReadWrite()) {
                    System.out.println(" ---- ReadWrite: " + exportsHostnameInfo.getAllHosts() + " " + exportsHostnameInfo.getName() + " " + exportsHostnameInfo.getNegate());
                }
                for (ExportsHostnameInfo exportsHostnameInfo : securityRuleInfo.getRoot()) {
                    System.out.println(" ---- Root: " + exportsHostnameInfo.getAllHosts() + " " + exportsHostnameInfo.getName() + " " + exportsHostnameInfo.getNegate());
                }                
            }
        }
    }       
    
    @Test
    public void test3() {
        List<Map<String,String>> bla = netAppFacade.listIscsiInterfaceInfo(null);
        for (Map<String,String> b : bla) {
            for (String key : b.keySet()) {
                System.out.println("iSCSI Interface : " + key + " : " + b.get(key));
            }
        }
    }
    
    @Test
    public void test4() {
        String nodeName = netAppFacade.getNodeName();
        System.out.println("NODE NAME: " + nodeName);
    }
    
    @Test
    public void test5() {
        List<Map<String,String>> bla = netAppFacade.listIscsiInitiatorInfo();
        for (Map<String,String> b : bla) {
            for (String key : b.keySet()) {
                System.out.println("iSCSI Initiator : " + key + " : " + b.get(key));
            }
        }
    }    

    @Test
    public void test6() {
        List<Map<String,String>> bla = netAppFacade.listWWNs(false);
        for (Map<String,String> b : bla) {
            System.out.println("WWN:");
            for (String key : b.keySet()) {
                System.out.println(key + " : " + b.get(key));
            }
        }
    }    
    
    @Test
    public void test7() {
        List<Map<String,String>> bla = netAppFacade.listIscsiPortalInfo();
        for (Map<String,String> b : bla) {
            System.out.println("iSCSI Portal:");
            for (String key : b.keySet()) {
                System.out.println("  " + key + " : " + b.get(key));
            }
        } 
    }
    
    @Test
    public void test8() {
        List<Quota> quotas = netAppFacade.listQuotas();
        for (Quota quota : quotas) {
            System.out.println("Quota:");
            System.out.println("  quotaTarget: " + quota.getQuotaTarget());
            System.out.println("  quotaType: " + quota.getQuotaType());
            System.out.println("  volume: " + quota.getVolume());
            System.out.println("  qtree: " + quota.getQtree());
            System.out.println("  diskUsed: " + quota.getDiskUsed());
            System.out.println("  diskLimit: " + quota.getDiskLimit());
            System.out.println("  softDiskLimit: " + quota.getSoftDiskLimit());
            System.out.println("  threshold: " + quota.getThreshold());
            System.out.println("  filesUsed: " + quota.getFilesUsed());
            System.out.println("  fileLimit: " + quota.getFileLimit());
            System.out.println("  softFileLimit: " + quota.getSoftFileLimit());
            System.out.println("  vfiler: " + quota.getVfiler());

        } 
    }    
    
    @Test
    public void test9() {
        List<Qtree> qtrees = netAppFacade.listQtrees();
        for (Qtree qtree : qtrees) {
            System.out.println("Qtree:");
            System.out.println("  id:" + qtree.getId());
            System.out.println("  oplocks:" + qtree.getOplocks());
            System.out.println("  owningVfiler:" + qtree.getOwningVfiler());
            System.out.println("  qtree:" + qtree.getQtree());
            System.out.println("  securityStyle:" + qtree.getSecurityStyle());
            System.out.println("  status:" + qtree.getStatus());
            System.out.println("  volume:" + qtree.getVolume());
        }
    }
    
}
