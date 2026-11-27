package com.magiii.televipnext.virtuals.tgnet;

import com.magiii.televipnext.Class.ClassLoad;
import com.magiii.televipnext.Class.ClassNames;
import com.magiii.televipnext.obfuscate.Obfuscate;

import java.util.ArrayList;

import com.magiii.televipnext.xposed.ReflectHelper;

public class TLRPC {
    public static class Peer {
        final Object peer;

        public Peer(Object peer) {
            this.peer = peer;
        }

        public long getUser_id(){
            return ReflectHelper.getLongField(peer, Obfuscate.getFieldName("TLRPC$Peer", "user_id"));
        }
        public long getChat_id(){
            return ReflectHelper.getLongField(peer, Obfuscate.getFieldName("TLRPC$Peer", "chat_id"));
        }
        public long getChannel_id(){
            return ReflectHelper.getLongField(peer, Obfuscate.getFieldName("TLRPC$Peer", "channel_id"));
        }
    }

    public static class User {
        Object user;

        public User(Object user) {
            this.user = user;
        }

        public String getPhone() {
            return (String) ReflectHelper.getObjectField(user, Obfuscate.getFieldName("TLRPC$User", "phone"));
        }

        public void setPhone(String phone){
            ReflectHelper.setObjectField(user, Obfuscate.getFieldName("TLRPC$User", "phone"), phone);
        }

        public Object getUser(){
            return user;
        }

    }

    public static class Message {
        private final Object message;
        private int id;
        public Message(Object message) {
            this.message = message;
        }

        public Message() {
            this.message = ReflectHelper.newInstance(ClassLoad.getClass(ClassNames.MESSAGE));
        }

        //get
        public int getId(){
            if (id == 0){
                id = ReflectHelper.getIntField(message, Obfuscate.getFieldName("TLRPC$Message", "id"));
            }
            return id;
        }

        public String getMessage(){
            return (String) ReflectHelper.getObjectField(message, Obfuscate.getFieldName("TLRPC$Message", "message"));
        }

        public Peer getFrom_id(){
            return new TLRPC.Peer(ReflectHelper.getObjectField(message, Obfuscate.getFieldName("TLRPC$Message", "from_id")));
        }

        public int getFlags(){
            return ReflectHelper.getIntField(message, Obfuscate.getFieldName("TLRPC$Message", "flags"));
        }

        public int getTtl(){
            return ReflectHelper.getIntField(message, Obfuscate.getFieldName("TLRPC$Message", "ttl"));
        }

        public int getDate() {
            try {
                String mapped = Obfuscate.getFieldName("TLRPC$Message", "date");
                if (ReflectHelper.findFieldIfExists(message.getClass(), mapped) != null) {
                    return ReflectHelper.getIntField(message, mapped);
                }
                if (!"date".equals(mapped) && ReflectHelper.findFieldIfExists(message.getClass(), "date") != null) {
                    return ReflectHelper.getIntField(message, "date");
                }
            } catch (Throwable ignored) {}
            return 0;
        }

        public Object get_Message(){
            return message;
        }

        //set
        public void setId(int id){
            ReflectHelper.setIntField(message, Obfuscate.getFieldName("TLRPC$Message", "id"), id);
            this.id = id;
        }

        public void setMessage(String msg){
            ReflectHelper.setObjectField(message, Obfuscate.getFieldName("TLRPC$Message", "message"), msg);
        }

        public void setFlags(int flags){
            ReflectHelper.setIntField(message, Obfuscate.getFieldName("TLRPC$Message", "flags"), flags);
        }

        public void setTtl(Object ttl){
            ReflectHelper.setObjectField(message, Obfuscate.getFieldName("TLRPC$Message", "ttl"), ttl);
        }

        public static Message TLdeserialize(NativeByteBuffer stream, int constructor, boolean exception){
            return new Message(ReflectHelper.callStaticMethod(ClassLoad.getClass(ClassNames.MESSAGE), Obfuscate.getMethodName("TLRPC$Message", "TLdeserialize"), stream.nativeByteBuffer, constructor, exception));
        }

        public void readAttachPath(NativeByteBuffer stream, long currentUserId){
            ReflectHelper.callMethod(message, Obfuscate.getMethodName("TLRPC$Message", "readAttachPath"), stream.nativeByteBuffer, currentUserId);
        }
    }

    public static class InputPeer {

        private final Object inputPeer;

        public InputPeer(Object message) {
            this.inputPeer = message;
        }
        public long getUser_id(){
            return ReflectHelper.getLongField(inputPeer, Obfuscate.getFieldName("TLRPC$InputPeer", "user_id"));
        }
        public long getChat_id(){
            return ReflectHelper.getLongField(inputPeer, Obfuscate.getFieldName("TLRPC$InputPeer", "chat_id"));
        }
        public long getChannel_id(){
            return ReflectHelper.getLongField(inputPeer, Obfuscate.getFieldName("TLRPC$InputPeer", "channel_id"));
        }

        public Object getInputPeer() {
            return inputPeer;
        }

    }

