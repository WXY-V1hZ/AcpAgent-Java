dependencies {
    // springboot
    implementation("org.springframework.boot:spring-boot-starter-web")
    testImplementation("org.springframework.boot:spring-boot-starter-test")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")

    // spring-ai
    implementation(platform("org.springframework.ai:spring-ai-bom:1.1.2"))
    implementation("org.springframework.ai:spring-ai-starter-model-deepseek")
    implementation("org.springframework.ai:spring-ai-starter-model-openai")
//    implementation("org.springframework.ai:spring-ai-starter-mcp-client-webflux")
//    implementation("org.springframework.ai:spring-ai-markdown-document-reader")
//    implementation("org.springframework.ai:spring-ai-starter-vector-store-pgvector")

    // spring-ai-alibaba
    implementation(platform("com.alibaba.cloud.ai:spring-ai-alibaba-bom:1.1.2.0"))
    implementation(platform("com.alibaba.cloud.ai:spring-ai-alibaba-extensions-bom:1.1.2.0"))
    implementation("com.alibaba.cloud.ai:spring-ai-alibaba-agent-framework")
//    implementation("com.alibaba.cloud.ai:spring-ai-alibaba-starter-dashscope")

    // acp
    implementation("com.agentclientprotocol:acp-agent-support:0.13.0-SNAPSHOT")
    implementation("com.agentclientprotocol:acp-websocket-jetty:0.13.0-SNAPSHOT")

    // lombok
    compileOnly("org.projectlombok:lombok:1.18.42")
    annotationProcessor("org.projectlombok:lombok:1.18.42")
    testCompileOnly("org.projectlombok:lombok:1.18.42")
    testAnnotationProcessor("org.projectlombok:lombok:1.18.42")

    // openapi
    implementation("org.springdoc:springdoc-openapi-starter-webmvc-ui:2.8.14")
    implementation("com.github.xiaoymin:knife4j-openapi3-jakarta-spring-boot-starter:4.5.0") {
        exclude(mapOf("group" to "org.springdoc", "module" to "springdoc-openapi-starter-common"))
        exclude(mapOf("group" to "org.springdoc", "module" to "springdoc-openapi-starter-webmvc-api"))
        exclude(mapOf("group" to "org.springdoc", "module" to "springdoc-openapi-starter-webmvc-ui"))
        exclude(mapOf("group" to "org.springdoc", "module" to "springdoc-openapi-starter-webflux-api"))
    }
}

group = "com.v1hz"
version = "0.0.1-SNAPSHOT"
description = "AcpAgent"

plugins {
    java
    application
    id("org.springframework.boot") version "3.5.8"
    id("io.spring.dependency-management") version "1.1.7"
}

repositories {
    mavenLocal()
    mavenCentral()
}

application {
    mainClass = "com.v1hz.acpagnet.AcpAgentApplication"
}

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(21)
    }
}

tasks.test {
    useJUnitPlatform()
}
