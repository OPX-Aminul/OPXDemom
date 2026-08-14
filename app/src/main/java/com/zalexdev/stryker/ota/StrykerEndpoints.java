package com.zalexdev.stryker.ota;

public final class StrykerEndpoints {

    public static final String GITHUB_REPO = "https://github.com/OPX-Aminul/OPXDemom";

    public static final String MANIFEST_URL =
            "https://raw.githubusercontent.com/OPX-Aminul/OPXDemom/main/stryker_manifest.json";

    public static final String FALLBACK_CHROOT_64 =
            "https://github.com/OPX-Aminul/OPXDemom/releases/download/all-corefile/chroot64-debian.tar.gz";

    public static final String FALLBACK_CHROOT_32 =
            "https://github.com/OPX-Aminul/OPXDemom/releases/download/all-corefile/chroot64-armhf-debian.tar.gz";

    private static final String ROOTLESS_BASE =
            "https://github.com/OPX-Aminul/OPXDemom/releases/download/all-corefile/";
    public static final String FALLBACK_ROOTLESS_QEMU     = ROOTLESS_BASE + "qemu-system-aarch64";
    public static final String FALLBACK_ROOTLESS_KERNEL   = ROOTLESS_BASE + "Image";
    public static final String FALLBACK_ROOTLESS_LIBSLIRP = ROOTLESS_BASE + "libslirp.so";
    public static final String FALLBACK_ROOTLESS_INITRD   = ROOTLESS_BASE + "initrd.img";
    public static final String FALLBACK_ROOTLESS_ROOTFS   = ROOTLESS_BASE + "rootfs.imgz";

    public static final String FALLBACK_ROOTLESS_QEMU_32     = ROOTLESS_BASE + "qemu-system-arm";
    public static final String FALLBACK_ROOTLESS_KERNEL_32   = ROOTLESS_BASE + "Image-armv7";
    public static final String FALLBACK_ROOTLESS_LIBSLIRP_32 = ROOTLESS_BASE + "libslirp-arm.so";
    public static final String FALLBACK_ROOTLESS_INITRD_32   = ROOTLESS_BASE + "initrd-armv7.img";
    public static final String FALLBACK_ROOTLESS_ROOTFS_32   = ROOTLESS_BASE + "rootfs-armv7.imgz";

    public static final String PREFS = "stryker_ota";

    private StrykerEndpoints() {
    }
}
