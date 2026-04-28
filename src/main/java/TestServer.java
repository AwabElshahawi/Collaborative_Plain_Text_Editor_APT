
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication(scanBasePackages = "Network")
public class TestServer {
    public static void main(String[] args) {
        SpringApplication.run(TestServer.class, args);
    }
}