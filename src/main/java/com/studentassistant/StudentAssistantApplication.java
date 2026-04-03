package com.studentassistant;

import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
public class StudentAssistantApplication implements CommandLineRunner {

    public static void main(String[] args) {
        SpringApplication.run(StudentAssistantApplication.class, args);
    }

    @Override
    public void run(String... args) {
        System.out.println("  Chat : llama-3.3-70b (Groq)");
        System.out.println("  Embed: nomic-embed-text (Ollama local)");
        System.out.println("  DB   : PostgreSQL + pgvector");
    }
}
