/*
 * Copyright (c) 2012-2013 EMC Corporation
 * All Rights Reserved
 */

package com.emc.storageos.systemservices;

import java.io.File;
import java.io.InputStream;
import java.io.UnsupportedEncodingException;
import java.net.URL;
import java.text.MessageFormat;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.commons.codec.binary.Base64;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

import com.emc.storageos.coordinator.client.model.SoftwareVersion;
import com.emc.storageos.coordinator.client.model.CoordinatorSerializable;
import com.emc.storageos.coordinator.client.model.PropertyInfoExt;

import static com.emc.storageos.coordinator.client.model.Constants.*;

import com.emc.storageos.db.client.model.EncryptionProvider;
import com.emc.storageos.services.util.EnvConfig;
import com.emc.storageos.svcs.errorhandling.resources.BadRequestException;
import com.emc.storageos.systemservices.exceptions.CoordinatorClientException;
import com.emc.storageos.systemservices.exceptions.RemoteRepositoryException;
import com.emc.storageos.systemservices.impl.upgrade.*;
import com.emc.storageos.systemservices.impl.upgrade.beans.*;

public class RemoteRepositoryTest {
    private static final String REMOTE_PROXY = EnvConfig.get("sanity", "syssvc.RemoteRepositoryTest.remoteProxy");
    private static final String DOWNLOAD_DIR = EnvConfig.get("sanity", "syssvc.RemoteRepositoryTest.downloadDir");
    private static final String DIRECTORY_REPO = EnvConfig.get("sanity", "syssvc.RemoteRepositoryTest.directoryRepo");
    private static final String CATALOG_SERVER_URL = EnvConfig.get("sanity", "syssvc.RemoteRepositoryTest.catalogServerURL");
    private static final String USERNAME = EnvConfig.get("sanity", "syssvc.RemoteRepositoryTest.username");
    private static final String PASSWORD = EnvConfig.get("sanity", "syssvc.RemoteRepositoryTest.password");
    private static volatile String repositoryProxy = null;
    private static volatile String repositoryUrl;
    private static volatile String username = USERNAME;
    private static volatile String password = PASSWORD;
    private static volatile RemoteRepositoryCache newSoftwareVersions;
    private RemoteRepository _repo = null;
    private UpgradeImageDownloader _downloader;
    private EncryptionProvider _encrypter;
    private String newVersionCheckLock = "new_version_check_lock";

    // Suppress Sonar warning that created objects are never used. The constructors are called to set static fields.
    @SuppressWarnings("squid:S1848")
    @Before
    public void setup() throws SecurityException, NoSuchFieldException, IllegalArgumentException, IllegalAccessException {
        new TestProductName();
        _encrypter = new TestEncryptonProvider();
        new TestSoftwareUpdate(_encrypter);
        _downloader = UpgradeImageDownloader.getInstance(null);
        RemoteRepository.setCoordinator(new TestCoordinatorClientExt());
    }

    @Test
    public void testCatalogRepository() throws Exception {
        repositoryUrl = CATALOG_SERVER_URL;
        _repo = RemoteRepository.getInstance();
        Assert.assertTrue(_repo != null);

        final List<SoftwareVersion> remoteVersions = _repo.getVersions();
        Assert.assertTrue(remoteVersions != null);
        Assert.assertTrue(!remoteVersions.isEmpty());

        for (SoftwareVersion v : remoteVersions) {
            System.out.println(v);
        }

        int downloadableVersions = 0;
        for (SoftwareVersion v : remoteVersions) {
            try {
                _repo.checkVersionDownloadable(v);
            } catch (RemoteRepositoryException e) {
                continue;
            } catch (BadRequestException e) {
                continue;
            }
            final InputStream in = _repo.getImageInputStream(v);
            Assert.assertTrue(in != null);
            byte[] buffer = new byte[0x10000];
            Assert.assertTrue("getImageInputStream failed for " + v, in.read(buffer) > 0);
            in.close();
            downloadableVersions++;

        }
        // Make sure there are at least some downloadable versiosn
        Assert.assertTrue(downloadableVersions > 0);
        System.out.println("Found " + downloadableVersions + " downloadable versions out of " + remoteVersions.size());
        SoftwareVersion version = null;
        // avoid version 121 since it is bad
        for (SoftwareVersion remoteVersion : remoteVersions) {
            // / Avoid a specific version on the downloads test site because it is no good
            if (0 != remoteVersion.compareTo(new SoftwareVersion("vipr-1.0.0.7.121"))) {
                version = remoteVersion;
                break;
            }
        }
        Assert.assertNotNull(version);

        File file = startBackgroundDownload(version);
        Assert.assertNotNull(file);
        while (_downloader.isDownloading()) {
            System.out.println("Downloading " + file);
            Thread.sleep(2000);
        }

        Assert.assertTrue(file.exists());
    }

