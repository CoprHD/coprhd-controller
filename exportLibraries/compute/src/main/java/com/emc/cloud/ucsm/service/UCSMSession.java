/*
 * Copyright (c) 2015 EMC Corporation
 * All Rights Reserved
 */
package com.emc.cloud.ucsm.service;

import java.net.MalformedURLException;
import java.net.URL;
import java.text.NumberFormat;
import java.util.concurrent.TimeUnit;

import javax.xml.bind.JAXBElement;

import org.apache.commons.beanutils.BeanUtils;
import org.apache.commons.codec.binary.Base64;
import org.apache.curator.framework.recipes.locks.InterProcessReadWriteLock;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;
import org.springframework.util.Assert;

import com.emc.cloud.platform.clientlib.ClientGeneralException;
import com.emc.cloud.platform.clientlib.ClientMessageKeys;
import com.emc.cloud.platform.ucs.in.model.AaaLogin;
import com.emc.cloud.platform.ucs.in.model.AaaRefresh;
import com.emc.cloud.platform.ucs.in.model.ObjectFactory;
import com.emc.cloud.platform.ucs.out.model.ConfigConfMo;
import com.emc.cloud.platform.ucs.out.model.ConfigResolveClass;
import com.emc.cloud.platform.ucs.out.model.ConfigResolveDn;
import com.emc.storageos.coordinator.client.service.DistributedDataManager;
import com.emc.storageos.coordinator.client.service.impl.CoordinatorClientImpl;
import com.emc.storageos.db.client.model.EncryptionProvider;

public class UCSMSession implements ComputeSession {

    @Autowired
    ApplicationContext applicationContext;

    EncryptionProvider encryptionProvider;

    private TransportWrapper ucsmTransportWrapper;

    private static final Logger LOGGER = LoggerFactory.getLogger(UCSMSession.class);

    private String serviceURI;
    private final String username;
    private final String password;
    private final String oneWayHash;
    private final String hostAddress;

    CoordinatorClientImpl coordinator;

    DistributedDataManager distributedDataManager = null;
    private UCSMDistributedDataUtil ucsmDistributedDataUtil;

    InterProcessReadWriteLock lock = null;

    private ObjectFactory objectFactory = new ObjectFactory();

    public UCSMSession(String serviceURI, String username, String password) throws ClientGeneralException {
        this.serviceURI = serviceURI;
        this.username = username;
        this.password = password;
        this.oneWayHash = ComputeSessionUtil.generateHash(serviceURI, username, password);

        try {
            hostAddress = new URL(serviceURI).getHost();
        } catch (MalformedURLException e) {
            throw new ClientGeneralException(ClientMessageKeys.MALFORMED_URL);
        }
    }

    public <T> T execute(Object object, Class<T> returnType) throws ClientGeneralException {
        initializeRequest();
        T returnObject = ucsmTransportWrapper.execute(serviceURI, postPayload((JAXBElement<?>) object), returnType);
        checkForResponseStatusErrors(returnObject);
        return returnObject;
    }

    public void login() throws ClientGeneralException {
        if (isSessionValid()) {
            return;
        }
        attemptSessionRefresh();

        if (isSessionValid()) {
            return;
        }
        relogin();
    }

    private Boolean isSessionValid() throws ClientGeneralException {
        Boolean writeLockHeld = false;
        try {
            writeLockHeld = getLock().writeLock().isAcquiredInThisProcess();
            if (!writeLockHeld) {
                readLock();
            }
            ComputeSessionCache cache = decryptAndRetrieveSessionCache();

            if (!isCacheNotNull(cache)) {
                LOGGER.debug("No available session information for {}. Need to login", hostAddress);
                return false;
            }

            if (!cache.getHashKey().equals(oneWayHash)) {
                LOGGER.info("Change of port/creds detected for {}. Need to re-login or refresh", hostAddress);
                return false;
            }

            /*
             * The recommended refresh time is returned from the UCSM, which is cached as session length.
             */
            if (Math.abs(System.currentTimeMillis() - cache.getCreateTime()) >= ((cache.getSessionLength() * 1000) * (0.80))) {
                LOGGER.info("Session has expired for {}. Need to re-login or refresh", hostAddress);
                return false;
            }

            return true;
        } finally {
            if (!writeLockHeld) {
                readUnlock();
            }
        }
    }

