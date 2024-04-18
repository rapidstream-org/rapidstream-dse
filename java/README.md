<!--
Copyright (c) 2024 RapidStream Design Automation, Inc. and contributors.
All rights reserved. The contributor(s) of this file has/have agreed to the
RapidStream Contributor License Agreement.
-->

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
