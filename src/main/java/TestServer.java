
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication(scanBasePackages = "main.java.Network")   // ← explicit scan
public class TestServer {
    public static void main(String[] args) {
        SpringApplication.run(TestServer.class, args);
    }
}