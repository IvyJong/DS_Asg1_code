# How to Run MD5Search
## A. Run the Java Program (this is for test 2- and  3-char hash)
1. Open the project folder in VS Code.
2. Open the terminal
3. Compile the Java program: 
-->type the command in teminal <--
=======================================
   javac -d src src/MD5Search.java
=======================================

4. Run the program:
=============================================================
   java -cp src MD5Search <hash> <threads> <passwordLength>
============================================================= 

   Example:
   java -cp src MD5Search a46da3640231e41e1782925a91e46103 4 2
   

## B. Run the Performance Benchmark (for 4- and 5-char if the ##A is works)
#directly run this run benchmark file without using #A run java command 
1. Open the VS Code terminal.
2. Navigate to the benchmark folder: by type this command in terminal
===================
   cd benchmark
=====================

3. If PowerShell blocks script execution, run this command:
===============================================================
   Set-ExecutionPolicy -Scope Process -ExecutionPolicy Bypass
================================================================ 

4. Run the benchmark script: by type in terminal
=============================
   .\run_benchmark.ps1
==============================

5. Wait for all 36 experiments to finish.
6. Open the generated CSV files inside the `benchmark` folder:
   - performance_runs.csv
   - performance_summary.csv