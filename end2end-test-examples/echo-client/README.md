# Echo Client

This is a simple client for e2e testing against our Echo service defined in [echo.proto](src/main/proto/echo.proto).


## Example

Client sends out `numRpcs` number of unary requests to `host` sequentially
with request payload size of `reqSize`, and expected response payload size of `rspSize`.

Run test:

```sh
./gradlew run --args="--numRpcs=100 --reqSize=100 --rspSize=100 --host=grpc-cloudapi1.googleapis.com"
```

Example results:

```sh
              Avg     Min     p50     p90     p99     Max
Time(ms)      67      65      68      70      72      72
```

