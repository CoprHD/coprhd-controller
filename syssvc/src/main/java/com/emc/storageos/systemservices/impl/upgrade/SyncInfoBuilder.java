/*
 * Copyright 2015 EMC Corporation
 * All Rights Reserved
 */
/**
 * Copyright (c) 2012 EMC Corporation
 * All Rights Reserved
 *
 * This software contains the intellectual property of EMC Corporation
 * or is licensed to EMC Corporation from third parties.  Use of this
 * software and the intellectual property contained therein is expressly
 * limited to the terms and conditions of the License Agreement under which
 * it is provided by or on behalf of EMC.
 */

package com.emc.storageos.systemservices.impl.upgrade;

import java.io.IOException;
import java.text.MessageFormat;
import java.util.*;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.emc.storageos.services.util.Strings;

import com.emc.storageos.coordinator.client.model.RepositoryInfo;
import com.emc.storageos.coordinator.client.model.SoftwareVersion;

public class SyncInfoBuilder {
    private static final Logger log = LoggerFactory.getLogger(SyncInfoBuilder.class);
    
    public  static final int    MAX_SOFTWARE_VERSIONS = 3;

    /**
     * Select a SoftwareVersion from the leader repository list that can be installed,
     * and a list of versions that have to be removed from the local repository.
     *
     * @return SyncInfo - An immutable Selector object with a SoftwareVersion toInstall and
     *         a list of SoftwareVersions to toRemove. The toInstall might be null.
     *         The toRemove might be empty (but not null).
     * @throws IOException 
     * @see SyncInfo
     */
    public static SyncInfo getTargetSyncInfo(final RepositoryInfo local, final RepositoryInfo target) {
        final SoftwareVersion       localCurrent   = (local != null)  ? local.getCurrentVersion() : null;
        final List<SoftwareVersion> localVersions  = (local != null  && local.getVersions() != null) ?
                local.getVersions() :  new ArrayList<SoftwareVersion>();
        final SoftwareVersion       targetCurrent  = (target != null) ? target.getCurrentVersion() : null;
        final List<SoftwareVersion> targetVersions = (target != null && target.getVersions() != null) ?
                target.getVersions() :  new ArrayList<SoftwareVersion>();

        // Basic validations
        if (localCurrent == null || localVersions == null || localVersions.isEmpty()) {
            log.error("inconsistent local repository state");
            return new SyncInfo();
        }
        if (targetCurrent == null || targetVersions == null || targetVersions.isEmpty()) {
            log.error("inconsistent target repository state");
            return new SyncInfo();    
        }
        
        final String args = MessageFormat.format("local: [{0}/{1}] current={2} versions={3} " +
                "remote: current {4} versions={5}", localVersions.size(), MAX_SOFTWARE_VERSIONS,
                localCurrent, Strings.repr(localVersions), targetCurrent, Strings.repr(targetVersions));
        final String prefix = "getTargetSyncInfo(): " + args + " : ";

        // Special case 1 - current on remote is not same as current on local, make sure they are still upgradable
        // Set force to true as we already did the version check during set_target call
        if (targetCurrent != null && !targetCurrent.equals(localCurrent) && !localVersions.contains(targetCurrent)) {
            try {
				if (!localCurrent.isSwitchableTo(targetCurrent)) {
				    // we are not expecting this case - don't change anything, wait for help!
				    log.error("{} local current not upgradable to current on leader", prefix);
				    return new SyncInfo();
				}
			} catch (IOException e) {
				log.error("Error occured when extracting version metadata from the image file", e);
			}
        }

        // To Install - what version do the target state have that I don't have
        List<SoftwareVersion> toInstallCandidates = new ArrayList<SoftwareVersion>(targetVersions);
        Collections.sort(toInstallCandidates);
        for (SoftwareVersion version: toInstallCandidates) {
            if (localVersions.contains(version)) {
                continue;
            }
            return new SyncInfo(version, new ArrayList<SoftwareVersion>());
        }
        
        // if we are here, we didn't find anything to install - see if we need to remove any
        // To Remove - what versions do I have that the target does not have
        // Set force to true as we already did the version check during set_target call
        List<SoftwareVersion> toRemoveCandidates = new ArrayList<SoftwareVersion>();
		try {
			toRemoveCandidates = findToRemove(localVersions, localCurrent, null, null, true);
		} catch (IOException e) {
			log.error("Error occured when extracting version metadata from the image file", e);
		}
        List<SoftwareVersion> toRemove = new ArrayList<SoftwareVersion>();
        for (SoftwareVersion version: toRemoveCandidates) {
            if (targetVersions.contains(version)) {
                continue;
            }
            toRemove.add(version);
            return new SyncInfo(toRemove);
        }

        log.info(prefix + " Nothing to do.");
        return new SyncInfo();
    }
    
