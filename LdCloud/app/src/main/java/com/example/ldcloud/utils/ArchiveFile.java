package com.example.ldcloud.utils;

public class ArchiveFile {
    private String name;
    private boolean isDirectory;
    private String size; // Using String to represent size, can be formatted (e.g., "1.2 MB")
    private String lastModifiedDate; // Using String for simplicity

    public ArchiveFile(String name, boolean isDirectory, String size, String lastModifiedDate) {
        this.name = name;
        this.isDirectory = isDirectory;
        this.size = size;
        this.lastModifiedDate = lastModifiedDate;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public boolean isDirectory() {
        return isDirectory;
    }

    public void setDirectory(boolean directory) {
        isDirectory = directory;
    }

    public String getSize() {
        return size;
    }

    public void setSize(String size) {
        this.size = size;
    }

    public String getLastModifiedDate() {
        return lastModifiedDate;
    }

    public void setLastModifiedDate(String lastModifiedDate) {
        this.lastModifiedDate = lastModifiedDate;
    }
}
