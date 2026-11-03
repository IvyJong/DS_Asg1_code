import java.nio.charset.StandardCharsets; //provides UTF-8 character encoding when converting passwords into bytes
import java.security.MessageDigest; // for generating MD5 hashes.
import java.security.NoSuchAlgorithmException;  //Handles errors when the requested hashing algorithm is unavailable
import java.util.ArrayList; // Provides array list to store and manage worker threads
import java.util.List; //List interface for managing collections of worker thread
import java.util.concurrent.atomic.AtomicReference; //Provides thread-safe access to a shared search result between multiple threads

public class MD5Search {
    // Declare Total possible ASCII characters: decimal 32 to 126
    private static final int CHAR_START = 32;
    private static final int CHAR_COUNT = 95;

    public static void main(String[] args) throws InterruptedException {

        // step 1: validate the command line inputs
        // to Accept MD5 hash, thread count and password length.
        Config config;

        try {
            config = validateInput(args);
        } catch (IllegalArgumentException e) {
            System.out.println("Error: " + e.getMessage());
            System.out.println(
                "Usage: java MD5Search <hash> <threads> <passwordLength>"
            );
            return;
        }

        // step 2: calculate the total number of password combinations
        // Search all 95 printable ASCII characters (32–126) for that password length
        long totalCombinations = calculateTotalCombinations(
            config.passwordLength
        );

        //print output for hash information
        System.out.println();
        System.out.println("Target MD5: " + config.targetHash);
        System.out.println("Number of threads: " + config.threadCount);
        System.out.println("Password length: " + config.passwordLength);
        System.out.println("Total combinations: " + totalCombinations);
        System.out.println();

        // Shared result used by all worker threads.
        // null means the password has not been found yet.
        AtomicReference<SearchResult> sharedResult =
            new AtomicReference<>(null);

        // step 3: Start measuring execution time 
        long startTime = System.nanoTime();

        // step 4: Create the worker threads
        // Support multithreaded searching.
        List<Thread> workers = createThreads(
            config,
            totalCombinations,
            sharedResult
        );

        // step 5: Start all worker threads
        //Execute the search concurrently.
        for (Thread worker : workers) {
            worker.start();
        }

        // step 6: Wait until all worker threads have terminated
        // Ensure all threads have terminated before reporting results.
        for (Thread worker : workers) {
            worker.join();
        }

        // step 7: Calculate execution time after all workers finish
        // Measure the time taken by the search.
        long endTime = System.nanoTime();

        double executionTime =
            (endTime - startTime) / 1_000_000_000.0;

        // step 8: Display the search result which is the final output of the program
        // Report the search results.
        SearchResult result = sharedResult.get();
        System.out.println("\n========== SEARCH RESULT ==========");
            if (result != null) {
                System.out.println("Password found: " + result.password);
                System.out.println("Found by thread ID: " + result.threadId);
            } else {
                System.out.println("Password not found.");
            }
        
        //java.util.Locale.ROOT prevents your computer's 
        // regional settings from changing how decimal numbers are displayed
        System.out.printf(
            java.util.Locale.ROOT,  
            "Execution time: %.9f seconds%n",
            executionTime);

        System.out.println("===================================");
    }

    /* FOR Input error handling and validation
     * Validate command-line arguments for make sure user input is correct
     */
    private static Config validateInput(String[] args) {

        // Check that exactly three arguments are provided
        if (args.length != 3) {
            throw new IllegalArgumentException(
                "Exactly three arguments are required."
            );
        }

        String targetHash = args[0];

        // Check that the MD5 hash contains exactly 32 hexadecimal characters
        if (!targetHash.matches("(?i)[0-9a-f]{32}")) {
            throw new IllegalArgumentException(
                "Invalid MD5 hash. It must contain 32 hexadecimal characters."
            );
        }

        int threadCount;
        int passwordLength;

        try {
            threadCount = Integer.parseInt(args[1]);
            passwordLength = Integer.parseInt(args[2]);
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException(
                "Thread count and password length must be integers."
            );
        }

        // Allowed thread count: 1 to 10
        if (threadCount < 1 || threadCount > 10) {
            throw new IllegalArgumentException(
                "Thread count must be between 1 and 10."
            );
        }

        // Supported password length: 2 to 6
        if (passwordLength < 2 || passwordLength > 6) {
            throw new IllegalArgumentException(
                "Password length must be between 2 and 6."
            );
        }

        return new Config(targetHash.toLowerCase(),
            threadCount,
            passwordLength);
    }

    // Generate MD5 hash of a password candidate function
    
    public static String getMd5(String input) {
        try {
            MessageDigest md = MessageDigest.getInstance("MD5");
            return getMd5(md, input);

        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException("MD5 algorithm not available.", e);
        }
    }

    // Reuse an existing MessageDigest to calculate the MD5 hash.
    private static String getMd5(MessageDigest md, String input) {

        byte[] messageDigest =
            md.digest(input.getBytes(StandardCharsets.UTF_8));

        return java.util.HexFormat.of().formatHex(messageDigest);
    }

    // Calculate total search-space combinations function
    private static long calculateTotalCombinations(int passwordLength) {

        long total = 1;

        for (int i = 0; i < passwordLength; i++) {
            total *= CHAR_COUNT;
        }

        return total;
    }

