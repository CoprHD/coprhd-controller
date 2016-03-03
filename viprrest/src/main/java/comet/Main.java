package comet;

//import org.glassfish.grizzly.http.server.HttpServer;
//import org.glassfish.jersey.grizzly2.httpserver.GrizzlyHttpServerFactory;
import com.sun.net.httpserver.HttpServer;

import com.sun.javafx.collections.MappingChange.Map;
import com.sun.jersey.api.container.httpserver.HttpServerFactory;
import com.sun.jersey.api.core.PackagesResourceConfig;
import com.sun.jersey.api.core.ResourceConfig;

import java.io.IOException;
import java.net.URI;

/**
 * Main class.
 *
 */
public class Main {
    // Base URI the Grizzly HTTP server will listen on
    public static final String BASE_URI = "http://localhost:9090/myapp/";

    /**
     * Starts Grizzly HTTP server exposing JAX-RS resources defined in this application.
     * @return Grizzly HTTP server.
     * @throws IOException 
     * @throws IllegalArgumentException 
     */
    public static HttpServer startServer() throws IllegalArgumentException, IOException {
        // create a resource config that scans for JAX-RS resources and providers
        // in comet package
        final PackagesResourceConfig rc = new PackagesResourceConfig("comet");//.packages("comet");
        //final PackagesResourceConfig rc = new PackagesResourceConfig("comet");
        rc.getProperties().put("com.sun.jersey.spi.container.ContainerResponseFilters", "comet.CorsSupportFilter");

        HttpServer server = HttpServerFactory.create(BASE_URI, rc);
        server.start();
        return server;
        // create and start a new instance of grizzly http server
        // exposing the Jersey application at BASE_URI
        //return GrizzlyHttpServerFactory.createHttpServer(URI.create(BASE_URI), rc);
    }

    /**
     * Main method.
     * @param args
     * @throws IOException
     */
    public static void main(String[] args) throws IOException {
        final HttpServer server = startServer();
        System.out.println(String.format("Jersey app started with WADL available at "
                + "%sapplication.wadl\nHit enter to stop it...", BASE_URI));
        System.in.read();
        server.stop(0);
    }
}

