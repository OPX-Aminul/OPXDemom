#!/usr/bin/env bash
#
# Patch a bare armv7 Stryker rootfs (rootfs-armv7.img, plain ext4 raw image)
# into a bootable guest by injecting the pieces that were missing from the
# published rootfs-armv7.imgz (release "all-corefile"):
#
#   * systemd-udevd + rules + units        -> device units resolve fast, boot
#                                            no longer stalls 90s on dev-ttyAMA0
#   * systemd-networkd + 10-eth.network    -> eth0 gets DHCP (10.0.2.15 under
#                                             QEMU user networking), so the
#                                             agent ports 1050-1052 are reachable
#   * stryker-agentd / stryker-ptyd        -> from app asset stryker-guest-core.tar
#   * stryker-agent.service (+ enablement) -> autostart of the guest agent
#   * serial-getty@ttyAMA0 autologin      -> root console login for the app's
#   autologin.conf                           serial-console bootstrap path
#
# Usage (as root, on a machine with loop-device access):
#   ./patch-armv7-rootfs.sh rootfs-armv7.img [path/to/stryker-guest-core.tar]
#
# After building, repackage and publish:
#   gzip -k -9 -f rootfs-armv7.img   # -> rootfs-armv7.imgz
#   sha256sum rootfs-armv7.imgz      # -> rootless/arm32/rootfs in stryker_manifest.json
#
set -euo pipefail

IMG="${1:?usage: $0 rootfs-armv7.img [stryker-guest-core.tar]}"
CORE_TAR="${2:-$(cd "$(dirname "$0")/.." && pwd)/app/src/main/assets/rootless/stryker-guest-core.tar}"

[ "$(id -u)" -eq 0 ] || { echo "must run as root" >&2; exit 1; }
[ -f "$IMG" ] || { echo "image not found: $IMG" >&2; exit 1; }
[ -f "$CORE_TAR" ] || { echo "guest core tar not found: $CORE_TAR" >&2; exit 1; }

WORK="$(mktemp -d /tmp/patch-armv7.XXXXXX)"
MNT="$WORK/mnt"
mkdir -p "$MNT"
LOOP=""

cleanup() {
    sync || true
    if [ -n "$LOOP" ]; then
        umount "$MNT" 2>/dev/null || true
        losetup -d "$LOOP" 2>/dev/null || true
    fi
    rm -rf "$WORK"
}
trap cleanup EXIT

fail() { echo "ERROR: $*" >&2; exit 1; }

echo "==> reading systemd version from $IMG"
SYSVER="$(debugfs -R 'cat /var/lib/dpkg/status' "$IMG" 2>/dev/null \
    | awk '/^Package: systemd$/{f=1} f && /^Version:/{print $2; exit}')"
[ -n "$SYSVER" ] || fail "could not determine systemd version (is debugfs available?)"
echo "    systemd $SYSVER"

DEB="$WORK/udev_armhf.deb"
echo "==> downloading udeb $SYSVER (armhf) from deb.debian.org"
curl -fsSL --retry 3 --max-time 180 -o "$DEB" \
    "http://deb.debian.org/debian/pool/main/s/systemd/udev_${SYSVER}_armhf.deb" \
    || fail "deb download failed"

UROOT="$WORK/udev"
mkdir -p "$UROOT"
dpkg-deb -x "$DEB" "$UROOT" || fail "deb extraction failed"
[ -x "$UROOT/usr/bin/udevadm" ] || fail "udevadm missing from package"

echo "==> extracting guest agent from $CORE_TAR"
tar -xf "$CORE_TAR" -C "$WORK" ./usr/local/sbin/stryker-agentd ./usr/local/sbin/stryker-ptyd

LOOP="$(losetup -f)"
losetup "$LOOP" "$IMG"
mount -o rw "$LOOP" "$MNT"

echo "==> injecting systemd-udevd"
install -m 0755 "$UROOT/usr/bin/udevadm"      "$MNT/usr/bin/udevadm"
install -m 0755 "$UROOT/usr/bin/systemd-hwdb" "$MNT/usr/bin/systemd-hwdb"
ln -sf ../../bin/udevadm "$MNT/usr/lib/systemd/systemd-udevd"
for u in systemd-udevd.service systemd-udevd-control.socket systemd-udevd-kernel.socket \
         systemd-udev-trigger.service systemd-udev-settle.service \
         systemd-udev-load-credentials.service; do
    install -m 0644 "$UROOT/usr/lib/systemd/system/$u" "$MNT/usr/lib/systemd/system/$u"
done
mkdir -p "$MNT/usr/lib/systemd/system/systemd-udevd.service.d"
install -m 0644 "$UROOT/usr/lib/systemd/system/systemd-udevd.service.d/syscall-architecture.conf" \
    "$MNT/usr/lib/systemd/system/systemd-udevd.service.d/syscall-architecture.conf"