    // Dynamically create worker threads function
    private static List<Thread> createThreads(
            Config config,
            long totalCombinations,
            AtomicReference<SearchResult> sharedResult) {

        List<Thread> workers = new ArrayList<>();

        // FRIEND: partitionSearchSpace() must return one range per thread.
        List<SearchRange> ranges = partitionSearchSpace(
            totalCombinations,
            config.threadCount
        );

        // Create the requested number of worker threads
        for (int i = 0; i < config.threadCount; i++) {

            SearchRange range = ranges.get(i);

            Worker task = new Worker(
                range,
                config.targetHash,
                config.passwordLength,
                sharedResult
            );

            Thread thread = new Thread(task, "Worker-" + (i + 1));

            workers.add(thread);
        }

        return workers;
    }

    // divide the search space into non-overlapping ranges for each thread
    // each range uses a start-inclusive and end-exclusive index
    private static List<SearchRange> partitionSearchSpace(
            long totalCombinations,
            int threadCount) {

        // store the search range assigned to each thread
        List<SearchRange> ranges = new ArrayList<>();

        for (int i = 0; i < threadCount; i++) {

            // calculate the starting and ending index for each thread
            long start = totalCombinations * i / threadCount;
            long end = totalCombinations * (i + 1) / threadCount;

             // add the calculated range to the list
            ranges.add(new SearchRange(start, end));
        }

        return ranges;
    }

    // convert a numeric index into a password using base-95 conversion
    private static String passwordFromIndex(
            long index,
            int passwordLength) {

        // create a character array to store the password
        char[] password = new char[passwordLength];

        // generate characters from right to left
        for (int i = passwordLength - 1; i >= 0; i--) {

            // get the remainder to determine the next character
            int remainder = (int) (index % CHAR_COUNT);

            // convert the remainder into its corresponding ASCII character
            password[i] = (char) (CHAR_START + remainder);

            // move to the next position in the base-95 conversion
            index = index / CHAR_COUNT;
        }

        return new String(password);
    }

    private static class Worker implements Runnable {

        private final SearchRange range;
        private final String targetHash;
        private final int passwordLength;

        // Shared result allows all threads to know when a password
        // has already been found.
        private final AtomicReference<SearchResult> sharedResult;

        public Worker(
                SearchRange range,
                String targetHash,
                int passwordLength,
                AtomicReference<SearchResult> sharedResult) {

            this.range = range;
            this.targetHash = targetHash;
            this.passwordLength = passwordLength;
            this.sharedResult = sharedResult;
        }

        @Override
        public void run() {
            // Create one MD5 digest instance for this worker.
            final MessageDigest md;

            try {
                md = MessageDigest.getInstance("MD5");
            } catch (NoSuchAlgorithmException e) {
                throw new RuntimeException("MD5 algorithm not available.", e);
            }

            // Display when this worker starts searching.
            System.out.println(
                Thread.currentThread().getName() + " started searching."
            );

            // Search through the worker's assigned range.
            for (long index = range.startInclusive;
                index < range.endExclusive;
                index++) {

                // Check whether another worker has found the password.
                if (sharedResult.get() != null) {
                    System.out.println(
                        Thread.currentThread().getName()
                        + " detected that the password was found. Stopping search."
                    );
                    return;
                }

                // Generate a password candidate.
                String candidate = passwordFromIndex(
                    index,
                    passwordLength
                );

                // Calculate MD5 using the reusable digest instance.
                String candidateHash = getMd5(md, candidate);

                // Compare with the target MD5 hash.
                if (candidateHash.equals(targetHash)) {

                    // Store the recovered password and thread ID.
                    SearchResult foundResult = new SearchResult(
                        candidate,
                        Thread.currentThread().threadId()
                    );

                    // Publish the result to other workers.
                    if (sharedResult.compareAndSet(null, foundResult)) {
                        System.out.println(
                            Thread.currentThread().getName()
                        + " found the password: " + candidate
                        );
                    }   

                    // Stop this worker.
                    return;
                }
            }
        }
    }

    // Supporting Class, search range assigned to each thread
    private static class SearchRange {

        // starting index of the search range (included)
        private final long startInclusive;

        // ending index of the search range (excluded)
        private final long endExclusive;

        // initialize the starting and ending index of the range
        public SearchRange(long startInclusive, long endExclusive) {
            this.startInclusive = startInclusive;
            this.endExclusive = endExclusive;
        }
    }

    // Supporting Class, store the recovered password and the ID of the thread that found it
    private static class SearchResult {

        // store the password that matches the target MD5 hash
        private final String password;

        // store the ID of the thread that found the password
        private final long threadId;

        // initialize the recovered password and thread ID
        public SearchResult(String password, long threadId) {
            this.password = password;
            this.threadId = threadId;
        }
    }

    // Supporting Class, store the validated command-line input values
    private static class Config {

        // store the target MD5 hash entered by the user
        private final String targetHash;

        // store the number of worker threads to be used
        private final int threadCount;

        // store the length of the password to be searched
        private final int passwordLength;

        // initialize the validated input values
        public Config(
            String targetHash,
            int threadCount,
            int passwordLength) {

            this.targetHash = targetHash;
            this.threadCount = threadCount;
            this.passwordLength = passwordLength;
        }
    }
}