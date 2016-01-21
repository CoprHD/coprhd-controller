/*
 * Copyright (c) 2015 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.isilon.restapi;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.jcraft.jsch.Channel;
import com.jcraft.jsch.ChannelExec;
import com.jcraft.jsch.JSch;
import com.jcraft.jsch.Session;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.StringTokenizer;
import java.util.concurrent.ConcurrentHashMap;
/**
 * Responsible for sending Isilon Cluster CLI commands to the Isilon cluster using SSH.
 */
public class IsilonSshApi {
    
    private static final Logger _log = LoggerFactory.getLogger(IsilonSshApi.class);
    private String _host;
    private String _userName;
    private String _password;

    // Amount of time in milliseconds to wait for a response
    private int _respDelay = 1000;

    private static final int    BUFFER_SIZE    = 1024;
    private static final int    DEFAULT_PORT   = 22;

    public void setConnParams(String host, String user, String password) {
        _host      = host;
        _userName  = user;
        _password  = password;
    }

    /**
     * Clear the connection parameters.
     */
    public void clearConnParams() {
        _host     = null;
        _userName = null;
        _password = null;
    }

    /**
     *
     * @param delay time in milliseconds
     */
    public void setResponseDelay(int delay) {
        if (delay < 0) {
            _respDelay = 0;
            return;
        }
        _respDelay = delay;
    }

    /**
     * Executes a command on the Isilon CLI.
     *
     * @param command  command to execute on the Isilon CLI.
     * @param request  payload for the command
     * @return result of executing the command.
     */
    public IsilonXMLApiResult executeSsh(String command, String request) {
        IsilonXMLApiResult result = new IsilonXMLApiResult();

        if ((_host == null) || (_userName == null) || (_password == null)) {
            _log.error("Invalid connection parameter");
            result.setCommandFailed();
            return result;
        }

        String cmd = "isi " + command + " " + request;
        _log.info("executeSsh: cmd: " + cmd);

        InputStream in      = null;
        Session     session = null;
        Channel     channel = null;
        try{
            java.util.Properties config = new java.util.Properties();
            config.put("StrictHostKeyChecking", "no");
            JSch jsch = new JSch();
            session = jsch.getSession(_userName, _host, DEFAULT_PORT);
            session.setPassword(_password);
            session.setConfig(config);
            session.connect();

            channel = session.openChannel("exec");
            ((ChannelExec)channel).setCommand(cmd);
            channel.setInputStream(null);
            in=channel.getInputStream();
            channel.connect();
            byte[] tmp=new byte[BUFFER_SIZE];
            StringBuilder cmdResults = new StringBuilder();
            while(true){
                while(in.available()>0){
                    int i=in.read(tmp, 0, BUFFER_SIZE);
                    if(i<0)break;
                    cmdResults.append(new String(tmp, 0, i));
                }

                if(channel.isClosed()){
                    _log.info("Ssh exit status: " + channel.getExitStatus());
                    result.setMessage(cmdResults.toString());

                    // Set the command result status.
                    if (channel.getExitStatus() == 0) {
                        StringTokenizer st = new StringTokenizer(cmdResults.toString());
                        if(st.hasMoreTokens()) {
                            st.nextToken();  // data mover name
                        }

                        String res = "";
                        if(st.hasMoreTokens()) {
                            res = st.nextToken(); // contains status or result.
                        }
                        if (res.equalsIgnoreCase("done")) {
                            result.setCommandSuccess();
                        } else if (res.equalsIgnoreCase("error")) {
                            result.setCommandFailed();
                        } else {
                            result.setCommandSuccess();
                        }
                    } else {
                        result.setCommandFailed();
                    }

                    break;
                }

                try {
                    Thread.sleep(_respDelay);
                } catch (InterruptedException e) {
                    _log.error("VNX File executeSsh Communication thread interrupted for command: " + cmd,e);
                }
            }

            _log.info("executeSsh: Done");
        } catch (Exception e){
            _log.error("VNX File executeSsh connection failed while attempting to execute: " + cmd, e);
            result.setCommandFailed();
            result.setMessage(e.getMessage());
        }
        finally {
            if (in != null) {
                try {
                    in.close();
                } catch (IOException ignored) {
                }
            }

            if (channel != null) {
                channel.disconnect();
            }

            if (session != null) {
                session.disconnect();
            }
        }
        return result;
    }
    
