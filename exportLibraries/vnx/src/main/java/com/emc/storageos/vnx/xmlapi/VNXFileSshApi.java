/*
 * Copyright (c) 2008-2012 EMC Corporation
 * All Rights Reserved
 */

package com.emc.storageos.vnx.xmlapi;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.StringTokenizer;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.jcraft.jsch.Channel;
import com.jcraft.jsch.ChannelExec;
import com.jcraft.jsch.JSch;
import com.jcraft.jsch.Session;

/**
 * Responsible for sending VNX CLI commands to the VNX File System using SSH.
 */

public class VNXFileSshApi {
    public static final String SERVER_EXPORT_CMD = "/nas/bin/server_export";
    public static final String SERVER_MOUNT_CMD = "/nas/bin/server_mount";
    public static final String SERVER_UNMOUNT_CMD = "/nas/bin/server_umount";
    public static final String SERVER_INFO_CMD = "/nas/bin/nas_server";
    public static final String SERVER_USER_CMD = "/nas/sbin/server_user";
    public static final String EXPORT = "EXPORT";
    public static final String VNX_CIFS = "cifs";
    public static final String NAS_FS = "/nas/bin/nas_fs";
    public static final String SHARE = "share";
    public static final String SERVER_MODEL = "/nas/sbin/model";

    private static final Logger _log = LoggerFactory.getLogger(VNXFileSshApi.class);

    private String _host;
    private String _userName;
    private String _password;

    // Amount of time in milliseconds to wait for a response
    private int _respDelay = 1000;

    private static final int BUFFER_SIZE = 1024;
    private static final int DEFAULT_PORT = 22;

    // TODO: change build files to be able to access FileShareExport.SecurityTypes.
    private enum SecurityTypes {
        sys, krb5, krb5i, krb5p
    }

    /**
     * Sets the host and user credentials for the VNX Device to communicate with.
     * 
     * @param host host name or ip address
     * @param user user name
     * @param password user password
     */
    public void setConnParams(String host, String user, String password) {
        _host = host;
        _userName = user;
        _password = password;
    }

