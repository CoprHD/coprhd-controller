/*
 * Copyright (c) 2012-2014 EMC Corporation
 * All Rights Reserved
 */

package com.emc.storageos.systemservices.impl.upgrade;

import java.io.*;
import java.text.MessageFormat;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.ConcurrentHashMap;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.emc.storageos.services.util.AlertsLogger;

public class UpgradeImageDownloader {
    private static final Logger _log = LoggerFactory.getLogger(UpgradeImageDownloader.class);
    private static UpgradeManager _upgradeManager = null;
    private static UpgradeImageDownloader _instance = null;

    public static UpgradeImageDownloader getInstance(final UpgradeManager upgradeManager) {
        synchronized (UpgradeImageDownloader.class) {
            if (_instance == null) {
                _instance = new UpgradeImageDownloader();
                _upgradeManager = upgradeManager;
            }
        }
        return _instance;
    }

    public void startBackgroundDownload(final String prefix, final File file, final InputStream in, final String url, String version) {
        _log.info(prefix + "");

        if (gcDownloads() > 0) {
            _log.error(prefix + "There is a download in progress.");
            tryClose(in);
            return;
        }

        Future<DownloadTask> future = (Future<DownloadTask>) _executor.submit(new DownloadTask(prefix, file, in, url, version));
        if (future == null) {
            _log.error(prefix + "Failed!");
            tryClose(in);
            return;
        }

        _downloads.put(file.getPath(), future);
        _log.info(prefix + "Download started.");
    }

    public void shutdownNow() {
        for (String downloadPath : _downloads.keySet()) {
            final Future<DownloadTask> f = _downloads.get(downloadPath);
            if (!f.isDone()) {
                f.cancel(true);
            }
        }
        _executor.shutdownNow();
        gcDownloads();
    }

    public boolean isDownloading() {
        return gcDownloads() != 0;
    }

    private int gcDownloads() {
        _log.info("gcDownloads():");
        List<String> done = new ArrayList<String>();
        for (String downloadPath : _downloads.keySet()) {
            if (_downloads.get(downloadPath).isDone()) {
                final File tmp = getTmpFile(new File(downloadPath));
                if (tmp.exists()) {
                    tmp.delete();
                    if (tmp.exists()) {
                        _log.error("gcDownloads(): Failed to remove {}", tmp);
                    }
                }
                done.add(downloadPath);
            }
        }
        for (String downloadPath : done) {
            _downloads.remove(downloadPath);
        }
        final int inProgress = _downloads.size();
        _log.info("gcDownloads(): {} downloads in progress", inProgress);
        return inProgress;
    }

    private static File getTmpFile(File file) {
        return new File(file + ".downloading");
    }

    private static void tryClose(final InputStream in) {
        try {
            if (in != null) {
                in.close();
            }
        } catch (Exception e) {
            ;
        }
    }

    private static void tryClose(final OutputStream out) {
        try {
            if (out != null) {
                out.close();
            }
        } catch (Exception e) {
            ;
        }
    }

    public class DownloadTask implements Runnable {
        private final String _prefix;
        private final InputStream _in;
        private final File _file;
        private final String _url;
        private final String _version;

        public DownloadTask(final String prefix, final File file, final InputStream in, final String url, String version) {
            _prefix = prefix;
            _in = in;
            _file = file;
            _url = url;
            _version = version;
        }

        @Override
        public void run() {
            _log.info(_prefix + "Background download.");
            download();
            if (_upgradeManager != null) {
                _upgradeManager.wakeup();
            }
        }

        private void download() {

            if (_file.exists()) {
                _file.delete();
                if (_file.exists()) {
                    _log.error(_prefix + "Download failed. Can't remove " + _file);
                    return;
                }
            }

            final File tmp = getTmpFile(_file);
            if (tmp.exists()) {
                tmp.delete();
                if (tmp.exists()) {
                    _log.error(_prefix + "Download failed. Can't remove " + tmp);
                    return;
                }
            }

            final File dir = tmp.getParentFile();
            if (!dir.exists()) {
                dir.mkdirs();
                if (!dir.exists()) {
                    _log.error(_prefix + "Download failed. Can't create directory " + dir);
                    return;
                }
            }

            long start = System.currentTimeMillis();
            OutputStream out = null;

            try {
                out = new BufferedOutputStream(new FileOutputStream(tmp));

                UpgradeImageCommon upgradeImage = new UpgradeImageCommon(_in, out, _log, _prefix, _upgradeManager, _version);
                if (!upgradeImage.start()) {
                    AlertsLogger.getAlertsLogger().error(
                            MessageFormat.format("Unexpected error downloading image from url \"{0}\". See syssvc logs for details", _url));
                    return;
                }

                if (!tmp.renameTo(_file)) {
                    _log.error(_prefix + "Download failed. Can't rename to: " + tmp);
                    return;
                } else if (!_file.exists()) {
                    _log.error(_prefix + "Download failed. No such file: " + _file);
                    return;
                }

                long end = System.currentTimeMillis();
                _log.info(_prefix + "Download successful. Time cost: " + (end - start) / 1000 + " seconds.");
            } catch (Exception e) {
                _log.error(_prefix + "Download failed. " + e);
            } finally {
                tryClose(_in);
                tryClose(out);
                tmp.delete();
            }
        }
    }

    private UpgradeImageDownloader() {
    }

    private final Map<String, Future<DownloadTask>> _downloads = new ConcurrentHashMap<String, Future<DownloadTask>>();
    private final ExecutorService _executor = Executors.newFixedThreadPool(1);
}
