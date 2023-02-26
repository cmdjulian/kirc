[![](https://jitpack.io/v/cmdjulian/kirc.svg)](https://jitpack.io/#cmdjulian/kirc)

# kirc ((k)container image registry client)

Kotlin client utilizing CoRoutines and Fuel to interact with the Container Registry API V2.
It supports all the read operations from the spec and can handle oci as well as docker format.

## Overview

The main interface to interact with the client is `ContainerImageRegistryClient`. It provides the basic functionality 
described in [Functionality](#functionality).  
After initializing the standard `ContainerImageRegistryClient` via `ContainerImageRegistryClientFactory` factory method, 
we can also pin it to a specific container image (repository and reference like Tag or Digest) and make it an 
`ContainerImageClient` with the `.toImageClient()` function. This client is then used to interact with a specific 
Image and provides some Image specific functions like the compressed size.    
The default client uses Kotlins CoRoutines and is therefore async. The library also provides the possibility to use a
blocking client instead. The blocking client can be obtained by first creating an async client and then use the
`.toBlockingClient()` extension function.

The library uses Kotlins Result type to report back the status of the call. It wraps a specific instance of
`DistributionError` for all errors. These errors are than divided into

1. a more general `ClientErrorException` like if a manifest was tried to be retrieved but didn't exist
   (`ClientErrorException.NotFoundException`)
2. network related errors (`NetworkError`) like HostNotFound or SSL related errors
3. unexpected errors (`UnknownError`)

As authentication methods JWT auth and BasicAuth are supported. Currently, there are no plans to implement certificate
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

## Adding the Dependency

The library requires at least java 11.  
The client can be pulled into gradle or maven by using [jitpack](https://jitpack.io/#cmdjulian/docker-registry-client).

<details>
<summary>Gradle</summary>

```groovy
repositories {
    maven { url 'https://jitpack.io' }
}


dependencies {
    implementation 'com.github.cmdjulian:docker-registry-client:{VERSION}'
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
    implementation("com.github.cmdjulian:docker-registry-client:{VERSION}")
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
            <groupId>com.github.cmdjulian</groupId>
            <artifactId>docker-registry-client</artifactId>
            <version>{VERSION}</version>
        </dependency>
    </dependencies>
</project>
```

</details>