    /**
     * Clear the connection parameters.
     */
    public void clearConnParams() {
        _host = null;
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

    private String formatCifsCmd(List<VNXFileExport> exports, String netBios) {
        VNXFileExport fileExp = exports.get(0);
        StringBuilder command = new StringBuilder();
        command.append(" -Protocol cifs -ignore ");

        String name = fileExp.getExportName();
        if (null != name && !name.isEmpty()) {
            command.append("-name ");
            command.append(name + " ");
        }

        boolean permExists = false;
        if (fileExp.getPermissions().equalsIgnoreCase("read")) {
            command.append("-option ro");
            permExists = true;
        }

        String maxUsers = fileExp.getMaxUsers();
        if (maxUsers != null && !maxUsers.equalsIgnoreCase("-1")) {
            if (permExists) {
                command.append(",");
            } else {
                command.append("-option ");
                permExists = true;
            }

            command.append("maxusr=");
            command.append(maxUsers);
        }

        if (null != netBios && netBios.length() > 0) {
            if (permExists) {
                command.append(",");
            } else {
                command.append("-option ");
            }
            command.append("netbios=");
            command.append("\"" + netBios + "\" ");
        }

        String comment = fileExp.getComment();
        if (null != comment && !comment.isEmpty()) {
            command.append(" " + "-comment ");
            command.append("\"" + comment + "\" ");
        }

        return command.toString();
    }

    private String formatNfsCmd(List<VNXFileExport> exports, Map<String, String> userInfo) {

        StringBuilder options = new StringBuilder();
        options.append(" -Protocol nfs -ignore -option ");

        String access = null;
        boolean append = false;
        boolean first = true;
        boolean rwOnly = false;
        String rwPerm = "rw";
        String accessPerm = "access";
        ArrayList<String> permissions = new ArrayList<String>();

        for (SecurityTypes secType : SecurityTypes.values()) {
            _log.debug("Processing security type: {}", secType.name());

            for (VNXFileExport exp : exports) {
                permissions.add(exp.getPermissions());
            }

            if (permissions.size() == 1 && permissions.get(0).equalsIgnoreCase(rwPerm)) {
                // Export contains just rw only.. so set it to access list
                rwOnly = true;
            }

            for (VNXFileExport exp : exports) {
                if (secType.name().equals(exp.getSecurityType())) {
                    _log.debug("Matching export with perms {}", exp.getPermissions());
                    if (append) {
                        options.append(",");
                    } else {
                        append = true;
                    }

                    String perms = exp.getPermissions();
                    if (first) {
                        options.append("sec=");
                        options.append(secType.name());
                        if (!perms.isEmpty()) {
                            options.append(":");
                        }

                        first = false;
                    }

                    if (!perms.isEmpty()) {
                        String translatedPerm = perms;
                        if (rwOnly) {
                            translatedPerm = accessPerm;
                        }
                        access = createAccessString(secType.name(),
                                translatedPerm,
                                exp.getClients(),
                                exp.getRootUserMapping(),
                                userInfo);

                        options.append(access);
                    }
                }
            }
            first = true;
            permissions.clear();
        }

        // if the command ends with -option meaning that
        // 1. We are removing all export rules
        // 2. VNX overwrites the export with the new command we execute
        // 3. no rules with -option results error in command execution
        // 4. -option should be taken out as part of command when no rules specified.
        // 5. when the command ends with -option -- we can treat this command is missing the rules.
        // 6. Hence -option shouldn't be the part of the command
        _log.info("Validating if requested to delete all export rules");
        if (options != null && options.toString() != null && options.toString().trim().endsWith("-option")) {
            String command = options.toString();
            command = command.replaceAll("-option", "");
            options.setLength(0);
            options.append(command);
        }

        String comment = exports.get(0).getComment();
        if (comment != null && !comment.isEmpty()) {
            options.append(" " + "-comment ").append("\"" + comment + "\" ");
        }
        return options.toString();
    }

    /**
     * Create the command string for removing a CIFS file share
     * 
     * @param dataMover data mover that contains the CIFS file share
     * @param fileShare name of the file share to remove.
     * @param netBios netbios used
     * @return formatted command string to be used by the VNX File CLI.
     */
    public String formatDeleteShareCmd(String dataMover, String fileShare, String netBios) {
        StringBuilder cmd = new StringBuilder();
        cmd.append(dataMover);
        cmd.append(" -unexport -name ");
        cmd.append(fileShare);
        if (netBios != null && netBios.length() > 0) {
            cmd.append(" -option netbios=");
            cmd.append("\"" + netBios + "\" ");
        }
        return cmd.toString();
    }

    /**
     * Create the command string for exporting a file system.
     * 
     * @param dataMover data mover that the export will reside on
     * @param exports contains file export details
     * @return formatted command required by the VNX CLI for file export
     */
    public String formatExportCmd(String dataMover, List<VNXFileExport> exports, Map<String, String> userInfo, String netBios) {

        // Verify that there is at least one entry in exports
        if (exports.isEmpty()) {
            _log.debug("There is no entry to export");
            return null;
        }

        String mountPoint = entryPathsDiffer(exports);
        // Verify that all export entries apply to the same file system or subdirectory
        if (mountPoint == null) {
            _log.debug("Single ssh API command is being applied to multiple paths.");
            return null;
        }

        // Verify that all export entries apply to the same file system or subdirectory
        String protocol = entryProtocolsDiffer(exports);
        if (protocol == null) {
            _log.debug("Single ssh API command is being applied to multiple protocols.");
            return null;
        }

        StringBuilder options = new StringBuilder();

        String result = null;

        if (protocol.equalsIgnoreCase(VNX_CIFS)) {
            result = formatCifsCmd(exports, netBios);
        } else {
            result = formatNfsCmd(exports, userInfo);
        }

        options.append(result);
        options.append(" ");

        StringBuilder cmd = new StringBuilder();
        cmd.append(dataMover);
        cmd.append(options.toString());

        cmd.append(mountPoint);

        return cmd.toString();
    }

    /**
     * Create the command string for deleting file system export.
     * 
     * @param dataMover data mover that the export will reside on
     * @param path path whose export will be deleted
     * @return formatted command required by the VNX CLI for file export
     */
    public String formatDeleteNfsExportCmd(String dataMover, String path) {

        StringBuilder cmd = new StringBuilder();
        cmd.append(" ");
        cmd.append(dataMover);
        cmd.append(" -unexport ");
        cmd.append(" ");
        cmd.append(path);
        return cmd.toString();
    }

    /**
     * Create the command string for deleting file system mount.
     * 
     * @param dataMover data mover that the export will reside on
     * @param path path whose export will be deleted
     * @param protocol protocol
     * @return formatted command required by the VNX CLI for file export
     */
    public String formatUnMountCmd(String dataMover, String path, String protocol) {

        // server_umount server_3 /testSaravRoot3

        StringBuilder cmd = new StringBuilder();
        cmd.append(" ");
        cmd.append(dataMover);
        cmd.append(" ");
        cmd.append("-perm");
        cmd.append(" ");
        cmd.append(path);
        return cmd.toString();
    }

    /**
     * Create the command string for create file system mount.
     * 
     * @param dataMover data mover that the export will reside on
     * @param fileSystem file system that is being mounted
     * @param path path whose export will be deleted
     * @return formatted command required by the VNX CLI for file export
     */
    public String formatMountCmd(String dataMover, String fileSystem, String path) {

        // server_mount server_3 testFS /testFS

        StringBuilder cmd = new StringBuilder();
        cmd.append(" ");
        cmd.append(dataMover);
        cmd.append(" ");
        cmd.append(fileSystem);
        cmd.append(" ");
        cmd.append(path);
        return cmd.toString();
    }

    private String entryPathsDiffer(List<VNXFileExport> exports) {
        HashSet<String> paths = new HashSet<>();
        for (VNXFileExport exp : exports) {
            paths.add(exp.getMountPoint());
        }
        if (paths.size() == 1) {
            return paths.iterator().next();
        }
        return null;
    }

    private String entryProtocolsDiffer(List<VNXFileExport> exports) {
        HashSet<String> protocols = new HashSet<>();
        for (VNXFileExport exp : exports) {
            protocols.add(exp.getProtocol());
        }
        if (protocols.size() == 1) {
            return protocols.iterator().next();
        }
        return null;
    }

    /**
     * Create an access string for VNX File exports.
     * 
     * @param security security method: sys, krb5, krb5i, or krb5p
     * @param perm access mode depending on security method: ro, rw=, ro=, root=, access=, etc.
     * @param clients list of endpoints to be exported to
     * @param anon mapping for an unknown or root user.
     * @return formatted access string to be used by the VNX CLI.
     */
    public String createAccessString(String security, String perm, List<String> clients, String anon, Map<String, String> userInfo) {
        StringBuilder access = new StringBuilder();

        if (!perm.isEmpty()) {
            if (perm.equals("ro")) {
                access.append(perm);
            }
            if (!clients.isEmpty()) {
                if (!perm.equals("ro")) {
                    access.append(perm);
                }
                access.append("=");
                Iterator it = clients.iterator();
                while (it.hasNext()) {
                    access.append(it.next());
                    if (it.hasNext()) {
                        access.append(":");
                    }
                }
            }
        }

        if (!anon.isEmpty() && security.equalsIgnoreCase(SecurityTypes.sys.name())) {
            if (!anon.equalsIgnoreCase("nobody")) {
                if (access.length() > 0) {
                    access.append(",anon=");
                } else {
                    access.append("anon=");
                }

                if (anon.equalsIgnoreCase("root")) {
                    access.append("0");
                } else {
                    try {
                        Integer.parseInt(anon);
                        access.append(anon);
                    } catch (NumberFormatException nfe) {
                        String uid = userInfo.get(anon);
                        if (uid != null && !uid.isEmpty()) {
                            access.append(uid);
                        } else {
                            // Illegal value for anon (not a UID or account name)
                            throw new IllegalArgumentException("Illegal Root User Mapping");
                        }
                    }
                }
            }
        }

        return access.toString();
    }

    /**
     * Executes a command on the VNX File CLI.
     * 
     * @param command command to execute on the VNX File CLI.
     * @param request payload for the command
     * @return result of executing the command.
     */
    public XMLApiResult executeSsh(String command, String request) {
        XMLApiResult result = new XMLApiResult();

        if ((_host == null) || (_userName == null) || (_password == null)) {
            _log.error("Invalid connection parameter");
            result.setCommandFailed();
            return result;
        }

        String cmd = "export NAS_DB=/nas;" + command + " " + request;
        _log.info("executeSsh: cmd: " + cmd);

        InputStream in = null;
        Session session = null;
        Channel channel = null;
        try {
            java.util.Properties config = new java.util.Properties();
            config.put("StrictHostKeyChecking", "no");
            JSch jsch = new JSch();
            session = jsch.getSession(_userName, _host, DEFAULT_PORT);
            session.setPassword(_password);
            session.setConfig(config);
            session.connect();

            channel = session.openChannel("exec");
            ((ChannelExec) channel).setCommand(cmd);
            channel.setInputStream(null);
            in = channel.getInputStream();
            channel.connect();

            byte[] tmp = new byte[BUFFER_SIZE];
            StringBuilder cmdResults = new StringBuilder();
            while (true) {
                while (in.available() > 0) {
                    int i = in.read(tmp, 0, BUFFER_SIZE);
                    if (i < 0) {
                        break;
                    }
                    cmdResults.append(new String(tmp, 0, i));
                }

                if (channel.isClosed()) {
                    _log.info("Ssh exit status: " + channel.getExitStatus());
                    result.setMessage(cmdResults.toString());

                    // Set the command result status.
                    if (channel.getExitStatus() == 0) {
                        StringTokenizer st = new StringTokenizer(cmdResults.toString());
                        if (st.hasMoreTokens()) {
                            st.nextToken();  // data mover name
                        }
                        if (!command.equalsIgnoreCase(SERVER_USER_CMD)) {
                            if (st.hasMoreTokens()) {
                                st.nextToken();
                            }
                        }
                        String res = "";
                        if (st.hasMoreTokens()) {
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
                    _log.error("VNX File executeSsh Communication thread interrupted for command: " + cmd, e);
                }
            }

            _log.info("executeSsh: Done");
        } catch (Exception e) {
            _log.error("VNX File executeSsh connection failed while attempting to execute: " + cmd, e);
            result.setCommandFailed();
            result.setMessage(e.getMessage());
        } finally {
            if (in != null) {
                try {
                    in.close();
                } catch (IOException ignored) {
                    _log.error("Exception occured while closing input stream due to ", ignored);
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

    public Map<String, String> getFsMountpathMap(String dmName) {
        Map<String, String> fsMountpathMap = new ConcurrentHashMap<String, String>();
        try {
            if (dmName == null) {
                return null;
            }
            XMLApiResult result = this.executeSsh(VNXFileSshApi.SERVER_MOUNT_CMD, dmName);
            // Parse message to get map
            String[] entries = result.getMessage().split("\n");
            for (String entry : entries) {
                String[] entryElements = entry.split(" ");
                if (entryElements.length > 2) {
                    String fileName = entry.split(" ")[0];
                    String path = entry.split(" ")[2];
                    if (path != null && (!path.startsWith("/root"))) {
                        fsMountpathMap.put(fileName, path);
                        _log.info("Adding File Name {} and Path {}", fileName, path);
                    } else {
                        _log.info("Skipping File Name {} and Path {}", fileName, path);
                    }
                }
            }
        } catch (Exception ex) {
            _log.error("VNX File getFsMountpathMap failed for Data Mover {} due to {}", dmName, ex);
        }
        return fsMountpathMap;
    }

    public Map<String, String> getVDMInterfaces(String vdmName) {
        Map<String, String> vdmIntfs = new ConcurrentHashMap<String, String>();
        try {
            // Prepare arguments for CLI command
            // nas_server -info -vdm vdm_3_a
            StringBuilder data = new StringBuilder();
            data.append(" -info -vdm ");
            data.append(vdmName);

            // Execute command
            XMLApiResult result = executeSsh(VNXFileSshApi.SERVER_INFO_CMD, data.toString());

            if (result.isCommandSuccess()) {
                // Parse message to get Interfaces and properties
                String[] propList = result.getMessage().split("[\n]");
                for (String prop : propList) {
                    if (prop != null && prop.startsWith(" interface")) {
                        _log.debug("Vdm Interface : " + prop);
                        String[] attrs = prop.split("=");
                        _log.debug("Vdm Interface : " + attrs[1]);
                        String[] intInfo = attrs[1].split(":");
                        String intName = intInfo[0].trim();
                        String capability = intInfo[1];
                        vdmIntfs.put(intName, capability);
                        _log.info("VDM interface {" + intName + "} - Capability -[" + capability + "]");
                    }
                }
            }
        } catch (Exception ex) {
            StringBuilder message = new StringBuilder();
            message.append("VNX File getVDMInterfaces failed for Data Mover" + vdmName);
            message.append(", due to {}");
            _log.error(message.toString(), ex);
        }

        return vdmIntfs;
    }

    public Map<String, Map<String, String>> getNFSExportsForPath(String dmName, String path) {

        Map<String, Map<String, String>> pathExportMap = new ConcurrentHashMap<String, Map<String, String>>();

        try {
            // Prepare arguments for CLI command
            StringBuilder data = new StringBuilder();
            data.append(dmName);
            data.append(" -list ");

            // Execute command
            XMLApiResult result = this.executeSsh(VNXFileSshApi.SERVER_EXPORT_CMD, data.toString());

            // Parse message to get export properties
            String[] propList = result.getMessage().split("[\n]");
            for (int i = 1; i < propList.length; i++) {
                String exp = propList[i];
                Map<String, String> fsExportInfoMap = new ConcurrentHashMap<String, String>();
                String expPath = "";
                if (exp.contains(path)) {
                    _log.info("Processing export path {} because it contains {}", exp, path);
                    String[] expList = exp.split("[ \n]");
                    // loose the double quotes from either ends
                    expPath = expList[1].substring(1, expList[1].length() - 1);
                    for (String prop : expList) {
                        String[] tempStr = prop.split("=");
                        if (tempStr.length > 1) {
                            String val = fsExportInfoMap.get(tempStr[0]);
                            if (val == null) {
                                fsExportInfoMap.put(tempStr[0], tempStr[1]);
                            } else {
                                fsExportInfoMap.put(tempStr[0], val + ":" + tempStr[1]);
                            }
                        }
                    }
                    pathExportMap.put(expPath, fsExportInfoMap);
                } else {
                    _log.info("Ignoring export path {} because it doesnt contain {}", exp, path);
                }
            }
        } catch (Exception ex) {
            StringBuilder message = new StringBuilder();
            message.append("VNX File getNFSExportsForPath failed for Data Mover" + dmName);
            message.append(", path " + path);
            message.append(", due to {}");
            _log.error(message.toString(), ex);
        }
        return pathExportMap;
    }

    public Map<String, Map<String, String>> getNFSExportsForPath(String dmName) {

        Map<String, Map<String, String>> pathExportMap = new ConcurrentHashMap<String, Map<String, String>>();

        try {
            // Prepare arguments for CLI command
            StringBuilder data = new StringBuilder();
            data.append(dmName);
            data.append(" -list ");

            // Execute command
            XMLApiResult result = this.executeSsh(VNXFileSshApi.SERVER_EXPORT_CMD, data.toString());

            // Parse message to get export properties
            String[] propList = result.getMessage().split("[\n]");
            if (propList == null || propList.length < 1) {
                // no exports found
                return pathExportMap;
            }
            for (int i = 1; i < propList.length; i++) {
                String exp = propList[i];
                Map<String, String> fsExportInfoMap = new ConcurrentHashMap<String, String>();
                String expPath = "";
                String[] expList = exp.split("[ \n]");
                // loose the double quotes from either ends
                expPath = expList[1].substring(1, expList[1].length() - 1);
                for (String prop : expList) {
                    String[] tempStr = prop.split("=");
                    if (tempStr.length > 1) {
                        String val = fsExportInfoMap.get(tempStr[0]);
                        if (val == null) {
                            fsExportInfoMap.put(tempStr[0], tempStr[1]);
                        } else {
                            fsExportInfoMap.put(tempStr[0], val + ":" + tempStr[1]);
                        }
                    }
                }
                pathExportMap.put(expPath, fsExportInfoMap);
            }
        } catch (Exception ex) {
            StringBuilder message = new StringBuilder();
            message.append("VNX File getNFSExportsForPath failed for Data Mover" + dmName);
            message.append(", due to {}");
            _log.error(message.toString(), ex);
        }
        return pathExportMap;
    }

    public Map<String, String> getFsExportInfo(String dmName, String path) {

        Map<String, String> fsExportInfoMap = new ConcurrentHashMap<String, String>();

        try {
            // Prepare arguments for CLI command
            StringBuilder data = new StringBuilder();
            data.append(dmName);
            data.append(" -list ");
            data.append(path);

            // Execute command
            XMLApiResult result = this.executeSsh(VNXFileSshApi.SERVER_EXPORT_CMD, data.toString());

            // Parse message to get export properties
            String[] propList = result.getMessage().split("[ \n]");
            for (String prop : propList) {
                String[] tempStr = prop.split("=");
                if (tempStr.length > 1) {
                    String val = fsExportInfoMap.get(tempStr[0]);
                    if (val == null) {
                        fsExportInfoMap.put(tempStr[0], tempStr[1]);
                    } else {
                        fsExportInfoMap.put(tempStr[0], val + ":" + tempStr[1]);
                    }
                }
            }
        } catch (Exception ex) {
            StringBuilder message = new StringBuilder();
            message.append("VNX File getFsExportInfo failed for Data Mover" + dmName);
            message.append(", path " + path);
            message.append(", due to {}");
            _log.error(message.toString(), ex);
        }

        return fsExportInfoMap;
    }

    public Map<String, Map<String, String>> getCIFSExportsForPath(String dmName) {

        Map<String, Map<String, String>> pathExportMap = new ConcurrentHashMap<String, Map<String, String>>();

        try {
            // Prepare arguments for CLI command
            StringBuilder data = new StringBuilder();
            data.append(dmName);
            data.append(" -Protocol cifs ");
            data.append(" -list ");

            // Execute command
            XMLApiResult result = this.executeSsh(VNXFileSshApi.SERVER_EXPORT_CMD, data.toString());

            // Parse message to get export properties
            String[] propList = result.getMessage().split("[\n]");
            if (propList == null || propList.length < 1) {
                // no exports found
                return pathExportMap;
            }
            for (int i = 1; i < propList.length; i++) {
                String exp = propList[i];
                Map<String, String> fsExportInfoMap = new ConcurrentHashMap<String, String>();
                String expPath = "";
                String[] expList = exp.split("[ \n]");
                // loose the double quotes from either ends
                // For CIFS exports - share path will be followed by share name
                if (expList[0].equalsIgnoreCase(SHARE)) {
                    expPath = expList[2].substring(1, expList[2].length() - 1);
                    String shareName = expList[1].substring(1, expList[1].length() - 1);
                    fsExportInfoMap.put(SHARE, shareName);
                } else {
                    continue;
                }

                for (String prop : expList) {
                    String[] tempStr = prop.split("=");
                    if (tempStr.length > 1) {
                        String val = fsExportInfoMap.get(tempStr[0]);
                        if (val == null) {
                            fsExportInfoMap.put(tempStr[0], tempStr[1]);
                        } else {
                            fsExportInfoMap.put(tempStr[0], val + ":" + tempStr[1]);
                        }
                    }
                }
                pathExportMap.put(expPath, fsExportInfoMap);
            }
        } catch (Exception ex) {
            StringBuilder message = new StringBuilder();
            message.append("VNX File getNFSExportsForPath failed for Data Mover" + dmName);
            message.append(", due to {}");
            _log.error(message.toString(), ex);
        }
        return pathExportMap;
    }

    public Map<String, String> getUserInfo(String dmName) {
        Map<String, String> userInfo = new ConcurrentHashMap<String, String>();
        try {
            // Prepare arguments for CLI command
            StringBuilder data = new StringBuilder();
            data.append(dmName);
            data.append(" -list ");

            // Execute command
            XMLApiResult result = this.executeSsh(VNXFileSshApi.SERVER_USER_CMD, data.toString());

            if (result.isCommandSuccess()) {
                // Parse message to get user properties
                String[] propList = result.getMessage().split("[ \n]");
                boolean firstRow = true;
                for (String prop : propList) {
                    _log.info("User Info : " + prop);
                    if (firstRow) {
                        firstRow = false;
                        continue;
                    }
                    // <UserAccount uid="117" gid="217" comment="Bourne Testing" md5="false"
                    // desPasswordState="locked" user="user_rss" mover="2"/>
                    String[] tempStr = prop.split(":");
                    if (tempStr.length > 1) {
                        String userName = tempStr[0];
                        String uid = tempStr[2];
                        userInfo.put("user", userName);
                        userInfo.put("uid", uid);
                        _log.info("User {} uuid {}", userName, uid);
                        break;
                    }
                }
            }
        } catch (Exception ex) {
            StringBuilder message = new StringBuilder();
            message.append("VNX File getUserInfo failed for Data Mover" + dmName);
            message.append(", due to {}");
            _log.error(message.toString(), ex);
        }

        return userInfo;
    }

    public String getModelInfo() {
        String modelStr = new String("VNX7500");

        try {
            // Prepare arguments for CLI command
            StringBuilder data = new StringBuilder();
            data.append("");

            // Execute command
            XMLApiResult result = this.executeSsh(VNXFileSshApi.SERVER_USER_CMD, data.toString());

            if (result.isCommandSuccess()) {
                // Parse message to get user properties
                String tmpModelStr = result.getMessage();
                if (tmpModelStr != null && tmpModelStr.startsWith("VNX")) {
                    modelStr = tmpModelStr;
                }
            }

        } catch (Exception ex) {
            StringBuilder message = new StringBuilder();
            message.append("VNX File getModel failed ");
            message.append(", due to {}");
            _log.error(message.toString(), ex);
        }

        return modelStr;
    }

    // nas_fs -name testSAPCliThinFS -type uxfs -create size=100M pool="clarsas_archive" -auto_extend yes -thin yes
    // -hwm 90% -max_size 10G
    // nas_fs -name testSAPCliThickFS -type uxfs -create size=100M pool="clarsas_archive" -auto_extend no -thin no
    public String formatCreateFS(String name, String type, String initialSizeInMB, String finalSizeInMB,
            String pool, String desc, boolean thin, String id) {
        StringBuilder cmd = new StringBuilder();
        cmd.append(" -name ");
        cmd.append(name);
        cmd.append(" -type ");
        cmd.append(type);
        cmd.append(" -create ");
        pool = "'" + pool + "'";
        if (thin) {
            cmd.append(" size=" + initialSizeInMB + "M"); // Defaut passed in MB
            cmd.append(" pool=" + pool);
            cmd.append(" -thin ");
            cmd.append("yes");
            cmd.append(" -auto_extend ");
            cmd.append("yes");
            cmd.append(" -hwm ");
            cmd.append("90%");
            cmd.append(" -max_size ");
            cmd.append(finalSizeInMB + "M");// Defaut passed in MB
        } else {
            cmd.append(" size=" + finalSizeInMB + "M"); // Defaut passed in MB
            cmd.append(" pool=" + pool);
            cmd.append(" -thin ");
            cmd.append("no");
            cmd.append(" -auto_extend ");
            cmd.append("no");
        }

        // only if id is not null and not empty
        if (id != null && !(id.isEmpty())) {
            cmd.append(" -o ");
            cmd.append("id=" + id);
        }
        // added for eNAS
        cmd.append(" -option ");
        cmd.append(" slice=y ");
        return cmd.toString();

        // nas_fs -name sebastian_test -type uxfs -create size=10M pool=tsi_pool1
        // log_type=common fast_clone_level=1 -auto_extend yes -thin yes -max_size 100M -o id=132
    }

    // nas_fs -size ThinFS
    public String getFSSizeInfo(String fsName) {

        String fsSize = "0";
        String[] fsInfoSizeList;

        try {
            // Prepare arguments for CLI command
            StringBuilder cmd = new StringBuilder();
            cmd.append(" -size ");
            cmd.append(fsName);

            // Execute command
            XMLApiResult fsInfoResult = this.executeSsh(VNXFileSshApi.NAS_FS,
                    cmd.toString());

            // Parse message to get file system size properties
            if (fsInfoResult.isCommandSuccess()) {
                String[] propList = fsInfoResult.getMessage().split("[\n]");
                if (propList == null || propList.length < 1) {
                    // no FS Size found
                    return fsSize;
                } else {
                    for (String prop : propList) {
                        if (fsInfoResult.getMessage().contains("volume:")) {
                            if (prop.startsWith("volume:")) {
                                fsInfoSizeList = prop.split(" ");
                                fsSize = fsInfoSizeList[3];
                                break;
                            } else {
                                continue;
                            }
                        } else {
                            fsInfoSizeList = prop.split(" ");
                            fsSize = fsInfoSizeList[2];
                            break;
                        }
                    }
                }
            }
        } catch (Exception ex) {
            StringBuilder message = new StringBuilder();
            message.append("VNX File getFsSizeInfo failed for file system"
                    + fsName);
            message.append(", due to {}");
            _log.error(message.toString(), ex);
        }

        return fsSize;
    }

    // Retry executeSsh Command
    public XMLApiResult executeSshRetry(String command, String request) {

        XMLApiResult reTryResult = new XMLApiResult();
        try {
            int maxRetry = 3;
            while (maxRetry > 0) {

                // Execute command
                reTryResult = this.executeSsh(command, request);
                String message = reTryResult.getMessage();
                if (reTryResult.isCommandSuccess()) {
                    // reTryResult successful
                    break;
                } else if (message != null
                        && !message.isEmpty()
                        && (message.contains("unable to acquire lock(s)") ||
                                message.contains("NAS_DB locked object is stale") ||
                                message.contains("Temporarily no Data Mover is available"))) {
                    try {
                        // Delaying execution since NAS_DB object is locked till
                        // current execution complete
                        TimeUnit.SECONDS.sleep(10);
                    } catch (InterruptedException e) {
                        _log.error(
                                "Exception occurred while delaying file system creation command due to {}",
                                e);
                    }
                    maxRetry--;
                } else {
                    // reTryResult failed
                    break;
                }
            }
        } catch (Exception ex) {
            StringBuilder message = new StringBuilder();
            message.append("VNX File executeSshReTry failed for create file system");
            message.append(", due to {}");
            _log.error(message.toString(), ex);
        }
        return reTryResult;
    }

}
