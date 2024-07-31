package alejandro.salazar.mejia.dao;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Iterator;
import java.util.List;

import javax.annotation.PostConstruct;
import javax.annotation.PreDestroy;
import javax.xml.validation.Schema;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import com.aerospike.client.AerospikeClient;
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
import com.aerospike.client.query.ResultSet;
import com.aerospike.client.query.Statement;
import com.aerospike.client.task.RegisterTask;

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
        defaultClientPolicy.writePolicyDefault.recordExistsAction = RecordExistsAction.REPLACE;
        return defaultClientPolicy;
    }

    public UserDao(@Value("${aerospike.seeds}") String[] aerospikeSeeds, @Value("${aerospike.port}") int port) {
        this.client = new AerospikeClient(defaultClientPolicy(), Arrays.stream(aerospikeSeeds).map(seed -> new Host(seed, port)).toArray(Host[]::new));
    }


    public void addUserTag(UserTagEvent userTagEvent) throws Exception {

        // Cookie is the unique identifier for the user
        Key key = new Key(NAMESPACE, SET, userTagEvent.getCookie());
        Policy readPolicy = new Policy(client.readPolicyDefault);
        Record record = client.get(readPolicy, key);

        List<UserTagEvent> viewEvents;
        List<UserTagEvent> buyEvents;

        if (record == null) {
            // No existing record, create new lists
            viewEvents = new ArrayList<>();
            buyEvents = new ArrayList<>();
        } else {
            // Existing record, decompress and parse JSON
            viewEvents = parseEvents(record.getValue(VIEW_BIN));
            buyEvents = parseEvents(record.getValue(BUY_BIN));
        }
        
        
        // Add the new event to the appropriate list. Only keep the most recent MAX_EVENTS events.
        if (userTagEvent.getAction() == Action.VIEW) {
            insertEvent(viewEvents, userTagEvent);
        } else {
            insertEvent(buyEvents, userTagEvent);
        }
        
        
        log.info("\nAdding user tag event: {} \n", userTagEvent);

        // Convert lists to JSON, compress and save in Aerospike
        byte[] compressedViewEventsJson = compressEvents(viewEvents);
        byte[] compressedBuyEventsJson = compressEvents(buyEvents);

        Bin viewBin = new Bin(VIEW_BIN, compressedViewEventsJson);
        Bin buyBin = new Bin(BUY_BIN, compressedBuyEventsJson);
        WritePolicy policy = new WritePolicy(client.writePolicyDefault);
        client.put(policy, key, viewBin, buyBin);

    }

    public UserProfileResult getUserProfile(String cookie, String timeRangeStr, int limit, UserProfileResult expectedResult) throws Exception {
        return null;
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
