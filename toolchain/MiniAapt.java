import java.io.*;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.util.*;

/** MiniAapt — 纯 Java 的最小化 AndroidManifest.xml 二进制编译器（AXML 生成器）。
 *  用法: java MiniAapt --package pkg --label L --activity cls --version-code N
 *        --version-name V --min-sdk N --target-sdk N --out FILE */
public class MiniAapt {
    // framework 属性资源 ID（frameworks/base public.xml，恒定）
    static final String[] ATTR_NAMES = {"versionCode","versionName","minSdkVersion","targetSdkVersion","label","name","exported"};
    static final int[] ATTR_IDS = {0x0101021b,0x0101021c,0x0101020c,0x01010270,0x01010001,0x01010003,0x01010010};
    static final int A_VERSIONCODE=0, A_VERSIONNAME=1, A_MINSDK=2, A_TARGETSDK=3, A_LABEL=4, A_NAME=5, A_EXPORTED=6;
    static final int NS_NONE = -1;
    static final int TYPE_STRING=0x03, TYPE_INT_DEC=0x10, TYPE_BOOLEAN=0x12;

    static ArrayList<String> strings = new ArrayList<>();
    static HashMap<String,Integer> stringIdx = new HashMap<>();

    static int intern(String s) {
        Integer i = stringIdx.get(s);
        if (i != null) return i;
        strings.add(s);
        int idx = strings.size()-1;
        stringIdx.put(s, idx);
        return idx;
    }

    static void writeLen(ByteArrayOutputStream o, int v) {
        if (v < 0x80) o.write(v);
        else { o.write(0x80 | ((v>>8)&0xFF)); o.write(v & 0xFF); }
    }

    static byte[] buildStringPool() {
        int count = strings.size();
        ByteArrayOutputStream data = new ByteArrayOutputStream();
        int[] offsets = new int[count];
        for (int i = 0; i < count; i++) {
            String s = strings.get(i);
            byte[] utf8 = s.getBytes(StandardCharsets.UTF_8);
            offsets[i] = data.size();
            writeLen(data, s.length());   // UTF-16 code units
            writeLen(data, utf8.length);  // UTF-8 bytes
            data.write(utf8, 0, utf8.length);
            data.write(0);
            while (data.size() % 4 != 0) data.write(0);
        }
        int stringsStart = 28 + 4*count;
        int total = stringsStart + data.size();
        ByteBuffer b = ByteBuffer.allocate(total).order(ByteOrder.LITTLE_ENDIAN);
        b.putShort((short)0x0001); b.putShort((short)28); b.putInt(total);
        b.putInt(count); b.putInt(0); b.putInt(0x100);
        b.putInt(stringsStart); b.putInt(0);
        for (int i = 0; i < count; i++) b.putInt(offsets[i]);
        b.put(data.toByteArray());
        return b.array();
    }

    static byte[] buildResourceMap() {
        int size = 8 + 4*ATTR_IDS.length;
        ByteBuffer b = ByteBuffer.allocate(size).order(ByteOrder.LITTLE_ENDIAN);
        b.putShort((short)0x0180); b.putShort((short)8); b.putInt(size);
        for (int id : ATTR_IDS) b.putInt(id);
        return b.array();
    }

    static class Attr {
        final int ns, name, raw, type, data;
        Attr(int ns, int name, int raw, int type, int data) {
            this.ns=ns; this.name=name; this.raw=raw; this.type=type; this.data=data;
        }
        static Attr str(int ns, int name, int idx) { return new Attr(ns,name,idx,TYPE_STRING,idx); }
        static Attr integer(int ns, int name, int v) { return new Attr(ns,name,NS_NONE,TYPE_INT_DEC,v); }
        static Attr bool(int ns, int name, boolean v) { return new Attr(ns,name,NS_NONE,TYPE_BOOLEAN,v?-1:0); }
    }

    static ByteArrayOutputStream body = new ByteArrayOutputStream();
    static void w16(int v) { body.write(v & 0xFF); body.write((v>>8)&0xFF); }
    static void w32(int v) { w16(v & 0xFFFF); w16((v>>16)&0xFFFF); }

    static void startNs(int prefix, int uri) {
        w16(0x0100); w16(16); w32(24); w32(1); w32(NS_NONE); w32(prefix); w32(uri);
    }
    static void endNs(int prefix, int uri) {
        w16(0x0101); w16(16); w32(24); w32(1); w32(NS_NONE); w32(prefix); w32(uri);
    }
    static void startEl(int ns, int name, List<Attr> attrs) {
        int n = attrs.size();
        w16(0x0102); w16(16); w32(36+20*n); w32(1); w32(NS_NONE);
        w32(ns); w32(name);
        w16(20); w16(20); w16(n); w16(0); w16(0); w16(0);
        for (Attr a : attrs) {
            w32(a.ns); w32(a.name); w32(a.raw);
            w16(8); body.write(0); body.write(a.type); w32(a.data);
        }
    }
    static void endEl(int ns, int name) {
        w16(0x0103); w16(16); w32(24); w32(1); w32(NS_NONE); w32(ns); w32(name);
    }

