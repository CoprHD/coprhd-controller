/*
 * Copyright (c) 2015 EMC Corporation
 * All Rights Reserved
 *
 * This software contains the intellectual property of EMC Corporation
 * or is licensed to EMC Corporation from third parties.  Use of this
 * software and the intellectual property contained therein is expressly
 * limited to the terms and conditions of the License Agreement under which
 * it is provided by or on behalf of EMC.
 */

import com.emc.storageos.volumecontroller.impl.plugins.metering.smis.processor.StorageSystemStatsProcessor;
import com.emc.vipr.client.ClientConfig;
import com.emc.vipr.client.ViPRCoreClient;
import com.emc.vipr.client.core.util.TimeBucketUtils;
import org.joda.time.LocalDate;

import java.io.*;
import java.text.NumberFormat;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.concurrent.TimeUnit;

public class GetEvent {

    static String host = "10.247.78.65";
    static boolean dump = false;

    public static void listEvent() throws Exception {

        ClientConfig config = new ClientConfig();
        config.setIgnoreCertificates(true);
        config.setHost(host);

        ViPRCoreClient client = new ViPRCoreClient(config).withLogin("root", "ChangeMe");

        SimpleDateFormat formatter = new SimpleDateFormat(TimeBucketUtils.HOUR_BUCKET_TIME_FORMAT);

        String timeBucketStr = String.format("2015-09-10T16");
        Date timeBucket = formatter.parse(timeBucketStr);

        InputStream stream = client.monitoring().getEventsForTimeBucketAsStream(timeBucket);
        FileOutputStream outFile = new FileOutputStream("event-" + timeBucketStr + ".xml");

        byte[] buf = new byte[1024 * 1024 * 16];
        long totalSize = 0;
        while (true) {
            int n = stream.read(buf);
            if (n < 0) {
                break;
            }
            totalSize += n;
            //    System.out.println("read something its len = " + n);
            if (dump) {
                outFile.write(buf, 0, n);
            }
        }
        outFile.close();
        System.out.println("timeBucketStr: total size is " + NumberFormat.getNumberInstance(Locale.US).format(totalSize));
    }

    public static void main(String[] args) throws Exception {
        if (System.getProperty("dump") != null) {
            dump = true;
        }

        long start = System.currentTimeMillis();
        new GetEvent().listEvent();
        long costTime = System.currentTimeMillis() - start;

        String result = String.format("%d min, %d sec",
                TimeUnit.MILLISECONDS.toMinutes(costTime),
                TimeUnit.MILLISECONDS.toSeconds(costTime) - TimeUnit.MINUTES.toSeconds(TimeUnit.MILLISECONDS.toMinutes(costTime)));

        System.out.println("Take " + result);
    }
}
