# Purpose: Automatically perform the performance experiments required
# by the assignment and generate CSV result files.

# ============================================================
# SECTION 1: CONFIGURATION
# ============================================================
# MC4 group's assigned MD5 hashes from Appendix B
$tests = @(
    @{
        Length = 4
        Hash = "6a3b3c12eb8c8ca1e51cc3e32855e9c0"
    },
    @{
        Length = 5
        Hash = "9031d22f97fae8eef8a331c11bae5ba4"
    }
)

# Thread configurations required by the assignment.
$threadCounts = @(1, 2, 4, 6, 8, 10)

# Each configuration must be executed three times.
$numberOfRuns = 3

# CSV output filenames.
$individualFile = Join-Path $PSScriptRoot "performance_runs.csv"
$summaryFile = Join-Path $PSScriptRoot "performance_summary.csv"


# ============================================================
# SECTION 2: PREPARE THE WORKING DIRECTORY
# ============================================================

# Get the main project directory.
# PSScriptRoot refers to the benchmark folder.
# Its parent directory is the main project folder.

$projectRoot = Split-Path $PSScriptRoot -Parent

# Move to the main project directory.
Set-Location $projectRoot

# Check whether Java compiler is available.
if (-not (Get-Command javac -ErrorAction SilentlyContinue)) {
    throw "javac was not found. Please install Java JDK and configure PATH."
}

# Check whether Java runtime is available.
if (-not (Get-Command java -ErrorAction SilentlyContinue)) {
    throw "java was not found. Please configure Java PATH."
}

# Check whether the Java source file exists.
$javaFile = Join-Path $projectRoot "src\MD5Search.java"

if (-not (Test-Path $javaFile)) {
    throw "MD5Search.java was not found inside the src folder."
}

Write-Host ""
Write-Host "========================================"
Write-Host " MD5 PERFORMANCE BENCHMARK"
Write-Host "========================================"

Write-Host "Project directory: $projectRoot"
Write-Host "Java source: src\MD5Search.java"
Write-Host "Benchmark directory: benchmark"


# ============================================================
# SECTION 3: COMPILE JAVA PROGRAM
# ============================================================

Write-Host ""
Write-Host "Compiling MD5Search.java..."

# Compile the Java source file.
# -d src places compiled .class files inside src.
javac -d src src/MD5Search.java

# Stop if compilation fails.
if ($LASTEXITCODE -ne 0) {
    throw "Compilation failed. Please check your Java code."
}

Write-Host "Compilation successful!" -ForegroundColor Green


# ============================================================
# SECTION 4: INITIALIZE RESULT COLLECTION
# ============================================================

# Stores the execution time of every individual run.
$allResults = @()

# Stores median and performance calculations.
$summaryResults = @()


# ============================================================
# SECTION 5: RUN PERFORMANCE EXPERIMENTS
# ============================================================

