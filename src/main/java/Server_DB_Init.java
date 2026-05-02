
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication(scanBasePackages = "Network")
public class Server_DB_Init {
    public static void main(String[] args) {
        SpringApplication.run(Server_DB_Init.class, args);
    }
}