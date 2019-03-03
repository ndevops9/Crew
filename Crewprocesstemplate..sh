#!/bin/bash

##############################################################
# Process and apply a template
# Arguments
#   PROJECT: project name
#   CLUSTER: openshift cluster
#   TOKEN: service account token
#   TEMPLATE_PATH: relative path to template in repo
##############################################################

PROJECT=$1
CLUSTER=$2
TOKEN=$3
TEMPLATE_PATH=$4

# login to OCP dev cluster
oc login --insecure-skip-tls-verify=true --token=$TOKEN $CLUSTER >/dev/null 2>&1 || echo 'OpenShift login failed'

oc patch dc/$COMPONENT_NAME \
  svc/$COMPONENT_NAME \
  route/$COMPONENT_NAME \
  is/$COMPONENT_NAME \
  --patch '{"metadata":{"annotations":{"kubectl.kubernetes.io/last-applied-configuration":""}}}' \
  -n $PROJECT || true

# Process the template and create resources
oc process -f $TEMPLATE_PATH \
  COMPONENT_NAME=$COMPONENT_NAME \
  SOURCE_REPOSITORY_REF=$GIT_BRANCH \
  SOURCE_REPOSITORY_URL=$GIT_URL \
  CONTEXT_DIR=$CONTEXT_DIR \
  $OCP_PARAMS \
  -n $PROJECT | oc apply -f - -n $PROJECT
