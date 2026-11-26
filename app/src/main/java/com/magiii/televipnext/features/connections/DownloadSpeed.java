package com.magiii.televipnext.features.connections;

import com.magiii.televipnext.Class.ClassLoad;
import com.magiii.televipnext.Class.ClassNames;
import com.magiii.televipnext.Configs.ConfigPreferences;
import com.magiii.televipnext.base.BaseMethodHook;
import com.magiii.televipnext.hooks.HMethod;
import com.magiii.televipnext.language.Keys;
import com.magiii.televipnext.logging.Logger;
import com.magiii.televipnext.obfuscate.Obfuscate;
import com.magiii.televipnext.virtuals.messenger.FileLoadOperation;

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
