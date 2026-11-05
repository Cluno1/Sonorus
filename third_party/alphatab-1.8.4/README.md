# alphaTab Android count-in patch

The app uses the published alphaTab 1.8.4 Android AAR with two local changes:

1. `AndroidAudioWorker.java` writes only real synth samples to `AudioTrack`. Upstream pads an empty read to a full silent buffer, which queues silence between count-in and score playback. The replacement reports only frames that reached the track and uses its playback head for completion progress. It flushes the paused track before a fresh count-in so old queued frames cannot interfere with resume.
2. `PatchAlphaSynthCountIn.java` changes `AlphaSynthBase.checkForFinish()` so the count-in can hand over when at most 80 ms of its generated tail remains. All other playback completion thresholds stay at zero. The remaining count-in tail consists of samples after the last click, and `resetSamples()` clears it when main playback starts.

`AndroidAudioWorker.java` also writes 800 ms of silence once after every `play()`, so a waking output path does not swallow the first clicks.

`rebuild.sh` fetches checksum-pinned upstream AAR and POM files from Maven Central, replaces these two classes, and writes the local `1.8.4-sonorus` Maven artifact. The patched source is tracked here. The upstream source and binary are [MPL-2.0](https://github.com/CoderLine/alphaTab/blob/v1.8.4/LICENSE); see the links in the patch sources.
