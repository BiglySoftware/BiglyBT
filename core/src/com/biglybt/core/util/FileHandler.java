/*
 * Copyright (C) Bigly Software.  All Rights Reserved.
 *
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU General Public License
 * as published by the Free Software Foundation; either version 2
 * of the License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program; if not, write to the Free Software
 * Foundation, Inc., 59 Temple Place - Suite 330, Boston, MA  02111-1307, USA.
 */

package com.biglybt.core.util;

import com.biglybt.core.diskmanager.file.impl.FMFileAccess.FileAccessor;
import com.biglybt.core.diskmanager.file.impl.FMFileAccessController.FileAccessorRAF;

import java.io.*;
import java.net.URI;
import java.nio.file.Files;
import java.util.Locale;

@SuppressWarnings("MethodMayBeStatic")
public class FileHandler
{
	/**
	 * @implNote Each string entry of subDir may contain File.separator
	 */
	public File newFile(File parent, String... subDirs) {
		if (subDirs == null || subDirs.length == 0) {
			return parent;
		}

		File file = parent;
		for (String subDir : subDirs) {
			file = new File(file, subDir);
		}
		return file;
	}

	/**
	 * @implNote 
	 *  parent and subDirs may contain File.separator<br/>
	 *  parent might be empty string (default is File.separator)
	 */
	public File newFile(String parent, String... subDirs) {
		if (subDirs == null || subDirs.length == 0) {
			return new File(parent);
		}

		File file = new File(parent, subDirs[0]);
		for (int i = 1; i < subDirs.length; i++) {
			file = new File(file, subDirs[i]);
		}
		return file;
	}

	public File newFile(URI uri) {
		return new File(uri);
	}

	public FileOutputStream newFileOutputStream(File file, boolean append)
			throws FileNotFoundException {
		return new FileOutputStream(file, append);
	}

	public FileInputStream newFileInputStream(File from_file)
			throws FileNotFoundException {
		return new FileInputStream(from_file);
	}

	public FileAccessor newFileAccessor(File file, String access_mode)
			throws FileNotFoundException {
		return new FileAccessorRAF(file, access_mode);
	}

	/**
	 * @implNote must handle path containing {@link File#separator}
	 */
	public boolean containsPathSegment(File f, String path,
			boolean caseSensitive) {
		String absolutePath = f.getAbsolutePath();
		if (!caseSensitive) {
			absolutePath = absolutePath.toLowerCase(Locale.US);
			path = path.toLowerCase(Locale.US);
		}
		return absolutePath.contains(File.separator + path + File.separator);
	}

	/**
	 * @return path string relative to <code>parentDir</code>. <br>
	 *         <code>null</code> if file is not in parentDir.<br>
	 *         Empty String if file is parentDir.
	 *
	 * @implNote throw RunTimeException for fallback implementation
	 */
	public String getRelativePath(File parentDir, File file) {
		String parentPath = getCanonicalPathSafe(parentDir);

		if (!parentPath.endsWith(File.separator)) {

			parentPath += File.separator;
		}

		String file_path = getCanonicalPathSafe(file);

		if (file_path.startsWith(parentPath)) {
			return file_path.substring(parentPath.length());
		}

		return FileUtil.areFilePathsIdentical(parentDir, file) ? "" : null;
	}

	/**
	 * Preserves the case of the file.name when the file exists but differs in case
	 */
	public File getCanonicalFileSafe(File file) {
		try {
			if (file.exists()) {

				File parent = file.getParentFile();

				if (parent == null) {

					return (file);
				}

				return (newFile(file.getParentFile().getCanonicalFile(),
						file.getName()));

			} else {

				return (file.getCanonicalFile());
			}
		} catch (Throwable e) {

			return (file.getAbsoluteFile());
		}
	}

	/**
	 * Preserves the case of the file.name when the file exists but differs in case
	 */
	public String getCanonicalPathSafe(File file) {
		try {
			if (file.exists()) {

				File parent = file.getParentFile();

				if (parent == null) {

					return (file.getAbsolutePath());
				}

				return (newFile(file.getParentFile().getCanonicalFile(),
						file.getName()).getAbsolutePath());

			} else {

				return (file.getCanonicalPath());
			}
		} catch (Throwable e) {

			return (file.getAbsolutePath());
		}
	}

	/**
	 * Whether child in an ancestor of parent, or child IS parent 
	 */
	public boolean isAncestorOf(File _parent, File _child) {
		// could use 
		// return getRelativePath(_parent, _child) != null;
		// except instead of canonise(..), it uses getCanonicalPathSafe(..)
		File parent = FileUtil.canonise(_parent);
		File child = FileUtil.canonise(_child);
		if (parent.equals(child)) {
			return (FileUtil.areFilePathsIdentical(_parent, _child));
		}
		String parent_s = parent.getPath();
		String child_s = child.getPath();
		if (parent_s.charAt(parent_s.length() - 1) != File.separatorChar) {
			parent_s += File.separatorChar;
		}
		return child_s.startsWith(parent_s);
	}

	public Object
	getFileStore(
		File		file )
	{
		// file has to exist to have a filestore so walk up the tree if necessary

		File temp = file;

		while( temp != null ){

			try{

				return Files.getFileStore(temp.toPath());

			}catch( Throwable e ){
			}

			temp = temp.getParentFile();
		}

		return( null );
	}
}
