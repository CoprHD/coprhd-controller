package com.emc.storageos.discoveryplugins.smis;


import java.math.BigInteger;
import java.net.URI;
import java.util.ArrayList;
import java.util.LinkedList;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import javax.cim.CIMObjectPath;
import javax.cim.UnsignedInteger64;
import javax.security.auth.Subject;
import javax.wbem.WBEMException;
import javax.wbem.client.PasswordCredential;
import javax.wbem.client.UserPrincipal;
import javax.wbem.client.WBEMClient;

import org.junit.Before;
import org.junit.Test;
import org.sblim.cimclient.internal.wbem.WBEMClientCIMXML;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationContext;
import org.springframework.context.support.ClassPathXmlApplicationContext;

import com.emc.storageos.cimadapter.connections.cim.CimObjectPathCreator;
import com.emc.storageos.db.client.model.Stat;
import com.emc.storageos.db.client.model.StorageSystem;
import com.emc.storageos.plugins.AccessProfile;
import com.emc.storageos.plugins.BaseCollectionException;
import com.emc.storageos.plugins.common.Constants;
import com.emc.storageos.plugins.common.Executor;
import com.emc.storageos.plugins.common.Util;
import com.emc.storageos.plugins.common.commandgenerator.CommandGenerator;
import com.emc.storageos.plugins.common.domainmodel.Namespace;
import com.emc.storageos.plugins.common.domainmodel.NamespaceList;
import com.emc.storageos.plugins.common.domainmodel.Operation;
import com.emc.storageos.plugins.metering.smis.SMIPluginException;
import com.emc.storageos.services.util.EnvConfig;
import com.google.common.collect.Sets;


public class CommunicationInterfaceTest {
    private static final Logger _logger  = LoggerFactory
                                                 .getLogger(CommunicationInterfaceTest.class);
    private ApplicationContext  _context = null;
    private static final String UNIT_TEST_CONFIG_FILE = "sanity";

    @Before
    public void setup() {
       _context = new ClassPathXmlApplicationContext("/metering-block-context.xml");
        // to be used for Mock
        // System.setProperty("wbeminterface",
        // "com.emc.srm.base.discovery.plugins.smi.MockWBEMClient");
    }

