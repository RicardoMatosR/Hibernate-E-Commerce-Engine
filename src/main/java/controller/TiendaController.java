package controller;

import com.google.gson.Gson;
import dao.ProductDAO;
import dao.UserDAO;
import model.Product;
import model.ProductResponse;
import model.User;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.List;

import database.HibernateUtil;
import org.hibernate.Session;
import org.hibernate.Transaction;
import security.PasswordHasher;

public class TiendaController {

    private UserDAO userDAO;
    private ProductDAO productDAO;

    public TiendaController() {
        userDAO = new UserDAO();
        productDAO = new ProductDAO();
    }

    // PRODUCT METHODS

    private static final String PRODUCTS_URL = "https://dummyjson.com/products";

    // One HttpClient for the whole app: it is reusable and keeps connections open.
    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .build();

    // Downloads the products from DummyJSON and saves them. Because each product keeps
    // its DummyJSON id, merge() inserts the new ones and updates the stock of the existing ones.
    // Returns true only if the API answered 200 and the products were saved.
    public boolean importProducts() {
        try {
            HttpRequest request = HttpRequest.newBuilder()
                    .GET()
                    .uri(URI.create(PRODUCTS_URL))
                    .timeout(Duration.ofSeconds(15))
                    .header("Accept", "application/json")
                    .build();
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() != 200) {
                System.out.println("DummyJSON answered HTTP " + response.statusCode() + ". Nothing was imported.");
                return false;
            }

            ProductResponse productResponse = new Gson().fromJson(response.body(), ProductResponse.class);
            if (productResponse == null || productResponse.getProducts() == null) {
                System.out.println("Unexpected JSON from DummyJSON. Nothing was imported.");
                return false;
            }
            return productDAO.insertProducts(productResponse.getProducts());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            System.out.println("Import interrupted.");
            return false;
        } catch (Exception e) {
            System.out.println("Connection failed when importing products: " + e.getMessage());
            return false;
        }
    }

    public void listAllProducts() {
        List<Product> products = productDAO.getAllProducts();
        if (products.isEmpty()) {
            System.out.println("No products available.");
        } else {
            products.forEach(p -> System.out.println("ID: " + p.getId() + " | Name: " + p.getTitle() + " | Price: "
                    + p.getPrice() + "€ | Stock: " + p.getStock()));
        }
    }

    // Buys one unit of a product for an already logged-in user, in ONE atomic transaction.
    // Stock and wallet are checked and updated by the database in a single UPDATE each
    // ("... WHERE stock >= 1"), so two customers buying the last unit at the same time
    // can never both succeed (no race condition). If anything fails, everything is rolled back.
    public boolean sellProduct(Long userId, int idProduct) {
        Transaction transaction = null;

        try (Session session = HibernateUtil.getSessionFactory().openSession()) {
            transaction = session.beginTransaction();

            // 1. The product must exist
            Product product = session.find(Product.class, (long) idProduct);
            if (product == null) {
                System.out.println("Product not found.");
                transaction.rollback();
                return false;
            }
            double price = product.getPrice();

            // 2. Take one unit of stock, only if there is any left (check + update in one step)
            int stockUpdated = session.createMutationQuery(
                            "UPDATE Product p SET p.stock = p.stock - 1 WHERE p.id = :id AND p.stock >= 1")
                    .setParameter("id", product.getId())
                    .executeUpdate();
            if (stockUpdated == 0) {
                System.out.println("Out of stock for: " + product.getTitle());
                transaction.rollback();
                return false;
            }

            // 3. Charge the wallet, only if the user has enough money (check + update in one step)
            int walletUpdated = session.createMutationQuery(
                            "UPDATE User u SET u.wallet = u.wallet - :price WHERE u.id = :id AND u.wallet >= :price")
                    .setParameter("price", price)
                    .setParameter("id", userId)
                    .executeUpdate();
            if (walletUpdated == 0) {
                System.out.println("Insufficient funds for: " + product.getTitle() + " (" + price + " Euros).");
                transaction.rollback(); // also gives the stock unit back
                return false;
            }

            // 4. Both steps worked: make them permanent together
            transaction.commit();
            System.out.println("Purchase successful: " + product.getTitle());
            System.out.println("Remaining balance: " + userDAO.getWalletBalance(userId) + " Euros");
            return true;

        } catch (Exception e) {
            if (transaction != null && transaction.isActive()) {
                transaction.rollback();
            }
            System.out.println("Transaction error. Rollback executed: " + e.getMessage());
            return false;
        }
    }

    // USER METHODS

    public void seedDatabase() {
        userDAO.seedDatabase();
    }

    public User login(String email, String password) {
        return userDAO.login(email, password);
    }

    public Double getWalletBalance(Long userId) {
        return userDAO.getWalletBalance(userId);
    }

    public model.Profile getProfileById(Long id) {
        return userDAO.getProfileById(id);
    }

    // Registers a new user. The password is stored as a salted hash (PBKDF2), never in plain text.
    public boolean signInUser(User user) {
        user.setPassword(PasswordHasher.hash(user.getPassword()));
        return userDAO.insertUser(user);
    }

    public void findUserByEmail(String email) {
        User user = userDAO.getUserByEmail(email);
        if (user != null) {
            user.showData();
        } else {
            System.out.println("No user found with email: " + email);
        }
    }

    public void listAdmins() {
        List<User> users = userDAO.getUsersByProfile(1L); // 1 = Admin
        if (users.isEmpty()) {
            System.out.println("No admins available.");
        } else {
            users.forEach(User::showData);
        }
    }

    public void listCustomers() {
        List<User> users = userDAO.getUsersByProfile(2L); // 2 = Customer
        if (users.isEmpty()) {
            System.out.println("No customers available.");
        } else {
            users.forEach(User::showData);
        }
    }

    public void deleteUser(Long id) {
        if (userDAO.deleteUser(id)) {
            System.out.println("User deleted successfully.");
        } else {
            System.out.println("Delete failed: User not found.");
        }
    }

    public void findUsers(String name) {
        List<User> lista = userDAO.getUsersByName(name);
        if (!lista.isEmpty()) {
            lista.forEach(User::showData);
        } else {
            System.out.println("No users found matching your criteria.");
        }
    }

    public void updateUserDetails(Long id, String name, String lastName, String email) {
        User user = userDAO.updateUserById(id, name, lastName, email);
        if (user != null) {
            System.out.println("User updated successfully.");
        } else {
            System.out.println("Update failed: User not found.");
        }
    }
}
