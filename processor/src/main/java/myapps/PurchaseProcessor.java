package myapps;


import java.time.Duration;
import java.util.Arrays;


import org.apache.kafka.streams.KeyValue;
import org.apache.kafka.streams.processor.PunctuationType;
import org.apache.kafka.streams.processor.api.Processor;
import org.apache.kafka.streams.processor.api.ProcessorContext;
import org.apache.kafka.streams.processor.api.Record;
import org.apache.kafka.streams.state.KeyValueIterator;
import org.apache.kafka.streams.state.KeyValueStore;

import com.aerospike.client.AerospikeClient;
import com.aerospike.client.Bin;
import com.aerospike.client.Host;
import com.aerospike.client.Key;
import com.aerospike.client.Operation;
import com.aerospike.client.policy.ClientPolicy;
import com.aerospike.client.policy.CommitLevel;
import com.aerospike.client.policy.RecordExistsAction;
import com.aerospike.client.policy.Replica;
import com.aerospike.client.policy.WritePolicy;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;

public class PurchaseProcessor implements Processor<String, String, String, String> {
    private KeyValueStore<String, Long> countStore;
    private KeyValueStore<String, Long> sumStore;
    private AerospikeClient aerospikeClient;
    private static final ObjectMapper objectMapper = new ObjectMapper().registerModule(new JavaTimeModule());


    @Override
    public void init(ProcessorContext<String, String> context) {
        countStore = context.getStateStore("actions-count");
        sumStore = context.getStateStore("actions-sum");
        String[] aerospikeSeeds = { "st112vm106.rtb-lab.pl", "st112vm107.rtb-lab.pl" };
        int port = 3000;
        aerospikeClient = new AerospikeClient(defaultClientPolicy(), Arrays.stream(aerospikeSeeds).map(seed -> new Host(seed, port)).toArray(Host[]::new));
        context.schedule(Duration.ofSeconds(10), PunctuationType.WALL_CLOCK_TIME, timestamp -> flushToAerospike());
    }

    @Override
    public void process(Record<String, String> record) {
        try {
            UserTagEvent event = objectMapper.readValue(record.value(), UserTagEvent.class);
            String keyPrefix = record.key();

            // TODO: Remove this line
            // System.out.println("Processing event: " + event);

            if (event.getAction() == Action.BUY || event.getAction() == Action.VIEW) {
                String actionType = event.getAction().name();
                updateMetrics(keyPrefix, actionType, event);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void updateMetrics(String keyPrefix, String actionType, UserTagEvent event) {
        String[] dimensions = {
            "", event.getOrigin(), event.getProductInfo().getBrandId(), event.getProductInfo().getCategoryId(),
            event.getOrigin() + "_" + event.getProductInfo().getBrandId(),
            event.getOrigin() + "_" + event.getProductInfo().getCategoryId(),
            event.getProductInfo().getBrandId() + "_" + event.getProductInfo().getCategoryId(),
            event.getOrigin() + "_" + event.getProductInfo().getBrandId() + "_" + event.getProductInfo().getCategoryId()
        };

        for (String dimension : dimensions) {
            String countSumKey = keyPrefix + "_" + actionType + (dimension.isEmpty() ? "" : "_" + dimension);

            // TODO: Remove this line
            // System.out.println("Incrementing count and sum for key: " + countSumKey);

            incrementStore(countStore, countSumKey, 1L);
            incrementStore(sumStore, countSumKey, (long) event.getProductInfo().getPrice());
        }
    }

    private void incrementStore(KeyValueStore<String, Long> store, String key, Long value) {
        Long oldValue = store.get(key);
        store.put(key, (oldValue == null ? 0 : oldValue) + value);
    }

    private void flushToAerospike() {

        WritePolicy writePolicy = new WritePolicy(aerospikeClient.writePolicyDefault);

        try (final KeyValueIterator<String, Long> countIter = countStore.all()) {
            while (countIter.hasNext()) {
                KeyValue<String, Long> countEntry = countIter.next();
                String key = countEntry.key;
                long localCount = countEntry.value;
                long localSum = sumStore.get(key) != null ? sumStore.get(key) : 0L;

                Key aerospikeKey = new Key("mimuw", "aggregates", key);
                Bin countBin = new Bin("count", localCount);
                Bin sumBin = new Bin("sum_price", localSum);

                // Atomically add the local count and sum to the values in Aerospike
                aerospikeClient.operate(writePolicy, aerospikeKey,
                    Operation.add(countBin),
                    Operation.add(sumBin)
                );
            }
        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            clearStores();
        }
    }

    private void clearStores() {
        try (final KeyValueIterator<String, Long> countIter = countStore.all()) {
            while (countIter.hasNext()) {
                String currentKey = countIter.next().key;
                countStore.delete(currentKey);
                sumStore.delete(currentKey);
            }
        }
    }

    private static ClientPolicy defaultClientPolicy() {
        ClientPolicy defaultClientPolicy = new ClientPolicy();
        defaultClientPolicy.readPolicyDefault.replica = Replica.MASTER_PROLES;
        defaultClientPolicy.readPolicyDefault.socketTimeout = 1000;
        defaultClientPolicy.readPolicyDefault.totalTimeout = 1000;
        defaultClientPolicy.writePolicyDefault.socketTimeout = 15000;
        defaultClientPolicy.writePolicyDefault.totalTimeout = 15000;
        defaultClientPolicy.writePolicyDefault.maxRetries = 1;
        defaultClientPolicy.writePolicyDefault.commitLevel = CommitLevel.COMMIT_MASTER;
        defaultClientPolicy.writePolicyDefault.recordExistsAction = RecordExistsAction.UPDATE;
        defaultClientPolicy.writePolicyDefault.sendKey = true;
        defaultClientPolicy.writePolicyDefault.expiration = 60 * 60 * 24; // 1 day
        return defaultClientPolicy;
    }
}
