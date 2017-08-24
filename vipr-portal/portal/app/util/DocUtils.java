/*
 * Copyright (c) 2015 EMC Corporation
 * All Rights Reserved
 */
package util;

import play.Play;
import play.libs.IO;
import java.util.Properties;
import play.mvc.Http;
import play.vfs.VirtualFile;
import play.Logger;

import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.DocumentBuilder;
import org.w3c.dom.Document;
import org.w3c.dom.NodeList;
import org.w3c.dom.Node;
import org.w3c.dom.Element;

/**
 * Utility to map the controller to the corresponding documentation link. This is driven by a file
 * 'conf/documentation.topics'.
 * 
 * @author Chris Dail
 */
public class DocUtils {
    // Path to documentation
    public static final String documentationBaseUrl = "/public/docs/en_US/index.html#";
    
    // GUID of documentation document. This should not change
    public static final String guid = "GUID-59FAE703-DF72-4FF8-81D2-4DE332A9C927";

    private static Properties docTopics = null;

    public static String getDocumentationLink() {
        return linkForTopic(getDocumentationTopic());
    }

    public static String getCatalogDocumentationLink(String name) {
        return linkForTopic(name);
    }

    private static String linkForTopic(String topic) {
        if (topic == null) {
            return documentationBaseUrl;
        }
        
        // Added for Context Sensitive Help(CSH) for HTML5 using the map.xml file
        String topicHtmlFilename = getHtmlHelpFile(topic);
        
        if (topicHtmlFilename != null && !topicHtmlFilename.isEmpty()) 
        	return String.format("%s%s", documentationBaseUrl, topicHtmlFilename);
        else {
	    	String unknownDocumentationBaseUrl = documentationBaseUrl.substring(0, documentationBaseUrl.length()-1);
	    	return String.format("%s?topic=%s", unknownDocumentationBaseUrl, topic);
        }
    }

    private static String getDocumentationTopic() {
        if (docTopics == null) {
            VirtualFile file = Play.getVirtualFile("conf/documentation.topics");
            synchronized (DocUtils.class) {
                if (docTopics == null) {
                    docTopics = IO.readUtf8Properties(file.inputstream());
                }
            }
        }

        Http.Request request = Http.Request.current();
        if (request != null && request.action != null) {
            // First look for a topic with 'controller.method'
            String topic = docTopics.getProperty(request.action);
            // Next look for just 'controller'
            if (topic == null && request.controller != null) {
                topic = docTopics.getProperty(request.controller);
            }
            return topic;
        }
        return null;
    }
    
    /**
     * Method returns the relative HTML file for the Help Topic
     * @param topic
     * @return topicHtmlFilename
     */
	private static String getHtmlHelpFile(String topic) {
		String topicHtmlFilename = null;
				
		try {
		    VirtualFile helpMapXmlFile = Play.getVirtualFile("public/docs/en_US/map.xml");
		    DocumentBuilderFactory dbFactory = DocumentBuilderFactory.newInstance();
		    DocumentBuilder dBuilder = dbFactory.newDocumentBuilder();
		    Document doc = dBuilder.parse(helpMapXmlFile.inputstream());
		    doc.getDocumentElement().normalize();
		
		    NodeList nList = doc.getElementsByTagName("mapID");
		
		    for (int temp = 0; temp < nList.getLength(); temp++) {
		        
		    	Node nNode = nList.item(temp);
		        
		    	if (nNode.getNodeType() == Node.ELEMENT_NODE) {
		           
		    	    Element eElement = (Element) nNode;
		            String topicValue = eElement.getAttribute("target");

		            if (topicValue.equals(topic)) {
		            	topicHtmlFilename = eElement.getAttribute("href");
		            	break;
		            }
		        }
		    }
		    
	    } catch (Exception e) {
		    Logger.error(e, e.getMessage());
	    }
		
	    return topicHtmlFilename;
	}

}
