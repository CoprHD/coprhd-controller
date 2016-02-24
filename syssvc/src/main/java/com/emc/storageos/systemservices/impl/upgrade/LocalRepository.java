/*
 * Copyright (c) 2014 EMC Corporation
 * All Rights Reserved
 */

package com.emc.storageos.systemservices.impl.upgrade;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.nio.charset.Charset;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermission;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

import org.apache.commons.lang.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.emc.storageos.coordinator.client.model.RepositoryInfo;
import com.emc.storageos.coordinator.client.model.SiteInfo;
import com.emc.storageos.coordinator.client.model.SoftwareVersion;
import com.emc.storageos.coordinator.client.model.PropertyInfoExt;
import com.emc.storageos.coordinator.client.service.PropertyInfoUtil;

import static com.emc.storageos.coordinator.client.model.Constants.*;

import com.emc.storageos.coordinator.exceptions.InvalidRepositoryInfoException;
import com.emc.storageos.coordinator.exceptions.InvalidSoftwareVersionException;
import com.emc.storageos.systemservices.exceptions.SyssvcException;
import com.emc.storageos.systemservices.exceptions.LocalRepositoryException;
import com.emc.storageos.services.util.Exec;
import com.emc.storageos.services.util.Strings;

public class LocalRepository {
    private static final Logger _log = LoggerFactory.getLogger(LocalRepository.class);

    private static LocalRepository _instance;

    public static LocalRepository getInstance() {
        synchronized (LocalRepository.class) {
            if (_instance == null) {
                _instance = new LocalRepository();
            }
        }
        return _instance;
    }

    private static final long _SYSTOOL_TIMEOUT = 120000;             // 2 min
    private static final int _SYSTOOL_DEVKIT_ERROR = 66;
    private static final int _SYSTOOL_SUCCESS = 0;

    private static final String _SYSTOOL_CMD = "/etc/systool";
    private static final String _SYSTOOL_LIST = "--list";
    private static final String _SYSTOOL_GET_DEFAULT = "--get-default";
    private static final String _SYSTOOL_SET_DEFAULT = "--set-default";
    private static final String _SYSTOOL_GET_IMAGE = "--get-image";
    private static final String _SYSTOOL_INSTALL = "--install";
    private static final String _SYSTOOL_REMOVE = "--remove";
    private static final String _SYSTOOL_SET_OPROPS = "--setoverrides";
    private static final String _SYSTOOL_GET_OPROPS = "--getoverrides";
    private static final String _SYSTOOL_SET_CONTROLLEROVFPROPS = "--set-controller-ovfprops";
    private static final String _SYSTOOL_GET_CONTROLLEROVFPROPS = "--get-controller-ovfprops";
    private static final String _SYSTOOL_GET_VDC_PROPS = "--getvdcprops";
    private static final String _SYSTOOL_SET_VDC_PROPS = "--setvdcprops";
    private static final String _SYSTOOL_GET_SSL_PROPS = "--getsslprops";
    private static final String _SYSTOOL_SET_SSL_PROPS = "--setsslprops";
    private static final String _SYSTOOL_SET_DATA_REVISION = "--set-data-revision";
    private static final String _SYSTOOL_GET_DATA_REVISION = "--get-data-revision";

    private static final String _SYSTOOL_REBOOT = "--reboot";
    private static final String _SYSTOOL_POWEROFF = "--poweroff";
    private static final String _SYSTOOL_RECONFIG = "--reconfig";
    private static final String _SYSTOOL_RECONFIG_PROPS = "--reconfig-props";
    private static final String _SYSTOOL_RESTART = "--restart";
    private static final String _SYSTOOL_STOP = "--stop";
    private static final String _SYSTOOL_RELOAD = "--reload";
    private static final String _SYSTOOL_IS_APPLIANCE = "--is-appliance";
    private static final String _SYSTOOL_RECONFIG_COORDINATOR = "--reconfig-coordinator";
    private static final String _SYSTOOL_REMOTE_SYSTOOL = "--remote-systool";
    private static final String _SYSTOOL_RESTART_COORDINATOR = "--restart-coordinator";

