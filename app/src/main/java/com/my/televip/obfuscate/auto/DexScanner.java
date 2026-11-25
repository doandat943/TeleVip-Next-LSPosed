package com.my.televip.obfuscate.auto;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

public class DexScanner {

    public static final class FieldInfo {
        public final boolean isStatic;
        public final String name;
        public final String typeDesc;

        public FieldInfo(boolean isStatic, String name, String typeDesc) {
            this.isStatic = isStatic;
            this.name = name;
            this.typeDesc = typeDesc;
        }
    }

    public static final class MethodInfo {
        public final boolean isStatic;
        public final String name;
        public final String[] paramDescs;
        public final String returnDesc;
        public final int codeOff;
        public final DexPart dex;

        public MethodInfo(DexPart dex, boolean isStatic, String name, String[] paramDescs, String returnDesc, int codeOff) {
            this.dex = dex;
            this.isStatic = isStatic;
            this.name = name;
            this.paramDescs = paramDescs;
            this.returnDesc = returnDesc;
            this.codeOff = codeOff;
        }

        public int getInsnsSize() {
            if (codeOff == 0) return 0;
            return dex.buf.getInt(codeOff + 12);
        }

        public boolean readsFieldNamed(String fieldName) {
            if (codeOff == 0 || fieldName == null) return false;
            int insnsSize = dex.buf.getInt(codeOff + 12);
            int insnsOff = codeOff + 16;
            int k = 0;
            while (k < insnsSize) {
                int op = dex.buf.getShort(insnsOff + k * 2) & 0xFF;
                if (((op >= 0x52 && op <= 0x58) || (op >= 0x60 && op <= 0x66)) && k + 1 < insnsSize) {
                    int fIdx = dex.buf.getShort(insnsOff + (k + 1) * 2) & 0xFFFF;
                    if (fIdx < dex.fieldSz) {
                        int nIdx = dex.buf.getInt(dex.fieldOff + fIdx * 8 + 4);
                        if (fieldName.equals(dex.getString(nIdx))) return true;
                    }
                }
                k += insnWidth(dex.buf, insnsOff, k, insnsSize);
            }
            return false;
        }

        public boolean containsConstInt(int target) {
            if (codeOff == 0) return false;
            int insnsSize = dex.buf.getInt(codeOff + 12);
            int insnsOff = codeOff + 16;
            int k = 0;
            while (k < insnsSize) {
                int unit = dex.buf.getShort(insnsOff + k * 2) & 0xFFFF;
                int op = unit & 0xFF;
                if (op == 0x12) {
                    int val = (unit >> 12) & 0xF;
                    if (val >= 8) val -= 16;
                    if (val == target) return true;
                } else if (op == 0x13 && k + 1 < insnsSize) {
                    if (dex.buf.getShort(insnsOff + (k + 1) * 2) == target) return true;
                } else if (op == 0x14 && k + 2 < insnsSize) {
                    if (dex.buf.getInt(insnsOff + (k + 1) * 2) == target) return true;
                }
                k += insnWidth(dex.buf, insnsOff, k, insnsSize);
            }
            return false;
        }

        public Integer getFirstConstInt() {
            if (codeOff == 0) return null;
            int insnsSize = dex.buf.getInt(codeOff + 12);
            int insnsOff = codeOff + 16;
            int k = 0;
            while (k < insnsSize) {
                int unit = dex.buf.getShort(insnsOff + k * 2) & 0xFFFF;
                int op = unit & 0xFF;
                if (op == 0x12) {
                    int val = (unit >> 12) & 0xF;
                    if (val >= 8) val -= 16;
                    return val;
                } else if (op == 0x13 && k + 1 < insnsSize) {
                    return (int) dex.buf.getShort(insnsOff + (k + 1) * 2);
                }
                k += insnWidth(dex.buf, insnsOff, k, insnsSize);
            }
            return null;
        }
    }

    public static final class DexPart {
        final byte[] data;
        final ByteBuffer buf;
        final int strSz, strOff;
        final int typeSz, typeOff;
        final int protoSz, protoOff;
        final int fieldSz, fieldOff;
        final int methSz, methOff;
        final int clsSz, clsOff;
        final Map<Integer, String> strCache = new HashMap<>();
        final Map<String, Integer> classDefMap = new HashMap<>();

