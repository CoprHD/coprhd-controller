/*
 * Copyright (c) 2012-2014 EMC Corporation
 * All Rights Reserved
 */

package com.emc.storageos.systemservices.impl.upgrade;

import org.json.simple.JSONObject;
import org.json.simple.parser.JSONParser;

import java.io.*;
import java.net.*;
import java.security.KeyManagementException;
import java.security.NoSuchAlgorithmException;
import java.security.cert.X509Certificate;
import java.text.MessageFormat;
import java.util.*;
import java.util.concurrent.*;

import javax.net.ssl.*;
import javax.swing.text.MutableAttributeSet;
import javax.swing.text.html.HTML;
import javax.swing.text.html.HTMLEditorKit;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.xpath.XPath;
import javax.xml.xpath.XPathConstants;
import javax.xml.xpath.XPathExpressionException;
import javax.xml.xpath.XPathFactory;

import com.emc.storageos.services.util.AlertsLogger;
import com.emc.storageos.svcs.errorhandling.resources.APIException;
import com.emc.storageos.svcs.errorhandling.resources.BadRequestException;
import com.emc.storageos.systemservices.exceptions.SyssvcException;
import org.apache.commons.codec.binary.Base64;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.w3c.dom.*;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;

import com.emc.storageos.services.util.Strings;
import com.emc.storageos.coordinator.client.model.SoftwareVersion;
import static com.emc.storageos.coordinator.client.model.Constants.*;
import com.emc.storageos.coordinator.exceptions.InvalidSoftwareVersionException;
import com.emc.storageos.systemservices.exceptions.RemoteRepositoryException;
import com.emc.storageos.systemservices.impl.upgrade.beans.SoftwareUpdate;
import com.emc.storageos.services.util.Waiter;

public class RemoteRepository {
    private static final Logger _log = LoggerFactory.getLogger(RemoteRepository.class);
    private URL _repo;
    private Proxy _proxy;
    private int _timeout;       // connect and read timeout
    private boolean _disabled = false;
    // List of image URLS
    List<URL> imageUrls = new ArrayList<URL>();

    // thread pool used to execute the new version check
    private static final int FIXED_THREAD_POOL_SIZE = 5;

    // create instance and invoke private constructor
    private static ExecutorService _executorService = null;
    private String _ssohost;
    private String _username;
    private String _password;
    private String _ctsession;
    private SSLSocketFactory _sslSocketFactory;
    private final int MAXIMUM_REDIRECT_ALLOWED = 10;
    private static volatile CoordinatorClientExt _coordinator;
    private static volatile RemoteRepositoryCacheUpdate _remoteRepositoryCacheUpdate;

    // constants for remote repository
    private final static String SYSTEM_UPDATE_REPO = "system_update_repo";
    private final static String SYSTEM_UPDATE_PROXY = "system_update_proxy";
    private final static String SYSTEM_UPDATE_USERNAME = "system_update_username";
    private final static String SYSTEM_UPDATE_PASSWORD = "system_update_password"; // NOSONAR
                                                                                   // ("squid:S2068 Suppressing sonar violation of hard-coded password")
    private final static String SYSTEM_UPDATE_CHECK_FREQUENCY_HOURS = "system_update_check_frequency_hours";
    // connect + read timeout constant
    private final static int SYSTEM_UPDATE_REPO_TIMEOUT = 30000;

    private static final String EMC_SSO_AUTH_SERVICE_PROTOCOL = "https";
    private static final String EMC_SSO_AUTH_SERVICE_HOST = "sso.emc.com";
    private static final String EMC_SSO_AUTH_SERVICE_TESTHOST = "sso-tst.emc.com";
    private static final String EMC_SSO_DOWNLOAD_SERVICE_HOST = "download.emc.com";
    private static final String EMC_SSO_DOWNLOAD_SERVICE_TESTHOST = "download-tst.emc.com";
    private static final String EMC_SSO_AUTH_SERVICE_URLPATH = "/authRest/service/auth.json";
    private static final String EMC_SSO_AUTH_SERVICE_LOGIN_POST_CONTENT = "<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?><user><password>{0}</password><username>{1}</username></user>";

    /**
     * Create singleton instance of ExecutorsService with a fixed thread pool.
     * 
     * @return ExecutorService
     */
    private synchronized static ExecutorService getExecutorServiceInstance() {
        if (_executorService == null) {
            _executorService = Executors.newFixedThreadPool(FIXED_THREAD_POOL_SIZE);
        }
        return _executorService;
    }

    /***
     * 
     * @param repoUrl - URL to the Software Update Repository
     * @param repoProxy - Proxy to access the Software Update Repository
     * @param password
     * @param username
     * @param timeout - Connect and read timeout
     */
    private RemoteRepository(String repoUrl, String repoProxy, String username, String password, int timeout) {
        _username = username;
        _password = password;
        _timeout = timeout;
        _proxy = Proxy.NO_PROXY;

        if (null == repoUrl || repoUrl.isEmpty()) {
            _disabled = true;
        } else {
            try {
                _repo = new URL(repoUrl);
                initializeSslContext();
            } catch (MalformedURLException e) {
                _log.error("Error in RemoteRepository Constructor: MalformedUrl found for remote repository URL " + repoUrl);
                _repo = null;
            } catch (KeyManagementException e) {
                throw APIException.internalServerErrors.initializeSSLContentError();
            } catch (NoSuchAlgorithmException e) {
                throw APIException.internalServerErrors.initializeSSLContentError();
            }
            if (repoProxy != null && !repoProxy.isEmpty()) {
                try {
                    URL repoProxyUrl = new URL(repoProxy);
                    _proxy = new Proxy(Proxy.Type.HTTP, new InetSocketAddress(repoProxyUrl.getHost(), repoProxyUrl.getPort()));
                } catch (MalformedURLException e) {
                    _log.error("Error in RemoteRepository Constructor: MalformedUrl found for proxy URL " + repoProxy);
                } catch (IllegalArgumentException e) {
                    _log.error("Error in RemoteRepository Constructor: Illegal argument for proxy URL {}, {}", repoProxy, e.getMessage());
                }
            }
        }
    }

    /**
     * Get an instance of the remote repository from the coordinator
     * 
     * @return remote repository instance
     * @throws Exception
     */
    public static RemoteRepository getInstance() throws Exception {
        Map<String, String> propInfo = _coordinator.getPropertyInfo().getProperties();
        return new RemoteRepository(propInfo.get(SYSTEM_UPDATE_REPO),
                propInfo.get(SYSTEM_UPDATE_PROXY),
                propInfo.get(SYSTEM_UPDATE_USERNAME),
                propInfo.get(SYSTEM_UPDATE_PASSWORD),
                SYSTEM_UPDATE_REPO_TIMEOUT);

    }

    public static void setCoordinator(CoordinatorClientExt coordinator) {
        _coordinator = coordinator;

    }

