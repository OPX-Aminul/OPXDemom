package com.zalexdev.stryker.engine;

import android.content.Context;

import com.zalexdev.stryker.utils.Core;

import java.io.File;

public final class RootlessPaths {

    private RootlessPaths() {}

    public static File base(Context c) {
        return new File(c.getFilesDir(), "rootless");
    }

    public static File qemuBin(Context c)   {
        if (com.zalexdev.stryker.utils.Core.isXiaomi()) {
            return new File(base(c), "qemu-system-aarch64-xiaomi");
        }
        return new File(base(c), Core.isArmV7() ? "qemu-system-arm" : "qemu-system-aarch64");
    }
    public static File libslirp(Context c)  {
        // The qemu binary links against "libslirp.so" (its DT_NEEDED entry) regardless of ABI,
        // so the file must always be named libslirp.so. On armv7 the download asset is called
        // libslirp-arm.so but it is stored under the linked name, matching the 64-bit layout.
        return new File(base(c), "libslirp.so");
    }
    public static File kernel(Context c)    {
        return new File(base(c), Core.isArmV7() ? "Image-armv7" : "Image");
    }
    public static File initrd(Context c)    {
        return new File(base(c), Core.isArmV7() ? "initrd-armv7.img" : "initrd.img");
    }
    public static File rootfs(Context c)    {
        return new File(base(c), Core.isArmV7() ? "rootfs-armv7.img" : "rootfs.img");
    }
    public static File rootfsGz(Context c)  {
        return new File(base(c), Core.isArmV7() ? "rootfs-armv7.img.gz" : "rootfs.img.gz");
    }

    public static File qmpSock(Context c)   { return new File(base(c), "qmp.sock"); }
    public static File serialSock(Context c){ return new File(base(c), "serial.sock"); }
    public static File serialLog(Context c){ return new File(base(c), "serial.log"); }
    public static File termSock(Context c)  { return new File(base(c), "term.sock"); }
    public static File bootLog(Context c)   { return new File(base(c), "boot.log"); }

    public static final int GUEST_EXEC_PORT = 1050;
    public static final int HOST_EXEC_PORT  = 1050;
    public static final String HOST_LOOPBACK = "127.0.0.1";

    public static final int GUEST_TERM_PORT = 1051;
    public static final int HOST_TERM_PORT  = 1051;

    public static final int GUEST_PTY_PORT = 1052;
    public static final int HOST_PTY_PORT  = 1052;

    public static final int GUEST_SSH_PORT = 22;
    public static final int HOST_SSH_PORT  = 2222;

    public static File activeFlag(Context c) {
        return new File(base(c), ".active");
    }
    public static final String ACTIVE_FLAG_PATH =
            "/data/data/com.zalexdev.stryker/files/rootless/.active";
}
