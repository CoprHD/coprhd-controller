/*
 * Copyright 2015 EMC Corporation
 * All Rights Reserved
 */
package util;

import play.Play;
import play.libs.IO;
import java.util.Properties;
import play.mvc.Http;
import play.vfs.VirtualFile;

/**
 * Utility to map the controller to the corresponding documentation link. This is driven by a file
 * 'conf/documentation.topics'.
 *
 * @author Chris Dail
 */
public class DocUtils {
    // Path to documentation
    public static final String documentationBaseUrl = "/public/docs/en_US/index.html";
    // GUID of documentation document. This should not change
    public static final String guid = "GUID-59FAE703-DF72-4FF8-81D2-4DE332A9C927";

    private static Properties docTopics;

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
        return String.format("%s?topic=%s&context=%s", documentationBaseUrl, topic, guid);
    }

    private static String getDocumentationTopic() {
        if (docTopics == null) {
            VirtualFile file = Play.getVirtualFile("conf/documentation.topics");
            docTopics = IO.readUtf8Properties(file.inputstream());
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
}
