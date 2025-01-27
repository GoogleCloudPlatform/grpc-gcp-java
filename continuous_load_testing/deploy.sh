#!/bin/bash
WORKING_DIR=$(pwd)
ROOT_DIR=$(dirname $(dirname $(pwd)))
echo $WORKING_DIR
echo $ROOT_DIR
kubectl delete deployment client-java-manual
gradle clean
gradle build
gradle installDist
cd $ROOT_DIR/grpc-java && gradle :grpc-rls:build -x test && cd $WORKING_DIR
cd $ROOT_DIR/grpc-java && gradle :grpc-api:build -x test && cd $WORKING_DIR
cp $ROOT_DIR/grpc-java/rls/build/libs/grpc-rls-1.70.0-SNAPSHOT.jar $ROOT_DIR/grpc-gcp-java/continuous_load_testing/build/install/continuous_load_testing/lib/grpc-rls-1.68.1.jar
cp $ROOT_DIR/grpc-java/api/build/libs/grpc-api-1.70.0-SNAPSHOT.jar $ROOT_DIR/grpc-gcp-java/continuous_load_testing/build/install/continuous_load_testing/lib/grpc-api-1.68.1.jar
docker system prune -af
docker build --progress=plain --no-cache -t directpathgrpctesting-client-java-manual .
docker tag directpathgrpctesting-client-java-manual us-docker.pkg.dev/directpathgrpctesting-client/directpathgrpctesting-client/directpathgrpctesting-client-java-manual
gcloud artifacts docker images delete us-docker.pkg.dev/directpathgrpctesting-client/directpathgrpctesting-client/directpathgrpctesting-client-java-manual --delete-tags -q
docker push us-docker.pkg.dev/directpathgrpctesting-client/directpathgrpctesting-client/directpathgrpctesting-client-java-manual
gcloud container clusters get-credentials cluster-1 --region us-east7 --project directpathgrpctesting-client
kubectl apply -f client-java-manual.yaml
