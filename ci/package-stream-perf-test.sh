#!/usr/bin/env bash

POM_VERSION=$(cat pom.xml | grep -oPm1 '(?<=<version>)[^<]+')
# shellcheck disable=SC2102
if [[ $POM_VERSION == *[SNAPSHOT]* ]]
then
  CURRENT_DATE=$(date --utc '+%Y%m%d-%H%M%S')
  RELEASE_VERSION="$(cat pom.xml | grep -oPm1 '(?<=<version>)[^<]+')-$CURRENT_DATE"
  FINAL_NAME="stream-perf-test-$RELEASE_VERSION"
  SNAPSHOT="true"
else
  source ./release-versions.txt
  FINAL_NAME="stream-perf-test-$RELEASE_VERSION"
fi

mkdir packages

./mvnw clean package checksum:files gpg:sign -Dgpg.skip=false -Dmaven.test.skip -DfinalName="$FINAL_NAME" --no-transfer-progress

./mvnw test-compile exec:java -Dexec.mainClass="picocli.AutoComplete" -Dexec.classpathScope=test -Dexec.args="-f -n stream-perf-test com.rabbitmq.stream.perf.AggregatingCommandForAutoComplete" --no-transfer-progress
cp stream-perf-test_completion packages/stream-perf-test-"$RELEASE_VERSION"_completion

rm target/*.original
cp target/"$FINAL_NAME".jar packages
cp target/"$FINAL_NAME".jar.* packages

mkdir packages-latest
cp target/"$FINAL_NAME".jar packages-latest
cp target/"$FINAL_NAME".jar.* packages-latest
cp packages/*_completion packages-latest

for filename in packages-latest/*; do
  [ -f "$filename" ] || continue
  filename_without_version=$(echo "$filename" | sed -e "s/$RELEASE_VERSION/latest/g")
  mv "$filename" "$filename_without_version"
done

if [[ $SNAPSHOT = "true" ]]
then
  echo "release_name=stream-perf-test-$RELEASE_VERSION" >> $GITHUB_ENV
  echo "tag_name=v-stream-perf-test-$RELEASE_VERSION" >> $GITHUB_ENV
else
  echo "release_name=$RELEASE_VERSION" >> $GITHUB_ENV
  echo "tag_name=v$RELEASE_VERSION" >> $GITHUB_ENV
  echo "release_branch=$RELEASE_BRANCH" >> $GITHUB_ENV
fi

echo "release_version=$RELEASE_VERSION" >> $GITHUB_ENV
