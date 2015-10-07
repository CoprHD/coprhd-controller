/*
 * Copyright 2015 EMC Corporation
 * All Rights Reserved
 */

package com.emc.storageos.api.ldap.ldapserver;

import com.unboundid.ldap.listener.InMemoryDirectoryServer;
import com.unboundid.ldap.listener.InMemoryDirectoryServerConfig;
import com.unboundid.ldap.listener.InMemoryListenerConfig;
import com.unboundid.ldap.sdk.LDAPException;
import com.unboundid.ldap.sdk.schema.Schema;
import com.unboundid.ldif.LDIFException;
import com.unboundid.util.ssl.KeyStoreKeyManager;
import com.unboundid.util.ssl.SSLUtil;
import com.unboundid.util.ssl.TrustAllTrustManager;
import org.apache.commons.io.IOUtils;
import org.springframework.util.CollectionUtils;
import org.apache.commons.lang.StringUtils;
import org.slf4j.LoggerFactory;
import org.slf4j.Logger;

import java.io.*;
import java.security.GeneralSecurityException;
import java.util.*;

import com.emc.storageos.api.ldap.exceptions.FileOperationFailedException;
import com.emc.storageos.api.ldap.exceptions.DirectoryOrFileNotFoundException;

/**
 * A class that implements the in memory ldap server using the unboundId
 * ldap sdk for java. It loads the given schema and configuration ldif files
 * inorder simulate the ldap server in memory. Unless specified in the api request
 * to start, the default schema and config files (stored as part of the class resources)
 * loaded into in memory ldap server. It supports both ldap and ldaps
 * types of connection. Unless specified in the api request to start,
 * the ldap connection uses default port 389 and ldaps connection uses the
 * default port 636.
 */
public class LDAPServer {
    private final Logger _log = LoggerFactory.getLogger(this.getClass());

    // The default base and manager DN (Distinguished Name) and managerDN password.
    // This can be overwritten by providing the required value in the /ldap-service/start
    // api request. Make sure these values matches entries or objects in the
    // schema and config ldif files. This manager DN and password can be used to
    // bind the ldap or ldaps connection.
    private static final String DEFAULT_LDAP_BASE_DN = "dc=apitest,dc=com";
    private static final String DEFAULT_LDAP_MANAGER_DN = "cn=manager,dc=apitest,dc=com";
    private static final String DEFAULT_LDAP_MANAGER_DN_PASSWORD = "secret";

    // The default schema and config ldif files given as a class resource.
    private static final String DEFAULT_LDAP_SCHEMA_EXPORT = "/Ldap_Schema_Export.ldif";
    private static final String DEFAULT_LDAP_CONFIG_EXPORT = "/Ldap_Config_Export.ldif";

    // While starting the in memory ldap server, all the required files (schema and config ldifs,
    // keystore file for the ldaps connection) will be read from the given respective source files
    // and a written into a a dummy temporary files under the build directory. This is make sure
    // we are not damaging any of source files. The next few constants specifies the default
    // location for the dummy files.
    private static final String DEFAULT_LDIF_FILES_DIRECTORY = "./build/%s/ldifs";
    private static final String DEFAULT_LDIF_SCHEMA_FILES_DIRECTORY = DEFAULT_LDIF_FILES_DIRECTORY + "/schema";
    private static final String DEFAULT_LDIF_CONFIG_FILES_DIRECTORY = DEFAULT_LDIF_FILES_DIRECTORY + "/config";
    private static final String DEFAULT_KEYSTORE_FILE_PATH = "./build/%s/secure/keystore";
    private static final String DEFAULT_KEYSTORE_FILE = "/ldapserverkeystore";
    private static final String DEFAULT_LDAP_SERVER_PROPERTIES = "/ldapserver.properties";

    // The default ldap and ldaps listening port.
    private static final int DEFAULT_LDAP_LISTEN_PORT = 389;
    private static final int DEFAULT_LDAPS_LISTEN_PORT = 636;

    private InMemoryDirectoryServerConfig _inMemoryDSConfig;
    private InMemoryDirectoryServer _inMemoryDS;
    private boolean _isRunning = false;

    private String _listenerName;
    private String _baseDN;
    private String _managerDN;
    private String _managerDNPassword;
    private List<String> _schemaLdifList;
    private List<String> _configLdifList;
    private int _ldapsListenPort;
    private int _ldapListenPort;