    @Test
    public void testArrayWeight() {
        // long s = UnsignedLongs.parseUnsignedLong("18446744073709551104");

       String s = "18446744073709551104";

       String s1 = s.substring(0, s.length() - 6);

       long l = Long.parseLong(s1);
       BigInteger t = new BigInteger("-1");
       UnsignedInteger64 ui = new UnsignedInteger64("18446744073709551104");
       String providerIPAddress = EnvConfig.get(UNIT_TEST_CONFIG_FILE, "smis.host.ipaddress");
       String providerNamespace = EnvConfig.get(UNIT_TEST_CONFIG_FILE, "smis.namespace");
       String providerPortNumber = EnvConfig.get(UNIT_TEST_CONFIG_FILE, "smis.host.port");
       String providerUser = EnvConfig.get(UNIT_TEST_CONFIG_FILE, "smis.host.username");
       String providerPwd = EnvConfig.get(UNIT_TEST_CONFIG_FILE, "smis.host.password");
       String providerUseSSL = EnvConfig.get(UNIT_TEST_CONFIG_FILE, "smis.usessl");
       String providerSystemSerialId = EnvConfig.get(UNIT_TEST_CONFIG_FILE, "smis.host.array.serial");
       
        
       BigInteger l1 =  ui.bigIntegerValue();
        _logger.info("Test Started");
        WBEMClientCIMXML _wbemClient = null;
        try {
            final NamespaceList _nsList = (NamespaceList) _context.getBean("namespaces");
            _wbemClient = (WBEMClientCIMXML) _context.getBean("cimClient");
            AccessProfile _profile = new AccessProfile();
            _profile.setInteropNamespace(providerNamespace);
            _profile.setIpAddress(providerIPAddress);
            _profile.setPassword(providerPwd);
            _profile.setProviderPort(providerPortNumber);
            _profile.setnamespace("Performance");
            _profile.setelementType("Array");
            _profile.setUserName(providerUser);
            _profile.setSslEnable(providerUseSSL); // need to set Array serial ID;
            _profile.setserialID(providerSystemSerialId);
            getCIMClient(_profile, _wbemClient);
            Map<String, Object> _keyMap = new ConcurrentHashMap<String, Object>();
            _keyMap.put(Constants._computerSystem, new ArrayList<CIMObjectPath>());
            _keyMap.put(Constants.REGISTEREDPROFILE, CimObjectPathCreator.createInstance(
                    Constants.PROFILECLASS, "interop"));
            _keyMap.put(Constants._cimClient, new WBEMClientCIMXML());
            _keyMap.put(Constants.ACCESSPROFILE, _profile);
            _profile.setSystemId(new URI("tag"));
            _profile.setSystemClazz(StorageSystem.class);
            _keyMap.put(Constants._serialID, _profile.getserialID());
            
            _keyMap.put(Constants._storagePool, CimObjectPathCreator.createInstance(Constants._cimPool,
                    _profile.getInteropNamespace()));
            _keyMap.put(Constants.STORAGEPOOLS, new LinkedList<CIMObjectPath>());
            _keyMap.put("storageProcessors", new LinkedList<CIMObjectPath>());
            _keyMap.put(Constants._computerSystem, CimObjectPathCreator.createInstance(Constants._cimSystem,
                    _profile.getInteropNamespace()));
            _keyMap.put(Constants._cimClient, _wbemClient);
            _keyMap.put(Constants._serialID, _profile.getserialID());
            //_keyMap.put(Constants.dbClient, _dbClient);
            _keyMap.put(Constants._Volumes, new LinkedList<CIMObjectPath>());
            _keyMap.put(Constants._nativeGUIDs, Sets.newHashSet());
            _keyMap.put(Constants._Stats, new LinkedList<Stat>());
            _keyMap.put(Constants._InteropNamespace, _profile.getInteropNamespace());
            _keyMap.put(Constants._debug, true);
            //_keyMap.put(Constants._cache, _Cache);
            //_keyMap.put(Constants._globalCacheKey, cacheVolumes);
           // _keyMap.put(Constants._cachePools, cachePools);
           // _keyMap.put(Constants._cassandraInsertion, _dbUtil);
           // _keyMap.put(Constants.PROPS, accessProfile.getProps());
            // Add storagePool Object path & LinkedList<CIMObjectPath> to Map
            _keyMap.put(Constants._storagePool, CimObjectPathCreator.createInstance(Constants._cimPool,
                    _profile.getInteropNamespace()));
            _keyMap.put(Constants.STORAGEPOOLS, new LinkedList<CIMObjectPath>());
            Executor _executor =new Executor() {

                @Override
                protected void customizeException(Exception e, Operation operation)
                        throws BaseCollectionException {
                    // TODO Auto-generated method stub
                    
                }
            };
            Util _util = new Util();
            CommandGenerator _gen = new CommandGenerator();
            _gen.setutil(_util);
            _executor.set_keyMap(_keyMap);
            _executor.set_generator(_gen);
            _executor.set_util(_util);
            _executor.execute((Namespace) _nsList.getNsList().get("metering"));
           
        } catch (Exception e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        } finally {
            _wbemClient.close();
        }
    }

    private void getCIMClient(AccessProfile accessProfile, WBEMClient cimClient)
            throws SMIPluginException {
        try {
            CIMObjectPath path = CimObjectPathCreator.createInstance(accessProfile.getProtocol(),
                    accessProfile.getIpAddress(), accessProfile.getProviderPort(),
                    accessProfile.getInteropNamespace(), null, null);
            UserPrincipal userPr = new UserPrincipal(accessProfile.getUserName(),
                    accessProfile.getIpAddress());
            PasswordCredential pwCred = new PasswordCredential(
                    accessProfile.getPassword(), accessProfile.getIpAddress());
            Subject subject = new Subject();
            subject.getPrincipals().add(userPr);
            subject.getPublicCredentials().add(pwCred);
            subject.getPrivateCredentials().add(pwCred);
            cimClient.initialize(path, subject, new Locale[] { Locale.US });
        } catch (WBEMException e) {
            final int errorCode = e.getID()
                    + SMIPluginException.ERRORCODE_START_WBEMEXCEPTION;
            // _logger.error(fetchWBEMErrorMessage(e), errorCode);
            throw new SMIPluginException("CIMOM Initializing Error", errorCode);
        }
    }
}
