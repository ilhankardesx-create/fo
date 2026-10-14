# FoldLinux Prototype

Experimental Android app for installing and running an ARM64 Linux rootfs without root.

This work lives only on the `foldlinux-prototype` branch. It does not modify FoldBook or other projects.

Prototype v0.1 goals:
- SAF file picker for an ARM64 rootfs tar archive
- App-private installation (no broad storage permission)
- PRoot-based rootless Linux runtime
- Full-screen first-run/install UI
- Launch installed distro into a terminal as the first compatibility test

The build workflow clones the open-source `oonid/pr` PRoot engine and overlays the FoldLinux UI. Third-party licenses must be preserved before public redistribution.
