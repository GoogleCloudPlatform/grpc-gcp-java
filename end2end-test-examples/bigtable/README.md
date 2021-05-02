# Bigtable test client

This is a simple test client for java-bigtable over DirectPath and CFE. 

## Example

A DataClient sends `numCall` read or write requests to CBT with project ID `directpath-prod-manual-testing` and instance ID `blackbox-us-central1-b`. The payload of each request is `reqSize` KB.

To test over CFE:
```sh
mvn clean compile exec:java -Dexec.mainClass="io.grpc.bigtable.TestMain" -Dexec.args="--method=write --numClient=1 --reqSize=1 --numCall=10"
```

To test over DP:
```sh
mvn clean compile exec:java -Dexec.mainClass="io.grpc.bigtable.TestMain" -Dexec.args="--method=write --numClient=1 --reqSize=1 --numCall=10" -Dbigtable.attempt-directpath=true
```

Example results:

```sh
10 total write requests sent. 
Payload per request = 1KB
Total payload = 10240KB. Total latency = 973s. Per sec payload = 10524.15KB
                Min     p50     p90     p99     p99.9   MAX
  Time(ms)      7       107     111     231     231     231
```

## Args

`--method`: test method, can only be read or write.

`--numClient`: number of CBT DataClient.

`--reqSize`: payload of one request. Unit: KB.

`--numCall`: number of requests.
