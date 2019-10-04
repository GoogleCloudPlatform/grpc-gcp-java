#!/bin/bash

JAR_FILE=build/libs/echo-client-1.0-SNAPSHOT.jar

gcloud compute copy-files ${JAR_FILE} echo-west-instance:/home/weiranf --project "grpc-gcp" --zone "us-west2-a"

gcloud compute copy-files ${JAR_FILE} echo-east-instance:/home/weiranf --project "grpc-gcp" --zone "us-east4-c"