        DexPart(byte[] data) {
            this.data = data;
            this.buf = ByteBuffer.wrap(data).order(ByteOrder.LITTLE_ENDIAN);
            this.strSz = buf.getInt(56);
            this.strOff = buf.getInt(60);
            this.typeSz = buf.getInt(64);
            this.typeOff = buf.getInt(68);
            this.protoSz = buf.getInt(72);
            this.protoOff = buf.getInt(76);
            this.fieldSz = buf.getInt(80);
            this.fieldOff = buf.getInt(84);
            this.methSz = buf.getInt(88);
            this.methOff = buf.getInt(92);
            this.clsSz = buf.getInt(96);
            this.clsOff = buf.getInt(100);

            for (int i = 0; i < clsSz; i++) {
                int cIdx = buf.getInt(clsOff + i * 32);
                String desc = getType(cIdx);
                classDefMap.put(desc, i);
            }
        }

        String getString(int idx) {
            if (idx < 0 || idx >= strSz) return null;
            String cached = strCache.get(idx);
            if (cached != null) return cached;
            int off = buf.getInt(strOff + idx * 4);
            int[] pos = new int[]{off};
            int utf16Len = readUleb128(data, pos);
            int start = pos[0];
            String s = decodeMutf8(data, start, utf16Len);
            strCache.put(idx, s);
            return s;
        }

        private static String decodeMutf8(byte[] data, int start, int utf16Len) {
            char[] out = new char[Math.max(utf16Len, 0)];
            int s = 0;
            int p = start;
            while (p < data.length) {
                int a = data[p++] & 0xFF;
                if (a == 0) break;
                if (s >= out.length) {
                    char[] grown = new char[out.length + 16];
                    System.arraycopy(out, 0, grown, 0, out.length);
                    out = grown;
                }
                if (a < 0x80) {
                    out[s++] = (char) a;
                } else if ((a & 0xE0) == 0xC0) {
                    if (p >= data.length) break;
                    int b = data[p++] & 0xFF;
                    out[s++] = (char) (((a & 0x1F) << 6) | (b & 0x3F));
                } else if ((a & 0xF0) == 0xE0) {
                    if (p + 1 >= data.length) break;
                    int b = data[p++] & 0xFF;
                    int c = data[p++] & 0xFF;
                    out[s++] = (char) (((a & 0x0F) << 12) | ((b & 0x3F) << 6) | (c & 0x3F));
                } else {
                    out[s++] = '\uFFFD';
                }
            }
            return new String(out, 0, s);
        }

        String getType(int idx) {
            if (idx < 0 || idx >= typeSz) return null;
            return getString(buf.getInt(typeOff + idx * 4));
        }

        String getSuper(int clsDefIdx) {
            int supIdx = buf.getInt(clsOff + clsDefIdx * 32 + 8);
            if (supIdx == -1) return null;
            return getType(supIdx);
        }

        int findStringIndex(String target) {
            int lo = 0, hi = strSz - 1;
            while (lo <= hi) {
                int mid = (lo + hi) >>> 1;
                String s = getString(mid);
                int cmp = s.compareTo(target);
                if (cmp == 0) return mid;
                if (cmp < 0) lo = mid + 1;
                else hi = mid - 1;
            }
            return -1;
        }

        boolean methodUsesString(int codeOff, int strIdx) {
            if (codeOff == 0 || strIdx < 0) return false;
            int insnsSize = buf.getInt(codeOff + 12);
            int insnsOff = codeOff + 16;
            int i = 0;
            while (i < insnsSize) {
                int op = buf.getShort(insnsOff + i * 2) & 0xFF;
                if (op == 0x1a && i + 1 < insnsSize) {
                    int idx = buf.getShort(insnsOff + (i + 1) * 2) & 0xFFFF;
                    if (idx == strIdx) return true;
                } else if (op == 0x1b && i + 2 < insnsSize) {
                    int idx = buf.getInt(insnsOff + (i + 1) * 2);
                    if (idx == strIdx) return true;
                }
                i += insnWidth(buf, insnsOff, i, insnsSize);
            }
            return false;
        }

