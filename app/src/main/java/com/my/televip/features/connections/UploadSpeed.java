package com.my.televip.features.connections;

import com.my.televip.Class.ClassLoad;
import com.my.televip.Class.ClassNames;
import com.my.televip.Configs.ConfigManager;
import com.my.televip.base.BaseMethodHook;
import com.my.televip.hooks.HMethod;
import com.my.televip.logging.Logger;
import com.my.televip.obfuscate.Obfuscate;
import com.my.televip.virtuals.messenger.FileUploadOperation;

/**
 * Boosts upload throughput by increasing standard upload chunk size and parallel connections
 * right before total parts count and the read buffer are initialized.
 */
public class UploadSpeed {

    private static final int BOOST_MIN_CHUNK_BYTES = 256 * 1024;
    private static final int DEFAULT_MIN_CHUNK_BYTES = 64 * 1024;
    private static final int MAX_BUFFER_KBYTES = 8192;

    public static boolean isEnable = false;

    public static void init() {
        try {
            if (!isEnable) {
                isEnable = true;

                Class<?> uploadOpClass = ClassLoad.getClass(ClassNames.FILE_UPLOAD_OPERATION);
                if (uploadOpClass != null) {
                    HMethod.hookMethod(
                            uploadOpClass,
                            Obfuscate.getMethodName("FileUploadOperation", "calcTotalPartsCount"),
                            new BaseMethodHook() {
                                @Override
                                protected void beforeMethod(MethodHookParam param) {
                                    if (ConfigManager.uploadSpeed == null || !ConfigManager.uploadSpeed.isEnable()) {
                                        return;
                                    }
                                    FileUploadOperation op = new FileUploadOperation(param.thisObject);
                                    // Never mutate uploadChunkSize after readBuffer has already been allocated.
                                    if (op.getReadBuffer() != null) {
                                        return;
                                    }

                                    int chunkBytes = op.getUploadChunkSize();
                                    if (chunkBytes >= DEFAULT_MIN_CHUNK_BYTES && chunkBytes < BOOST_MIN_CHUNK_BYTES) {
                                        op.setUploadChunkSize(BOOST_MIN_CHUNK_BYTES);
                                        int oldMaxReq = op.getMaxRequestsCount();
                                        int targetMaxReq = Math.max(1, MAX_BUFFER_KBYTES / (BOOST_MIN_CHUNK_BYTES / 1024));
                                        // Clamp downward only so encrypted uploads' freeRequestIvs never underflows.
                                        op.setMaxRequestsCount(oldMaxReq > 0 ? Math.min(oldMaxReq, targetMaxReq) : targetMaxReq);
                                    }

                                    int[] reqPerConn = op.getUploadRequestsPerConnection();
                                    int maxConn = (reqPerConn != null && reqPerConn.length > 0) ? Math.min(8, reqPerConn.length) : 4;
                                    if (op.getUploadConnectionsCount() < maxConn) {
                                        op.setUploadConnectionsCount(maxConn);
                                    }
                                }
                            }
                    );
                }
            }
        } catch (Throwable t) {
            Logger.e(t);
        }
    }
}
