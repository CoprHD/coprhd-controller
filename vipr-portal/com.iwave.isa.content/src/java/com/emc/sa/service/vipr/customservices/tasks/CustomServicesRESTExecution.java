package com.emc.sa.service.vipr.customservices.tasks;

import com.emc.sa.service.vipr.customservices.CustomServicesConstants;
import com.emc.sa.service.vipr.tasks.ViPRExecutionTask;
import com.emc.storageos.coordinator.client.service.CoordinatorClient;
import com.emc.storageos.model.customservices.CustomServicesWorkflowDocument;
import com.emc.storageos.primitives.Primitive;
import com.emc.storageos.security.ssl.ViPRX509TrustManager;
import com.emc.storageos.svcs.errorhandling.resources.InternalServerErrorException;
import com.sun.jersey.api.client.config.ClientConfig;
import com.sun.jersey.api.client.config.DefaultClientConfig;
import com.sun.jersey.api.client.filter.HTTPBasicAuthFilter;

import com.sun.jersey.api.client.Client;
import com.sun.jersey.api.client.*;
import com.sun.jersey.client.urlconnection.HTTPSProperties;
import org.apache.commons.io.IOUtils;
import org.apache.commons.lang.StringUtils;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.util.UriTemplate;

import javax.ws.rs.core.MediaType;
import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManager;
import java.util.List;
import java.util.HashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;


public class CustomServicesRESTExecution extends ViPRExecutionTask<CustomServicesTaskResult> {

    @Autowired
    private final CoordinatorClient coordinator;
    private final Map<String, List<String>> input;
    final CustomServicesWorkflowDocument.Step step;
    private final BuildRestRequest buildrest;
    private static final org.slf4j.Logger logger = LoggerFactory.getLogger(CustomServicesRESTExecution.class);

    public  CustomServicesRESTExecution(CoordinatorClient coordinator, Map<String, List<String>> input,
                                        CustomServicesWorkflowDocument.Step step) {
        this.coordinator = coordinator;
        this.input = input;
        this.step = step;
        buildrest = new BuildRestRequest(new DefaultClientConfig(), coordinator);
    }

    @Override
    public CustomServicesTaskResult executeTask() throws Exception {

        final Primitive.StepType type = Primitive.StepType.fromString(step.getType());

        switch (type) {
            case REST_LOGIN:
                return login();

            case REST:
                return buildRequest();

            default:

        }

        return null;
    }

    private static String getOptions(final String key, final Map<String, List<String>> input) {
        if (input.get(key) != null) {
            logger.info("found key:{}", key);
            return StringUtils.strip(input.get(key).get(0).toString(), "\"");
        }

        System.out.println("key not defined. key:{}"+ key);
        return null;
    }

    /**
     * Example uri: "/block/volumes/{id}/findname/{name}";
     * @param templatePath
     * @return
     */
    private String makePath(String templatePath) {
//	templatePath = "projects/{id}";
	logger.info("path is:{}", templatePath);
        final UriTemplate template = new UriTemplate(templatePath);
        final List<String> pathParameters = template.getVariableNames();
        final Map<String, Object> pathParameterMap = new HashMap<String, Object>();

        for(final String key : pathParameters) {
            List<String> value = input.get(key);
		logger.info("path key:{} value:{}", key, value);
            if(null == value) {
		logger.info("value is null");
                throw InternalServerErrorException.internalServerErrors.customServiceExecutionFailed("Unfulfilled path parameter: " + key);
            }
            //TODO find a better fix
            pathParameterMap.put(key, value.get(0).replace("\"",""));
        }

        final String path = template.expand(pathParameterMap).getPath();

        logger.info("URI string is: {}", path);

        return path;
    }

