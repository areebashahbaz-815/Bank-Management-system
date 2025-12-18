import java.io.*;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.file.*;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;

public class BankManagementSystem {
    // File used to persist account data
    private static final String DATA_FILE = "accounts.txt";
    private static final Scanner scanner = new Scanner(System.in);
    private static final Bank bank = new Bank(DATA_FILE);

    public static void main(String[] args) {
        System.out.println("=== Pine Valley Bank Management System ===");
        bank.loadFromFile(); // load accounts at start

        boolean running = true;
        while (running) {
            printMenu();
            String choice = scanner.nextLine().trim();
            switch (choice) {
                case "1" -> createAccount();
                case "2" -> deposit();
                case "3" -> withdraw();
                case "4" -> transfer();
                case "5" -> viewAccount();
                case "6" -> deleteAccount();
                case "7" -> listAllAccounts();
                case "8" -> {
                    bank.saveToFile();
                    System.out.println("Exiting... data saved. Goodbye!");
                    running = false;
                }
                default -> System.out.println("Invalid choice. Enter number 1-8.");
            }
            System.out.println();
        }
    }

    private static void printMenu() {
        System.out.println("""
                Menu:
                1. Create Account
                2. Deposit
                3. Withdraw
                4. Transfer
                5. View Account
                6. Delete Account
                7. List All Accounts
                8. Save & Exit
                Enter choice (1-8):
                """);
    }

    private static void createAccount() {
        System.out.print("Enter customer name: ");
        String name = scanner.nextLine().trim();
        if (name.isEmpty()) {
            System.out.println("Name cannot be empty.");
            return;
        }
        System.out.print("Initial deposit amount (e.g. 1000.00): ");
        BigDecimal amount = readAmount();
        if (amount == null) return;

        Account acc = bank.createAccount(name, amount);
        System.out.println("Account created successfully!");
        System.out.println(acc);
    }

    private static void deposit() {
        System.out.print("Enter Account Number: ");
        int accNo = readInt();
        if (accNo < 0) return;
        System.out.print("Enter deposit amount: ");
        BigDecimal amt = readAmount();
        if (amt == null) return;
        boolean ok = bank.deposit(accNo, amt);
        System.out.println(ok ? "Deposit successful." : "Deposit failed (account not found).");
    }

    private static void withdraw() {
        System.out.print("Enter Account Number: ");
        int accNo = readInt();
        if (accNo < 0) return;
        System.out.print("Enter withdrawal amount: ");
        BigDecimal amt = readAmount();
        if (amt == null) return;
        boolean ok = bank.withdraw(accNo, amt);
        System.out.println(ok ? "Withdrawal successful." : "Withdrawal failed (insufficient funds or account not found).");
    }

    private static void transfer() {
        System.out.print("From Account Number: ");
        int from = readInt();
        if (from < 0) return;
        System.out.print("To Account Number: ");
        int to = readInt();
        if (to < 0) return;
        System.out.print("Enter transfer amount: ");
        BigDecimal amt = readAmount();
        if (amt == null) return;
        boolean ok = bank.transfer(from, to, amt);
        System.out.println(ok ? "Transfer successful." : "Transfer failed (check accounts and balances).");
    }

    private static void viewAccount() {
        System.out.print("Enter Account Number: ");
        int accNo = readInt();
        if (accNo < 0) return;
        Account acc = bank.getAccount(accNo);
        if (acc == null) System.out.println("Account not found.");
        else System.out.println(acc);
    }

    private static void deleteAccount() {
        System.out.print("Enter Account Number to delete: ");
        int accNo = readInt();
        if (accNo < 0) return;
        boolean ok = bank.deleteAccount(accNo);
        System.out.println(ok ? "Account deleted." : "Delete failed (account not found).");
    }

    private static void listAllAccounts() {
        List<Account> all = bank.getAllAccounts();
        if (all.isEmpty()) {
            System.out.println("No accounts found.");
            return;
        }
        System.out.println("All accounts:");
        for (Account a : all) System.out.println(a);
    }

    // utility: read integer
    private static int readInt() {
        try {
            String s = scanner.nextLine().trim();
            return Integer.parseInt(s);
        } catch (Exception e) {
            System.out.println("Invalid number.");
            return -1;
        }
    }

    // utility: read currency amount as BigDecimal, rounded to 2 decimals
    private static BigDecimal readAmount() {
        try {
            String s = scanner.nextLine().trim();
            BigDecimal bd = new BigDecimal(s).setScale(2, RoundingMode.HALF_UP);
            if (bd.compareTo(BigDecimal.ZERO) <= 0) {
                System.out.println("Amount must be greater than 0.");
                return null;
            }
            return bd;
        } catch (Exception e) {
            System.out.println("Invalid amount format.");
            return null;
        }
    }

    // ----- Inner classes for Bank and Account -----

    /**
     * Bank class handles accounts in memory and persists to a CSV file.
     */
    static class Bank {
        private final Map<Integer, Account> accounts = new HashMap<>();
        private int nextAccountNumber = 1001; // starting account number
        private final Path dataFile;

