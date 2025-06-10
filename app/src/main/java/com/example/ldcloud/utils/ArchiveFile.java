package com.example.ldcloud.utils;

import java.io.Serializable;
import java.util.Objects;

// Placeholder based on typical fields for such a class
public class ArchiveFile implements Serializable {
    public String name;
    public boolean isDirectory;
    public long size;
    public long lastModified; // Store as millis since epoch
    public String path; // Full path or relative path within the archive item
    public String iaS3Key; // Key for S3 object if applicable
    public String jsonPath; // Path to the JSON file on GitHub if applicable

    public ArchiveFile(String name, boolean isDirectory, long size, long lastModified, String path) {
        this.name = name;
        this.isDirectory = isDirectory;
        this.size = size;
        this.lastModified = lastModified;
        this.path = path;
    }

    // Overriding equals and hashCode for proper functioning in collections if needed
    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        ArchiveFile that = (ArchiveFile) o;
        return isDirectory == that.isDirectory &&
                size == that.size &&
                lastModified == that.lastModified &&
                Objects.equals(name, that.name) &&
                Objects.equals(path, that.path);
    }

    @Override
    public int hashCode() {
        return Objects.hash(name, isDirectory, size, lastModified, path);
    }

    @Override
    public String toString() {
        return "ArchiveFile{" +
                "name='" + name + '\'' +
                ", isDirectory=" + isDirectory +
                ", size=" + size +
                ", lastModified=" + lastModified +
                ", path='" + path + '\'' +
                '}';
    }
}
