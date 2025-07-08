package Controller;

import Model.History;
import Model.Logs;
import Model.Product;
import Model.User;
import View.Frame;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.Date;

public class Main {
    
    public SQLite sqlite;
    
    public static void main(String[] args) {
        new Main().init();
    }
    
    public void init(){
        // Initialize a driver object
        sqlite = new SQLite();

        // SECURITY FIX: Uncommented database initialization - this is critical!
        // Create a database
        sqlite.createNewDatabase();
        
        // Drop users table if needed (only for development - remove in production)
        // sqlite.dropHistoryTable();
        // sqlite.dropLogsTable();
        // sqlite.dropProductTable();
        // sqlite.dropUserTable();
        
        // Create tables if they don't exist
        sqlite.createHistoryTable();
        sqlite.createLogsTable();
        sqlite.createProductTable();
        sqlite.createUserTable();
        
        // Add sample history (only if tables are empty)
        if (sqlite.getHistory().isEmpty()) {
            sqlite.addHistory("admin", "Antivirus", 1, "2019-04-03 14:30:00.000");
            sqlite.addHistory("manager", "Firewall", 1, "2019-04-03 14:30:01.000");
            sqlite.addHistory("staff", "Scanner", 1, "2019-04-03 14:30:02.000");
        }
        
        // Add sample logs (only if tables are empty)
        if (sqlite.getLogs().isEmpty()) {
            sqlite.addLogs("NOTICE", "admin", "User creation successful", new Timestamp(new Date().getTime()).toString());
            sqlite.addLogs("NOTICE", "manager", "User creation successful", new Timestamp(new Date().getTime()).toString());
            sqlite.addLogs("NOTICE", "admin", "User creation successful", new Timestamp(new Date().getTime()).toString());
        }
        
        // Add sample products (only if tables are empty)
        if (sqlite.getProduct().isEmpty()) {
            sqlite.addProduct("Antivirus", 5, 500.0);
            sqlite.addProduct("Firewall", 3, 1000.0);
            sqlite.addProduct("Scanner", 10, 100.0);
        }

        // Add sample users (only if no users exist)
        if (sqlite.getUsers().isEmpty()) {
            System.out.println("Creating default users...");
            sqlite.addUser("admin", "qwerty1234", 5);      
            sqlite.addUser("manager", "qwerty1234", 4);      
            sqlite.addUser("staff", "qwerty1234", 3);      
            sqlite.addUser("client1", "qwerty1234", 2);    
            sqlite.addUser("client2", "qwerty1234", 2);    
            System.out.println("Default users created.");
            System.out.println("Default login credentials:");
            System.out.println("Admin: admin / qwerty1234");
            System.out.println("Manager: manager / qwerty1234");
            System.out.println("Staff: staff / qwerty1234");
            System.out.println("Client: client1 / qwerty1234");
        } else {
            System.out.println("Existing users found. Database migration completed.");
            System.out.println("Your existing login credentials should still work!");
        }
        
        // Initialize User Interface
        Frame frame = new Frame();
        frame.init(this);
    }
}