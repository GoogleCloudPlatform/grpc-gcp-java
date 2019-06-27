#!/bin/bash
cd "$(dirname "$0")"

GOOGLEAPIS_DIR=../third_party/googleapis
GRPC_JAVA_PLUGIN=./protoc-gen-grpc-java
GENERATED_PROTO_DIR=./src/main/java

rm -rf ${GENERATED_PROTO_DIR}/com/google

for p in $(find ${GOOGLEAPIS_DIR}/google/datastore ${GOOGLEAPIS_DIR}/google/api ${GOOGLEAPIS_DIR}/google/type ${GOOGLEAPIS_DIR}/google/longrunning -type f -name *.proto); do
  protoc \
    --plugin=protoc-gen-grpc-java=${GRPC_JAVA_PLUGIN} \
    --grpc-java_out=${GENERATED_PROTO_DIR} \
    --java_out=${GENERATED_PROTO_DIR} \
    --proto_path=${GOOGLEAPIS_DIR} \
    ${p}
done