    /**
     * Returns the listener name.
     *
     * @return _listenerName.
     */
    public String getListenerName() {
        return _listenerName;
    }

    /**
     * Sets the listener name.
     *
     * @param _listenerName a unique listener name.
     */
    public void setListenerName(String _listenerName) {
        this._listenerName = _listenerName;
    }

    /**
     * Returns the base Distinguished Name.
     *
     * @return _baseDN.
     */
    public String getBaseDN() {
        return _baseDN;
    }

    /**
     * Sets the base Distinguished Name.
     *
     * @param _baseDN base DN of the ldap DIT.
     */
    public void setBaseDN(String _baseDN) {
        this._baseDN = _baseDN;
    }

    /** Returns the manager Distinguished Name.
     *
     * @return _managerDN.
     */
    public String getManagerDN() {
        return _managerDN;
    }

    /**
     * Sets the manager Distinguished Name.
     *
     * @param _managerDN a DN used to used bind the ldap connections.
     */
    public void setManagerDN(String _managerDN) {
        this._managerDN = _managerDN;
    }

    /**
     * Returns the password of the manager DN.
     *
     * @return _managerDNPassword.
     */
    public String getManagerDNPassword() {
        return _managerDNPassword;
    }

    /**
     * Sets the password of manager DN.
     *
     * @param _managerDNPassword a manager DN password.
     */
    public void setManagerDNPassword(String _managerDNPassword) {
        this._managerDNPassword = _managerDNPassword;
    }

    /**
     * Set the list of schema ldif files. A new copy is generated
     * from the source list.
     *
     * @param _schemaLdifList list of source schema ldif files path.
     */
    public void setSchemaLdifList(List<String> _schemaLdifList) {
        if (CollectionUtils.isEmpty(_schemaLdifList)) {
            _log.debug("There are not schema ldifs given.");
            return;
        }

        if (this._schemaLdifList == null) {
            this._schemaLdifList = new ArrayList<String>();
        } else {
            this._schemaLdifList.clear();
        }

        this._schemaLdifList.addAll(_schemaLdifList);
    }

    /**
     * Adds a single schema ldif file to the exising list of schema
     * ldif files.
     *
     * @param schemaLdif a schema ldif file path.
     */
    public void addSchemaLdif(String schemaLdif) {
        if (this._schemaLdifList == null) {
            this._schemaLdifList = new ArrayList<String>();
        }
        _schemaLdifList.add(schemaLdif);
    }

    /**
     * Set the list of config ldif files. A new copy is generated
     * from the source list.
     *
     * @param _configLdifList list of source config ldif files path.
     */
    public void setConfigLdifList(List<String> _configLdifList) {
        if (CollectionUtils.isEmpty(_configLdifList)) {
            _log.debug("There are not config ldifs given.");
            return;
        }

        if (this._configLdifList == null) {
            this._configLdifList = new ArrayList<String>();
        } else {
            this._configLdifList.clear();
        }

        this._configLdifList.addAll(_configLdifList);
    }

    /**
     * Adds a single config ldif file to the exising list of schema
     * ldif files.
     *
     * @param configLdif a config ldif file path.
     */
    public void addConfigLdif(String configLdif) {
        if (this._configLdifList == null) {
            this._configLdifList = new ArrayList<String>();
        }
        _configLdifList.add(configLdif);
    }

    /**
     * Returns the ldaps listen port.
     *
     * @return _ldapsListenPort
     */
    public int getLdapsListenPort() {
        return _ldapsListenPort;
    }

    /**
     * Sets the ldaps listen port.
     *
     * @param _ldapsListenPort a ldaps listener port.
     */
    public void setLdapsListenPort(int _ldapsListenPort) {
        this._ldapsListenPort = _ldapsListenPort;
    }

    /**
     * Returns the ldap listen port.
     *
     * @return _ldapListenPort
     */
    public int getListenPort() {
        return _ldapListenPort;
    }

    /**
     * Sets the ldap listen port.
     *
     * @param _listenPort a ldap listener port.
     */
    public void setListenPort(int _listenPort) {
        this._ldapListenPort = _listenPort;
    }

