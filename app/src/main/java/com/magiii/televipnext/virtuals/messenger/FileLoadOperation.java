package com.magiii.televipnext.virtuals.messenger;

import com.magiii.televipnext.obfuscate.ArgsResolver;
import com.magiii.televipnext.obfuscate.Obfuscate;

import com.magiii.televipnext.xposed.ReflectHelper;

public class FileLoadOperation {

    private final Object fileOperation;

    public FileLoadOperation(Object fileOperation){ this.fileOperation = fileOperation; }

    public void setDownloadChunkSizeBig(int v){
        ReflectHelper.setIntField(fileOperation, Obfuscate.getFieldName("FileLoadOperation", "downloadChunkSizeBig"), v);
    }

    public void setMaxDownloadRequests(int v){
        ReflectHelper.setIntField(fileOperation, Obfuscate.getFieldName("FileLoadOperation", "maxDownloadRequests"), v);
    }

    public void setMaxDownloadRequestsBig(int v){
        ReflectHelper.setIntField(fileOperation, Obfuscate.getFieldName("FileLoadOperation", "maxDownloadRequestsBig"), v);
    }

    public void setMaxCdnParts(int v){
        ReflectHelper.setIntField(fileOperation, Obfuscate.getFieldName("FileLoadOperation", "maxCdnParts"), v);
    }

}
