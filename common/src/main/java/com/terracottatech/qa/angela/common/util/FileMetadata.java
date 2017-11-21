package com.terracottatech.qa.angela.common.util;

import java.io.File;

public class FileMetadata {
    private final String path;
    private final String name;
    private final long length;
    private final boolean directory;

    public FileMetadata(String path, File file) {
        this.path = path;
        this.name = file.getName();
        this.length = file.length();
        this.directory = file.isDirectory();
    }

    public String getName() {
        return name;
    }

    public long getLength() {
        return length;
    }

    public boolean isDirectory() {
        return directory;
    }

    public String getPath() {
        return path;
    }

    @Override
    public String toString() {
        return (path == null ? "" : path + File.separator) + name + (directory ? " (dir)" : " " + length + " bytes");
    }

    public String getPathName() {
        return (path == null ? "" : path + File.separator) + name;
    }
}
