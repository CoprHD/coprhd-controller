/*
 * Copyright (c) 2016 EMC Corporation
 * All Rights Reserved
 */

package controllers.auth;

import com.emc.storageos.model.auth.SamlMetadata;
import com.emc.storageos.model.auth.SamlMetadataResponse;
import com.emc.storageos.model.auth.SamlResponseAttribute;
import com.emc.storageos.svcs.errorhandling.resources.APIException;
import controllers.Common;
import controllers.deadbolt.Restrict;
import controllers.deadbolt.Restrictions;
import controllers.util.ViprResourceController;
import play.mvc.With;
import util.MessagesUtils;
import util.SamlSSOUtils;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

@With(Common.class)
@Restrictions({ @Restrict("SECURITY_ADMIN")})
public class SamlSingleSignOn extends ViprResourceController {

    protected static final String SAVED = "saml.metadata.saved";
    protected static final String DELETED = "saml.metadata.deleted";

    public static void metadata() throws IOException {
        renderArgs.put("viewMetadata", viewMetadata());
        SamlMetadataForm metadata = getMetadata();
        render(metadata);
    }

    public static void save(SamlMetadataForm metadata) throws IOException {
        metadata.createSamlMetadata();

        flash.success(MessagesUtils.get(SAVED, metadata.entityID));
        metadata();
    }

    public static String viewMetadata() throws IOException {
        return SamlSSOUtils.getSamlMetadataXML();
    }

    public static SamlMetadataForm getMetadata() {
        SamlMetadata metadata = SamlSSOUtils.getSamlMetadataObject();
        if ( metadata != null) {
            return new SamlMetadataForm(metadata);
        }

        return new SamlMetadataForm();
    }

    public static void deleteMetadata() {
        SamlSSOUtils.deleteSamlMetadata();
        flash.success(MessagesUtils.get(DELETED));
    }

    public static class SamlMetadataForm {
        public SamlMetadataForm() {

        }

        public SamlMetadataForm(SamlMetadata metadata) {
            readFrom(metadata);
        }

        //Base saml service provider metadata.
        public String entityID;
        public String entityBaseURL;
        public Boolean requestSigned;
        public Boolean assertionSigned;
        public List<SamlResponseAttribute> samlResponseAttributes;

        //Extended saml service provider metadata.
        public String entityAlias;
        public Boolean logoutRequestSigned;
        public Boolean logoutResponseSigned;
        public Boolean artifactResolveSigned;
        public String hostNameVerification;
        public Boolean signMetadata;
        public String signingAlgorithm;

        public void createSamlMetadata() {
            SamlMetadata metadata = new SamlMetadata();
            writeTo(metadata);

            SamlSSOUtils.create(metadata);
        }

        private void readFrom(SamlMetadata metadata) {
            //Saml service provider base metadata.
            this.entityID = metadata.getBaseMetadata().getEntityID();
            this.entityBaseURL = metadata.getBaseMetadata().getEntityBaseURL();
            this.requestSigned = metadata.getBaseMetadata().isRequestSigned();
            this.assertionSigned = metadata.getBaseMetadata().isAssertionSigned();

            //Saml service provider extended metadata.
            this.entityAlias = metadata.getExtendedMetadata().getEntityAlias();
            this.logoutRequestSigned = metadata.getExtendedMetadata().isLogoutRequestSigned();
            this.logoutResponseSigned = metadata.getExtendedMetadata().isLogoutResponseSigned();
            this.artifactResolveSigned = metadata.getExtendedMetadata().isArtifactResolveSigned();
            this.hostNameVerification = metadata.getExtendedMetadata().getHostNameVerification();
            this.signMetadata = metadata.getExtendedMetadata().isMetadataSigned();
            this.signingAlgorithm = metadata.getExtendedMetadata().getSigningAlgorithm();
        }

        private void writeTo(SamlMetadata metadata) {
            //Saml service provider base metadata.
            metadata.getBaseMetadata().setEntityID(this.entityID);
            metadata.getBaseMetadata().setEntityBaseURL(this.entityBaseURL);
            metadata.getBaseMetadata().setRequestSigned(this.requestSigned);
            metadata.getBaseMetadata().setAssertionSigned(this.assertionSigned);

            samlResponseAttributes = new ArrayList<SamlResponseAttribute>();
            SamlResponseAttribute samlAttribute = new SamlResponseAttribute();
            samlAttribute.setAttributeName("email");
            samlAttribute.setAttributeName("string");
            samlResponseAttributes.add(samlAttribute);
            metadata.getBaseMetadata().setSamlResponseAttributes(samlResponseAttributes);

            //Saml service provider extended metadata.
            metadata.getExtendedMetadata().setEntityAlias(this.entityAlias);
            metadata.getExtendedMetadata().setLogoutRequestSigned(this.logoutRequestSigned);
            metadata.getExtendedMetadata().setLogoutResponseSigned(this.logoutResponseSigned);
            metadata.getExtendedMetadata().setArtifactResolveSigned(this.artifactResolveSigned);
            metadata.getExtendedMetadata().setHostNameVerification(this.hostNameVerification);
            metadata.getExtendedMetadata().setMetadataSigned(this.signMetadata);
            metadata.getExtendedMetadata().setSigningAlgorithm(this.signingAlgorithm);
        }
    }
}