    private static final String _IPSECTOOL_CMD = "/etc/ipsectool";
    private static final String MASK_IPSEC_KEY_PATTERN = "ipsec_key=.*?\\n";

    // inject value from spring config.
    private String cmdZkutils;

    public void setCmdZkutils(String cmdZkutils) {
        this.cmdZkutils = cmdZkutils;
    }

    /***
     * 
     * @return RepositoryState
     * @throws InvalidRepositoryInfoException
     * 
     */
    public RepositoryInfo getRepositoryInfo() throws LocalRepositoryException, InvalidRepositoryInfoException {
        final String prefix = "getRepositoryState(): ";
        _log.debug(prefix);

        final String[] cmd1 = { _SYSTOOL_CMD, _SYSTOOL_LIST };
        List<SoftwareVersion> versions =
                toSoftwareVersionList(prefix + _SYSTOOL_LIST, exec(prefix, cmd1));

        final String[] cmd2 = { _SYSTOOL_CMD, _SYSTOOL_GET_DEFAULT };
        final SoftwareVersion current =
                toSoftwareVersionList(prefix + _SYSTOOL_GET_DEFAULT, exec(prefix, cmd2)).get(0);

        _log.debug(prefix + "current={} versions={}", current, Strings.repr(versions));
        return new RepositoryInfo(current, versions);
    }

    /**
     * Change the bootloader default to the new version and reboot.
     * 
     * @param version - the new target version
     */
    public void setCurrentVersion(final SoftwareVersion version) throws LocalRepositoryException {
        final String prefix = "setCurrentVersion(): to=" + version + ": ";
        _log.debug(prefix);
        final String[] cmd = { _SYSTOOL_CMD, _SYSTOOL_SET_DEFAULT, version.toString() };
        exec(prefix, cmd);
        _log.info(prefix + "Success");
    }

    /**
     * Install an image file into the local repository
     * 
     * @param file to image
     * @return installed image name
     */
    public String installImage(final File file) throws LocalRepositoryException {
        final String prefix = "installImage path=" + file + ": ";
        _log.debug(prefix);
        final String[] cmd = { _SYSTOOL_CMD, _SYSTOOL_INSTALL, file.getPath() };
        final String[] images = exec(prefix, cmd);

        if (images == null) {
            throw SyssvcException.syssvcExceptions.localRepoError(prefix + "Internal error. Null output");
        } else if (images.length != 1) {
            throw SyssvcException.syssvcExceptions.localRepoError(prefix + "Internal error. No results.");
        }

        _log.info(prefix + "Success!");
        return images[0];
    }

    /**
     * Remove a version from the local repository
     * 
     * @param version to remove
     */
    public void removeVersion(final SoftwareVersion version) throws LocalRepositoryException {
        final String prefix = "removeVersion=" + version + ": ";
        _log.debug(prefix);
        final String[] cmd = { _SYSTOOL_CMD, _SYSTOOL_REMOVE, version.toString() };
        exec(prefix, cmd);
        _log.info(prefix + "Success!");
    }

    /***
     * Open an InputStream to a local image
     * 
     * @param version - SoftwareVersion of the image
     * 
     * @return an opened InputStream (FileInputStream)
     */
    public InputStream getImageInputStream(SoftwareVersion version) throws LocalRepositoryException {
        final String prefix = "getImageInputStream(): version=" + version + ": ";
        final String[] cmd = { _SYSTOOL_CMD, _SYSTOOL_GET_IMAGE, version.toString() };
        final String[] images = exec(prefix, cmd);
        _log.debug(prefix + "images=" + Strings.repr(images));

        if (images == null) {
            throw SyssvcException.syssvcExceptions.localRepoError(prefix + "Internal error. Null output");
        } else if (images.length == 0) {
            throw SyssvcException.syssvcExceptions.localRepoError(prefix + "Internal error. No results.");
        }

        try {
            return new FileInputStream(images[0]);
        } catch (Exception e) {
            throw SyssvcException.syssvcExceptions.localRepoError(prefix + e);
        }
    }