    private void attemptSessionRefresh() throws ClientGeneralException {
        try {
            writeLock();
            // write lock defence code
            LOGGER.debug("Attempt to refresh session for {}", hostAddress);
            if (isSessionValid()) {
                LOGGER.debug("Session is valid for {}. No need to refresh", hostAddress);
                return;
            }
            ComputeSessionCache cache = decryptAndRetrieveSessionCache();

            if (cache == null || cache.getHashKey() == null || !cache.getHashKey().equals(oneWayHash)) {
                encryptAndUpdateSessionCache(new ComputeSessionCache());
                return;
            }

            AaaRefresh aaaRefresh = new AaaRefresh();
            aaaRefresh.setInName(username);
            aaaRefresh.setInPassword(password);
            aaaRefresh.setCookie(cache.getSessionId());
            aaaRefresh.setInCookie(cache.getSessionId());

            Object response = ucsmTransportWrapper.execute(serviceURI, objectFactory.createAaaRefresh(aaaRefresh), Object.class);

            Assert.notNull(response, "Authentication Call resulted in Null Response");
            Assert.isTrue(response instanceof com.emc.cloud.platform.ucs.out.model.AaaRefresh, "Invalid Response Type!");

            com.emc.cloud.platform.ucs.out.model.AaaRefresh refreshResponse = (com.emc.cloud.platform.ucs.out.model.AaaRefresh) response;
            if (refreshResponse != null && refreshResponse.getOutCookie() != null && !refreshResponse.getOutCookie().isEmpty()) {
                LOGGER.info("Session has been refreshed");

                cache = new ComputeSessionCache(refreshResponse.getOutCookie(), System.currentTimeMillis(), parseNumber(
                        refreshResponse.getOutRefreshPeriod()).longValue(), oneWayHash);
                encryptAndUpdateSessionCache(cache);

            } else {
                LOGGER.info("Session for {} cannot be refreshed", hostAddress);
                encryptAndUpdateSessionCache(new ComputeSessionCache());
            }

        } finally {
            writeUnlock();
        }
    }

    public Object postPayload(JAXBElement<?> jaxbElement) throws ClientGeneralException {
        try {
            login();
            try {
                readLock();

                ComputeSessionCache cache = decryptAndRetrieveSessionCache();

                if (cache != null && cache.getSessionId() != null) {
                    BeanUtils.setProperty(jaxbElement.getValue(), "cookie", cache.getSessionId());
                }
            } catch (IllegalAccessException e) {
                LOGGER.error("Unable to set the cookie on object type: " + jaxbElement.getValue(), e);
                throw new ClientGeneralException(ClientMessageKeys.MODEL_EXCEPTION);
            } catch (Exception e) {
                LOGGER.error("Unable to set the cookie on object type: " + jaxbElement.getValue(), e);
                throw new ClientGeneralException(ClientMessageKeys.INTERNAL_SERVER_ERROR);
            } finally {
                readUnlock();
            }
        } catch (ClientGeneralException e) {
            LOGGER.debug(e.getLocalizedMessage(), e);
            throw e;
        }
        return jaxbElement;
    }

