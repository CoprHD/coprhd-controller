/*
 * Copyright (c) 2015 EMC Corporation
 * All Rights Reserved
 */

package com.emc.storageos.systemservices.impl.licensing;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.math.BigInteger;
import java.net.URI;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.emc.storageos.model.vpool.ManagedResourcesCapacity;
import com.emc.storageos.model.vpool.ManagedResourcesCapacity.ManagedResourceCapacity;
import com.emc.storageos.svcs.errorhandling.resources.APIException;
import com.emc.storageos.svcs.errorhandling.resources.InternalServerErrorException;

import org.joda.time.DateTime;
import org.joda.time.Days;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;

import com.emc.cams.elm.ELMFeatureDetail;
import com.emc.cams.elm.ELMLicenseProps;
import com.emc.cams.elm.ELMLicenseSource;
import com.emc.cams.elm.exception.ELMLicenseException;
import com.emc.storageos.coordinator.client.service.CoordinatorClient.LicenseType;
import com.emc.storageos.coordinator.client.service.LicenseInfo;
import com.emc.storageos.coordinator.client.model.Constants;
import com.emc.storageos.coordinator.common.Service;
import com.emc.storageos.systemservices.exceptions.CoordinatorClientException;
import com.emc.storageos.systemservices.exceptions.LocalRepositoryException;
import com.emc.storageos.systemservices.exceptions.SysClientException;
import com.emc.storageos.systemservices.impl.client.SysClientFactory;
import com.emc.storageos.systemservices.impl.client.SysClientFactory.SysClient;
import com.emc.storageos.systemservices.impl.eventhandler.connectemc.SendEventScheduler;
import com.emc.storageos.systemservices.impl.upgrade.CoordinatorClientExt;
import com.emc.vipr.model.sys.licensing.License;
import com.emc.vipr.model.sys.licensing.LicenseFeature;

import static com.emc.storageos.coordinator.client.service.LicenseInfo.*;

public class LicenseManagerImpl implements LicenseManager {

    private CoordinatorClientExt _coordinator;
    private SendEventScheduler _sendEventScheduler;
    private static final Logger _log = LoggerFactory.getLogger(LicenseManagerImpl.class);

    public static final int waitClusterStableInterval = 5000;
    public static final int waitRetryConvertLicneseInterval = 5000;

    public static final String LICENSETYPE_DELIMITER = ":";

    // used to ensure thread-safety of parsing license
    final Lock parseLicenseLock = new ReentrantLock();
    public final static int waitAcquireParseLicenseLock = 60; // 60 seconds

    /**
     * Configure the license information in properties file, disk and coordinator.
     * 
     * @param license
     * @throws LocalRepositoryException
     * @throws CoordinatorClientException
     */
    public void addLicense(License license)
            throws LocalRepositoryException, CoordinatorClientException, ELMLicenseException {

        if (getTargetInfoLock()) {
            try {
                // Step 1: Add the license test to disk in the .license file
                // Step 2: parse the .license file on disk in root directory using the ELMS API. This is required by the
                // ELMS API.
                License fullLicense = buildLicenseObjectFromText(license.getLicenseText());

                // Step 3: Add license features to coordinator service.
                updateCoordinatorWithLicenseFeatures(fullLicense, true);

                // Step 4: Add the raw license file to coordinator to keep a copy of the actual license file.
                updateCoordinatorWithLicenseText(license);

                // Step 5: Force the events to run
                _sendEventScheduler.run();
            } finally {
                releaseTargetVersionLock();
            }
        }
        else {
            _log.warn("Cannot acquire lock for adding license");
            throw APIException.serviceUnavailable.postLicenseBusy();
        }
    }

    /**
     * Check if it is a one-node deployment required by trial package in vipr 1.1
     * 
     * @return true if it is a 1+0 vipr deployment
     */
    public boolean isTrialPackage() {
        // check if it is 1+0 deployment of controller
        return (_coordinator.getNodeCount() == 1 && Constants.CONTROL_NODE_SYSSVC_ID_PATTERN.matcher(
                _coordinator.getMySvcId()).matches());
    }

