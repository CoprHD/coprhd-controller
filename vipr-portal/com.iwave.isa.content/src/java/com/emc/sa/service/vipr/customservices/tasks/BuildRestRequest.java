package com.emc.sa.service.vipr.customservices.tasks;


import com.emc.storageos.model.customservices.CustomServicesWorkflowDocument;
import com.emc.storageos.coordinator.client.service.CoordinatorClient;
import com.emc.storageos.security.ssl.ViPRX509TrustManager;
import com.emc.storageos.svcs.errorhandling.resources.InternalServerErrorException;
import com.sun.jersey.api.client.Client;
import com.sun.jersey.api.client.*;
import com.sun.jersey.api.client.config.ClientConfig;
import com.sun.jersey.api.client.config.DefaultClientConfig;
import com.sun.jersey.api.client.filter.HTTPBasicAuthFilter;
import com.sun.jersey.client.urlconnection.HTTPSProperties;
import io.netty.util.internal.StringUtil;
import org.apache.http.impl.client.HttpClientBuilder;
import org.slf4j.LoggerFactory;

import org.apache.commons.io.IOUtils;
import com.emc.sa.service.vipr.customservices.CustomServicesConstants;
import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManager;
import javax.ws.rs.core.MediaType;
import java.util.List;
import java.util.Map;

public class BuildRestRequest {

    private Client client;
    WebResource.Builder builder = null;
    WebResource resource;
    final private ClientConfig config;
    private final CoordinatorClient coordinator;
    private String body;
    private static final org.slf4j.Logger logger = LoggerFactory.getLogger(BuildRestRequest.class);

    public BuildRestRequest(ClientConfig config, CoordinatorClient coordinator) {
        this.config = config;
        this.coordinator = coordinator;
    }

    public WebResource.Builder getResource() {
        return resource.getRequestBuilder();
    }

    public BuildRestRequest setSSL(final String protocol) throws Exception {

        if (!protocol.equals("https")) {
            logger.error("Only Https is supported. Protocol:{} is not supported", protocol);

            InternalServerErrorException.internalServerErrors.
                    customServiceExecutionFailed("Protocol not supported" + protocol);
        }

        final SSLContext context = SSLContext.getInstance("SSL");
        final ViPRX509TrustManager trustManager = new ViPRX509TrustManager(coordinator);

        context.init(null, new TrustManager[]{trustManager}, null);
        config.getProperties().put(HTTPSProperties.PROPERTY_HTTPS_PROPERTIES, new HTTPSProperties(null, context));

        client = Client.create(config);

        return this;
    }
    public WebResource.Builder getBuilder() {
        if (builder!= null) {
            return builder;
        }
        builder = resource.getRequestBuilder();
        return builder;
    }

   public BuildRestRequest setHeaders(final CustomServicesWorkflowDocument.Step step, final Map<String, List<String>> input){
        if (step.getInputGroups() == null || step.getInputGroups().get("headers") == null) {
            return  this;
        }
        final List<CustomServicesWorkflowDocument.Input> inputs = step.getInputGroups().get("headers").getInputGroup();
        if (inputs == null) {
            return this;
        }
        for (final CustomServicesWorkflowDocument.Input header : inputs) {
            final String name = header.getName();
            final String value = input.get(name).get(0);

            logger.info("header name:{} value:{}", name, value);
            if (value.isEmpty()) {
                //Throw exception
            }
		getBuilder().header(name, value);
            /*if (builder != null) {
                logger.info("builder header");
                builder.header(name, value);
            } else {
                logger.info("resource header");
                this.builder = resource.header(name, value);
            }*/
        }

        return this;
    }

    public BuildRestRequest setContentType(String type) {
	getBuilder().type(MediaType.APPLICATION_JSON_TYPE);
        /*if (builder != null) {
            logger.info("accept builder");
            builder.type(MediaType.APPLICATION_JSON_TYPE);
        } else {
            logger.info("accept resource");
            this.builder = resource.type(MediaType.APPLICATION_JSON_TYPE);
        }*/


        return this;
    }

    public BuildRestRequest setAccept(final String mediaType) {
	getBuilder().accept(MediaType.APPLICATION_JSON_TYPE);
        /*if (builder != null) {
            logger.info("accept builder");
            builder.accept(MediaType.APPLICATION_JSON_TYPE);
        } else {
            logger.info("accept resource");
            this.builder = resource.accept(MediaType.APPLICATION_JSON_TYPE);
        }*/

        return this;
    }

    public CustomServicesRestTaskResult executeRest(CustomServicesConstants.restMethods method, final String input) throws Exception {

	logger.info("executing rest");
        ClientResponse response = null;
        switch (method) {
            case GET:
		logger.info("executing rest get");
		response = getBuilder().get(ClientResponse.class);
		logger.info("done get exec1");
                /*if (builder!=null) {
                    response = builder.get(ClientResponse.class);
                } else {
                    response = resource.get(ClientResponse.class);
                }*/
		break;
            case PUT:
                response = getBuilder().put(ClientResponse.class, input);
                break;
            case POST:
		response = getBuilder().post(ClientResponse.class, input);
               // response = resource.post(ClientResponse.class, getOptions("body", input));
                break;
            case DELETE:
		logger.info("calling delete");
		try {
                response = getBuilder().delete(ClientResponse.class);
		} catch(Exception e) {
			logger.info("got exception :{}", e);
		}
                break;
            default:
                logger.error("Rest method type not supported");
        }
        String output =  IOUtils.toString(response.getEntityInputStream(), "UTF-8");

        logger.info("result is:{} headers:{}", output, response.getHeaders());
        return new CustomServicesRestTaskResult(response.getHeaders().entrySet(), output, output, response.getStatus());
    }

    public BuildRestRequest setQueryparam(final Map<String, String> queries) {
        for (final Map.Entry<String, String> entry : queries.entrySet()) {
            resource.queryParam(entry.getKey(), entry.getValue());
        }

        return this;
    }

    public BuildRestRequest setUrl(final String url) {
        if (!url.isEmpty()) {
            this.resource = client.resource(url);
        }

        return this;
    }

    public BuildRestRequest setFilter(final String user, final String password) {

        if (!(user.isEmpty() && password.isEmpty())) {
            client.addFilter(new HTTPBasicAuthFilter(user, password));
        }

        return this;
    }
}