    /***
     * 
     * @return PropertyInfo
     */
    public PropertyInfoExt getOverrideProperties() throws LocalRepositoryException {
        final String prefix = "getOverrideProperties(): ";
        _log.debug(prefix);
        final String[] cmd = { _SYSTOOL_CMD, _SYSTOOL_GET_OPROPS };
        PropertyInfoExt overrides = new PropertyInfoExt(exec(prefix, cmd));
        _log.debug(prefix + "properties={}", Strings.repr(overrides));
        return overrides;
    }

    /***
     * Update property
     * 
     * @param state
     * @throws LocalRepositoryException
     */
    public void setOverrideProperties(PropertyInfoExt state) throws LocalRepositoryException {
        final String prefix = "setOverrideProperties(): to=" + state;
        _log.debug(prefix);

        final Path tmpFilePath = FileSystems.getDefault().getPath(TMP_CONFIG_USER_CHANGED_PROPS_PATH);
        createTmpFile(tmpFilePath, state.toString(false), prefix);

        try {
            final String[] cmd = { _SYSTOOL_CMD, _SYSTOOL_SET_OPROPS, TMP_CONFIG_USER_CHANGED_PROPS_PATH };
            exec(prefix, cmd);
            _log.info(prefix + "Success");
        } finally {
            cleanupTmpFile(tmpFilePath);
        }
    }

    /***
     * Update property
     * 
     * @param state
     * @throws LocalRepositoryException
     */
    public void setVdcPropertyInfo(PropertyInfoExt state) throws LocalRepositoryException {
        final String prefix = "setVdcPropertyInfo(): to=" + state;
        _log.debug(prefix);

        final Path tmpFilePath = FileSystems.getDefault().getPath(VDC_PROPERTY_DIR);
        createTmpFile(tmpFilePath, state.toString(false), prefix);

        try {
            final String[] cmd = { _SYSTOOL_CMD, _SYSTOOL_SET_VDC_PROPS, VDC_PROPERTY_DIR };
            exec(prefix, cmd);
            _log.info(prefix + "Success");
        } finally {
            cleanupTmpFile(tmpFilePath);
        }
    }

    /***
     * 
     * @return PropertyInfo
     */
    public PropertyInfoExt getVdcPropertyInfo() throws LocalRepositoryException {
        final String prefix = "getVdcPropertyInfo(): ";
        _log.debug(prefix);

        final String[] cmd1 = { _SYSTOOL_CMD, _SYSTOOL_GET_VDC_PROPS };
        String[] props = exec(prefix, MASK_IPSEC_KEY_PATTERN, cmd1);

        // remove below redundant debug info, the props content already print
        // in above exec method.
        // _log.debug(prefix + "properties={}", Strings.repr(props));
        return new PropertyInfoExt(props);
    }

    /***
     * Update SSL property
     * 
     * @param state
     * @throws LocalRepositoryException
     */
    public void setSslPropertyInfo(PropertyInfoExt state) throws LocalRepositoryException {
        final String prefix = "setSslPropertyInfo(): to=" + state;
        _log.debug(prefix);

        final Path tmpFilePath = FileSystems.getDefault().getPath(SSL_PROPERTY_TMP);
        createTmpFile(tmpFilePath, state.toString(false), prefix);

        try {
            final String[] cmd = { _SYSTOOL_CMD, _SYSTOOL_SET_SSL_PROPS, SSL_PROPERTY_TMP };
            exec(prefix, cmd);
            _log.info(prefix + "Success");
        } finally {
            cleanupTmpFile(tmpFilePath);
        }
    }

    /***
     * 
     * @return PropertyInfo
     */
    public PropertyInfoExt getSslPropertyInfo() throws LocalRepositoryException {
        final String prefix = "getSslPropertyInfo(): ";
        _log.debug(prefix);

        final String[] cmd1 = { _SYSTOOL_CMD, _SYSTOOL_GET_SSL_PROPS };
        String[] props = exec(prefix, cmd1);

        _log.debug(prefix + "properties={}", Strings.repr(props));
        return new PropertyInfoExt(props);
    }

