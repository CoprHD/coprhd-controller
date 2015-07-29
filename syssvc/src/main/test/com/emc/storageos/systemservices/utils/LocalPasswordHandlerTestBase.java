/*
 * Copyright 2015 EMC Corporation
 * All Rights Reserved
 */
/**
 *  Copyright (c) 2013 EMC Corporation
 * All Rights Reserved
 *
 * This software contains the intellectual property of EMC Corporation
 * or is licensed to EMC Corporation from third parties.  Use of this
 * software and the intellectual property contained therein is expressly
 * limited to the terms and conditions of the License Agreement under which
 * it is provided by or on behalf of EMC.
 */

package com.emc.storageos.systemservices.utils;

import static com.emc.storageos.model.property.PropertyConstants.ENCRYPTEDSTRING;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.Map;
import java.util.Properties;
import java.util.TreeMap;

import javax.ws.rs.core.Response;

import com.emc.storageos.coordinator.client.model.PropertyInfoExt;

import com.emc.storageos.systemservices.impl.resource.DummyEncryptionProvider;
import org.apache.commons.codec.binary.Base64;

import com.emc.storageos.db.server.util.StubCoordinatorClientImpl;
import com.emc.storageos.security.password.PasswordUtils;
import com.emc.storageos.systemservices.exceptions.CoordinatorClientException;
import com.emc.storageos.systemservices.exceptions.LocalRepositoryException;
import com.emc.storageos.systemservices.impl.resource.ConfigService;
import com.emc.storageos.systemservices.impl.upgrade.CoordinatorClientExt;
import com.emc.storageos.systemservices.impl.upgrade.LocalRepository;
import com.emc.storageos.systemservices.impl.upgrade.UpgradeManager;
import com.emc.storageos.coordinator.client.model.CoordinatorSerializable;
import com.emc.storageos.model.property.PropertyInfoUpdate;
import com.emc.storageos.model.property.PropertyMetadata;
import com.emc.storageos.systemservices.impl.util.LocalPasswordHandler;
import com.emc.storageos.util.DummyDbClient;

public class LocalPasswordHandlerTestBase {
    private DummyConfigService _cfg = new DummyConfigService();
    private DummyCoordinatorClientExt _coordinator = new DummyCoordinatorClientExt();

    public PropertyInfoExt _passwordProps = new PropertyInfoExt();
    public Map<String, PropertyMetadata> _propsMetadata = new TreeMap<String, PropertyMetadata>();

    public DummyEncryptionProvider _encryptionProvider = new DummyEncryptionProvider();

    public LocalPasswordHandler getPasswordHandler() {
        PasswordUtils.setDefaultProperties(new Properties());
        PasswordUtils utils = new PasswordUtils();
        utils.setCoordinator(new StubCoordinatorClientImpl(URI.create("urn:coordinator")));
        utils.setDbClient(new DummyDbClient());

        LocalPasswordHandler ph = new LocalPasswordHandler();
        ph.setConfigService(_cfg);
        ph.setPasswordUtils(utils);
        return ph;
    }

