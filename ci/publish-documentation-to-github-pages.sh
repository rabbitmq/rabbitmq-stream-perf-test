#!/bin/bash

. $(pwd)/release-versions.txt

MESSAGE=$(git log -1 --pretty=%B)
./mvnw buildnumber:create pre-site --no-transfer-progress

RELEASE_VERSION=$(cat pom.xml | grep -oPm1 "(?<=<version>)[^<]+")

# GHA does shallow clones, so need the next 2 commands to have the gh-pages branch
git remote set-branches origin 'gh-pages'
git fetch -v

git checkout gh-pages
mkdir -p $RELEASE_VERSION/htmlsingle
cp target/generated-docs/index.html $RELEASE_VERSION/htmlsingle
git add $RELEASE_VERSION/

if [[ $LATEST == "true" ]]
  then
    if [[ $RELEASE_VERSION == *[RCM]* ]]
  then
    DOC_DIR="milestone"
  elif [[ $RELEASE_VERSION == *SNAPSHOT* ]]
  then
    DOC_DIR="snapshot"
  else
    DOC_DIR="stable"
  fi

  mkdir -p $DOC_DIR/htmlsingle
  cp target/generated-docs/index.html $DOC_DIR/htmlsingle
  git add $DOC_DIR/

fi

git commit -m "$MESSAGE"
git push origin gh-pages
git checkout main
