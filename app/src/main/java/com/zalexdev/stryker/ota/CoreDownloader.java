package com.zalexdev.stryker.ota;

import android.content.Context;
import android.os.Build;

public final class CoreDownloader {

    private CoreDownloader() {
    }

    private static boolean supportsAbi(String wanted) {
        if (Build.SUPPORTED_ABIS == null) return false;
        for (String abi : Build.SUPPORTED_ABIS) {
            if (wanted.equals(abi)) return true;
        }
        return false;
    }

    public static RemoteManifest.Asset resolve(Context context) {
        boolean armV7 = supportsAbi("armeabi-v7a");
        RemoteManifest manifest = ManifestService.fetch(context);
        if (manifest != null) {
            if (armV7
                    && manifest.chroot32 != null && manifest.chroot32.isUsable()) {
                return manifest.chroot32;
            }
            if (manifest.chroot64 != null && manifest.chroot64.isUsable()) {
                return manifest.chroot64;
            }
        }
        return new RemoteManifest.Asset(
                armV7 ? StrykerEndpoints.FALLBACK_CHROOT_32 : StrykerEndpoints.FALLBACK_CHROOT_64,
                "", 0);
    }
}
