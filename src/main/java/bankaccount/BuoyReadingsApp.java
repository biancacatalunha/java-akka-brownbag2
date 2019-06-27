package bankaccount;

import akka.actor.ActorRef;
import akka.actor.ActorSystem;
import akka.actor.Props;
import akka.cluster.sharding.ClusterSharding;
import akka.cluster.sharding.ClusterShardingSettings;
import bankaccount.actors.BuoyReadingActor;
import bankaccount.domain.siterep.BuoyConfig;
import bankaccount.domain.siterep.BuoyConfigObject;
import bankaccount.domain.siterep.BuoyReadingCommand;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.net.InetSocketAddress;
import java.net.ProxySelector;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

public class BuoyReadingsApp {

    public static void main(String[] args){

        final ActorSystem system = ActorSystem.create("accountApp");
        ClusterShardingSettings settings = ClusterShardingSettings.create(system);
        ActorRef buoyReadingActorRegion = ClusterSharding.get(system)
                .start("BuoyReadingActor",
                        Props.create(BuoyReadingActor.class).withDispatcher("actor-dispatcher"),
                        settings,
                        BuoyReadingActor.shardRegionMessageExtractor);

        HttpClient client = HttpClient.newBuilder()
                .proxy(ProxySelector.of(new InetSocketAddress("hqproxy.corp.ppbplc.com", 8080)))
                .build();

        ObjectMapper mapper = new ObjectMapper();
        mapper.disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES);

        BuoyConfig buoyConfig = new BuoyConfig();

        BuoyConfigObject config = buoyConfig.getConfig();
        config.getLocation().forEach(k -> {

            URI url = URI.create("http://datapoint.metoffice.gov.uk/public/data/val/wxmarineobs/all/json/" + k.getI() + "?res=daily&key=" + config.getKey());
            HttpRequest request = HttpRequest.newBuilder().uri(url).build();

            String siteRepString = client.sendAsync(request, HttpResponse.BodyHandlers.ofString())
                    .thenApply(HttpResponse::body)
                    .join();

            try {
                BuoyReadingCommand buoyReading = mapper.readValue(siteRepString, BuoyReadingCommand.class);
                buoyReadingActorRegion.tell(buoyReading, ActorRef.noSender());
            } catch (Exception e) {
                System.out.print("Unable to convert json to object" +  e);
            }
        });
    }
}
