package org.ioctl.octopus;

import net.sf.cglib.proxy.Callback;
import net.sf.cglib.proxy.Enhancer;
import net.sf.cglib.proxy.MethodInterceptor;
import net.sf.cglib.proxy.MethodProxy;
import org.apache.commons.io.IOUtils;

import java.io.IOException;
import java.io.InputStream;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * "I am passing through unearthly angles; I am approaching—oh, the burning horror of it."
 *    - Frank Belknap Long, The Hounds of Tindalos, 1929
 *
 * Given a set of class or package prefixes, construct a "Forked" ClassLoader which causes classes
 * under those prefixes to be recreated in the context of said ClassLoader. The implementations
 * of those classes are identical to the ones in the parent ClassLoader; however, having a
 * different site of definition, all static members have their own private existence.
 *
 * Additionally, we provide a one-way {@link #bridge(Object)} which permits objects constructed
 * in the forked ClassLoader to be used in the parent scope as objects of their analogous type.
 * Method invocations are bridged down into the forked scope. No attempt (apart from a rudimentary
 * one involving methods on an object which return {@code this}) to perform any kind of
 * identity mapping is done.
 *
 * We cache the constructed proxy classes, which offers some speedup; however, "crossing the bridge"
 * still involves reflection, which is pretty sluggish. For efficient use of this capability,
 * look to access the underlying capabilities of a forked system through a broad-grained service
 * interface.
 */
public class CthulhuClassLoader extends java.lang.ClassLoader {
    private final List<String> prefixes;
    private final Map<String, Class<?>> loaded = new HashMap<>();

    private interface NotForked {}
    private static Class<?> UNFORKED_TYPE = NotForked.class;

    public CthulhuClassLoader(java.lang.ClassLoader parent, List<String> prefixes) {
        super(parent);
        this.prefixes = prefixes;
    }

    @Override
    public synchronized Class<?> loadClass(String s) throws ClassNotFoundException {
        Class<?> definedClass = loaded.get(s);
        if (definedClass == UNFORKED_TYPE)
            return getParent().loadClass(s);
        else if (definedClass != null)
            return definedClass;

        for (String prefix: prefixes) {
            if (s.startsWith(prefix)) {
                try {
                    InputStream in = getResourceAsStream(s.replace('.', '/') + ".class");
                    assert in != null;
                    byte[] cls = IOUtils.toByteArray(in);
                    definedClass = defineClass(s, cls, 0, cls.length);
                    resolveClass(definedClass);
                    loaded.put(s, definedClass);
                    return definedClass;
                } catch (IOException e) {
                    throw new ClassNotFoundException(s, e);
                }
            }
        }
        loaded.put(s, UNFORKED_TYPE);
        return getParent().loadClass(s);
    }

    /**
     * "For now we see through a glass, darkly; but then face to face: now I know in part;
     *  but then shall I know even as also I am known."
     *  - 1 Corinthians 13:12, KJV
     *
     * Lift an object from the scope of this forked ClassLoader into that of its parent, returning
     * a proxy object that can be used as an instance of (a subclass of) the type in the scope of
     * the parent ClassLoader.
     *
     * @param <U> the type upon which the reflected methods are to be found
     */
    public synchronized <T, U> T bridge(U obj) throws AnglesAreWrongException, ClassNotFoundException {
        if (obj == null)
            return null;
        Class<?> subclass = getSubclass(obj);
        if (subclass == UNFORKED_TYPE) {
            return (T) obj;
        }

        try {
            T instance = org.objenesis.ObjenesisHelper.newInstance((Class<T>) subclass);
            Method bc = subclass.getDeclaredMethod("CGLIB$BIND_CALLBACKS", Object.class);
            Method stc = subclass.getDeclaredMethod("CGLIB$SET_THREAD_CALLBACKS", Callback[].class);
            bc.setAccessible(true);
            stc.setAccessible(true);

            stc.invoke(null, (Object) new Callback[]{new BridgeInterceptor<>(obj)});
            bc.invoke(null, instance);
            stc.invoke(null, (Object) null);

            return instance;
        } catch (ReflectiveOperationException e) {
            throw new AnglesAreWrongException(e);
        }
    }

    /**
     * Since we'll only be bridging objects that come out of a set of forked packages through
     * types that are named on the types in those packages, the total set of target types for
     * which we need to look up an enhanced subclass is typically limited. Thus, we cache the
     * constructed proxy classes here.
     *
     * @param obj An object to find the corresponding proxy class for
     * @param <U> the type of the object passed in
     * @return    A type which subclasses {@code U} in the scope of the parent ClassLoader,
     *            whose interfaces should be isomorphic to those of {@code U}
     * @throws ClassNotFoundException
     */
    private synchronized <U> Class<?> getSubclass(U obj) throws ClassNotFoundException {
        Class<?> subclass = enhancers.get(obj.getClass());
        if (subclass != null)
            return subclass;

        String clsName = obj.getClass().getName();
        for (String prefix: prefixes) {
            if (clsName.startsWith(prefix)) {
                Class<?> cls = getParent().loadClass(clsName);
                Enhancer bridge = new Enhancer();
                bridge.setSuperclass(cls);
                bridge.setInterfaces(new Class[]{Tindalos.class});
                /* We want to avoid causing the no-arg underlying constructor to be called again,
                 * which bridge.create() would do. Instead, mint a new instance without calling any
                 * inherited constructors, then wire up the callback using the (slightly baroque)
                 * protocol on the underlying Enhancer instance.
                 */
                bridge.setCallbackType(BridgeInterceptor.class);
                subclass = bridge.createClass();
                enhancers.put(obj.getClass(), subclass);
                return subclass;
            }
        }
        enhancers.put(obj.getClass(), UNFORKED_TYPE);
        return UNFORKED_TYPE;
    }

    private final Map<Class<?>, Class<?>> enhancers = new HashMap<>();

    private final class BridgeInterceptor<U> implements MethodInterceptor {
        private final U target;

        private BridgeInterceptor(U target) {
            this.target = target;
        }

        @Override
        public Object intercept(Object obj, Method method, Object[] args, MethodProxy proxy) throws Throwable {
            if (method.equals(TENDRIL)) {
                // The tendril method: return the target object
                return target;
            }
            Class<?>[] cls = new Class<?>[args.length];
            for (int i = 0; i < args.length; i++) {
                if (args[i] instanceof Tindalos) {
                    args[i] = ((Tindalos) args[i]).$$$_getInternalObject_$$$();
                }
                cls[i] = args[i].getClass();
            }
            Method bridgedMethod = null;
            try {
                bridgedMethod = target.getClass().getMethod(
                        method.getName(),
                        (Class[]) Arrays.stream(args)
                                .map(Object::getClass).toArray(Class[]::new));
            } catch (NoSuchMethodException e) {
                throw new AnglesAreWrongException(e);
            }
            try {
                Object result = bridgedMethod.invoke(target, args);
                if (result == target)
                    return obj;
                return bridge(result);
            } catch (InvocationTargetException e) {
                throw (Throwable) bridge(e.getCause());
            }
        }
    }

    /**
     * We require an accessor method to get at the proxied object. This interface provides that method.
     * It has a horrible name because all reasonable ones are almost certainly taken.
     *
     * (An alternative here would be to give the method a signature that is distinct - say, by
     * using a dummy parameter of this exact type.)
     */
    public interface Tindalos {
        Object $$$_getInternalObject_$$$();
    }

    private static Method TENDRIL;

    static {
        try {
            TENDRIL = Tindalos.class.getDeclaredMethod("$$$_getInternalObject_$$$");
        } catch (NoSuchMethodException e) {
            e.printStackTrace();
        }
    }

    public class AnglesAreWrongException extends RuntimeException {
        AnglesAreWrongException(ReflectiveOperationException e) {
            super(e);
        }
    }
}
