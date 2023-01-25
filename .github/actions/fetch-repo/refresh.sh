#!/bin/bash
set -x

UPSTREAM=$1
LOCAL_BRANCH=$2

REMOTE_NAME=upstream-${LOCAL_BRANCH}
git remote add ${REMOTE_NAME} ${UPSTREAM}
git fetch origin ${LOCAL_BRANCH}:${LOCAL_BRANCH} || exit 1
git fetch ${REMOTE_NAME} master:${LOCAL_BRANCH} || exit 1
git push origin ${LOCAL_BRANCH}
