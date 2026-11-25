package com.my.televip.features.connections;

import com.my.televip.Class.ClassLoad;
import com.my.televip.Class.ClassNames;
import com.my.televip.Configs.ConfigPreferences;
import com.my.televip.base.BaseMethodHook;
import com.my.televip.hooks.HMethod;
import com.my.televip.language.Keys;
import com.my.televip.logging.Logger;
import com.my.televip.obfuscate.Obfuscate;
import com.my.televip.virtuals.messenger.FileLoadOperation;

public class DownloadSpeed {

    public static final int MODE_OFF = 0;
    public static final int MODE_FAST = 1;
    public static final int MODE_ULTRA = 2;

    public static boolean isEnable = false;

    /**
     * Returns the selected download boost mode (0=Off, 1=Fast, 2=Ultra).
     */
    public static int getMode() {
        int mode = ConfigPreferences.getInt(Keys.DownloadSpeed + "Int");
        if (mode < MODE_OFF) return MODE_OFF;
        if (mode > MODE_ULTRA) return MODE_ULTRA;
        return mode;
    }

    public static void init() {
        try {
            if (!isEnable) {
                isEnable = true;

                if (ClassLoad.getClass(ClassNames.FILE_LOAD_OPERATION) != null) {
                    HMethod.hookMethod(ClassLoad.getClass(ClassNames.FILE_LOAD_OPERATION), Obfuscate.getMethodName("FileLoadOperation", "updateParams"), new BaseMethodHook() {
                        @Override
                        protected void afterMethod(MethodHookParam param) {
                            int mode = getMode();
                            if (mode <= MODE_OFF) return;

                            FileLoadOperation fileLoadOperation = new FileLoadOperation(param.thisObject);

                            int downloadChunkSizeBig = (mode == MODE_ULTRA) ? (1024 * 1024) : (1024 * 512);
                            int maxDownloadRequests = (mode == MODE_ULTRA) ? 12 : 8;

                            long defaultMaxFileSize = 1024L * 1024L * 2000L;
                            int maxCdnParts = (int) (defaultMaxFileSize / downloadChunkSizeBig);

                            fileLoadOperation.setDownloadChunkSizeBig(downloadChunkSizeBig);
                            fileLoadOperation.setMaxDownloadRequests(maxDownloadRequests);
                            fileLoadOperation.setMaxDownloadRequestsBig(maxDownloadRequests);
                            fileLoadOperation.setMaxCdnParts(maxCdnParts);
                        }
                    });
                }
            }
        } catch (Throwable t) {
            Logger.e(t);
        }
    }

}