        boolean classUsesAllStrings(int clsDefIdx, int[] strIndices) {
            int classDataOff = buf.getInt(clsOff + clsDefIdx * 32 + 24);
            if (classDataOff == 0) return false;
            int[] pos = new int[]{classDataOff};
            int sf = readUleb128(data, pos);
            int inf = readUleb128(data, pos);
            int dm = readUleb128(data, pos);
            int vm = readUleb128(data, pos);
            for (int i = 0; i < sf + inf; i++) {
                readUleb128(data, pos);
                readUleb128(data, pos);
            }
            boolean[] found = new boolean[strIndices.length];
            int remaining = strIndices.length;
            for (int i = 0; i < dm + vm; i++) {
                readUleb128(data, pos);
                readUleb128(data, pos);
                int codeOff = readUleb128(data, pos);
                if (codeOff != 0) {
                    int insnsSize = buf.getInt(codeOff + 12);
                    int insnsOff = codeOff + 16;
                    int k = 0;
                    while (k < insnsSize) {
                        int op = buf.getShort(insnsOff + k * 2) & 0xFF;
                        if (op == 0x1a && k + 1 < insnsSize) {
                            int idx = buf.getShort(insnsOff + (k + 1) * 2) & 0xFFFF;
                            for (int s = 0; s < strIndices.length; s++) {
                                if (!found[s] && idx == strIndices[s]) {
                                    found[s] = true;
                                    if (--remaining == 0) return true;
                                }
                            }
                        } else if (op == 0x1b && k + 2 < insnsSize) {
                            int idx = buf.getInt(insnsOff + (k + 1) * 2);
                            for (int s = 0; s < strIndices.length; s++) {
                                if (!found[s] && idx == strIndices[s]) {
                                    found[s] = true;
                                    if (--remaining == 0) return true;
                                }
                            }
                        }
                        k += insnWidth(buf, insnsOff, k, insnsSize);
                    }
                }
            }
            return false;
        }

        List<FieldInfo> getFields(int clsDefIdx) {
            List<FieldInfo> out = new ArrayList<>();
            int classDataOff = buf.getInt(clsOff + clsDefIdx * 32 + 24);
            if (classDataOff == 0) return out;
            int[] pos = new int[]{classDataOff};
            int sf = readUleb128(data, pos);
            int inf = readUleb128(data, pos);
            readUleb128(data, pos);
            readUleb128(data, pos);

            int idx = 0;
            for (int i = 0; i < sf; i++) {
                idx += readUleb128(data, pos);
                readUleb128(data, pos);
                int tIdx = buf.getShort(fieldOff + idx * 8 + 2) & 0xFFFF;
                int nIdx = buf.getInt(fieldOff + idx * 8 + 4);
                out.add(new FieldInfo(true, getString(nIdx), getType(tIdx)));
            }
            idx = 0;
            for (int i = 0; i < inf; i++) {
                idx += readUleb128(data, pos);
                readUleb128(data, pos);
                int tIdx = buf.getShort(fieldOff + idx * 8 + 2) & 0xFFFF;
                int nIdx = buf.getInt(fieldOff + idx * 8 + 4);
                out.add(new FieldInfo(false, getString(nIdx), getType(tIdx)));
            }
            return out;
        }

        List<MethodInfo> getMethods(int clsDefIdx) {
            List<MethodInfo> out = new ArrayList<>();
            int classDataOff = buf.getInt(clsOff + clsDefIdx * 32 + 24);
            if (classDataOff == 0) return out;
            int[] pos = new int[]{classDataOff};
            int sf = readUleb128(data, pos);
            int inf = readUleb128(data, pos);
            int dm = readUleb128(data, pos);
            int vm = readUleb128(data, pos);
            for (int i = 0; i < sf + inf; i++) {
                readUleb128(data, pos);
                readUleb128(data, pos);
            }
            for (int g = 0; g < 2; g++) {
                int cnt = (g == 0) ? dm : vm;
                int idx = 0;
                for (int i = 0; i < cnt; i++) {
                    idx += readUleb128(data, pos);
                    int acc = readUleb128(data, pos);
                    int codeOff = readUleb128(data, pos);
                    boolean isStatic = (acc & 0x0008) != 0;
                    int pIdx = buf.getShort(methOff + idx * 8 + 2) & 0xFFFF;
                    int nIdx = buf.getInt(methOff + idx * 8 + 4);
                    int retIdx = buf.getInt(protoOff + pIdx * 12 + 4);
                    int paramsOff = buf.getInt(protoOff + pIdx * 12 + 8);
                    String[] params;
                    if (paramsOff != 0) {
                        int pCnt = buf.getInt(paramsOff);
                        params = new String[pCnt];
                        for (int k = 0; k < pCnt; k++) {
                            params[k] = getType(buf.getShort(paramsOff + 4 + k * 2) & 0xFFFF);
                        }
                    } else {
                        params = new String[0];
                    }
                    out.add(new MethodInfo(this, isStatic, getString(nIdx), params, getType(retIdx), codeOff));
                }
            }
            return out;
        }
    }

