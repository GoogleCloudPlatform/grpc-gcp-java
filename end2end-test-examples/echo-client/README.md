# Echo Client

This is a simple client for e2e testing against our Echo service defined in [echo.proto](src/main/proto/echo.proto).

## Example

Client sends out `numRpcs` number of unary requests to `host` sequentially
with request payload size of `reqSize`, and expected response payload size of `rspSize`:

```sh
./gradlew run --args="--numRpcs=100 --reqSize=100 --resSize=100 --host=grpc-cloudapi1.googleapis.com"
```

Enable gRPC compression for both request and response with gzip:

```sh
./gradlew run --args="--numRpcs=100 --reqSize=100 --resSize=100 --reqComp=gzip --resComp=gzip --host=grpc-cloudapi1.googleapis.com"
```

Example results:

```sh
Total Send time = 14634ms, Total Receive time = 14634ms
1 channels, 100 total rpcs sent
Payload Size per request = 100KB
Per sec Payload = 0.07 MB (exact amount of KB = 10000)
                Min     p50     p90     p99     p99.9   Max
  Time(ms)      72      135     183     287     1343    1343
```

## Args
`--host`: Target endpoint.

`--numRpcs`: Number of gRPC calls to send.

`--warmup`: Number of warm-up RPCs to send before the test. Default: 5.

`--reqSize`: Set request payload size in KB.

`--resSize`: Set response payload size in KB.

`--reqComp`: gRPC compression algorithm to use for request. Currently only supports `gzip`.

`--resComp`: gRPC compression algorithm to use for response. Currently only supports `gzip`.

`--async`: Whether to use gRPC async API. Default: false.

`--numChannels`: Number of channels to use.

`--cookie`: Cookie String to enable tracing.