    public void setPropsMetaData() {
        PropertyMetadata proxyuser_metadata = setPropMetaData("Encrypted password for the 'proxyuser' account",
                "Encrypted (SHA-512) password for the local 'proxyuser' account.",
                "encryptedstring", 255, "Security", true, true, false, true, false, "", true);
        PropertyMetadata sysmonitor_metadata = setPropMetaData("Encrypted password for the 'sysmonitor' account",
                "Encrypted password for the 'sysmonitor' account.",
                "string", 255, "Security", true, true, false, true, false,
                "$6$BIu9aQ6$wBnn9Tn.CUuuoi/JZe.oAOmUDIVCqHpXeem7ZHO5R7dPg2hul8tNCBzwumKrFw8A0qm.LH8YvMJUaN2AL1JVc0", true);
        PropertyMetadata root_metadata = setPropMetaData("Encrypted password for the 'root' account",
                "Encrypted (SHA-512) password for the local 'root' account.",
                "string", 255, "Security", true, true, false, true, false,
                "$6$eBIu9aQ6$wBnn9Tn.CUuuoi/JZe.oAOmUDIVCqHpXeem7ZHO5R7dPg2hul8tNCBzwumKrFw8A0qm.LH8YvMJUaN2AL1JVc0", false);
        PropertyMetadata svcuser_metadata = setPropMetaData("Encrypted password for the 'svcuser' account",
                "Encrypted (SHA-512) password for the local 'svcuser' account.",
                "string", 255, "Security", true, true, false, true, false,
                "$6$eBIu9aQ6$wBnn9Tn.CUuuoi/JZe.oAOmUDIVCqHpXeem7ZHO5R7dPg2hul8tNCBzwumKrFw8A0qm.LH8YvMJUaN2AL1JVc0", false);

        _propsMetadata.put("system_proxyuser_encpassword", proxyuser_metadata);
        _propsMetadata.put("system_sysmonitor_encpassword", sysmonitor_metadata);
        _propsMetadata.put("system_root_encpassword", root_metadata);
        _propsMetadata.put("system_svcuser_encpassword", svcuser_metadata);
    }

    public PropertyMetadata setPropMetaData(String label, String description, String type, int maxLen, String tag, Boolean advanced,
            Boolean userMutable, Boolean userConfigurable, Boolean reconfigRequired, Boolean rebootRequired,
            String value, Boolean controlNodeOnly) {

        PropertyMetadata metaData = new PropertyMetadata();

        metaData.setLabel(label);
        metaData.setDescription(description);
        metaData.setType(type);
        metaData.setMaxLen(maxLen);
        metaData.setTag(tag);
        metaData.setAdvanced(advanced);
        metaData.setUserMutable(userMutable);
        metaData.setUserConfigurable(userConfigurable);
        metaData.setReconfigRequired(reconfigRequired);
        metaData.setRebootRequired(rebootRequired);
        metaData.setValue(value);
        metaData.setControlNodeOnly(controlNodeOnly);

        return metaData;
    }

    public DummyCoordinatorClientExt getCoordinator() {
        return _coordinator;
    }

    private class DummyCoordinatorClientExt extends CoordinatorClientExt {

        public DummyCoordinatorClientExt() {
        }

        @Override
        public boolean isClusterUpgradable() {
            return true;
        }

        @Override
        public void setTargetInfo(final CoordinatorSerializable info) throws CoordinatorClientException {

            PropertyInfoExt props = (PropertyInfoExt) info;

            for (Map.Entry<String, String> e : props.getAllProperties().entrySet()) {
                _passwordProps.addProperty(e.getKey(), e.getValue());
            }

        }
    }

    private class DummyUpgradeManager extends UpgradeManager {

        @Override
        public LocalRepository getLocalRepository() {
            return new DummyLocalRepository(_passwordProps);
        }

        @Override
        public void wakeupOtherNodes() {
        }

        @Override
        public void wakeup() {
        }
    }

    private class DummyLocalRepository extends LocalRepository {

        private PropertyInfoExt _props = null;

        public DummyLocalRepository(PropertyInfoExt props) {
            super();
            _props = props;
        }
    }

    private class DummyConfigService extends ConfigService {

        @Override
        public Response setProperties(PropertyInfoUpdate setProperty) throws LocalRepositoryException, CoordinatorClientException,
                URISyntaxException {
            _passwordProps.addProperties(setProperty.getAllProperties());
            for (Map.Entry<String, String> entry : setProperty.getAllProperties().entrySet()) {
                String validatedPropVal;
                final String key = entry.getKey();
                final String value = entry.getValue();
                PropertyMetadata metaData = _propsMetadata.get(key);
                if (metaData != null && ENCRYPTEDSTRING.equalsIgnoreCase(metaData.getType())) {
                    validatedPropVal = new String(Base64.encodeBase64(_encryptionProvider.encrypt(value)));
                    _passwordProps.addProperty(key, validatedPropVal);
                }
            }

            return Response.ok().build();
        }

    }

}
