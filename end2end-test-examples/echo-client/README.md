# Echo Client

This is a simple client for e2e testing against our Echo service defined in [echo.proto](src/main/proto/echo.proto).

## Usage

The client sends out `numRpcs` number of Ping-Pong unary requests sequentially
with payload size specified by `payloadKB`, and displays the test result.

## Build and run the test

Build:

```sh
./gradlew build
```

Run client with test service:

```sh
./gradlew run --args="--numRpcs=100 --payloadKB=100 --host=test-service.googleapis.com"
```

Example results:

```sh
              Avg     Min     p50     p90     p99     Max
Time(ms)      67      65      68      70      72      72
```