    /**
     * Check if the license is a trial license. In vipr 1.1, only ViPR_Controller license can be trial license.
     * 
     * @param license license object
     * @return true if the license object is a trial license
     * @throws ELMLicenseException
     */
    public boolean isTrialLicense(License license) throws ELMLicenseException
    {
        // parse the license text
        License fullLicense = buildLicenseObjectFromText(license.getLicenseText());
        if (fullLicense != null) {
            for (LicenseFeature feature : fullLicense.getLicenseFeatures()) {
                if (feature.getModelId().startsWith(LicenseConstants.VIPR_CONTROLLER)
                        && feature.isTrialLicense()) {
                    return true;
                }
            }
        }
        return false;
    }

    /**
     * Returns a full license object complete with features.
     * 
     * @return
     */
    public License getLicense() throws Exception {
        LicenseInfoListExt licenseInfoList = getLicenseInfoListFromCoordinator();
        License license = new License();
        if (licenseInfoList != null) {
            // create a license object from the above license features.
            createLicenseObject(licenseInfoList.getLicenseList(), license);
        }
        // get the raw license text from coordinator.
        LicenseTextInfo licenseTextInfo = getLicenseTextFromCoordinator();

        // if text is found, add the raw license text to the License object.
        // if text is not found, put message in license text stating that the product
        // is not licensed.
        if (licenseTextInfo != null) {
            license.setLicenseText(licenseTextInfo.getLicenseText());
        } else {
            license.setLicenseText("The product is not licensed");
        }

        return license;
    }

    /**
     * Build a valid license object from the license string. If there is any
     * error while reading the license file, a license object will be created
     * with a licensed value of false.
     * 
     * @return license object if successful, otherwise null
     */
    protected License buildLicenseObjectFromText(String licenseText) throws ELMLicenseException {

        boolean bGetLock = false;
        try {
            bGetLock = parseLicenseLock.tryLock(waitAcquireParseLicenseLock, TimeUnit.SECONDS);
        } catch (Exception e) {
            _log.warn("Exception when adding license, msg: {}", e.getMessage());
            throw APIException.internalServerErrors.processLicenseError("failed getting lock to validate and parse license, error:"
                    + e.getMessage());
        }

        if (bGetLock) {
            try {

                License license = new License();

                // Add the license test to disk in the .license file in root. This is required in order
                // to parse the license using the ELMS API.
                addLicenseToDiskLicenseFile(licenseText);

                ELMLicenseProps licProps = new ELMLicenseProps();
                licProps.setLicPath(LicenseConstants.LICENSE_FILE_PATH);
                ELMFeatureDetail[] featureDetails = null;

                ELMLicenseSource licSource = new ELMLicenseSource(licProps);
                featureDetails = licSource.getFeatureDetailList();

                LicenseFeature licenseFeature = null;
                for (ELMFeatureDetail featureDetail : featureDetails) {
                    // create a license feature object.
                    licenseFeature = new LicenseFeature();
                    if (!featureDetail.getFeatureName().equals(LicenseConstants.VIPR_CONTROLLER)) {
                        throw APIException.badRequests.licenseIsNotValid(
                                String.format("The license file contains a not supported feature: %s.",
                                        featureDetail.getFeatureName()) +
                                        "Non controller license is no longer supported.");
                    }
                    if (featureDetail.getDaysUntilExp() > 0) {
                        licenseFeature.setLicensed(true);
                        licenseFeature.setVersion(featureDetail.getVersion());
                        licenseFeature.setIssuer(featureDetail.getIssuer());
                        licenseFeature.setNotice(featureDetail.getNotice());
                        licenseFeature.setDateExpires(convertCalendarToString(featureDetail.getExpDate()));
                        licenseFeature.setExpired(isExpired(licenseFeature.getDateExpires()));
                        licenseFeature.setDateIssued(convertCalendarToString(featureDetail.getIssuedDate()));

                        String subModelId = LicenseConstants.OLD_LICENSE_SUBMODEL;
                        Properties p = featureDetail.getVendorString(";");
                        if (p.size() > 0) {
                            for (Enumeration e = p.propertyNames(); e.hasMoreElements();) {
                                String str = (String) e.nextElement();
                                if (str.equals(LicenseConstants.LICENSE_TYPE_PROPERTYNAME)) {
                                    subModelId = p.getProperty(str);
                                    _log.info("Get a license increment with type: {}", subModelId);
                                    break;
                                }
                            }
                        }
                        licenseFeature.setModelId(featureDetail.getFeatureName() + LICENSETYPE_DELIMITER + subModelId);
                        setVendorStringFields(featureDetail, licenseFeature, p);
                    } else {
                        _log.info("The license file contains a feature which is in an expired state. The license was not added to the system.");
                        throw APIException.badRequests
                                .licenseIsNotValid(
                                "The license file contains a feature which is in an expired state. The license was not added to the system.");
                    }
                    license.addLicenseFeature(licenseFeature);
                }

                // delete /tmp/.license if it exists
                deleteCurrentLicenseFileOnDisk();

                _log.debug("Finished parsing of license");
                return license;
            } finally {
                parseLicenseLock.unlock();
            }
        } else {
            _log.warn("Cannot acquire lock. Another thread is holding the lock validating and parsing license");
            throw APIException.serviceUnavailable.postLicenseBusy();
        }
    }

