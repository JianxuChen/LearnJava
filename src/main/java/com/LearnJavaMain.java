package com;

import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

import java.util.function.Function;


/**
 * @author 003066
 */
@SpringBootApplication
public class LearnJavaMain implements CommandLineRunner {

    public static void main(String[] args) {
        SpringApplication.run(LearnJavaMain.class, args);
    }

    @Override
    public void run(String... args) throws Exception {
        Function<Long, Long> adderLambda = (value) -> value + 3;
        Long resultLambda = adderLambda.apply((long) 8);
        System.out.println("resultLambda = " + resultLambda);
    }
}
