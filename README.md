# Practical Distributed Systems Project:<br>_Data Collection and Analytics Platform_

## Overview

This project implements a data collection and analytics platform designed to handle high-volume user event data. The platform captures user interactions such as `VIEW` and `BUY` events and provides efficient storage and querying capabilities using a distributed system architecture.

![Architecture diagram](<arch.PNG>)

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

Quick access to the user profiles (history of the user actions). For each user, their 200 most recent events of each type are kept.

- **Endpoint**: `POST /user_profiles/{cookie}?time_range=<time_range>&limit=<limit>`
- **Response**: Returns the 200 most recent events for each action type (`VIEW`, `BUY`), sorted in descending time order.
- **Performance**: Supports a throughput of at least 100 requests per second with response times under 200 milliseconds.
- **Data Query**: Events can be queried starting from 10 seconds ago.

### 3. Aggregated User Actions (Statistics)

This endpoint allow you to get aggregated stats about user actions matching some criteria and grouped by 1 minute buckets from the given time range.
This allows you to view historical data from different perspectives. There is a broad category of queries for which aggregating results in 1 minute buckets is satisfactory. Such queries will allow us to present various metrics over longer periods of time enabling us to visualize trends such as: users' buying patterns, performance of ad campaigns, etc. Aggregates become available in query results soon after their time buckets are closed (in a matter of minutes). There is no predefined list of metrics which are going to be visualized.

#### Examples

##### Example 1
We want to draw a chart showing the number of Nike products bought over time.

* HTTP query
  * `POST /aggregates?time_range=<time_from>_<time_to>&action=BUY&brand_id=Nike&aggregates=COUNT`
* Result (presented here as rows)
  * Let's assume there are N buckets inside the given range: bucket1 ... bucketN.
    | 1m_bucket | action | brand_id | count |
    | --------- | ------ | -------- | ----- |
    | bucket1   | BUY    | Nike     | 49    |
    | bucket2   | BUY    | Nike     | 52    |
    | ...       | ...    | ...      | ...   |
    | bucketN   | BUY    | Nike     | 77    |


##### Example 2
We want to draw a chart showing the number of Adidas women shoes viewed over time.

* HTTP query
  * `POST /aggregates?time_range=<time_from>_<time_to>&action=VIEW&brand_id=Adidas&category_id=WOMEN_SHOES&aggregates=COUNT`
* Result (presented here as rows)
  * Let's assume there are N buckets inside the given range: bucket1 ... bucketN.
    | 1m_bucket | action | brand_id | category_id | count |
    | --------- | ------ | -------- | ----------- | ----- |
    | bucket1   | VIEW   | Adidas   | WOMAN_SHOES | 510   |
    | bucket2   | VIEW   | Adidas   | WOMAN_SHOES | 490   |
    | ...       | ...    | ...      | ...         | ...   |
    | bucketN   | VIEW   | Adidas   | WOMAN_SHOES | 770   |

##### Example 3

We want to draw a chart showing two metrics over time: the number of BUY actions and the revenue, both associated with the given origin (i.e. ad campaign).

* HTTP query
  * `POST /aggregates?time_range=<time_from>_<time_to>&action=BUY&origin=NIKE_WOMEN_SHOES_CAMPAIGN&aggregates=COUNT&aggregates=SUM_PRICE`
    * Different aggregates are represented by repeating "aggregates" query params.
* Result (presented here as rows)
  * Let's assume there are N buckets inside the given range: bucket1 ... bucketN.
    | 1m_bucket | action | origin                    | count | sum_price |
    | --------- | ------ | ------------------------- | ----- | --------- |
    | bucket1   | BUY    | NIKE_WOMEN_SHOES_CAMPAIGN | 5     | 580       |
    | bucket2   | BUY    | NIKE_WOMEN_SHOES_CAMPAIGN | 12    | 2190      |
    | ...       | ...    | ...                       | ...   | ...       |
    | bucketN   | BUY    | NIKE_WOMEN_SHOES_CAMPAIGN | 7     | 840       |


#### Semantics of the query corresponds to the following SQL:
  ```
  SELECT 1m_bucket(time), action, [origin, brand_id, category_id], count(*), sum(price)
      FROM events
      WHERE time >= ${time_range.begin} and time < ${time_range.end}
              AND action = ${action}
              [AND origin = ${origin}]
              [AND brand_id = ${brand_id}]
              [AND category_id = ${category_id}]
      GROUP BY 1m_bucket(time), action, [origin, brand_id, category_id]
      ORDER BY 1m_bucket(time)
  ```

- **Endpoint**: `POST /aggregates?time_range=<time_range>&action=<action>&aggregates=[<aggregate>]`
- **Parameters**:
  - `time_range` (required): Specifies the time range in seconds, with a maximum length of 10 minutes.
  - `action` (required): The action type (`VIEW` or `BUY`).
  - `aggregates` (required): The type of aggregation (e.g., `COUNT`, `SUM_PRICE`). Multiple aggregates can be specified by repeating the parameter.
  - Additional optional parameters include `origin`, `brand_id`, and `category_id` for filtering results.
- **Response**: Returns aggregated data in a JSON format, with columns specifying the aggregation criteria and rows representing 1-minute buckets within the specified time range.
- **Performance**: Supports a throughput of at least 1 request per second with response times under 200 milliseconds.
- **Data Query**: Events can be queried starting from 1 minute ago.


## Architecture

![Architecture diagram](<arch.PNG>)

The platform is distributed across 10 virtual machines (VMs) to ensure scalability and resilience. The VMs are organized as follows:

- **VMs 106-110**: Aerospike database cluster, responsible for storing and retrieving user data efficiently.
- **VMs 103-104**: Apache Kafka cluster, which facilitates high-throughput messaging and event streaming between components
- **VM 105**: HAProxy, a high-availability load balancer that distributes incoming traffic across the front component instances to ensure reliability
- **VMs 101-102**: Front component instances. *(Refer to the [Front Component](#front-component) section for more details.)*
- **VMs 103-104**: Processor component instances. *(Refer to the [Processor Component](#processor-component) section for more details.)*

### Front Component

The front component manages data ingestion, processing for Use Case 1 and 2, and data retieval for Use Case 3. It uses Kafka for event streaming the user tags to the processor component and an Aerospike cluster for storing and retrieving user data. The component is built in Java Spring boot and uses libraries such as Jackson for JSON handling and Snappy for compression.

### Processor Component

The Processor component consumes user tag events from the Kafka topic, calculates various aggregates utilizing Kafka Streams' state store, and makes these aggregates available to the Front component through Aerospike. This component is crucial for Use Cases 3, which involve real-time aggregation and data analysis.
The processing logic is written in Java, utilizing Kafka Streams for processing and Aerospike for storage.



## Installation and Deployment
1. Log into machine `st112vm105.rtb-lab.pl`:
```bash
ssh st112@st112vm105.rtb-lab.pl
```
2. Clone the project repository:
```bash
git clone https://github.com/thually/Practical-Distributed-Systems.git
```
3. Change to directory `Practical-Distributed-Systems/deployment`:
```bash
cd ~/Practical-Distributed-Systems/deployment
```
4. Edit the last line of the shell script `Practical-Distributed-Systems/deployment/setup_and_deploy.sh` to specify the `<user>` and `<password>`.
5. Make the shell script executable:
```bash
chmod +x setup_and_deploy.sh
```
6. Execute the script:
```bash
./setup_and_deploy.sh
```
