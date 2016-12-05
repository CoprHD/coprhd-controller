import com.emc.vipr.client.ClientConfig;
import com.emc.vipr.client.ViPRCoreClient;

/**
 * Created by wangs12 on 11/30/2016.
 */
public class Main {

    public static void main(String[] args) {
        ViPRCoreClient client = new ViPRCoreClient(new ClientConfig().withHost("10.247.78.62").withIgnoringCertificates(true));
        String authToken = client.auth().oidcLogin("admin", "changeme");

        System.out.println("Login successfully and token is " + authToken);
    }

}