        Bank(String filename) {
            dataFile = Paths.get(filename);
        }

        // Create new account and return it
        synchronized Account createAccount(String customerName, BigDecimal initialDeposit) {
            int accNo = nextAccountNumber++;
            Account acc = new Account(accNo, customerName, initialDeposit);
            accounts.put(accNo, acc);
            saveToFile(); // persist immediately
            log("Created account " + accNo + " for " + customerName);
            return acc;
        }

        synchronized boolean deposit(int accNo, BigDecimal amount) {
            Account acc = accounts.get(accNo);
            if (acc == null) return false;
            acc.setBalance(acc.getBalance().add(amount));
            saveToFile();
            log("Deposit " + amount + " to " + accNo);
            return true;
        }

        synchronized boolean withdraw(int accNo, BigDecimal amount) {
            Account acc = accounts.get(accNo);
            if (acc == null) return false;
            if (acc.getBalance().compareTo(amount) < 0) return false;
            acc.setBalance(acc.getBalance().subtract(amount));
            saveToFile();
            log("Withdraw " + amount + " from " + accNo);
            return true;
        }

        synchronized boolean transfer(int fromAcc, int toAcc, BigDecimal amount) {
            Account aFrom = accounts.get(fromAcc);
            Account aTo = accounts.get(toAcc);
            if (aFrom == null || aTo == null) return false;
            if (aFrom.getBalance().compareTo(amount) < 0) return false;
            aFrom.setBalance(aFrom.getBalance().subtract(amount));
            aTo.setBalance(aTo.getBalance().add(amount));
            saveToFile();
            log("Transfer " + amount + " from " + fromAcc + " to " + toAcc);
            return true;
        }

        synchronized Account getAccount(int accNo) {
            return accounts.get(accNo);
        }

        synchronized boolean deleteAccount(int accNo) {
            Account removed = accounts.remove(accNo);
            if (removed != null) {
                saveToFile();
                log("Deleted account " + accNo);
                return true;
            }
            return false;
        }

        synchronized List<Account> getAllAccounts() {
            List<Account> list = new ArrayList<>(accounts.values());
            list.sort(Comparator.comparingInt(Account::getAccountNumber));
            return list;
        }

        // Load accounts from CSV file (if exists)
        synchronized void loadFromFile() {
            accounts.clear();
            if (!Files.exists(dataFile)) {
                // no file yet; start fresh
                return;
            }
            try (BufferedReader br = Files.newBufferedReader(dataFile)) {
                String line;
                int maxAcc = nextAccountNumber - 1;
                while ((line = br.readLine()) != null) {
                    line = line.trim();
                    if (line.isEmpty() || line.startsWith("#")) continue;
                    // CSV format: accNo,customerName,balance
                    String[] parts = splitCsv(line);
                    if (parts.length < 3) continue;
                    int accNo = Integer.parseInt(parts[0]);
                    String name = parts[1];
                    BigDecimal bal = new BigDecimal(parts[2]);
                    accounts.put(accNo, new Account(accNo, name, bal));
                    if (accNo > maxAcc) maxAcc = accNo;
                }
                nextAccountNumber = Math.max(nextAccountNumber, maxAcc + 1);
            } catch (Exception e) {
                System.out.println("Error loading data file: " + e.getMessage());
            }
        }

        // Save accounts to CSV file (overwrite)
        synchronized void saveToFile() {
            try (BufferedWriter bw = Files.newBufferedWriter(dataFile, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING)) {
                // optional header
                bw.write("# AccountNumber,CustomerName,Balance");
                bw.newLine();
                for (Account acc : getAllAccounts()) {
                    // Escape commas in name by replacing with semicolon (simple approach)
                    String safeName = acc.getCustomerName().replace(",", ";");
                    bw.write(String.format("%d,%s,%s", acc.getAccountNumber(), safeName, acc.getBalance().toPlainString()));
                    bw.newLine();
                }
            } catch (IOException e) {
                System.out.println("Error saving data file: " + e.getMessage());
            }
        }

        // simple CSV split that won't break here because we replaced commas in names
        private String[] splitCsv(String line) {
            return line.split(",", 3);
        }

        private void log(String msg) {
            DateTimeFormatter fmt = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
            System.out.println("[" + LocalDateTime.now().format(fmt) + "] " + msg);
        }
    }

    /**
     * Account class: holds account data
     */
    static class Account {
        private final int accountNumber;
        private final String customerName;
        private BigDecimal balance;

        Account(int accountNumber, String customerName, BigDecimal balance) {
            this.accountNumber = accountNumber;
            this.customerName = customerName;
            this.balance = balance.setScale(2, RoundingMode.HALF_UP);
        }

        int getAccountNumber() { return accountNumber; }
        String getCustomerName() { return customerName; }
        BigDecimal getBalance() { return balance; }
        void setBalance(BigDecimal newBalance) { this.balance = newBalance.setScale(2, RoundingMode.HALF_UP); }

        @Override
        public String toString() {
            return String.format("Account #%d | Name: %s | Balance: %s", accountNumber, customerName, balance.toPlainString());
        }
    }
}

