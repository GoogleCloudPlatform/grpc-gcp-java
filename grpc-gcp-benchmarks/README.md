## Prerequisite
Access to the GCP project "cloudprober-test" is required.

```
export GOOGLE_APPLICATION_CREDENTIALS=path/to/you/key.json
export GCP_PROJECT_ID=cloudprober-test
```

## Load tests

Run Bigtable load test:

```sh
./gradlew bigtableLoadTest
```

Run Spanner load test:

```sh
./gradlew spannerLoadTest
```
## Spanner benchmarks

Run Spanner benchmark tests:

```sh
./gradlew spannerBenchmark --args="--gcp=true --thread=3 --rpc=100"
```
There are four available arguments: 
* gcp: whether or not gRPC-GCP extension ManagedChannel.
* thread: the number of threads run concurrently
* rpc: the number of rpcs run concurrently
* client: three available:
    * "v1.SpannerGrpc": auto generated gRPC stubs.
    * "v1.SpannerClient": generated by gapic. The gax layer is included in this client.
    * "SpannerClinet": (default)the top layer Spanner client implemented by Spanner team and are in use by customers.

The normal cloud Spanner client:
```sh
./gradlew spannerClientBenchmark --args="--thread=3 --rpc=100"
```