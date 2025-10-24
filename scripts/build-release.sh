#!/bin/sh

VERSION=$(cat VERSION)

rm -rf _release
rm -rf target
mkdir _release

cp scripts/aor _release/
cp scripts/log4j2.properties _release/
cp VERSION _release/

sh scripts/build-ui.sh
lein jar
cp target/agent-o-rama*jar _release/agent-o-rama.jar

# gather all dependency jars into the lib subdir
mkdir _release/lib
cp $(lein with-profile -provided,-dev,-test classpath | tr ':' '\n' | grep -v '/src' | grep '\.jar$') _release/lib/

mkdir _release/logs

cd _release
zip -r ../agent-o-rama-$VERSION.zip *
cd ..
rm -rf _release
