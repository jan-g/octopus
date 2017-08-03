package testing.pkg;

public class MyClass2 {
    public MyClass2() {}

    public void setStatic(String s) {
        MyClass.s = s;
    }

    public String getStatic() { return MyClass.s; }
}
