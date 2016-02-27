package hackathon.comet;

import java.net.URI;
import java.util.List;

import com.emc.vipr.client.ViPRCoreClient;

public class ViperCaller {

    private ViPRCoreClient client;
    public List<URI>  getVolumes(){
        client = MigrateClient.getViprClient();
       return client.blockVolumes().listBulkIds();
    }
}
