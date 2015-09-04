/*
 * Copyright 2015 EMC Corporation
 * All Rights Reserved
 */
/**
 *  Copyright (c) 2015 EMC Corporation
 * All Rights Reserved
 *
 * This software contains the intellectual property of EMC Corporation
 * or is licensed to EMC Corporation from third parties.  Use of this
 * software and the intellectual property contained therein is expressly
 * limited to the terms and conditions of the License Agreement under which
 * it is provided by or on behalf of EMC.
 */
package com.emc.storageos.db.common;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import org.springframework.context.support.FileSystemXmlApplicationContext;

public class DbMigrationCallbackChecker {
    private static final String SERVICE_BEAN = "versionedCustomMigrationCallbacks";
    private static final String ADD_CALLBACK = "add";
    private static final String REMOVE_CALLBACK = "remove";

    private Map<String, List<String>> baseVersionedCallbacks = null;
    private Map<String, List<String>> currentVersionedCallbacks = null;

    public DbMigrationCallbackChecker(final String baseFile, final String currentFile) {
        this.baseVersionedCallbacks = getVersionedCallbacks(baseFile);
        this.currentVersionedCallbacks = getVersionedCallbacks(currentFile);
    }

    public DbMigrationCallbackChecker(final Map<String, List<String>> baseCallbacks, final Map<String, List<String>> currentCallbacks) {
        this.baseVersionedCallbacks = baseCallbacks;
        this.currentVersionedCallbacks = currentCallbacks;
    }

    @SuppressWarnings("unchecked")
    private Map<String, List<String>> getVersionedCallbacks(final String beanFile) {
        FileSystemXmlApplicationContext ctx = new FileSystemXmlApplicationContext(beanFile);
        Map<String, List<Object>> bean = (Map<String, List<Object>>) ctx.getBean(SERVICE_BEAN);
        Map<String, List<String>> versionedCallbacks = new HashMap<String, List<String>>();

        for (Map.Entry<String, List<Object>> versionEntry : bean.entrySet()) {
            List<String> callbacks = new ArrayList<String>();
            for (Object callbackInstance : versionEntry.getValue()) {
                callbacks.add(callbackInstance.getClass().getCanonicalName());
            }

            versionedCallbacks.put(versionEntry.getKey(), callbacks);
        }
        return versionedCallbacks;
    }

    public boolean hasDiff() {
        if (this.baseVersionedCallbacks == null || this.currentVersionedCallbacks == null) {
            return false;
        }
        if (this.baseVersionedCallbacks.size() != this.currentVersionedCallbacks.size()) {
            return true;
        }

        for (Entry<String, List<String>> currentCallbacks : this.currentVersionedCallbacks.entrySet()) {
            List<String> baseCallbacks = this.baseVersionedCallbacks.get(currentCallbacks.getKey());
            List<MigrationCallbackDiff> listDiff = getListDiff(baseCallbacks, currentCallbacks.getValue());
            if (listDiff != null && listDiff.size() > 0) {
                return true;
            }
        }

        return false;
    }

    public Map<String, List<MigrationCallbackDiff>> getDiff() {
        Map<String, List<MigrationCallbackDiff>> versionedDiffs = new HashMap<String, List<MigrationCallbackDiff>>();

        for (Map.Entry<String, List<String>> versionedCurrentCallbacks : this.currentVersionedCallbacks.entrySet()) {
            List<String> baseCallbacks = this.baseVersionedCallbacks.get(versionedCurrentCallbacks.getKey());
            List<MigrationCallbackDiff> diff = getListDiff(baseCallbacks, versionedCurrentCallbacks.getValue());
            if (diff != null && diff.size() > 0) {
                versionedDiffs.put(versionedCurrentCallbacks.getKey(), diff);
            }
        }

        return Collections.unmodifiableMap(versionedDiffs);
    }

    private List<MigrationCallbackDiff> getListDiff(final List<String> baseCallbacks, final List<String> list) {
        if (baseCallbacks == null || baseCallbacks.size() <= 0) {
            return MigrationCallbackDiff.build(ADD_CALLBACK, list);
        }
        if (list == null || list.size() <= 0) {
            return MigrationCallbackDiff.build(REMOVE_CALLBACK, baseCallbacks);
        }

        List<MigrationCallbackDiff> diffs = new ArrayList<MigrationCallbackDiff>();
        for (String callback : baseCallbacks) {
            if (!list.contains(callback)) {
                diffs.add(MigrationCallbackDiff.build(REMOVE_CALLBACK, callback));
            }
        }
        for (String callback : list) {
            if (!baseCallbacks.contains(callback)) {
                diffs.add(MigrationCallbackDiff.build(ADD_CALLBACK, callback));
            }
        }
        return diffs;
    }

    public void setBaselineVersionedCallbacks(
            Map<String, List<String>> baselineVersionedCallbacks) {
        this.baseVersionedCallbacks = baselineVersionedCallbacks;
    }

    public void setCurrentVersionedCallbacks(
            Map<String, List<String>> currentVersionedCallbacks) {
        this.currentVersionedCallbacks = currentVersionedCallbacks;
    }

    public static class MigrationCallbackDiff {
        private String action;
        private String callback;

        public MigrationCallbackDiff(String action, String callback) {
            super();
            this.action = action;
            this.callback = callback;
        }

        public String getAction() {
            return action;
        }

        public void setAction(String action) {
            this.action = action;
        }

        public String getCallback() {
            return callback;
        }

        public void setCallback(String callback) {
            this.callback = callback;
        }

        public static List<MigrationCallbackDiff> build(final String action, final List<String> list) {
            List<MigrationCallbackDiff> diffs = new ArrayList<MigrationCallbackDiff>();
            if (action == null || list == null) {
                return diffs;
            }
            for (String callback : list) {
                MigrationCallbackDiff diff = new MigrationCallbackDiff(action, callback);
                diffs.add(diff);
            }

            return diffs;
        }

        @Override
        public String toString() {
            return this.action + "  " + this.callback;
        }

        public static MigrationCallbackDiff build(final String action, final String callback) {
            return new MigrationCallbackDiff(action, callback);
        }
    }

}
