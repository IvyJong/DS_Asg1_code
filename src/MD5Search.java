
import java.math.BigInteger; //convert byte arrays into a positive integer
import java.nio.charset.StandardCharsets; //provides UTF-8 character encoding when converting passwords into bytes
import java.security.MessageDigest; // for generating MD5 hashes.
import java.security.NoSuchAlgorithmException;  //Handles errors when the requested hashing algorithm is unavailable
import java.util.ArrayList; // Provides array list to store and manage worker threads
import java.util.List; //List interface for managing collections of worker thread
import java.util.concurrent.atomic.AtomicReference; //Provides thread-safe access to a shared search result between multiple threads

public class MD5Search {
    // Declare Total possible ASCII characters: decimal 32 to 126
    private static final int CHAR_START = 32;
    private static final int CHAR_END = 126;
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
        System.out.println("Target MD5: " + config.targetHash);
        System.out.println("Number of threads: " + config.threadCount);
        System.out.println("Password length: " + config.passwordLength);
        System.out.println("Total combinations: " + totalCombinations);

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

    // ==================================================
    // Generate MD5 hash of a password candidate function
    // ==================================================
    public static String getMd5(String input) {
        try {
            MessageDigest md = MessageDigest.getInstance("MD5");

            byte[] messageDigest =
                md.digest(input.getBytes(StandardCharsets.UTF_8));

            BigInteger no = new BigInteger(1, messageDigest);

            String hashtext = no.toString(16);

            // Ensure the hash always contains 32 characters
            while (hashtext.length() < 32) {
                hashtext = "0" + hashtext;
            }

            return hashtext;

        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException("MD5 algorithm not available.", e);
        }
    }

    // =========================================================
    // Calculate total search-space combinations function
    // =========================================================
    private static long calculateTotalCombinations(int passwordLength) {

        long total = 1;

        for (int i = 0; i < passwordLength; i++) {
            total *= CHAR_COUNT;
        }

        return total;
    }

    // =========================================================
    // Dynamically create worker threads function
    // =========================================================
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


    // =========================================================
    // 2ND PERSON Task: IMPLEMENT THIS PART
    // Divide the complete search space among the requested threads.
    // Each thread must receive a non-overlapping range.
    // Use start-inclusive and end-exclusive indexes.
    // The ranges must cover every combination.
    // =========================================================

    private static List<SearchRange> partitionSearchSpace(
            long totalCombinations,
            int threadCount) {


        // 2ND PERSON Task: IMPLEMENT THIS PART
        // Suggested partitioning formula:
        //
        // start = totalCombinations * threadIndex / threadCount;
        //
        // end = totalCombinations * (threadIndex + 1) / threadCount;
        //
        // Return a List<SearchRange> containing one range per thread.

        throw new UnsupportedOperationException(
            "Friend needs to implement partitionSearchSpace()."
        );
    }


    // =========================================================
    // 2ND PERSON Task: IMPLEMENT THIS PART
    // Convert a numeric index into an ASCII password candidate.
    // Use characters from ASCII 32 to 126.
    // Keep the candidate-generation order consistent.
    // =========================================================

    private static String passwordFromIndex(
            long index,
            int passwordLength) {

        // 2ND PERSON Task: IMPLEMENT THIS PART

        // Convert the index into a password using base-95 conversion.
        // ASCII 32 is the first character.
        // ASCII 126 is the last character.

        throw new UnsupportedOperationException(
            "Friend needs to implement passwordFromIndex()."
        );
    }


    // =========================================================
    // 2ND PERSON Task: IMPLEMENT THIS PART
    // Worker thread performs the actual brute-force search.
    // =========================================================

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

            // 2ND PERSON Task: IMPLEMENT THIS PART

            // Suggested searching process:
            //
            // 1. Loop through this worker's assigned index range.
            //
            // 2. Before checking each candidate, check whether:
            //    sharedResult.get() != null
            //
            //    If another thread has found the password, stop.
            //
            // 3. Convert the current index into a password:
            //    String candidate =
            //        passwordFromIndex(index, passwordLength);
            //
            // 4. Generate the candidate's MD5 hash:
            //    String candidateHash = getMd5(candidate);
            //
            // 5. Compare candidateHash with targetHash.
            //
            // 6. If they match, create a SearchResult containing:
            //    - The recovered password
            //    - Thread.currentThread().getId()
            //
            // 7. Publish the result using:
            //    sharedResult.compareAndSet(null, foundResult);
            //
            // 8. Stop this worker after finding the password.

        }
    }



    /*
     * SUPPORTING CLASS: Search range assigned to each thread
     */
    private static class SearchRange {

        private final long startInclusive;
        private final long endExclusive;

        public SearchRange(long startInclusive, long endExclusive) {
            this.startInclusive = startInclusive;
            this.endExclusive = endExclusive;
        }
    }

    /*
     * SUPPORTING CLASS: Store the recovered password and thread ID
     */

    private static class SearchResult {

        private final String password;
        private final long threadId;

        public SearchResult(String password, long threadId) {
            this.password = password;
            this.threadId = threadId;
        }
    }

    /*
     * SUPPORTING CLASS: Store validated CLI inputs
     */
    private static class Config {

        private final String targetHash;
        private final int threadCount;
        private final int passwordLength;

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