    private void relogin() throws ClientGeneralException {
        try {
            writeLock();
            LOGGER.info("Attempt to login {}", hostAddress);
            // recheck login status
            if (isSessionValid()) {
                LOGGER.debug("After rechecking. Re-login session not required for {}", hostAddress);
                return;
            }
            ComputeSessionCache cache = null;

            AaaLogin aaaLogin = new AaaLogin();
            aaaLogin.setInName(username);
            aaaLogin.setInPassword(password);

            Object response = ucsmTransportWrapper.execute(serviceURI, objectFactory.createAaaLogin(aaaLogin), Object.class);

            Assert.notNull(response, "Authentication Call resulted in Null Response");
            Assert.isTrue(response instanceof com.emc.cloud.platform.ucs.out.model.AaaLogin, "Invalid Response Type!");

            com.emc.cloud.platform.ucs.out.model.AaaLogin loginResponse = (com.emc.cloud.platform.ucs.out.model.AaaLogin) response;

            if (loginResponse != null && loginResponse.getOutCookie() != null && !loginResponse.getOutCookie().isEmpty()) {
                cache = new ComputeSessionCache(loginResponse.getOutCookie(), System.currentTimeMillis(), parseNumber(
                        loginResponse.getOutRefreshPeriod()).longValue(), oneWayHash);
                encryptAndUpdateSessionCache(cache);
            } else {
                throw new ClientGeneralException(ClientMessageKeys.UNAUTHORIZED, new String[] { serviceURI, "",
                        "Unable to authenticate username/credentials pair" });
            }
        } finally {
            writeUnlock();
        }
    }

    @Override
    public void logout() throws ClientGeneralException {
        // Is is no-op for the UCS
    }

    @Override
    public void clearSession() throws ClientGeneralException {
        try {
            writeLock();
            LOGGER.debug("Acquired write lock to clear out session for {}", hostAddress);
            initializeSession();
            distributedDataManager.removeNode(getNodePath());
            LOGGER.debug("Session cache for {} cleared successfully", hostAddress);
            distributedDataManager.close();
            LOGGER.debug("DistributedDataManager for {} closed successfully", hostAddress);
        } catch (Exception e) {
            LOGGER.warn("Unable to clear session cache for {}", hostAddress);
        } finally {
            writeUnlock();
        }
    }

    public void setUcsmTransportWrapper(TransportWrapper ucsmTransportWrapper) {
        this.ucsmTransportWrapper = ucsmTransportWrapper;
    }

    public void setCoordinator(CoordinatorClientImpl coordinator) {
        this.coordinator = coordinator;
    }

    public void setEncryptionProvider(EncryptionProvider encryptionProvider) {
        this.encryptionProvider = encryptionProvider;
    }

    public ComputeSessionCache decryptAndRetrieveSessionCache() throws ClientGeneralException {
        Object zkdata = null;
        ComputeSessionCache cache = null;
        try {
            LOGGER.debug("Attempt to fetch session info for {}", hostAddress);

            try {
                zkdata = distributedDataManager.getData(getNodePath(), false);
            } catch (Exception e) {
                throw new ClientGeneralException(ClientMessageKeys.DISTRIBUTED_DATA_CACHE_ERROR, e);
            }

            if (zkdata != null) {
                cache = (ComputeSessionCache) zkdata;
            }

            try {
                if (cache != null && cache.getSessionId() != null) {
                    cache.setSessionId(encryptionProvider.decrypt(Base64.decodeBase64(cache.getSessionId())));
                }
            } catch (Exception e) {
                throw new ClientGeneralException(ClientMessageKeys.DATA_ENCRYPTION_ERROR, e);
            }
        } catch (Exception e) {
            LOGGER.warn(e.getMessage());
        }
        return cache;
    }

    public void encryptAndUpdateSessionCache(ComputeSessionCache cache) throws ClientGeneralException {
        try {
            if (cache != null && cache.getSessionId() != null) {
                cache.setSessionId(encryptionProvider.getEncryptedString(cache.getSessionId()));
            }
        } catch (Exception e) {
            throw new ClientGeneralException(ClientMessageKeys.DATA_ENCRYPTION_ERROR, e);
        }

        try {
            distributedDataManager.putData(getNodePath(), cache);
        } catch (Exception e) {
            throw new ClientGeneralException(ClientMessageKeys.DISTRIBUTED_DATA_CACHE_ERROR, e);
        }
    }