    public static List<SoftwareVersion> getInstallableRemoteVersions(final RepositoryInfo local,
                                                             Map<SoftwareVersion, List<SoftwareVersion>> remoteVersions,
                                                             final boolean forceInstall) {
        final SoftwareVersion       localCurrent  = (local != null)  ? local.getCurrentVersion() : null;
        final List<SoftwareVersion> localVersions = (local != null  && local.getVersions() != null) ?
                local.getVersions() :  new ArrayList<SoftwareVersion>();

        final String args = MessageFormat.format("Number of local versions/maximum software versions allowed: [{0}/{1}] current version: {2} local versions: {3} ",
                localVersions.size(), MAX_SOFTWARE_VERSIONS, localCurrent, Strings.repr(localVersions));
        final String prefix = "getUpgradeableRemoteVersions(): " + args + " : ";
        log.info("Getting remote new versions: " + prefix);
	    List<SoftwareVersion> tempList = new ArrayList<SoftwareVersion>();
	    List<SoftwareVersion> toInstallCandidates = new ArrayList<SoftwareVersion>(remoteVersions.keySet());
	    Collections.sort(toInstallCandidates);
	    Collections.reverse(toInstallCandidates);
	    log.debug("Test if a version is upgradeable");
	    ToInstallLoop: for (SoftwareVersion toInstall : toInstallCandidates) {
	        log.debug(" try=" + toInstall);
	
	        //skip version lower than current version
	        if(!forceInstall && localCurrent.compareTo(toInstall) > 0){
	            log.debug(" try=" + toInstall +
	                    ": lower than or equal to existing. Skipping.");
	            continue;
	        }
	       //skip local versions
	        for (SoftwareVersion version: localVersions) {
	            if (version.compareTo(toInstall) == 0 ) {
	                log.debug(" try=" + toInstall +
	                        ": already downloaded {}. Skipping.", version);
	                continue ToInstallLoop;
	            }
	        }
	        if (localCurrent.isNaturallySwitchableTo(toInstall)) {
	        	tempList.add(toInstall);
	        	continue;
	        }
	        for(SoftwareVersion v :remoteVersions.get(toInstall)) {
	        	for(SoftwareVersion s: localVersions) {
	        		if (v.weakEquals(s)) {
	        			log.debug(" try=" + toInstall +
	                            ": can be ungraded from one of the local versions, it's upgradeable.");
	    	        	tempList.add(toInstall); 
	    	        	continue ToInstallLoop;
	        		}
	        	}
	        }
	    }
	    return tempList;
    }
    
    /**
     * Get the list of removable versions in the local repository
     * 
     * @param local the local repository information
     * @param forceRemove whether versions should be in the list if
     *                    they can be fore removed
     * @return the list of software versions that can be removed from the local repository
     * @throws IOException 
     */
    public static SyncInfo removableVersions(final RepositoryInfo local,
                                                          final boolean forceRemove) throws IOException {
        final SoftwareVersion       localCurrent  = (local != null)  ? local.getCurrentVersion() : null;
        final List<SoftwareVersion> localVersions = (local != null  && local.getVersions() != null) ?
                local.getVersions() :  new ArrayList<SoftwareVersion>();        
        return new SyncInfo(findToRemove(localVersions, localCurrent, null, null, forceRemove));
    }
    
    // Method to test if a version is removable. 
    // The first or the last version in the list is removable.
    // Version that cannot be upgraded from its previous versions is removable.
    // Version that cannot upgrade to its following versions is removable.
    // Version a in a list x can upgrade to a list of versions. Those versions form a list y. If any version from list y have at least one version other than target 
    // version to upgrade from, the version a is removable otherwise it's not removable
    // Note that the maximum versions allowed is 4.
    private static boolean isRemovable(List<SoftwareVersion> versions,
            SoftwareVersion version) throws IOException {
        int index = versions.indexOf(version);
        int size = versions.size();
        if (index==0 || index==size-1) {
            return true; // if the tested version is the first or last version in the list it will not cause further inconsistency for sure
            }
        
        // Get lists of versions that target version can upgrade from and upgrade to
        List<SoftwareVersion> upgradeFromVersionList = new ArrayList<SoftwareVersion>();
        List<SoftwareVersion> upgradeToVersionList = new ArrayList<SoftwareVersion>();
        for(int i=0; i<index; i++){
            SoftwareVersion tempVersion = versions.get(i);
            if(tempVersion.isSwitchableTo(version)) {
                upgradeFromVersionList.add(tempVersion);
        }
    }
        for(int i=index+1; i<size; i++){
            SoftwareVersion tempVersion = versions.get(i);
            if(version.isSwitchableTo(tempVersion)) {
                upgradeToVersionList.add(tempVersion);
    }
        }
        
        if (upgradeFromVersionList.isEmpty() || upgradeToVersionList.isEmpty()) {
            return true; // If no version can upgrade to the target version or no version can be upgrade from the target version, target version is removable
        }
        
        OUTLOOP: for(SoftwareVersion v: upgradeToVersionList) {
            int position = versions.indexOf(v);
            for(int i=0; i<position; i++){
                if(i!=index && versions.get(i).isSwitchableTo(v)) continue OUTLOOP; 
                // If this version can be upgrade from a version other than the target continue to test the next version
            }
            return false; // If this version doesn't have any version to upgrade from, false should be returned
        }
        return true; // All versions from the upgradeToVersionList have versions other than the target version to upgrade from, true should be returned
    }

    public static List<SoftwareVersion> findToRemove(final List<SoftwareVersion> localVersions, 
                                                      final SoftwareVersion localCurrent, 
                                                      final SoftwareVersion remoteCurrent,
                                                      final SoftwareVersion toInstall,
                                                      final boolean force) throws IOException {
        List<SoftwareVersion> sortedList = new ArrayList<SoftwareVersion>(localVersions);
        Collections.sort(sortedList);

        List<SoftwareVersion> toRemove = new ArrayList<SoftwareVersion>();
        if (force || sortedList.size() > MAX_SOFTWARE_VERSIONS) {
            for (SoftwareVersion v : sortedList) {
                if (!v.equals(localCurrent) && !v.equals(remoteCurrent) && isRemovable(sortedList, v)) {
                    toRemove.add(v);
                }
            }
        }
        return toRemove;
    }
}
