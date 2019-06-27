# End to End Checksum Client

This is an example datastore client applying end to end checksum for data
integrity.

## Usage

For this client, you can choose to use the production datastore target
("datastore.googleapis.com"), or use a test server running behind GFE.

## Build and run the example

Generate Datastore grpc client code:

```sh
./codegen.sh
```

Build:

```sh
./gradlew build
```

Run client with test service:

```sh
./gradlew run --args="my-project-id <rpc_command> my-test-service.sandbox.googleapis.com"
```

Run client with production service:

```sh
./gradlew run --args="my-project-id <rpc_command>"
```

Note: if running against production, need to setup credentials,see [Getting Started With Authentication](https://cloud.google.com/docs/authentication/getting-started) for more details.
