package com.magiii.televipnext.virtuals.messenger;

import com.magiii.televipnext.Class.ClassNames;
import com.magiii.televipnext.Class.ClassLoad;
import com.magiii.televipnext.obfuscate.ArgsResolver;
import com.magiii.televipnext.obfuscate.Obfuscate;

import java.io.File;

import com.magiii.televipnext.xposed.ReflectHelper;

public class FileLoader {

    Object fileLoader;

    public FileLoader(Object fileLoader){
        this.fileLoader = fileLoader;
    }

    public File getLocalFile(ImageLocation location) {
        return (File) ReflectHelper.callMethod(fileLoader, Obfuscate.getMethodName("FileLoader", "getLocalFile"), location.imageLocation);
    }

    public static FileLoader getInstance(int num) {
        return new FileLoader(ReflectHelper.callStaticMethod(ClassLoad.getClass(ClassNames.FILE_LOADER), Obfuscate.getMethodName("FileLoader", "getInstance"), num));
    }

    public Object getFileLoaderObject() {
        return fileLoader;
    }

}