    /**
     * Stars the in memory ldap server by reading all the schema and config
     * ldif files. Once all the configurations are loaded to the in memory
     * ldap server, it starts listening for both ldap and ldaps connections
     * from clients.
     *
     * @return true if the in memory ldap server is started and successfully
     *          listening for the client connections, false otherwise.
     *
     * @throws LDIFException
     * @throws LDAPException
     * @throws IOException
     * @throws FileOperationFailedException
     * @throws GeneralSecurityException
     * @throws DirectoryOrFileNotFoundException
     */
    public boolean start() throws LDIFException,
                                    LDAPException,
                                    IOException,
                                    FileOperationFailedException,
                                    GeneralSecurityException,
                                    DirectoryOrFileNotFoundException {
        if (_isRunning) {
            _log.info("LDAP Service is already running.");
            return false;
        }

        _log.info("Starting LDAP Service.");
        addLDAPBindCredentials();

        _log.info("Importing Schema Ldifs");
        importLDAPSchemaLdifs();
        List<InMemoryListenerConfig> listenerConfigs = getInMemoryListenerConfigs();

        _inMemoryDSConfig.setListenerConfigs(listenerConfigs);
        _inMemoryDS = new InMemoryDirectoryServer(_inMemoryDSConfig);

        _log.info("Importing Config Ldifs");
        importLDAPConfigLdifs();

        _log.info("Star listening...");
        _inMemoryDS.startListening();

        _isRunning = true;

        return _isRunning;
    }

    /**
     * Stops the in memory ldap server.
     *
     * @return true if the in memory ldap server is stoppedsuccessfully
     *          false otherwise.
     */
    public boolean stop() {
        boolean isLDAPServerStopped = true;

        if (_isRunning) {
            _inMemoryDS.shutDown(true);
            _isRunning = false;
            _log.info("Stopping LDAP Service");
        } else {
            _log.debug("LDAP Service is not running");
            isLDAPServerStopped = false;
        }
        return isLDAPServerStopped;
    }

    /**
     * Returns the status of the in memory ldap server.
     *
     * @return true if it running, false otherwise.
     */
    public boolean isRunning() {
        _log.debug("LDAP Service status : {}", _isRunning);
        return _isRunning;
    }

    /**
     * Adds the Base Distinguished Name and credentials of the
     * user to be used for binding the in memory ldap server.
     *
     * @throws LDAPException
     */
    private void addLDAPBindCredentials() throws LDAPException {
        String baseDN = _baseDN;
        if(StringUtils.isEmpty(baseDN)) {
            baseDN = DEFAULT_LDAP_BASE_DN;
        }
        _log.debug("BaseDN {}", baseDN);
        _inMemoryDSConfig = new InMemoryDirectoryServerConfig(baseDN);

        String managerDN = _managerDN;
        if(StringUtils.isEmpty(managerDN)) {
            managerDN = DEFAULT_LDAP_MANAGER_DN;
        }

        String managerDNPassword = _managerDNPassword;
        if(StringUtils.isEmpty(managerDNPassword)) {
            managerDNPassword = DEFAULT_LDAP_MANAGER_DN_PASSWORD;
        }

        _log.debug("ManagerDN {} and ManagerDN password {}", managerDN, managerDNPassword);
        _inMemoryDSConfig.addAdditionalBindCredentials(managerDN, managerDNPassword);
    }

