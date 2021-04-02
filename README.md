# GRPCSpringStartingPoint
Starting point for using GRPC with Spring. Based upon https://github.com/sajeerzeji/SpringBoot-GRPC

## Build

**1. Compile Shared Module:**

```sh
cd grpc-shared
mvn install
```

**2. Compile & Run Server:**

```sh
cd grpc-server
mvn package
./mvnw clean spring-boot:run
```

**2. Compile & Run Client:**

```sh
cd grpc-client
mvn package
./mvnw clean spring-boot:run
```