# ARM32 USB Passthrough — Next Steps & Fallback Plan

> The 2026-08-17 rebuild (AOSP libusb + `LIBUSB_OPTION_NO_DEVICE_DISCOVERY` patch) did
> NOT fix the attach on itel A663L — the app still shows
> "Couldn't attach — grant USB access, then retry". This document records what to do next,
> in order, so the remaining root cause can be pinned and fixed.

## What we know
- 64-bit works perfectly with the WiFi dongle; 32-bit does not — same app code.
- The app message is GENERIC: `MainActivity.showAttachError()` (line ~411) fires on any
  `attachAsync` failure. It does NOT tell us which step failed:
    1. `usbManager.openDevice()` (Java permission / fd)
    2. `qmp.addFd(fd)` (SCM_RIGHTS fd transfer over the LocalSocket)
    3. `qmp.deviceAdd({driver:usb-host, hostdevice:/dev/fdset/N})` (QEMU opening the usbfs fd)
- The 64-bit binary is AOSP-patched QEMU (usbfs backend, no libusb). The 32-bit is QEMU
  8.2.7 with libusb. The rebuild linked AOSP libusb + set NO_DEVICE_DISCOVERY, but it
  still fails on-device. Either (a) the device did not pick up the new binary (OTA cache),
  or (b) the failure is at a different step than `libusb_wrap_sys_device`.

## Step 0 — Rule out stale binary (do this FIRST)
Before any code change, confirm the device actually runs the new QEMU:
- Force a fresh OTA pull / clear app cache / reinstall, then check the on-device
  `qemu-system-arm` sha256 == `09d92520c28b6b641b1bdc5b5a60a1fc8c13627367f7a07dc9383fe04b114c48`.
- If the device still runs the OLD sha, the rebuild never reached it — fix delivery, not code.

## Step 1 — Capture the real QMP error (most important)
The decisive missing data is the EXACT error string QEMU returns for `device_add usb-host`.
Today `QmpClient.readReturn()` only `Log.w`s QMP errors; they go to logcat, not the
exported `LogStore`. Without it we are guessing.

Action: in `app/.../engine/QmpClient.java`, inside `readReturn()` (the QMP error branch),
write the error class+desc into the app's `LogStore` (same sink as `GuestExec.logToStore`)
for `add-fd` and `device_add` replies. Then release a new APK (next versionCode; last was
607 / v1.0.6). On the next failed attach the exported log will contain e.g.:
  - `"Failed to open host usb device /dev/fdset/0"`  -> fd/permission problem (step 2/3)
  - `"Property '...' not found"` / `"Parameter '...' is missing"` -> QEMU arg mismatch
  - `"libusb: ... operation not supported"` -> libusb-wrap still failing
  - `"No 'usb-host' device registered"` -> backend not compiled in (it is, so unlikely)

## Step 2 — If the error is at addFd / fd transfer
`add-fd` uses SCM_RIGHTS over the Android `LocalSocket`. Possible issues:
- The fd passed to `addFd` must be the raw USB fd from `UsbDeviceConnection.getFileDescriptor()`,
  NOT a dup that loses the usbfs context. Verify `UsbPassthroughManager.attach()` passes the
  right fd (see `Wifi.java:211` -> `RootlessEngine.ensureUsbWifiAttached` -> `attach()`).
- Some Android versions reject SCM_RIGHTS across the socket unless the receiving process
  (the one running QEMU) holds the USB permission. Confirm QEMU runs as the same UID that
  called `openDevice`, or that the fd was transferred before QEMU dropped privileges.

## Step 3 — If the error is at device_add (open host usb device)
This is where our libusb change lives. If it STILL fails after Step 0:
- Option A: build the 32-bit binary from the SAME AOSP QEMU source the 64-bit binary came
  from (true `qemu-system-arm`, usbfs backend, no libusb). This is the highest-confidence
  fix because it reproduces the working 64-bit path exactly. Requires locating the AOSP
  emulator QEMU repo + its Android.bp / build flags for `qemu-system-arm`.
- Option B: instead of `usb-host`, try the `hostdevice=/dev/bus/usb/BBB/DDD` form (classic
  path) — but on Android the usbfs node is usually only reachable via the already-open fd,
  so this rarely helps.
- Option C: add `LIBUSB_DEBUG=4` / `libusb_set_option(LOG_LEVEL)` and dump libusb's own
  error from `op_wrap_sys_device` to see WHY the Android fd is rejected (selinux, fd type).

## Step 4 — If openDevice itself fails (Java side)
- The "grant USB access" dialog may not actually grant the fd on this OEM/Android 13.
- Check `UsbPassthroughManager.isWifiCandidate()` / `attachAllWifiDongles()` — maybe the
  dongle is filtered out before `openDevice` is ever called (wrong VID/PID, or it's matched
  as a different class).
- Some dongles need the user to pick them in the system USB-permission dialog; if that
  dialog is auto-denied on Android 13, `openDevice` returns null.

## Build/Deploy reminder
See `docs/arm32-qemu-build.md` for the full, reusable build recipe. After any QEMU change:
rebuild -> strip -> `gh release upload all-corefile qemu-system-arm --clobber` ->
update `stryker_manifest.json` `rootless.arm32.qemu` sha/size -> commit + push `main`.

After any app change (e.g. Step 1 logging): bump `versionCode`/`versionName`, build
`app-release.apk`, upload to a new GitHub release tag, update `stryker_manifest.json`
`app.*`, commit + push.
