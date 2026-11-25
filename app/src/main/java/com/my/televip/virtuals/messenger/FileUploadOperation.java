package com.my.televip.virtuals.messenger;

import com.my.televip.obfuscate.Obfuscate;

import de.robv.android.xposed.XposedHelpers;

/**
 * Virtual wrapper around Telegram's obfuscated FileUploadOperation instance.
 */
public class FileUploadOperation {

    private final Object fileOperation;

    public FileUploadOperation(Object fileOperation) {
        this.fileOperation = fileOperation;
    }

    public byte[] getReadBuffer() {
        return (byte[]) XposedHelpers.getObjectField(fileOperation, Obfuscate.getFieldName("FileUploadOperation", "readBuffer"));
    }

    public int getUploadChunkSize() {
        return XposedHelpers.getIntField(fileOperation, Obfuscate.getFieldName("FileUploadOperation", "uploadChunkSize"));
    }

    public void setUploadChunkSize(int v) {
        XposedHelpers.setIntField(fileOperation, Obfuscate.getFieldName("FileUploadOperation", "uploadChunkSize"), v);
    }

    public int getMaxRequestsCount() {
        return XposedHelpers.getIntField(fileOperation, Obfuscate.getFieldName("FileUploadOperation", "maxRequestsCount"));
    }

    public void setMaxRequestsCount(int v) {
        XposedHelpers.setIntField(fileOperation, Obfuscate.getFieldName("FileUploadOperation", "maxRequestsCount"), v);
    }

    public int getUploadConnectionsCount() {
        return XposedHelpers.getIntField(fileOperation, Obfuscate.getFieldName("FileUploadOperation", "uploadConnectionsCount"));
    }

    public void setUploadConnectionsCount(int v) {
        XposedHelpers.setIntField(fileOperation, Obfuscate.getFieldName("FileUploadOperation", "uploadConnectionsCount"), v);
    }

    public int[] getUploadRequestsPerConnection() {
        return (int[]) XposedHelpers.getObjectField(fileOperation, Obfuscate.getFieldName("FileUploadOperation", "uploadRequestsPerConnection"));
    }
}