    private String getNodePath() {
        return ComputeSessionUtil.Constants.COMPUTE_SESSION_BASE_PATH.toString() + "/" + hostAddress;
    }

    private void readLock() throws ClientGeneralException {
        try {
            getLock().readLock().acquire(1, TimeUnit.HOURS);
        } catch (Exception e) {
            throw new ClientGeneralException(ClientMessageKeys.DISTRIBUTED_LOCK_ERROR, e);
        }
    }

    private void readUnlock() throws ClientGeneralException {
        try {
            getLock().readLock().release();
        } catch (Exception e) {
            throw new ClientGeneralException(ClientMessageKeys.DISTRIBUTED_LOCK_ERROR, e);
        }
    }

    private void writeLock() throws ClientGeneralException {
        try {
            getLock().writeLock().acquire(1, TimeUnit.HOURS);
        } catch (Exception e) {
            throw new ClientGeneralException(ClientMessageKeys.DISTRIBUTED_LOCK_ERROR, e);
        }
    }

    private void writeUnlock() throws ClientGeneralException {
        try {
            getLock().writeLock().release();
        } catch (Exception e) {
            throw new ClientGeneralException(ClientMessageKeys.DISTRIBUTED_LOCK_ERROR, e);
        }
    }

    private InterProcessReadWriteLock getLock() throws ClientGeneralException {
        try {
            if (lock == null) {
                String lockName = ComputeSessionUtil.Constants.COMPUTE_SESSION_BASE_PATH.toString() + "-" + hostAddress;
                lock = coordinator.getReadWriteLock(lockName);
            }
        } catch (Exception e) {
            throw new ClientGeneralException(ClientMessageKeys.DISTRIBUTED_LOCK_ERROR, e);
        }
        return lock;
    }

    private void initializeSession() {
        distributedDataManager = ucsmDistributedDataUtil.getDistributedDataManager();
    }

    private void initializeRequest() {
        initializeSession();
        try {
            distributedDataManager.checkExists(getNodePath());
        } catch (Exception e) {
            LOGGER.warn(e.getMessage());
        }
    }

    private Boolean isCacheNotNull(ComputeSessionCache cache) {
        if (cache == null || cache.getSessionId() == null || cache.getSessionLength() == null ||
                cache.getHashKey() == null) {
            return false;
        }
        return true;
    }

    private void checkForResponseStatusErrors(Object response) throws ClientGeneralException {
        String errorCode = null;
        String errorDescription = null;

        if (response instanceof ConfigResolveDn) {
            ConfigResolveDn configResolveDn = ((ConfigResolveDn) response);
            errorCode = configResolveDn.getErrorCode();
            errorDescription = configResolveDn.getErrorDescr();
        }
        else if (response instanceof ConfigConfMo) {
            ConfigConfMo configResolveDn = ((ConfigConfMo) response);
            errorCode = configResolveDn.getErrorCode();
            errorDescription = configResolveDn.getErrorDescr();
        }
        else if (response instanceof ConfigResolveClass) {
            ConfigResolveClass configResolveDn = ((ConfigResolveClass) response);
            errorCode = configResolveDn.getErrorCode();
            errorDescription = configResolveDn.getErrorDescr();
        }

        if (errorCode != null) {
            String[] errors = new String[] { errorDescription };
            throw new ClientGeneralException(ClientMessageKeys.byErrorCode(parseNumber(errorCode).intValue()), errors);
        }
    }

    private Number parseNumber(String number) {
        try {
            return NumberFormat.getInstance().parse(number);
        } catch (Exception e) {
            LOGGER.error("Encountered an parse error for string {} caused by {}", number, e.getMessage());
        }
        return new Integer(0);
    }

    /**
     * @param ucsmDistributedDataUtil the ucsmDistributedDataUtil to set
     */
    public void setUcsmDistributedDataUtil(UCSMDistributedDataUtil ucsmDistributedDataUtil) {
        this.ucsmDistributedDataUtil = ucsmDistributedDataUtil;
    }

}
