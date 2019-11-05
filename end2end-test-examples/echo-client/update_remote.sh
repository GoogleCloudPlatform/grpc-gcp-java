#!/bin/bash

JAR_FILE=build/libs/echo-client-2.1-SNAPSHOT.jar

gcloud compute scp ${JAR_FILE} weiranf-west-bdp-on:/home/weiranf --project "grpc-testing" --zone "us-west2-c"

gcloud compute scp ${JAR_FILE} weiranf-west-bdp-off:/home/weiranf --project "grpc-testing" --zone "us-west2-c"

gcloud compute scp ${JAR_FILE} weiranf-cfe-bdp-on:/home/weiranf --project "grpc-testing" --zone "us-east2-a"

gcloud compute scp ${JAR_FILE} weiranf-cfe-bdp-off:/home/weiranf --project "grpc-testing" --zone "us-east2-a"

