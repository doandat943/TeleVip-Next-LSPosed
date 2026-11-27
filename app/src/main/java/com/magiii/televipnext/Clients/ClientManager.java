package com.magiii.televipnext.Clients;

import com.magiii.televipnext.Class.ClassNames;
import com.magiii.televipnext.utils.Utils;
import com.magiii.televipnext.xposed.ReflectHelper;

public class ClientManager {

    public enum Client {
        Telegram("org.telegram.messenger"),
        TelegramWeb("org.telegram.messenger.web"),
        TelegramPlus("org.telegram.plus"),
        Nagram("xyz.nextalone.nagram", com.magiii.televipnext.Clients.Nagram.class),
        Nicegram("app.nicegram", com.magiii.televipnext.Clients.Nicegram.class),
        TelegramBeta("org.telegram.messenger.beta"),
        XPlus("com.xplus.messenger"),
        iMe("com.iMe.android"),
        iMeWeb("com.iMe.android.web"),
        forkgram("org.forkgram.messenger"),
        forkgramBeta("org.forkclient.messenger.beta"),
        Telegraph("ir.ilmili.telegraph", com.magiii.televipnext.Clients.Telegraph.class),
        Momogram("momo.gram", com.magiii.televipnext.Clients.Momogram.class),
        Nekogram("tw.nekomimi.nekogram", true),
        Cherrygram("uz.unnarsx.cherrygram", true),
        ForkgramClassic("org.forkgram.classic"),
        Turrit("org.telegram.group", com.magiii.televipnext.Clients.Turrit.class),
        NagramXF("fork.risin42.nagramx"),
        Mercurygram("it.belloworld.mercurygram");

        private final boolean tgnetObfuscated;
        private final String pkg;
        private final Class<?> resolverClass;

        Client(String pkg, boolean tgnetObfuscated) {
            this.tgnetObfuscated = tgnetObfuscated;
            this.pkg = pkg;
            resolverClass = null;
        }

        Client(String pkg) {
            this.tgnetObfuscated = false;
            this.pkg = pkg;
            resolverClass = null;
        }

        Client(String pkg, Class<?> resolverClass) {
            this.tgnetObfuscated = false;
            this.pkg = pkg;
            this.resolverClass = resolverClass;
        }

        public boolean isTgnetObfuscated() {
            return tgnetObfuscated;
        }

        public Class<?> getResolverClass() {
            return resolverClass;
        }

        public boolean hasPackage(String pkg) {

            return this.pkg.equals(pkg);
        }
    }

    public static boolean is(Client client) {

        return is(client, Utils.pkgName);

    }

    public static boolean is(Client client, String pkg) {

        return client.hasPackage(pkg);

    }

    public static Client getCurrent() {

        for (Client client : Client.values()) {

            if (client.hasPackage(Utils.pkgName))
                return client;

        }

        return null;
    }

    public static boolean isTgnetObfuscated() {

        Client client = getCurrent();

        return (client == null || client.isTgnetObfuscated())
                && Utils.classLoader != null
                && ReflectHelper.findClassIfExists(ClassNames.MESSAGE, Utils.classLoader) == null;

    }

    public static boolean containsPackage(String pkg, ClassLoader classLoader) {

        for (Client client : Client.values()) {

            if (client.hasPackage(pkg))
                return true;

        }

        if (classLoader == null) return false;
        if (ReflectHelper.findClassIfExists(ClassNames.LAUNCH_ACTIVITY, classLoader) == null) {
            return false;
        }
        return ReflectHelper.findClassIfExists(ClassNames.CONNECTIONS_MANAGER, classLoader) != null
                || ReflectHelper.findClassIfExists(ClassNames.APPLICATION_LOADER, classLoader) != null;
    }

}