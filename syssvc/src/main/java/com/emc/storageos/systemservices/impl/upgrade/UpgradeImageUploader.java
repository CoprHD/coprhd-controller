/*
 * Copyright (c) 2012-2014 EMC Corporation
 * All Rights Reserved
 */

package com.emc.storageos.systemservices.impl.upgrade;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.emc.storageos.coordinator.client.model.SoftwareVersion;

import java.io.*;
import java.text.MessageFormat;

import static com.emc.storageos.coordinator.client.model.Constants.*;

public class UpgradeImageUploader {
    private static final Logger _log = LoggerFactory.getLogger(UpgradeImageDownloader.class);
    private static UpgradeImageUploader _instance = null;
    private static UpgradeManager _upgradeManager = null;
    private String _version;
    private UpgradeImageUploader() {}

    public static UpgradeImageUploader getInstance(UpgradeManager manager) {
        synchronized (UpgradeImageUploader.class) {
            if (_instance == null) {
                _instance = new UpgradeImageUploader();
                _upgradeManager = manager;
            }
        }
        return _instance;
    }

    public void cleanUploadFiles() {
        File dir = new File(UPLOAD_DIR);

        if (dir.listFiles() != null) {
            for (File file : dir.listFiles()) {
                file.delete();
            }
        }
    }

    public File startUpload(InputStream in, String version) {
        String fileName = UPLOAD_DIR + '/' + System.currentTimeMillis() + SOFTWARE_IMAGE_SUFFIX;
        final File file = new File(fileName);
        final String prefix = MessageFormat.format("uploadImage(): path=\"{0}\": ", fileName);
        _version = version;
        _log.info(prefix + "Upload started.");

        upload(prefix, in, file);

        _log.info(prefix + "Upload ended.");

        return file;
    }

    private void upload(String prefix, InputStream in, File file) {
        if (file.exists()) {
            file.delete();
            if (file.exists()) {
                _log.error(prefix + "Upload failed. Can't remove " + file);
                return;
            }
        }

        final File tmp = getTmpFile(file);
        if (tmp.exists()) {
            tmp.delete();
            if (tmp.exists()) {
                _log.error(prefix + "Upload failed. Can't remove " + tmp);
                return;
            }
        }

        final File dir = tmp.getParentFile();
        if (!dir.exists()) {
            dir.mkdirs();
            if (!dir.exists()) {
                _log.error(prefix + "Upload failed. Can't create directory " + dir);
                return;
            }
        }

        long start = System.currentTimeMillis();
        OutputStream out = null;

        try {
            out = new BufferedOutputStream(new FileOutputStream(tmp));

            UpgradeImageCommon upgradeImage = new UpgradeImageCommon(in, out, _log, prefix, _upgradeManager, _version);
            if (!upgradeImage.start()) {
                return;
            }

            if (!tmp.renameTo(file)) {
                _log.error(prefix + "Upload failed. Can't rename " + tmp);
                return;
            } else if (!file.exists()) {
                _log.error(prefix + "Upload failed. No such file " + file);
                return;
            }

            long end = System.currentTimeMillis();
            _log.info(prefix + "Upload successful. Time cost: " + (end - start) / 1000 + " seconds.");
        } catch (Exception e) {
            _log.error(prefix + "Upload failed. {} ", e);
        } finally {
            tryClose(in);
            tryClose(out);
            tmp.delete();
        }
    }


    private static File getTmpFile(File file) {
        return new File(file + ".uploading");
    }

    private static void tryClose(final InputStream in) {
        try {
            if (in != null) in.close();
        } catch(Exception e) {
            ;
        }
    }

    private static void tryClose(final OutputStream out) {
        try {
            if (out != null) out.close();
        } catch(Exception e) {
            ;
        }
    }
}
