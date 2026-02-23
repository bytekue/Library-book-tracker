import java.io.*;
import java.nio.file.*;
import java.util.*;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

public class LibraryBookTracker {

    private static int validRecords = 0;
    private static int errorCount = 0;
    private static int searchResults = 0;
    private static int booksAdded = 0;

    public static void main(String[] args) {
        try {
            validateArgs(args);

            Path catalogPath = prepareCatalogFile(args[0]);
            String op = args[1];

            List<Book> books = loadCatalog(catalogPath);

            processOperation(op, books, catalogPath);

            System.out.println("Valid records: " + validRecords);
            System.out.println("Search results: " + searchResults);
            System.out.println("Books added: " + booksAdded);
            System.out.println("Errors encountered: " + errorCount);

        } catch (BookCatalogException e) {
            System.out.println("Error: " + e.getMessage());
            try {
                if (args.length > 0) {
                    logError(Paths.get(args[0]),
                            "BAD OPERATION: \"" + args[1] + "\" -> "
                                    + e.getClass().getSimpleName() + ": " + e.getMessage());
                    errorCount++;
                }
            } catch (Exception ignored) {}
        } catch (Exception e) {
            System.out.println("Unexpected error: " + e.getMessage());
        } finally {
            System.out.println("Thank you for using the Library Book Tracker.");
        }
    }

    private static void validateArgs(String[] args)
            throws InsufficientArgumentsException, InvalidFileNameException {
        if (args.length < 2) {
            throw new InsufficientArgumentsException(
                    "Need 2 arguments: <catalogFile.txt> <operationArgument>");
        }
        if (!args[0].toLowerCase().endsWith(".txt")) {
            throw new InvalidFileNameException("Catalog file must end with .txt");
        }
    }

    private static Path prepareCatalogFile(String catalogFile) throws Exception {
        Path path = Paths.get(catalogFile);

        Path parent = path.getParent();
        if (parent != null && !Files.exists(parent)) {
            Files.createDirectories(parent);
        }

        if (!Files.exists(path)) {
            Files.createFile(path);
        }

        return path;
    }

    private static List<Book> loadCatalog(Path catalogPath) {
        List<Book> books = new ArrayList<>();

        try (BufferedReader reader = Files.newBufferedReader(catalogPath)) {
            String line;
            while ((line = reader.readLine()) != null) {
                try {
                    Book b = parseBookLine(line);
                    books.add(b);
                    validRecords++;
                } catch (BookCatalogException e) {
                    errorCount++;
                    logError(catalogPath,
                            "INVALID LINE: \"" + line + "\" -> "
                                    + e.getClass().getSimpleName() + ": " + e.getMessage());
                }
            }
        } catch (IOException e) {
            System.out.println("Error reading catalog: " + e.getMessage());
        }

        return books;
    }

    private static Book parseBookLine(String line)
            throws MalformedBookEntryException, InvalidISBNException {

        String[] parts = line.split(":");

        if (parts.length != 4) {
            throw new MalformedBookEntryException("Line must have 4 fields");
        }

        String title = parts[0].trim();
        String author = parts[1].trim();
        String isbn = parts[2].trim();
        String copiesStr = parts[3].trim();

        if (title.isEmpty() || author.isEmpty()) {
            throw new MalformedBookEntryException("Author is empty");
        }

        if (!isbn.matches("\\d{13}")) {
            throw new InvalidISBNException("ISBN must be exactly 13 digits");
        }

        int copies;
        try {
            copies = Integer.parseInt(copiesStr);
            if (copies <= 0) {
                throw new MalformedBookEntryException("Copies must be positive");
            }
        } catch (NumberFormatException e) {
            throw new MalformedBookEntryException("Copies must be an integer");
        }

        return new Book(title, author, isbn, copies);
    }

    private static void logError(Path catalogPath, String message) {
        try {
            Path logPath = catalogPath.resolveSibling("errors.log");

            String time = LocalDateTime.now()
                    .format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));