    /**
     * Get controller ovf properties
     * 
     * @return PropertyInfo controller ovf properties
     */
    public PropertyInfoExt getControllerOvfProperties() throws LocalRepositoryException {
        final String prefix = "getControllerOvfProperties(): ";
        _log.debug(prefix);
        final String[] cmd = { _SYSTOOL_CMD, _SYSTOOL_GET_CONTROLLEROVFPROPS };
        PropertyInfoExt overrides = new PropertyInfoExt(exec(prefix, cmd));
        _log.debug(prefix + "properties={}", Strings.repr(overrides));
        return overrides;
    }

    /***
     * Update controller ovf properties
     * 
     * @param state controller ovf properties to update
     * @throws LocalRepositoryException
     */
    public void setControllerOvfProperties(PropertyInfoExt state) throws LocalRepositoryException {
        final String prefix = "setControllerOvfProperties(): to=" + state;
        _log.debug(prefix);

        final Path tmpFilePath = FileSystems.getDefault().getPath(TMP_CONFIG_CONTROLLER_OVF_PROPS_PATH);
        createTmpFile(tmpFilePath, state.toString(false), prefix);

        try {
            final String[] cmd = { _SYSTOOL_CMD, _SYSTOOL_SET_CONTROLLEROVFPROPS, TMP_CONFIG_CONTROLLER_OVF_PROPS_PATH };
            exec(prefix, cmd);
            _log.info(prefix + "Success");
        } finally {
            cleanupTmpFile(tmpFilePath);
        }
    }

    /**
     * Reboot
     * throw LocalRepositoryException if exit value = 66 or exit value != 0
     * also throw LocalRepositoryException if not exited normally
     * 
     * @throws LocalRepositoryException
     */
    public void reboot() throws LocalRepositoryException {
        final String prefix = "reboot(): ";
        _log.debug(prefix);
        final String[] cmd = { _SYSTOOL_CMD, _SYSTOOL_REBOOT };
        final Exec.Result result = Exec.sudo(_SYSTOOL_TIMEOUT, cmd);
        checkFailure(result, prefix);
    }

    /**
     * Poweroff
     * throw LocalRepositoryException if exit value = 66 or exit value != 0
     * also throw LocalRepositoryException if not exited normally
     * 
     * @throws LocalRepositoryException
     */
    public void poweroff() throws LocalRepositoryException {
        final String prefix = "poweroff(): ";
        _log.debug(prefix);
        final String[] cmd = { _SYSTOOL_CMD, _SYSTOOL_POWEROFF };
        final Exec.Result result = Exec.sudo(_SYSTOOL_TIMEOUT, cmd);
        checkFailure(result, prefix);
    }

    /**
     * Reconfig
     * throw LocalRepositoryException if exit value = 66 or exit value != 0
     * also throw LocalRepositoryException if not exited normally
     * 
     * @throws LocalRepositoryException
     */
    public void reconfig() throws LocalRepositoryException {
        final String prefix = "reconfig(): ";
        _log.debug(prefix);
        final String[] cmd = { _SYSTOOL_CMD, _SYSTOOL_RECONFIG };
        final Exec.Result result = Exec.sudo(_SYSTOOL_TIMEOUT, cmd);
        checkFailure(result, prefix);
    }

    /**
     * Reconfig properties associated with a list of tags
     * 
     * @param propertyTags space separated list of property tags
     * @throws LocalRepositoryException
     */
    public void reconfigProperties(String propertyTags) throws LocalRepositoryException {
        final String prefix = String.format("reconfigProperty(%s): ", propertyTags);
        _log.debug(prefix);
        final String[] cmd = { _SYSTOOL_CMD, _SYSTOOL_RECONFIG_PROPS, propertyTags };
        final Exec.Result result = Exec.sudo(_SYSTOOL_TIMEOUT, cmd);
        checkFailure(result, prefix);
    }

    /**
     * Reconfig remote coordinatorsvc to observer(default mode), or pariticpant(when active site is down)
     * For DR standby site only.
     *
     * @param nodeId
     * @param type
     * @throws LocalRepositoryException
     */
    public void remoteReconfigCoordinator(String nodeId,String type) throws LocalRepositoryException {
        final String prefix = String.format("reconfigCoordinator(%s): on %s", type,nodeId);
        _log.debug(prefix);
        final String[] cmd = { _SYSTOOL_CMD, _SYSTOOL_REMOTE_SYSTOOL, nodeId, _SYSTOOL_RECONFIG_COORDINATOR, type };
        final Exec.Result result = Exec.sudo(_SYSTOOL_TIMEOUT, cmd);
        checkFailure(result, prefix);
    }

