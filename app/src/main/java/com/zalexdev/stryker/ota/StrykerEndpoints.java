package com.zalexdev.stryker.ota;

public final class StrykerEndpoints {

    public static final String GITHUB_REPO = "https://github.com/mahmudabegum8859-design/OPXDemom";

    public static final String MANIFEST_URL =
            "https://raw.githubusercontent.com/mahmudabegum8859-design/OPXDemom/main/stryker_manifest.json";

    public static final String FALLBACK_CHROOT_64 =
            "https://github.com/mahmudabegum8859-design/OPXDemom/releases/download/all-corefile/chroot64-debian.tar.gz";

    private static final String ROOTLESS_BASE =
            "https://github.com/mahmudabegum8859-design/OPXDemom/releases/download/all-corefile/";
    public static final String FALLBACK_ROOTLESS_QEMU     = ROOTLESS_BASE + "qemu-system-aarch64";
    public static final String FALLBACK_ROOTLESS_KERNEL   = ROOTLESS_BASE + "Image";
    public static final String FALLBACK_ROOTLESS_LIBSLIRP = ROOTLESS_BASE + "libslirp.so";
    public static final String FALLBACK_ROOTLESS_INITRD   = ROOTLESS_BASE + "initrd.img";
    public static final String FALLBACK_ROOTLESS_ROOTFS   = ROOTLESS_BASE + "rootfs.imgz";

    public static final String PREFS = "stryker_ota";

    private StrykerEndpoints() {
    }
}