            try (BufferedWriter writer = Files.newBufferedWriter(
                    logPath,
                    StandardOpenOption.CREATE,
                    StandardOpenOption.APPEND)) {

                writer.write("[" + time + "] " + message);
                writer.newLine();
            }
        } catch (IOException e) {
            System.out.println("Failed to write log: " + e.getMessage());
        }
    }

    private static void searchByISBN(String isbn, List<Book> books)
            throws DuplicateISBNException {

        List<Book> matches = new ArrayList<>();

        for (Book b : books) {
            if (b.getIsbn().equals(isbn)) {
                matches.add(b);
            }
        }

        if (matches.size() > 1) {
            throw new DuplicateISBNException(
                    "Multiple books found with ISBN: " + isbn);
        }

        searchResults = matches.size();

        if (matches.isEmpty()) {
            System.out.println("Thank you");
            return;
        }

        printTableHeader();
        printBookRow(matches.get(0));
        System.out.println("Thank you");
    }

    private static void searchByTitle(String keyword, List<Book> books) {
        String key = keyword.toLowerCase();
        List<Book> results = new ArrayList<>();

        for (Book b : books) {
            if (b.getTitle().toLowerCase().contains(key)) {
                results.add(b);
            }
        }

        searchResults = results.size();

        if (results.isEmpty()) {
            System.out.println("Thank you");
            return;
        }

        printTableHeader();
        for (Book b : results) {
            printBookRow(b);
        }

        System.out.println("Thank you");
    }

    private static void addBook(String data, List<Book> books,
                                Path catalogPath)
            throws BookCatalogException {

        String[] parts = data.split(":");
        if (parts.length != 4) {
            throw new MalformedBookEntryException(
                    "Add format must be title:author:isbn:copies");
        }

        String title = parts[0].trim();
        String author = parts[1].trim();
        String isbn = parts[2].trim();
        String copiesStr = parts[3].trim();

        if (title.isEmpty() || author.isEmpty()) {
            throw new MalformedBookEntryException("Author is empty");
        }

        if (!isbn.matches("\\d{13}")) {
            throw new InvalidISBNException(
                    "ISBN must be exactly 13 digits");
        }

        int copies;
        try {
            copies = Integer.parseInt(copiesStr);
            if (copies <= 0) {
                throw new MalformedBookEntryException(
                        "Copies must be positive");
            }
        } catch (NumberFormatException e) {
            throw new MalformedBookEntryException(
                    "Copies must be an integer");
        }

        for (Book b : books) {
            if (b.getIsbn().equals(isbn)) {
                throw new DuplicateISBNException(
                        "Cannot add: ISBN already exists: " + isbn);
            }
        }

        Book newBook = new Book(title, author, isbn, copies);
        books.add(newBook);
        booksAdded = 1;

        books.sort(Comparator.comparing(
                b -> b.getTitle().toLowerCase()));

        saveCatalog(catalogPath, books);

        printTableHeader();
        printBookRow(newBook);
        System.out.println("Thank you");
    }

    private static void saveCatalog(Path catalogPath, List<Book> books)
            throws BookCatalogException {

        try (BufferedWriter writer = Files.newBufferedWriter(
                catalogPath,
                StandardOpenOption.TRUNCATE_EXISTING,
                StandardOpenOption.CREATE)) {

            for (Book b : books) {
                writer.write(b.toFileLine());
                writer.newLine();
            }

        } catch (IOException e) {
            throw new BookCatalogException(
                    "Failed to save catalog: " + e.getMessage());
        }
    }

    private static void processOperation(String op,
                                         List<Book> books,
                                         Path catalogPath)
            throws BookCatalogException {

        if (op.matches("\\d{13}")) {
            searchByISBN(op, books);
        } else if (op.contains(":")) {
            addBook(op, books, catalogPath);
        } else {
            searchByTitle(op, books);
        }
    }

    private static void printTableHeader() {
        System.out.printf("%-30s %-20s %-15s %5s%n",
                "Title", "Author", "ISBN", "Copies");
        System.out.println("--------------------------------------------------------------------------");
    }

    private static void printBookRow(Book b) {
        System.out.printf("%-30s %-20s %-15s %5d%n",
                b.getTitle(),
                b.getAuthor(),
                b.getIsbn(),
                b.getCopies());
    }
}