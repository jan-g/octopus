package org.ioctl.octopus;

import org.ioctl.octopus.CthulhuClassLoader;
import org.junit.Test;
import testing.pkg.MyClass;
import testing.pkg.MyClass2;
import testing.pkg.MyException;
import testing.pkg.MyInterface;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.Arrays;

import static org.hamcrest.CoreMatchers.*;
import static org.junit.Assert.*;

public class CthulhuClassLoaderTest {
    public CthulhuClassLoader cl = new CthulhuClassLoader(getClass().getClassLoader(),
            Arrays.asList("testing.pkg."));

    private Object makeAlienObject() throws ClassNotFoundException, IllegalAccessException, InstantiationException {
        Class<?> c = cl.loadClass("testing.pkg.MyClass");
        return c.newInstance();
    }

    private MyClass makeAlienMyClass() throws IllegalAccessException, InstantiationException, ClassNotFoundException {
        return cl.bridge(makeAlienObject());
    }

    @Test
    public void canLoadTestClass() {
        MyClass m = new MyClass();
        m.setStatic("foo");
        assertThat(m.getStatic(), is("foo"));
    }

    @Test
    public void canSubloadTestClass() throws IllegalAccessException, InstantiationException, ClassNotFoundException, NoSuchMethodException, InvocationTargetException {
        MyClass myc = new MyClass();
        Object obj = makeAlienObject();
        Method get = obj.getClass().getDeclaredMethod("getStatic");
        Method set = obj.getClass().getDeclaredMethod("setStatic", String.class);
        assert(get.invoke(obj) == null);
        myc.setStatic("foo");
        set.invoke(obj, "bar");
        assertThat(myc.getStatic(), is("foo"));
        assertThat(get.invoke(obj), is("bar"));
    }

    @Test
    public void bridgedObjects() throws Exception {
        MyClass first = new MyClass();
        first.setStatic("baz");
        Class<?> c = cl.loadClass("testing.pkg.MyClass");
        Object obj = c.newInstance();
        Method set = obj.getClass().getDeclaredMethod("setStatic", String.class);
        set.invoke(obj, "quux");
        MyClass second = cl.bridge(obj);
        assertThat(first.getStatic(), is("baz"));
        assertThat(second.getStatic(), is("quux"));
    }

    @Test
    public void getSecondObject() throws Exception {
        MyClass first = new MyClass();
        first.setStatic("blue");
        MyClass2 partner = first.makePartner();

        MyClass second = makeAlienMyClass();
        MyClass2 pard = second.makePartner();
        second.setStatic("green");

        assertThat(partner.getStatic(), is("blue"));
        assertThat(pard.getStatic(), is("green"));
    }

    @Test
    public void interfacesShouldBeImplemented() throws Exception {
        MyClass first = new MyClass();
        assertThat(first, isA(MyInterface.class));

        MyClass second = makeAlienMyClass();
        assertThat(second, isA(MyInterface.class));
    }

    @Test
    public void interfacesUnderTheHoodAreDifferent() throws Exception {
        MyClass first = new MyClass();
        Object second = makeAlienObject();
        assertThat(second, is(not(instanceOf(MyInterface.class))));

        Class<?> c = cl.loadClass("testing.pkg.MyInterface");
        assertThat(second, is(instanceOf(c)));
        assertThat(first, is(not(instanceOf(c))));
    }

    @Test
    public void bridgesAreStrippedOffWhenPassedAsArguments() throws Exception {
        MyClass first = new MyClass();
        MyClass2 partner = first.makePartner();
        MyClass second = makeAlienMyClass();
        MyClass2 pard = second.makePartner();

        first.setStatic("bah");
        second.setStatic("humbug");

        assertTrue(first.matchesPartner(partner));
        assertFalse(first.matchesPartner(pard));
        assertTrue(second.matchesPartner(pard));
    }

    @Test(expected = CthulhuClassLoader.AnglesAreWrongException.class)
    public void nativeObjectsAreNotWrappedOnTheWayDown() throws Exception {
        MyClass first = new MyClass();
        MyClass2 partner = first.makePartner();
        MyClass second = makeAlienMyClass();
        MyClass2 pard = second.makePartner();

        first.setStatic("bah");
        second.setStatic("humbug");

        assertFalse(second.matchesPartner(partner));
    }

    @Test
    public void exceptionsAreWrapped() throws Exception {
        MyClass first = new MyClass();
        MyClass second = makeAlienMyClass();

        first.setStatic("bang");
        second.setStatic("kapow");

        try {
            first.boom();
        } catch (MyException e) {
            assertThat(e.getOwnerIndicator(), is("bang"));
        }

        try {
            second.boom();
        } catch (MyException e) {
            assertThat(e.getOwnerIndicator(), is("kapow"));
        }
    }
}
