package com.emc.storageos.api.service.impl.placement;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;

import javax.xml.bind.JAXB;
import javax.xml.bind.JAXBContext;
import javax.xml.bind.Marshaller;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.soap.MessageFactory;
import javax.xml.soap.SOAPBody;
import javax.xml.soap.SOAPConstants;
import javax.xml.soap.SOAPMessage;
import javax.xml.transform.dom.DOMSource;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.w3c.dom.Document;

import com.emc.storageos.vasa.CreateVirtualVolume;

public class VVolPlacementManagerUtil {
    
    private static final Logger _log = LoggerFactory.getLogger(VVolPlacementManagerUtil.class);
    
    public static InputStream marshall(CreateVirtualVolume vvol){
        InputStream is = null;
        ByteArrayOutputStream outputStream = null;
        
        _log.info("@@@@@@@@@@@@@@@@@@@@@@@ Marshalling Create Virtual Volume @@@@@@@@@@@@@@@@@@@@@@");
        
        try {
            Document document = DocumentBuilderFactory.newInstance().newDocumentBuilder().newDocument();
            Marshaller marshaller = JAXBContext.newInstance(CreateVirtualVolume.class).createMarshaller();
            marshaller.marshal(vvol, document);
            SOAPMessage soapMessage = MessageFactory.newInstance().createMessage();
            soapMessage.getSOAPBody().addDocument(document);
            outputStream = new ByteArrayOutputStream();
            soapMessage.writeTo(outputStream);
            is = new ByteArrayInputStream(outputStream.toByteArray());
        }catch (Exception e) {
            e.printStackTrace();
        }finally{
            if(outputStream != null){
                try {
                    outputStream.close();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }
        return is;
    }
    
    public static CreateVirtualVolume unmarshall(InputStream is){
        
        _log.info("@@@@@@@@@@@@@@@@@@@@@@@ Unmarshalling Create Virtual Volume @@@@@@@@@@@@@@@@@@@@@@");
        CreateVirtualVolume createVirtualVol = null;
        try {
            
            SOAPMessage message = MessageFactory.newInstance(SOAPConstants.SOAP_1_2_PROTOCOL).createMessage(null,is);
            SOAPBody sb = message.getSOAPBody();
            DOMSource source = new DOMSource(sb.getFirstChild().getNextSibling());
            createVirtualVol = (CreateVirtualVolume)JAXB.unmarshal(source, CreateVirtualVolume.class);
            _log.info("####### Virtual Volume Create Request Information ###########");
            _log.info("ContainerId : " + createVirtualVol.getContainerId());
            _log.info("VVolType : " + createVirtualVol.getVvolType());
            _log.info("Size " + createVirtualVol.getSizeInMB());
           
            
        } catch(Exception e){
            e.printStackTrace();
        }finally{
            if(is != null){
                try {
                    is.close();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }
        return createVirtualVol;
    }

}
