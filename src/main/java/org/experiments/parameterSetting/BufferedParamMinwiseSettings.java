package org.experiments.parameterSetting;

import java.io.*;
import java.util.HashSet;
import java.util.Objects;
import java.util.Set;

public class BufferedParamMinwiseSettings {
    private final String csvFilePath;
    private final Set<Setting> settings;

    // Represents a single setting (must implement equals and hashCode properly)
    private static class Setting {
        long ram;
        int B, d, w, numStoredAttributes, b;

        public Setting(long ram, int B, int d, int w, int numStoredAttributes, int b) {
            this.ram = ram;
            this.B = B;
            this.d = d;
            this.w = w;
            this.numStoredAttributes = numStoredAttributes;
            this.b = b;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (!(o instanceof Setting)) return false;
            Setting setting = (Setting) o;
            return ram == setting.ram &&
                    B == setting.B &&
                    d == setting.d &&
                    w == setting.w &&
                    numStoredAttributes == setting.numStoredAttributes &&
                    b == setting.b;
        }

        @Override
        public int hashCode() {
            return Objects.hash(ram, B, d, w, numStoredAttributes, b);
        }

        @Override
        public String toString() {
            return ram + "," + B + "," + d + "," + w + "," + numStoredAttributes + "," + b;
        }

        public static Setting fromString(String line) {
            String[] parts = line.split(",");
            return new Setting(
                    Long.parseLong(parts[0]),
                    Integer.parseInt(parts[1]),
                    Integer.parseInt(parts[2]),
                    Integer.parseInt(parts[3]),
                    Integer.parseInt(parts[4]),
                    Integer.parseInt(parts[5])
            );
        }
    }

    public BufferedParamMinwiseSettings(String path) {
        this.csvFilePath = path + "/paramTable/existingSettings.csv";
        this.settings = new HashSet<>();

        createFileIfNotExists();
        readSettingsFromFile();
    }

    private void createFileIfNotExists() {
        try {
            File file = new File(csvFilePath);
            File parent = file.getParentFile();
            if (parent != null && !parent.exists()) {
                parent.mkdirs();
            }
            if (!file.exists()) {
                file.createNewFile();
                try (BufferedWriter bw = new BufferedWriter(new FileWriter(file))) {
                    bw.write("RAM,B,d,w,numStoredAttributes,b");
                    bw.newLine();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private void readSettingsFromFile() {
        try (BufferedReader br = new BufferedReader(new FileReader(csvFilePath))) {
            // skip header
            br.readLine();
            String line;
            while ((line = br.readLine()) != null) {
                if (!line.trim().isEmpty()) {
                    settings.add(Setting.fromString(line));
                }
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public void add(long ram, int B, int d, int w, int numStoredAttributes, int b) {
        Setting newSetting = new Setting(ram, B, d, w, numStoredAttributes, b);
        if (!settings.contains(newSetting)) {
            settings.add(newSetting);
            appendSettingToFile(newSetting);
            throw new RuntimeException("New setting added: " + newSetting);
        }
    }

    private void appendSettingToFile(Setting setting) {
        try (BufferedWriter bw = new BufferedWriter(new FileWriter(csvFilePath, true))) {
            bw.write(setting.toString());
            bw.newLine();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}
