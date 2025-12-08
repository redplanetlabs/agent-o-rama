#!/bin/sh

# Set NODE_ENV to production for React production builds
export NODE_ENV=production

npm i
rm -rf resource/public
mkdir -p resource/public
cp -r resource/assets/* resource/public
lein with-profile +ui run -m shadow.cljs.devtools.cli --npm compile :frontend
