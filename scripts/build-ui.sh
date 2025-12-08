#!/bin/sh

# Set NODE_ENV to production for React production builds
export NODE_ENV=production

npm i
rm -rf resource/public
mkdir -p resource/public
cp -r resource/assets/* resource/public
# Use 'release' instead of 'compile' for production builds
# This automatically enables advanced optimizations and NODE_ENV=production for npm
lein with-profile +ui run -m shadow.cljs.devtools.cli release :frontend
