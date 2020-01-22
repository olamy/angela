package com.terracottatech.qa.angela.common.util;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.FileSystems;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.StandardCopyOption;
import java.nio.file.attribute.BasicFileAttributes;
import java.nio.file.attribute.PosixFilePermission;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.Set;

public class DirectoryUtils {
  private static final Logger logger = LoggerFactory.getLogger(DirectoryUtils.class);

  public static void createAndValidateDir(Path dirToCreate) {
    try {
      if (!Files.exists(dirToCreate)) {
        Files.createDirectories(dirToCreate);
      } else if (!Files.isDirectory(dirToCreate)) {
        throw new RuntimeException(dirToCreate.getFileName() + " is not a directory");
      }

      if (!Files.isWritable(dirToCreate)) {
        throw new RuntimeException(dirToCreate.getFileName() + " directory is not writable");
      }

      if (FileSystems.getDefault().supportedFileAttributeViews().contains("posix")) {
        Set<PosixFilePermission> perms = new HashSet<>(Files.getPosixFilePermissions(dirToCreate));
        perms.addAll(EnumSet.of(PosixFilePermission.OWNER_WRITE, PosixFilePermission.GROUP_WRITE, PosixFilePermission.OTHERS_WRITE));
        Files.setPosixFilePermissions(dirToCreate, perms);
      }
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }
  }

  public static boolean deleteQuietly(Path path) {
    try{
      deleteDirectory(path);
      return true;
    } catch (Exception e) {
      logger.debug("Deletion of directory: " + path + " failed", e);
      return false;
    }
  }

  public static void deleteDirectory(Path path) {
    logger.info("Deleting directory: " + path);
    try {
      Files.walkFileTree(path, new SimpleFileVisitor<Path>() {
        @Override
        public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
          Files.delete(file);
          return FileVisitResult.CONTINUE;
        }

        @Override
        public FileVisitResult postVisitDirectory(Path dir, IOException exc) throws IOException {
          Files.delete(dir);
          return FileVisitResult.CONTINUE;
        }
      });
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }
  }

  public static void copyDirectory(Path src, Path dest) {
    try {
      Files.walkFileTree(src, new SimpleFileVisitor<Path>() {
        @Override
        public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) throws IOException {
          Files.createDirectories(dest.resolve(src.relativize(dir)));
          return FileVisitResult.CONTINUE;
        }

        @Override
        public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
          Files.copy(file, dest.resolve(src.relativize(file)), StandardCopyOption.REPLACE_EXISTING);
          return FileVisitResult.CONTINUE;
        }
      });
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }
  }
}
