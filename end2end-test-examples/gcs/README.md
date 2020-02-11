## Args
`--client`: Type of client to use, among `grpc`, `yoshi`, `gcsio`

`--calls`: Number of calls to execute

`--bkt`: gcs bucket name

`--obj`: gcs object name

`--dp`: whether to use DirectPath code

`--method`: make `read` or `write` calls to gcs server

`--size`: size in KB for read/write

`--conscrypt`: whether to enable Conscrypt


## Usage

Build:

```sh
./gradlew build
```

Example test run:

```sh
./gradlew run --args="--bkt=gcs-grpc-team-weiranf --obj=1kb --calls=100 --method=read"
```

```sh
./gradlew run --args="--bkt=gcs-grpc-team-weiranf --obj=upload-1kb --calls=100 --method=write --size=1"
```