    static Map<String,String> parseArgs(String[] args) {
        Map<String,String> m = new HashMap<>();
        for (int i = 0; i < args.length-1; i += 2)
            if (args[i].startsWith("--")) m.put(args[i].substring(2), args[i+1]);
        return m;
    }
    static int optInt(Map<String,String> o, String k, int def) {
        String v = o.get(k);
        return v == null ? def : Integer.parseInt(v);
    }

    public static void main(String[] args) throws Exception {
        Map<String,String> opt = parseArgs(args);
        String pkg = opt.getOrDefault("package", "com.example.app");
        String label = opt.getOrDefault("label", "MyApp");
        String activity = opt.getOrDefault("activity", pkg + ".MainActivity");
        int versionCode = optInt(opt, "version-code", 1);
        String versionName = opt.getOrDefault("version-name", "1.0");
        int minSdk = optInt(opt, "min-sdk", 21);
        int targetSdk = optInt(opt, "target-sdk", 35);
        String out = opt.getOrDefault("out", "AndroidManifest.xml");

        for (String a : ATTR_NAMES) intern(a);  // 属性名必须先入池（下标0..6）
        int androidPrefix = intern("android");
        int androidUri = intern("http://schemas.android.com/apk/res/android");
        int T_MANIFEST = intern("manifest"), T_USESSDK = intern("uses-sdk");
        int T_APPLICATION = intern("application"), T_ACTIVITY = intern("activity");
        int T_INTENTFLT = intern("intent-filter"), T_ACTION = intern("action");
        int T_CATEGORY = intern("category"), N_PACKAGE = intern("package");
        int pkgIdx = intern(pkg), verNameIdx = intern(versionName);
        int labelIdx = intern(label), actIdx = intern(activity);
        int mainIdx = intern("android.intent.action.MAIN");
        int launchIdx = intern("android.intent.category.LAUNCHER");

        startNs(androidPrefix, androidUri);
        startEl(NS_NONE, T_MANIFEST, Arrays.asList(
            Attr.integer(androidUri, A_VERSIONCODE, versionCode),
            Attr.str(androidUri, A_VERSIONNAME, verNameIdx),
            Attr.str(NS_NONE, N_PACKAGE, pkgIdx)));
        startEl(NS_NONE, T_USESSDK, Arrays.asList(
            Attr.integer(androidUri, A_MINSDK, minSdk),
            Attr.integer(androidUri, A_TARGETSDK, targetSdk)));
        endEl(NS_NONE, T_USESSDK);
        startEl(NS_NONE, T_APPLICATION, Collections.singletonList(
            Attr.str(androidUri, A_LABEL, labelIdx)));
        startEl(NS_NONE, T_ACTIVITY, Arrays.asList(
            Attr.str(androidUri, A_NAME, actIdx),
            Attr.bool(androidUri, A_EXPORTED, true)));
        startEl(NS_NONE, T_INTENTFLT, Collections.emptyList());
        startEl(NS_NONE, T_ACTION, Collections.singletonList(
            Attr.str(androidUri, A_NAME, mainIdx)));
        endEl(NS_NONE, T_ACTION);
        startEl(NS_NONE, T_CATEGORY, Collections.singletonList(
            Attr.str(androidUri, A_NAME, launchIdx)));
        endEl(NS_NONE, T_CATEGORY);
        endEl(NS_NONE, T_INTENTFLT);
        endEl(NS_NONE, T_ACTIVITY);
        endEl(NS_NONE, T_APPLICATION);
        endEl(NS_NONE, T_MANIFEST);
        endNs(androidPrefix, androidUri);

        byte[] pool = buildStringPool();
        byte[] resmap = buildResourceMap();
        byte[] bodyBytes = body.toByteArray();
        int total = 8 + pool.length + resmap.length + bodyBytes.length;
        ByteBuffer outBuf = ByteBuffer.allocate(total).order(ByteOrder.LITTLE_ENDIAN);
        outBuf.putShort((short)0x0003); outBuf.putShort((short)8); outBuf.putInt(total);
        outBuf.put(pool); outBuf.put(resmap); outBuf.put(bodyBytes);
        try (FileOutputStream f = new FileOutputStream(out)) { f.write(outBuf.array()); }
        System.out.println("Wrote " + out + " (" + total + " bytes, " + strings.size() + " strings)");
    }
}