    /**
     * Restart a service on remote node
     *
     * @param nodeId
     * @throws LocalRepositoryException
     */
    public void remoteRestartCoordinator(String nodeId, String type) throws LocalRepositoryException {
        final String prefix = String.format("restart(): type=%s on %s", type,nodeId);
        _log.debug(prefix);

        final String[] cmd = { _SYSTOOL_CMD, _SYSTOOL_REMOTE_SYSTOOL, nodeId, _SYSTOOL_RESTART_COORDINATOR, type };
        final Exec.Result result = Exec.sudo(_SYSTOOL_TIMEOUT, cmd);
        checkFailure(result, prefix);
    }

    /**
     * Restart a service
     * 
     * @param serviceName service name
     * @throws LocalRepositoryException
     */
    public void restart(final String serviceName) throws LocalRepositoryException {
        final String prefix = "restart(): serviceName=" + serviceName + " ";
        _log.debug(prefix);

        final String[] cmd = { _SYSTOOL_CMD, _SYSTOOL_RESTART, serviceName };
        final Exec.Result result = Exec.sudo(_SYSTOOL_TIMEOUT, cmd);
        checkFailure(result, prefix);
    }
    
    /**
     * Stop a service
     * 
     * @param serviceName service name
     * @throws LocalRepositoryException
     */
    public void stop(final String serviceName) throws LocalRepositoryException {
        final String prefix = "stop(): serviceName=" + serviceName + " ";
        _log.debug(prefix);

        final String[] cmd = { _SYSTOOL_CMD, _SYSTOOL_STOP, serviceName };
        final Exec.Result result = Exec.sudo(_SYSTOOL_TIMEOUT, cmd);
        checkFailure(result, prefix);
    }

    /**
     * Notify a service to reload configs after /etc/genconfig regenerates them.
     * The notification is done via systool since the service is not owned by storageos.
     * 
     * @throws LocalRepositoryException
     */
    public void reload(final String svcName) throws LocalRepositoryException {
        final String prefix = String.format("reload %s(): ", svcName);
        _log.debug(prefix);

        final String[] cmd = { _SYSTOOL_CMD, _SYSTOOL_RELOAD, svcName };
        final Exec.Result result = Exec.sudo(_SYSTOOL_TIMEOUT, cmd);
        checkFailure(result, prefix);
    }

    /**
     * Check if the current deployment is an appliance
     * 
     * @return true or false
     */
    public boolean isValidRepository() throws LocalRepositoryException {
        final String prefix = "isValidRepository: ";
        _log.debug(prefix);
        final String[] cmd = { _SYSTOOL_CMD, _SYSTOOL_IS_APPLIANCE };
        final String[] ret = exec(prefix, cmd);

        if (ret == null) {
            throw SyssvcException.syssvcExceptions.localRepoError(prefix + "Internal error. Null output");
        } else if (ret.length != 1) {
            throw SyssvcException.syssvcExceptions.localRepoError(prefix + "Internal error. No results.");
        }

        _log.info(prefix + ret[0]);
        return Boolean.valueOf(ret[0]);
    }

    /***
     * Update data revision property
     *
     * @param revisionTag
     * @param committed 
     * @throws LocalRepositoryException
     */
    public void setDataRevision(String revisionTag, boolean committed) throws LocalRepositoryException {
        final String prefix = String.format("setDataRevisionTag(): to=%s committed=%s" , revisionTag, committed);
        _log.debug(prefix);

        final Path tmpFilePath = FileSystems.getDefault().getPath(DATA_REVISION_TMP);
        StringBuilder s = new StringBuilder();
        s.append(KEY_DATA_REVISION);
        s.append(PropertyInfoExt.ENCODING_EQUAL);
        s.append(revisionTag == null ? "" : revisionTag);
        s.append(PropertyInfoExt.ENCODING_NEWLINE);
        s.append(KEY_DATA_REVISION_COMMITTED);
        s.append(PropertyInfoExt.ENCODING_EQUAL);
        s.append(String.valueOf(committed));
        createTmpFile(tmpFilePath, s.toString(), prefix);

        try {
            final String[] cmd = { _SYSTOOL_CMD, _SYSTOOL_SET_DATA_REVISION, DATA_REVISION_TMP };
            exec(prefix, cmd);
            _log.info(prefix + " Success");
        } finally {
            cleanupTmpFile(tmpFilePath);
        }
    }

