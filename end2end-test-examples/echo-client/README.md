# Echo Client

This is a simple client for e2e testing against our Echo service defined in [echo.proto](src/main/proto/echo.proto).

## Example

Client sends out `numRpcs` number of unary requests to `host` sequentially
with request payload size of `reqSize`, and expected response payload size of `rspSize`:

```sh
./gradlew run --args="--numRpcs=100 --reqSize=100 --resSize=100 --host=grpc-cloudapi.googleapis.com"
```

Enable gRPC compression for both request and response with gzip:

```sh
./gradlew run --args="--numRpcs=100 --reqSize=100 --resSize=100 --reqComp=gzip --resComp=gzip --host=grpc-cloudapi.googleapis.com"
```

Sending requests infinitely with 10 seconds interval between requests.

```sh
./gradlew run --args="--numRpcs=0 --interval=10000 --reqSize=100 --resSize=100 --host=grpc-cloudapi.googleapis.com"
```

Receive server-streaming responses with 10 seconds interval. Re-create the stream after each 10 responses.

```sh
./gradlew run --args="--stream=true --numRpcs=10 --interval=10000 --host=grpc-cloudapi.googleapis.com"
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

`--numRpcs`: Number of gRPC calls to send. Use 0 for sending requests infinitely with a 1-second interval between requests.

`--interval`: Interval in ms between gRPC calls. For finite number of requests the default value is 0, 1000 ms otherwise.

`--recreateChannelSeconds`: Re-create the channel after n seconds. This will not cancel an ongoing request. Not applicable with --async. Default: -1 = do not recreate the channel.

`--timeout`: Timeout for each request in ms. (Default 60 minutes).

`--warmup`: Number of warm-up RPCs to send before the test. Default: 0.

`--reqSize`: Set request payload size in KB.

`--resSize`: Set response payload size in KB.

`--resType`: Set the response type. If 0, the client sends a `EchoWithResponseSize` rpc. If [1,2,3], the client sends a `BatchEcho` and sets the `response_type` with this value in `BatchEchoRequest`. 

`--reqComp`: gRPC compression algorithm to use for request. Currently only supports `gzip`.

`--resComp`: gRPC compression algorithm to use for response. Currently only supports `gzip`.

`--async`: Whether to use gRPC async API. Default: false.

`--numChannels`: Number of channels to use.

`--cookie`: Cookie String to enable tracing.

`--stream`: Infinitely call EchoStream request with server-streaming responses for `--numRpcs` responses with `--interval`. (This overrides --async option).

`--debugHeader`: Request debug headers from the server. Default: false.

`--logConfig`: Logging configuration file in the format of logging.properties. If specified, the following logging flags have no effect.

`--fineLogs`: Extensive logging of network operations. Default: false.

`--logFilename`: Filename to log to. Default: empty, will log to console only.

`--logMaxSize`: Max log file size in bytes. Default: unlimited.

`--logMaxFiles`: If log file size is limited rotate log files and keep this number of files. Default: unlimited.
 
`--disableConsoleLog`: If logging to a file do not log to console. Default: false.
