package net.corda.parsedata.client.webserver;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.Banner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;

import java.sql.Connection;
import java.sql.Driver;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.Optional;

import static org.springframework.boot.WebApplicationType.SERVLET;

/**
 * Our Spring Boot application.
 */
@SpringBootApplication
public class Starter {
    @Value("${config.db.url:jdbc:h2:tcp://localhost:20042/node}")
    private String url;
    @Value("${config.db.user:sa}")
    private String user;
    @Value("${config.db.passwd:}")
    private String passwd;
    @Value("${config.db.nodb:false}")
    private boolean nodb;
    @Bean
    public Connection dbConnection() throws SQLException {
        /*
        final String url = "jdbc:h2:tcp://localhost:20042/node";
        final String user = "sa";
        final String passwd = "";
         */
        if (nodb) {
            return null;
        }
        try {
            return DriverManager.getConnection(url, user, passwd);
        } catch (SQLException throwables) {
            System.out.println("config.db.nodb is set to false, trying to establish h2 connection." +
                    "Consider setting config.db.nodb=true to fix this error.");
            throw throwables;
        }
    }
    /**
     * Starts our Spring Boot application.
     */
    public static void main(String[] args) {
        SpringApplication app = new SpringApplication(Starter.class);
        app.setBannerMode(Banner.Mode.OFF);
        app.setWebApplicationType(SERVLET);
        app.run(args);
    }
}