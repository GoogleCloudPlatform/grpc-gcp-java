#!/bin/bash

# curl commands for reading
ntimes 100 -- curl -o /dev/null -H 'Cache-Control: no-cache' -s -w "%{time_total}\n" -H "Authorization: Bearer `gcloud auth print-access-token`" 'https://www.googleapis.com/download/storage/v1/b/gcs-grpc-team-weiranf/o/100kb?alt=media'

# curl commands for uploading
ntimes 100 -- curl -X POST --data-binary @1gb.txt -H 'Cache-Control: no-cache' -s -w "%{time_total}\n" -H "Authorization: Bearer `gcloud auth print-access-token`" 'https://www.googleapis.com/upload/storage/v1/b/gcs-grpc-team-weiranf/o?uploadType=media&name=curl-1gb'