    @Test
    public void testDirectoryRepository() throws Exception {
        repositoryUrl = DIRECTORY_REPO;
        _repo = RemoteRepository.getInstance();
        Assert.assertTrue(_repo != null);

        final List<SoftwareVersion> remoteVersions = _repo.getVersions();
        Assert.assertTrue(remoteVersions != null);
        Assert.assertTrue(!remoteVersions.isEmpty());

        for (SoftwareVersion v : remoteVersions) {
            System.out.println(v);
        }
        int downloadableVersions = 0;
        for (SoftwareVersion v : remoteVersions) {
            try {
                _repo.checkVersionDownloadable(v);
            } catch (RemoteRepositoryException e) {
                continue;
            } catch (BadRequestException e) {
                continue;
            }
            final InputStream in = _repo.getImageInputStream(v);
            Assert.assertTrue(in != null);
            byte[] buffer = new byte[0x10000];
            Assert.assertTrue("getImageInputStream failed for " + v, in.read(buffer) > 0);
            in.close();
            downloadableVersions++;

        }
        // Make sure there are at least some downloadable versiosn
        Assert.assertTrue(downloadableVersions > 0);
        System.out.println("Found " + downloadableVersions + " downloadable versions out of " + remoteVersions.size());
        final SoftwareVersion version = (SoftwareVersion) remoteVersions.toArray()[0];
        File file = startBackgroundDownload(version);
        Assert.assertNotNull(file);
        while (_downloader.isDownloading()) {
            System.out.println("Downloading " + file);
            Thread.sleep(2000);
        }

        Assert.assertTrue(file.exists());
    }

    @Test
    public void testRemoteDirectoryRepositoryViaProxy() throws Exception {
        repositoryProxy = REMOTE_PROXY;
        repositoryUrl = DIRECTORY_REPO;
        _repo = RemoteRepository.getInstance();
        Assert.assertTrue(_repo != null);

        final List<SoftwareVersion> remoteVersions = _repo.getVersions();
        Assert.assertTrue(remoteVersions != null);
        Assert.assertTrue(!remoteVersions.isEmpty());

        for (SoftwareVersion v : remoteVersions) {
            System.out.println(v);
        }
    }

    @Test
    public void testCatalogRepositoryViaProxy() throws Exception {
        repositoryProxy = REMOTE_PROXY;
        repositoryUrl = CATALOG_SERVER_URL;
        _repo = RemoteRepository.getInstance();
        Assert.assertTrue(_repo != null);

        final List<SoftwareVersion> remoteVersions = _repo.getVersions();
        Assert.assertTrue(remoteVersions != null);
        Assert.assertTrue(!remoteVersions.isEmpty());

        for (SoftwareVersion v : remoteVersions) {
            System.out.println(v);
        }
    }

    @Test
    public void testCachedVersions() throws Exception {

        repositoryProxy = null;
        newSoftwareVersions = null;
        repositoryUrl = null;
        RemoteRepository.startRemoteRepositoryCacheUpdate();
        _repo = RemoteRepository.getInstance();
        synchronized (newVersionCheckLock) {
            newVersionCheckLock.wait(60 * 1000);
        }
        Assert.assertTrue(newSoftwareVersions.getCachedVersions().isEmpty());

        repositoryUrl = CATALOG_SERVER_URL;
        _repo = RemoteRepository.getInstance();
        synchronized (newVersionCheckLock) {
            newVersionCheckLock.wait(60 * 1000);
        }
        Assert.assertEquals(_repo.getVersions(), newSoftwareVersions.getCachedVersions());
        Assert.assertEquals(_repo.toString(), newSoftwareVersions.getRepositoryInfo());

        repositoryUrl = DIRECTORY_REPO;
        _repo = RemoteRepository.getInstance();
        synchronized (newVersionCheckLock) {
            newVersionCheckLock.wait(60 * 1000);
        }
        Assert.assertEquals(_repo.getVersions(), newSoftwareVersions.getCachedVersions());
        Assert.assertEquals(_repo.toString(), newSoftwareVersions.getRepositoryInfo());

        long previousCheck = newSoftwareVersions.getLastVersionCheck();
        repositoryProxy = REMOTE_PROXY;
        _repo = RemoteRepository.getInstance();
        synchronized (newVersionCheckLock) {
            newVersionCheckLock.wait(60 * 1000);
        }
        Assert.assertTrue(newSoftwareVersions.getLastVersionCheck() > previousCheck);

        repositoryUrl = null;
        _repo = RemoteRepository.getInstance();
        synchronized (newVersionCheckLock) {
            newVersionCheckLock.wait(60 * 1000);
        }
        Assert.assertTrue(newSoftwareVersions.getCachedVersions().isEmpty());

    }

    @Test
    public void testAccessDenied() throws Exception {
        repositoryUrl = CATALOG_SERVER_URL;
        repositoryProxy = null;
        username = "tarter";
        _repo = RemoteRepository.getInstance();
        final List<SoftwareVersion> remoteVersions = _repo.getVersions();
        Assert.assertTrue(remoteVersions != null);
        Assert.assertTrue(!remoteVersions.isEmpty());
        try {
            _repo.checkVersionDownloadable(remoteVersions.get(0));
        } catch (BadRequestException e) {
            Assert.assertTrue(e.getMessage().contains("Verify that the supplied credentials have access to the URL"));
        }
    }

