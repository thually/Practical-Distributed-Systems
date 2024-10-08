package alejandro.salazar.mejia;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
public class SimpleClientApplication {

    private static final Logger log = LoggerFactory.getLogger(SimpleClientApplication.class);

    public static void main(String[] args) {
        SpringApplication.run(SimpleClientApplication.class, args);
    }

}
