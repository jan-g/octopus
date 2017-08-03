package testing.pkg;

public class MyException extends Exception {
    public String getOwnerIndicator() {
        return MyClass.s;
    }
}