foreach ($test in $tests) {

    # Retrieve the current password length and assigned hash.
    $passwordLength = $test.Length
    $targetHash = $test.Hash

    # Validate the assigned MD5 hash.
    if ($targetHash -notmatch '^[a-fA-F0-9]{32}$') {
        throw "Invalid MD5 hash for password length $passwordLength."
    }

    Write-Host ""
    Write-Host "========================================"
    Write-Host "Testing password length: $passwordLength"
    Write-Host "========================================"

    foreach ($threadCount in $threadCounts) {

        # Store the three execution times for this configuration.
        $times = @()

        Write-Host ""
        Write-Host "Thread configuration: $threadCount thread(s)"

        # Repeat the same experiment three times.
        for ($run = 1; $run -le $numberOfRuns; $run++) {

            Write-Host ""
            Write-Host "Running experiment $run of $numberOfRuns..."

            # Run Java with:
            # 1. Target MD5 hash
            # 2. Number of threads
            # 3. Password length
            #
            # -cp src tells Java where to find the compiled class.

            $javaOutput = & java -cp src MD5Search `
                $targetHash $threadCount $passwordLength 2>&1 |
                Out-String

            # Check whether Java execution was successful.
            if ($LASTEXITCODE -ne 0) {
                Write-Host $javaOutput
                throw "Java execution failed. Check your Java program."
            }

            # Extract the recovered password.
            $passwordMatch = [regex]::Match(
                $javaOutput,
                '(?m)^Password found: ([^\r\n]*)'
            )

            if (-not $passwordMatch.Success) {
                Write-Host $javaOutput
                throw "Password was not recovered. Run was not recorded."
            }

            # Retrieve the recovered password.
            $recoveredPassword = $passwordMatch.Groups[1].Value

            # Check whether the recovered password has the expected length.
            if ($recoveredPassword.Length -ne $passwordLength) {
                Write-Host $javaOutput
                throw "Recovered password length does not match the expected length."
            }

            # Extract the thread ID that found the password.
            $threadMatch = [regex]::Match(
                $javaOutput,
                'Found by thread ID:\s*(\d+)'
            )

            if (-not $threadMatch.Success) {
                Write-Host $javaOutput
                throw "Could not extract the finding thread ID."
            }

            # Extract the execution time reported by Java.
            $timeMatch = [regex]::Match(
                $javaOutput,
                'Execution time:\s*([0-9]+(?:\.[0-9]+)?)\s*seconds'
            )

            if (-not $timeMatch.Success) {
                Write-Host $javaOutput
                throw "Could not extract execution time."
            }

            # Convert execution time into a numeric value.
            $executionTime = [double]::Parse(
                $timeMatch.Groups[1].Value,
                [System.Globalization.CultureInfo]::InvariantCulture
            )

            # Reject zero or negative execution times.
            if ($executionTime -le 0) {
                Write-Host $javaOutput
                throw "Execution time must be greater than zero. Check Java timing precision."
            }

            # Retrieve the finding thread ID.
            $foundByThread = $threadMatch.Groups[1].Value

            # Store execution time for median calculation.
            $times += $executionTime

            # Create an individual experiment result.
            $runResult = [PSCustomObject]@{
                PasswordLength = $passwordLength
                Threads = $threadCount
                Run = $run
                Password = $recoveredPassword
                FoundByThread = $foundByThread
                ExecutionTimeSeconds = $executionTime
            }

            # Add result to the collection.
            $allResults += $runResult

            # Save individual results immediately.
            # This protects completed runs if a later test fails.
            $allResults | Export-Csv `
                -Path $individualFile `
                -NoTypeInformation `
                -Encoding UTF8

            Write-Host "Password recovered: $recoveredPassword"
            Write-Host "Found by thread: $foundByThread"
            Write-Host "Execution time: $executionTime seconds"

            Write-Host "Individual result saved." -ForegroundColor Green
        }


        # ====================================================
        # SECTION 6: CALCULATE MEDIAN EXECUTION TIME
        # ====================================================

        # Sort execution times from smallest to largest.
        $sortedTimes = @($times | Sort-Object)

        # The middle value is the median.
        $median = $sortedTimes[1]

        Write-Host ""
        Write-Host "Median execution time: $median seconds" `
            -ForegroundColor Cyan

        # Store results for later performance calculations.
        $summaryResults += [PSCustomObject]@{
            PasswordLength = $passwordLength
            Threads = $threadCount
            Run1 = $times[0]
            Run2 = $times[1]
            Run3 = $times[2]
            Median = $median
        }

        # Save intermediate summary results.
        # Speedup and efficiency will be added after all
        # configurations have been completed.
        $summaryResults | Export-Csv `
            -Path $summaryFile `
            -NoTypeInformation `
            -Encoding UTF8
    }
}


# ============================================================
# SECTION 7: CALCULATE SPEEDUP AND PARALLEL EFFICIENCY
# ============================================================

foreach ($result in $summaryResults) {

    # Find the one-thread median for the same password length.
    $baseline = $summaryResults |
        Where-Object {
            $_.PasswordLength -eq $result.PasswordLength -and
            $_.Threads -eq 1
        } |
        Select-Object -First 1

    # Speedup = Single-thread median / Current median.
    $speedup = $baseline.Median / $result.Median

    # Parallel efficiency = Speedup / Number of threads * 100.
    $efficiency = ($speedup / $result.Threads) * 100

    # Add calculated values to the summary results.
    $result | Add-Member -NotePropertyName Speedup `
        -NotePropertyValue ([math]::Round($speedup, 4))

    $result | Add-Member -NotePropertyName EfficiencyPercent `
        -NotePropertyValue ([math]::Round($efficiency, 2))
}


# ============================================================
# SECTION 8: EXPORT FINAL RESULTS TO CSV
# ============================================================

# Export all individual experiment results.
$allResults | Export-Csv `
    -Path $individualFile `
    -NoTypeInformation `
    -Encoding UTF8

# Export median, speedup and efficiency results.
$summaryResults | Export-Csv `
    -Path $summaryFile `
    -NoTypeInformation `
    -Encoding UTF8


# ============================================================
# SECTION 9: DISPLAY FINAL SUMMARY
# ============================================================

Write-Host ""
Write-Host "========================================"
Write-Host " ALL EXPERIMENTS COMPLETED!"
Write-Host "========================================"

# Display the summary table in PowerShell.
$summaryResults |
    Format-Table PasswordLength, Threads, Median, Speedup, EfficiencyPercent -AutoSize

Write-Host ""
Write-Host "Individual results saved to:"
Write-Host $individualFile

Write-Host ""
Write-Host "Summary results saved to:"
Write-Host $summaryFile

Write-Host ""
Write-Host "Total experiments completed: $($allResults.Count)"
Write-Host "Expected experiments: 36"

Write-Host ""
Write-Host "Benchmark completed successfully!" -ForegroundColor Green
