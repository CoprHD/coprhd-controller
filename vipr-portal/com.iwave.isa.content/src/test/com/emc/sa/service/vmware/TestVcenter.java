/*
 * Copyright (c) 2017 Dell EMC Corporation
 * All Rights Reserved
 */
package com.emc.sa.service.vmware;

import java.net.MalformedURLException;
import java.net.URL;
import java.rmi.RemoteException;

import com.iwave.ext.vmware.VCenterAPI;
import com.vmware.vim25.DatastoreInfo;
import com.vmware.vim25.InvalidProperty;
import com.vmware.vim25.RuntimeFault;
import com.vmware.vim25.mo.Datastore;

/**
 * Floating test module that is helpful for diagnosing APi issues with vmware.
 * The code here isn't meant to be use for a unit test as-is.  It's more of a playground
 * to help developers figure out coding patterns quickly without changing code in the larger 
 * product.
 */
public class TestVcenter {

    // Are you using a simulator?
    public static boolean simulator = true;
    
    public static void main(String[] args) throws MalformedURLException, InvalidProperty, RuntimeFault, RemoteException {
        VCenterAPI vcenterAPI = getVcenterApi("lglw1045.lss.emc.com", "root", "vmware");
        try {
            Datastore ds = vcenterAPI.findDatastore("DC-Simulator-1", "ds-1");
            DatastoreInfo dsInfo = ds.getInfo();
            if (dsInfo != null) {
                return;
            }
        } catch (Exception ex) {
            ex.printStackTrace();
        }
    }

    public static VCenterAPI getVcenterApi(String hostname, String user, String password) throws MalformedURLException {
        // Physical vmware is 443.  Usually the simulator is on 7230
        URL url = new URL("https", hostname, simulator ? 7230 : 443, "/sdk");
        VCenterAPI vcenterAPI = new VCenterAPI(url);
        vcenterAPI.login(user, password);
        return vcenterAPI;
    }

}