    public static class messages_Messages {
        Object messages_Messages;

        public messages_Messages(Object messages) {
            messages_Messages = messages;
        }
        public ArrayList<Object> getMessages(){
            return (ArrayList<Object>) ReflectHelper.getObjectField(messages_Messages, Obfuscate.getFieldName("TLRPC$messages_Messages", "messages"));
        }
    }

    public static class TL_updateDeleteChannelMessages {
        private final Object tl_updateDeleteChannelMessages;

        public TL_updateDeleteChannelMessages(Object instance)
        {
            this.tl_updateDeleteChannelMessages = instance;
        }

        public long getChannelID()
        {
            return ReflectHelper.getLongField(tl_updateDeleteChannelMessages,  Obfuscate.getFieldName("TL_update$TL_updateDeleteChannelMessages", "channel_id"));
        }

        public ArrayList<Integer> getMessages() {
            return (ArrayList<Integer>) ReflectHelper.getObjectField(tl_updateDeleteChannelMessages, Obfuscate.getFieldName("TL_update$TL_updateDeleteChannelMessages", "messages"));
        }
    }

    public static class TL_updateDeleteMessages {
        private final Object tl_updateDeleteMessages;

        public TL_updateDeleteMessages(Object instance) {
            this.tl_updateDeleteMessages = instance;
        }

        public ArrayList<Integer> getMessages() {
            return (ArrayList<Integer>) ReflectHelper.getObjectField(tl_updateDeleteMessages, Obfuscate.getFieldName("TL_update$TL_updateDeleteMessages", "messages"));
        }
    }

    public static class TL_messages_affectedMessages {
        final Object instance;

        public TL_messages_affectedMessages() {
            Object created = null;
            try {
                Class<?> cls = ClassLoad.getClass(ClassNames.TL_MESSAGES_AFFECTED);
                if (cls != null) created = ReflectHelper.newInstance(cls);
            } catch (Throwable ignored) {}
            this.instance = created;
        }
        public TL_messages_affectedMessages(Object instance) {
            this.instance = instance;
        }

        public int getPts(){
            if (instance == null) return 0;
            return ReflectHelper.getIntField(instance, Obfuscate.getFieldName("TLRPC$TL_messages_affectedMessages", "pts"));
        }

        public int getPtsCount(){
            if (instance == null) return 0;
            return ReflectHelper.getIntField(instance, Obfuscate.getFieldName("TLRPC$TL_messages_affectedMessages", "pts_count"));
        }
        public void setPts(int pts){
            if (instance == null) return;
            ReflectHelper.setIntField(instance, Obfuscate.getFieldName("TLRPC$TL_messages_affectedMessages", "pts"), pts);
        }

        public void setPtsCount(int pts_count){
            if (instance == null) return;
            ReflectHelper.setIntField(instance, Obfuscate.getFieldName("TLRPC$TL_messages_affectedMessages", "pts_count"), pts_count);
        }

        public Object getTL_messages_affectedMessages(){
            return instance;
        }

    }

    public static class TL_channels_readHistory {
        final Object instance;

        public TL_channels_readHistory() {
            Object created = null;
            try {
                Class<?> cls = ClassLoad.getClass(ClassNames.TL_CHANNELS_READ_HISTORY);
                if (cls != null) created = ReflectHelper.newInstance(cls);
            } catch (Throwable ignored) {}
            this.instance = created;
        }
        public TL_channels_readHistory(Object instance) {
            this.instance = instance;
        }

        public void setChannel(Object channel){
            if (instance == null) return;
            ReflectHelper.setObjectField(instance, Obfuscate.getFieldName("TLRPC$TL_channels_readHistory", "channel"), channel);
        }

        public void setMax_id(int max_id){
            if (instance == null) return;
            ReflectHelper.setIntField(instance, Obfuscate.getFieldName("TLRPC$TL_channels_readHistory", "max_id"), max_id);
        }

        public Object getTL_channels_readHistory(){
            return instance;
        }
    }

    public static class TL_messages_readHistory {
        final Object instance;

        public TL_messages_readHistory() {
            Object created = null;
            try {
                Class<?> cls = ClassLoad.getClass(ClassNames.TL_MESSAGES_READ_HISTORY);
                if (cls != null) created = ReflectHelper.newInstance(cls);
            } catch (Throwable ignored) {}
            this.instance = created;
        }

        public TL_messages_readHistory(Object instance) {
            this.instance = instance;
        }

        public void setPeer(InputPeer peer){
            if (instance == null || peer == null) return;
            ReflectHelper.setObjectField(instance, Obfuscate.getFieldName("TLRPC$TL_messages_readHistory", "peer"), peer.inputPeer);
        }

        public void setMax_id(int max_id){
            if (instance == null) return;
            ReflectHelper.setIntField(instance, Obfuscate.getFieldName("TLRPC$TL_messages_readHistory", "max_id"), max_id);
        }

        public Object getTL_messages_readHistory(){
            return instance;
        }

    }
}
