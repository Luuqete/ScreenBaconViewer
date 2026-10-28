package idsParser;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.util.List;

public class IdsParser {
    private static final String FILE_NAME = "ids.txt";
    private static String basePath = "."; 

    public static void setBasePath(String path) {
        basePath = path;
    }

    private static String getFilePath() {
        return basePath + File.separator + FILE_NAME;
    }

    public static synchronized void saveId(String id) {
        try {
            File file = new File(getFilePath());
            if (file.getParentFile() != null && !file.getParentFile().exists()) {
                file.getParentFile().mkdirs();
            }
            Files.write(
                    Paths.get(getFilePath()),
                    (id + "\n").getBytes(StandardCharsets.UTF_8),
                    StandardOpenOption.CREATE, 
                    StandardOpenOption.APPEND 
            );
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public static synchronized boolean isIdSaved(String id) {
        boolean toRet = false;
        try {
            File file = new File(getFilePath());
            if (!file.exists()) {
                return false;
            }
            List<String> lines = Files.readAllLines(Paths.get(getFilePath()), StandardCharsets.UTF_8);
            for (String idSaved : lines) {
                if (idSaved.contains(id)) {
                    toRet = true;
                    break;
                }
            }
        } catch (IOException e) {
            e.printStackTrace();
        }

        return toRet;
    }
}
