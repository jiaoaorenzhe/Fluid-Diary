import com.sun.net.httpserver.*;
import java.io.*;
import java.net.InetSocketAddress;

/** 极简文件服务器: java MiniServe <端口> <目录> */
public class MiniServe {
    public static void main(String[] a) throws Exception {
        int port = Integer.parseInt(a[0]);
        File dir = new File(a[1]);
        HttpServer s = HttpServer.create(new InetSocketAddress(port), 0);
        s.createContext("/", t -> {
            try {
                String p = t.getRequestURI().getPath().substring(1);
                File f = new File(dir, p).getCanonicalFile();
                if (!f.getPath().startsWith(dir.getCanonicalPath()) || !f.isFile()) {
                    t.sendResponseHeaders(404, 0); t.close(); return;
                }
                t.getResponseHeaders().set("Content-Type",
                    f.getName().endsWith(".apk")
                        ? "application/vnd.android.package-archive" : "application/octet-stream");
                t.sendResponseHeaders(200, f.length());
                OutputStream o = t.getResponseBody();
                FileInputStream in = new FileInputStream(f);
                byte[] buf = new byte[8192]; int n;
                while ((n = in.read(buf)) > 0) o.write(buf, 0, n);
                in.close(); o.close();
                System.out.println("served: " + f.getName() + " -> " + t.getRemoteAddress());
            } catch (Exception e) {
                System.out.println("err: " + e);
            }
        });
        s.start();
        System.out.println("serving " + dir + " on 0.0.0.0:" + port);
    }
}
