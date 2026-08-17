# ARM32 (armeabi-v7a) QEMU Build — Current Process

> Reference for rebuilding `qemu-system-arm` that ships to 32-bit Stryker (itel A663L etc.).
> Everything below was done in the agent workspace at `/tmp/opencode`. Reuse these exact
> paths/flags to continue from where the last successful build left off.

## Environment
- Host: Linux x86_64 (agent container)
- NDK: `/opt/android-sdk/ndk/25.1.8937393` (r25)
- Target API: `24`
- Target triple: `armv7a-linux-androideabi24`
- Wrapper scripts (clang/gcc/ar/ranlib/strip/nm/pkg-config): `/tmp/opencode/wrap`
  - These wrap NDK's `llvm` toolchain so `./configure --cross-prefix=armv7a-linux-androideabi24-` works.
- Stage (libs QEMU links against): `/tmp/opencode/stage`
  - `lib/libslirp.so` (4.9.3, dynamic — shipped as `libslirp-arm.so`)
  - `lib/libfdt.a`
  - `lib/libintl.a` (proxy-libintl)
  - `lib/libz.a`, `lib/libglib-2.0.a`, `lib/libgmodule-2.0.a`, `lib/libiconv.a`, `lib/libpcre2-8.a`
  - `lib/libusb-1.0.a`  <-- **AOSP libusb**, NOT vanilla (see below)
- AOSP libusb source: `/tmp/opencode/android-libusb` (cloned)
- QEMU source tree (already configured + patched): `/tmp/opencode/build/qemu-8.2.7`
- Built binary (stripped): `/tmp/opencode/qemu-system-arm-new` (sha `09d92520...`, size `20136392`)

## Why AOSP libusb (critical)
Vanilla libusb's `libusb_wrap_sys_device()` fails on Android because there is no
`/dev/bus/usb` enumeration. AOSP's `android-libusb` supports wrapping an already-open
Android USB fd when libusb is initialised with `LIBUSB_OPTION_NO_DEVICE_DISCOVERY`.
The 64-bit binary (which works) is AOSP's patched QEMU; the 32-bit must replicate that.

## 1. Build AOSP libusb (static, armv7)
Only compile the Linux usbfs + netlink + posix threads backend. Do NOT define
`HAVE_LIBUDEV` (it counts as defined even at `0`, and would pull in the udev monitor
path that is unresolved). Do NOT compile `linux_udev.c`.

```bash
cd /tmp/opencode/android-libusb
# copy android/config.h into a build dir
mkdir -p /tmp/opencode/libusb_android_build
cp /tmp/opencode/android-libusb/android/config.h /tmp/opencode/libusb_android_build/config.h
cd /tmp/opencode/libusb_android_build
export PATH="/tmp/opencode/wrap:/opt/android-sdk/ndk/25.1.8937393/toolchains/llvm/prebuilt/linux-x86_64/bin:$PATH"
BASE=/tmp/opencode/android-libusb
INC="-I$BASE -I$BASE/libusb -I$BASE/libusb/os -I."
for s in core descriptor hotplug io strerror sync; do
  armv7a-linux-androideabi24-clang -fPIC -O2 -D__ANDROID_API__=24 $INC -c $BASE/libusb/$s.c -o $s.o || exit 1
done
for s in linux_usbfs events_posix threads_posix linux_netlink; do
  armv7a-linux-androideabi24-clang -fPIC -O2 -D__ANDROID_API__=24 $INC -c $BASE/libusb/os/$s.c -o $s.o || exit 1
done
llvm-ar rcs /tmp/opencode/stage/lib/libusb-1.0.a \
  core.o descriptor.o hotplug.o io.o strerror.o sync.o \
  linux_usbfs.o events_posix.o threads_posix.o linux_netlink.o
```

Notes:
- `__ANDROID_API__=24` redefinition warning is harmless (NDK sets it too).
- Resulting archive must contain `linux_usbfs.o` and `linux_netlink.o`, and must NOT
  reference `linux_udev_*`. Verify: `llvm-nm libusb-1.0.a | grep -c linux_udev` => `0`.
- AOSP libusb calls `__android_log_write` (in `core.c`), so the final QEMU link needs
  `-llog` (NDK `liblog.so`, a standard Android system lib — always present at runtime).

