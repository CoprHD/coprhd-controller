package com.emc.storageos.db.server.upgrade.impl.callback;

import java.io.IOException;
import java.net.URI;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

import org.junit.Assert;
import org.junit.BeforeClass;

import com.emc.storageos.db.client.DbClient;
import com.emc.storageos.db.client.URIUtil;
import com.emc.storageos.db.client.model.ComputeImage;
import com.emc.storageos.db.client.upgrade.BaseCustomMigrationCallback;
import com.emc.storageos.db.client.upgrade.callbacks.ComputeImageMigration;
import com.emc.storageos.db.server.DbsvcTestBase;
import com.emc.storageos.db.server.upgrade.DbSimpleMigrationTestBase;

public class ComputeImageMigrationTest extends DbSimpleMigrationTestBase {

    private static final String urlWithPwd = "ftp://username:samplepwd@somehost.test.com/somefile.iso";
    private static final String urlWithoutPwd = "ftp://somehost.test.com/somefile.iso";
    private static final String IMAGE_NAME_WITH_PWD = "imageWithPwd";
    private static final String IMAGE_NAME_WITHOUT_PWD = "imageWithoutPwd";

    @BeforeClass
    public static void setup() throws IOException {

        customMigrationCallbacks.put("2.4", new ArrayList<BaseCustomMigrationCallback>() {
            private static final long serialVersionUID = 1L;
            {
                // Add your implementation of migration callback below.
                add(new ComputeImageMigration());
            }
        });

        DbsvcTestBase.setup();
    }

    /*
     * (non-Javadoc)
     * 
     * @see com.emc.storageos.db.server.upgrade.DbSimpleMigrationTestBase#getSourceVersion()
     */
    @Override
    protected String getSourceVersion() {
        return "2.4";
    }

    /*
     * (non-Javadoc)
     * 
     * @see com.emc.storageos.db.server.upgrade.DbSimpleMigrationTestBase#getTargetVersion()
     */
    @Override
    protected String getTargetVersion() {
        return "2.4.1";
    }

    @Override
    protected void prepareData() throws Exception {
        DbClient dbClient = getDbClient();

        ComputeImage image1 = new ComputeImage();
        image1.setId(URIUtil.createId(ComputeImage.class));
        image1.setLabel(IMAGE_NAME_WITH_PWD);
        image1.setImageUrl(urlWithPwd);
        dbClient.createObject(image1);

        ComputeImage image2 = new ComputeImage();
        image2.setId(URIUtil.createId(ComputeImage.class));
        image2.setLabel(IMAGE_NAME_WITHOUT_PWD);
        image2.setImageUrl(urlWithoutPwd);
        dbClient.createObject(image2);
    }

    @Override
    protected void verifyResults() throws Exception {
        DbClient dbClient = getDbClient();
        List<URI> imageUris = dbClient.queryByType(ComputeImage.class, true);
        Iterator<ComputeImage> imageItr = dbClient.queryIterativeObjects(ComputeImage.class, imageUris);
        while(imageItr.hasNext())
        {
            ComputeImage image = imageItr.next();
            if(image.getLabel().equalsIgnoreCase(IMAGE_NAME_WITH_PWD)) {
                Assert.assertNotEquals(image.getImageUrl(), urlWithPwd);
            }else if(image.getLabel().equalsIgnoreCase(IMAGE_NAME_WITHOUT_PWD)) {
                Assert.assertEquals(image.getImageUrl(), urlWithoutPwd);
            }
        }
    }
}
