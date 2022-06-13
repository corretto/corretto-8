#!/bin/bash
set -x

UPSTREAM_BRANCH=$1
MERGE_BRANCH=$2

git config user.email "no-reply@amazon.com"
git config user.name "corretto-github-robot"

git checkout ${MERGE_BRANCH}
git merge -m "Merge ${UPSTREAM_BRANCH}" ${UPSTREAM_BRANCH} || exit 1

git push origin ${MERGE_BRANCH}