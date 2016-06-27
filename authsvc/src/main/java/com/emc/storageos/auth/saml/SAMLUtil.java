package com.emc.storageos.auth.saml;


import org.joda.time.DateTime;
import org.opensaml.Configuration;
import org.opensaml.DefaultBootstrap;
import org.opensaml.common.impl.SecureRandomIdentifierGenerator;
import org.opensaml.saml2.core.Artifact;
import org.opensaml.saml2.core.ArtifactResolve;
import org.opensaml.saml2.core.Issuer;
import org.opensaml.saml2.core.Response;
import org.opensaml.ws.soap.client.BasicSOAPMessageContext;
import org.opensaml.ws.soap.client.http.HttpClientBuilder;
import org.opensaml.ws.soap.client.http.HttpSOAPClient;
import org.opensaml.ws.soap.soap11.Body;
import org.opensaml.ws.soap.soap11.Envelope;
import org.opensaml.xml.XMLObject;
import org.opensaml.xml.XMLObjectBuilderFactory;
import org.opensaml.xml.io.Unmarshaller;
import org.opensaml.xml.io.UnmarshallerFactory;
import org.opensaml.xml.parse.BasicParserPool;
import org.opensaml.xml.util.XMLHelper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.xml.namespace.QName;

public class SAMLUtil {

    static String arsUrl = "http://lglw9040.lss.emc.com:8080/openam/ArtifactResolver/metaAlias/idp";
    private static final Logger _log = LoggerFactory.getLogger(SAMLUtil.class);

    public static <T> T buildSAMLObjectWithDefaultName(Class<T> clazz) throws Exception {
        XMLObjectBuilderFactory builderFactory = Configuration.getBuilderFactory();

        QName defaultElementName = (QName)clazz.getDeclaredField("DEFAULT_ELEMENT_NAME").get(null);
        System.out.println(defaultElementName);
        T object = (T)builderFactory.getBuilder(defaultElementName).buildObject(defaultElementName);

        return object;
    }

    public static Envelope wrapInSOAPEnvelope(final XMLObject xmlObject) throws Exception {
        Envelope envelope = buildSAMLObjectWithDefaultName(Envelope.class);
        Body body = buildSAMLObjectWithDefaultName(Body.class);

        body.getUnknownXMLObjects().add(xmlObject);

        envelope.setBody(body);

        return envelope;
    }

    public static String getSecureRandomIdentifier() throws Exception {
        return new SecureRandomIdentifierGenerator().generateIdentifier();
    }


    private static Envelope sendArtifactResolve(final ArtifactResolve artifactResolve) throws Exception {
        Envelope envelope = SAMLUtil.wrapInSOAPEnvelope(artifactResolve);

        BasicSOAPMessageContext soapContext = new BasicSOAPMessageContext();
        soapContext.setOutboundMessage(envelope);
        HttpClientBuilder clientBuilder = new HttpClientBuilder();
        HttpSOAPClient soapClient = new HttpSOAPClient(clientBuilder.buildClient(), new BasicParserPool());

        String artifactResolutionServiceURL = arsUrl; //todo
    /*    for (ArtifactResolutionService ars : SAMLMetaData.getIdpEntityDescriptor().getIDPSSODescriptor(SAMLConstants.SAML20P_NS)
                .getArtifactResolutionServices()) {
            if (ars.getBinding().equals(SAMLConstants.SAML2_SOAP11_BINDING_URI)) {
                artifactResolutionServiceURL = ars.getLocation();
            }
        }
        */

        soapClient.send(artifactResolutionServiceURL, soapContext);

        return (Envelope)soapContext.getInboundMessage();
    }

    private static ArtifactResolve generateArtifactResolve(final String artifactString) throws Exception {
        ArtifactResolve artifactResolve = SAMLUtil.buildSAMLObjectWithDefaultName(ArtifactResolve.class);

        Issuer issuer = SAMLUtil.buildSAMLObjectWithDefaultName(Issuer.class);
        issuer.setValue("http://lglw1102.lss.emc.com"); // todo configurable
        //     issuer.setValue("http://lglw9040.lss.emc.com:8080/openam"); // todo configurable
        artifactResolve.setIssuer(issuer);
        artifactResolve.setIssueInstant(new DateTime());

        String artifactResolveId = SAMLUtil.getSecureRandomIdentifier();
        artifactResolve.setID(artifactResolveId);

        /*
        for (ArtifactResolutionService sss : metaData.getIdpEntityDescriptor().getIDPSSODescriptor(SAMLConstants.SAML20P_NS).getArtifactResolutionServices()) {
            if (sss.getBinding().equals(SAMLConstants.SAML2_SOAP11_BINDING_URI)) {
                artifactResolve.setDestination(sss.getLocation());
            }
        }
        */

        artifactResolve.setDestination(arsUrl); // todo

        Artifact artifact = SAMLUtil.buildSAMLObjectWithDefaultName(Artifact.class);
        artifact.setArtifact(artifactString);
        artifactResolve.setArtifact(artifact);

        return artifactResolve;
    }

    public static String resolveArtifact(String artifact) {

        try {
            DefaultBootstrap.bootstrap();

            Envelope rsp = sendArtifactResolve(generateArtifactResolve(artifact));
            _log.info("result is ");
            _log.info(XMLHelper.prettyPrintXML(rsp.getDOM()));

            UnmarshallerFactory unmarshallerFactory = Configuration.getUnmarshallerFactory();
            Unmarshaller unmarshaller = unmarshallerFactory.getUnmarshaller(rsp.getBody().getDOM());
            XMLObject obj = (XMLObject) unmarshaller.unmarshall(rsp.getBody().getDOM());
            //Response artRsp = (Response) obj.getOrderedChildren().get(0);
            //artRsp.
            Response artRsp = (Response) obj.getOrderedChildren().get(0).getOrderedChildren().get(2);
            String nameId = artRsp.getAssertions().get(0).getSubject().getNameID().getValue();
            _log.info("name id extracted from artifact resolve response: " + nameId);
            return nameId;
        } catch (Exception ex) {
            _log.error("exception resolve artifact: ", ex);
        }
        return "";
    }
}