    /**
     * Convert Calendar to MM/dd/yyyy format.
     * 
     * @param calendar
     * @return
     */
    private static String convertCalendarToString(Calendar calendar) {
        if (calendar != null) {
            SimpleDateFormat sdf = new SimpleDateFormat(LicenseConstants.MM_DD_YYYY_FORMAT);
            return sdf.format(calendar.getTime());
        } else {
            return null;
        }
    }

    /**
     * Verify if product is licensed for the specified feature.
     * 
     * @return boolean
     */
    public boolean isProductLicensed(LicenseType licenseType) {
        return _coordinator.isProductLicensed(licenseType);
    }

    /**
     * Get all license info (features) from coordinator.
     * 
     * @return LicenseInfoListExt which represent a list of license features
     * @throws Exception
     */
    public LicenseInfoListExt getLicenseInfoListFromCoordinator() {
        try {
            return _coordinator.getTargetInfo(LicenseInfoListExt.class,
                    TARGET_PROPERTY_ID, LicenseInfo.LICENSE_INFO_TARGET_PROPERTY);

        } catch (Exception e) {
            throw APIException.internalServerErrors.getObjectFromError("license info list",
                    "coordinator", e);
        }
    }

    /**
     * Get license info for a specific license type from coordinator.
     * 
     * @return license info for the specified license type
     * @throws Exception
     */
    public LicenseInfoExt getLicenseInfoFromCoordinator(LicenseType licenseType) {
        try {
            LicenseInfoListExt licInfoList = _coordinator.getTargetInfo(LicenseInfoListExt.class,
                    TARGET_PROPERTY_ID, LicenseInfo.LICENSE_INFO_TARGET_PROPERTY);

            if (licInfoList != null) {
                for (LicenseInfoExt licenseInfo : licInfoList.getLicenseList()) {
                    if (licenseInfo.getLicenseType() == licenseType) {
                        return licenseInfo;
                    }
                }
            }
            return null;
        } catch (Exception e) {
            throw APIException.internalServerErrors.getObjectFromError(licenseType + " license info",
                    "coordinator", e);
        }
    }

    /**
     * Get raw license text in LicenseTextInfo from coordinator.
     * 
     * @return
     * @throws Exception
     */
    public LicenseTextInfo getLicenseTextFromCoordinator() throws Exception {
        return _coordinator.getTargetInfo(LicenseTextInfo.class);
    }

