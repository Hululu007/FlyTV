package dev.flytv.engine;

import java.io.BufferedReader;
import java.io.File;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicBoolean;

/** Java 爬虫宿主管理（等价 C# JarRuntime）：以子进程方式拉起 jar-host.jar（dex2jar 转换 + 加载 TVBox jar 爬虫），
 * 引擎通过 HTTP 调用它（/load /call /jarpost /jarstream /savedfid）。 */
public final class JarHost {
    private static final Object LOCK = new Object();
    private static Process proc;
    private static int port = 9790;
    private static final AtomicBoolean logPumpStarted = new AtomicBoolean(false);

    public static String baseUrl() { return "http://127.0.0.1:" + port; }
    public static boolean running() { return proc != null && proc.isAlive(); }

    /** 启动宿主（幂等）。无 Java 环境/组件缺失时记录错误并返回 false。 */
    public static boolean start() {
        synchronized (LOCK) {
            if (running()) return true;
            try {
                // 组件目录：优先安装根（合并布局：jar-host.jar 与 libs 直接位于安装目录），
                // 否则回退旧布局（{install}/jar/…）。
                File root = new File(AppPaths.InstallRoot);
                File hostJar = new File(root, "jar-host.jar");
                File libsDir = new File(root, "libs");
                File hostDir = root;
                if (!hostJar.exists() || !libsDir.isDirectory()) {
                    hostDir = new File(AppPaths.jarDir());
                    hostJar = new File(hostDir, "jar-host.jar");
                    libsDir = new File(hostDir, "libs");
                }
                if (!hostJar.exists() || !libsDir.isDirectory()) {
                    Logger.e("JarHost", "缺少 Java 爬虫宿主组件（jar-host.jar / libs）");
                    return false;
                }
                String javaExe = System.getProperty("java.home") + File.separator + "bin" + File.separator + "java.exe";
                if (!new File(javaExe).exists()) javaExe = "java";

                ProcessBuilder pb = new ProcessBuilder(
                        javaExe,
                        "-noverify",
                        "-Dfile.encoding=UTF-8",
                        "-cp",
                        hostJar.getAbsolutePath() + File.pathSeparator + libsDir.getAbsolutePath() + File.separator + "*",
                        "host.Host");
                pb.directory(hostDir);
                pb.environment().put("JAR_HOST_PORT", String.valueOf(port));
                pb.environment().put("JAR_HOST_DATA", AppPaths.JarCache);
                // PATH 前置宿主目录：jar 内部调 chmod 等命令时用桩程序兜底
                String path = pb.environment().get("PATH");
                pb.environment().put("PATH", hostDir + File.pathSeparator + (path == null ? "" : path));
                pb.redirectErrorStream(true);
                // stdin 管道：引擎退出 → 宿主 stdin EOF → 自杀（等价 C# 版父进程看门狗）
                proc = pb.start();

                pumpLogs(proc);
                Logger.d("JarHost", "宿主进程已启动 pid=" + proc.pid() + "（等待就绪…）");

                // 等待就绪（轮询 /config，最多 20 秒）
                for (int i = 0; i < 100; i++) {
                    if (!proc.isAlive()) { Logger.e("JarHost", "宿主进程提前退出"); return false; }
                    try {
                        String r = HttpUtil.get(baseUrl() + "/config", null, 1500);
                        if (r != null) {
                            Logger.d("JarHost", "Java 爬虫宿主就绪: " + baseUrl());
                            return true;
                        }
                    } catch (Exception ignored) { }
                    Thread.sleep(200);
                }
                Logger.e("JarHost", "宿主就绪超时");
                return false;
            } catch (Exception e) {
                Logger.e("JarHost", "宿主启动失败: " + e);
                return false;
            }
        }
    }

    public static void stop() {
        synchronized (LOCK) {
            try { if (proc != null) proc.destroy(); } catch (Exception ignored) { }
            proc = null;
        }
    }

    private static void pumpLogs(Process p) {
        if (!logPumpStarted.compareAndSet(false, true)) return;
        Thread t = new Thread(() -> {
            try (BufferedReader r = new BufferedReader(new InputStreamReader(p.getInputStream(), StandardCharsets.UTF_8))) {
                String line;
                while ((line = r.readLine()) != null) Logger.d("jar-host", line);
            } catch (Exception ignored) { }
            Logger.d("JarHost", "宿主日志流结束");
        }, "jar-host-log");
        t.setDaemon(true);
        t.start();
    }

    // ---- 与宿主通信（内部 HTTP） ----

    public static String postJson(String path, String json, int timeoutMs) throws Exception {
        return HttpUtil.postJson(baseUrl() + path, json, null, timeoutMs);
    }

    public static String get(String path, int timeoutMs) throws Exception {
        return HttpUtil.get(baseUrl() + path, null, timeoutMs);
    }
}