    /**
     * Creates the dummy schema ldif files under ./build directory.
     * These files are created based on the given schema_ldifs of
     * /ldap-service/start api payload. If there are no schema_ldfis
     * specified in the or the /ldap-service/start api payload, the
     * default file will be used.
     *
     * @throws FileOperationFailedException
     * @throws DirectoryOrFileNotFoundException
     * @throws IOException
     */
    private void createLDAPSchemaFiles() throws FileOperationFailedException,
                                                DirectoryOrFileNotFoundException,
                                                IOException {
        // Creates a dummy ldif files directory under ./build.
        createLdifFilesDirectory();

        if (CollectionUtils.isEmpty(_schemaLdifList)) {
            _log.info("Using default schema ldif files");
            InputStream schemaExportStream = LDAPServer.class.getResourceAsStream(DEFAULT_LDAP_SCHEMA_EXPORT);
            BufferedReader schemaExportReader = new BufferedReader(new InputStreamReader(schemaExportStream));
            String ldapSchemaExportFileName = "%s" + DEFAULT_LDAP_SCHEMA_EXPORT;
            ldapSchemaExportFileName = String.format(ldapSchemaExportFileName, getSchemaFilesDirectory());

            _log.debug("Schema export file name {}", ldapSchemaExportFileName);
            createLdifFile(schemaExportReader, ldapSchemaExportFileName);
        } else {
            _log.info("Using configured schema ldif files");
            for (String file : _schemaLdifList) {
                File fileObject = new File(file);
                if (!fileObject.exists()) {
                    throw new DirectoryOrFileNotFoundException("File", file);
                }
                InputStream schemaExportStream = new FileInputStream(file);
                BufferedReader schemaExportReader = new BufferedReader(new InputStreamReader(schemaExportStream));
                String ldapSchemaExportFileName = "%s/" + fileObject.getName();
                ldapSchemaExportFileName = String.format(ldapSchemaExportFileName, getSchemaFilesDirectory());

                _log.debug("Schema export file name {}", ldapSchemaExportFileName);
                createLdifFile(schemaExportReader, ldapSchemaExportFileName);
            }
        }
    }

    /**
     * Creates the dummy config ldif files under ./build directory.
     * These files are created based on the given config_ldifs of
     * /ldap-service/start api payload. If there are no config_ldfis
     * specified in the or the /ldap-service/start api payload, the
     * default file will be used.
     *
     * @throws FileOperationFailedException
     * @throws DirectoryOrFileNotFoundException
     * @throws IOException
     */
    private void createLDAPConfigFiles() throws FileOperationFailedException,
                                                DirectoryOrFileNotFoundException,
                                                IOException {
        // Creates a dummy ldif files directory under ./build.
        createLdifFilesDirectory();

        if (CollectionUtils.isEmpty(_configLdifList)) {
            _log.info("Using default config ldif files");
            InputStream configExportStream = LDAPServer.class.getResourceAsStream(DEFAULT_LDAP_CONFIG_EXPORT);
            BufferedReader configExportReader = new BufferedReader(new InputStreamReader(configExportStream));
            String ldapConfigExportFileName = "%s" + DEFAULT_LDAP_CONFIG_EXPORT;
            ldapConfigExportFileName = String.format(ldapConfigExportFileName, getConfigFilesDirectory());

            _log.debug("Config export file name {}", ldapConfigExportFileName);
            createLdifFile(configExportReader, ldapConfigExportFileName);
        } else {
            _log.info("Using configured config ldif files");
            for (String file : _configLdifList) {
                File fileObject = new File(file);
                if (!fileObject.exists()) {
                    throw new DirectoryOrFileNotFoundException("File", file);
                }
                InputStream configExportStream = new FileInputStream(file);
                BufferedReader configExportReader = new BufferedReader(new InputStreamReader(configExportStream));
                String ldapConfigExportFileName = "%s/" + fileObject.getName();
                ldapConfigExportFileName = String.format(ldapConfigExportFileName, getConfigFilesDirectory());

                _log.debug("Config export file name {}", ldapConfigExportFileName);
                createLdifFile(configExportReader, ldapConfigExportFileName);
            }
        }
    }

    /**
     * Creates a dummy ldif file under ./build directory from the
     * source file.
     *
     * @param schemaExportReader a source ldif stream reader.
     * @param ldapSchemaFileName a an Absolute path of destination ldif file.
     *
     * @throws IOException
     * @throws FileOperationFailedException
     */
    private void createLdifFile(BufferedReader schemaExportReader,
                                String ldapSchemaFileName) throws IOException,
                                                                    FileOperationFailedException {
        _log.info("Ldif file {}", ldapSchemaFileName);
        File ldapSchemaExportFile = new File(ldapSchemaFileName);

        if (ldapSchemaExportFile.exists()) {
            if (!ldapSchemaExportFile.delete()) {
                throw new FileOperationFailedException("delete", "file", ldapSchemaFileName);
            }
        }

        if (!ldapSchemaExportFile.createNewFile()) {
            throw new FileOperationFailedException("create", "file", ldapSchemaFileName);
        }

        if (!ldapSchemaExportFile.setWritable(true)) {
            throw new FileOperationFailedException("set writable", "file", ldapSchemaFileName);
        }

        ldapSchemaExportFile.deleteOnExit();

        BufferedWriter writer = new BufferedWriter(new FileWriter(ldapSchemaExportFile));
        String line;
        while ((line = schemaExportReader.readLine()) != null) {
            writer.write(line);
            writer.newLine();
        }
        writer.close();
    }