    /***
     * Get data revision from disk
     * 
     * @return DataRevisonTag
     */
    public String getDataRevision() throws LocalRepositoryException {
        final String prefix = "getDataRevision(): ";
        _log.debug(prefix);

        final String[] cmd1 = { _SYSTOOL_CMD, _SYSTOOL_GET_DATA_REVISION };
        String[] props = exec(prefix, cmd1);

        _log.debug(prefix + "properties={}", Strings.repr(props));
        Map<String, String> map = PropertyInfoUtil.splitKeyValue(props);
        String revision = map.get(KEY_DATA_REVISION);
        String committed = map.get(KEY_DATA_REVISION_COMMITTED);
        if (committed != null && Boolean.valueOf(committed)) {
            return revision;
        }
        return SiteInfo.DEFAULT_TARGET_VERSION;
    }

    /**
     * check ipsec connections between local machine and other nodes in vipr
     *
     * @return ips don't have ipsec connection with local machine
     * @throws LocalRepositoryException
     */
    public String[] checkIpsecConnection() throws LocalRepositoryException {
        final String prefix = "checkIpsecConnection(): ";
        _log.debug(prefix);

        final String[] cmd = { _IPSECTOOL_CMD, IPSEC_CHECK_CONNECTION };
        String[] ips = exec(prefix, cmd);

        _log.debug(prefix + "ips without ipsec connection: ", Strings.repr(ips));
        return ips;
    }

    /**
     * get ipsec properties from specified node
     *
     * @param ip
     * @return map of ipsec related properties: VDC_CONFIG_VERSION and IPSEC_KEY
     */
    public Map<String, String> getIpsecProperties(String ip) throws LocalRepositoryException {
        final String prefix = "getIpsecPropertiesFromRemoteNode(): ";
        _log.debug(prefix);

        final String[] cmd = { _IPSECTOOL_CMD, IPSEC_GET_PROPS, ip };
        String[] props = exec(prefix, MASK_IPSEC_KEY_PATTERN, cmd);

        return PropertyInfoUtil.splitKeyValue(props);
    }

    /**
     * write given ipsec key to local file system.
     *
     * @param ipsecKey
     * @throws LocalRepositoryException
     */
    public void syncIpsecKeyToLocal(String ipsecKey) throws LocalRepositoryException {
        final String prefix = "syncIpsecKeyToLocal(): ";
        _log.debug(prefix);

        final String[] cmd = { _IPSECTOOL_CMD, IPSEC_SYNC_KEY, ipsecKey };
        exec(prefix, cmd);
        _log.info(prefix + "Success!");
    }

    /**
     * write given ipsec state to local file system.
     *
     * @param ipsecStatus
     * @throws LocalRepositoryException
     */
    public void syncIpsecStatusToLocal(String ipsecStatus) throws LocalRepositoryException {
        final String prefix = "syncIpsecStateToLocal(): ";
        _log.debug(prefix);

        final String[] cmd = { _IPSECTOOL_CMD, IPSEC_SYNC_STATUS, ipsecStatus };
        exec(prefix, cmd);
        _log.info(prefix + "Success!");
    }

    /**
     * Reconfig local coordinatorsvc to observer(default mode), or pariticpant(when active site is down)
     * For DR standby site only.  
     * 
     * @param type
     * @throws LocalRepositoryException
     */
    public void reconfigCoordinator(String type) throws LocalRepositoryException {
        final String prefix = String.format("reconfigCoordinator(%s): ", type);
        _log.debug(prefix);
        final String[] cmd = { _SYSTOOL_CMD, _SYSTOOL_RECONFIG_COORDINATOR, type };
        final Exec.Result result = Exec.sudo(_SYSTOOL_TIMEOUT, cmd);
        checkFailure(result, prefix);
    }
    
