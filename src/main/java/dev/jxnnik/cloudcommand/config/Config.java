package dev.jxnnik.cloudcommand.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.PrintWriter;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class Config {

    private final File file;
    private final Gson gson;
    private final ExecutorService pool;
    private JsonObject json;

    public Config() {
        this.file = new File("../../modules/cloud-command", "config.json");
        this.gson = new GsonBuilder().setPrettyPrinting().create();
        this.pool = Executors.newFixedThreadPool(2);
        this.initFile();
    }

    private void initFile() {
        if (!file.getParentFile().exists()) {
            file.getParentFile().mkdirs();
        }

        if (!file.exists()) {
            try (PrintWriter writer = new PrintWriter(file)) {
                json = new JsonObject();
                json.addProperty("prefix", "<dark_gray>[<gradient:dark_aqua:aqua>SimpleCloud</gradient><dark_gray>] <gray>");
                json.addProperty("module.kickscreen.newrank", "\n <gray>You has been kicked from the network<dark_gray>! \n \n <gray>Reason<dark_gray>: <yellow>New rank \n <gray>New Rank<dark_gray>: <red>%rank% \n");
                writer.print(gson.toJson(json));
            } catch (FileNotFoundException e) {
                e.printStackTrace();
            }
        } else {
            try {
                json = JsonParser.parseReader(new FileReader(file)).getAsJsonObject();
            } catch (FileNotFoundException e) {
                e.printStackTrace();
            }
        }
    }

    public void save() {
        pool.execute(() -> {
            try (PrintWriter writer = new PrintWriter(file)) {
                writer.print(gson.toJson(json));
                writer.flush();
            } catch (FileNotFoundException e) {
                e.printStackTrace();
            }
        });
    }

    public String getString(String key) {
        return json.get(key).getAsString();
    }


    public String getString(String key, String replaceKey, String replacement) {
        return json.get(key).getAsString().replace(replaceKey, replacement);
    }
}