## 2. QEMU source patch (host-libusb.c)
File: `/tmp/opencode/build/qemu-8.2.7/hw/usb/host-libusb.c`, function `usb_host_init()`.
After `libusb_init(&ctx)` and inside the `#if LIBUSB_API_VERSION >= 0x01000106` block,
add (mirrors AOSP unrooted-Android init):

```c
    libusb_set_option(ctx, LIBUSB_OPTION_NO_DEVICE_DISCOVERY, NULL);
```

(The working tree already has this applied at commit time. Re-apply if you re-extract
the QEMU tarball.)

## 3. Configure QEMU 8.2.7 (arm-softmmu)
```bash
cd /tmp/opencode/build/qemu-8.2.7
export PATH="/tmp/opencode/wrap:/opt/android-sdk/ndk/25.1.8937393/toolchains/llvm/prebuilt/linux-x86_64/bin:$PATH"
export PREFIX=/tmp/opencode/stage
export TARGET=armv7a-linux-androideabi24
export PKG_CONFIG_PATH="$PREFIX/lib/pkgconfig"
./configure --prefix="$PREFIX" \
  --cross-prefix="$TARGET-" \
  --target-list=arm-softmmu \
  --disable-werror --disable-docs --disable-guest-agent \
  --disable-capstone --disable-libssh --disable-curses --disable-vnc-sasl \
  --disable-vhost-user \
  --enable-slirp --enable-libusb --enable-fdt \
  --extra-cflags="-I$PREFIX/include" \
  --extra-ldflags="-L$PREFIX/lib -llog"
```

Key flags:
- `--disable-vhost-user` — NDK r25 sysroot `linux/virtio_ring.h` clashes with QEMU's
  bundled standard-headers (`vring_packed_desc` redefinition). Not needed for TCG/Android.
- `--enable-slirp` + `--enable-fdt` + `--enable-libusb` — keep all three (networking,
  USB passthrough, and device-tree for `-M virt`).
- `--extra-ldflags "... -llog"` — resolves AOSP libusb's `__android_log_write`.

## 4. Build + strip
```bash
cd /tmp/opencode/build/qemu-8.2.7
make -j"$(nproc)"            # or: make qemu-system-arm
BIN=$(find . -name qemu-system-arm -type f | grep -v '\.o' | head -1)
/opt/android-sdk/ndk/25.1.8937393/toolchains/llvm/prebuilt/linux-x86_64/bin/llvm-strip "$BIN"
cp "$BIN" /tmp/opencode/qemu-system-arm-new
sha256sum /tmp/opencode/qemu-system-arm-new
```

## 5. Verify the binary
```bash
strings qemu-system-arm-new | grep -E "usb-host"      # must show "usb-host"
strings qemu-system-arm-new | grep -i "no device discovery"   # patch present
# NEEDED libs (expect liblog.so, libslirp.so, libm/libdl/libc):
llvm-readelf -d qemu-system-arm-new | grep NEEDED
```
The binary is arm32 and can ONLY be executed on the Android device (not on the x86 host).
Local boot/USB tests use the host's x86 qemu (`/usr/bin/qemu-system-arm`) with the same
command line; they verify boot/slirp/9p but NOT the Android USB path.

## 6. Deploy (GitHub release `all-corefile`, repo OPX-Aminul/OPXDemom)
```bash
cd /tmp/opencode
cp qemu-system-arm-new qemu-system-arm     # asset name MUST be qemu-system-arm
gh release upload all-corefile qemu-system-arm --repo OPX-Aminul/OPXDemom --clobber
# remove any stray asset if a wrong name was uploaded:
gh release delete-asset all-corefile <wrong-name> --repo OPX-Aminul/OPXDemom -y
```
`gh` is authenticated as `monkeycode-global[bot]` in the agent container.

Then update `stryker_manifest.json` -> `rootless.arm32.qemu.sha256` / `.size` to the new
values, commit, and `git push origin main`.

## 7. App-side (DO NOT change attach logic)
The app's USB attach path (`UsbPassthroughManager.attach()` ->
`usbManager.openDevice` -> `qmp.addFd(fd)` -> `qmp.deviceAdd({driver:usb-host,...})`)
must stay identical 32/64-bit. Only the QEMU binary is the fix surface. Keep it that way.
