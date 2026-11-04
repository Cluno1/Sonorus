#!/usr/bin/env bash
set -euo pipefail

root_dir="$(cd "$(dirname "$0")/../.." && pwd)"
patch_dir="$root_dir/third_party/alphatab-1.8.4"
artifact_dir="$patch_dir/maven/net/alphatab/alphaTab/1.8.4-sonorus"
work_dir="$(mktemp -d)"
trap 'rm -rf "$work_dir"' EXIT

curl -fsSL 'https://repo.maven.apache.org/maven2/net/alphatab/alphaTab/1.8.4/alphaTab-1.8.4.aar' -o "$work_dir/upstream.aar"
curl -fsSL 'https://repo.maven.apache.org/maven2/net/alphatab/alphaTab/1.8.4/alphaTab-1.8.4.pom' -o "$work_dir/upstream.pom"
echo "657cf884c25f867b444b5f4d0b5b6e0dd614b3927423ce11e9a1cfa82f754e60  $work_dir/upstream.aar" | shasum -a 256 -c - >/dev/null
echo "d666867454333720114e89cfc8e5f6ac53fbb4df6840c1f1c3117b3653086c53  $work_dir/upstream.pom" | shasum -a 256 -c - >/dev/null
unzip -p "$work_dir/upstream.aar" classes.jar > "$work_dir/classes.jar"
mkdir -p "$work_dir/compiled/alphaTab/synth" "$artifact_dir"
javac --release 17 \
    -cp "$work_dir/classes.jar:$ANDROID_HOME/platforms/android-36/android.jar" \
    -d "$work_dir/compiled" \
    "$patch_dir/AndroidAudioWorker.java"
unzip -p "$work_dir/classes.jar" alphaTab/synth/AlphaSynthBase.class \
    > "$work_dir/compiled/alphaTab/synth/AlphaSynthBase.class"
javac --add-exports java.base/jdk.internal.org.objectweb.asm=ALL-UNNAMED \
    -d "$work_dir/compiled" "$patch_dir/PatchAlphaSynthCountIn.java"
java --add-exports java.base/jdk.internal.org.objectweb.asm=ALL-UNNAMED \
    -cp "$work_dir/compiled" PatchAlphaSynthCountIn \
    "$work_dir/compiled/alphaTab/synth/AlphaSynthBase.class"
jar uf "$work_dir/classes.jar" -C "$work_dir/compiled" alphaTab/platform/android/AndroidAudioWorker.class
jar uf "$work_dir/classes.jar" -C "$work_dir/compiled" alphaTab/synth/AlphaSynthBase.class
cp "$work_dir/upstream.aar" "$artifact_dir/alphaTab-1.8.4-sonorus.aar"
(cd "$work_dir" && zip -q -u "$artifact_dir/alphaTab-1.8.4-sonorus.aar" classes.jar)
sed 's/<version>1.8.4<\/version>/<version>1.8.4-sonorus<\/version>/' \
    "$work_dir/upstream.pom" > "$artifact_dir/alphaTab-1.8.4-sonorus.pom"