    /**
     * Write the contents of the license file to /tmp/.license file. This is required
     * by the ELMS API for parsing the license.
     * 
     * @param licenseText
     */
    private void addLicenseToDiskLicenseFile(String licenseText) {

        // check if file exists, if so, delete it first and create a new one.
        deleteCurrentLicenseFileOnDisk();
        // create a new file to hold the license text.
        // licenseFile.createNewFile();
        writeFile(LicenseConstants.LICENSE_FILE_PATH, licenseText);
    }

    /**
     * Write license file text to disk.
     * 
     * @param file licenseFile, String licenseText
     */
    private void writeFile(String file, String licenseText) {

        BufferedWriter writer = null;
        File licenseFile = new File(file);
        try {
            writer = new BufferedWriter(new FileWriter(licenseFile.getAbsoluteFile()));
            writer.write(licenseText);
            writer.close();
        } catch (IOException e) {
            _log.error("IO Exception while writing to .license file: {}", e);
            APIException.internalServerErrors.ioWriteError("/tmp/.license");
        } finally {
            try {
                writer.close();
            } catch (Exception e) {
                // do nothing.
            }
        }
    }

    /**
     * Deletes current version of license file in /tmp/.license
     * 
     * @return File
     */
    private void deleteCurrentLicenseFileOnDisk() {

        File licenseFile = new File(LicenseConstants.LICENSE_FILE_PATH);
        if (licenseFile.exists()) {
            licenseFile.delete();
        }
    }

    /**
     * Build the coordinator service version of the license features from the
     * License object.
     * 
     * @param license license to update
     * @param checkClusterUpgradable check if cluster is upgradable
     * @throws CoordinatorClientException
     */
    private void updateCoordinatorWithLicenseFeatures(License license, boolean checkClusterUpgradable)
            throws CoordinatorClientException {

        LicenseInfoListExt licenseList = null;
        List<LicenseInfoExt> licenseInfoList = new ArrayList<LicenseInfoExt>();
        for (LicenseFeature licenseFeature : license.getLicenseFeatures()) {
            LicenseType licenseType;
            if (licenseFeature.getModelId().startsWith(LicenseConstants.VIPR_CONTROLLER)) {
                licenseType = LicenseType.CONTROLLER;
            } else {
                throw APIException.internalServerErrors.licenseInfoNotFoundForType(
                    "invalid license model id" + licenseFeature.getModelId());
            }

            LicenseInfoExt licenseInfo = new LicenseInfoExt();
            licenseInfo.setLicenseType(licenseType);
            licenseInfo.setExpirationDate(licenseFeature.getDateExpires());
            licenseInfo.setStorageCapacity(licenseFeature.getStorageCapacity());
            licenseInfo.setProductId(licenseFeature.getProductId());
            licenseInfo.setModelId(licenseFeature.getModelId());
            licenseInfo.setIssuedDate(licenseFeature.getDateIssued());
            licenseInfo.setLicenseTypeIndicator(licenseFeature.getLicenseIdIndicator());
            licenseInfo.setVersion(licenseFeature.getVersion());
            licenseInfo.setNotice(licenseFeature.getNotice());
            if (licenseFeature.isTrialLicense()) {
                licenseInfo.setTrialLicense(true);
            }
            licenseInfoList.add(licenseInfo);
        }
        if (!licenseInfoList.isEmpty()) {
            licenseList = new LicenseInfoListExt(licenseInfoList);
            _coordinator.setTargetInfo(licenseList, checkClusterUpgradable);
        }
    }

    /**
     * Update Coordinator Service with the customers actual raw license file text.
     * 
     * @param license
     * @throws CoordinatorClientException
     */
    public void updateCoordinatorWithLicenseText(License license)
            throws CoordinatorClientException {

        LicenseTextInfo licenseTextInfo = new LicenseTextInfo();
        licenseTextInfo.setLicenseText(license.getLicenseText());
        _coordinator.setTargetInfo(licenseTextInfo);
    }