install -m 0644 "$UROOT"/usr/lib/udev/rules.d/*.rules "$MNT/usr/lib/udev/rules.d/"
mkdir -p "$MNT/usr/lib/systemd/system/sysinit.target.wants" \
         "$MNT/usr/lib/systemd/system/sockets.target.wants"
ln -sf ../systemd-udevd.service       "$MNT/usr/lib/systemd/system/sysinit.target.wants/systemd-udevd.service"
ln -sf ../systemd-udev-trigger.service "$MNT/usr/lib/systemd/system/sysinit.target.wants/systemd-udev-trigger.service"
ln -sf ../systemd-udevd-control.socket "$MNT/usr/lib/systemd/system/sockets.target.wants/systemd-udevd-control.socket"
ln -sf ../systemd-udevd-kernel.socket  "$MNT/usr/lib/systemd/system/sockets.target.wants/systemd-udevd-kernel.socket"
ln -sf systemd-udevd.service "$MNT/usr/lib/systemd/system/udev.service"

echo "==> injecting network config (systemd-networkd DHCP)"
mkdir -p "$MNT/etc/systemd/network"
printf '[Match]\nName=eth0 en*\n[Network]\nDHCP=yes\n' > "$MNT/etc/systemd/network/10-eth.network"
mkdir -p "$MNT/etc/systemd/system/multi-user.target.wants"
ln -sf /usr/lib/systemd/system/systemd-networkd.service \
    "$MNT/etc/systemd/system/multi-user.target.wants/systemd-networkd.service"

echo "==> injecting guest agent + service"
mkdir -p "$MNT/usr/local/sbin"
install -m 0755 "$WORK/usr/local/sbin/stryker-agentd" "$MNT/usr/local/sbin/stryker-agentd"
install -m 0755 "$WORK/usr/local/sbin/stryker-ptyd"   "$MNT/usr/local/sbin/stryker-ptyd"
cat > "$MNT/etc/systemd/system/stryker-agent.service" <<'UNIT'
[Unit]
Description=Stryker guest agent (command + terminal servers)
After=network.target

[Service]
ExecStartPre=/bin/sh -c 'mkdir -p /sdcard/Stryker/hs /sdcard/Stryker/captured'
ExecStart=/usr/local/sbin/stryker-agentd
Restart=always
RestartSec=2

[Install]
WantedBy=multi-user.target
UNIT
ln -sf /etc/systemd/system/stryker-agent.service \
    "$MNT/etc/systemd/system/multi-user.target.wants/stryker-agent.service"

echo "==> injecting serial-getty@ttyAMA0 autologin"
mkdir -p "$MNT/etc/systemd/system/serial-getty@ttyAMA0.service.d"
cat > "$MNT/etc/systemd/system/serial-getty@ttyAMA0.service.d/autologin.conf" <<'DROPIN'
[Service]
ExecStart=
ExecStart=-/sbin/agetty --autologin root --keep-baud 115200,38400,9600 %I vt220
DROPIN

echo "==> trimming boot-time work (faster boot on slow emulated CPUs)"
# On low-end phones QEMU TCG is very slow, so shave the guest's boot work:
# disable services that add time but are not needed for the Stryker session.
mkdir -p "$MNT/etc/systemd/system"
DISABLE_UNITS="
    apt-daily.service apt-daily.timer apt-daily-upgrade.service apt-daily-upgrade.timer
    man-db.timer mlocate.timer e2scrub_reap.service
    systemd-networkd-wait-online.service
    systemd-timesyncd.service
    remote-fs.target
    getty@tty1.service getty@tty2.service getty@tty3.service getty@tty4.service getty@tty5.service getty@tty6.service
    bluetooth.service
"
for u in $DISABLE_UNITS; do
    if [ -e "$MNT/usr/lib/systemd/system/$u" ] || [ -e "$MNT/lib/systemd/system/$u" ] \
       || [ -e "$MNT/etc/systemd/system/$u" ]; then
        ln -sf /dev/null "$MNT/etc/systemd/system/$u" 2>/dev/null || true
    fi
done
# systemd-networkd-wait-online can block network-online.target; we don't need it.
rm -f "$MNT/etc/systemd/system/network-online.target.wants/systemd-networkd-wait-online.service" \
      2>/dev/null || true
# Quiet the console further to reduce serial churn under emulation.
if [ -f "$MNT/etc/systemd/system.conf" ]; then
    grep -q '^#LogLevel' "$MNT/etc/systemd/system.conf" \
        && sed -i 's/^#LogLevel=.*/LogLevel=warning/' "$MNT/etc/systemd/system.conf" || \
        echo 'LogLevel=warning' >> "$MNT/etc/systemd/system.conf"
fi

sync
umount "$MNT"
losetup -d "$LOOP"
LOOP=""

echo "==> filesystem check"
e2fsck -fy "$IMG" >/dev/null 2>&1 || e2fsck -fn "$IMG" || fail "ext4 check failed"

echo
echo "Patched image ready: $IMG"
echo
echo "Next steps:"
echo "  gzip -k -9 -f \"$IMG\"          # produces rootfs-armv7.imgz"
echo "  sha256sum \"$IMG\".gz           # update rootless/arm32/rootfs in stryker_manifest.json"
