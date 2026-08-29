package com.contextclip;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.nio.file.Paths;

@SpringBootApplication
public class ContextClipApplication {

    public static void main(String[] args) {
        loadDotenvIfPresent();
        SpringApplication.run(ContextClipApplication.class, args);
    }

    private static void loadDotenvIfPresent() {
        File envFile = findDotenvFile();
        if (envFile == null || !envFile.exists()) {
            return;
        }

        try (BufferedReader reader = new BufferedReader(new FileReader(envFile, StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                line = line.trim();
                if (line.isEmpty() || line.startsWith("#")) {
                    continue;
                }
                int eqIdx = line.indexOf('=');
                if (eqIdx > 0) {
                    String key = line.substring(0, eqIdx).trim();
                    String val = line.substring(eqIdx + 1).trim();
                    if ((val.startsWith("\"") && val.endsWith("\"")) || (val.startsWith("'") && val.endsWith("'"))) {
                        if (val.length() >= 2) {
                            val = val.substring(1, val.length() - 1);
                        }
                    }

                    String envVal = System.getenv(key);
                    if (envVal == null || envVal.isBlank()) {
                        System.setProperty(key, val);
                        if ("POSTGRES_PASSWORD".equals(key)) {
                            System.setProperty("spring.datasource.password", val);
                        } else if ("POSTGRES_USER".equals(key)) {
                            System.setProperty("spring.datasource.username", val);
                        }
                    }
                }
            }
        } catch (IOException ignored) {
        }
    }

    private static File findDotenvFile() {
        Path[] searchPaths = new Path[]{
                Paths.get(".env"),
                Paths.get("..", ".env"),
                Paths.get(System.getProperty("user.dir", ""), ".env"),
                Paths.get(System.getProperty("user.dir", ""), "..", ".env")
        };

        for (Path path : searchPaths) {
            File f = path.toFile();
            if (f.exists() && f.isFile()) {
                return f;
            }
        }
        return null;
    }
}
