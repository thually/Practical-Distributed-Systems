package alejandro.salazar.mejia.dao;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

import javax.annotation.PreDestroy;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import com.aerospike.client.AerospikeClient;
import com.aerospike.client.AerospikeException;
import com.aerospike.client.ResultCode;
import com.aerospike.client.Bin;
import com.aerospike.client.Host;
import com.aerospike.client.Key;
import com.aerospike.client.Record;
import com.aerospike.client.policy.ClientPolicy;
import com.aerospike.client.policy.CommitLevel;
import com.aerospike.client.policy.Policy;
import com.aerospike.client.policy.RecordExistsAction;
import com.aerospike.client.policy.Replica;
import com.aerospike.client.policy.WritePolicy;
import com.aerospike.client.policy.GenerationPolicy;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.fasterxml.jackson.core.type.TypeReference;
import org.xerial.snappy.Snappy;

import alejandro.salazar.mejia.domain.Action;
import alejandro.salazar.mejia.domain.UserProfileResult;
import alejandro.salazar.mejia.domain.UserTagEvent;
import java.util.Comparator;


@Component
public class UserDao {

    private static final Logger log = LoggerFactory.getLogger(UserDao.class);

    private static final String NAMESPACE = "mimuw";
    private static final String SET = "users";
    private static final String VIEW_BIN = "views";
    private static final String BUY_BIN = "buys";
    private static final int MAX_EVENTS = 200;

    private static final ObjectMapper objectMapper = new ObjectMapper().registerModule(new JavaTimeModule());

    private AerospikeClient client;

    private static ClientPolicy defaultClientPolicy() {
        ClientPolicy defaultClientPolicy = new ClientPolicy();
        defaultClientPolicy.readPolicyDefault.replica = Replica.MASTER_PROLES;
        defaultClientPolicy.readPolicyDefault.socketTimeout = 1000;
        defaultClientPolicy.readPolicyDefault.totalTimeout = 1000;
        defaultClientPolicy.writePolicyDefault.socketTimeout = 15000;
        defaultClientPolicy.writePolicyDefault.totalTimeout = 15000;
        defaultClientPolicy.writePolicyDefault.maxRetries = 1;
        defaultClientPolicy.writePolicyDefault.commitLevel = CommitLevel.COMMIT_MASTER;
        return defaultClientPolicy;
    }

    public UserDao(@Value("${aerospike.seeds}") String[] aerospikeSeeds, @Value("${aerospike.port}") int port) {
        this.client = new AerospikeClient(defaultClientPolicy(), Arrays.stream(aerospikeSeeds).map(seed -> new Host(seed, port)).toArray(Host[]::new));
    }


    public void addUserTag(UserTagEvent userTagEvent) throws Exception {

        // Cookie is the unique identifier for the user
        Key key = new Key(NAMESPACE, SET, userTagEvent.getCookie());
        Policy readPolicy = new Policy(client.readPolicyDefault);
        WritePolicy writePolicy = new WritePolicy(client.writePolicyDefault);

        // Optimistic concurrency control
        while (true) {
            Record record = client.get(readPolicy, key);
            List<UserTagEvent> viewEvents;
            List<UserTagEvent> buyEvents;
            

            if (record == null) {
                // No existing record, create new lists
                viewEvents = new ArrayList<>();
                buyEvents = new ArrayList<>();

                // Set the write policy to create only if the record does not exist
                writePolicy.recordExistsAction = RecordExistsAction.CREATE_ONLY;

            } else {
                // Existing record, decompress and parse JSON
                viewEvents = parseEvents(record.getValue(VIEW_BIN));
                buyEvents = parseEvents(record.getValue(BUY_BIN));

                // Set the write policy to replace the record
                writePolicy.recordExistsAction = RecordExistsAction.REPLACE;

                // Set the generation policy and expected generation
                writePolicy.generationPolicy = GenerationPolicy.EXPECT_GEN_EQUAL;
                writePolicy.generation = record.generation;

            }

            // Add the new event to the appropriate list. Only keep the most recent MAX_EVENTS events.
            if (userTagEvent.getAction() == Action.VIEW) {
                insertEvent(viewEvents, userTagEvent);
            } else {
                insertEvent(buyEvents, userTagEvent);
            }


            // log.info("\nAdding user tag event: {} \n", userTagEvent);
    
            // Convert lists to JSON and compress
            byte[] compressedViewEventsJson = compressEvents(viewEvents);
            byte[] compressedBuyEventsJson = compressEvents(buyEvents);
    
            Bin viewBin = new Bin(VIEW_BIN, compressedViewEventsJson);
            Bin buyBin = new Bin(BUY_BIN, compressedBuyEventsJson);
            

            // Write to the database
            try {
                client.put(writePolicy, key, viewBin, buyBin);
                break;
            } catch (AerospikeException e) {
                if (e.getResultCode() == ResultCode.GENERATION_ERROR || e.getResultCode() == ResultCode.KEY_EXISTS_ERROR) {
                    // Retry on generation error or key exists error
                    log.warn("Optimistic concurrency control failed, retrying");
                } else {
                    throw e; // If it's another exception, rethrow it
                }

            }

        }

    }

