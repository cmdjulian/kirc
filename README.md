[![](https://jitpack.io/v/cmdjulian/kirc.svg)](https://jitpack.io/#cmdjulian/kirc)

# kirc - (k)container image registry client

![kirc](./logo.png)

Kotlin client utilizing CoRoutines and Fuel to interact with the Container Registry API V2.
It supports all the read operations from the spec and can not only handle docker format but also oci.
The library is compatible with GraalVM and does already include the required reflection configs.

## Overview

A client can be obtained by the factory pattern from `{MODULE}ContainerImageClientFactory`.
After initializing the client, we can also pin it to a specific container image (repository and reference like Tag or
Digest) and make it an `ContainerImageClient` with the `.toImageClient()` function. This client is then used to interact
with a specific Image and provides some Image specific functions like the compressed size.

The library throws a dedicated error type to report back on exceptions for the different calls. It wraps a specific
instance of `RegistryClientException` for all errors. These errors are than divided into

1. a more general `ClientErrorException` like if a manifest was tried to be retrieved but didn't exist
   (`ClientErrorException.NotFoundException`)
2. network related errors (`NetworkError`) like HostNotFound or SSL related errors
3. unexpected errors (`UnknownError`)

As authentication schema JWT auth and BasicAuth are supported. Currently, there are no plans to implement certificate
based authentication.

The Registry communication can be done using either `HTTP` or `HTTPS`. The library is also able to use a proxy for the
communication.

## Functionality

### Implemented

- ping
- list images
- list tags
- retrieve blob
- exists manifest
- retrieve manifest
- delete manifest
- download image
- inspect image

## Modules

The lib is published in three different flavors. All of them are based up on kotlins coroutines. All modules
transitively include the [suspending module](#suspending).

### Blocking

This module provides as the main entry point as `BlockingClientFactory`. All requests are blocking the current Thread.

<details>
<summary>Gradle</summary>

```groovy
repositories {
    maven { url 'https://jitpack.io' }
}


dependencies {
    implementation 'com.github.cmdjulian.kirc:blocking:{VERSION}'
}
```

</details>

<details>
<summary>Gradle Kts</summary>

```kotlin
repositories {
    maven(url = "https://jitpack.io")
}


dependencies {
    implementation("com.github.cmdjulian.kirc:blocking:{VERSION}")
}
```

</details>

<details>
<summary>Maven</summary>

```xml

<project xmlns="http://maven.apache.org/POM/4.0.0" xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 http://maven.apache.org/xsd/maven-4.0.0.xsd">

    ...

    <repositories>
        <repository>
            <id>jitpack.io</id>
            <url>https://jitpack.io</url>
        </repository>
    </repositories>

    ...

    <dependencies>
        <dependency>
            <groupId>com.github.cmdjulian.kirc</groupId>
            <artifactId>blocking</artifactId>
            <version>{VERSION}</version>
        </dependency>
    </dependencies>
</project>
```

</details>

### Reactive

This module provides as the main entry point as `ReactiveClientFactory`. It uses the kotlin extension functions to
return project reactor types.

<details>
<summary>Gradle</summary>

```groovy
repositories {
    maven { url 'https://jitpack.io' }
}


dependencies {
    implementation 'com.github.cmdjulian.kirc:reactive:{VERSION}'
}
```

</details>

<details>
<summary>Gradle Kts</summary>

```kotlin
repositories {
    maven(url = "https://jitpack.io")
}


dependencies {
    implementation("com.github.cmdjulian.kirc:reactive:{VERSION}")
}
```

</details>

<details>
<summary>Maven</summary>

```xml

<project xmlns="http://maven.apache.org/POM/4.0.0" xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 http://maven.apache.org/xsd/maven-4.0.0.xsd">

    ...

    <repositories>
        <repository>
            <id>jitpack.io</id>
            <url>https://jitpack.io</url>
        </repository>
    </repositories>

    ...

    <dependencies>
        <dependency>
            <groupId>com.github.cmdjulian.kirc</groupId>
            <artifactId>reactive</artifactId>
            <version>{VERSION}</version>
        </dependency>
    </dependencies>
</project>
```

</details>

### Suspending

This module provides as the main entry point as `SuspendingClientFactory`. It uses the kotlin coroutines to do the
requests.

<details>
<summary>Gradle</summary>

```groovy
repositories {
    maven { url 'https://jitpack.io' }
}


dependencies {
    implementation 'com.github.cmdjulian.kirc:suspending:{VERSION}'
}
```

</details>

<details>
<summary>Gradle Kts</summary>

```kotlin
repositories {
    maven(url = "https://jitpack.io")
}


dependencies {
    implementation("com.github.cmdjulian.kirc:suspending:{VERSION}")
}
```

</details>

<details>
<summary>Maven</summary>

```xml

<project xmlns="http://maven.apache.org/POM/4.0.0" xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 http://maven.apache.org/xsd/maven-4.0.0.xsd">

    ...

    <repositories>
        <repository>
            <id>jitpack.io</id>
            <url>https://jitpack.io</url>
        </repository>
    </repositories>

    ...

    <dependencies>
        <dependency>
            <groupId>com.github.cmdjulian.kirc</groupId>
            <artifactId>suspending</artifactId>
            <version>{VERSION}</version>
        </dependency>
    </dependencies>
</project>
```

</details>

### Image

This module is transitively included from all the above modules. It's main purpose is to provide the components to parse
container image names. It's mainly packaged in its own module to be included without any of the aforementioned modules. 

<details>
<summary>Gradle</summary>

```groovy
repositories {
    maven { url 'https://jitpack.io' }
}


dependencies {
    implementation 'com.github.cmdjulian.kirc:image:{VERSION}'
}
```

</details>

<details>
<summary>Gradle Kts</summary>

```kotlin
repositories {
    maven(url = "https://jitpack.io")
}


dependencies {
    implementation("com.github.cmdjulian.kirc:image:{VERSION}")
}
```

</details>

<details>
<summary>Maven</summary>

```xml

<project xmlns="http://maven.apache.org/POM/4.0.0" xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 http://maven.apache.org/xsd/maven-4.0.0.xsd">

    ...

    <repositories>
        <repository>
            <id>jitpack.io</id>
            <url>https://jitpack.io</url>
        </repository>
    </repositories>

    ...

    <dependencies>
        <dependency>
            <groupId>com.github.cmdjulian.kirc</groupId>
            <artifactId>image</artifactId>
            <version>{VERSION}</version>
        </dependency>
    </dependencies>
</project>
```

</details>