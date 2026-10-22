package psr.sc;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.jar.Attributes;
import java.util.jar.JarFile;
import java.util.jar.Manifest;

import java.net.URL;
import java.net.URLClassLoader;

import javax.swing.JPanel;

/** Загрузка своих модулей/API: jar-плагины из папки modules/ (см. PsrPlugin). */
public final class Plugins {

    public static final File DIR = new File("modules");

    public static List<PsrPlugin> load(List<String> errors) {
        List<PsrPlugin> list = new ArrayList<>();
        DIR.mkdirs();
        File[] jars = DIR.listFiles(f -> f.getName().toLowerCase().endsWith(".jar"));
        if (jars == null) return list;
        for (File jar : jars) {
            try (JarFile jf = new JarFile(jar)) {
                Manifest mf = jf.getManifest();
                if (mf == null) { errors.add(jar.getName() + ": нет манифеста"); continue; }
                Attributes a = mf.getMainAttributes();
                String cn = a.getValue("PSR-Plugin");
                if (cn == null) { errors.add(jar.getName() + ": нет атрибута PSR-Plugin"); continue; }
                URLClassLoader cl = new URLClassLoader(
                    new URL[] { jar.toURI().toURL() }, PsrPlugin.class.getClassLoader());
                Object o = Class.forName(cn, true, cl).newInstance();
                if (!(o instanceof PsrPlugin)) { errors.add(jar.getName() + ": класс не реализует PsrPlugin"); continue; }
                PsrPlugin p = (PsrPlugin) o;
                if (a.getValue("PSR-Title") != null) {
                    list.add(new Wrapped(p, a.getValue("PSR-Title")));
                } else list.add(p);
            } catch (Throwable t) {
                errors.add(jar.getName() + ": " + t);
            }
        }
        return list;
    }

    /** Обёртка: название из манифеста важнее названия внутри jar. */
    static class Wrapped implements PsrPlugin {
        private final PsrPlugin inner; private final String title;
        Wrapped(PsrPlugin inner, String title) { this.inner = inner; this.title = title; }
        @Override public String title() { return title; }
        @Override public JPanel createPanel() { return inner.createPanel(); }
    }

    private Plugins() { }
}
