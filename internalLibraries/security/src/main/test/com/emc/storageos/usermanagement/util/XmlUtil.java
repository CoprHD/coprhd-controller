package com.emc.storageos.usermanagement.util;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.xml.bind.JAXBContext;
import javax.xml.bind.JAXBException;
import javax.xml.bind.Marshaller;
import javax.xml.bind.Unmarshaller;
import java.io.IOException;
import java.io.InputStream;
import java.io.StringReader;
import java.io.StringWriter;


public class XmlUtil {

    private static Logger _log = LoggerFactory.getLogger(XmlUtil.class);

    private XmlUtil() {
        throw new AssertionError("Don't create any instance of class XmlUtils");
    }


    /**
     * General method to parse XML content
     * @param t
     *          The Class instance which will be marshaled
     * @return string of content
     */
    public synchronized static <T> String marshal(T t) {
        StringWriter stringWriter = new StringWriter();
        String str = "";
        try {
            JAXBContext context = JAXBContext.newInstance(t.getClass());
            Marshaller marshaller = context.createMarshaller();

            // Create a stringWriter to hold the XML
            marshaller.marshal(t, stringWriter);
            str = stringWriter.toString();
        } catch (JAXBException je) {
            _log.error("Unable to construct XML content.", je);
            je.printStackTrace();
        }

        return str;
    }

    /**
     * General method to parse XML content
     * @param content
     *          The string content which need to be parsed
     * @param tClass
     *          Class identifier of desired type
     * @return instance of desired type
     */
    public synchronized static <T> T unmarshal(String content, Class<T> tClass) {

        StringReader stringReader = new StringReader(content);
        try {
            JAXBContext context = JAXBContext.newInstance(tClass);
            Unmarshaller unMarshaller = context.createUnmarshaller();

            return tClass.cast(unMarshaller.unmarshal(stringReader));
        } catch (JAXBException je) {
            _log.error("Unable to parse XML content.", je);
            je.printStackTrace();
        }
        return null;
    }

    /**
     * General method to parse XML content
     * @param inputStream
     *          The content which need to be parsed
     * @param tClass
     *          Class identifier of desired type
     * @return instance of desired type
     */
    public synchronized static <T> T unmarshal(InputStream inputStream, Class<T> tClass) {
        try {
            JAXBContext jaxbContext = JAXBContext.newInstance(tClass);
            Unmarshaller jaxbUnmarshaller = jaxbContext.createUnmarshaller();
            return tClass.cast(jaxbUnmarshaller.unmarshal(inputStream));
        } catch (JAXBException je) {
            _log.error("Unable to parse XML content.", je);
            je.printStackTrace();
        } finally {
            try {
                if(inputStream != null) {
                    inputStream.close();
                }
            } catch (IOException ioe) {
                _log.error("Unable to close input stream "+ioe.getMessage(), ioe);
                ioe.printStackTrace();
            }
        }
        return null;
    }
}