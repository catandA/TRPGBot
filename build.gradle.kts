plugins {
    java
    id("org.springframework.boot") version "3.3.3"
    id("io.spring.dependency-management") version "1.1.7"
}

group = "me.catand"
version = "0.0.1-SNAPSHOT"

java {
    sourceCompatibility = JavaVersion.VERSION_21
}

configurations {
    compileOnly {
        extendsFrom(configurations.annotationProcessor.get())
    }
}

repositories {
    maven("https://maven.aliyun.com/repository/public")
    maven("https://maven.aliyun.com/repository/spring/")
    maven("https://jitpack.io")
    mavenCentral()
}

dependencies {
    implementation("org.springframework.boot:spring-boot-starter")

    // Lombok
    compileOnly("org.projectlombok:lombok")
    annotationProcessor("org.projectlombok:lombok")

    // Shiro
    implementation("com.mikuac:shiro:2.3.2")

    // Database
    implementation("org.springframework.boot:spring-boot-starter-data-jpa")
    implementation("org.xerial:sqlite-jdbc:3.46.1.0")
    implementation("org.hibernate.orm:hibernate-community-dialects:6.4.10.Final")

    // 词云
    implementation("com.kennycason:kumo-core:1.28")
    //implementation("com.kennycason:kumo-tokenizers:1.28")

    // 结巴分词
    implementation("com.huaban:jieba-analysis:1.0.2")
}
