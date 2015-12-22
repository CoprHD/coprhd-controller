package com.emc.storageos.db.client.upgrade.callbacks;

import java.net.URI;
import java.util.Iterator;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.commons.lang.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.emc.storageos.db.client.impl.EncryptionProviderImpl;
import com.emc.storageos.db.client.model.ComputeImage;
import com.emc.storageos.db.client.model.EncryptionProvider;
import com.emc.storageos.db.client.upgrade.BaseCustomMigrationCallback;

public class ComputeImageMigration extends BaseCustomMigrationCallback {
    private static final Logger log = LoggerFactory
            .getLogger(ComputeImageMigration.class);

    private EncryptionProvider encryptionProvider;
    private static final String IMAGEURL_PASSWORD_SPLIT_REGEX = "(.*?:){2}((?<=\\:).*(?=\\@))";
    private static final String IMAGEURL_HOST_REGEX = "^*(?<=@)([^/@]++)/.*+$";

    @Override
    public void process() {
        log.info("Starting Compute Image Migration");
        if (null == encryptionProvider) {
            EncryptionProviderImpl encryptionProviderImpl = new EncryptionProviderImpl();
            encryptionProviderImpl.setCoordinator(coordinatorClient);
            encryptionProviderImpl.start();
            encryptionProvider = encryptionProviderImpl;
        }
        List<URI> computeImageURIList = dbClient.queryByType(
                ComputeImage.class, true);
        Iterator<ComputeImage> computeImageItr = dbClient
                .queryIterativeObjects(ComputeImage.class, computeImageURIList,
                        true);
        while (computeImageItr.hasNext()) {
            ComputeImage image = computeImageItr.next();
            String imageUrl = image.getImageUrl();
            String password = extractPasswordFromImageUrl(imageUrl);
            String encryptedPassword = password;
            if (StringUtils.isNotBlank(password)) {
                encryptedPassword = encryptionProvider
                        .getEncryptedString(password);
                imageUrl = StringUtils.replace(imageUrl, ":" + password + "@",
                        ":" + encryptedPassword + "@");
            }
            image.setImageUrl(imageUrl);
            dbClient.updateObject(image);
            log.info("Successfully migrated compute image : {} - {}",
                    image.getLabel(), image.getId());
        }
        log.info("Completed Compute Image Migration");
    }

    /**
     * Extract password if present from the given imageUrl string
     * @param imageUrl {@link String} image url
     * @return {@link String} password
     */
    private String extractPasswordFromImageUrl(String imageUrl)
    {
        Pattern r = Pattern.compile(IMAGEURL_PASSWORD_SPLIT_REGEX);
        Matcher m = r.matcher(imageUrl);
        String password = null;
        if (m.find() && m.groupCount() >= 2
                && StringUtils.isNotBlank(m.group(2))) {
            password = m.group(2);
            Pattern hostpattern = Pattern.compile(IMAGEURL_HOST_REGEX);
            Matcher hostMatcher = hostpattern.matcher(password);
            if(hostMatcher.find()) {
                String preHostregex = "^(.*?)\\@"+hostMatcher.group(1);
                Pattern pwdPattern = Pattern.compile(preHostregex);
                Matcher pwdMatcher = pwdPattern.matcher(password);
                if(pwdMatcher.find())
                {
                    password = pwdMatcher.group(1);
                }
            }
        }
        return password;
    }
}
