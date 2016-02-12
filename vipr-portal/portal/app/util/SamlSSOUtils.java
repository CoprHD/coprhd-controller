/*
 * Copyright (c) 2016 EMC Corporation
 * All Rights Reserved
 */

package util;

import com.emc.storageos.model.auth.SamlMetadata;
import com.emc.storageos.model.auth.SamlMetadataResponse;
import com.emc.storageos.model.usergroup.UserGroupCreateParam;
import com.emc.storageos.model.usergroup.UserGroupRestRep;
import com.emc.vipr.client.exceptions.ViPRHttpException;
import com.sun.jersey.api.client.ClientResponse;
import org.apache.commons.httpclient.HttpStatus;

import javax.ws.rs.core.Response;

import java.io.IOException;
import java.io.InputStream;
import java.util.Scanner;

import static util.BourneUtil.getViprClient;

public class SamlSSOUtils {

    public static String getSamlMetadataXML() throws IOException{
        try {

            ClientResponse response = getViprClient().getSamlSingleSignOn().getSamlMetadataXML();
            InputStream inputStream = response.getEntityInputStream();
            Scanner scanner = new Scanner(inputStream);
            StringBuilder builder = new StringBuilder();
            while(scanner.hasNextLine()) {
                builder.append(scanner.nextLine());
            }
            inputStream.close();

            return builder.toString();
        } catch (ViPRHttpException viprException) {
            if (viprException.getHttpCode() == HttpStatus.SC_BAD_REQUEST) {
                return null;
            }
            throw viprException;
        }
    }

    public static SamlMetadata getSamlMetadataObject() {
        try {
            return getViprClient().getSamlSingleSignOn().getSamlMetadataObject();
        } catch (ViPRHttpException viprException) {
            if (viprException.getHttpCode() == HttpStatus.SC_BAD_REQUEST) {
                return null;
            }
            throw viprException;
        }
    }

    public static SamlMetadata create(SamlMetadata metadata) {
        return getViprClient().getSamlSingleSignOn().createSamlMetadata(metadata);
    }

    public static void deleteSamlMetadata() {
        try {
            getViprClient().getSamlSingleSignOn().deleteSamlMetadata();
        } catch (ViPRHttpException viprException) {
            if (viprException.getHttpCode() != HttpStatus.SC_BAD_REQUEST) {
                throw viprException;
            }
        }
    }
}
