package com.magiii.televipnext.Clients;

import android.content.Context;

import com.magiii.televipnext.Class.ClassNames;
import com.magiii.televipnext.utils.Utils;
import com.magiii.televipnext.xposed.ReflectHelper;

public class ClientManager {

    public enum Client {
        Telegram("org.telegram.messenger", "Telegram"),
        TelegramWeb("org.telegram.messenger.web", "Telegram"),
        TelegramPlus("org.telegram.plus", "Plus Messenger"),
        Nagram("xyz.nextalone.nagram", "Nagram", com.magiii.televipnext.Clients.Nagram.class),
        Nicegram("app.nicegram", "Nicegram", com.magiii.televipnext.Clients.Nicegram.class),
        TelegramBeta("org.telegram.messenger.beta", "Telegram"),
        XPlus("com.xplus.messenger", "XPlus"),
        iMe("com.iMe.android", "iMe"),
        iMeWeb("com.iMe.android.web", "iMe"),
        forkgram("org.forkgram.messenger", "Forkgram"),
        forkgramBeta("org.forkclient.messenger.beta", "Forkgram"),
        Telegraph("ir.ilmili.telegraph", "Telegraph", com.magiii.televipnext.Clients.Telegraph.class),
        Momogram("momo.gram", "Momogram", com.magiii.televipnext.Clients.Momogram.class),
        Nekogram("tw.nekomimi.nekogram", "Nekogram", true),
        Cherrygram("uz.unnarsx.cherrygram", "Cherrygram", true),
        ForkgramClassic("org.forkgram.classic", "Forkgram Classic"),
        Turrit("org.telegram.group", "Turrit", com.magiii.televipnext.Clients.Turrit.class),
        NagramXF("fork.risin42.nagramx", "Nagram X Fork"),
        Mercurygram("it.belloworld.mercurygram", "Mercurygram"),
        ExteraGram("com.exteragram.messenger", "exteraGram"),
        NagramX("nu.gpu.nagram", "Nagram X"),
        Octogram("it.octogram.android", "OctoGram");

        private final boolean tgnetObfuscated;
        private final String pkg;
        private final String displayName;
        private final Class<?> resolverClass;

        Client(String pkg, String displayName, boolean tgnetObfuscated) {
            this.tgnetObfuscated = tgnetObfuscated;
            this.pkg = pkg;
            this.displayName = displayName;
            this.resolverClass = null;
        }

        Client(String pkg, String displayName) {
            this.tgnetObfuscated = false;
            this.pkg = pkg;
            this.displayName = displayName;
            this.resolverClass = null;
        }

        Client(String pkg, String displayName, Class<?> resolverClass) {
            this.tgnetObfuscated = false;
            this.pkg = pkg;
            this.displayName = displayName;
            this.resolverClass = resolverClass;
        }

        public boolean isTgnetObfuscated() {
            return tgnetObfuscated;
        }

        public Class<?> getResolverClass() {
            return resolverClass;
        }

        public String getDisplayName() {
            return displayName;
        }

        public boolean hasPackage(String pkg) {

            return this.pkg.equals(pkg);
        }
    }

    public static String getCurrentDisplayName(Context context) {
        Client current = getCurrent();
        if (current != null && current.getDisplayName() != null && !current.getDisplayName().isEmpty()) {
            return current.getDisplayName();
        }
        if (context != null) {
            try {
                CharSequence label = context.getApplicationInfo().loadLabel(context.getPackageManager());
                if (label != null) {
                    String s = label.toString().trim();
                    if (!s.isEmpty()) {
                        return s;
                    }
                }
            } catch (Throwable ignored) {
            }
        }
        return "Telegram";
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