    public UserProfileResult getUserProfile(String cookie, String timeRangeStr, int limit, UserProfileResult expectedResult) throws Exception {
        // Parse the time range
        String[] timeRange = timeRangeStr.split("_");
        Instant startTime = Instant.parse(timeRange[0] + "Z");
        Instant endTime = Instant.parse(timeRange[1] + "Z");

        // Retrieve the user record
        Key key = new Key(NAMESPACE, SET, cookie);
        Policy readPolicy = new Policy(client.readPolicyDefault);
        Record record = client.get(readPolicy, key);

        if (record == null) {
            return new UserProfileResult(cookie, new ArrayList<>(), new ArrayList<>());
        }

        // Decompress and parse events
        List<UserTagEvent> viewEvents = parseEvents(record.getValue(VIEW_BIN));
        List<UserTagEvent> buyEvents = parseEvents(record.getValue(BUY_BIN));

        // Filter and limit events
        List<UserTagEvent> filteredViews = filterAndLimitEvents(viewEvents, startTime, endTime, limit);
        List<UserTagEvent> filteredBuys = filterAndLimitEvents(buyEvents, startTime, endTime, limit);
        
        UserProfileResult result = new UserProfileResult(cookie, filteredViews, filteredBuys);

        // TODO: Remove this
        if (!expectedResult.toString().equals(result.toString())) {
            log.error("Different results for cookie: {}, time range: {}, limit: {}", cookie, timeRangeStr, limit);
            log.info("Expected result: \t{}", expectedResult);
            log.info("Actual result: \t{}\n", result);
            log.info("View events: \t{}", viewEvents);
            log.info("Buy events: \t{}", buyEvents);
        }
        

        return result;
    }

    private static List<UserTagEvent> filterAndLimitEvents(List<UserTagEvent> events, Instant startTime, Instant endTime, int limit) {
        return events.stream()
                .filter(event -> !event.getTime().isBefore(startTime) && event.getTime().isBefore(endTime))
                .limit(limit)
                .collect(Collectors.toList());
    }

    private static List<UserTagEvent> parseEvents(Object compressedData) throws Exception {
        if (compressedData == null) {
            log.error("Compressed data is null");
            return new ArrayList<>();
        }
        byte[] decompressedData = Snappy.uncompress((byte[]) compressedData);
        return objectMapper.readValue(decompressedData, new TypeReference<List<UserTagEvent>>() {});
    }

    private static void insertEvent(List<UserTagEvent> events, UserTagEvent newEvent) {
        events.add(newEvent);
        events.sort(Comparator.comparing(UserTagEvent::getTime).reversed());
        if (events.size() > MAX_EVENTS) {
            events.subList(MAX_EVENTS, events.size()).clear();
        }
    }

    private static byte[] compressEvents(List<UserTagEvent> events) throws Exception {
        String json = objectMapper.writeValueAsString(events);
        return Snappy.compress(json);
    }


    @PreDestroy
    public void close() {
        client.close();
    }
}
