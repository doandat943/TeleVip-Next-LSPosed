package com.magiii.televipnext.virtuals.messenger;

import com.magiii.televipnext.obfuscate.Obfuscate;

import com.magiii.televipnext.xposed.ReflectHelper;

public class FileLoadOperation {

    private final Object fileOperation;

    public FileLoadOperation(Object fileOperation){ this.fileOperation = fileOperation; }

    private void setIntFieldIfPresent(String mappedKey, int v) {
        if (fileOperation == null) return;
        String fieldName = Obfuscate.getFieldName("FileLoadOperation", mappedKey);
        if (ReflectHelper.findFieldIfExists(fileOperation.getClass(), fieldName) != null) {
            ReflectHelper.setIntField(fileOperation, fieldName, v);
        }
    }

    public void setDownloadChunkSizeBig(int v){
        setIntFieldIfPresent("downloadChunkSizeBig", v);
    }

    public void setMaxDownloadRequests(int v){
        setIntFieldIfPresent("maxDownloadRequests", v);
    }

    public void setMaxDownloadRequestsBig(int v){
        setIntFieldIfPresent("maxDownloadRequestsBig", v);
    }

    public void setMaxCdnParts(int v){
        setIntFieldIfPresent("maxCdnParts", v);
    }

}