    /**
     * Update Coordinator Service with license information.
     * 
     * @throws CoordinatorClientException
     */
    public void updateCoordinatorWithLicenseInfo(LicenseInfoExt licenseInfo)
            throws CoordinatorClientException {

        LicenseInfoListExt licenseList = getLicenseInfoListFromCoordinator();
        if (licenseList != null) {
            licenseList.updateLicense(licenseInfo);
            _coordinator.setTargetInfo(licenseList, TARGET_PROPERTY_ID,
                    LicenseInfo.LICENSE_INFO_TARGET_PROPERTY);
        }
    }

    /**
     * Verify if the license has expired.
     * 
     * @param licenseInfo
     * @return
     */
    public boolean isLicenseExpired(LicenseInfoExt licenseInfo) {

        LicenseFeature licenseFeature = createLicenseFeatureFromLicenseInfoExt(licenseInfo);
        if (licenseFeature != null) {
            return licenseFeature.isExpired();
        }

        return false;
    }

    /**
     * Verify if storage capacity currently used has exceeded the licensed capacity from the license file.
     * 
     * @param licenseInfo
     * @return true if capacity is exceeded
     */
    public boolean isCapacityExceeded(LicenseInfoExt licenseInfo) {
        double currentCapacityUsed = 0;
        double licenseCapacity = Double.parseDouble(licenseInfo.getStorageCapacity());
        try {
            // if (licenseInfo.getModelId().equalsIgnoreCase(LicenseConstants.getModelId(LicenseType.CONTROLLER)))
            if (licenseInfo.getLicenseType().equals(LicenseType.CONTROLLER))
            {
                // Get capacity from apisvc
                currentCapacityUsed = getTotalControllerCapacity();
            } else {
                return false;
            }
            Object[] args = new Object[] { licenseInfo.getModelId(), currentCapacityUsed, licenseCapacity };
            _log.info("Capacity currently used by {}: {}, licensed capacity: {}", args);
            return currentCapacityUsed > licenseCapacity;
        } catch (Exception e) {
            _log.warn("Internal server error occurred while getting capacity: {}", e);
        }
        return false;
    }

    /**
     * Update License object with data license information from coordinator service.
     * 
     * @param licenseInfoExts licenseInfoExts, License license
     * @return
     */
    private void createLicenseObject(List<LicenseInfoExt> licenseInfoExts, License license) {

        if (licenseInfoExts.isEmpty()) {
            return;
        }

        for (LicenseInfoExt licenseExt : licenseInfoExts) {
            LicenseFeature licenseFeature = createLicenseFeatureFromLicenseInfoExt(licenseExt);
            license.addLicenseFeature(licenseFeature);
        }
    }

    /**
     * Create a LicenseFeature object from a LicenseInfoExt from coordinator service.
     * 
     * @return LicenseFeature
     */
    private LicenseFeature createLicenseFeatureFromLicenseInfoExt(LicenseInfoExt licenseInfo) {

        if (licenseInfo == null) {
            return null;
        }

        LicenseFeature licenseFeature = new LicenseFeature();
        licenseFeature.setDateExpires(licenseInfo.getExpirationDate());
        licenseFeature.setExpired(isExpired(licenseFeature.getDateExpires()));
        licenseFeature.setStorageCapacity(licenseInfo.getStorageCapacity());
        licenseFeature.setProductId(licenseInfo.getProductId());
        licenseFeature.setSerial(licenseInfo.getProductId());
        licenseFeature.setModelId(licenseInfo.getModelId());
        licenseFeature.setDateIssued(licenseInfo.getIssuedDate());
        licenseFeature.setLicenseIdIndicator(licenseInfo.getLicenseTypeIndicator());
        licenseFeature.setVersion(licenseInfo.getVersion());
        licenseFeature.setNotice(licenseInfo.getNotice());
        licenseFeature.setTrialLicense(licenseInfo.isTrialLicense());
        licenseFeature.setLicensed(true);
        return licenseFeature;
    }

