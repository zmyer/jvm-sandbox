package com.alibaba.jvm.sandbox.agent;

import java.io.Closeable;
import java.lang.reflect.Method;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.Collection;
import java.util.jar.JarFile;

/**
 * 加载Sandbox用的ClassLoader
 * Created by luanjia@taobao.com on 2016/10/26.
 */
class SandboxClassLoader extends URLClassLoader {

    private final String namespace;
    private final String path;

    SandboxClassLoader(final String namespace,
                       final String sandboxCoreJarFilePath) throws MalformedURLException {
        super(new URL[]{new URL("file:" + sandboxCoreJarFilePath)});
        this.namespace = namespace;
        this.path = sandboxCoreJarFilePath;
    }

    @Override
    protected synchronized Class<?> loadClass(String name, boolean resolve) throws ClassNotFoundException {
        final Class<?> loadedClass = findLoadedClass(name);
        if (loadedClass != null) {
            return loadedClass;
        }

//        // 优先从parent（SystemClassLoader）里加载系统类，避免抛出ClassNotFoundException
//        if(name != null && (name.startsWith("sun.") || name.startsWith("java."))) {
//            return super.loadClass(name, resolve);
//        }

        try {
            Class<?> aClass = findClass(name);
            if (resolve) {
                resolveClass(aClass);
            }
            return aClass;
        } catch (Exception e) {
            return super.loadClass(name, resolve);
        }
    }

    @Override
    public String toString() {
        return String.format("SandboxClassLoader[namespace=%s;path=%s;]", namespace, path);
    }


    public void closeIfPossible() {

        // 如果是JDK7+的版本, URLClassLoader实现了Closeable接口，直接调用即可
        if (this instanceof Closeable) {
            try {
                final Method closeMethod = URLClassLoader.class.getMethod("close");
                closeMethod.invoke(this);
            } catch (Throwable cause) {
                // ignore...
            }
            return;
        }


        // 对于JDK6的版本，URLClassLoader要关闭起来就显得有点麻烦，这里弄了一大段代码来稍微处理下
        // 而且还不能保证一定释放干净了，至少释放JAR文件句柄是没有什么问题了
        try {
            final Object sun_misc_URLClassPath = URLClassLoader.class.getDeclaredField("ucp").get(this);
            final Object java_util_Collection = sun_misc_URLClassPath.getClass().getDeclaredField("loaders").get(sun_misc_URLClassPath);

            for (Object sun_misc_URLClassPath_JarLoader :
                    ((Collection) java_util_Collection).toArray()) {
                try {
                    final JarFile java_util_jar_JarFile = (JarFile) sun_misc_URLClassPath_JarLoader.getClass().getDeclaredField("jar").get(sun_misc_URLClassPath_JarLoader);
                    java_util_jar_JarFile.close();
                } catch (Throwable t) {
                    // if we got this far, this is probably not a JAR loader so skip it
                }
            }

        } catch (Throwable cause) {
            // ignore...
        }

    }

}