    private final List<DexPart> parts = new ArrayList<>();

    public DexScanner(String apkPath) throws Exception {
        try (ZipFile zf = new ZipFile(apkPath)) {
            Enumeration<? extends ZipEntry> entries = zf.entries();
            while (entries.hasMoreElements()) {
                ZipEntry e = entries.nextElement();
                String name = e.getName();
                if (name.startsWith("classes") && name.endsWith(".dex") && !name.contains("/")) {
                    try (InputStream is = zf.getInputStream(e)) {
                        ByteArrayOutputStream bos = new ByteArrayOutputStream((int) Math.max(e.getSize(), 65536));
                        byte[] tmp = new byte[16384];
                        int r;
                        while ((r = is.read(tmp)) != -1) {
                            bos.write(tmp, 0, r);
                        }
                        parts.add(new DexPart(bos.toByteArray()));
                    }
                }
            }
        }
    }

    public String findClassByStrings(String pkgPrefix, String superDesc, String... requiredStrings) {
        for (DexPart part : parts) {
            int[] sIdx = new int[requiredStrings.length];
            boolean allPresent = true;
            for (int i = 0; i < requiredStrings.length; i++) {
                int idx = part.findStringIndex(requiredStrings[i]);
                if (idx < 0) {
                    allPresent = false;
                    break;
                }
                sIdx[i] = idx;
            }
            if (!allPresent) continue;

            for (Map.Entry<String, Integer> entry : part.classDefMap.entrySet()) {
                String desc = entry.getKey();
                if (pkgPrefix != null && !desc.startsWith(pkgPrefix)) continue;
                int cidx = entry.getValue();
                if (superDesc != null && !superDesc.equals(part.getSuper(cidx))) continue;
                if (part.classUsesAllStrings(cidx, sIdx)) {
                    return desc;
                }
            }
        }
        return null;
    }

    public List<FieldInfo> getFields(String classDesc) {
        for (DexPart part : parts) {
            Integer idx = part.classDefMap.get(classDesc);
            if (idx != null) return part.getFields(idx);
        }
        return new ArrayList<>();
    }

    public List<MethodInfo> getMethods(String classDesc) {
        for (DexPart part : parts) {
            Integer idx = part.classDefMap.get(classDesc);
            if (idx != null) return part.getMethods(idx);
        }
        return new ArrayList<>();
    }

    public String findSingleMethod(String classDesc, Boolean isStatic, String returnDesc, String... paramDescs) {
        String found = null;
        for (MethodInfo m : getMethods(classDesc)) {
            if (m.name.startsWith("<")) continue;
            if (isStatic != null && m.isStatic != isStatic) continue;
            if (returnDesc != null && !returnDesc.equals(m.returnDesc)) continue;
            if (paramDescs != null) {
                if (m.paramDescs.length != paramDescs.length) continue;
                boolean match = true;
                for (int i = 0; i < paramDescs.length; i++) {
                    if (paramDescs[i] != null && !paramDescs[i].equals(m.paramDescs[i])) {
                        match = false;
                        break;
                    }
                }
                if (!match) continue;
            }
            if (found != null && !found.equals(m.name)) return null; // ambiguous
            found = m.name;
        }
        return found;
    }

    public String findMethodByString(String classDesc, String requiredStr, Boolean isStatic, String returnDesc, String... paramDescs) {
        for (MethodInfo m : getMethods(classDesc)) {
            if (m.name.startsWith("<")) continue;
            if (isStatic != null && m.isStatic != isStatic) continue;
            if (returnDesc != null && !returnDesc.equals(m.returnDesc)) continue;
            if (paramDescs != null) {
                if (m.paramDescs.length != paramDescs.length) continue;
                boolean match = true;
                for (int i = 0; i < paramDescs.length; i++) {
                    if (paramDescs[i] != null && !paramDescs[i].equals(m.paramDescs[i])) {
                        match = false;
                        break;
                    }
                }
                if (!match) continue;
            }
            int sIdx = m.dex.findStringIndex(requiredStr);
            if (sIdx >= 0 && m.dex.methodUsesString(m.codeOff, sIdx)) {
                return m.name;
            }
        }
        return null;
    }

