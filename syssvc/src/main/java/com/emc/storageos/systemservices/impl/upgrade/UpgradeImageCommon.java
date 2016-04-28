/*
 * Copyright (c) 2013-2014 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.systemservices.impl.upgrade;

import org.slf4j.Logger;

import com.emc.storageos.coordinator.client.model.DownloadingInfo;
import com.emc.vipr.model.sys.NodeProgress.DownloadStatus;

import static com.emc.storageos.coordinator.client.model.Constants.*;

import java.io.*;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.zip.CRC32;

/**
 * This class is used for both downloading file and checking trailer
 */
public class UpgradeImageCommon {
    private static final int DOWNLOAD_MONITORING_TIME_INCREMENT = 1000;
    private static final int DOWNLOAD_MONITORING_SIZE_INCREMENT = 1048576;
    private static final int MINIMUM_IMAGE_BYTES = 256;
    InputStream _in;
    OutputStream _out;
    Logger _log;
    String _prefix;
    UpgradeManager _manager = null;
    String _version;

    public UpgradeImageCommon(InputStream in, OutputStream out, Logger log, String prefix, UpgradeManager manager, String version) {
        _in = in;
        _out = out;
        _log = log;
        _prefix = prefix;
        _manager = manager;
        _version = version;
    }

    public boolean start() throws Exception {
        MessageDigest sha1 = MessageDigest.getInstance("SHA1");

        // chunk size
        int chunkSize = 0x10000;
        // main buffer used to read and write
        byte[] buffer = new byte[chunkSize];
        // trailer buffer
        byte[] trailerBuffer = new byte[TRAILER_LENGTH];
        // number of valid bytes in trailerBuffer
        int validBytesCnt = 0;

        int bytesRead = 0;
        long bytesWritten = 0;

        long bytesDownloaded = 0;
        long currentTime = System.currentTimeMillis();

        CoordinatorClientExt coordinator = _manager.getCoordinator();
        String svcId = coordinator.getMySvcId();
        DownloadingInfo targetDownloadingInfo = coordinator.getTargetInfo(DownloadingInfo.class);
        boolean trackProgress = false;
        long imageSize = 0;
        if (targetDownloadingInfo != null && _version.equals(targetDownloadingInfo._version)) {
            trackProgress = true;
            imageSize = targetDownloadingInfo._size;
        }

        ArrayList<Integer> counter = null;
        // Need to get the accumulated error count, if there is no previous download or it's downloading a new version, we should use a new
        // counter
        if (trackProgress) {
            DownloadingInfo nodeDownloadingInfo = coordinator.getNodeGlobalScopeInfo(DownloadingInfo.class,
                    DOWNLOADINFO_KIND, svcId);
            if (nodeDownloadingInfo == null || nodeDownloadingInfo._version != targetDownloadingInfo._version) {
                counter = targetDownloadingInfo._errorCounter;
            } else {
                counter = nodeDownloadingInfo._errorCounter;
            }
        }
        try {
            while (true) {
                bytesRead = _in.read(buffer);
                if (bytesRead == -1) {
                    break;
                }

                int overFlow = validBytesCnt + bytesRead - TRAILER_LENGTH;
                if (overFlow > 0) {
                    if (overFlow >= validBytesCnt) {
                        // compute sha for the valid bytes staying in cache from last read
                        sha1.update(trailerBuffer, 0, validBytesCnt);
                        // copy last trailer length bytes into cache
                        System.arraycopy(buffer, bytesRead - TRAILER_LENGTH, trailerBuffer, 0, TRAILER_LENGTH);
                        // sha the rest in buffer
                        sha1.update(buffer, 0, bytesRead - TRAILER_LENGTH);
                    } else {
                        // sha the bytes which cannot be trailer
                        sha1.update(trailerBuffer, 0, overFlow);
                        // shift trailer to left
                        for (int i = overFlow; i < validBytesCnt; i++) {
                            trailerBuffer[i - overFlow] = trailerBuffer[i];
                        }
                        // add read bytes at the end of trailer buffer
                        System.arraycopy(buffer, 0, trailerBuffer, TRAILER_LENGTH - bytesRead, bytesRead);
                    }
                    // trailer must be full
                    validBytesCnt = TRAILER_LENGTH;
                } else {
                    // trailer does not overflow, add read bytes at the end
                    System.arraycopy(buffer, 0, trailerBuffer, validBytesCnt, bytesRead);
                    validBytesCnt += bytesRead;
                }

                _out.write(buffer, 0, bytesRead);
                bytesWritten += bytesRead;

                long timeNow = System.currentTimeMillis();
                if (bytesWritten - bytesDownloaded > DOWNLOAD_MONITORING_SIZE_INCREMENT
                        && timeNow - currentTime > DOWNLOAD_MONITORING_TIME_INCREMENT) {
                    if (trackProgress) {
                        DownloadingInfo tmpInfo = coordinator.getTargetInfo(DownloadingInfo.class);
                        bytesDownloaded = bytesWritten;
                        currentTime = timeNow;
                        _log.info("The bytesDownloaded is " + bytesDownloaded);
                        if (null != tmpInfo && DownloadStatus.CANCELLED == tmpInfo._status) { // Check the target info of the
                                                                                              // DownloadingInfo class to see if user sent a
                                                                                              // cancel request
                            coordinator.setNodeGlobalScopeInfo(new DownloadingInfo(_version, imageSize, 0, DownloadStatus.CANCELLED,
                                    counter), DOWNLOADINFO_KIND, svcId);
                            return false;
                        }
                        coordinator.setNodeGlobalScopeInfo(new DownloadingInfo(_version, imageSize, bytesDownloaded, DownloadStatus.NORMAL,
                                counter), DOWNLOADINFO_KIND, svcId); // reset the bytesDownloaded field
                    }
                }
            }

            _log.info(_prefix + "Downloaded " + bytesWritten + " bytes");

            byte[] sha1Bytes = sha1.digest();
            // In the case of a failure download.emc.com will still return a 200 status code
            // but the file that will be downloaded is an error page. So if a small number of
            // bytes is downloaded just output the downloaded content to the logs
            if (bytesWritten > 0 && bytesWritten < MINIMUM_IMAGE_BYTES) {
                _log.error(_prefix + "Downloaded error: {}", new String(buffer, "UTF-8"));
                if (trackProgress) {
                    int downloadErrorCount = counter.get(0);
                    counter.set(0, ++downloadErrorCount);
                    coordinator.setNodeGlobalScopeInfo(new DownloadingInfo(_version, imageSize, 0, DownloadStatus.DOWNLOADERROR, counter),
                            DOWNLOADINFO_KIND, svcId); // It indicate a download error
                }
                return false;
            } else if (!verifyChecksum(sha1Bytes, trailerBuffer, _log)) {
                if (trackProgress) {
                    int checksumErrorCount = counter.get(1);
                    counter.set(1, ++checksumErrorCount);
                    coordinator.setNodeGlobalScopeInfo(new DownloadingInfo(_version, imageSize, 0, DownloadStatus.CHECKSUMERROR, counter),
                            DOWNLOADINFO_KIND, svcId); // It indicate that checksum failed
                }
                return false;
            }
            if (trackProgress) {
                DownloadingInfo tmpInfo = coordinator.getTargetInfo(DownloadingInfo.class);
                if (null != tmpInfo && DownloadStatus.CANCELLED == tmpInfo._status) { // Check the target info of the DownloadingInfo class
                                                                                      // to see if user sent a cancel request
                    coordinator.setNodeGlobalScopeInfo(new DownloadingInfo(_version, imageSize, 0, DownloadStatus.CANCELLED, counter),
                            DOWNLOADINFO_KIND, svcId);
                    return false;
                }
                coordinator.setNodeGlobalScopeInfo(new DownloadingInfo(_version, imageSize, imageSize, DownloadStatus.COMPLETED, counter),
                        DOWNLOADINFO_KIND, svcId); // When it reaches here, it means the download is successful
            }
        } finally {
            _out.close();
            _in.close();
        }
        return true;
    }

