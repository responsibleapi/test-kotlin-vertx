# Kotlin Vert.x test helper

Validates [Vert.x](https://vertx.io/) responses against [OpenAPI](https://www.openapis.org/) schema

## Usage

[![](https://jitpack.io/v/responsibleapi/test-kotlin-vertx.svg)](https://jitpack.io/#responsibleapi/test-kotlin-vertx)

In `build.gradle`:

```groovy
repositories {
    mavenCentral()

    // ResponsibleAPI test-kotlin-vertx
    maven { url 'https://jitpack.io' }
}

dependencies {
    testImplementation(
        'com.github.responsibleapi:test-kotlin-vertx:X.X.X'
    )
}
```

Assuming you have:

- A [verticle](https://vertx.io/docs/apidocs/io/vertx/core/Verticle.html) called `App`
- Spec at `src/main/resources/openapi.json`

here's how responses are validated:

```kotlin
class AppTest {

    private companion object {
        val vertx = Vertx.vertx()!!

        val port = (40000..50000).random()

        @JvmStatic
        @BeforeAll
        fun setUp(): Unit = runBlocking {
            vertx.deployVerticle(
                App(port = port)
            ).coAwait()
        }

        val client: WebClient = WebClient.create(
            vertx,
            WebClientOptions()
                .setDefaultHost("localhost")
                .setDefaultPort(port)
        )
        val rb = runBlocking {
            RouterBuilder.create(vertx, "openapi.json").coAwait()
        }
        val responsible = Responsible(rb.openAPI.openAPI, client)

        @JvmStatic
        @AfterAll
        fun tearDown() {
            vertx.close()
        }
    }

    @Test
    fun `show not found`() = vertx.test {
        responsible.check(
            req = rb.operation("getShow2")
                .toRequest(pathParams = mapOf("show_id" to genID(length = 11))),
            status = 404,
        )
    }
}
```

Requests are validated [by Vert.x itself](https://vertx.io/docs/vertx-web-openapi/java/)

---

ResponsibleAPI is a compact language that compiles to OpenAPI: https://responsibleapi.com
