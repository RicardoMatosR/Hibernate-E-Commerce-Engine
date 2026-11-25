package database;

import org.hibernate.SessionFactory;
import org.hibernate.cfg.Configuration;

public class HibernateUtil {

    private static final SessionFactory sessionFactory = buildSessionFactory();

    private static SessionFactory buildSessionFactory() {
        try {
            Configuration configuration = new Configuration();
            configuration.configure();

            // Connection data comes from environment variables, never from the repository.
            // The defaults match docker-compose.yml so the project runs out of the box locally.
            configuration.setProperty("hibernate.connection.url",
                    env("DB_URL", "jdbc:mysql://localhost:3306/tienda_ces"));
            configuration.setProperty("hibernate.connection.username", env("DB_USER", "tienda"));
            configuration.setProperty("hibernate.connection.password", env("DB_PASSWORD", "tienda"));

            return configuration.buildSessionFactory();
        } catch (Throwable ex) {
            throw new ExceptionInInitializerError(ex);
        }
    }

    private static String env(String name, String defaultValue) {
        String value = System.getenv(name);
        return (value == null || value.isBlank()) ? defaultValue : value;
    }

    public static SessionFactory getSessionFactory() {
        return sessionFactory;
    }

    public static void shutdown() {
        getSessionFactory().close();
    }
}
