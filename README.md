# OCI Distribution Client

Kotlin client utilizing CoRoutines and Retrofit to interact with the Docker Registry API V2 via
[OCI Distribution Specification](https://github.com/opencontainers/distribution-spec/blob/main/spec.md#pull).

## General

The main interface to interact with the client is `DistributionClient`. It provides the basic functionality described
in [Functionality](#functionality).  
After initializing the standard `DistributionClient` via `DistributionClientFactorys` factory method, we can also pin it
to a specific OCI Image (repository and reference like Tag or Digest) and make it an `ImageClient` with the
`.toImageClient()` function. This client is then used to interact with a specific Image and provides some Image specific
functions like the compressed size.    
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
- delete blob
- exists manifest
- retrieve manifest
- delete manifest
- download image
- inspect image

### Missing

- upload blob
- upload manifest
- export image as tar archive
- upload image from tar

## Installation

The client can be pulled into gradle or maven by
using [jitpack](https://jitpack.io/#com.gitlab.cmdjulian/oci-distribution-client).

<details>
<summary>Gradle</summary>

```groovy
repositories {
    maven { url 'https://jitpack.io' }
}


dependencies {
    implementation 'com.gitlab.cmdjulian:oci-distribution-client:{VERSION}'
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
    implementation("com.gitlab.cmdjulian:oci-distribution-client:{VERSION}")
}
```

</details>

<details>
<summary>Maven</summary>

```xml
<repositories>
    <repository>
        <id>jitpack.io</id>
        <url>https://jitpack.io</url>
    </repository>
</repositories>


<dependency>
<groupId>com.gitlab.cmdjulian</groupId>
<artifactId>oci-distribution-client</artifactId>
<version>{VERSION}</version>
</dependency>
```

</details>