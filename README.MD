# Transaction Statistics Server

For task details, check the `Backend Code Assignment.pdf` file

## Implementation Details

Time complexity: O(1)

Space complexity: 0(1)

Space estimations: 

```    // Space requirements for 1 record: 70 bytes in total (instance: 16bytes, Int field: 16 bytes, 2x Long fields: 48 bytes)
       // 70 bytes * 120_000 records (max cache size before cleanup) = 8MB
       internal data class StatsRecord(val t: Long, val amount: Long, val orderCount: Int) {
```
Tech stack:

1. Kotlin 1.3
2. Gradle 5.5
3. Micronaut Framework 1.2 [https://micronaut.io]
4. Junit 5
5. Docker
6. GraalVM Native Image [https://www.graalvm.org/docs/reference-manual/aot-compilation/]

### Build

Build local JAR file: `./gradlew shadowJar`

After local build's success, build and run with Docker: `docker build .` 

### Run locally

```$ java -jar .build/libs/ebay-tx-stats-0.1-all.jar
 
   08:35:41.166 [main] INFO  io.micronaut.runtime.Micronaut - Startup completed in 1949ms. Server Running: http://localhost:8080
```

## Development guides

Set up your IDE to support Micronaut:

1. Intellij IDEA: enable annotations and delegate all build tasks to Gradle
2. Other IDEs: https://docs.micronaut.io/latest/guide/index.html#ideSetup
3. Build, run and continuously rebuild with Gradle: `./gradlew run --continuous` 
and then after changing a kotlin file this restart message should appear:
```
08:58:47.667 [main] INFO  io.micronaut.runtime.Micronaut - Startup completed in 873ms. Server Running: http://localhost:8080
```

### Running Tests

1. Run tests from IDE and check the output:
`08:52:43.653 [Test worker] INFO  ebay.tx.stats.TxRepoTest - Population time:	296.4 ms	Calculation time:	25.41 ms
`
2. Run tests with Gradle `./gradlew clean test`