    /**
     * get the network pool list
     * @return
     */
    public Map<String, List<String>> getNetworkPools() {
        String command = "networks list pools";
        String request = "-v";

        Map <String, List<String>> networkpoolMap = new ConcurrentHashMap<String, List<String>>();
        try {
            IsilonXMLApiResult result = this.executeSsh(command, request);
            // Parse message to get map
            String[] entries = result.getMessage().split("\n");
            String accessZone = "";
            for (String entry: entries) {
                String[] entryElements = entry.split(":");
                if (entryElements.length >=2) {
                    String key = entryElements[0].trim();
                    String values = entryElements[1].trim();
                    //first check for access zone
                    if(key.equalsIgnoreCase("Access Zone")) {
                        //get the access zone name
                        String[] entrySubElements = values.split(" ");
                        if(entrySubElements.length >= 2) {
                            accessZone = entrySubElements[0].trim();
                            List<String> accessZonesTemp = networkpoolMap.get(accessZone);
                            if (accessZonesTemp == null) {
                                accessZonesTemp = new ArrayList<>();
                                networkpoolMap.put(accessZone, accessZonesTemp);
                            }
                        }
                    }
                    //next sequence's check for zone and finally set access zone emtpy for next iteration
                    if(key.equalsIgnoreCase("Zone") && !accessZone.isEmpty()) {
                        List<String> accesszones = networkpoolMap.get(accessZone);
                        if(null != accesszones) {
                            accesszones.add(values);
                            networkpoolMap.put(accessZone, accesszones);
                            accessZone = "";
                        }
                    }
                }
            }
        } catch (Exception ex) {
            _log.error("Isilon getNetworkPools is failed {} due to {}", command, ex);
        }
        return networkpoolMap;
    }
    
    public Double getClusterSize() {
        String command = "storagepool nodepools";
        String request = "list -v";
        Double maxCapacity = 0.0;
        Map <String, String> clusterSizeMap = new ConcurrentHashMap<String, String>();
        try {
            IsilonXMLApiResult result = this.executeSsh(command, request);
            // Parse message to get map
            String[] entries = result.getMessage().split("\n");
            for (String entry: entries) {
                String[] entryElements = entry.split(":");
                if (entryElements.length >=2) {
                    String key = entryElements[0].trim();
                    String values = entryElements[1].trim();
                    clusterSizeMap.put(key, values);
                    _log.info("Adding File Name {} and Path {}", key, values);
                }
            }
            //calcuate max capacity
            String vhsValue = clusterSizeMap.get("Virtual Hot Spare Bytes");
            String totalBytes = clusterSizeMap.get("Total Bytes");

            Double dtotalBytes = getCapacityInGB(totalBytes);
            Double dvhsByes = getCapacityInGB(vhsValue);
            maxCapacity =  dtotalBytes - dvhsByes;
            return maxCapacity;

        } catch (Exception ex) {
            _log.error("Isilon cluster map is failed {} due to {}", command, ex);
        }
        return maxCapacity;
    }

    
    /**
     * storage capacity into gb
     * @param data
     * @return
     */
    public Double getCapacityInGB(final String data) {
        Double bytes = 0.0;
        char bitFormat = data.charAt(data.length()-1);
        Double totalBytes = Double.parseDouble(data.substring(0, data.length() - 1));
        if(bitFormat == 'P') {
            bytes = (totalBytes *1024)*1024;
        } else if (bitFormat == 'T') {
            bytes = totalBytes * 1024;
        } else if (bitFormat == 'G') {
            bytes = totalBytes;
        } else {
            bytes = 0.0;
        }
        return bytes;
    }
}