    /**
     * Parse the license text manually to get the model id's and appropriate vendor string.
     * This must be done manually because retrieving this via the ELMS API does not work
     * 
     * @param licenseText
     * @return Map
     */
    private Map<String, String> parseLicenseVendorStrings(String licenseText) {

        Pattern pattern = Pattern.compile(LicenseConstants.LICENSE_PATTERN, Pattern.DOTALL);
        Map<String, String> featureMap = new HashMap<String, String>();
        // get a String array of the license features.
        String[] licenseFeatures = licenseText.split(LicenseConstants.LICENSE_FEATRES_DELIM);
        // iterate through the license features, building the vendor string map.
        for (String licenseFeature : licenseFeatures) {
            if (licenseFeature == null || licenseFeature.isEmpty()) {
                continue;
            }
            Matcher matcher = pattern.matcher(licenseFeature.trim());
            if (matcher.find()) {
                featureMap.put(matcher.group(1), matcher.group(2));
            }
        }
        return featureMap;
    }

    /**
     * Set the license feature with the appropriate data from model number's
     * vendor string.
     */
    private void setVendorStringFields(ELMFeatureDetail featureDetail,
            LicenseFeature licenseFeature, Properties vendorProps) {

        if (vendorProps.getProperty(LicenseConstants.STORAGE_CAPACITY) != null) {
            licenseFeature.setStorageCapacity(computeLicensedAmount(vendorProps));
        }

        // If SWID is returned in the vendor string, we will use that as the Serial number,
        // otherwise, use SN. Set the LicenseIndicator so that the SYR knows if we're using a SWID or LAC.
        String swidValue = vendorProps.getProperty(LicenseConstants.SWID_VALUE);
        if (swidValue == null || swidValue.startsWith("ERR")) {
            licenseFeature.setSerial(featureDetail.getSN());
            licenseFeature.setProductId(featureDetail.getSN());
            licenseFeature.setLicenseIdIndicator(LicenseConstants.LAC);
        } else {
            licenseFeature.setSerial(swidValue);
            licenseFeature.setProductId(swidValue);
            licenseFeature.setLicenseIdIndicator(LicenseConstants.SWID);
        }
        // if it is a trial license
        String trialLicenseStr = vendorProps.getProperty(LicenseConstants.TRIAL_LICENSE_NAME);
        if (trialLicenseStr != null) {
            for (String value : LicenseConstants.TRIAL_LICENSE_VALUE) {
                if (trialLicenseStr.equals(value)) {
                    licenseFeature.setTrialLicense(true);
                    _log.info("License {} is trial license", featureDetail.getFeatureName());
                    break;
                }
            }
        }
    }

    /**
     * Splits the vendor string from license into individual entries in a map.
     */
    private Map<String, String> splitVendorString(String vendorString) {
        Map<String, String> vendorMap = new HashMap<String, String>();
        String[] vendorStringArray = vendorString.split(";");
        for (int x = 0; x < vendorStringArray.length; x++) {
            String[] vendorEntry = vendorStringArray[x].split("=");
            vendorMap.put(vendorEntry[0], vendorEntry[1]);
        }

        return vendorMap;
    }

    /**
     * Gets capacity from controller.
     * List of returned resources include volume, file and free storage pool capacities.
     */
    public ManagedResourcesCapacity getControllerCapacity() throws InternalServerErrorException {
        _log.info("Getting controller capacity");
        List<Service> services = _coordinator.locateAllServices(
                LicenseConstants.API_SVC_LOOKUP_KEY,
                LicenseConstants.SERVICE_LOOKUP_VERSION, null, null);
        for (Service service : services) {
            try {
                // service could be null, if so get next service.
                if (service != null) {
                    return getClient(service).get(SysClientFactory._URI_PROVISIONING_MANAGED_CAPACITY,
                            ManagedResourcesCapacity.class, null);
                }
            } catch (SysClientException exception) {
                _log.error("LicenseManager::getCapacity for Controller. Cannot connect to host: {}"
                        , service.getEndpoint().toString());
            }
        }
        // if capacity cannot be retrieved
        _log.error("Controller capacity could not be retrieved");
        throw APIException.internalServerErrors.getObjectError("controller capacity",
                null);
    }

