#!/bin/sh

rm -rf _release
rm -rf target
mkdir _release

lein jar
cp target/agent-o-rama*jar _release/agent-o-rama.jar
cp VERSION _release/
cp scripts/aor _release/

sh scripts/build-ui.sh
cp -r resource _release/
rm -rf _release/resource/clj-kondo.exports

# gather all dependency jars into the lib subdir
mkdir _release/lib
cp $(lein with-profile -provided,-dev,-test classpath | tr ':' '\n' | grep -v '/src' | grep '\.jar$') _release/lib/

mkdir _release/logs

cd _release
zip ../agent-o-rama-$VERSION.zip *
cd ..
rm -rf _release
