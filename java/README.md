# RapidStream DSE Engine Java Source Code

## How to Compile
1. Copy the source code in `<rapidstream-dse-base>/java` to `<RapidWright-base>/src/com/xilinx/rapidwright/examples`.
2. Register the corresponding pass in `<RapidWright-base>/src/com/xilinx/rapidwright/MainEntrypoint.java`
    ```=java
    static {
        ...
        addFunction("CrossingCRNodeCounter", CrossingCRNodeCounter::main);
        ...
    }
    ```
3. Recompile RapidWright with `./gradlew compileJava`