    public String findSingleField(String classDesc, Boolean isStatic, String typeDesc) {
        String found = null;
        for (FieldInfo f : getFields(classDesc)) {
            if (isStatic != null && f.isStatic != isStatic) continue;
            if (!typeDesc.equals(f.typeDesc)) continue;
            if (found != null) return null; // ambiguous
            found = f.name;
        }
        return found;
    }

    public interface ClassPredicate {
        boolean test(String desc, List<FieldInfo> fields, List<MethodInfo> methods);
    }

    public String findClassByPredicate(String pkgPrefix, ClassPredicate pred) {
        for (DexPart part : parts) {
            for (Map.Entry<String, Integer> entry : part.classDefMap.entrySet()) {
                String desc = entry.getKey();
                if (pkgPrefix != null && !desc.startsWith(pkgPrefix)) continue;
                int cidx = entry.getValue();
                if (pred.test(desc, part.getFields(cidx), part.getMethods(cidx))) {
                    return desc;
                }
            }
        }
        return null;
    }

    public List<FieldInfo> getWrittenFieldsInMethod(String classDesc, String methodName, String[] paramDescs, String targetClassDesc) {
        List<FieldInfo> out = new ArrayList<>();
        for (MethodInfo m : getMethods(classDesc)) {
            if (!m.name.equals(methodName) || m.codeOff == 0) continue;
            if (paramDescs != null && !java.util.Arrays.equals(m.paramDescs, paramDescs)) continue;
            DexPart part = m.dex;
            int insnsSize = part.buf.getInt(m.codeOff + 12);
            int insnsOff = m.codeOff + 16;
            int k = 0;
            while (k < insnsSize) {
                int op = part.buf.getShort(insnsOff + k * 2) & 0xFF;
                if ((op == 0x59 || op == 0x5b || op == 0x5c) && k + 1 < insnsSize) {
                    int fIdx = part.buf.getShort(insnsOff + (k + 1) * 2) & 0xFFFF;
                    if (fIdx < part.fieldSz) {
                        int cIdx = part.buf.getShort(part.fieldOff + fIdx * 8) & 0xFFFF;
                        int tIdx = part.buf.getShort(part.fieldOff + fIdx * 8 + 2) & 0xFFFF;
                        int nIdx = part.buf.getInt(part.fieldOff + fIdx * 8 + 4);
                        String fc = part.getType(cIdx);
                        if (targetClassDesc == null || targetClassDesc.equals(fc)) {
                            out.add(new FieldInfo(false, part.getString(nIdx), part.getType(tIdx)));
                        }
                    }
                }
                k += insnWidth(part.buf, insnsOff, k, insnsSize);
            }
            break;
        }
        return out;
    }

    public List<FieldInfo> getReadInstanceFieldsInMethod(String classDesc, String methodName, String[] paramDescs, String targetClassDesc) {
        List<FieldInfo> out = new ArrayList<>();
        for (MethodInfo m : getMethods(classDesc)) {
            if (!m.name.equals(methodName) || m.codeOff == 0) continue;
            if (paramDescs != null && !java.util.Arrays.equals(m.paramDescs, paramDescs)) continue;
            DexPart part = m.dex;
            int insnsSize = part.buf.getInt(m.codeOff + 12);
            int insnsOff = m.codeOff + 16;
            int k = 0;
            while (k < insnsSize) {
                int op = part.buf.getShort(insnsOff + k * 2) & 0xFF;
                if ((op >= 0x52 && op <= 0x58) && k + 1 < insnsSize) {
                    int fIdx = part.buf.getShort(insnsOff + (k + 1) * 2) & 0xFFFF;
                    if (fIdx < part.fieldSz) {
                        int cIdx = part.buf.getShort(part.fieldOff + fIdx * 8) & 0xFFFF;
                        int tIdx = part.buf.getShort(part.fieldOff + fIdx * 8 + 2) & 0xFFFF;
                        int nIdx = part.buf.getInt(part.fieldOff + fIdx * 8 + 4);
                        String fc = part.getType(cIdx);
                        if (targetClassDesc == null || targetClassDesc.equals(fc)) {
                            out.add(new FieldInfo(false, part.getString(nIdx), part.getType(tIdx)));
                        }
                    }
                }
                k += insnWidth(part.buf, insnsOff, k, insnsSize);
            }
            break;
        }
        return out;
    }

