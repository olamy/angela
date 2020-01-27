/*
 * The contents of this file are subject to the Terracotta Public License Version
 * 2.0 (the "License"); You may not use this file except in compliance with the
 * License. You may obtain a copy of the License at
 *
 * http://terracotta.org/legal/terracotta-public-license.
 *
 * Software distributed under the License is distributed on an "AS IS" basis,
 * WITHOUT WARRANTY OF ANY KIND, either express or implied. See the License for
 * the specific language governing rights and limitations under the License.
 *
 * The Covered Software is Angela.
 *
 * The Initial Developer of the Covered Software is
 * Terracotta, Inc., a Software AG company
 */

package org.terracotta.angela.common.util;

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
