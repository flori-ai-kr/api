package kr.ai.flori

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication

@SpringBootApplication
class FloriServerApplication

fun main(args: Array<String>) {
    runApplication<FloriServerApplication>(*args)
}
