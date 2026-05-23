package com.hazel

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication

@SpringBootApplication
class HazelServerApplication

fun main(args: Array<String>) {
    runApplication<HazelServerApplication>(*args)
}