    /**
     * Creates the dummy directory for the ldif files.
     *
     * @throws FileOperationFailedException
     */
    private void createLdifFilesDirectory() throws FileOperationFailedException {
        String schemaExportDirName = getSchemaFilesDirectory();
        _log.info("Schema ldif files directory {}", schemaExportDirName);
        File schemaExportDir = new File(schemaExportDirName);
        if (!schemaExportDir.exists()) {
            if (!schemaExportDir.mkdirs()) {
                throw new FileOperationFailedException("create", "directory", schemaExportDirName);
            }
        }

        schemaExportDir.deleteOnExit();

        String configExportDirName = getConfigFilesDirectory();
        _log.info("Config ldif files directory {}", configExportDirName);
        File configExportDir = new File(configExportDirName);
        if (!configExportDir.exists()) {
            if (!configExportDir.mkdirs()) {
                throw new FileOperationFailedException("create", "directory", configExportDirName);
            }
        }

        configExportDir.deleteOnExit();
    }

    /**
     * Creates they dummy keystore file from the source keystore file
     * for ldaps listener configuration.
     *
     * @return returns the absolute path of the dummy keystore file.
     *
     * @throws IOException
     * @throws FileOperationFailedException
     */
    private String createKeystoreFile() throws IOException,
                                                FileOperationFailedException {
        InputStream keystoreFileStream = LDAPServer.class.getResourceAsStream(DEFAULT_KEYSTORE_FILE);
        byte [] keystoreData = IOUtils.toByteArray(keystoreFileStream);
        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        outputStream.write(keystoreData);

        String keyStoreDirName = getDefaultKeyStoreDirPath();
        File keystoreDir = new File(keyStoreDirName);
        if (!keystoreDir.exists()) {
            if (!keystoreDir.mkdirs()) {
                throw new FileOperationFailedException("create", "directory", keyStoreDirName);
            }
        }

        keystoreDir.deleteOnExit();

        _log.debug("Keystore file {}", keyStoreDirName + DEFAULT_KEYSTORE_FILE);
        File keystoreFile = new File(keyStoreDirName + DEFAULT_KEYSTORE_FILE);
        if (keystoreFile.exists()) {
            if (!keystoreFile.delete()) {
                throw new FileOperationFailedException("delete", "file", keystoreFile.getAbsolutePath());
            }
        }

        if (!keystoreFile.createNewFile()) {
            throw new FileOperationFailedException("create", "file", keystoreFile.getAbsolutePath());
        }

        keystoreFile.setWritable(true);
        keystoreFile.deleteOnExit();

        FileOutputStream keyStoreFileOutStream = new FileOutputStream(keystoreFile);
        outputStream.writeTo(keyStoreFileOutStream);

        outputStream.close();
        keyStoreFileOutStream.close();

        return keystoreFile.getAbsolutePath();
    }

    /**
     * Returns the relative path of the dummy config ldif files directory.
     *
     * @return relative path of the dummy config ldif files directory.
     */
    private String getConfigFilesDirectory() {
        return String.format(DEFAULT_LDIF_CONFIG_FILES_DIRECTORY, _listenerName);
    }

    /**
     * Returns the relative path of the dummy schema ldif files directory.
     *
     * @return relative path of the dummy schema ldif files directory.
     */
    private String getSchemaFilesDirectory() {
        return String.format(DEFAULT_LDIF_SCHEMA_FILES_DIRECTORY, _listenerName);
    }

    /**
     * Returns the relative path of the dummy keystore files directory.
     *
     * @return relative path of the dummy keystore files directory.
     */
    private String getDefaultKeyStoreDirPath() {
        return String.format(DEFAULT_KEYSTORE_FILE_PATH, _listenerName);
    }

    /**
     * Returns the relative path of the dummy ldif files directory.
     *
     * @return relative path of the dummy ldif files directory.
     */
    private String getDefaultLdifDirPath() {
        return String.format(DEFAULT_LDIF_FILES_DIRECTORY, _listenerName);
    }

