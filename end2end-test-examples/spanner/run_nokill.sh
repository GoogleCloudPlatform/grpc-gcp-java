#!/bin/bash

#echo "qps10_timeout100 start"
#mvn clean compile exec:java -Dexec.mainClass="io.grpc.spanner.TestMain" -Dexec.args="--gcpProject=directpath-prod-manual-testing --instanceId=test-instance-20671322 --databaseId=db-id-08411247 --numChannels=2 --intervalMs=100 --timeoutMs=100 --numRpcs=3000 --tcpkillMs=0 --logFilename=./nokill_qps10_timeout100 --fineLogs=true --disableConsoleLog=true"
#echo "qps10_timeout100 complete"
#
#echo "qps10_timeout200 start"
#mvn clean compile exec:java -Dexec.mainClass="io.grpc.spanner.TestMain" -Dexec.args="--gcpProject=directpath-prod-manual-testing --instanceId=test-instance-20671322 --databaseId=db-id-08411247 --numChannels=2 --intervalMs=100 --timeoutMs=200 --numRpcs=3000 --tcpkillMs=0 --logFilename=./nokill_qps10_timeout200 --fineLogs=true --disableConsoleLog=true"
#echo "qps10_timeout200 complete"
#
#echo "qps10_timeout500 start"
#mvn clean compile exec:java -Dexec.mainClass="io.grpc.spanner.TestMain" -Dexec.args="--gcpProject=directpath-prod-manual-testing --instanceId=test-instance-20671322 --databaseId=db-id-08411247 --numChannels=2 --intervalMs=100 --timeoutMs=500 --numRpcs=3000 --tcpkillMs=0 --logFilename=./nokill_qps10_timeout500 --fineLogs=true --disableConsoleLog=true"
#echo "qps10_timeout500 complete"
#
#echo "qps50_timeout100 start"
#mvn clean compile exec:java -Dexec.mainClass="io.grpc.spanner.TestMain" -Dexec.args="--gcpProject=directpath-prod-manual-testing --instanceId=test-instance-20671322 --databaseId=db-id-08411247 --numChannels=2 --intervalMs=20 --timeoutMs=100 --numRpcs=15000 --tcpkillMs=0 --logFilename=./nokill_qps50_timeout100 --fineLogs=true --disableConsoleLog=true"
#echo "qps50_timeout100 complete"
#
#echo "qps50_timeout200 start"
#mvn clean compile exec:java -Dexec.mainClass="io.grpc.spanner.TestMain" -Dexec.args="--gcpProject=directpath-prod-manual-testing --instanceId=test-instance-20671322 --databaseId=db-id-08411247 --numChannels=2 --intervalMs=20 --timeoutMs=200 --numRpcs=15000 --tcpkillMs=0 --logFilename=./nokill_qps50_timeout200 --fineLogs=true --disableConsoleLog=true"
#echo "qps50_timeout200 complete"
#
#echo "qps50_timeout500 start"
#mvn clean compile exec:java -Dexec.mainClass="io.grpc.spanner.TestMain" -Dexec.args="--gcpProject=directpath-prod-manual-testing --instanceId=test-instance-20671322 --databaseId=db-id-08411247 --numChannels=2 --intervalMs=20 --timeoutMs=500 --numRpcs=15000 --tcpkillMs=0 --logFilename=./nokill_qps50_timeout500 --fineLogs=true --disableConsoleLog=true"
#echo "qps50_timeout500 complete"
#
#echo "qps100_timeout100 start"
#mvn clean compile exec:java -Dexec.mainClass="io.grpc.spanner.TestMain" -Dexec.args="--gcpProject=directpath-prod-manual-testing --instanceId=test-instance-20671322 --databaseId=db-id-08411247 --numChannels=2 --intervalMs=10 --timeoutMs=100 --numRpcs=30000 --tcpkillMs=0 --logFilename=./nokill_qps100_timeout100 --fineLogs=true --disableConsoleLog=true"
#echo "qps100_timeout100 complete"
#
#echo "qps100_timeout200 start"
#mvn clean compile exec:java -Dexec.mainClass="io.grpc.spanner.TestMain" -Dexec.args="--gcpProject=directpath-prod-manual-testing --instanceId=test-instance-20671322 --databaseId=db-id-08411247 --numChannels=2 --intervalMs=10 --timeoutMs=200 --numRpcs=30000 --tcpkillMs=0 --logFilename=./nokill_qps100_timeout200 --fineLogs=true --disableConsoleLog=true"
#echo "qps100_timeout200 complete"

echo "qps100_timeout500 start"
mvn clean compile exec:java -Dexec.mainClass="io.grpc.spanner.TestMain" -Dexec.args="--gcpProject=directpath-prod-manual-testing --instanceId=test-instance-20671322 --databaseId=db-id-08411247 --numChannels=2 --intervalMs=10 --timeoutMs=500 --numRpcs=30000 --tcpkillMs=0 --logFilename=./nokill_qps100_timeout500 --fineLogs=true --disableConsoleLog=true"
echo "qps100_timeout500 complete"