    @Test
    public void testBadCredentials() throws Exception {
        repositoryUrl = CATALOG_SERVER_URL;
        repositoryProxy = null;
        username = USERNAME;
        _repo = RemoteRepository.getInstance();
        final List<SoftwareVersion> remoteVersions = _repo.getVersions();
        Assert.assertTrue(remoteVersions != null);
        Assert.assertTrue(!remoteVersions.isEmpty());
        SoftwareVersion v = remoteVersions.get(0);
        password = "badpassword"; // NOSONAR ("squid:S2068 Suppressing sonar violation of hard-coded password")
        _repo = RemoteRepository.getInstance();
        try {
            _repo.checkVersionDownloadable(remoteVersions.get(0));
        } catch (RemoteRepositoryException e) {
            Assert.assertTrue(e.getMessage().contains("Log in to") && e.getMessage().contains("failed"));
        }
    }

    @Test
    public void testBadVersion() throws Exception {
        repositoryUrl = DIRECTORY_REPO;
        repositoryProxy = null;
        username = USERNAME;
        password = PASSWORD;
        _repo = RemoteRepository.getInstance();
        try {
            _repo.checkVersionDownloadable(new SoftwareVersion("1.0.0.0.688"));
        } catch (BadRequestException e) {
            Assert.assertTrue(e.getMessage().contains("not accessible at URL"));
        }

    }

    private File startBackgroundDownload(final SoftwareVersion version) {
        final File file = new File(DOWNLOAD_DIR + '/' + version + SOFTWARE_IMAGE_SUFFIX);
        final String prefix = MessageFormat.format("startBackGroundDownload(): version={0} path=\"{1}\": ", version, file);
        InputStream in;
        URL url;
        try {
            url = _repo.getImageURL(version);
            in = _repo.getImageInputStream(url);

        } catch (RemoteRepositoryException e) {
            System.out.println(prefix + e);
            return null;
        }
        System.out.println(prefix + "Starting backgroud download.");
        _downloader.startBackgroundDownload(prefix, file, in, url.toString(), version.toString());
        return file;
    }

    private class TestSoftwareUpdate extends SoftwareUpdate {
        public TestSoftwareUpdate(EncryptionProvider encrypter) {
            setCatalogName("STORAGEOS.SOFTWARE.EN_US.PRODUCTION");
            setCatalogServerHostNames(Collections.singletonList("colu-test.emc.com"));

            setEncryptionProvider(encrypter);
        }
    }

    private class TestEncryptonProvider implements EncryptionProvider {

        @Override
        public void start() {
            // TODO Auto-generated method stub

        }

        @Override
        public byte[] encrypt(String input) {
            try {
                return input.getBytes("UTF-8");
            } catch (UnsupportedEncodingException e) {
                throw new RuntimeException(e);
            }
        }

        @Override
        public String getEncryptedString(String input) {
            byte[] data = encrypt(input);
            try {
                return new String(Base64.encodeBase64(data), "UTF-8");
            } catch (UnsupportedEncodingException e) {
                // All JVMs must support UTF-8, this really can never happen
                throw new RuntimeException(e);
            }
        }

        @Override
        public String decrypt(byte[] input) {
            try {
                return new String(input, "UTF-8");
            } catch (UnsupportedEncodingException e) {
                throw new RuntimeException(e);
            }

        }

    }

    public class TestCoordinatorClientExt extends CoordinatorClientExt {

        @SuppressWarnings("unchecked")
        @Override
        public <T extends CoordinatorSerializable> T getTargetInfo(final Class<T> clazz) throws CoordinatorClientException {

            if (clazz.isAssignableFrom(PropertyInfoExt.class)) {
                Map<String, String> repoProperties = new HashMap<String, String>();
                repoProperties.put("system_update_repo", repositoryUrl);
                repoProperties.put("system_update_proxy", repositoryProxy);
                repoProperties.put("system_update_username", username);
                repoProperties.put("system_update_password", new String(Base64.encodeBase64(_encrypter.encrypt(password))));
                return (T) new PropertyInfoExt(repoProperties);
            } else if (clazz.isAssignableFrom(RemoteRepositoryCache.class)) {
                return (T) newSoftwareVersions;
            }
            return null;
        }

        @Override
        public void setTargetInfo(final CoordinatorSerializable info, boolean checkClusterUpgradable) throws CoordinatorClientException {
            if (info == null) {
                return;
            }

            if (info.getClass().isAssignableFrom(RemoteRepositoryCache.class)) {
                synchronized (newVersionCheckLock) {
                    newSoftwareVersions = (RemoteRepositoryCache) info;
                    newVersionCheckLock.notifyAll();
                }
            }
        }

        /**
         * The method which try and grant the non-persistent target version lock
         * 
         * @return True - If node gets the lock False - Otherwise
         */
        @Override
        public boolean getNewVersionLock() {
            return true;
        }

        /**
         * The method to release the non-persistent target version lock
         * 
         */
        @Override
        public void releaseNewVersionLock() {

        }
    }
}