    /**
     * POST body format:
     *  "{\n" +
     "  \"consistency_group\": \"$consistency_group\",\n" +
     "  \"count\": \"$count\",\n" +
     "  \"name\": \"$name\",\n" +
     "  \"project\": \"$project\",\n" +
     "  \"size\": \"$size\",\n" +
     "  \"varray\": \"$varray\",\n" +
     "  \"vpool\": \"$vpool\"\n" +
     "}";
     * @param body
     * @return
     */
    private String makePostBody(String body) {
      /*body = "{\n" +
                "  \"consistency_group\": $consistency_group,\n" +
                "  \"count\": $count,\n" +
                "  \"name\": $name,\n" +
                "  \"project\": $project,\n" +
                "  \"size\": $size,\n" +
                "  \"varray\": $varray,\n" +
                "  \"vpool\": $vpool\n" +
                "}";*/
        body = "{\n" +
                "  \"name\": $name,\n" +
                "  \"owner\": $owner\n" +
                "}\n";
	logger.info("Body is:{}", body);
        Matcher m = Pattern.compile("\\$(\\w+)").matcher(body);

        while (m.find()) {
            String pat = m.group(1);
            String newpat = "$" + pat;
            if (input.get(pat) == null || input.get(pat).get(0) == null) {
                logger.info("input.get(pat) is null for pat:{}", pat);
                body = body.replace(newpat, "\"" +" "+"\"");
            } else {
		logger.info("pat:{} new pat:{}",input.get(pat).get(0), newpat);
                body = body.replace(newpat, "\"" +input.get(pat).get(0).replace("\"","")+"\"");
            }
        }
	logger.info("New body:{}", body);
        return body;
    }

    private CustomServicesTaskResult buildRequest() throws Exception {
        logger.info("url is:{}", getUrl());
        BuildRestRequest b = buildrest.setSSL(getOptions("protocol", input)).setUrl(getUrl());

        b.setAccept("json").setContentType("json").setHeaders(step, input);
        final String body;
        if (getOptions("method", input).equals("POST") || getOptions("method", input).equals("PUT")) {
            logger.info("building post body");
            body = makePostBody(getOptions("body", input));
        } else {
            body = null;
        }
        return b.executeRest(CustomServicesConstants.restMethods.valueOf(getOptions("method", input)), body);

/*        BuildRestRequest b = buildrest.setSSL(getOptions("protocol", input)).setUrl(getUrl());

        b.setAccept("json").setContentType("json").setHeaders(step, input);
        return b.executeRest(CustomServicesConstants.restMethods.valueOf(getOptions("method", input)));

        BuildRestRequest b = buildrest.setSSL(getOptions("protocol", input)).setUrl(getUrl());

        final List<CustomServicesWorkflowDocument.Input> inputs = step.getInputGroups().get("headers").getInputGroup();
        for (final CustomServicesWorkflowDocument.Input header : inputs) {
            final String name = header.getName();
            final String value = input.get(name).get(0);

            logger.info("header name:{} value:{}", name, value);
            if (value.isEmpty()) {
                //Throw exception
            }
            b.getResource().header(name, value);

        }
b.getResource().header("X-SDS-AUTH-TOKEN", "BAAcY0FnMUR3a1dpakxZejhRaEowZnB0UCsrYndnPQMAVAQADTE0ODg5ODYxMTE4NDcCAAEABQA9dXJuOnN0b3JhZ2VvczpUb2tlbjo4MTgwMGUwYS1hMGY1LTQ3MGMtODI5ZC0zNjBjNGQxY2QzZmQ6dmRjMQIAAtAP");

        return executeRest(b.getResource());*/
   /*     logger.info("url is:{}", getUrl());
        BuildRestRequest b = buildrest.setSSL(getOptions("protocol", input)).setUrl(getUrl()).setHeaders(step, input)
                .setAccept(null).setContentType(null);

        return executeRest(b.getResource());*/
    }

