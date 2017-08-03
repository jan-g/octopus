package testing.pkg;

public class MyClass implements MyInterface {
    static String s = null;

    public void setStatic(String s) {
        MyClass.s = s;
    }

    public String getStatic() { return MyClass.s; }

    public MyClass2 makePartner() { return new MyClass2(); }

    public boolean matchesPartner(MyClass2 partner) {
        return s.equals(partner.getStatic());
    }

    public void boom() throws MyException {
        throw new MyException();
    }
}