    public List<FieldInfo> getReadStaticFieldsInMethod(String classDesc, String methodName, String[] paramDescs, String targetClassDesc) {
        List<FieldInfo> out = new ArrayList<>();
        for (MethodInfo m : getMethods(classDesc)) {
            if (!m.name.equals(methodName) || m.codeOff == 0) continue;
            if (paramDescs != null && !java.util.Arrays.equals(m.paramDescs, paramDescs)) continue;
            DexPart part = m.dex;
            int insnsSize = part.buf.getInt(m.codeOff + 12);
            int insnsOff = m.codeOff + 16;
            int k = 0;
            while (k < insnsSize) {
                int op = part.buf.getShort(insnsOff + k * 2) & 0xFF;
                if ((op >= 0x60 && op <= 0x66) && k + 1 < insnsSize) {
                    int fIdx = part.buf.getShort(insnsOff + (k + 1) * 2) & 0xFFFF;
                    if (fIdx < part.fieldSz) {
                        int cIdx = part.buf.getShort(part.fieldOff + fIdx * 8) & 0xFFFF;
                        int tIdx = part.buf.getShort(part.fieldOff + fIdx * 8 + 2) & 0xFFFF;
                        int nIdx = part.buf.getInt(part.fieldOff + fIdx * 8 + 4);
                        String fc = part.getType(cIdx);
                        if (targetClassDesc == null || targetClassDesc.equals(fc)) {
                            out.add(new FieldInfo(true, part.getString(nIdx), part.getType(tIdx)));
                        }
                    }
                }
                k += insnWidth(part.buf, insnsOff, k, insnsSize);
            }
            break;
        }
        return out;
    }

    private static final int[] OPCODE_WIDTH = new int[]{
            // 0x00..0x0f
            1, 1, 2, 3, 1, 2, 3, 1, 2, 3, 1, 1, 1, 1, 1, 1,
            // 0x10..0x1f
            1, 1, 1, 2, 3, 2, 2, 3, 5, 2, 2, 3, 2, 1, 1, 2,
            // 0x20..0x2f
            2, 1, 2, 2, 3, 3, 3, 1, 1, 2, 3, 3, 3, 2, 2, 2,
            // 0x30..0x3f
            2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 1, 1,
            // 0x40..0x4f
            1, 1, 1, 1, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2,
            // 0x50..0x5f
            2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2,
            // 0x60..0x6f
            2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 3, 3,
            // 0x70..0x7f
            3, 3, 3, 1, 3, 3, 3, 3, 3, 1, 1, 1, 1, 1, 1, 1,
            // 0x80..0x8f
            1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1,
            // 0x90..0x9f
            2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2,
            // 0xa0..0xaf
            2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2,
            // 0xb0..0xbf
            1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1,
            // 0xc0..0xcf
            1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1,
            // 0xd0..0xdf
            2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2,
            // 0xe0..0xef
            2, 2, 2, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1,
            // 0xf0..0xff
            1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 4, 4, 3, 3, 2, 2
    };

    static int insnWidth(ByteBuffer buf, int insnsOff, int k, int insnsSize) {
        int unit = buf.getShort(insnsOff + k * 2) & 0xFFFF;
        if (unit == 0x0100 && k + 1 < insnsSize) {
            int size = buf.getShort(insnsOff + (k + 1) * 2) & 0xFFFF;
            return Math.max(1, 4 + size * 2);
        } else if (unit == 0x0200 && k + 1 < insnsSize) {
            int size = buf.getShort(insnsOff + (k + 1) * 2) & 0xFFFF;
            return Math.max(1, 2 + size * 4);
        } else if (unit == 0x0300 && k + 3 < insnsSize) {
            int elemWidth = buf.getShort(insnsOff + (k + 1) * 2) & 0xFFFF;
            int size = buf.getInt(insnsOff + (k + 2) * 2);
            return Math.max(1, 4 + (size * elemWidth + 1) / 2);
        }
        return OPCODE_WIDTH[unit & 0xFF];
    }

    public static String descToJava(String desc) {
        if (desc == null) return null;
        if (desc.startsWith("L") && desc.endsWith(";")) {
            return desc.substring(1, desc.length() - 1).replace('/', '.');
        }
        return desc;
    }

    public static String javaToDesc(String name) {
        if (name == null) return null;
        return "L" + name.replace('.', '/') + ";";
    }

    private static int readUleb128(byte[] data, int[] pos) {
        int result = 0;
        int shift = 0;
        int p = pos[0];
        while (true) {
            int b = data[p++] & 0xFF;
            result |= (b & 0x7F) << shift;
            if ((b & 0x80) == 0) break;
            shift += 7;
        }
        pos[0] = p;
        return result;
    }
}