    private CustomServicesTaskResult login() throws Exception {

        BuildRestRequest b = buildrest.setSSL(getOptions("protocol", input)).setUrl(getUrl()).
                setFilter(getOptions("user", input), getOptions("password", input));
        return b.executeRest(CustomServicesConstants.restMethods.valueOf(getOptions("method", input)), null);


     /*   Client c;
        if (getOptions("protocol", input).equals("https")) {
            logger.info("Setting up SSL");
            c = getSslClient(coordinator);
        } else {
            logger.info("We do not support Https:{}", getOptions("protocol", input));
            return null;
        }

        c = getSslClient(coordinator);
        logger.info("get the url");
        String url = getUrl();
        logger.info("Url is:{}", url);

        WebResource resource = c.resource(url);
        logger.info("Set the basic auth filter");
        c.addFilter(new HTTPBasicAuthFilter(getOptions("user", input), getOptions("password", input)));
        logger.info("Send the REST Request");
        ClientResponse res = executeRest(resource);

        logger.info("Headers:{}", res.getHeaders());
        String output =  IOUtils.toString(res.getEntityInputStream(), "UTF-8");
        logger.info("out/err:{}",  output);

        return new CustomServicesRestTaskResult(res.getHeaders().entrySet(), output, output, res.getStatus());*/
    }

/*
    private CustomServicesRestTaskResult executeRest(final WebResource.Builder resource) throws Exception {

        CustomServicesConstants.restMethods method = CustomServicesConstants.restMethods.valueOf(getOptions("method", input));
        ClientResponse response = null;
        switch (method) {
            case GET:
                response = resource.get(ClientResponse.class);
            case PUT:
                //resource.put()
                break;
            case POST:
                response = resource.post(ClientResponse.class, getOptions("body", input));
                break;
            case DELETE:
                break;
            default:
                logger.error("Rest method type not supported");
        }
        String output =  IOUtils.toString(response.getEntityInputStream(), "UTF-8");

        logger.info("result is:{} headers:{}", output, response.getHeaders());
        return new CustomServicesRestTaskResult(response.getHeaders().entrySet(), output, output, response.getStatus());
    }
*/

	public String getUrl1() {
		String tmp = String.format("%s://%s:%s/%s",getOptions("protocol", input),getOptions("ipaddress", input),
                getOptions("port", input), getOptions("path", input));
		logger.info("tmp path is:{}", tmp);
		        return String.format("%s://%s:%s/%s",getOptions("protocol", input),getOptions("ipaddress", input),
                getOptions("port", input), getOptions("path", input));
    }

    public String getUrl() {
	String mypath = String.format("%s://%s:%s/%s",getOptions("protocol", input),getOptions("ipaddress", input),
                getOptions("port", input), makePath(getOptions("path", input)));
	logger.info("mypath:{}", mypath);
        return String.format("%s://%s:%s/%s",getOptions("protocol", input),getOptions("ipaddress", input),
                getOptions("port", input), makePath(getOptions("path", input)));
    }

    public static Client getSslClient(CoordinatorClient coordinator) throws Exception {
        SSLContext context = SSLContext.getInstance("SSL");
        ViPRX509TrustManager trustManager = new ViPRX509TrustManager(coordinator);

        context.init(null, new TrustManager[]{trustManager}, null);

        final ClientConfig config = new DefaultClientConfig();
        config.getProperties().put(HTTPSProperties.PROPERTY_HTTPS_PROPERTIES, new HTTPSProperties(null, context));

        final Client c = Client.create(config);

        return c;
    }

    public static void getmyClient(CoordinatorClient coordinator) throws Exception {
        SSLContext context = SSLContext.getInstance("SSL");
        ViPRX509TrustManager trustManager = new ViPRX509TrustManager(coordinator);

        context.init(null,new TrustManager[]{trustManager}, null);

        final ClientConfig config = new DefaultClientConfig();
        final Client c = Client.create(config);
        WebResource resource = c.resource("url");
        boolean basicAuth = true;
        if (basicAuth) {
            c.addFilter(new HTTPBasicAuthFilter("user", "password"));
        } else {
            //set headers.
            resource.header("XDX-AUTH-TOKEN", "XXXCGSAUIKLLOOJGFFYUK");
        }

        //add other headers
        //setHeaders();
        config.getProperties().put(HTTPSProperties.PROPERTY_HTTPS_PROPERTIES, new HTTPSProperties(null, context));
        ClientResponse res = resource.get(ClientResponse.class);
        res.getEntity(String.class);

    }

}
