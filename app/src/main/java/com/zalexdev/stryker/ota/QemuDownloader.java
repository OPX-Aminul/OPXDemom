package com.zalexdev.stryker.ota;

import android.content.Context;

import com.zalexdev.stryker.utils.Core;

public final class QemuDownloader {

    private QemuDownloader() {}

    public static final class Bundle {
        public final RemoteManifest.Asset qemu;
        public final RemoteManifest.Asset kernel;
        public final RemoteManifest.Asset initrd;
        public final RemoteManifest.Asset libslirp;
        public final RemoteManifest.Asset rootfs;
        public final RemoteManifest.Asset libcxxShared;

        Bundle(RemoteManifest.Asset qemu, RemoteManifest.Asset kernel, RemoteManifest.Asset initrd,
               RemoteManifest.Asset libslirp, RemoteManifest.Asset rootfs,
               RemoteManifest.Asset libcxxShared) {
            this.qemu = qemu;
            this.kernel = kernel;
            this.initrd = initrd;
            this.libslirp = libslirp;
            this.rootfs = rootfs;
            this.libcxxShared = libcxxShared;
        }
    }

    public static Bundle resolve(Context context) {
        boolean armV7 = com.zalexdev.stryker.utils.Core.isArmV7();
        RemoteManifest manifest = ManifestService.fetch(context);
        RemoteManifest.RootlessAssets r = null;
        if (manifest != null) {
            if (armV7 && manifest.rootless32 != null && manifest.rootless32.isComplete()) {
                r = manifest.rootless32;
            } else if (manifest.rootless != null && manifest.rootless.isComplete()) {
                r = manifest.rootless;
            }
        }
        if (r != null) {
            RemoteManifest.Asset qemu = r.qemuForDevice();
            RemoteManifest.Asset libcxx = com.zalexdev.stryker.utils.Core.isXiaomi()
                    ? r.libcxxShared : null;
            return new Bundle(qemu, r.kernel, r.initrd, r.libslirp, r.rootfs, libcxx);
        }
        if (armV7) {
            return new Bundle(
                    new RemoteManifest.Asset(StrykerEndpoints.FALLBACK_ROOTLESS_QEMU_32, "", 0),
                    new RemoteManifest.Asset(StrykerEndpoints.FALLBACK_ROOTLESS_KERNEL_32, "", 0),
                    new RemoteManifest.Asset(StrykerEndpoints.FALLBACK_ROOTLESS_INITRD_32, "", 0),
                    new RemoteManifest.Asset(StrykerEndpoints.FALLBACK_ROOTLESS_LIBSLIRP_32, "", 0),
                    new RemoteManifest.Asset(StrykerEndpoints.FALLBACK_ROOTLESS_ROOTFS_32, "", 0),
                    null);
        }
        return new Bundle(
                new RemoteManifest.Asset(StrykerEndpoints.FALLBACK_ROOTLESS_QEMU, "", 0),
                new RemoteManifest.Asset(StrykerEndpoints.FALLBACK_ROOTLESS_KERNEL, "", 0),
                new RemoteManifest.Asset(StrykerEndpoints.FALLBACK_ROOTLESS_INITRD, "", 0),
                new RemoteManifest.Asset(StrykerEndpoints.FALLBACK_ROOTLESS_LIBSLIRP, "", 0),
                new RemoteManifest.Asset(StrykerEndpoints.FALLBACK_ROOTLESS_ROOTFS, "", 0),
                null);
    }
}
