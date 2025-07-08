package Controller;

import Model.History;
import Model.Logs;
import Model.Product;
import Model.User;
import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.ArrayList;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;

public class SQLite {
    
    public int DEBUG_MODE = 0;
    String driverURL = "jdbc:sqlite:" + "database.db";
    
    // SECURITY FIX: Added password hashing methods
    private String hashPassword(String password, String salt) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            md.update(salt.getBytes());
            byte[] hashedPassword = md.digest(password.getBytes());
            
            StringBuilder sb = new StringBuilder();
            for (byte b : hashedPassword) {
                sb.append(String.format("%02x", b));
            }
            return sb.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException("Error hashing password", e);
        }
    }
    
    private String generateSalt() {
        SecureRandom random = new SecureRandom();
        byte[] salt = new byte[16];
        random.nextBytes(salt);
        
        StringBuilder sb = new StringBuilder();
        for (byte b : salt) {
            sb.append(String.format("%02x", b));
        }
        return sb.toString();
    }
    
    // MIGRATION FIX: Check if column exists
    private boolean columnExists(String tableName, String columnName) {
        try (Connection conn = DriverManager.getConnection(driverURL)) {
            DatabaseMetaData meta = conn.getMetaData();
            ResultSet rs = meta.getColumns(null, null, tableName, columnName);
            return rs.next();
        } catch (Exception ex) {
            return false;
        }
    }
    
    // MIGRATION FIX: Add missing columns to existing table
    private void migrateUserTable() {
        try (Connection conn = DriverManager.getConnection(driverURL);
             Statement stmt = conn.createStatement()) {
            
            // Add salt column if it doesn't exist
            if (!columnExists("users", "salt")) {
                stmt.execute("ALTER TABLE users ADD COLUMN salt TEXT DEFAULT ''");
                System.out.println("Added salt column to users table");
            }
            
            // Add failed_attempts column if it doesn't exist
            if (!columnExists("users", "failed_attempts")) {
                stmt.execute("ALTER TABLE users ADD COLUMN failed_attempts INTEGER DEFAULT 0");
                System.out.println("Added failed_attempts column to users table");
            }
            
            // Add last_failed_attempt column if it doesn't exist
            if (!columnExists("users", "last_failed_attempt")) {
                stmt.execute("ALTER TABLE users ADD COLUMN last_failed_attempt TEXT");
                System.out.println("Added last_failed_attempt column to users table");
            }
            
            // Migrate existing plain text passwords to hashed passwords
            migrateExistingPasswords();
            
        } catch (Exception ex) {
            System.out.println("Error migrating user table: " + ex.getMessage());
        }
    }
    
    // MIGRATION FIX: Convert existing plain text passwords to hashed passwords
    private void migrateExistingPasswords() {
        String selectSql = "SELECT id, username, password, salt FROM users WHERE salt = '' OR salt IS NULL";
        String updateSql = "UPDATE users SET password = ?, salt = ? WHERE id = ?";
        
        try (Connection conn = DriverManager.getConnection(driverURL);
             PreparedStatement selectStmt = conn.prepareStatement(selectSql);
             PreparedStatement updateStmt = conn.prepareStatement(updateSql)) {
            
            ResultSet rs = selectStmt.executeQuery();
            int migratedCount = 0;
            
            while (rs.next()) {
                int id = rs.getInt("id");
                String username = rs.getString("username");
                String plainPassword = rs.getString("password");
                
                // Generate new salt and hash the plain text password
                String newSalt = generateSalt();
                String hashedPassword = hashPassword(plainPassword, newSalt);
                
                updateStmt.setString(1, hashedPassword);
                updateStmt.setString(2, newSalt);
                updateStmt.setInt(3, id);
                updateStmt.executeUpdate();
                
                migratedCount++;
                System.out.println("Migrated password for user: " + username);
            }
            
            if (migratedCount > 0) {
                System.out.println("Successfully migrated " + migratedCount + " user passwords to secure hashing");
            }
            
        } catch (Exception ex) {
            System.out.println("Error migrating passwords: " + ex.getMessage());
        }
    }
    
    public void createNewDatabase() {
        try (Connection conn = DriverManager.getConnection(driverURL)) {
            if (conn != null) {
                DatabaseMetaData meta = conn.getMetaData();
                System.out.println("Database database.db created.");
            }
        } catch (Exception ex) {
            System.out.print(ex);
        }
    }
    
    public void createHistoryTable() {
        String sql = "CREATE TABLE IF NOT EXISTS history (\n"
            + " id INTEGER PRIMARY KEY AUTOINCREMENT,\n"
            + " username TEXT NOT NULL,\n"
            + " name TEXT NOT NULL,\n"
            + " stock INTEGER DEFAULT 0,\n"
            + " timestamp TEXT NOT NULL\n"
            + ");";

        try (Connection conn = DriverManager.getConnection(driverURL);
            Statement stmt = conn.createStatement()) {
            stmt.execute(sql);
            System.out.println("Table history in database.db created.");
        } catch (Exception ex) {
            System.out.print(ex);
        }
    }
    
    public void createLogsTable() {
        String sql = "CREATE TABLE IF NOT EXISTS logs (\n"
            + " id INTEGER PRIMARY KEY AUTOINCREMENT,\n"
            + " event TEXT NOT NULL,\n"
            + " username TEXT NOT NULL,\n"
            + " desc TEXT NOT NULL,\n"
            + " timestamp TEXT NOT NULL\n"
            + ");";

        try (Connection conn = DriverManager.getConnection(driverURL);
            Statement stmt = conn.createStatement()) {
            stmt.execute(sql);
            System.out.println("Table logs in database.db created.");
        } catch (Exception ex) {
            System.out.print(ex);
        }
    }
     
    public void createProductTable() {
        String sql = "CREATE TABLE IF NOT EXISTS product (\n"
            + " id INTEGER PRIMARY KEY AUTOINCREMENT,\n"
            + " name TEXT NOT NULL UNIQUE,\n"
            + " stock INTEGER DEFAULT 0,\n"
            + " price REAL DEFAULT 0.00\n"
            + ");";

        try (Connection conn = DriverManager.getConnection(driverURL);
            Statement stmt = conn.createStatement()) {
            stmt.execute(sql);
            System.out.println("Table product in database.db created.");
        } catch (Exception ex) {
            System.out.print(ex);
        }
    }
     
    public void createUserTable() {
        // Create table with original structure first
        String sql = "CREATE TABLE IF NOT EXISTS users (\n"
            + " id INTEGER PRIMARY KEY AUTOINCREMENT,\n"
            + " username TEXT NOT NULL UNIQUE,\n"
            + " password TEXT NOT NULL,\n"
            + " role INTEGER DEFAULT 2,\n"
            + " locked INTEGER DEFAULT 0\n"
            + ");";

        try (Connection conn = DriverManager.getConnection(driverURL);
            Statement stmt = conn.createStatement()) {
            stmt.execute(sql);
            System.out.println("Table users in database.db created.");
            
            // MIGRATION FIX: Migrate existing table to add new security columns
            migrateUserTable();
            
        } catch (Exception ex) {
            System.out.print(ex);
        }
    }
    
    public void dropHistoryTable() {
        String sql = "DROP TABLE IF EXISTS history;";

        try (Connection conn = DriverManager.getConnection(driverURL);
            Statement stmt = conn.createStatement()) {
            stmt.execute(sql);
            System.out.println("Table history in database.db dropped.");
        } catch (Exception ex) {
            System.out.print(ex);
        }
    }
    
    public void dropLogsTable() {
        String sql = "DROP TABLE IF EXISTS logs;";

        try (Connection conn = DriverManager.getConnection(driverURL);
            Statement stmt = conn.createStatement()) {
            stmt.execute(sql);
            System.out.println("Table logs in database.db dropped.");
        } catch (Exception ex) {
            System.out.print(ex);
        }
    }
    
    public void dropProductTable() {
        String sql = "DROP TABLE IF EXISTS product;";

        try (Connection conn = DriverManager.getConnection(driverURL);
            Statement stmt = conn.createStatement()) {
            stmt.execute(sql);
            System.out.println("Table product in database.db dropped.");
        } catch (Exception ex) {
            System.out.print(ex);
        }
    }
    
    public void dropUserTable() {
        String sql = "DROP TABLE IF EXISTS users;";

        try (Connection conn = DriverManager.getConnection(driverURL);
            Statement stmt = conn.createStatement()) {
            stmt.execute(sql);
            System.out.println("Table users in database.db dropped.");
        } catch (Exception ex) {
            System.out.print(ex);
        }
    }
    
    // SECURITY FIX: Using PreparedStatement to prevent SQL injection
    public void addHistory(String username, String name, int stock, String timestamp) {
        String sql = "INSERT INTO history(username,name,stock,timestamp) VALUES(?,?,?,?)";
        
        try (Connection conn = DriverManager.getConnection(driverURL);
            PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setString(1, username);
            pstmt.setString(2, name);
            pstmt.setInt(3, stock);
            pstmt.setString(4, timestamp);
            pstmt.executeUpdate();
        } catch (Exception ex) {
            System.out.print(ex);
        }
    }
    
    // SECURITY FIX: Using PreparedStatement to prevent SQL injection
    public void addLogs(String event, String username, String desc, String timestamp) {
        String sql = "INSERT INTO logs(event,username,desc,timestamp) VALUES(?,?,?,?)";
        
        try (Connection conn = DriverManager.getConnection(driverURL);
            PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setString(1, event);
            pstmt.setString(2, username);
            pstmt.setString(3, desc);
            pstmt.setString(4, timestamp);
            pstmt.executeUpdate();
        } catch (Exception ex) {
            System.out.print(ex);
        }
    }
    
    // SECURITY FIX: Using PreparedStatement to prevent SQL injection
    public void addProduct(String name, int stock, double price) {
        String sql = "INSERT INTO product(name,stock,price) VALUES(?,?,?)";
        
        try (Connection conn = DriverManager.getConnection(driverURL);
            PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setString(1, name);
            pstmt.setInt(2, stock);
            pstmt.setDouble(3, price);
            pstmt.executeUpdate();
        } catch (Exception ex) {
            System.out.print(ex);
        }
    }
    
    // SECURITY FIX: Hash password and use PreparedStatement
    public void addUser(String username, String password) {
        String salt = generateSalt();
        String hashedPassword = hashPassword(password, salt);
        String sql = "INSERT INTO users(username,password,salt) VALUES(?,?,?)";
        
        try (Connection conn = DriverManager.getConnection(driverURL);
            PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setString(1, username);
            pstmt.setString(2, hashedPassword);
            pstmt.setString(3, salt);
            pstmt.executeUpdate();
            System.out.println("User " + username + " has been created.");
        } catch (Exception ex) {
            System.out.print(ex);
        }
    }
    
    // SECURITY FIX: Hash password and use PreparedStatement
    public void addUser(String username, String password, int role) {
        String salt = generateSalt();
        String hashedPassword = hashPassword(password, salt);
        String sql = "INSERT INTO users(username,password,salt,role) VALUES(?,?,?,?)";
        
        try (Connection conn = DriverManager.getConnection(driverURL);
            PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setString(1, username);
            pstmt.setString(2, hashedPassword);
            pstmt.setString(3, salt);
            pstmt.setInt(4, role);
            pstmt.executeUpdate();
        } catch (Exception ex) {
            System.out.print(ex);
        }
    }
    
    public ArrayList<History> getHistory(){
        String sql = "SELECT id, username, name, stock, timestamp FROM history";
        ArrayList<History> histories = new ArrayList<History>();
        
        try (Connection conn = DriverManager.getConnection(driverURL);
            Statement stmt = conn.createStatement();
            ResultSet rs = stmt.executeQuery(sql)){
            
            while (rs.next()) {
                histories.add(new History(rs.getInt("id"),
                                   rs.getString("username"),
                                   rs.getString("name"),
                                   rs.getInt("stock"),
                                   rs.getString("timestamp")));
            }
        } catch (Exception ex) {
            System.out.print(ex);
        }
        return histories;
    }
    
    public ArrayList<Logs> getLogs(){
        String sql = "SELECT id, event, username, desc, timestamp FROM logs";
        ArrayList<Logs> logs = new ArrayList<Logs>();
        
        try (Connection conn = DriverManager.getConnection(driverURL);
            Statement stmt = conn.createStatement();
            ResultSet rs = stmt.executeQuery(sql)){
            
            while (rs.next()) {
                logs.add(new Logs(rs.getInt("id"),
                                   rs.getString("event"),
                                   rs.getString("username"),
                                   rs.getString("desc"),
                                   rs.getString("timestamp")));
            }
        } catch (Exception ex) {
            ex.printStackTrace();
        }
        return logs;
    }
    
    public ArrayList<Product> getProduct(){
        String sql = "SELECT id, name, stock, price FROM product";
        ArrayList<Product> products = new ArrayList<Product>();
        
        try (Connection conn = DriverManager.getConnection(driverURL);
            Statement stmt = conn.createStatement();
            ResultSet rs = stmt.executeQuery(sql)){
            
            while (rs.next()) {
                products.add(new Product(rs.getInt("id"),
                                   rs.getString("name"),
                                   rs.getInt("stock"),
                                   rs.getFloat("price")));
            }
        } catch (Exception ex) {
            System.out.print(ex);
        }
        return products;
    }
    
    public ArrayList<User> getUsers(){
        String sql = "SELECT id, username, password, role, locked FROM users";
        ArrayList<User> users = new ArrayList<User>();
        
        try (Connection conn = DriverManager.getConnection(driverURL);
            Statement stmt = conn.createStatement();
            ResultSet rs = stmt.executeQuery(sql)){
            
            while (rs.next()) {
                users.add(new User(rs.getInt("id"),
                                   rs.getString("username"),
                                   "***", // Don't expose password hash
                                   rs.getInt("role"),
                                   rs.getInt("locked")));
            }
        } catch (Exception ex) {}
        return users;
    }
    
    // SECURITY FIX: Using PreparedStatement to prevent SQL injection
    public void removeUser(String username) {
        String sql = "DELETE FROM users WHERE username=?";

        try (Connection conn = DriverManager.getConnection(driverURL);
            PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setString(1, username);
            pstmt.executeUpdate();
            System.out.println("User " + username + " has been deleted.");
        } catch (Exception ex) {
            System.out.print(ex);
        }
    }
    
    // SECURITY FIX: Using PreparedStatement to prevent SQL injection
    public Product getProduct(String name){
        String sql = "SELECT name, stock, price FROM product WHERE name=?";
        Product product = null;
        try (Connection conn = DriverManager.getConnection(driverURL);
            PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setString(1, name);
            ResultSet rs = pstmt.executeQuery();
            if (rs.next()) {
                product = new Product(rs.getString("name"),
                                       rs.getInt("stock"),
                                       rs.getFloat("price"));
            }
        } catch (Exception ex) {
            System.out.print(ex);
        }
        return product;
    }

    // SECURITY FIX: Using PreparedStatement to prevent SQL injection
    public User getUser(String username){
        String sql = "SELECT id, username, password, role, locked FROM users WHERE username=?";
        User user = null;
        try (Connection conn = DriverManager.getConnection(driverURL);
            PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setString(1, username);
            ResultSet rs = pstmt.executeQuery();
            if (rs.next()) {
                user = new User(rs.getInt("id"),
                               rs.getString("username"),
                               rs.getString("password"), // This will be the hash
                               rs.getInt("role"),
                               rs.getInt("locked"));
            }
        } catch (Exception ex) {
            System.out.print(ex);
        }
        return user;
    }

    // SECURITY FIX: Using PreparedStatement to prevent SQL injection
    public boolean isUserExist(String username) {
        String sql = "SELECT username FROM users WHERE username=?";
        boolean isExist = false;

        try (Connection conn = DriverManager.getConnection(driverURL);
            PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setString(1, username);
            ResultSet rs = pstmt.executeQuery();
            if(rs.next()) {
                isExist = true;
            }
        } catch (Exception ex) {
            System.out.print(ex);
        }
        return isExist;
    }
    
    // SECURITY FIX: Using PreparedStatement and password hashing
    public boolean validateUser(String username, String password) {
        String sql = "SELECT password, salt FROM users WHERE username=?";
        boolean isValid = false;

        try (Connection conn = DriverManager.getConnection(driverURL);
            PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setString(1, username);
            ResultSet rs = pstmt.executeQuery();
            if(rs.next()) {
                String storedHash = rs.getString("password");
                String salt = rs.getString("salt");
                
                // Handle migration case where salt might be empty/null
                if (salt == null || salt.isEmpty()) {
                    // This shouldn't happen after migration, but just in case
                    System.out.println("Warning: User " + username + " has no salt. Please run migration.");
                    return false;
                }
                
                String inputHash = hashPassword(password, salt);
                isValid = storedHash.equals(inputHash);
            }
        } catch (Exception ex) {
            System.out.print(ex);
        }
        return isValid;
    }
}