/*
 * Copyright 2015 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.systemservices;

import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import org.junit.Assert;
import org.junit.Test;

import com.emc.storageos.coordinator.client.model.SoftwareVersion;
import com.emc.storageos.coordinator.client.model.SoftwareVersionMetadata;
import com.emc.storageos.coordinator.exceptions.InvalidSoftwareVersionException;

import java.io.File;
import java.nio.charset.StandardCharsets;

import javax.swing.filechooser.FileSystemView;

public class SoftwareVersionMetadataTest {
    static File homeDirectory = FileSystemView.getFileSystemView().getHomeDirectory();
    static String template = homeDirectory.getAbsolutePath() + File.separator + "%s";
    static Random ran = new Random();

    @Test
    public void testSoftwareVersionMetadata() throws Exception {
        new TestProductName(); // NOSONAR
                               // ("squid:S1848 Suppress Sonar warning that created objects are never used. The constructor is called to set static fields")
        SoftwareVersionMetadata.setimageFileTemplate(template);

        String dataString200 = "upgrade_from:vipr-1.1.0.0.*\ndowngrade_to:vipr-1.1.0.0.*\nversion:vipr-2.0.0.0.200";
        String dataString201 = "upgrade_from:vipr-1.1.0.0.*;downgrade_to:vipr-1.1.0.0.*;version:vipr-2.0.0.0.201";
        String dataString202 = "upgradefrom:vipr-1.0.0.0.*;vipr-1.1.0.0.*\ndowngradeto:vipr-1.1.0.0.*\nversion:vipr-2.0.0.0.202";
        String dataString203 = "upgrade_from:\ndowngrade_to:vipr-1.1.0.0.*\nversion:vipr-2.0.0.0.203";
        String dataString204 = "upgrade_from:vipr-1.0.0.0.*,vipr-1.1.0.0.*\ndowngrade_to:vipr-1.1.0.0.*\nversion:vipr-2.0.0.0.204";

        // Test on dummy vipr-2.0.0.0.200 image file
        createTestImageFile(dataString200, "vipr-2.0.0.0.200");

        SoftwareVersionMetadata data200 = SoftwareVersionMetadata.getInstance(new SoftwareVersion("vipr-2.0.0.0.200"));
        System.out.println(data200.version.toString());
        System.out.println(data200.upgradeFromVersionsList.toString());
        System.out.println(data200.downgradeToVersionsList.toString());
        List<SoftwareVersion> tmpUpList200 = new ArrayList<SoftwareVersion>();
        tmpUpList200.add(new SoftwareVersion("vipr-1.1.0.0.*"));
        List<SoftwareVersion> tmpDownList200 = new ArrayList<SoftwareVersion>();
        tmpDownList200.add(new SoftwareVersion("vipr-1.1.0.0.*"));
        Assert.assertTrue(new SoftwareVersion("vipr-2.0.0.0.200").equals(data200.version));
        Assert.assertTrue(tmpUpList200.equals(data200.upgradeFromVersionsList));
        Assert.assertTrue(tmpDownList200.equals(data200.downgradeToVersionsList));

        deleteImageFile("vipr-2.0.0.0.200");

        // Test on dummy vipr-2.0.0.0.201 image file
        createTestImageFile(dataString201, "vipr-2.0.0.0.201");

        try {
            SoftwareVersionMetadata data201 = SoftwareVersionMetadata.getInstance(new SoftwareVersion("vipr-2.0.0.0.201"));
        } catch (Exception e) {
            Assert.assertEquals(e.getClass(), InvalidSoftwareVersionException.class);
        }

        deleteImageFile("vipr-2.0.0.0.201");

        // Test on dummy vipr-2.0.0.0.202 image file
        createTestImageFile(dataString202, "vipr-2.0.0.0.202");

        try {
            SoftwareVersionMetadata data202 = SoftwareVersionMetadata.getInstance(new SoftwareVersion("vipr-2.0.0.0.202"));
        } catch (Exception e) {
            Assert.assertEquals(e.getClass(), InvalidSoftwareVersionException.class);
        }

        deleteImageFile("vipr-2.0.0.0.202");

        // Test on dummy vipr-2.0.0.0.203 image file
        createTestImageFile(dataString203, "vipr-2.0.0.0.203");

        SoftwareVersionMetadata data203 = SoftwareVersionMetadata.getInstance(new SoftwareVersion("vipr-2.0.0.0.203"));
        System.out.println(data203.version.toString());
        System.out.println(data203.upgradeFromVersionsList.toString());
        System.out.println(data203.downgradeToVersionsList.toString());
        List<SoftwareVersion> tmpUpList203 = new ArrayList<SoftwareVersion>();
        List<SoftwareVersion> tmpDownList203 = new ArrayList<SoftwareVersion>();
        tmpDownList203.add(new SoftwareVersion("vipr-1.1.0.0.*"));
        Assert.assertTrue(new SoftwareVersion("vipr-2.0.0.0.203").equals(data203.version));
        Assert.assertTrue(tmpUpList203.equals(data203.upgradeFromVersionsList));
        Assert.assertTrue(tmpDownList203.equals(data203.downgradeToVersionsList));

        deleteImageFile("vipr-2.0.0.0.203");

        // Test on dummy vipr-2.0.0.0.204 image file
        createTestImageFile(dataString204, "vipr-2.0.0.0.204");

        SoftwareVersionMetadata data204 = SoftwareVersionMetadata.getInstance(new SoftwareVersion("vipr-2.0.0.0.204"));
        System.out.println(data204.version.toString());
        System.out.println(data204.upgradeFromVersionsList.toString());
        System.out.println(data204.downgradeToVersionsList.toString());
        List<SoftwareVersion> tmpUpList204 = new ArrayList<SoftwareVersion>();
        tmpUpList204.add(new SoftwareVersion("vipr-1.0.0.0.*"));
        tmpUpList204.add(new SoftwareVersion("vipr-1.1.0.0.*"));
        List<SoftwareVersion> tmpDownList204 = new ArrayList<SoftwareVersion>();
        tmpDownList204.add(new SoftwareVersion("vipr-1.1.0.0.*"));
        Assert.assertTrue(new SoftwareVersion("vipr-2.0.0.0.204").equals(data204.version));
        Assert.assertTrue(tmpUpList204.equals(data204.upgradeFromVersionsList));
        Assert.assertTrue(tmpDownList204.equals(data204.downgradeToVersionsList));

        deleteImageFile("vipr-2.0.0.0.204");
    }

    private void createTestImageFile(String metadataString, String versionString)
            throws FileNotFoundException, IOException {
        byte[] stringByteArray = metadataString.getBytes(StandardCharsets.UTF_8);
        int len = stringByteArray.length;

        int x = ran.nextInt(1001);
        byte[] targetByteArray = new byte[x + 20516];
        for (int i = 0; i < len; i++) {
            targetByteArray[x + i] = stringByteArray[i];
        }
        String testPath = String.format(template, versionString);
        FileOutputStream fileOuputStream =
                new FileOutputStream(testPath);
        fileOuputStream.write(targetByteArray);
        fileOuputStream.close();
    }

    private void deleteImageFile(String versionString) {
        String filePath = String.format(template, versionString);
        File file = new File(filePath);
        file.delete();
    }

}