    /**
     * Get the cached list of software versions. Return an empty list if the cache is null
     * 
     * @return cached list of software versions
     * @throws Exception
     */
    public static Map<SoftwareVersion, List<SoftwareVersion>> getCachedSoftwareVersions() throws Exception {
        RemoteRepositoryCache cachedSoftwareVersions = _coordinator.getTargetInfo(RemoteRepositoryCache.class);
        return cachedSoftwareVersions == null ? Collections.<SoftwareVersion, List<SoftwareVersion>> emptyMap() : cachedSoftwareVersions
                .getCachedVersions();
    }

    @Override
    public String toString() {
        return MessageFormat.format("repo={0} proxy={1}", _repo, _proxy);
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        }
        if (obj == null) {
            return false;
        }
        if (getClass() != obj.getClass()) {
            return false;
        }
        RemoteRepository other = (RemoteRepository) obj;
        if (_proxy == null) {
            if (other._proxy != null) {
                return false;
            }
        } else if (!_proxy.equals(other._proxy)) {
            return false;
        }
        if (_repo == null) {
            if (other._repo != null) {
                return false;
            }
        } else if (!_repo.equals(other._repo)) {
            return false;
        }
        return true;
    }

    @Override
    public int hashCode() {
        return Objects.hash(_proxy, _repo);
    }

    /***
     * Get an InputStream to the URL within the Software Update Repository
     * Use the HTTP proxy if needed.
     * 
     * @param version - Version number
     * @return InputStream - Opened input stream to the image
     */
    public InputStream getImageInputStream(final SoftwareVersion version) throws RemoteRepositoryException {
        try {
            return getImageInputStream(parseRepository().get(version));
        } catch (RemoteRepositoryException e) {
            throw e;
        } catch (Exception e) {
            throw SyssvcException.syssvcExceptions.remoteRepoError(MessageFormat.format(
                    "Failed to open an input stream for version={0}: {1}", version, e));
        }
    }

    /**
     * Get the size of the image file of a version
     * 
     * @param version
     */
    public int checkVersionSize(final SoftwareVersion version) {
        URL imageUrl = getImageURL(version);
        HttpURLConnection urlConnection = invokeRequest(imageUrl);
        return urlConnection.getContentLength();
    }

    /**
     * Check that a version can be downloaded
     * 
     * @param version
     */
    public void checkVersionDownloadable(final SoftwareVersion version) {
        URL imageUrl = getImageURL(version);
        HttpURLConnection urlConnection;
        InputStream is;

        try {
            urlConnection = invokeRequest(imageUrl);
            is = urlConnection.getInputStream();
        } catch (RemoteRepositoryException e) {
            throw e;
        } catch (Exception e) {
            // log the exception then throw a bad request exception instead
            _log.error("Caught an exception trying to access " + version.toString() + " at url " + imageUrl.toString(), e);
            throw BadRequestException.badRequests.invalidImageUrl(version.toString(), imageUrl.toString());
        }

        // The URL is valid. Check that a binary file will be downloaded.
        try {
            // Throw an exception if the content is html
            // the content should be a binary type so it
            // is safe to assume the content is an error
            // page.
            if (null != urlConnection.getContentType() && urlConnection.getContentType().contains("html")) {
                // try to grab some bytes and dump it to the logs
                // in case the page has some detailed information
                byte[] buffer = new byte[256];
                try {
                    if (is.read(buffer) > -1) {
                        _log.error("Downloaded error page when attempting to get version " + version.toString() +
                                " from url " + imageUrl.toString() + " error page contents: " +
                                new String(buffer, "UTF-8"));
                    }
                } catch (Exception e) {
                    // ignore exceptions trying to get detailed content
                }
                throw BadRequestException.badRequests.downloadFailed(version.toString(), imageUrl.toString());
            }
        } finally {
            try {
                is.close();
            } catch (Exception e) {
                // ignore errors trying to close the input stream
            }
        }
    }

    /***
     * Get an InputStream to the URL within the Software Update Repository
     * Use the HTTP proxy if needed.
     * 
     * @param url - A URL within the remote repository
     * @return InputStream - Opened input stream to the image
     */
    public InputStream getImageInputStream(final URL url) throws RemoteRepositoryException {
        try {
            return invokeRequest(url).getInputStream();
        } catch (RemoteRepositoryException e) {
            throw e;
        } catch (Exception e) {
            throw SyssvcException.syssvcExceptions.remoteRepoError(MessageFormat.format(
                    "Failed to open an input stream for url={0} proxy={1}: {2}", url, _proxy, e));
        }
    }

    /**
     * Get URL for a software version image in the repository
     * 
     * @param version ViPR software version
     * @return URL of the version image in the repository
     */
    public URL getImageURL(final SoftwareVersion version) {
        return parseRepository().get(version);
    }

    /***
     * 
     * @return Map<SoftwareVersion, URL> containg a list of the available versions and their URLs
     */
    public List<SoftwareVersion> getVersions() throws RemoteRepositoryException {
        if (_disabled) {
            _log.debug("Check for software versions is disabled.");
            return new ArrayList<SoftwareVersion>();
        }
        final Map<SoftwareVersion, URL> versions = parseRepository();
        _log.info("Getting available versions from url: {}", _repo);
        _log.debug("Available versions: {}", Strings.repr(versions));

        return new ArrayList<SoftwareVersion>(versions.keySet());
    }

    /**
     * Read a remote repository. Return an input stream and the content-type
     * 
     * @return RepositoryContent object that contains an input stream of the content and the type of content
     * @throws RemoteRepositoryException
     */
    private RepositoryContent readRepository(URL repo) throws RemoteRepositoryException {
        return readRepository(repo, 0); // There was 0 redirects at the initial redirect
    }

    /**
     * Read a remote repository. Return an input stream and the content-type
     * 
     * @param The url of the repository to read
     * @param A redirect counter to keep track of the number of redirects. There is a upper limit on the redirects
     * @return RepositoryContent object that contains an input stream of the content and the type of content
     * @throws RemoteRepositoryException
     */
    private RepositoryContent readRepository(URL repo, int redirectCount) throws RemoteRepositoryException {
        try {
            _log.debug("Repository URL is: " + repo.toString());
            HttpURLConnection httpCon = prepareConnection(repo);
            httpCon.setInstanceFollowRedirects(false);
            httpCon.addRequestProperty("User-Agent", "Mozilla");
            if (SoftwareUpdate.isCatalogServer(repo)) {
                writePostContent(httpCon, SoftwareUpdate.getCatalogPostContent(repo));
            } else {
                httpCon.connect();
                _log.debug("The return code of the connection is: " + httpCon.getResponseCode());
                if (httpCon.getResponseCode() == HttpURLConnection.HTTP_MOVED_PERM
                        || httpCon.getResponseCode() == HttpURLConnection.HTTP_MOVED_TEMP) {
                    redirectCount++;
                    if (redirectCount > MAXIMUM_REDIRECT_ALLOWED) {
                        throw SyssvcException.syssvcExceptions.remoteRepoError("Too many redirects! Quit connection!");
                    }
                    URL forwardedTo = new URL(httpCon.getHeaderField("Location"));
                    _log.info("Connecting to URL " + repo.toString() + " redirected to " + forwardedTo);
                    return readRepository(forwardedTo, redirectCount);
                }
            }
            return new RepositoryContent(httpCon.getContentType(), httpCon.getInputStream(), repo);
        } catch (Exception e) {
            throw SyssvcException.syssvcExceptions.remoteRepoError(MessageFormat.format("Failed to read repository {0} ({1})", _repo, e));
        }

    }

    // Parse the remote repository into a Map<SoftwareVersion, URL>
    //
    private Map<SoftwareVersion, URL> parseRepository() throws RemoteRepositoryException {
        RepositoryContent repositoryContent = readRepository(_repo);
        _log.debug("Parsing repository URL: the URL after redirections is " + repositoryContent.getRepoURL());
        try {
            if (repositoryContent.getContentType().toLowerCase().contains("text/xml")) {
                return parseCatalog(readCatalog(readInputStream(repositoryContent.getContentStream())));
            } else {
                return parseDirectory(repositoryContent.getRepoURL(), readInputStream(repositoryContent.getContentStream()));
            }
        } catch (RemoteRepositoryException e) {
            throw e;
        } catch (Exception e) {
            throw SyssvcException.syssvcExceptions.remoteRepoError(
                    MessageFormat.format("Failed to parse the remote repository {0} input={1} ({2})",
                            _repo, Strings.repr(repositoryContent), e));
        } finally {
            try {
                if (null != repositoryContent.getContentStream()) {
                    repositoryContent.getContentStream().close();
                }
            } catch (IOException e) {
                _log.error("Failed to close input stream: " + e);
            }
        }
    }

    /**
     * @param is InputStream of the remote directory to read
     * @return the remote repository directory as a string
     * @throws RemoteRepositoryException
     * @throws IOException
     */
    private String readInputStream(InputStream is) throws RemoteRepositoryException, IOException {
        StringBuilder input = new StringBuilder();

        Reader in = new InputStreamReader(is, "UTF-8");
        char[] buffer = new char[0x10000];
        while (true) {
            int read = in.read(buffer, 0, buffer.length);
            if (read <= 0) {
                break;
            }
            input.append(buffer, 0, read);
        }
        in.close();
        return input.toString();
    }

    /**
     * Read an EMC catalog containing ViPR software updates
     * 
     * @param input InputStream of the catalog
     * @return the catalog as a string
     * @throws SAXException
     * @throws IOException
     * @throws ParserConfigurationException
     * @throws DOMException
     * @throws XPathExpressionException
     * @throws RemoteRepositoryException
     */
    private String readCatalog(String input) throws SAXException, IOException, ParserConfigurationException, DOMException,
            XPathExpressionException, RemoteRepositoryException {
        _log.debug("Reading catalog: {}", input);
        DocumentBuilderFactory dbFactory = DocumentBuilderFactory.newInstance();
        dbFactory.setExpandEntityReferences(false);
        DocumentBuilder dBuilder = dbFactory.newDocumentBuilder();
        Document doc = dBuilder.parse(new InputSource(new StringReader(input)));
        XPath xPath = XPathFactory.newInstance().newXPath();
        Element downloadUpdatesElement = (Element) xPath.compile("//downloadUpdatesOUT").evaluate(doc, XPathConstants.NODE);
        if (null == downloadUpdatesElement) {
            throw SyssvcException.syssvcExceptions.remoteRepoError(MessageFormat.format("Invalid catalog recieved from {0} catalog: {1}",
                    _repo, input));
        }
        String hasErrors = downloadUpdatesElement.getElementsByTagName("hasErrors").item(0).getTextContent();
        if (Boolean.parseBoolean(hasErrors)) {
            String errorMessage = downloadUpdatesElement.getElementsByTagName("errorString").item(0).getTextContent();
            throw SyssvcException.syssvcExceptions.remoteRepoError(MessageFormat.format("Error receiving catalog from {0} error: {1}",
                    _repo, errorMessage));
        }
        String encodedCatalog = downloadUpdatesElement.getElementsByTagName("encodedXML").item(0).getTextContent();
        return new String(Base64.decodeBase64(encodedCatalog.getBytes("UTF-8")), "UTF-8");

    }

    /**
     * Parse the remote repository directory string
     * 
     * @param input the remote repository directory string representation
     * @return a map of software versions and file URLs
     * @throws IOException
     * @throws RemoteRepositoryException
     */
    private Map<SoftwareVersion, URL> parseDirectory(URL url, String input)
            throws IOException, RemoteRepositoryException {
        Map<SoftwareVersion, URL> versions = new HashMap<SoftwareVersion, URL>();
        ParserCallback callback = new ParserCallback(url, versions);
        new HTMLEditor().getParser().parse(new StringReader(input.toString()), callback, true);

        return versions;
    }

    /**
     * Parse the EMC software update string representation
     * 
     * @param input the EMC software catalog string representation
     * @return a map of software version to remote file URLs
     * @throws ParserConfigurationException
     * @throws SAXException
     * @throws IOException
     * @throws XPathExpressionException
     * @throws InvalidSoftwareVersionException
     * @throws MalformedURLException
     * @throws RemoteRepositoryException
     */
    private Map<SoftwareVersion, URL> parseCatalog(String input)
            throws ParserConfigurationException, SAXException, IOException,
            XPathExpressionException, InvalidSoftwareVersionException,
            MalformedURLException, RemoteRepositoryException {
        Map<SoftwareVersion, URL> versions = new HashMap<SoftwareVersion, URL>();
        DocumentBuilderFactory dbFactory = DocumentBuilderFactory.newInstance();
        DocumentBuilder dBuilder = dbFactory.newDocumentBuilder();
        Document doc = dBuilder.parse(new InputSource(new StringReader(input)));
        XPath xPath = XPathFactory.newInstance().newXPath();
        NodeList fileList = (NodeList) xPath.compile("//File").evaluate(doc, XPathConstants.NODESET);
        for (int fileItr = 0; fileItr < fileList.getLength(); fileItr++) {
            Node fileNode = fileList.item(fileItr);
            Element element = (Element) fileNode;
            Node nameNode = element.getAttributeNode("Name");
            if (null != nameNode) {
                String fileName = nameNode.getNodeValue();
                if (fileName.endsWith(SOFTWARE_IMAGE_SUFFIX)) {
                    String fileVersion = fileName.replace(SOFTWARE_IMAGE_SUFFIX,"");
                    Node urlNode = element.getAttributeNode("URL");
                    String fileUrl = urlNode.getNodeValue();
                    versions.put(new SoftwareVersion(fileVersion), new URL(fileUrl));
                }
            }
        }
        if (versions.isEmpty()) {
            throw SyssvcException.syssvcExceptions.remoteRepoError("Empty remote repository: " + _repo);
        }
        return versions;
    }

    /**
     * Send a post request with content to the specified connection
     * 
     * @param connection connection to URL
     * @param postContent content to post
     * @throws Exception
     */
    private void writePostContent(HttpURLConnection connection, String postContent) throws Exception {
        connection.setRequestMethod("POST");
        connection.addRequestProperty("Content-Type",
                "application/xml; charset=utf-8");
        // set the output and input to true
        connection.setDoOutput(true);
        connection.setDoInput(true);
        connection.setAllowUserInteraction(false);
        // set the content length
        DataOutputStream dstream = null;
        try {
            connection.connect();
            dstream = new DataOutputStream(connection.getOutputStream());
            // write the post content
            dstream.writeBytes(postContent);
            dstream.flush();
        } finally {
            // flush the stream
            if (dstream != null) {
                try {
                    dstream.close();
                } catch (Exception ex) {
                    _log.error("Exception while closing the stream." +
                            " Exception: " + ex,
                            "WebClient._writePostContent()");
                }
            }
        }
    }

    public static void startRemoteRepositoryCacheUpdate() {
        if (!_coordinator.isControlNode()) {
            return;
        }

        if (null == _remoteRepositoryCacheUpdate) {
            _remoteRepositoryCacheUpdate = new RemoteRepositoryCacheUpdate();
            getExecutorServiceInstance().submit(_remoteRepositoryCacheUpdate);
        } else {
            throw new IllegalStateException("New version check thread is already started");
        }
    }

    public static void wakeupNewVersionCheck() {
        if (null == _remoteRepositoryCacheUpdate) {
            startRemoteRepositoryCacheUpdate();
        } else {
            _remoteRepositoryCacheUpdate.wakeup();
        }

    }

    public static void stopRemoteRepositoryCacheUpdate() {
        if (null != _remoteRepositoryCacheUpdate) {
            _remoteRepositoryCacheUpdate.stop();
        }
    }

    /**
     * Class that is used to refresh the list of new versions in the remote repository.
     * The check is executed on frequency that can be changed by the user. A cached list of new
     * versions is updated in the coordinator whenever the check is successfully executed.
     */
    private static class RemoteRepositoryCacheUpdate implements Runnable {
        private static final long DEFAULT_FREQUENCY = 24 * 60 * 60 * 1000; // 24 hours
        private static final long MINIMUM_FREQUENCY = 60 * 60 * 1000; // 1 hour
        private static final long WAIT = 5 * 1000;
        boolean _run = true;
        private final Waiter _waiter = new Waiter();

        private RemoteRepositoryCacheUpdate() {
        }

        @Override
        public void run() {
            while (_run) {
                if (_coordinator.getNewVersionLock()) {
                    try {
                        Map<String, String> propInfo = _coordinator.getPropertyInfo().getProperties();
                        long softwareVersionCheckFrequencyMillis = DEFAULT_FREQUENCY;
                        if (null != propInfo.get(SYSTEM_UPDATE_CHECK_FREQUENCY_HOURS)) {   // use the configured check frequency
                            int frequencyHours = Integer.parseInt(propInfo.get(SYSTEM_UPDATE_CHECK_FREQUENCY_HOURS));
                            if (frequencyHours < TimeUnit.MILLISECONDS.toHours(MINIMUM_FREQUENCY)) {
                                _log.warn("Software version check frequency cannot be less than {} hour",
                                        TimeUnit.MILLISECONDS.toHours(MINIMUM_FREQUENCY));
                                softwareVersionCheckFrequencyMillis = MINIMUM_FREQUENCY;
                            } else {
                                softwareVersionCheckFrequencyMillis = TimeUnit.HOURS.toMillis(frequencyHours);
                            }
                        }
                        RemoteRepository remoteRepository = getInstance();
                        RemoteRepositoryCache remoteRepositoryCache = _coordinator.getTargetInfo(RemoteRepositoryCache.class); // get cached
                                                                                                                               // repository
                                                                                                                               // info
                        boolean bDoCheck = false; // a flag indicating if a reread of the repository is needed
                        long now = System.currentTimeMillis();
                        if (null == remoteRepositoryCache) {
                            bDoCheck = true;
                            _log.debug("Performing new version check because there is no new software version cached");
                        } else if (!remoteRepositoryCache.getRepositoryInfo().equals(remoteRepository.toString())) {
                            bDoCheck = true;
                            _log.debug("Performing new version check because remote repository info changed");
                        } else {
                            if (now - remoteRepositoryCache.getLastVersionCheck() > softwareVersionCheckFrequencyMillis) {
                                bDoCheck = true;
                                _log.debug("Performing new version check because a check hasn't been performed in at least {} hours",
                                        TimeUnit.MILLISECONDS.toHours(softwareVersionCheckFrequencyMillis));
                            }
                        }
                        if (bDoCheck) {
                            String repository = remoteRepository.toString();
                            Map<SoftwareVersion, List<SoftwareVersion>> softwareVersionMap;
                            try {
                                softwareVersionMap = remoteRepository.getVersionsWithMetadata();
                                _log.info("Got the versions with metadata from repo: " + softwareVersionMap);
                            } catch (Exception e) {
                                AlertsLogger.getAlertsLogger().error(
                                        "Failed to get the list of software versions from remote repository: " + e.getMessage()
                                                + " please verify ViPR's Internet connection and check if the repository URL("
                                                + remoteRepository.toString() + ") is correct.");
                                // Set the list to empty if it didn't exist or if whatever used to be cached
                                // is from a different repository.
                                // That way we can still update the time the check was last performed
                                // so we're not churning this check on a bad repository configuration
                                if (null != remoteRepositoryCache
                                        && remoteRepositoryCache.getRepositoryInfo().equals(remoteRepository.toString())) {
                                    softwareVersionMap = remoteRepositoryCache.getCachedVersions();
                                } else {
                                    softwareVersionMap = Collections.<SoftwareVersion, List<SoftwareVersion>> emptyMap();
                                }
                            }
                            _log.debug("Caching software versions: {}", softwareVersionMap);
                            _coordinator.setTargetInfo(new RemoteRepositoryCache(softwareVersionMap, now, repository), false);
                        } else {
                            _log.debug("No version check necessary");
                        }
                    } catch (Exception e) {
                        _log.error("Get new software versions failed: ", e);
                    } finally {
                        _coordinator.releaseNewVersionLock();
                    }
                } else {
                    _log.debug("Failed to get new software versions check lock");
                }
                _waiter.sleep(WAIT);
            }
        }

        private void stop() {
            _run = false;
            wakeup();
        }

        private void wakeup() {
            _waiter.wakeup();
        }

    }

    /**
     * Invoke a request to the URL
     * For images in download-tst.emc.com, using sso-tst.emc.com as auth source
     * For images in download.emc.com, using sso.emc.com as auth source
     * 
     * @param url
     * @return a connection to the URL that was sent the request
     * @throws RemoteRepositoryException
     */
    private HttpURLConnection invokeRequest(URL url) throws RemoteRepositoryException {
        if (url.getProtocol().equalsIgnoreCase(EMC_SSO_AUTH_SERVICE_PROTOCOL) &&
                url.getHost().equalsIgnoreCase(EMC_SSO_DOWNLOAD_SERVICE_TESTHOST)) {
            _ssohost = EMC_SSO_AUTH_SERVICE_TESTHOST;
        } else if (url.getProtocol().equalsIgnoreCase(EMC_SSO_AUTH_SERVICE_PROTOCOL) &&
                url.getHost().equalsIgnoreCase(EMC_SSO_DOWNLOAD_SERVICE_HOST)) {
            _ssohost = EMC_SSO_AUTH_SERVICE_HOST;
        } else {
            _ssohost = null;
        }
        if (_ssohost != null) {
            login();
        }
        return connectImage(url);
    }

    /**
     * The EMC SSO service (https://sso.emc.com) authenticates uses and issues authentication
     * tokens that enable access to other EMC services, in particular to the EMC download repository (https://download.emc.com).
     * Request:
     * URI: http://sso.emc.com/authRest/service/auth.json
     * Format: XML
     * HTTP Method: POST
     * Request Body for customer, partner or lite users:
     * <?xml version="1.0" encoding="UTF-8"
     * standalone="yes"?><user><password>##########</password><username>johndoe@acme.com</username></user>
     * Request Body for employee
     * <?xml version="1.0" encoding="UTF-8" standalone="yes"?><user><password>pin+fob/softtoken</password><username>emp nt</username></user>
     * 
     * Response:
     * 1. Example for successful response:
     * {
     * "object": {
     * "authResult": {
     * "status":"SUCCESS",
     * "operation":"VALID_USER",
     * "token":"AAAAAgABAFBLtr+WcJAh+DJ1Q2GXYiH0PC5+Txuscy1+pU7TRpAcUoyfhNwB55DZwPCZlQwgVpyY+vaOYNblApcSOZ+hEWFzIxj1JtII/ozshY+33ddafg==",
     * "userProps":[{"propName":"LAST_NAME","propValue":"[TestFour]"},{"propName":"GIVEN_NAME","propValue":"[AlphaFour]"},{"propName":"UID",
     * "propValue":"[1110000003]"},{"propName":"EMC_IDENTITY","propValue":"[C]"}]
     * }
     * },
     * "serviceFault":null
     * }
     * 2. Example for failure response:
     * {
     * "object": {
     * "authResult": {
     * "status": "FAILED",
     * "operation": "INVALID_USERNAME_PASSWORD",
     * "token": null,
     * "userProps": null
     * }
     * },
     * "serviceFault":null
     * }
     * 
     * @param username
     * @param password
     */
    private void login() {
        _log.info("{} is trying to login ...", _username);
        try {
            URL url = new URL(EMC_SSO_AUTH_SERVICE_PROTOCOL, _ssohost, EMC_SSO_AUTH_SERVICE_URLPATH);
            HttpURLConnection httpCon = prepareConnection(url);
            httpCon.setInstanceFollowRedirects(false);
            String loginContent = SoftwareUpdate.getDownloadLoginContent(_username, _password);
            writePostContent(httpCon, loginContent);

            InputStream in = httpCon.getInputStream();
            if (in == null) {
                throw new IllegalArgumentException("in is null");
            }

            // use InputStreamReader to read charset encoding gracefully
            BufferedReader rd = new BufferedReader(new InputStreamReader(in));
            String line;
            StringBuffer response = new StringBuffer();
            while ((line = rd.readLine()) != null) {
                response.append(line);
                response.append('\r');
            }
            rd.close();
            String s = response.toString();
            _log.debug("Response body: " + s);

            JSONParser parser = new JSONParser();
            JSONObject obj = (JSONObject) parser.parse(s);
            JSONObject authObj = (JSONObject) obj.get("object");
            JSONObject authResultObj = (JSONObject) authObj.get("authResult");
            if (authResultObj.get("status").toString().equalsIgnoreCase("SUCCESS")) {
                _log.info("{} login EMC SSO service successfully", _username);
                if (authResultObj.get("token") != null) {
                    _ctsession = authResultObj.get("token").toString();
                    _log.debug("From EMC SSO service, user {} obtained CTSESSION cookie: {}", _username, _ctsession);
                } else {
                    throw new IllegalArgumentException("Failed to parse ctsession token as expected");
                }
            } else if (authResultObj.get("status").toString().equalsIgnoreCase("FAILED")) {
                JSONObject serviceFaultObj = (JSONObject) obj.get("serviceFault");
                String errstr = "";
                if (serviceFaultObj != null) {
                    errstr = "Please contact with EMC customer support.  EMC SSO service failed:" + serviceFaultObj.toString();
                } else {
                    String operation = authResultObj.get("operation").toString();
                    errstr = "EMC SSO authentication result is " + operation;
                }
                _log.error(errstr);
                throw SyssvcException.syssvcExceptions.remoteRepoError(errstr);
            }
        } catch (Exception e) {
            throw SyssvcException.syssvcExceptions.remoteRepoError(
                    MessageFormat.format("User {0} failed to login {1}: {2}", _username, EMC_SSO_AUTH_SERVICE_URLPATH, e));
        }
    }

    /**
     * Connect with remote target url for download
     * Client needs to read the token from returned JSON object for login request.
     * Token aka CTSESSION is one of the important attribute to access any application or rest services that is protected behind RSA.
     * For Example: if support zone rest needs to be accessed then use the auth rest service read the token and set the cookie header with
     * the token as shown
     * For download request:
     * URI: image url
     * Format: XML
     * HTTP Method: GET
     * HTTP Header: CTSESSION="token"
     * 
     * @param url
     * @return HttpURLConnection
     * @throws RemoteRepositoryException
     */
    private HttpURLConnection connectImage(URL url) throws RemoteRepositoryException {
        try {
            _log.info("Connecting to URL: {}", url.toString());
            HttpURLConnection httpCon = prepareConnection(url);

            httpCon.setInstanceFollowRedirects(false);
            String cookie = "CTSESSION=" + _ctsession;
            httpCon.setRequestProperty("Cookie", cookie);
            httpCon.setRequestMethod("GET");
            httpCon.connect();
            if (httpCon.getResponseCode() != HttpURLConnection.HTTP_OK) {
                _log.info("connect image request return {}", httpCon.getResponseCode());
                throw new IllegalArgumentException("Http error code:" + httpCon.getResponseCode());
            }
            _log.info("Image is located successfully and its size is " + httpCon.getContentLength());
            return httpCon;
        } catch (Exception e) {
            throw SyssvcException.syssvcExceptions.remoteRepoError(
                    MessageFormat.format("User {0} failed to connect with remote image {1}: {2}", _username, url.toString(), e));
        }
    }

    /**
     * Initialize the SSL context for connecting to a remote repository
     * 
     * @throws NoSuchAlgorithmException
     * @throws KeyManagementException
     */
    private void initializeSslContext() throws NoSuchAlgorithmException, KeyManagementException {

        // Create a trust manager that does not validate certificate chains
        final TrustManager[] trustAllCerts = new TrustManager[] { new X509TrustManager() {
            @Override
            public void checkClientTrusted(final X509Certificate[] chain, final String authType) {
            }

            @Override
            public void checkServerTrusted(final X509Certificate[] chain, final String authType) {
            }

            @Override
            public X509Certificate[] getAcceptedIssuers() {
                return null;
            }
        } };
        // Install the all-trusting trust manager
        SSLContext sslContext;

        sslContext = SSLContext.getInstance("SSL");
        sslContext.init(null, trustAllCerts, new java.security.SecureRandom());
        // Create an ssl socket factory with our all-trusting manager
        _sslSocketFactory = sslContext.getSocketFactory();

    }

    /**
     * Open a URL connection and set the SSL context factory if necessary
     * 
     * @param url
     * @return a connection to the URL
     * @throws IOException
     */
    private HttpURLConnection prepareConnection(URL url) throws IOException {
        HttpURLConnection connection;
        connection = (HttpURLConnection) url.openConnection(_proxy);
        connection.setConnectTimeout(_timeout);
        connection.setReadTimeout(_timeout);
        if (url.getProtocol().equalsIgnoreCase(EMC_SSO_AUTH_SERVICE_PROTOCOL)) {
            ((HttpsURLConnection) connection).setSSLSocketFactory(_sslSocketFactory);
        }
        return connection;

    }

    /**
     * Class to hold the remote repository input stream and content type
     * 
     */
    private class RepositoryContent {
        private String _contentType;
        private InputStream _contentStream;
        private URL _repoURL;

        public URL getRepoURL() {
            return _repoURL;
        }

        public RepositoryContent(String contentType, InputStream contentStream, URL repoURL) {
            _contentType = contentType;
            _contentStream = contentStream;
            _repoURL = repoURL;
        }

        public String getContentType() {
            return _contentType;
        }

        public InputStream getContentStream() {
            return _contentStream;
        }
    }

    // Parser callback used by parseDirectory()
    //
    private class ParserCallback extends HTMLEditorKit.ParserCallback {

        private URL _url;
        private Map<SoftwareVersion, URL> _versionsMap;

        public ParserCallback(URL url, Map<SoftwareVersion, URL> versionsMap) {
            _url = url;
            _versionsMap = versionsMap;
        }

        @Override
        public void handleStartTag(HTML.Tag t, MutableAttributeSet a, int pos) {
            if (t == HTML.Tag.A) {
                // Extract the
                final String href = (String) a.getAttribute(HTML.Attribute.HREF);
                final String logMsg = "getVersions(): href=" + href;

                try {
                    URL subdirUrl = new URL(_url, href);
                    String[] subdirFileParts = subdirUrl.getFile().split("/");
                    SoftwareVersion version = new SoftwareVersion(subdirFileParts[subdirFileParts.length - 1]);
                    URL imageUrl = new URL(subdirUrl, version.toString() + SOFTWARE_IMAGE_SUFFIX);
                    _log.debug(logMsg + " version=" + version + " url=" + imageUrl);
                    _versionsMap.put(version, imageUrl);
                } catch (Exception e) {
                    _log.debug("Ignored. " + logMsg + ": " + e);
                }
            }
        }
    }

    private class HTMLEditor extends HTMLEditorKit {
        public HTMLEditorKit.Parser getParser() {
            return super.getParser();
        }
    }

    public List<SoftwareVersion> getUpgradeFromVersions(SoftwareVersion version) throws Exception {
        RemoteRepositoryCache remoteRepositoryCache = _coordinator.getTargetInfo(RemoteRepositoryCache.class); // get cached repository info
        Map<SoftwareVersion, List<SoftwareVersion>> cachedVersionMap = remoteRepositoryCache.getCachedVersions();
        if (cachedVersionMap.containsKey(version)) {
            return cachedVersionMap.get(version);
        }
        // The target version is not cached, need to recache the repository
        String repository = this.toString();
        Map<SoftwareVersion, List<SoftwareVersion>> softwareVersionMap;
        try {
            softwareVersionMap = this.getVersionsWithMetadata();
            _log.info("Got the versions with metadata from repo: " + softwareVersionMap);
        } catch (Exception e) {
            AlertsLogger.getAlertsLogger().error(
                    "Failed to get the list of software versions from remote repository: " + e.getMessage()
                            + " please verify ViPR's Internet connection and check if the repository URL(" + this.toString()
                            + ") is correct.");
            // Set the list to empty if it didn't exist or if whatever used to be cached
            // is from a different repository.
            // That way we can still update the time the check was last performed
            // so we're not churning this check on a bad repository configuration
            if (null != remoteRepositoryCache && remoteRepositoryCache.getRepositoryInfo().equals(repository)) {
                softwareVersionMap = remoteRepositoryCache.getCachedVersions();
            } else {
                softwareVersionMap = Collections.<SoftwareVersion, List<SoftwareVersion>> emptyMap();
            }
        }
        _log.debug("Caching software versions: {}", softwareVersionMap);
        _coordinator.setTargetInfo(new RemoteRepositoryCache(softwareVersionMap, System.currentTimeMillis(), repository), false);
        if (!softwareVersionMap.containsKey(version)) {
            // log the exception then throw a bad request exception instead
            _log.error("Version: " + version.toString() + "does not exist!");
            throw BadRequestException.badRequests.versionNotExist(version.toString());
        }
        return softwareVersionMap.get(version);
    }

    public Map<SoftwareVersion, List<SoftwareVersion>> getVersionsWithMetadata() {
        RepositoryContent repositoryContent = readRepository(_repo);
        try {
            if (repositoryContent.getContentType().toLowerCase().contains("text/xml")) {
                return getVersionsWithMetadataCatalog(readCatalog(readInputStream(repositoryContent.getContentStream())));
            } else {
                return getVersionsWithMetadataDirectory();
            }
        } catch (RemoteRepositoryException e) {
            throw e;
        } catch (Exception e) {
            throw SyssvcException.syssvcExceptions.remoteRepoError(
                    MessageFormat.format("Failed to parse the remote repository {0} input={1} ({2})",
                            _repo, Strings.repr(repositoryContent), e));
        } finally {
            try {
                if (null != repositoryContent.getContentStream()) {
                    repositoryContent.getContentStream().close();
                }
            } catch (IOException e) {
                _log.error("Failed to close input stream: " + e);
            }
        }
    }

    private Map<SoftwareVersion, List<SoftwareVersion>> getVersionsWithMetadataCatalog(
            String catalogString) throws ParserConfigurationException, SAXException, IOException,
            XPathExpressionException, InvalidSoftwareVersionException,
            MalformedURLException, RemoteRepositoryException {
        Map<SoftwareVersion, List<SoftwareVersion>> map = new HashMap<SoftwareVersion, List<SoftwareVersion>>();
        DocumentBuilderFactory dbFactory = DocumentBuilderFactory.newInstance();
        DocumentBuilder dBuilder = dbFactory.newDocumentBuilder();
        Document doc = dBuilder.parse(new InputSource(new StringReader(catalogString)));
        XPath xPath = XPathFactory.newInstance().newXPath();
        NodeList fileList = (NodeList) xPath.compile("//File").evaluate(doc, XPathConstants.NODESET);
        for (int fileItr = 0; fileItr < fileList.getLength(); fileItr++) {
            Node fileNode = fileList.item(fileItr);
            Element element = (Element) fileNode;
            Node nameNode = element.getAttributeNode("Name");
            if (null != nameNode) {
                String fileName = nameNode.getNodeValue();
                if (fileName.endsWith(SOFTWARE_IMAGE_SUFFIX)) {
                    String fileVersion = fileName.replace(SOFTWARE_IMAGE_SUFFIX,"");
                    Node catalogInfoNode = element.getAttributeNode("CatalogInfo");
                    String catalogInfo = catalogInfoNode.getNodeValue();
                    SoftwareVersion tempVersion = new SoftwareVersion(fileVersion);
                    List<SoftwareVersion> tempList = new ArrayList<SoftwareVersion>();
                    if (!catalogInfo.equals("")) {
                        String upgradeFromInfoRaw = null;
                        for (String s : catalogInfo.split(",")) { // key-value pairs are separated by comma
                            if (s.startsWith("upgradeFromVersions=")) {
                                upgradeFromInfoRaw = s;
                                break;
                            }
                        }
                        String upgradeFromInfo = upgradeFromInfoRaw.split("=")[1]; // only need the value
                        for (String versionStr : upgradeFromInfo.split(";")) { // versions are separated by semicolon
                            tempList.add(new SoftwareVersion(versionStr));
                        }
                    }
                    map.put(tempVersion, tempList);
                }
            }
        }
        return map;
    }

    private Map<SoftwareVersion, List<SoftwareVersion>> getVersionsWithMetadataDirectory()
            throws IOException, RemoteRepositoryException, MalformedURLException {
        RepositoryContent content = readRepository(_repo);
        URL redirectedURL = content.getRepoURL();
        Map<SoftwareVersion, List<SoftwareVersion>> versions = new HashMap<SoftwareVersion, List<SoftwareVersion>>();
        DirectoryMetaDataCallback callback = new DirectoryMetaDataCallback(redirectedURL, versions);

        new HTMLEditor().getParser().parse(new StringReader(readInputStream(content.getContentStream())), callback, true);

        return versions;
    }

    // Parser callback used by parseDirectory()
    //
    private class DirectoryMetaDataCallback extends HTMLEditorKit.ParserCallback {

        private URL _url;
        private Map<SoftwareVersion, List<SoftwareVersion>> _versionsMap;

        public DirectoryMetaDataCallback(URL url, Map<SoftwareVersion, List<SoftwareVersion>> versionsMap) {
            _versionsMap = versionsMap;
            _url = url;
        }

        @Override
        public void handleStartTag(HTML.Tag t, MutableAttributeSet a, int pos) {
            if (t == HTML.Tag.A) {
                // Extract the
                final String href = (String) a.getAttribute(HTML.Attribute.HREF);
                final String logMsg = "getVersionsWithMetadataCatalogDirectoryRepo(): href=" + href;

                try {
                    URL subdirUrl = new URL(_url, href);
                    String[] subdirFileParts = subdirUrl.getFile().split("/");
                    SoftwareVersion version = new SoftwareVersion(subdirFileParts[subdirFileParts.length - 1]);
                    URL metadataFileUrl = new URL(subdirUrl, "vipr.md");
                    HttpURLConnection httpCon = prepareConnection(metadataFileUrl);
                    httpCon.setRequestMethod("GET");
                    List<SoftwareVersion> tempList = new ArrayList<SoftwareVersion>();
                    if (httpCon.getResponseCode() == HttpURLConnection.HTTP_OK) {
                        InputStream is = httpCon.getInputStream();
                        StringBuilder input = new StringBuilder();

                        Reader in = new InputStreamReader(is, "UTF-8");
                        while (true) {
                            char[] buffer = new char[0x10000];
                            int read = in.read(buffer, 0, buffer.length);
                            if (read <= 0) {
                                break;
                            }
                            input.append(buffer, 0, read);
                        }
                        _log.info("The meta data file is: " + input.toString());
                        for (String s : input.toString().split("\n")) {
                            if (s.startsWith("upgrade_from:")) {
                                if (s.trim().endsWith(":")) {
                                    break;
                                }
                                for (String versionStr : s.substring(13).split(",")) {
                                    tempList.add(new SoftwareVersion(versionStr));
                                }
                                break;
                            }
                        }
                    }
                    _versionsMap.put(version, tempList);
                } catch (Exception e) {
                    _log.debug("Ignored. " + logMsg + ": " + e);
                }
            }
        }
    }

    public List<String> getUpgradeFromVersionsString(SoftwareVersion version) throws Exception {

        RepositoryContent repositoryContent = readRepository(_repo);
        try {
            if (repositoryContent.getContentType().toLowerCase().contains("text/xml")) {
                return getUpgradeFromVersionsFromCatalogRepo(readCatalog(readInputStream(repositoryContent.getContentStream())), version);
            } else {
                return getUpgradeFromVersionsFromDirectoryRepo(version);
            }
        } catch (RemoteRepositoryException e) {
            throw e;
        } catch (Exception e) {
            throw SyssvcException.syssvcExceptions.remoteRepoError(
                    MessageFormat.format("Failed to parse the remote repository {0} input={1} ({2})",
                            _repo, Strings.repr(repositoryContent), e));
        } finally {
            try {
                if (null != repositoryContent.getContentStream()) {
                    repositoryContent.getContentStream().close();
                }
            } catch (IOException e) {
                _log.error("Failed to close input stream: " + e);
            }
        }
    }

    private List<String> getUpgradeFromVersionsFromCatalogRepo(String input, SoftwareVersion version) throws ParserConfigurationException,
            SAXException, IOException,
            XPathExpressionException, InvalidSoftwareVersionException,
            MalformedURLException, RemoteRepositoryException {
        List<String> versions = new ArrayList<String>();
        DocumentBuilderFactory dbFactory = DocumentBuilderFactory.newInstance();
        DocumentBuilder dBuilder = dbFactory.newDocumentBuilder();
        Document doc = dBuilder.parse(new InputSource(new StringReader(input)));
        XPath xPath = XPathFactory.newInstance().newXPath();
        NodeList fileList = (NodeList) xPath.compile("//File").evaluate(doc, XPathConstants.NODESET);
        OUTER: for (int fileItr = 0; fileItr < fileList.getLength(); fileItr++) {
            Node fileNode = fileList.item(fileItr);
            Element element = (Element) fileNode;
            Node nameNode = element.getAttributeNode("Name");
            if (null != nameNode) {
                String fileName = nameNode.getNodeValue();
                if (fileName.endsWith(SOFTWARE_IMAGE_SUFFIX)) {
                    String fileVersion = fileName.replace(SOFTWARE_IMAGE_SUFFIX,"");
                    if (new SoftwareVersion(fileVersion).equals(version)) {
                        // Only find the node for image file of that particular version
                        Node catalogInfoNode = element.getAttributeNode("CatalogInfo");
                        String catalogInfo = catalogInfoNode.getNodeValue();
                        if (catalogInfo == null) {
                            return versions;
                        }
                        String upgradeFromInfoRaw = null;
                        for (String s : catalogInfo.split(",")) { // key-value pairs are separated by comma
                            if (s.startsWith("upgradeFromVersions=")) {
                                upgradeFromInfoRaw = s;
                                break;
                            }
                        }
                        String upgradeFromInfo = upgradeFromInfoRaw.split("=")[1]; // The format is
                                                                                   // "upgradeableFromVersion=version1;version2". We don't
                                                                                   // need the key and the equal sign
                        for (String v : upgradeFromInfo.split(";")) {
                            versions.add(v);
                        }
                        break OUTER;
                    }
                }
            }
        }
        return versions;
    }

    private List<String> getUpgradeFromVersionsFromDirectoryRepo(
            SoftwareVersion version) throws IOException, RemoteRepositoryException, MalformedURLException {
        URL redirectedURL = readRepository(_repo).getRepoURL();
        URL metadataFileURL = new URL(redirectedURL, version.toString().substring(5) + "/vipr.md");
        HttpURLConnection httpCon = null;
        httpCon = prepareConnection(metadataFileURL);
        httpCon.setRequestMethod("GET");
        if (httpCon.getResponseCode() == HttpURLConnection.HTTP_OK) {
            for (String s : readInputStream(httpCon.getInputStream()).split("\n")) {
                if (s.startsWith("upgrade_from:")) {
                    if (!s.trim().endsWith(":")) {
                        return Arrays.asList(s.substring(13).split(","));
                    }
                    break;
                }
            }
        }
        return new ArrayList<String>();
    }

    /**
     * Get a list of new versions that can be installed from the remote repository
     * 
     * @param remoteVersions versions available in the remote repository
     * @param forceInstall whether we should show versions available for force install
     * @param localCurrent current target version
     * @param localVersions currently installed versions
     * @param prefix log prefix
     */
    public List<SoftwareVersion> findInstallableVersions(
            final boolean forceInstall,
            final SoftwareVersion localCurrent,
            final List<SoftwareVersion> localVersions) {
        List<SoftwareVersion> newVersionList = findNewVersions(localCurrent, localVersions, forceInstall);
        if (newVersionList.isEmpty()) {
            return newVersionList;
        }
        RepositoryContent repositoryContent = readRepository(_repo);
        try {
            if (repositoryContent.getContentType().toLowerCase().contains("text/xml")) {
                return finInstallableVersionsFromCatalogRepo(readCatalog(readInputStream(repositoryContent.getContentStream())),
                        newVersionList, localVersions);
            } else {
                return findInstallableVersionsFromDirectoryRepo(newVersionList, localVersions);
            }
        } catch (RemoteRepositoryException e) {
            throw e;
        } catch (Exception e) {
            throw SyssvcException.syssvcExceptions.remoteRepoError(
                    MessageFormat.format("Failed to parse the remote repository {0} input={1} ({2})",
                            _repo, Strings.repr(repositoryContent), e));
        } finally {
            try {
                if (null != repositoryContent.getContentStream()) {
                    repositoryContent.getContentStream().close();
                }
            } catch (IOException e) {
                _log.error("Failed to close input stream: " + e);
            }
        }
    }

    private List<SoftwareVersion> findNewVersions(final SoftwareVersion localCurrent,
            final List<SoftwareVersion> localVersions, final boolean forceInstall) {
        List<SoftwareVersion> newVersions = new ArrayList<SoftwareVersion>();
        List<SoftwareVersion> allVersions = new ArrayList<SoftwareVersion>(getVersions());
        Collections.sort(allVersions);
        Collections.reverse(allVersions);
        _log.debug("Test if a version is new:");
        ToInstallLoop: for (SoftwareVersion v : allVersions) {
            // skip version lower than current version
            if (!forceInstall && localCurrent.compareTo(v) > 0) {
                _log.debug(" try=" + v +
                        ": lower than or equal to current version. Skipping.");
                continue;
            }
            // skip local versions
            for (SoftwareVersion version : localVersions) {
                if (version.compareTo(v) == 0) {
                    _log.debug(" try=" + v +
                            ": already downloaded {}. Skipping.", version);
                    continue ToInstallLoop;
                }
            }
            _log.debug(" try=" + v +
                    ": new version.");
            newVersions.add(v);
        }
        return newVersions;
    }

    private List<SoftwareVersion> finInstallableVersionsFromCatalogRepo(String catalogString,
            List<SoftwareVersion> newVersionList, List<SoftwareVersion> localVersions) throws ParserConfigurationException, SAXException,
            IOException,
            XPathExpressionException, InvalidSoftwareVersionException,
            MalformedURLException, RemoteRepositoryException {
        List<SoftwareVersion> validVersions = new ArrayList<SoftwareVersion>();
        DocumentBuilderFactory dbFactory = DocumentBuilderFactory.newInstance();
        DocumentBuilder dBuilder = dbFactory.newDocumentBuilder();
        Document doc = dBuilder.parse(new InputSource(new StringReader(catalogString)));
        XPath xPath = XPathFactory.newInstance().newXPath();
        NodeList fileList = (NodeList) xPath.compile("//File").evaluate(doc, XPathConstants.NODESET);
        OUTER: for (int fileItr = 0; fileItr < fileList.getLength(); fileItr++) {
            Node fileNode = fileList.item(fileItr);
            Element element = (Element) fileNode;
            Node nameNode = element.getAttributeNode("Name");
            if (null != nameNode) {
                String fileName = nameNode.getNodeValue();
                if (fileName.endsWith(SOFTWARE_IMAGE_SUFFIX)) {
                    String fileVersion = fileName.replace(SOFTWARE_IMAGE_SUFFIX,"");
                    SoftwareVersion tempVersion = new SoftwareVersion(fileVersion);
                    if (newVersionList.contains(tempVersion)) {
                        Node catalogInfoNode = element.getAttributeNode("CatalogInfo");
                        String catalogInfo = catalogInfoNode.getNodeValue();
                        if (catalogInfo.equals(""))
                         {
                            continue; // Ignore the version that doesn't have the upgradeFromVersion metadata
                        }
                        String upgradeFromInfoRaw = catalogInfo.split(",")[0]; // key-value pairs are separated by comma
                        String upgradeFromInfo = upgradeFromInfoRaw.split("=")[1]; // only need the value
                        for (String versionStr : upgradeFromInfo.split(";")) { // versions are separated by semicolon
                            for (SoftwareVersion v : localVersions) {
                                if (new SoftwareVersion(versionStr).weakEquals(v)) { // wild card is used in the upgradeFromVersions list,
                                                                                     // need use weakEquals
                                    validVersions.add(tempVersion);
                                    continue OUTER;
                                }
                            }
                        }
                    }
                }
            }
        }
        return validVersions;
    }

    private List<SoftwareVersion> findInstallableVersionsFromDirectoryRepo(
            List<SoftwareVersion> newVersionList,
            List<SoftwareVersion> localVersions) throws IOException, RemoteRepositoryException, MalformedURLException {
        // TODO
        return null;
    }

}
