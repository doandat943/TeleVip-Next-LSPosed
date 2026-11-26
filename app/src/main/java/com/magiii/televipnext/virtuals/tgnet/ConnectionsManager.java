package com.magiii.televipnext.virtuals.tgnet;

import com.magiii.televipnext.Class.ClassLoad;
import com.magiii.televipnext.Class.ClassNames;
import com.magiii.televipnext.obfuscate.Obfuscate;
import com.magiii.televipnext.virtuals.messenger.UserConfig;

import com.magiii.televipnext.xposed.ReflectHelper;

public class ConnectionsManager {
    final Object connectionsManager;

    public ConnectionsManager(Object instance)
    {
        this.connectionsManager = instance;
    }

    public void sendRequest(Object object, Object completionBlock) {
        ReflectHelper.callMethod(connectionsManager, Obfuscate.getMethodName("ConnectionsManager", "sendRequest"), object, completionBlock);
    }

    public int getCurrentTime(){
        return (int)ReflectHelper.callMethod(connectionsManager, Obfuscate.getMethodName("ConnectionsManager", "getCurrentTime"));
    }

    public static ConnectionsManager getInstance(int num){
        return new ConnectionsManager(ReflectHelper.callStaticMethod(ClassLoad.getClass(ClassNames.CONNECTIONS_MANAGER), Obfuscate.getMethodName("ConnectionsManager", "getInstance"), num));
    }

    public static ConnectionsManager getConnectionsManager() {
        return getInstance(UserConfig.getSelectedAccount());
    }

}
