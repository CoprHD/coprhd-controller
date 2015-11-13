/*
 * Copyright (c) 2012-2015 iWave Software LLC
 * All Rights Reserved
 */
package com.iwave.ext.netappc;

import java.util.ArrayList;
import java.util.List;

import netapp.manage.NaElement;
import netapp.manage.NaServer;

import org.apache.log4j.Logger;

public class StorageVirtualMachine {
    private final Logger log = Logger.getLogger(getClass());

    private String name = "";
    private NaServer server = null;
    private static final String DATA_SVM = "data";
    private static final String FIBRE_CHANNEL_CONNECTIONS = "fcp";
    private static final String IP_CIFS_CONNECTIONS = "cifs";
    private static final String IP_NFS_CONNECTIONS = "nfs";

    public StorageVirtualMachine(NaServer server, String name) {
        this.name = name;
        this.server = server;
    }

    public List<StorageVirtualMachineInfo> listSVMs(boolean listAll) {
        ArrayList<StorageVirtualMachineInfo> svms = new ArrayList<StorageVirtualMachineInfo>();

        NaElement svmElem = new NaElement("vserver-get-iter");
        NaElement intfElem = new NaElement("net-interface-get-iter");
        String tag = null;
        if (!listAll) {
            log.info("listAll : " + listAll);
        }
        NaElement svmResult = null;
        NaElement intfResult = null;
        try {
            do {
                NaElement results = server.invokeElem(svmElem);
                tag = results.getChildContent("next-tag");
                svmResult = results.getChildByName("attributes-list");
                if (svmResult != null) {
                    for (NaElement svm : (List<NaElement>) svmResult.getChildren()) {
                        // TO DO: Right now filters out SVMs/Vservers other than data/cluster Vservers. If creation of SVMs are supported we
                        // need to handle admin and node Vservers as well. In that case, type of Vserver should be stored in ViPR db.
                        if (svm.getChildContent("vserver-type").equalsIgnoreCase(DATA_SVM)) {
                            StorageVirtualMachineInfo svmInfo = new StorageVirtualMachineInfo();
                            String name = svm.getChildContent("vserver-name");
                            svmInfo.setName(name);
                            svmInfo.setUuid(svm.getChildContent("uuid"));
                            svmInfo.setRootVolume(svm.getChildContent("root-volume"));
                            svms.add(svmInfo);
                            log.info("Found Data SVM : {}" + name);
                        }
                    }
                }
                if (tag != null && !tag.isEmpty()) {
                    svmElem = new NaElement("vserver-get-iter");
                    svmElem.addNewChild("tag", tag);
                    log.info("Updating the tag value as there are one or more svms available");
                }
            } while (tag != null && !tag.isEmpty());
        } catch (Exception e) {
            // If MultiStore not enabled, then this is the expected behavior.
            String msg = "No svm information returned from array.";
            log.info(msg);
            throw new NetAppCException(msg, e);
        }

        try {
            do {
                NaElement results = server.invokeElem(intfElem);
                tag = results.getChildContent("next-tag");
                intfResult = results.getChildByName("attributes-list");
                if (intfResult != null) {
                    for (StorageVirtualMachineInfo svmInfo : svms) {
                        List<SVMNetInfo> netInfo = new ArrayList<SVMNetInfo>();
                        for (NaElement vsnet : (List<NaElement>) intfResult.getChildren()) {
                            NaElement dataProtocols = vsnet.getChildByName("data-protocols");
                            boolean valid = false;
                            if (dataProtocols != null) {
                                for (NaElement dataProtocol : (List<NaElement>) dataProtocols.getChildren()) {
                                    if (dataProtocol != null) {
                                        String protocolValue = dataProtocol.getContent();
                                        // select only those port which support CIFS or NFS
                                        if (protocolValue.equalsIgnoreCase(IP_CIFS_CONNECTIONS) || protocolValue
                                                .equalsIgnoreCase(IP_NFS_CONNECTIONS)) {
                                            valid = true;
                                            break;
                                        }
                                    }
                                }
                            }
                            if (valid) {
                                if (svmInfo.getName().equalsIgnoreCase(vsnet.getChildContent("vserver"))) {
                                    SVMNetInfo svmNetInfo = new SVMNetInfo();
                                    svmNetInfo.setIpAddress(vsnet.getChildContent("address"));
                                    svmNetInfo.setNetInterface(vsnet.getChildContent("interface-name"));
                                    svmNetInfo.setNetMask(vsnet.getChildContent("netmask"));
                                    svmNetInfo.setRole(vsnet.getChildContent("role"));
                                    netInfo.add(svmNetInfo);
                                    log.info("Found vserver network interface: {}" + svmNetInfo.getNetInterface());
                                }
                            }
                        }

                        if (svmInfo.getInterfaces() == null) {
                            List<SVMNetInfo> interfaces = new ArrayList<SVMNetInfo>();
                            svmInfo.setInterfaces(interfaces);
                        }
                        svmInfo.getInterfaces().addAll(netInfo);
                    }
                }
                if (tag != null && !tag.isEmpty()) {
                    intfElem = new NaElement("net-interface-get-iter");
                    intfElem.addNewChild("tag", tag);
                    log.info("Updating the tag value as there are one or more network interfaces available");
                }
            } while (tag != null && !tag.isEmpty());
        } catch (Exception e) {
            String msg = "No network interface information returned from array.";
            log.info(msg);
            throw new NetAppCException(msg, e);
        }
        return svms;
    }
}