    private boolean verifyChecksum(final byte[] sha1Bytes, final byte[] trailerBytes, final Logger log) {
        // decompose trailer
        byte[] trailerSha = Arrays.copyOfRange(trailerBytes, TRAILER_SHA_OFFSET, TRAILER_SHA_SIZE);
        byte[] trailerCRC = Arrays.copyOfRange(trailerBytes, TRAILER_CRC_OFFSET, TRAILER_CRC_OFFSET + TRAILER_CRC_SIZE);
        byte[] trailerLen = Arrays.copyOfRange(trailerBytes, TRAILER_LEN_OFFSET, TRAILER_LEN_OFFSET + TRAILER_LEN_SIZE);
        byte[] trailerMagic = Arrays.copyOfRange(trailerBytes, TRAILER_MAGIC_OFFSET, TRAILER_LENGTH);

        // calculate crc32 based on trailerSHA, trailerLen, trailerMagic
        byte[] trailerCopy = Arrays.copyOf(trailerBytes, trailerBytes.length);
        Arrays.fill(trailerCopy, TRAILER_CRC_OFFSET, TRAILER_CRC_OFFSET + TRAILER_CRC_SIZE, (byte) 0);

        CRC32 crc32 = new CRC32();
        crc32.update(trailerCopy);
        long crc32Val = crc32.getValue();

        // test crc32 == trailerCrc32
        if (crc32Val != byteArrayToLong(trailerCRC)) {
            log.error(_prefix + "Downloaded file is corrupted. Trailer CRC check failed {}", trailerCRC);
            return false;
        }

        // test trailerLen == 36
        if (TRAILER_LEN_VALUE != byteArrayToLong(trailerLen)) {
            log.error(_prefix + "Downloaded file is corrupted. Trailer length check failed {}", trailerLen);
            return false;
        }

        // test trailerMagic == 0x3031656e72756f42L
        if (TRAILER_MAGIC_VALUE != byteArrayToLong(trailerMagic)) {
            log.error(_prefix + "Downloaded file is corrupted. Trailer magic check failed {}", trailerMagic);
            return false;
        }

        // test sha1Bytes == trailerSha
        if (!Arrays.equals(trailerSha, sha1Bytes)) {
            log.error(_prefix + "Downloaded file is corrupted. Trailer sha1 check failed {}", trailerSha);
            return false;
        }

        return true;
    }

    private long byteArrayToLong(byte[] byteArray) {
        byte[] buffer = new byte[8];
        int start = 8 - byteArray.length;
        for (int i = 0; i < byteArray.length; i++) {
            buffer[start + i] = byteArray[i];
        }

        ByteBuffer byteBuffer = ByteBuffer.wrap(buffer).order(ByteOrder.BIG_ENDIAN);
        return byteBuffer.getLong();
    }
}