    private List<InMemoryListenerConfig> getInMemoryListenerConfigs() throws LDAPException,
                                                                                IOException,
                                                                                GeneralSecurityException,
                                                                                FileOperationFailedException {
        // Creates the ldap configuration of the in memory ldap server.
        int ldapPort = this._ldapListenPort != 0 ? this._ldapListenPort : DEFAULT_LDAP_LISTEN_PORT;
        InMemoryListenerConfig ldapListenerConfig = InMemoryListenerConfig.createLDAPConfig(_listenerName, ldapPort);

        // Creates the ldaps configuration of the in memory ldap server.
        int ldapsPort = this._ldapsListenPort != 0 ? this._ldapsListenPort : DEFAULT_LDAPS_LISTEN_PORT;

        _log.debug("Ldap port {} and Ldaps port {}", ldapPort, ldapsPort);

        InputStream propFile = LDAPServer.class.getResourceAsStream(DEFAULT_LDAP_SERVER_PROPERTIES);
        Properties prop = new Properties();
        prop.load(propFile);

        String keyStorePassword = prop.getProperty("keyStorePassword");
        String keyStoreAlias = prop.getProperty("keyStoreAlias");
        String keyStoreType = prop.getProperty("keyStoreType");

        final SSLUtil serverSSLUtil = new SSLUtil(new KeyStoreKeyManager(createKeystoreFile(),
                                            keyStorePassword.toCharArray(), keyStoreType, keyStoreAlias), null);
        final SSLUtil clientSSLUtil = new SSLUtil(new TrustAllTrustManager());

        String secureListenerName = "Secure_" + _listenerName;
        InMemoryListenerConfig ldapsListenerConfig = InMemoryListenerConfig.createLDAPSConfig(secureListenerName, null,
                                                        ldapsPort, serverSSLUtil.createSSLServerSocketFactory(),
                                                        clientSSLUtil.createSSLSocketFactory());

        _log.info("Listener config {} and secure listener config {}", ldapListenerConfig.getListenerName(),
                ldapsListenerConfig.getListenerName());

        // Adds both ldap and ldaps configuration to the list of listener configs of the
        // in memory ldap server.
        List<InMemoryListenerConfig> listenerConfigs = new ArrayList<InMemoryListenerConfig>();
        listenerConfigs.add(ldapListenerConfig);
        listenerConfigs.add(ldapsListenerConfig);

        return listenerConfigs;
    }

    /**
     * Imports the schema ldif to the in memory ldap server.
     *
     * @throws FileOperationFailedException
     * @throws IOException
     * @throws LDIFException
     * @throws DirectoryOrFileNotFoundException
     */
    private void importLDAPSchemaLdifs() throws FileOperationFailedException,
                                                IOException,
                                                LDIFException,
                                                DirectoryOrFileNotFoundException {
        createLDAPSchemaFiles();
        File schemaFileDirectory = new File(getSchemaFilesDirectory());
        if (!schemaFileDirectory.exists() ||
                CollectionUtils.isEmpty(Arrays.asList(schemaFileDirectory.listFiles()))) {
            throw new DirectoryOrFileNotFoundException("Directory", getSchemaFilesDirectory());
        }
        _inMemoryDSConfig.setSchema(Schema.getSchema(schemaFileDirectory.listFiles()));
    }

    /**
     * Imports the config ldif to the in memory ldap server.
     *
     * @throws FileOperationFailedException
     * @throws IOException
     * @throws LDIFException
     * @throws LDAPException
     * @throws DirectoryOrFileNotFoundException
     */
    private void importLDAPConfigLdifs() throws FileOperationFailedException,
                                                IOException,
                                                LDIFException,
                                                LDAPException,
                                                DirectoryOrFileNotFoundException {
        createLDAPConfigFiles();
        File configFileDirectory = new File(getConfigFilesDirectory());
        if (!configFileDirectory.exists() ||
                CollectionUtils.isEmpty(Arrays.asList(configFileDirectory.listFiles()))) {
            throw new DirectoryOrFileNotFoundException("Directory", getConfigFilesDirectory());
        }

        for(File configFile : configFileDirectory.listFiles()) {
            _inMemoryDS.importFromLDIF(true, configFile.getPath());
        }
    }
}
