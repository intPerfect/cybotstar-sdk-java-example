package com.brgroup.cybotstar.javafx;

import java.io.File;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;

/**
 * ChatApp 启动器 - 自动配置 JavaFX 模块路径
 */
public class ChatAppLauncher {

    private static final String[] JAVAFX_MODULES = {"javafx-controls", "javafx-fxml", "javafx-base", "javafx-graphics"};
    private static final String JAVAFX_VERSION = "17.0.2";
    private static final String MAIN_CLASS = "com.brgroup.cybotstar.javafx.ChatApp";

    public static void main(String[] args) {
        try {
            if (hasJavaFXInClasspath()) {
                launchWithAutoConfig(args);
            } else {
                tryDirectLaunch(args);
            }
        } catch (Exception e) {
            printErrorAndExit(e);
        }
    }

    private static void tryDirectLaunch(String[] args) throws Exception {
        try {
            ChatApp.main(args);
        } catch (Throwable e) {
            if (isJavaFXError(e)) {
                System.out.println("检测到 JavaFX 缺失，正在自动配置...");
                launchWithAutoConfig(args);
            } else {
                throw e;
            }
        }
    }

    private static boolean isJavaFXError(Throwable e) {
        String msg = e.getMessage();
        String causeMsg = e.getCause() != null ? e.getCause().getMessage() : "";
        return (msg != null && msg.contains("javafx"))
                || (causeMsg != null && causeMsg.contains("javafx"))
                || (e instanceof ClassNotFoundException && msg != null && msg.contains("javafx"))
                || (e instanceof NoClassDefFoundError && msg != null && msg.contains("javafx"));
    }

    private static void launchWithAutoConfig(String[] args) throws Exception {
        String mavenRepo = findMavenRepository();
        if (mavenRepo == null) throw new RuntimeException("无法找到 Maven 仓库");

        List<String> modulePaths = findJavaFXModules(mavenRepo);
        if (modulePaths.isEmpty()) throw new RuntimeException("未找到 JavaFX 模块，请运行: mvn dependency:resolve");

        ProcessBuilder pb = new ProcessBuilder(buildCommand(modulePaths));
        pb.inheritIO();
        System.exit(pb.start().waitFor());
    }

    private static List<String> buildCommand(List<String> modulePaths) {
        List<String> cmd = new ArrayList<>();
        cmd.add(getJavaExecutable());
        cmd.add("--module-path");
        cmd.add(String.join(File.pathSeparator, modulePaths));
        cmd.add("--add-modules");
        cmd.add("javafx.controls,javafx.fxml");
        cmd.add("--add-opens");
        cmd.add("javafx.graphics/javafx.scene=ALL-UNNAMED");
        cmd.add("--add-opens");
        cmd.add("javafx.graphics/com.sun.javafx.application=ALL-UNNAMED");
        cmd.add("-Djava.awt.headless=false");
        cmd.add("-cp");
        cmd.add(buildClasspath());
        cmd.add(MAIN_CLASS);
        return cmd;
    }

    private static String getJavaExecutable() {
        String javaHome = System.getProperty("java.home");
        String javaExe = javaHome + File.separator + "bin" + File.separator + "java";
        return System.getProperty("os.name").toLowerCase().contains("win") ? javaExe + ".exe" : javaExe;
    }

    private static List<String> findJavaFXModules(String mavenRepo) {
        List<String> paths = new ArrayList<>();
        String platform = getPlatformSuffix();
        for (String module : JAVAFX_MODULES) {
            String jarPath = findJavaFXJar(mavenRepo, module, platform);
            if (jarPath != null) paths.add(jarPath);
        }
        return paths;
    }

    private static String findJavaFXJar(String mavenRepo, String module, String platform) {
        String basePath = mavenRepo + "/org/openjfx/" + module + "/" + JAVAFX_VERSION + "/" + module + "-" + JAVAFX_VERSION;

        if (!platform.isEmpty() && new File(basePath + platform + ".jar").exists())
            return basePath + platform + ".jar";
        if (new File(basePath + ".jar").exists()) return basePath + ".jar";

        for (String p : new String[]{"-win", "-mac", "-linux"}) {
            if (!p.equals(platform) && new File(basePath + p + ".jar").exists())
                return basePath + p + ".jar";
        }
        return null;
    }

    private static String getPlatformSuffix() {
        String os = System.getProperty("os.name").toLowerCase();
        if (os.contains("win")) return "-win";
        if (os.contains("mac")) return "-mac";
        if (os.contains("linux") || os.contains("nix")) return "-linux";
        return "";
    }

    private static boolean hasJavaFXInClasspath() {
        String cp = System.getProperty("java.class.path");
        return cp != null && cp.toLowerCase().contains("openjfx");
    }

    private static String buildClasspath() {
        String cp = System.getProperty("java.class.path");
        String userDir = System.getProperty("user.dir");
        if (cp == null || cp.isEmpty()) return userDir;

        List<String> paths = new ArrayList<>();
        for (String path : cp.split(File.pathSeparator)) {
            if (!isJavaFXJar(path)) {
                File f = new File(path);
                paths.add(f.isAbsolute() ? path : new File(userDir, path).getAbsolutePath());
            }
        }

        if (paths.size() == 1 && paths.get(0).endsWith("classes")) {
            addMavenDependencies(paths);
        }
        return paths.isEmpty() ? userDir : String.join(File.pathSeparator, paths);
    }

    private static boolean isJavaFXJar(String path) {
        String p = path.replace('\\', '/').toLowerCase();
        if (p.contains("/org/openjfx/")) return true;
        for (String m : JAVAFX_MODULES) {
            if (p.contains(m.replace("-", ""))) return true;
        }
        return false;
    }

    private static void addMavenDependencies(List<String> paths) {
        File depDir = new File(System.getProperty("user.dir"), "target/dependency");
        if (depDir.isDirectory()) {
            File[] jars = depDir.listFiles((dir, name) -> name.endsWith(".jar") && !name.contains("javafx"));
            if (jars != null) {
                for (File jar : jars) paths.add(jar.getAbsolutePath());
            }
        }
    }

    private static String findMavenRepository() {
        String repo = System.getProperty("maven.repo.local");
        if (repo != null && new File(repo).exists()) return repo;

        repo = System.getenv("MAVEN_REPO");
        if (repo != null && new File(repo).exists()) return repo;

        String[] commonPaths = {
                "C:\\Env\\Maven\\repository",
                System.getProperty("user.home") + "/.m2/repository"
        };
        for (String path : commonPaths) {
            if (new File(path, "org").exists()) return path;
        }

        return readSettingsXml();
    }

    private static String readSettingsXml() {
        String[] settingsPaths = {
                System.getProperty("user.home") + "/.m2/settings.xml",
                "C:\\Env\\Maven\\conf\\settings.xml"
        };
        for (String sp : settingsPaths) {
            File f = new File(sp);
            if (f.exists()) {
                try {
                    String content = new String(Files.readAllBytes(f.toPath()));
                    int start = content.indexOf("<localRepository>");
                    if (start > 0) {
                        start += 17;
                        int end = content.indexOf("</localRepository>", start);
                        if (end > start) {
                            String repo = content.substring(start, end).trim();
                            if (new File(repo).exists()) return repo;
                        }
                    }
                } catch (Exception ignored) {}
            }
        }
        return null;
    }

    private static void printErrorAndExit(Exception e) {
        System.err.println("启动失败: " + e.getMessage());
        System.err.println("解决方案: mvn javafx:run");
        e.printStackTrace();
        System.exit(1);
    }
}
