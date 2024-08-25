# Practical Distributed Systems Project: _Data Collection and Analytics Platform_

## Overview

This project implements a data collection and analytics platform designed to handle high-volume user event data. The platform captures user interactions such as `VIEW` and `BUY` events and provides efficient storage and querying capabilities using a distributed system architecture.

## Features

1. **User Event Collection**: Collects user action data (VIEW and BUY events) including metadata such as time, device type, product information, etc.
2. **User Profile Retrieval**: Provides endpoints to retrieve user profiles, including their recent actions sorted by time.
3. **Aggregated Statistics**: Offers endpoints to obtain aggregated statistics for user actions over specified time ranges, supporting various filters and aggregation types.

## Data Format

The platform uses a consistent JSON structure to represent user events. Below is the format of the user event (`<user_tag>`):

```json
{
    "time": "string",   // format: "2022-03-22T12:15:00.000Z" (ISO 8601 format with millisecond precision and 'Z' suffix)
    "cookie": "string",
    "country": "string",
    "device": "PC | MOBILE | TV",
    "action": "VIEW | BUY",
    "origin": "string",
    "product_info": {
        "product_id": "string",
        "brand_id": "string",
        "category_id": "string",
        "price": "int32"
    }
}
```

## Endpoints

### 1. Adding User Tags

Adds a single user tag event via an HTTP request.

- **Endpoint**: `POST /user_tags`
- **Request Body**: JSON formatted `<user_tag>`
- **Performance**: Supports a throughput of at least 1000 requests per second with response times under 200 milliseconds.
- **Data Storage**: Events are stored in an Aerospike database cluster of 5 nodes for efficient and reliable storage.

### 2. Getting User Profiles

Retrieves a user's profile, including a history of their actions.

- **Endpoint**: `POST /user_profiles/{cookie}?time_range=<time_range>&limit=<limit>`
- **Response**: Returns the 200 most recent events for each action type (`VIEW`, `BUY`), sorted in descending time order.
- **Performance**: Supports a throughput of at least 100 requests per second with response times under 200 milliseconds.
- **Data Query**: Events can be queried starting from 10 seconds ago.

### 3. Aggregated User Actions (Statistics)

Provides aggregated statistics of user actions over specified time ranges, grouped into 1-minute buckets.

- **Endpoint**: `POST /aggregates?time_range=<time_range>&action=<action>&aggregates=[<aggregate>]`
- **Parameters**:
  - `time_range` (required): Specifies the time range in seconds, with a maximum length of 10 minutes.
  - `action` (required): The action type (`VIEW` or `BUY`).
  - `aggregates` (required): The type of aggregation (e.g., `COUNT`, `SUM_PRICE`). Multiple aggregates can be specified by repeating the parameter.
  - Additional optional parameters include `origin`, `brand_id`, and `category_id` for filtering results.
- **Response**: Returns aggregated data in a JSON format, with columns specifying the aggregation criteria and rows representing 1-minute buckets within the specified time range.

### Example Queries

- **Query 1**: Number of Nike products bought over time.
  - **Request**: `POST /aggregates?time_range=2022-03-22T12:00:00_2022-03-22T13:00:00&action=BUY&brand_id=Nike&aggregates=COUNT`
- **Query 2**: Views of Adidas women's shoes over time.
  - **Request**: `POST /aggregates?time_range=2022-03-22T12:00:00_2022-03-22T13:00:00&action=VIEW&brand_id=Adidas&category_id=WOMEN_SHOES&aggregates=COUNT`
- **Query 3**: Number of BUY actions and revenue for a specific ad campaign over time.
  - **Request**: `POST /aggregates?time_range=2022-03-22T12:00:00_2022-03-22T13:00:00&action=BUY&origin=NIKE_WOMEN_SHOES_CAMPAIGN&aggregates=COUNT&aggregates=SUM_PRICE`

## Architecture

The platform is distributed across 10 virtual machines (VMs):

- **VMs 106-110**: Aerospike database cluster.
- **VMs 103-104**: Kafka cluster brokers.
- **VM 105**: HAProxy load balancer.
- **VMs 101-102**: Instances of the front-end component that handle incoming traffic and manage interactions with Kafka and Aerospike.

### Front-End Component

The front-end component manages data ingestion and processing. It uses Kafka for event streaming and an Aerospike cluster for storing and retrieving user data. The component is built in Java and uses libraries such as Jackson for JSON handling and Snappy for compression.

```java
package alejandro.salazar.mejia.dao;

// Java imports
import java.time.Instant;
// ... other imports

@Component
public class UserDao {
    // Class details...

    // Method to add a user tag event
    public void addUserTag(UserTagEvent userTagEvent) {
        // Implementation...
    }

    // Method to retrieve user profile
    public UserProfileResult getUserProfile(String cookie, String timeRangeStr, int limit) {
        // Implementation...
    }

    // Method to get aggregated data
    public AggregatesQueryResult getAggregates(String timeRangeStr, Action action, List<Aggregate> aggregates, String origin, String brandId, String categoryId) {
        // Implementation...
    }

    // Additional utility methods...
}
```

## Installation and Deployment

1. **Prerequisites**: Ensure all required software (Java, Aerospike, Kafka, HAProxy, etc.) is installed on the corresponding VMs.
2. **Deployment**: Follow the instructions in the `deploy` directory to set up each component using Ansible scripts.
3. **Configuration**: Update the configuration files under the `config` directory for Aerospike, Kafka, and the front-end component.
4. **Run the Platform**: Start the platform services using the provided scripts or Docker Compose files.

## Contributing

Contributions are welcome! Please submit a pull request or open an issue for discussion.

## License

This project is licensed under the MIT License. See the `LICENSE` file for more details.

---

Feel free to adjust or expand this README to better suit your needs!
