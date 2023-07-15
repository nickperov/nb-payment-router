# Non-blocking payment router

The application simulates the work of a payment router.

Payment router receives batch payments process request (in a form of array inside the Json) and a flag inside the header which states how to process it (sequentially or parallel in a particular way).
For each payment inside the batch request payment router calls payment processor API to execute the payment (it is a synchronous call), and then consolidates all results and returns response to the client.
 
Payment router utilises number of strategies (based on Router header provided):

* **SEQ** - sequentially (iterate over list of payments and)
* **PARALLEL_STREAM** - parallel processing using parallel streams and common ForkJoinPool
* **PARALLEL_FIX_100** - parallel processing using fix size thread pool executor
* **PARALLEL_NO_LIM** - parallel processing using cached thread pool executor
* **ASYNC** - parallel asynchronous execution using CompletionStage in JAX-RS
* **REACTIVE** - parallel reactive processing using RxJava
* **COROUTINES** - parallel using kotlin coroutines
* **VIRTUAL_THREADS** - parallel using java virtual threads

Application utilises technology of java virtual threads.

## Run-build requirements

Application written in Kotlin employs Quarkus, the Supersonic Subatomic Java Framework.

In order to utilize Java virtual threads it required to use **JDK 19** or later versions. For **JDK 19** and **JDK 20** java virtual threads feature is in preview status. 
To enable preview feature the following JVM arguments need to be passed to both `javac` and `java`:
```
--enable-preview --add-opens java.base/java.lang=ALL-UNNAME
```
## Running the application in dev mode

You can run your application in dev mode that enables live coding using:
```shell script
./mvnw compile quarkus:dev
```

> **_NOTE:_**  Quarkus now ships with a Dev UI, which is available in dev mode only at http://localhost:8080/q/dev/.

## Packaging and running the application

The application can be packaged using:
```shell script
./mvnw package
```
It produces the `quarkus-run.jar` file in the `target/quarkus-app/` directory.
Be aware that it’s not an _über-jar_ as the dependencies are copied into the `target/quarkus-app/lib/` directory.

The application is now runnable using `java -jar target/quarkus-app/quarkus-run.jar`.

If you want to build an _über-jar_, execute the following command:
```shell script
./mvnw package -Dquarkus.package.type=uber-jar
```
The application, packaged as an _über-jar_, is now runnable using `java --enable-preview --add-opens java.base/java.lang=ALL-UNNAMED -jar target/*-runner.jar`.

## Related Guides

- Kotlin ([guide](https://quarkus.io/guides/kotlin)): Write your services in Kotlin

## APIS

* To process payments batch: POST - /payment/execute

To get open API contract - run in dev mode and navigate to - http://localhost:8080/q/swagger-ui/

## Authors

Nikolay Perov

## License
This project is licensed under the MIT License - see the [LICENSE.md](LICENSE.md) file for details
