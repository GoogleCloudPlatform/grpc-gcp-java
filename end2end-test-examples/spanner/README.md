# Spanner test client

This is a simple E2E test client for java-spanner over GFE. The spanner client will:
1. create or use an instance (/google.spanner.admin.instance.v1.InstanceAdmin/CreateInstance);
2. create or use a database (/google.spanner.admin.database.v1.DatabaseAdmin/CreateDatabase);
3. evenly create sessions on channels (/google.spanner.v1.Spanner/BatchCreateSessions);
4. do warm up by performing 10 write RPCs (/google.spanner.v1.Spanner/BeginTransaction and /google.spanner.v1.Spanner/Commit) and 50 read RPCs (/google.spanner.v1.Spanner/StreamingRead) synchronously, and measure the median and maximum latency of read RPCs;
5. do real operation by performing `numRpcs` times of read RPCs asynchronously. The time interval between two neighboring RPCs obeys the exponential distribution, whose expectation is `intervalMs`. QPS can be estimated as the reciprocal of `intervalMs`. There are three cases:
    a). Only run RPCs;
    b). Run RPCs with `tcpkill`: A side thread will run `tcpkill` to break the TCP connection between the client and GFE every `tcpkillMs` ms;
    c). Run RPCs with `iptables`: A side thread will run `iptables` to blackhole
    packets from GFE;
6. delete the instance if it is created in step 1 (/google.spanner.admin.instance.v1.InstanceAdmin/DeleteInstance), which implicitly delete the created database;
7. delete the created sessions (/google.spanner.v1.Spanner/DeleteSession).

## Args

`--gcpProject`: String, the GCP project used by the Spanner client. Default value: "".

`--instanceId`: String, the spanner instance Id. If not set, the test will create a new instance. Default value: "".

`--databaseId`: String, the spanner database Id. If not set, the test will create a new database. Default value: "".

`--endpoint`: String, the test endpoint. Deafult value: "spanner.googleapis.com:443".

`--numChannels`: int, the number of TCP connections that the client will create to connect to GFE. Default value: 4.

`--numRpcs`: int, the number of RPCs that the client will send. Default value: 1.

`--intervalMs`: int, the expectation of the expoenential distribution. Default value: 0.

`--tcpkillMs`: int, the interval between two `tcpkill` operations. If not set, `tcpkill` will not be run. Also notice `fineLogs` must be set to true and `logFilename` must be given to use `tcpkill`. Default value: 0.

`--iptablesMs`: int, the interval between two `iptables` operations. If not
set, `iptables` will not be run. Also notice `fineLogs` must be set to true and `logFilename` must be given to use `iptables`. Default value: 0.

`--iptablesDurationMs`: int, the duration that `iptables` will blackhole a port,
  after that the port will be whitelisted again. The minimal value is 5s. Only
  work if `iptableMs` has been set.

`--timeoutMs`: int, timeout of a RPC in ms. Default value: 1000.

`--fineLogs`: Boolean, extensive logging of network operations. Default value: false.

`--logConfig`: String, logging configuration file in the format of logging.properties. If specified, the following logging flags have no effect. Default value: "".

`--logFilename`: String, filename to log to. Default value: empty, will log to console only.

`--logMaxSize`: int, max log file size in bytes. Default value: unlimited.

`--logMaxFiles`: int, if log file size is limited rotate log files and keep this number of files. Default value: unlimited.

`--disableConsoleLog`: Boolean, disable log to console. Default value: false.

## Example

```sh
mvn clean compile exec:java -Dexec.mainClass="io.grpc.spanner.TestMain" -Dexec.args="--gcpProject=directpath-prod-manual-testing --instanceId=uber-debug-instance --databaseId=db-id-74750157 --numChannels=2 --intervalMs=100 --timeoutMs=150 --numRpcs=1000 --tcpkillMs=1000 --logFilename=./mohanli --fineLogs=true"
```
