package myapps;

import java.util.Properties;
import java.util.concurrent.CountDownLatch;

import org.apache.kafka.common.serialization.Serdes;
import org.apache.kafka.streams.KafkaStreams;
import org.apache.kafka.streams.StreamsConfig;
import org.apache.kafka.streams.Topology;
import org.apache.kafka.streams.state.KeyValueStore;
import org.apache.kafka.streams.state.StoreBuilder;
import org.apache.kafka.streams.state.Stores;

public class MyApp {
    private static final String TOPIC = "user_tags";

    public static void main(String[] args) {
        Properties props = new Properties();
        props.put(StreamsConfig.APPLICATION_ID_CONFIG, "aggregator");
        props.put(StreamsConfig.BOOTSTRAP_SERVERS_CONFIG, "st112vm103.rtb-lab.pl:9092");
        props.put(StreamsConfig.DEFAULT_KEY_SERDE_CLASS_CONFIG, Serdes.String().getClass());
        props.put(StreamsConfig.DEFAULT_VALUE_SERDE_CLASS_CONFIG, Serdes.String().getClass());

        StoreBuilder<KeyValueStore<String, Long>> countStoreBuilder = Stores.keyValueStoreBuilder(
                Stores.persistentKeyValueStore("actions-count"),
                Serdes.String(),
                Serdes.Long()
        );

        StoreBuilder<KeyValueStore<String, Long>> sumStoreBuilder = Stores.keyValueStoreBuilder(
                Stores.persistentKeyValueStore("actions-sum"),
                Serdes.String(),
                Serdes.Long()
        );

        Topology builder = new Topology();
        builder.addSource("source", TOPIC)
                .addProcessor("processor", PurchaseProcessor::new, "source")
                .addStateStore(countStoreBuilder, "processor")
                .addStateStore(sumStoreBuilder, "processor");

        final KafkaStreams streams = new KafkaStreams(builder, props);
        final CountDownLatch latch = new CountDownLatch(1);

        // attach shutdown handler to catch control-c
        Runtime.getRuntime().addShutdownHook(new Thread("streams-shutdown-hook") {
            @Override
            public void run() {
                streams.close();
                latch.countDown();
            }
        });

        try {
            streams.start();
            latch.await();
        } catch (Throwable e) {
            System.exit(1);
        }
        System.exit(0);
    }
}