    /**
     * Gets controller total managed capacity.
     * sum of volume managed + file managed + free managed storage pool
     * This is mainly used to check capacity against licensed capacity amount.
     */
    private double getTotalControllerCapacity() throws InternalServerErrorException {
        _log.info("Getting controller total capacity");
        ManagedResourcesCapacity resourceCapacities = getControllerCapacity();
        double total = 0;
        for (ManagedResourceCapacity cap : resourceCapacities.getResourceCapacityList()) {
            _log.debug("{} capacity is {}", cap.getType(), cap.getResourceCapacity());
            total += cap.getResourceCapacity();
        }
        _log.info("Controller total capacity is {}", total);
        return total;
    }

    /**
     * Get a instance of the SysClient for the base url.
     * 
     * @return
     */
    private SysClient getClient(Service service) {
        URI hostUri = null;
        hostUri = service.getEndpoint();
        String baseNodeURL = String.format(LicenseConstants.BASE_URL_FORMAT, hostUri.getHost(),
                hostUri.getPort());
        _log.info("Calling URI: " + baseNodeURL);
        return SysClientFactory.getSysClient(URI.create(baseNodeURL));
    }

    /**
     * Compute the licensed amount by computing storage capacity times the
     * storage capacity unit.
     * 
     * @param vendorProps
     * @return
     */
    private String computeLicensedAmount(Properties vendorProps) {

        String units = vendorProps.getProperty(LicenseConstants.STORAGE_CAPACITY_UNITS);
        if (units == null) {
            return null;
        }

        BigInteger computedLicensedCapacity = null;
        // Currently, only TERABYTE is supported in the license.
        if (units.equalsIgnoreCase(LicenseConstants.TERABYTE)) {
            BigInteger licensedCapacity = new BigInteger(vendorProps.getProperty(LicenseConstants.STORAGE_CAPACITY));
            computedLicensedCapacity = licensedCapacity.multiply(LicenseConstants.TB_VALUE);
        }

        // If for some reason we cannot compute the capacity for the capacity values in the license
        // (CAPACITY * CAPACITY_UNIT), return 0.
        if (computedLicensedCapacity != null) {
            return computedLicensedCapacity.toString();
        } else {
            return "0";
        }
    }

    protected static boolean isExpired(String expiryDateString) {
        // if date expires is empty, check to see if the product is licensed. If
        // it is licensed and the _dateExpires is false, the license has expired.
        // This is because the ELM software does not set the date expires when
        // it notices a license is invalid. This license must have expired
        // sometime between server restarts. The product is technically
        // licenses..but in an expired state.
        if (expiryDateString == null) {
            return false;
        }

        if (!expiryDateString.equalsIgnoreCase(PERMANENT_LICENSE)) {
            SimpleDateFormat sdf = new SimpleDateFormat(EXPIRE_DATE_FORMAT);
            Date expireDate = null;
            try {
                expireDate = sdf.parse(expiryDateString);
                Date today = Calendar.getInstance().getTime();
                int days = Days.daysBetween(new DateTime(expireDate), new DateTime(today)).getDays();
                if (days > 0) {
                    return true;
                }
            } catch (ParseException e) {
                _log.error("Parse Exception in License::isExpired() : " + e.getMessage());
            }
        }

        return false;
    }

    /**
     * Get a target info lock from coordinator.
     * 
     * @return
     */
    public boolean getTargetInfoLock() {
        return _coordinator.getTargetInfoLock();
    }

    /**
     * calls coordinator client to release a target version lock.
     */
    public void releaseTargetVersionLock() {
        _coordinator.releaseTargetVersionLock();
    }

    /**
     * Set the CoordinatorClientExt
     * 
     * @param coordinatorClientExt
     */
    public void setCoordinatorClientExt(CoordinatorClientExt coordinatorClientExt) {
        _coordinator = coordinatorClientExt;
    }

    /**
     * Set the events scheduler.
     * 
     * @param scheduler
     */
    @Autowired
    public void setSendEventScheduler(SendEventScheduler scheduler) {
        _sendEventScheduler = scheduler;
    }
}
