package com.emc.storageos.auth.saml;


import org.joda.time.DateTime;
import org.opensaml.Configuration;
import org.opensaml.DefaultBootstrap;
import org.opensaml.common.SAMLVersion;
import org.opensaml.common.impl.SecureRandomIdentifierGenerator;
import org.opensaml.saml2.core.*;
import org.opensaml.saml2.core.impl.*;
import org.opensaml.ws.soap.client.BasicSOAPMessageContext;
import org.opensaml.ws.soap.client.http.HttpClientBuilder;
import org.opensaml.ws.soap.client.http.HttpSOAPClient;
import org.opensaml.ws.soap.soap11.Body;
import org.opensaml.ws.soap.soap11.Envelope;
import org.opensaml.xml.XMLObject;
import org.opensaml.xml.XMLObjectBuilderFactory;
import org.opensaml.xml.io.Marshaller;
import org.opensaml.xml.io.MarshallingException;
import org.opensaml.xml.io.Unmarshaller;
import org.opensaml.xml.io.UnmarshallerFactory;
import org.opensaml.xml.parse.BasicParserPool;
import org.opensaml.xml.util.Base64;
import org.opensaml.xml.util.XMLHelper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.xml.namespace.QName;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.StringWriter;
import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;
import java.util.Random;
import java.util.zip.Deflater;
import java.util.zip.DeflaterOutputStream;

public class SAMLUtil {

    //private static String IDP = "lglw9040.lss.emc.com";
    private static String IDP = "lglou242.lss.emc.com";

    static String arsUrl = "http://" + IDP + ":8080/openam/ArtifactResolver/metaAlias/idp";
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


    public static String generateSAMLRequest() {
        try {
            DefaultBootstrap.bootstrap();

            //Generate ID
            String randId = createRandomString(42);

            // create Issuer
            IssuerBuilder issuerBuilder = new IssuerBuilder();
            Issuer issuer = issuerBuilder.buildObject("urn:oasis:names:tc:SAML:2.0:assertion", "Issuer", "saml" );
            issuer.setValue("http://lglw1102.lss.emc.com");

            //Create NameIDPolicy
            NameIDPolicyBuilder nameIdPolicyBuilder = new NameIDPolicyBuilder();
            NameIDPolicy nameIdPolicy = nameIdPolicyBuilder.buildObject();
            //nameIdPolicy.setFormat("urn:oasis:names:tc:SAML:1.1:nameid-format:emailAddress");
            //nameIdPolicy.setFormat("urn:oasis:names:tc:SAML:1.1:nameid-format:unspecified");
            nameIdPolicy.setFormat("urn:oasis:names:tc:SAML:2.0:nameid-format:transient");
            //nameIdPolicy.setFormat("urn:oasis:names:tc:SAML:2.0:nameid-format:persistent");
            nameIdPolicy.setSPNameQualifier("http://lglw1102.lss.emc.com");
            nameIdPolicy.setAllowCreate(true);

            //Create AuthnContextClassRef
            AuthnContextClassRefBuilder authnContextClassRefBuilder = new AuthnContextClassRefBuilder();
            AuthnContextClassRef authnContextClassRef =
                    authnContextClassRefBuilder.buildObject("urn:oasis:names:tc:SAML:2.0:assertion", "AuthnContextClassRef", "saml");
            authnContextClassRef.setAuthnContextClassRef("urn:oasis:names:tc:SAML:2.0:ac:classes:PasswordProtectedTransport");
            Marshaller accrMarshaller = org.opensaml.Configuration.getMarshallerFactory().getMarshaller(authnContextClassRef);
            org.w3c.dom.Element authnContextClassRefDom = accrMarshaller.marshall(authnContextClassRef);

            //Create RequestedAuthnContext
            RequestedAuthnContextBuilder requestedAuthnContextBuilder = new RequestedAuthnContextBuilder();
            RequestedAuthnContext requestedAuthnContext =
                    requestedAuthnContextBuilder.buildObject();
            requestedAuthnContext.setComparison(AuthnContextComparisonTypeEnumeration.EXACT);
            requestedAuthnContext.getAuthnContextClassRefs().add(authnContextClassRef);
            requestedAuthnContext.setDOM(authnContextClassRefDom);
            authnContextClassRef.setParent((XMLObject) requestedAuthnContext);


            // construct AuthnRequest
            DateTime issueInstant = new DateTime();
            AuthnRequestBuilder authRequestBuilder = new AuthnRequestBuilder();
            AuthnRequest authRequest = authRequestBuilder.buildObject("urn:oasis:names:tc:SAML:2.0:protocol", "AuthnRequest", "samlp");
            authRequest.setForceAuthn(false);
            authRequest.setIsPassive(false);
            authRequest.setIssueInstant(issueInstant);
            authRequest.setProtocolBinding("urn:oasis:names:tc:SAML:2.0:bindings:HTTP-Artifact");
            authRequest.setAssertionConsumerServiceURL("https://lglw1102.lss.emc.com:4443/samllogin");
            authRequest.setIssuer(issuer);
            authRequest.setNameIDPolicy(nameIdPolicy);
            authRequest.setRequestedAuthnContext(requestedAuthnContext);
            authRequest.setID(randId);
            authRequest.setVersion(SAMLVersion.VERSION_20);

            // Now we must build our representation to put into the html form to be submitted to the idp
            Marshaller marshaller = org.opensaml.Configuration.getMarshallerFactory().getMarshaller(authRequest);
            org.w3c.dom.Element authDOM = marshaller.marshall(authRequest);
            StringWriter rspWrt = new StringWriter();
            XMLHelper.writeNode(authDOM, rspWrt);
            String messageXML = rspWrt.toString();
            System.out.println("SAML Request: " + messageXML);

            Deflater deflater = new Deflater(Deflater.DEFLATED, true);
            ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();
            DeflaterOutputStream deflaterOutputStream = new DeflaterOutputStream(byteArrayOutputStream, deflater);
            deflaterOutputStream.write(messageXML.getBytes());
            deflaterOutputStream.close();
            String samlResponse = Base64.encodeBytes(byteArrayOutputStream.toByteArray(), Base64.DONT_BREAK_LINES);
            samlResponse = URLEncoder.encode(samlResponse);

            String actionURL = "http://" + IDP + ":8080/openam/SSORedirect/metaAlias/idp";
            String url = actionURL + "?SAMLRequest=" + samlResponse;
            System.out.println(url);
            return url;

        } catch (MarshallingException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        } catch (UnsupportedEncodingException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        } catch (IOException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        }catch (Exception ex) {
            ex.printStackTrace();
        } finally{
            //Nothing yet
        }
        return "";

    }


    public static String createRandomString(int length) {
        Random random = new Random();
        StringBuilder sb = new StringBuilder();
        while (sb.length() < length) {
            sb.append(Integer.toHexString(random.nextInt()));
        }
        return sb.toString();
    }

    public static void main(String[] args) throws Exception {
        generateSAMLRequest();
    }
}
