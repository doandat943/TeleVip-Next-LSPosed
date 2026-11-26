package com.magiii.televipnext.virtuals.messenger;

import com.magiii.televipnext.obfuscate.Obfuscate;

import com.magiii.televipnext.xposed.ReflectHelper;

/**
 * Virtual wrapper around Telegram's obfuscated FileUploadOperation instance.
 */
public class FileUploadOperation {

    private final Object fileOperation;

    public FileUploadOperation(Object fileOperation) {
        this.fileOperation = fileOperation;
    }

    public byte[] getReadBuffer() {
        return (byte[]) ReflectHelper.getObjectField(fileOperation, Obfuscate.getFieldName("FileUploadOperation", "readBuffer"));
    }

    public int getUploadChunkSize() {
        return ReflectHelper.getIntField(fileOperation, Obfuscate.getFieldName("FileUploadOperation", "uploadChunkSize"));
    }

    public void setUploadChunkSize(int v) {
        ReflectHelper.setIntField(fileOperation, Obfuscate.getFieldName("FileUploadOperation", "uploadChunkSize"), v);
    }

    public int getMaxRequestsCount() {
        return ReflectHelper.getIntField(fileOperation, Obfuscate.getFieldName("FileUploadOperation", "maxRequestsCount"));
    }

    public void setMaxRequestsCount(int v) {
        ReflectHelper.setIntField(fileOperation, Obfuscate.getFieldName("FileUploadOperation", "maxRequestsCount"), v);
    }

    public int getUploadConnectionsCount() {
        return ReflectHelper.getIntField(fileOperation, Obfuscate.getFieldName("FileUploadOperation", "uploadConnectionsCount"));
    }

    public void setUploadConnectionsCount(int v) {
        ReflectHelper.setIntField(fileOperation, Obfuscate.getFieldName("FileUploadOperation", "uploadConnectionsCount"), v);
    }

    public int[] getUploadRequestsPerConnection() {
        return (int[]) ReflectHelper.getObjectField(fileOperation, Obfuscate.getFieldName("FileUploadOperation", "uploadRequestsPerConnection"));
    }
}
