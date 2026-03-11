import java.util.List;
import java.util.stream.IntStream;

public class App {

    static int factorial(int n) {
        return IntStream.rangeClosed(1, n).reduce(1, (a, b) -> a * b);
    }

    static boolean isPrime(int n) {
        if (n < 2)
            return false;
        return IntStream.rangeClosed(2, (int) Math.sqrt(n))
                .noneMatch(i -> n % i == 0);
    }

    public static void main(String[] args) {
        record TestCase(String name, boolean passed) {
        }

        var tests = List.of(
                new TestCase("factorial(5)==120", factorial(5) == 120),
                new TestCase("factorial(0)==1", factorial(0) == 1),
                new TestCase("isPrime(17)", isPrime(17)),
                new TestCase("!isPrime(4)", !isPrime(4)));

        int passed = 0;
        for (var t : tests) {
            String status = t.passed() ? "PASS" : "FAIL";
            System.out.printf("  [%s] %s%n", status, t.name());
            if (t.passed())
                passed++;
        }
        System.out.printf("%nResults: %d/%d passed%n", passed, tests.size());
        if (passed != tests.size())
            System.exit(1);
    }
}
