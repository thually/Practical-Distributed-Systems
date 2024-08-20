package alejandro.salazar.mejia;

import alejandro.salazar.mejia.domain.*;

import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Comparator;

import org.xerial.snappy.Snappy;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.aerospike.client.AerospikeClient;
import com.aerospike.client.Bin;
import com.aerospike.client.Host;
import com.aerospike.client.Key;
import com.aerospike.client.Record;
import com.aerospike.client.policy.ClientPolicy;
import com.aerospike.client.policy.CommitLevel;
import com.aerospike.client.policy.RecordExistsAction;
import com.aerospike.client.policy.Replica;
import com.aerospike.client.policy.WritePolicy;
import com.fasterxml.jackson.core.type.TypeReference;

public class MyClassTest {

    private static final String NAMESPACE = "mimuw";
    private static final String SET_USERS = "users";
    private static final String SET_TEST = "test";
    private static final String VIEW_BIN = "views";
    private static final String BUY_BIN = "buys";
    private static final int MAX_EVENTS = 200;
    private static final ObjectMapper objectMapper = new ObjectMapper().registerModule(new JavaTimeModule());

    public static void main(String[] args) throws Exception {

        test4();

    }

    public static void test4() {
        String timeRangeStr = "2022-03-22T12:25:00_2022-03-22T12:28:00";
        // Parse the time range into start and end Instants
        String[] timeRange = timeRangeStr.split("_");
        Instant startTime = Instant.parse(timeRange[0] + "Z");
        Instant endTime = Instant.parse(timeRange[1] + "Z");

        System.out.println("Start time: " + startTime);
        System.out.println("End time: " + endTime);

        System.out.println("Start time: " + startTime.atZone(ZoneOffset.UTC).format(DateTimeFormatter.ISO_LOCAL_DATE_TIME));
        System.out.println("End time: " + endTime.atZone(ZoneOffset.UTC).format(DateTimeFormatter.ISO_LOCAL_DATE_TIME));

        while (startTime.isBefore(endTime)) {
            System.out.println(startTime.atZone(ZoneOffset.UTC).format(DateTimeFormatter.ISO_LOCAL_DATE_TIME));
            startTime = startTime.plusSeconds(60);
            
        }
    }

    public static void test3() {

        Instant instant = Instant.now();
        System.out.println(instant);

        ZonedDateTime zonedDateTime = instant.atZone(ZoneOffset.UTC).withSecond(0).withNano(0);
        // Format as a string in the desired format
        String timeBucket = zonedDateTime.format(DateTimeFormatter.ISO_LOCAL_DATE_TIME);

        // Output the result
        System.out.println(timeBucket);
        

        // Interpreting the Instant in UTC
        ZonedDateTime utcTime = instant.atZone(ZoneOffset.UTC);
        System.out.println("UTC Time: " + utcTime);

        // Interpreting the Instant in New York Time
        ZonedDateTime nyTime = instant.atZone(ZoneId.of("America/New_York"));
        System.out.println("New York Time: " + nyTime);

        // Interpreting the Instant in Warsaw Time
        ZonedDateTime warsawTime = instant.atZone(ZoneId.of("Europe/Warsaw"));
        System.out.println("Warsaw Time: " + warsawTime);
    }

    public static void test2() {

        // help me test insertInt
        List<Integer> integers = new ArrayList<>();
        // insert 250 integers
        for (int i = 0; i < 250; i++) {
            insertInt(integers, i);
        }
        System.out.println("After inserting 250 integers: ");
        System.out.println(integers);
        System.out.println(integers.size());

    }

    private static void test1() throws Exception {
        System.out.println("This is test1 method in the test folder.");

        String[] aerospikeSeeds = { "st112vm106.rtb-lab.pl", "st112vm107.rtb-lab.pl" };
        int port = 3000;
        AerospikeClient client = new AerospikeClient(defaultClientPolicy(),
                Arrays.stream(aerospikeSeeds).map(seed -> new Host(seed, port)).toArray(Host[]::new));

        // Add your test-specific code here
        UserTagEvent userTag1 = new UserTagEvent(
                Instant.now(),
                "cookie123",
                "USA",
                Device.PC,
                Action.VIEW,
                "origin",
                new Product(1, "brandA", "categoryX", 100));

        UserTagEvent userTag2 = new UserTagEvent(
                Instant.now(),
                "cookie123",
                "CAN",
                Device.MOBILE,
                Action.BUY,
                "productPage",
                new Product(2, "brandB", "categoryY", 200));

        UserTagEvent userTag3 = new UserTagEvent(
                Instant.now(),
                "cookie123",
                "UK",
                Device.TV,
                Action.VIEW,
                "checkout",
                new Product(3, "brandC", "categoryZ", 300));

        /*
         * System.out.println(userTag1 + "\n");
         * System.out.println(userTag2);
         * System.out.println(userTag3);
         */

        List<UserTagEvent> viewEvents = List.of(userTag3, userTag1);
        List<UserTagEvent> buyEvents = new ArrayList<>();
        System.out.println("Before aerospike: ");
        System.out.println("View events: " + viewEvents);
        System.out.println("Buy events: " + buyEvents);
        System.out.println();

        // Compress the data
        byte[] compressedViewEvents = compressEvents(viewEvents);
        byte[] compressedBuyEvents = compressEvents(buyEvents);

        Bin viewBin = new Bin(VIEW_BIN, compressedViewEvents);
        Bin buyBin = new Bin(BUY_BIN, compressedBuyEvents);
        WritePolicy policy = new WritePolicy(client.writePolicyDefault);
        Key key = new Key(NAMESPACE, SET_TEST, "cookie123");
        client.put(policy, key, viewBin, buyBin);

        // Read the data
        Key keyRead = new Key(NAMESPACE, SET_TEST, "cookie123");
        Record record = client.get(null, keyRead);
        List<UserTagEvent> viewEventsRead = parseEvents(record.getValue(VIEW_BIN));
        List<UserTagEvent> buyEventsRead = parseEvents(record.getValue(BUY_BIN));

        System.out.println("After aerospike: ");
        System.out.println("View events: " + viewEventsRead);
        System.out.println("Buy events: " + buyEventsRead);
    }

    private static List<UserTagEvent> parseEvents(Object compressedData) throws Exception {
        if (compressedData == null) {
            return new ArrayList<>();
        }
        byte[] decompressedData = Snappy.uncompress((byte[]) compressedData);
        return objectMapper.readValue(decompressedData, new TypeReference<List<UserTagEvent>>() {
        });
    }

    private static void insertEvent(List<UserTagEvent> events, UserTagEvent newEvent) {
        events.add(newEvent);
        events.sort(Comparator.comparing(UserTagEvent::getTime).reversed());
        if (events.size() > MAX_EVENTS) {
            events.subList(MAX_EVENTS, events.size()).clear();
        }
    }

    private static void insertInt(List<Integer> integers, int integer) {
        integers.add(integer);
        integers.sort(Comparator.comparing(Integer::intValue).reversed());
        if (integers.size() > MAX_EVENTS) {
            integers.subList(MAX_EVENTS, integers.size()).clear();
        }
    }

    private static byte[] compressEvents(List<UserTagEvent> events) throws Exception {
        String json = objectMapper.writeValueAsString(events);
        return Snappy.compress(json);
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
        defaultClientPolicy.writePolicyDefault.recordExistsAction = RecordExistsAction.REPLACE;
        return defaultClientPolicy;
    }
}
