import java.io.*;
import java.nio.file.*;
import java.util.*;

public class LibraryBookTracker {

    public static void main(String[] args) {
        try {
            validateArgs(args);

            Path catalogPath = prepareCatalogFile(args[0]);
            String op = args[1];

            System.out.println("Catalog: " + catalogPath.toAbsolutePath());
            System.out.println("Operation: " + op);

            List<Book> books = loadCatalog(catalogPath);
            System.out.println("Loaded books: " + books.size());

            

            processOperation(op, books, catalogPath);

            

        } catch (BookCatalogException e) {
            System.out.println("Error: " + e.getMessage());
           
        } catch (Exception e) {
            System.out.println("Unexpected error: " + e.getMessage());
        }
    }

    private static void validateArgs(String[] args) throws InsufficientArgumentsException, InvalidFileNameException {
        if (args.length < 2) {
            throw new InsufficientArgumentsException("Need 2 arguments: <catalogFile.txt> <operationArgument>");
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
            } catch (BookCatalogException e) {
                logError(catalogPath, "Invalid line: " + line + " -> " + e.getMessage());
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
        throw new MalformedBookEntryException("Title/Author cannot be empty");
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
        try (BufferedWriter writer = Files.newBufferedWriter(
                logPath,
                StandardOpenOption.CREATE,
                StandardOpenOption.APPEND)) {

            writer.write(message);
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

    if (matches.isEmpty()) {
        System.out.println("No book found with this ISBN.");
        return;
    }

    if (matches.size() > 1) {
        throw new DuplicateISBNException(
                "Multiple books found with ISBN: " + isbn);
    }

    
    printTableHeader();
    printBookRow(matches.get(0));
}

private static void searchByTitle(String keyword, List<Book> books) {
    String key = keyword.toLowerCase();
    List<Book> results = new ArrayList<>();

    for (Book b : books) {
        if (b.getTitle().toLowerCase().contains(key)) {
            results.add(b);
        }
    }

    if (results.isEmpty()) {
        System.out.println("No books found matching: " + keyword);
        return;
    }

    printTableHeader();
    for (Book b : results) {
        printBookRow(b);
    }
}

private static void printTableHeader() {
    System.out.printf("%-25s %-20s %-15s %-6s%n",
            "Title", "Author", "ISBN", "Copies");
    System.out.println("-----------------------------------------------------------------------");
}

private static void printBookRow(Book b) {
    System.out.printf("%-25s %-20s %-15s %-6d%n",
            b.getTitle(),
            b.getAuthor(),
            b.getIsbn(),
            b.getCopies());
}

private static void addBook(String data, List<Book> books, Path catalogPath)
        throws BookCatalogException {

    // data: title:author:isbn:copies
    String[] parts = data.split(":");
    if (parts.length != 4) {
        throw new MalformedBookEntryException("Add format must be title:author:isbn:copies");
    }

    String title = parts[0].trim();
    String author = parts[1].trim();
    String isbn = parts[2].trim();
    String copiesStr = parts[3].trim();

    if (title.isEmpty() || author.isEmpty()) {
        throw new MalformedBookEntryException("Title/Author cannot be empty");
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

   
    for (Book b : books) {
        if (b.getIsbn().equals(isbn)) {
            throw new DuplicateISBNException("Cannot add: ISBN already exists: " + isbn);
        }
    }

    Book newBook = new Book(title, author, isbn, copies);
    books.add(newBook);

    
    books.sort(Comparator.comparing(b -> b.getTitle().toLowerCase()));

    saveCatalog(catalogPath, books);

   
    System.out.println("Book added successfully:");
    printTableHeader();
    printBookRow(newBook);
}

private static void saveCatalog(Path catalogPath, List<Book> books) throws BookCatalogException {
    try (BufferedWriter writer = Files.newBufferedWriter(
            catalogPath,
            StandardOpenOption.TRUNCATE_EXISTING,
            StandardOpenOption.CREATE)) {

        for (Book b : books) {
            writer.write(b.toFileLine());
            writer.newLine();
        }

    } catch (IOException e) {
        throw new BookCatalogException("Failed to save catalog: " + e.getMessage());
    }
}

private static void processOperation(
        String op,
        List<Book> books,
        Path catalogPath) throws BookCatalogException {

   
    if (op.matches("\\d{13}")) {
        searchByISBN(op, books);
    }

   
    else if (op.contains(":")) {
        addBook(op, books, catalogPath);
    }

    
    else {
        searchByTitle(op, books);
    }
}


}