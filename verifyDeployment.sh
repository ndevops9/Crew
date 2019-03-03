#!/bin/bash

##############################################################
# Verify a pod deployment
# Arguments
#   PROJECT: project name
#   TOKEN: service account token
#   DC_NAME: deploy config name
##############################################################

PROJECT=$1
TOKEN=$2
DC_NAME=$3

RETRY = 2
WAIT_TIME = 480

oc login --token=$TOKEN >/dev/null 2>&1 || echo 'OpenShift login failed'

echo "[STATUS] Verifying deployment $DC_NAME ..."

# Check rollout status
# If uncessful, cancel the deployment and trigger a new one
# If RETRY > 0, recursively run this script again with RETRY-1
if [ "$RETRY" -gt 0 ]; then
  oc rollout status dc/$DC_NAME --request-timeout=$WAIT_TIME -n $PROJECT  || \
  ( echo $?; echo "Deployment failed. $RETRY retries left..."; ((RETRY--)); 
  oc rollout cancel dc/$DC_NAME -n $PROJECT;
  oc rollout latest $DC_NAME -n $PROJECT;
  ./$0 $PROJECT $TOKEN $DC_NAM; exit 0; )
else
  oc rollout status dc/$DC_NAME --request-timeout=$WAIT_TIME -n $PROJECT  || \
  (echo $?; oc logs dc/$DC_NAME -n $PROJECT;  echo "Deployment failed."; exit 1)
fi