    /**
     * Reset coordinator and restart services on standby site. It applies on DR standby site only
     * 
     * @throws LocalRepositoryException
     */
    public void restartCoordinator(String type) throws LocalRepositoryException {
        final String prefix = String.format("resetCoordinator (%s): ", type);
        _log.debug(prefix);

        final String[] cmd = { _SYSTOOL_CMD, _SYSTOOL_RESTART_COORDINATOR, type};
        final Exec.Result result = Exec.sudo(_SYSTOOL_TIMEOUT, cmd);
        checkFailure(result, prefix);
    }

    /**
     * Common method checking exec execution failure
     * 
     * @param result Exec execution result
     * @param prefix prefix string
     * @throws LocalRepositoryException
     */
    private void checkFailure(Exec.Result result, String prefix) throws LocalRepositoryException {
        if (!result.exitedNormally() || result.getExitValue() != _SYSTOOL_SUCCESS) {
            _log.info(prefix + " failed. Result exit value: " + result.getExitValue());
            if (result.getExitValue() == _SYSTOOL_DEVKIT_ERROR) {
                throw SyssvcException.syssvcExceptions.localRepoError("Command failed since executed not on an appliance");
            } else {
                throw SyssvcException.syssvcExceptions.localRepoError(prefix + "Command failed: " + result);
            }
        }

        _log.info(prefix + "Success!");
    }



    private static String[] exec(final String prefix, String[] cmd) throws LocalRepositoryException {
        return exec(prefix, null, cmd);
    }

    private static String[] exec(final String prefix, String outputMaskPatternStr, String[] cmd) throws LocalRepositoryException {
        Pattern maskFilter = null;
        if (!StringUtils.isEmpty(outputMaskPatternStr)) {
            maskFilter = Pattern.compile(outputMaskPatternStr);
        }

        final Exec.Result result = Exec.sudo(_SYSTOOL_TIMEOUT, maskFilter, cmd);
        if (!result.exitedNormally() || result.getExitValue() != 0) {
            _log.info(prefix + "Command failed. Result exit value: " + result.getExitValue());
            throw SyssvcException.syssvcExceptions.localRepoError(prefix + "Command failed: " + result);
        }

        return result.getStdOutput().split(LINE_DELIMITER);
    }

    private static List<SoftwareVersion> toSoftwareVersionList(final String prefix, String[] strings) throws LocalRepositoryException {
        if (strings == null) {
            throw SyssvcException.syssvcExceptions.localRepoError(prefix + "Internal error. Null list.");
        } else if (strings.length == 0) {
            throw SyssvcException.syssvcExceptions.localRepoError(prefix + "No versions found.");
        }

        List<SoftwareVersion> versions = new ArrayList<SoftwareVersion>(strings.length);
        for (String s : strings) {
            try {
                versions.add(new SoftwareVersion(s));
            } catch (InvalidSoftwareVersionException e) {
                _log.error("{}. Skipping", e);
            }
        }

        if (versions.isEmpty()) {
            throw SyssvcException.syssvcExceptions.localRepoError(prefix + "No valid versions found.");
        }

        return versions;
    }

    private void createTmpFile(Path filePath, String content, String exceptionPrefix) {
        try (BufferedWriter out = Files.newBufferedWriter(filePath, Charset.defaultCharset())) {
            Set<PosixFilePermission> permissions = new HashSet<>();
            permissions.add(PosixFilePermission.OWNER_READ);
            permissions.add(PosixFilePermission.OWNER_WRITE);
            Files.setPosixFilePermissions(filePath, permissions);
            out.write(content);
        } catch (Exception e) {
            throw SyssvcException.syssvcExceptions.localRepoError(exceptionPrefix + e);
        }
    }

    private void cleanupTmpFile(Path filePath) {
        try {
            Files.delete(filePath);
        } catch (Exception e) {
            _log.warn("Failed to delete tmp file {}", filePath);
        }
    }
}
