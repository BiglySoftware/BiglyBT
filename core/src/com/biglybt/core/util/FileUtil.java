/*
 * Created on Oct 10, 2003
 * Modified Apr 14, 2004 by Alon Rohter
 * Copyright (C) Azureus Software, Inc, All Rights Reserved.
 *
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU General Public License
 * as published by the Free Software Foundation; either version 2
 * of the License, or (at your option) any later version.
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 * You should have received a copy of the GNU General Public License
 * along with this program; if not, write to the Free Software
 * Foundation, Inc., 59 Temple Place - Suite 330, Boston, MA  02111-1307, USA.
 *
 */

package com.biglybt.core.util;

import java.io.*;
import java.lang.reflect.Method;
import java.net.MalformedURLException;
import java.net.SocketTimeoutException;
import java.net.URI;
import java.net.URL;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.BasicFileAttributeView;
import java.nio.file.attribute.BasicFileAttributes;
import java.nio.file.attribute.FileTime;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.zip.GZIPInputStream;

import com.biglybt.core.Core;
import com.biglybt.core.CoreFactory;
import com.biglybt.core.CoreOperation;
import com.biglybt.core.CoreOperationTask;
import com.biglybt.core.config.COConfigurationListener;
import com.biglybt.core.config.COConfigurationManager;
import com.biglybt.core.diskmanager.file.impl.FMFileAccess.FileAccessor;
import com.biglybt.core.logging.LogEvent;
import com.biglybt.core.logging.LogIDs;
import com.biglybt.core.logging.Logger;
import com.biglybt.platform.PlatformManager;
import com.biglybt.platform.PlatformManagerCapabilities;
import com.biglybt.platform.PlatformManagerFactory;

/**
 * File utility class.
 */
public class FileUtil {
  private static final LogIDs LOGID = LogIDs.CORE;
  public static final String DIR_SEP = System.getProperty("file.separator");


  private static final int	RESERVED_FILE_HANDLE_COUNT	= 4;
  private static boolean    first_reservation		= true;
  private static boolean	is_my_lock_file			= false;
  private static final List		reserved_file_handles 	= new ArrayList();
  private static final AEMonitor	class_mon				= new AEMonitor( "FileUtil:class" );

  private static Method reflectOnUsableSpace;

  private static char[]	char_conversion_mapping = null;

  private static final FileHandler fileHandling;

  private static AEDiagnosticsLogger file_logger;
  
  static {

	  try
	  {
		  reflectOnUsableSpace = File.class.getMethod("getUsableSpace", (Class[])null);
	  } catch (Throwable e)
	  {
		  reflectOnUsableSpace = null;
	  }

		String fileHandlingCN = System.getProperty("az.FileHandling.impl",
			"");
	  // To test:
	  // fileHandlingCN = FileHandlerHack.class.getName();
		FileHandler fileHandlingImpl = null;
	  if (!fileHandlingCN.isEmpty()) {
			try {
				Class<FileHandler> cla = (Class<FileHandler>) Class.forName(fileHandlingCN);
				fileHandlingImpl = cla.newInstance();
			} catch (Throwable t) {
				t.printStackTrace();
			}
		}
		fileHandling = fileHandlingImpl == null ? new FileHandler() : fileHandlingImpl;
	}

  public static boolean
  areFilePathsIdentical(
	  File	f1,
	  File	f2 )
  {
	  if ( f1.equals( f2 )){
		 
		  		// on Windows we get a true if they differ in case :(
		  
		  String p1 = f1.getParent();
		  String p2 = f2.getParent();
		  
		  boolean same_parent = p1 == p2 || ( p1 != null && p1.equals( p2 ));

		  if ( 	same_parent &&
				!f1.getName().equals( f2.getName()) && 
				f1.getName().equalsIgnoreCase( f2.getName())){

			  return( false );

		  }else{

			  return( true );
		  }
	  }else{

		  return( false );
	  }
  }
  
  /**
   * Verifies the case of the file.name
   * @param file
   * @return
   */
  
  public static boolean
  reallyExists(
		File file )
  {
	if ( file.exists()){
		
		try{
			return( file.getCanonicalFile().getName().equals( file.getName()));
		
		}catch( Throwable e ){
			
			return( true );
		}
	}else{
		return( false );
	}
  }
  
  /**
   * Preserves the case of the file.name when the file exists but differs in case
   * @param file
   * @return
   */
  
  public static File
  getCanonicalFileSafe(
	 File		file )
  {
		return fileHandling.getCanonicalFileSafe(file);
  }
  
  /**
   * Preserves the case of the file.name when the file exists but differs in case
   * @param file
   * @return
   */
  
  public static String
  getCanonicalPathSafe(
	 File		file )
  {
		return fileHandling.getCanonicalPathSafe(file);
  }
  
  public static boolean isAncestorOf(File _parent, File _child) {
  	return fileHandling.isAncestorOf(_parent, _child);
  }

  public static File canonise(File file) {
	  try {return file.getCanonicalFile();}
	  catch (IOException ioe) {return file;}
  }

  public static String getCanonicalFileName(String filename) {
    // Sometimes Windows use filename in 8.3 form and cannot
    // match .torrent extension. To solve this, canonical path
    // is used to get back the long form

    String canonicalFileName = filename;
    try {
      canonicalFileName = newFile(filename).getCanonicalPath();
    }
    catch (IOException ignore) {}
    return canonicalFileName;
  }


  public static File getUserFile(String filename) {
    return newFile(SystemProperties.getUserPath(), filename);
  }

  /**
   * Get a file relative to this program's install directory.
   * <p>
   * On Windows, this is usually %Program Files%\[AppName]\[filename]
   * <br>
   * On *nix, this is usually the [Launch Dir]/[filename]
   * <br>
   * On Mac, this is usually "/Applications/.[AppName]/[filename]"
   */
	public static File getApplicationFile(String filename) {

		String path = SystemProperties.getApplicationPath();

		return newFile(path, filename);
	}



  /**
   * Deletes the given dir and all files/dirs underneath
   */
  public static boolean recursiveDelete(File f) {
    String defSaveDir = COConfigurationManager.getStringParameter("Default save path");
    String moveToDir = COConfigurationManager.getStringParameter("Completed Files Directory", "");

    try{
  	  moveToDir = newFile(moveToDir).getCanonicalPath();
    }catch( Throwable e ){
    }
    try{
    	defSaveDir = newFile(defSaveDir).getCanonicalPath();
    }catch( Throwable e ){
    }

    try {

      if (f.getCanonicalPath().equals(moveToDir)) {
        System.out.println("FileUtil::recursiveDelete:: not allowed to delete the MoveTo dir !");
        return( false );
      }
      if (f.getCanonicalPath().equals(defSaveDir)) {
        System.out.println("FileUtil::recursiveDelete:: not allowed to delete the default data dir !");
        return( false );
      }

      if (f.isDirectory()) {
        File[] files = f.listFiles();
        for (int i = 0; i < files.length; i++) {
          if ( !recursiveDelete(files[i])){

        	  return( false );
          }
        }
        if ( !f.delete()){

        	return( false );
        }
      }
      else {
        if ( !f.delete()){

        	return( false );
        }
      }
    } catch (Exception ignore) {/*ignore*/}

    return( true );
  }

  public static boolean recursiveDeleteNoCheck(File f) {
    try {
      if (f.isDirectory()) {
        File[] files = f.listFiles();
        for (int i = 0; i < files.length; i++) {
          if ( !recursiveDeleteNoCheck(files[i])){

        	  return( false );
          }
        }
        if ( !f.delete()){

        	return( false );
        }
      }
      else {
        if ( !f.delete()){

        	return( false );
        }
      }
    } catch (Exception ignore) {/*ignore*/}

    return( true );
  }

  public static long
  getFileOrDirectorySize(
  	File		file )
  {
  	if ( file.isFile()){

  		return( file.length());

  	}else{

  		long	res = 0;

  		File[] files = file.listFiles();

  		if ( files != null ){

  			for (int i=0;i<files.length;i++){

  				res += getFileOrDirectorySize( files[i] );
  			}
  		}

  		return( res );
  	}
  }

  protected static void
  recursiveEmptyDirDelete(
  	File	f,
	Set		ignore_set,
	boolean	log_warnings )
  {
     try {
      String defSaveDir 	= COConfigurationManager.getStringParameter("Default save path");
      String moveToDir 		= COConfigurationManager.getStringParameter("Completed Files Directory", "");

      if ( defSaveDir.trim().length() > 0 ){

      	defSaveDir = newFile(defSaveDir).getCanonicalPath();
      }

      if ( moveToDir.trim().length() > 0 ){

      	moveToDir = newFile(moveToDir).getCanonicalPath();
      }

      if ( f.isDirectory()){

        File[] files = f.listFiles();

        if ( files == null ){

        	if (log_warnings ){
        		Debug.out("Empty folder delete:  failed to list contents of directory " + f );
        	}

          	return;
        }

        boolean hasIgnoreSet = ignore_set.size() > 0;
        for (int i = 0; i < files.length; i++) {

        	File	x = files[i];

        	if ( x.isDirectory()){

        		recursiveEmptyDirDelete(files[i],ignore_set,log_warnings);

        	}else{

        		if ( hasIgnoreSet && ignore_set.contains( x.getName().toLowerCase())){

        			if ( !x.delete()){

        				if ( log_warnings ){
        					Debug.out("Empty folder delete: failed to delete file " + x );
        				}
        			}
        		}
        	}
        }

        if (f.getCanonicalPath().equals(moveToDir)) {

        	if ( log_warnings ){
        		Debug.out("Empty folder delete:  not allowed to delete the MoveTo dir !");
        	}

          return;
        }

        if (f.getCanonicalPath().equals(defSaveDir)) {

        	if ( log_warnings ){
        		Debug.out("Empty folder delete:  not allowed to delete the default data dir !");
        	}

          return;
        }

        File[] files_inside = f.listFiles();
        if (files_inside.length == 0) {

          if ( !f.delete()){

        	  if ( log_warnings ){
        		  Debug.out("Empty folder delete:  failed to delete directory " + f );
        	  }
          }
        }else{
        	if ( log_warnings ){
        		Debug.out("Empty folder delete:  " + files_inside.length + " file(s)/folder(s) still in \"" + f + "\" - first listed item is \"" + files_inside[0].getName() + "\". Not removing.");
        	}
        }
      }

    } catch (Exception e) { Debug.out(e.toString()); }
  }

  public static String
  convertOSSpecificChars(
  	String		file_name_in,
  	boolean		is_folder )
  {
	  char[] mapping;

	  synchronized( FileUtil.class ){

		  if ( char_conversion_mapping == null ){

			  COConfigurationManager.addAndFireListener(
					 new COConfigurationListener() {

						 @Override
						 public void configurationSaved()
						 {
							 synchronized( FileUtil.class ){

								 String map = COConfigurationManager.getStringParameter( "File.Character.Conversions" );

								 String[] bits = map.split( "," );

								 List<Character> chars = new ArrayList<>();

								 for ( String bit: bits ){
									 bit = bit.trim();
									 if ( bit.length()==3){
										 char from	= bit.charAt(0);
										 char to	= bit.charAt(2);

										 chars.add( from );
										 chars.add( to );
									 }
								 }

								 char[] new_map = new char[chars.size()];

								 for ( int i=0;i<new_map.length;i++){

									 new_map[i] = chars.get(i);
								 }

								 char_conversion_mapping = new_map;
							 }
						 }
					});
		  }

		  mapping = char_conversion_mapping;
	  }

  		// this rule originally from DiskManager

  	char[]	chars = file_name_in.toCharArray();

	if ( mapping.length == 2 ){

			// default case

  		char from 	= mapping[0];
  		char to		= mapping[1];

  		for (int i=0;i<chars.length;i++){

  			if ( chars[i] == from ){

		  		chars[i] = to;
		  	}
	  	}
  	}else if ( mapping.length > 0 ){

	  	for (int i=0;i<chars.length;i++){

	  		char c = chars[i];

	 		for (int j=0;j<mapping.length;j+=2){

	  			if ( c == mapping[j] ){

			  		chars[i] = mapping[j+1];
			  	}
		  	}
	  	}
  	}

  	if ( !Constants.isOSX ){

  		if ( Constants.isWindows ){

  				//  this rule originally from DiskManager

	 		// The definitive list of characters permitted for Windows is defined here:
	 		// http://support.microsoft.com/kb/q120138/
  			String not_allowed = "\\/:?*<>|";
  		 	for (int i=0;i<chars.length;i++){
  		 		if (not_allowed.indexOf(chars[i]) != -1) {
  		  			chars[i] = '_';
  		  		}
  		  	}

  		 	// windows doesn't like trailing dots and whitespaces in folders, replace them

  		 	if ( is_folder ){

  		 		for(int i = chars.length-1;i >= 0 && (chars[i] == '.' || chars[i] == ' ');chars[i] = '_',i--);
  		 	}
  		}

  			// '/' is valid in mac file names, replace with space
  			// so it seems are cr/lf

	 	for (int i=0;i<chars.length;i++){

			char	c = chars[i];

			if ( c == '/' || c == '\r' || c == '\n'  ){

				chars[i] = ' ';
			}
		}
  	}

  	String	file_name_out = new String(chars);

	try{

			// mac file names can end in space - fix this up by getting
			// the canonical form which removes this on Windows

			// however, for soem reason getCanonicalFile can generate high CPU usage on some user's systems
			// in  java.io.Win32FileSystem.canonicalize
			// so changing this to only be used on non-windows

		if ( Constants.isWindows ){

			while( file_name_out.endsWith( " " )){

				file_name_out = file_name_out.substring(0,file_name_out.length()-1);
			}

		}else{

			String str = newFile(file_name_out).getCanonicalFile().toString();

			int	p = str.lastIndexOf( File.separator );

			file_name_out = str.substring(p+1);
		}

	}catch( Throwable e ){
		// ho hum, carry on, it'll fail later
		//e.printStackTrace();
	}

	//System.out.println( "convertOSSpecificChars: " + file_name_in + " ->" + file_name_out );

	return( file_name_out );
  }

  public static void
  writeResilientConfigFile(
  	String		file_name,
	Map			data )
  {
	  File parent_dir = newFile(SystemProperties.getUserPath());

	  boolean use_backups = COConfigurationManager.getBooleanParameter("Use Config File Backups" );

	  writeResilientFile( parent_dir, file_name, data, use_backups );
  }

  public static void
  writeResilientFile(
	File		file,
	Map			data )
  {
	  writeResilientFile( file.getParentFile(), file.getName(), data, false );
  }

  public static boolean
  writeResilientFileWithResult(
    File		parent_dir,
  	String		file_name,
	Map			data )
  {
	  return( writeResilientFile( parent_dir, file_name, data ));
  }

  public static void
  writeResilientFile(
    File		parent_dir,
  	String		file_name,
	Map			data,
	boolean		use_backup )
  {
	  writeResilientFile( parent_dir, file_name, data, use_backup, true );
  }

  public static void
  writeResilientFile(
    File		parent_dir,
  	String		file_name,
	Map			data,
	boolean		use_backup,
	boolean		copy_to_backup )
  {
	  if ( use_backup ){

		  File	originator = newFile( parent_dir, file_name );

		  if ( originator.exists()){

			  backupFile( originator, copy_to_backup );
		  }
	  }

	  writeResilientFile( parent_dir, file_name, data );
  }

  	// synchronise it to prevent concurrent attempts to write the same file

  private static boolean
  writeResilientFile(
	File		parent_dir,
  	String		file_name,
	Map			data )
  {
	  try{
		  class_mon.enter();

		  byte[] encoded_data;
		  
		  try{
			  encoded_data  = BEncoder.encode(data);
			  
		  }catch( Throwable e ){

			  Debug.out( "Save of '" + file_name + "' fails", e );

			  return( false );
		  }
		  
		  File existing = newFile(  parent_dir, file_name );
		  
		  if ( existing.length() == encoded_data.length ) {
			  
			  //System.out.println( "same length for " + file_name );
			  
			  try{
				  BufferedInputStream	bin = new BufferedInputStream( newFileInputStream(existing), encoded_data.length );

				  try{
					  BDecoder	decoder = new BDecoder();

					  Map	old = decoder.decodeStream( bin );

					  if ( BEncoder.mapsAreIdentical( data, old )) {

						  //System.out.println( "same data for " + file_name );

						  return( true );
					  }
				  }finally{

					  bin.close();
				  }
			  }catch( Throwable e ) {
			  }
		  }

		  try{
			  getReservedFileHandles();
			  File temp = newFile(  parent_dir, file_name + ".saving");
			  BufferedOutputStream	baos = null;

			  try{
				  FileOutputStream tempOS = newFileOutputStream( temp, false );
				  baos = new BufferedOutputStream( tempOS, 8192 );
				  baos.write( encoded_data );
				  baos.flush();

				  tempOS.getFD().sync();

				  baos.close();
				  baos = null;

				  	//only use newly saved file if it got this far, i.e. it saved successfully

				  if ( temp.length() > 1L ){

					  File file = newFile( parent_dir, file_name );

					  if ( file.exists()){

						  if ( !file.delete()){

							  Debug.out( "Save of '" + file_name + "' fails - couldn't delete " + file.getAbsolutePath());
						  }
					  }

					  if (file.exists()) {
					  	Debug.out(file + " still exists after delete attempt");
					  }

					  if ( temp.renameTo( file )){

						  return( true );

					  }

					  // rename failed, sleep a little and try again
					  Thread.sleep(50);
					  if ( temp.renameTo( file )){
					  	//System.err.println("2nd attempt of rename succeeded for " + temp.getAbsolutePath() + " to " + file.getAbsolutePath());
					  	return true;
					  }

				  	Debug.out( "Save of '" + file_name + "' fails - couldn't rename " + temp.getAbsolutePath() + " to " + file.getAbsolutePath());
				  }

				  return( false );

			  }catch( Throwable e ){

				  Debug.out( "Save of '" + file_name + "' fails", e );

				  return( false );

			  }finally{

				  try{
					  if (baos != null){

						  baos.close();
					  }
				  }catch( Exception e){

					  Debug.out( "Save of '" + file_name + "' fails", e );

					  return( false );
				  }
			  }
		  }finally{

			  releaseReservedFileHandles();
		  }
	  }finally{

		  class_mon.exit();
	  }
  }

  	public static boolean
  	resilientConfigFileExists(
  		String		name )
  	{
 		File parent_dir = newFile(SystemProperties.getUserPath());

 		boolean use_backups = COConfigurationManager.getBooleanParameter("Use Config File Backups" );

 		return( newFile( parent_dir, name ).exists() ||
 				( use_backups && newFile( parent_dir, name + ".bak" ).exists()));
  	}

	/**
	 *
 	 * @return Map read from config file, or empty HashMap if error
	 */
	public static Map
	readResilientConfigFile(
		String		file_name )
	{
 		File parent_dir = newFile(SystemProperties.getUserPath());

 		boolean use_backups = COConfigurationManager.getBooleanParameter("Use Config File Backups" );

 		return( readResilientFile( parent_dir, file_name, use_backups ));
	}

	/**
	 *
 	 * @return Map read from config file, or empty HashMap if error
	 */
	public static Map
	readResilientConfigFile(
		String		file_name,
		boolean		use_backups )
	{
 		File parent_dir = newFile(SystemProperties.getUserPath());

 		if ( !use_backups ){

 				// override if a backup file exists. This is needed to cope with backups
 				// of the main config file itself as when bootstrapping we can't get the
 				// "use backups"

 			if ( newFile( parent_dir, file_name + ".bak").exists()){

 				use_backups = true;
 			}
 		}

 		return( readResilientFile( parent_dir, file_name, use_backups ));
	}

	/**
	 *
 	 * @return Map read from config file, or empty HashMap if error
	 */
	public static Map
	readResilientFile(
		File		file )
	{
		return( readResilientFile( file.getParentFile(),file.getName(),false, true));
	}

	/**
	 *
 	 * @return Map read from config file, or empty HashMap if error
	 */
 	public static Map
	readResilientFile(
		File		parent_dir,
		String		file_name,
		boolean		use_backup )
 	{
 		return readResilientFile(parent_dir, file_name, use_backup, true);
 	}

 	/**
 	 *
 	 * @param parent_dir
 	 * @param file_name
 	 * @param use_backup
 	 * @param intern_keys
 	 *
 	 * @return Map read from config file, or empty HashMap if error
 	 */
 	public static Map
	readResilientFile(
		File		parent_dir,
		String		file_name,
		boolean		use_backup,
		boolean		intern_keys )
 	{
		File	backup_file = newFile( parent_dir, file_name + ".bak" );

 		if ( use_backup ){

 			use_backup = backup_file.exists();
 		}

 			// if we've got a backup, don't attempt recovery here as the .bak file may be
 			// fully OK

 		Map	res = readResilientFileSupport( parent_dir, file_name, !use_backup, intern_keys );

 		if ( res == null && use_backup ){

 				// try backup without recovery

 		 	res = readResilientFileSupport( parent_dir, file_name + ".bak", false, intern_keys );

	 		if ( res != null ){

	 			Debug.out( "Backup file '" + backup_file + "' has been used for recovery purposes" );

					// rewrite the good data, don't use backups here as we want to
					// leave the original backup in place for the moment

				writeResilientFile( parent_dir, file_name, res, false );

	 		}else{

	 				// neither main nor backup file ok, retry main file with recovery

	 			res = readResilientFileSupport( parent_dir, file_name, true, true );
	 		}
 		}

 		if ( res == null ){

 			res = new HashMap();
 		}

 		return( res );
 	}

  		// synchronised against writes to make sure we get a consistent view

  	private static Map
	readResilientFileSupport(
		File		parent_dir,
		String		file_name,
		boolean		attempt_recovery,
		boolean		intern_keys )
	{
   		try{
  			class_mon.enter();

	  		try{
	  			getReservedFileHandles();

	  			Map	res = null;

	  			try{
	  				res = readResilientFile( file_name, parent_dir, file_name, 0, false, intern_keys );

	  			}catch( Throwable e ){

	  				// ignore, it'll be rethrown if we can't recover below
	  			}

	  			if ( res == null && attempt_recovery ){

	  				res = readResilientFile( file_name, parent_dir, file_name, 0, true, intern_keys );

	  				if ( res != null ){

	  					Debug.out( "File '" + file_name + "' has been partially recovered, information may have been lost!" );
	  				}
	  			}

	  			return( res );

	  		}catch( Throwable e ){

	  			Debug.printStackTrace( e );

	  			return( null );

	  		}finally{

	  			releaseReservedFileHandles();
	  		}
  		}finally{

  			class_mon.exit();
  		}
  	}

	private static Map
	readResilientFile(
		String		original_file_name,
		File		parent_dir,
		String		file_name,
		int			fail_count,
		boolean		recovery_mode,
		boolean		skip_key_intern)
	{
			// logging in here is only done during "non-recovery" mode to prevent subsequent recovery
			// attempts logging everything a second time.
			// recovery-mode allows the decoding process to "succeed" with a partially recovered file

  		boolean	using_backup	= file_name.endsWith(".saving");

  		File file = newFile(  parent_dir, file_name );

	   		//make sure the file exists and isn't zero-length

  		if ( (!file.exists()) || file.length() <= 1L ){

  			if ( using_backup ){

  				if ( !recovery_mode ){

	  				if ( fail_count == 1 ){

	  					Debug.out( "Load of '" + original_file_name + "' fails, no usable file or backup" );

	  				}else{
	  					// drop this log, it doesn't really help to inform about the failure to
	  					// find a .saving file

	  					//if (Logger.isEnabled())
						//		Logger.log(new LogEvent(LOGID, LogEvent.LT_ERROR, "Load of '"
						//				+ file_name + "' fails, file not found"));

	  				}
  				}

  				return( null );
  			}

  			if ( !recovery_mode ){

  				// kinda confusing log this as we get it under "normal" circumstances (loading a config
  				// file that doesn't exist legitimately, e.g. shares or bad-ips
//  				if (Logger.isEnabled())
//						Logger.log(new LogEvent(LOGID, LogEvent.LT_ERROR, "Load of '"
//								+ file_name + "' failed, " + "file not found or 0-sized."));
  			}

  			return( readResilientFile( original_file_name, parent_dir, file_name + ".saving", 0, recovery_mode, true ));
  		}

  		BufferedInputStream bin = null;

  		try{
  			int	retry_limit = 5;

  			while(true){

  				try{
  					bin = new BufferedInputStream( newFileInputStream(file), 16384 );

  					break;

  				}catch( IOException e ){

  	 				if ( --retry_limit == 0 ){

  						throw( e );
  					}

  	 				if (Logger.isEnabled())
							Logger.log(new LogEvent(LOGID, "Failed to open '" + file.toString()	+ "', retrying", e));

  					Thread.sleep(500);
  				}
  			}

  			BDecoder	decoder = new BDecoder();

  			if ( recovery_mode ){

  				decoder.setRecoveryMode( true );
  			}

	    	Map	res = decoder.decodeStream(bin, !skip_key_intern);

	    	if ( using_backup && !recovery_mode ){

	    		Debug.out( "Load of '" + original_file_name + "' had to revert to backup file" );
	    	}

	    	return( res );

	    }catch( Throwable e ){

	    	Debug.printStackTrace( e );

	    	try {
	    		if (bin != null){

	    			bin.close();

	    			bin	= null;
	    		}
	    	} catch (Exception x) {

	    		Debug.printStackTrace( x );
	    	}

	    		// if we're not recovering then backup the file

	    	if ( !recovery_mode ){

	   			// Occurs when file is there but b0rked
      			// copy it in case it actually contains useful data, so it won't be overwritten next save

		    	File bad;

		    	int	bad_id = 0;

		    	while(true){

		    		File	test = newFile( parent_dir, file.getName() + ".bad" + (bad_id==0?"":(""+bad_id)));

		    		if ( !test.exists()){

		    			bad	= test;

		    			break;
		    		}

		    		bad_id++;
		    	}

		    	if (Logger.isEnabled())
						Logger.log(new LogEvent(LOGID, LogEvent.LT_WARNING, "Read of '"
								+ original_file_name + "' failed, decoding error. " + "Renaming to "
								+ bad.getName()));

		    		// copy it so its left in place for possible recovery

		    	copyFile( file, bad );
	    	}

	    	if ( using_backup ){

	    		if ( !recovery_mode ){

	    			Debug.out( "Load of '" + original_file_name + "' fails, no usable file or backup" );
	    		}

	    		return( null );
	    	}

	    	return( readResilientFile( original_file_name, parent_dir, file_name + ".saving", 1, recovery_mode, true ));

	    }finally{

	    	try {

	    		if (bin != null){

	    			bin.close();
	    		}
	    	}catch (Exception e) {

	    		Debug.printStackTrace( e );
	    	}
	    }
	}

	public static void
	deleteResilientFile(
		File		file )
	{
		file.delete();
		newFile( file.getParentFile(), file.getName() + ".bak" ).delete();
	}

	public static void
	deleteResilientConfigFile(
		String		name )
	{
		File parent_dir = newFile(SystemProperties.getUserPath());

		newFile( parent_dir, name ).delete();
		newFile( parent_dir, name + ".bak" ).delete();
	}

	private static void
	getReservedFileHandles()
	{
		try{
			class_mon.enter();

			while( reserved_file_handles.size() > 0 ){

				// System.out.println( "releasing reserved file handle");

				InputStream	is = (InputStream)reserved_file_handles.remove(0);

				try{
					is.close();

				}catch( Throwable e ){

					Debug.printStackTrace( e );
				}
			}
		}finally{

			class_mon.exit();
		}
	}

	private static void
	releaseReservedFileHandles()
	{
		try{

			class_mon.enter();

			File	lock_file	= newFile(SystemProperties.getUserPath() + ".lock");

			if ( first_reservation ){

				first_reservation = false;

				lock_file.delete();

				is_my_lock_file = lock_file.createNewFile();

			}else{

				lock_file.createNewFile();
			}

			while(  reserved_file_handles.size() < RESERVED_FILE_HANDLE_COUNT ){

				// System.out.println( "getting reserved file handle");

				InputStream	is = newFileInputStream( lock_file );

				reserved_file_handles.add(is);
			}
		}catch( Throwable e ){

			Debug.printStackTrace( e );

		}finally{

			class_mon.exit();
		}
	}

	public static boolean
	isMyFileLock()
	{
		return( is_my_lock_file );
	}

    /**
     * Backup the given file to filename.bak, removing the old .bak file if necessary.
     * If _make_copy is true, the original file will copied to backup, rather than moved.
     * @param _filename name of file to backup
     * @param _make_copy copy instead of move
     */
    public static void backupFile( final String _filename, final boolean _make_copy ) {
      backupFile( newFile( _filename ), _make_copy );
    }

    /**
     * Backup the given file to filename.bak, removing the old .bak file if necessary.
     * If _make_copy is true, the original file will copied to backup, rather than moved.
     * @param _file file to backup
     * @param _make_copy copy instead of move
     */
    public static void backupFile( final File _file, final boolean _make_copy ) {
      if ( _file.length() > 0L ) {
        File bakfile = newFile( _file.getAbsolutePath() + ".bak" );
        if ( bakfile.exists() ) bakfile.delete();
        if ( _make_copy ) {
          copyFile( _file, bakfile );
        }
        else {
          _file.renameTo( bakfile );
        }
      }
    }


	/**
     * Copy the given source file to the given destination file.
     * Returns file copy success or not.
     * @param _source source file
     * @param _dest destination file
     * @return true if file copy successful, false if copy failed
     */
    /*
    // FileChannel.transferTo() seems to fail under certain linux configurations.
    public static boolean copyFile( final File _source, final File _dest ) {
      FileChannel source = null;
      FileChannel dest = null;
      try {
        if( _source.length() < 1L ) {
          throw new IOException( _source.getAbsolutePath() + " does not exist or is 0-sized" );
        }
        source = newInputStream( _source ).getChannel();
        dest = newOutputStream( _dest ).getChannel();

        source.transferTo(0, source.size(), dest);
        return true;
      }
      catch (Exception e) {
        Debug.out( e );
        return false;
      }
      finally {
        try {
          if (source != null) source.close();
          if (dest != null) dest.close();
        }
        catch (Exception ignore) {}
      }
    }
    */

    public static boolean copyFile( final File _source, final File _dest ) {
      try {
    	File parent = _dest.getParentFile();
    	if ( !parent.exists()){
    		parent.mkdirs();
    	}
        copyFile( newFileInputStream( _source ), newFileOutputStream( _dest ) );
        return true;
      }
      catch( Throwable e ) {
      	Debug.out( "Copy failed for " + _source, e );
        return false;
      }
    }

    public static void copyFileWithException( final File _source, final File _dest ) throws IOException{
         copyFile( newFileInputStream( _source ), newFileOutputStream( _dest ) );
    }

    public static boolean copyFile( final File _source, final OutputStream _dest, boolean closeInputStream ) {
        try {
          copyFile( newFileInputStream( _source ), _dest, closeInputStream );
          return true;
        }
        catch( Throwable e ) {
        	Debug.printStackTrace( e );
          return false;
        }
      }

    	/**
    	 * copys the input stream to the file. always closes the input stream
    	 * @param _source
    	 * @param _dest
    	 * @throws IOException
    	 */

    public static void
    copyFile(
    	final InputStream 	_source,
    	final File 			_dest )

    	throws IOException
    {
    	File parent = _dest.getParentFile();
    	
    	if ( !parent.exists()){
    		
    		parent.mkdirs();
    	}
    	
    	FileOutputStream	dest = null;

    	boolean	close_input = true;

    	try{
    		dest = newFileOutputStream(_dest);

    			// copyFile will close from now on, we don't need to

    		close_input = false;

    		copyFile( _source, dest, true );

    	}finally{

       		try{
    			if(close_input){
    				_source.close();
    			}
    		}catch( IOException e ){
     		}

    		if ( dest != null ){

    			dest.close();
    		}
    	}
    }

    public static void
    copyFile(
    	final InputStream 	_source,
    	final File 			_dest,
    	boolean				_close_input_stream )

    	throws IOException
    {
    	File parent = _dest.getParentFile();
    	
    	if ( !parent.exists()){
    		
    		parent.mkdirs();
    	}
    	
    	FileOutputStream	dest = null;

    	boolean	close_input = _close_input_stream;

    	try{
    		dest = newFileOutputStream(_dest);

    		close_input = false;

    		copyFile( _source, dest, close_input );

    	}finally{

       		try{
    			if( close_input ){

    				_source.close();
    			}
    		}catch( IOException e ){
     		}

    		if ( dest != null ){

    			dest.close();
    		}
    	}
    }

    public static void
    copyFile(
      InputStream   is,
      OutputStream  os )

    	throws IOException
    {
      copyFile(is,os,true);
    }

    public static void
	copyFile(
		InputStream		is,
		OutputStream	os,
		boolean 		closeInputStream )

		throws IOException
	{
    	try{

    		if ( !(is instanceof BufferedInputStream )){

    			is = new BufferedInputStream(is,128*1024);
    		}

    		byte[]	buffer = new byte[128*1024];

    		while(true){

    			int	len = is.read(buffer);

    			if ( len == -1 ){

    				break;
    			}

    			os.write( buffer, 0, len );
    		}
    	}finally{
    		try{
    			if(closeInputStream){
    			  is.close();
    			}
    		}catch( IOException e ){

    		}

    		os.close();
    	}
	}

    public static void
    copyFileOrDirectory(
    	File	from_file_or_dir,
    	File	to_parent_dir )

    	throws IOException
    {
    	if ( !from_file_or_dir.exists()){

    		throw( new IOException( "File '" + from_file_or_dir.toString() + "' doesn't exist" ));
    	}

    	if ( !to_parent_dir.exists()){

    		throw( new IOException( "File '" + to_parent_dir.toString() + "' doesn't exist" ));
    	}

    	if ( !to_parent_dir.isDirectory()){

    		throw( new IOException( "File '" + to_parent_dir.toString() + "' is not a directory" ));
    	}

    	if ( from_file_or_dir.isDirectory()){

    		File[]	files = from_file_or_dir.listFiles();

    		File	new_parent = newFile( to_parent_dir, from_file_or_dir.getName());

    		FileUtil.mkdirs(new_parent);

    		for (int i=0;i<files.length;i++){

    			File	from_file	= files[i];

    			copyFileOrDirectory( from_file, new_parent );
    		}
    	}else{

    		File target = newFile( to_parent_dir, from_file_or_dir.getName());

    		if ( !copyFile(  from_file_or_dir, target )){

    			throw( new IOException( "File copy from " + from_file_or_dir + " to " + target + " failed" ));
    		}
    	}
    }

    /**
     * Returns the file handle for the given filename or it's
     * equivalent .bak backup file if the original doesn't exist
     * or is 0-sized.  If neither the original nor the backup are
     * available, a null handle is returned.
     * @param _filename root name of file
     * @return file if successful, null if failed
     */
    public static File getFileOrBackup( final String _filename ) {
      try {
        File file = newFile( _filename );
        //make sure the file exists and isn't zero-length
        if ( file.length() <= 1L ) {
          //if so, try using the backup file
          File bakfile = newFile( _filename + ".bak" );
          if ( bakfile.length() <= 1L ) {
            return null;
          }
          else return bakfile;
        }
        else return file;
      }
      catch (Exception e) {
        Debug.out( e );
        return null;
      }
    }

    public static File
    getJarFileFromClass(
    	Class		cla )
    {
    	try{
	    	String str = cla.getName();

	    	str = str.replace( '.', '/' ) + ".class";

	        URL url = cla.getClassLoader().getResource( str );

	        if ( url != null ){

	        	String	url_str = url.toExternalForm();

	        	if ( url_str.startsWith("jar:file:")){

	        		File jar_file = FileUtil.getJarFileFromURL(url_str);

	        		if ( jar_file != null && jar_file.exists()){

	        			return( jar_file );
	        		}
	        	}
	        }
    	}catch( Throwable e ){

    		Debug.printStackTrace(e);
    	}

        return( null );
    }

    public static File
	getJarFileFromURL(
		String		url_str )
    {
    	if (url_str.startsWith("jar:file:")) {

        	// java web start returns a url like "jar:file:c:/sdsd" which then fails as the file
        	// part doesn't start with a "/". Add it in!
    		// here's an example
    		// jar:file:C:/Documents%20and%20Settings/stuff/.javaws/cache/http/Dparg.homeip.net/P9090/DMazureus-jnlp/DMlib/XMAzureus2.jar1070487037531!/com/biglybt/internat/MessagesBundle.properties

        	// also on Mac we don't get the spaces escaped

    		url_str = url_str.replaceAll(" ", "%20" );

        	if ( !url_str.startsWith("jar:file:/")){


        		url_str = "jar:file:/".concat(url_str.substring(9));
        	}

        	try{
        			// 	you can see that the '!' must be present and that we can safely use the last occurrence of it

        		int posPling = url_str.lastIndexOf('!');

        		String jarName = url_str.substring(4, posPling);

        			//        System.out.println("jarName: " + jarName);

        		URI uri;

        		try{
        			uri = URI.create(jarName);

        			if ( !newFile(uri).exists()){

        				throw( new FileNotFoundException());
        			}
        		}catch( Throwable e ){

        			jarName = "file:/" + UrlUtils.encode( jarName.substring( 6 ));

        			uri = URI.create(jarName);
        		}

        		File jar = newFile(uri);

        		return( jar );

        	}catch( Throwable e ){

        		Debug.printStackTrace( e );
        	}
    	}

    	return( null );
    }

    public static boolean
	renameFile(
		File		from_file,
		File		to_file )
    {
        return renameFile(from_file, to_file, true);
   	}

    public static boolean
 	renameFile(
 		File				from_file,
 		File				to_file,
 		ProgressListener	pl )
    {
         return renameFile(from_file, to_file, true, null, pl );
    }
    
    public static boolean
    renameFile(
        File        from_file,
        File        to_file,
        boolean     fail_on_existing_directory)
    {
    	return renameFile(from_file, to_file, fail_on_existing_directory, null, null);
    }
    
    public static boolean
    renameFile(
   		File        		from_file,
   		File        		to_file,
   		boolean     		fail_on_existing_directory,
   		FileFilter  		file_filter,
   		ProgressListener	pl )
    {
		FileTime from_last_modified = null;
		FileTime from_last_access	= null;
		FileTime from_created		= null;
		
   		try{
			BasicFileAttributeView from_attributes_view 	= Files.getFileAttributeView( from_file.toPath(), BasicFileAttributeView.class);

			BasicFileAttributes from_attributes = from_attributes_view.readAttributes();

			from_last_modified 	= from_attributes.lastModifiedTime();
			from_last_access	= from_attributes.lastAccessTime();
			from_created		= from_attributes.creationTime();
			
		}catch( Throwable e ){
		}
   		
    	boolean result = renameFileSupport( from_file, to_file, fail_on_existing_directory, file_filter, pl );

    	if ( result ){

    			// try to maintain the file times if they now differ 
    		
    		try{
    			BasicFileAttributeView to_attributes_view 		= Files.getFileAttributeView( to_file.toPath(), BasicFileAttributeView.class);

     			BasicFileAttributes to_attributes 	= to_attributes_view.readAttributes();

    			FileTime to_last_modified 	= to_attributes.lastModifiedTime();
    			FileTime to_last_access		= to_attributes.lastAccessTime();
    			FileTime to_created			= to_attributes.creationTime();

    			if (	from_last_modified.equals( to_last_modified ) && 
    					from_last_access.equals( to_last_access ) && 
    					from_created.equals( to_created )){

    			}else{
    				
    				to_attributes_view.setTimes( from_last_modified, from_last_access, from_created );
    			}
    		}catch( Throwable e ){
    		}
    	}

    	return( result );
    }
        
    private static boolean
    renameFileSupport(
    	File        		from_file,
    	File        		to_file,
    	boolean     		fail_on_existing_directory,
    	FileFilter  		file_filter,
    	ProgressListener	pl )
    {
    	if ( !from_file.exists()){

    		Debug.out( "renameFile: source file '" + from_file + "' doesn't exist, failing" );

    		return( false );
    	}
    	
    	boolean to_file_exists = to_file.exists();

    	if ( to_file_exists ) {

    		if ( from_file.equals( to_file )){
    			
    			if ( areFilePathsIdentical( from_file, to_file )){
    				
    				return( true );	// nothing to do
    				
    			}else{
    				
    				if ( from_file.renameTo( to_file )){
    					
    					return( true );
    				}
    			}
    		}
    		// on OSX (at least?) to_file.exists returns true if the existing file differs in case only - if we don't find the actual file then
    		// carry on and rename

    		String to_name = to_file.getName();

    		String[] existing_files = to_file.getParentFile().list();

    		if ( !Arrays.asList( existing_files ).contains( to_name )) {

    			to_file_exists = false;
    		}
    	}
    	
        /**
         * If the destination exists, we only fail if requested.
         */
    	
        if ( to_file_exists && ( fail_on_existing_directory || from_file.isFile() || to_file.isFile())) {

        	Debug.out( "renameFile: target file '" + to_file + "' already exists, failing" );

            return( false );
        }
        
		if ( pl != null ){
			
			while( true ){
				
				int state =  pl.getState();
				
				if ( state == ProgressListener.ST_PAUSED ){
		
					try{
						Thread.sleep(250);
					}catch( Throwable e ){
					}
					
				}else if ( state == ProgressListener.ST_CANCELLED ){
					
					Debug.out( "renameFile: Cancelled" );
					
					return( false );
					
				}else{
					
					break;
				}
			}
		}	
        
    	File to_file_parent = to_file.getParentFile();
    	
    	if ( !to_file_parent.exists()){
    		
    		FileUtil.mkdirs(to_file_parent);
    	}

    	if ( from_file.isDirectory()){

    		File[] files = null;
    		
    		if ( file_filter != null ){
    			
    			files = from_file.listFiles(file_filter);
    			
    		}else{
    			
    			files = from_file.listFiles();
    		}

    		if ( files == null ){

    				// empty dir

    			return( true );
    		}

    		int	last_ok = 0;

    		if ( !to_file.exists()){
    			
    			to_file.mkdir();
    		}

    		for (int i=0;i<files.length;i++){

  				File	ff = files[i];
				File	tf = newFile( to_file, ff.getName());

    			try{
     				if ( renameFile( ff, tf, fail_on_existing_directory, file_filter, pl )){

    					last_ok++;

    				}else{

    					break;
    				}
    			}catch( Throwable e ){

    				Debug.out( "renameFile: failed to rename file '" + ff.toString() + "' to '"
									+ tf.toString() + "'", e );

    				break;
    			}
    		}

    		if ( last_ok == files.length ){

    			File[]	remaining = from_file.listFiles();

    			if ( remaining != null && remaining.length > 0 ){
    				// This might be important or not. We'll make it a debug message if we had a filter,
    				// or log it normally otherwise.
    				if (file_filter == null) {
    					Debug.out( "renameFile: files remain in '" + from_file.toString()
									+ "', not deleting");
    				}
    				else {
    					/* Should we log this? How should we log this? */
    					return true;
    				}

    			}else{

    				if ( !from_file.delete()){
    					
    					Debug.out( "renameFile: failed to delete '" + from_file.toString() + "'" );
    				}
    			}

    			return( true );
    		}

    			// recover by moving files back

      		for (int i=0;i<last_ok;i++){

				File	ff = files[i];
				File	tf = newFile( to_file, ff.getName());

    			try{
    				// null - We don't want to use the file filter, it only refers to source paths.
    				
                    if ( !renameFile( 
                    		tf, 
                    		ff, 
                    		false, 
                    		null, 
                    		new ProgressListener()
                    		{
                    			public void
                    			setTotalSize(
                    				long	size )
                    			{
                    			}
                    			
                    			@Override
                    			public void 
                    			setCurrentFile(File file)
                    			{
                    				if ( pl != null ){
                    					try{
                    						pl.setCurrentFile( file );
                    					}catch( Throwable e ){
                    						Debug.out( e );
                    					}
                    				}
                    			}
                    			public void
                    			bytesDone(
                    				long	num )
                    			{
                    				if ( pl != null ){
                    					try{
                    						pl.bytesDone( -num );
                    					}catch( Throwable e ){
                    						Debug.out( e );
                    					}
                    				}
                    			}
                    			
                    			public int
                    			getState()
                    			{
                    				return( ST_NORMAL );
                    			}
                    			
                    			public void
                    			complete()
                    			{
                    				
                    			}
                    		}))
                    {
    					Debug.out( "renameFile: recovery - failed to move file '" + tf.toString()
										+ "' to '" + ff.toString() + "'" );
    				}
    			}catch( Throwable e ){
    				Debug.out("renameFile: recovery - failed to move file '" + tf.toString()
									+ "' to '" + ff.toString() + "'", e);

    			}
      		}

      		return( false );

    	}else{

    		boolean	same_drive = false;
    		
			try{
				/* FileStore is minSDK 26 on Android
				FileStore fs1 = Files.getFileStore( from_file.toPath());
				FileStore fs2 = Files.getFileStore( to_file_parent.toPath());
				*/

				Class claPath = Class.forName("java.nio.file.Path");
				Class claFiles = Class.forName("java.nio.file.Files");
				Method mPath_getFileStore = claFiles.getMethod("getFileStore", claPath);

				Method mToPath = from_file.getClass().getMethod("toPath");
				/* Path */ Object from_file_toPath = mToPath.invoke(from_file);
				/* Path */ Object to_file_parent_toPath = mToPath.invoke(to_file_parent);

				/* FileStore */ Object fs1 = mPath_getFileStore.invoke(null, from_file_toPath);
				/* FileStore */ Object fs2 = mPath_getFileStore.invoke(null, to_file_parent_toPath);

   				if ( fs1.equals( fs2 )){

   					same_drive = true;
   				}
			}catch( Throwable e ){
			}
			
    		boolean	use_copy = COConfigurationManager.getBooleanParameter("Copy And Delete Data Rather Than Move");

    		if ( use_copy ){

        		boolean	move_if_same_drive = COConfigurationManager.getBooleanParameter("Move If On Same Drive");

     			if ( move_if_same_drive && same_drive ){

     				use_copy = false;
    			}
    		}

    		if ( !use_copy ){
    			
    			if ( pl != null && !same_drive ){
    				
    				use_copy = true;	// so we get incremental progress reports
    			}
    		}
    		
    		if ( use_copy ){
    			
    			return( reallyCopyFile( from_file, to_file, pl ));
    			
    		}else{
    			
    			if ( pl != null ){
					
					try{
						
						pl.setCurrentFile( from_file );
						
					}catch( Throwable e ){
						
						Debug.out( e );
					}
				}
    			
    			if ( from_file.renameTo( to_file )){
    				
    				if ( pl != null ){
    					
    					try{
    						
    						pl.bytesDone( to_file.length());
    						
    					}catch( Throwable e ){
    						
    						Debug.out( e );
    					}
    				}
    				
    				return( true );
    				
    			}else{
    				
    					// attempt to copy
    				
    				return( reallyCopyFile( from_file, to_file, pl ));
    			}
    		}
    	}
    }

    private static boolean
    reallyCopyFile(
    	File				from_file,
    	File				to_file,
    	ProgressListener	pl )
    {
    	if ( pl != null ){
			
			try{
				
				pl.setCurrentFile( from_file );
				
			}catch( Throwable e ){
				
				Debug.out( e );
			}
		}
    	
    	boolean		success	= false;

    	// can't rename across file systems under Linux - try copy+delete

    	FileInputStream 	from_is 	= null;
    	FileOutputStream 	to_os		= null;
    	DirectByteBuffer	buffer		= null;

    	long total_reported = 0;
    	
    	try{
    		final int BUFFER_SIZE = 128*1024;

    		buffer = DirectByteBufferPool.getBuffer( DirectByteBuffer.AL_EXTERNAL, BUFFER_SIZE );

    		ByteBuffer bb = buffer.getBuffer( DirectByteBuffer.SS_EXTERNAL  );

    		from_is 	= newFileInputStream( from_file );
    		to_os 		= newFileOutputStream( to_file );

    		FileChannel from_fc = from_is.getChannel();
    		FileChannel to_fc 	= to_os.getChannel();

    		long	rem = from_fc.size();

    		while( rem > 0 ){

    			if ( pl != null ){
    				
    				while( true ){
    					
    					int state =  pl.getState();
    					
    					if ( state == ProgressListener.ST_PAUSED ){
    			
    						try{
    							Thread.sleep(250);
    							
    						}catch( Throwable e ){
    						}
    						
    					}else if ( state == ProgressListener.ST_CANCELLED ){
    						
    						throw( new IOException( "Cancelled" ));
    						   						
    					}else{
    						
    						break;
    					}
    				}
    			}
    			
    			int	to_read = (int)Math.min( rem, BUFFER_SIZE );

    			bb.position( 0 );
    			bb.limit( to_read );

    			while( bb.hasRemaining()) {

    				from_fc.read( bb );
    			}

    			bb.position( 0 );

    			to_fc.write( bb );

    			rem -= to_read;

    			if ( pl != null ){
    				
    				try{
    					total_reported += to_read;
    					
    					pl.bytesDone( to_read );
    					
    				}catch( Throwable e ){
    					
    					Debug.out( e );
    				}
    			}
    		}

    		from_is.close();

    		from_is	= null;

    		to_os.close();

    		to_os = null;

    		if ( !from_file.delete()){
    			Debug.out( "renameFile: failed to delete '"
    					+ from_file.toString() + "'" );

    			throw( new Exception( "Failed to delete '" + from_file.toString() + "'"));
    		}

    		success	= true;

    		return( true );

    	}catch( Throwable e ){

    		Debug.out( "renameFile: failed to rename '" + from_file.toString()
    		+ "' to '" + to_file.toString() + "'", e );

    		return( false );

    	}finally{

    		if ( from_is != null ){

    			try{
    				from_is.close();

    			}catch( Throwable e ){
    			}
    		}

    		if ( to_os != null ){

    			try{
    				to_os.close();

    			}catch( Throwable e ){
    			}
    		}

    		if ( buffer != null ){

    			buffer.returnToPool();
    		}

    		// if we've failed then tidy up any partial copy that has been performed

    		if ( !success ){

    			if ( pl != null ){
    				
    				try{
    					pl.bytesDone( -total_reported );
    					
    				}catch( Throwable e ){
    					
    					Debug.out( e );
    				}
    			}
    			
    			if ( to_file.exists()){

    				to_file.delete();
    			}
    		}
    	}
    }

	public static FileInputStream newFileInputStream(File from_file)
			throws FileNotFoundException {
		return fileHandling.newFileInputStream(from_file);
	}

	public static FileOutputStream newFileOutputStream(File file)
			throws FileNotFoundException {
   	return fileHandling.newFileOutputStream(file, false);
	}

	public static FileOutputStream newFileOutputStream(File file, boolean append)
			throws FileNotFoundException {
		return fileHandling.newFileOutputStream(file, append);
	}

    /*
    private static boolean
    transfer(
    	File				from_file,
    	File				to_file,
    	ProgressListener	pl )
    {
    	if ( pl == null ){
    		
    		return( from_file.renameTo( to_file ));
    	}
    	
    		// documentation claims that transferFrom has the potential to be much more efficient than doing the read/writes
    		// ourselves but I'm not convinced...
    	
		FileInputStream 	from_is 	= null;
		FileOutputStream 	to_os		= null;
		
		boolean	success = false;
		
		try{			
			from_is 	= new FileInputStream( from_file );
			to_os 		= new FileOutputStream( to_file );

			FileChannel from_fc = from_is.getChannel();
			FileChannel to_fc 	= to_os.getChannel();
			
			long	size = from_fc.size();
			
			long done = 
				to_fc.transferFrom(
					new ReadableByteChannel(){
						
						@Override
						public boolean 
						isOpen()
						{
							return( from_fc.isOpen());
						}
						
						@Override
						public void 
						close() 
							throws IOException
						{
							from_fc.close();
						}
						
						@Override
						public int 
						read(
							ByteBuffer dst ) 
						
							throws IOException
						{
							int	read = from_fc.read( dst );
							
							if ( pl != null ){
								try{
									pl.bytesDone( read );
								}catch( Throwable e ){
									Debug.out( e );
								}
							}
							
							return( read );
						}
					},
					0,
					size );

			if ( done != size ){
				
				throw( new Exception( "Incorrect byte count transferred: " + done + "/" + size ));
			}
			
			from_is.close();

			from_is	= null;

			to_os.close();

			to_os = null;

			if ( !from_file.delete()){
				Debug.out( "renameFile: failed to delete '"
								+ from_file.toString() + "'" );

				return( false );
			}

			success	= true;

			return( true );

		}catch( Throwable e ){

			Debug.out( "renameFile: failed to rename '" + from_file.toString()
							+ "' to '" + to_file.toString() + "'", e );

			return( false );

		}finally{

			if ( from_is != null ){

				try{
					from_is.close();

				}catch( Throwable e ){
				}
			}

			if ( to_os != null ){

				try{
					to_os.close();

				}catch( Throwable e ){
				}
			}
			
				// if we've failed then tidy up any partial copy that has been performed

			if ( !success ){

				if ( to_file.exists()){

					to_file.delete();
				}
			}
		}
    }
    */
    
    public static boolean
    writeStringAsFile(
    	File		file,
    	String		text )
    {
    	return( writeStringAsFile( file, text, "UTF-8" ));
    }

    public static boolean
    writeStringAsFile(
    	File		file,
    	String		text,
    	String		charset )
    {
    	try{
    		return( writeBytesAsFile2( file.getAbsolutePath(), text.getBytes( charset )));

    	}catch( Throwable e ){

    		Debug.out( e );

    		return( false );
    	}
    }
    
    public static void
    writeBytesAsFile(
    	String filename,
    	byte[] file_data )
    {
    		// pftt, this is used by emp so can't fix signature to make more useful

    	writeBytesAsFile2( filename, file_data );
    }

    public static boolean
    writeBytesAsFile2(
    	String filename,
    	byte[] file_data )
    {
    	try{
    		File file = newFile( filename );

    		if ( !file.getParentFile().exists()){

    			file.getParentFile().mkdirs();
    		}

    		FileOutputStream out = newFileOutputStream( file );

    		try{
    			out.write( file_data );

     		}finally{

       			out.close();
    		}

    		return( true );

    	}catch( Throwable t ){

    		Debug.out( "writeBytesAsFile:: error: ", t );

    		return( false );
    	}
    }

    private static AsyncDispatcher	recycler = new AsyncDispatcher( "Recycler" );
    
	public static boolean
	deleteWithRecycle(
		File		file,
		boolean		force_no_recycle )
	{
		if ( COConfigurationManager.getBooleanParameter("Move Deleted Data To Recycle Bin" ) && !force_no_recycle ){

		    final PlatformManager	platform  = PlatformManagerFactory.getPlatformManager();

		    if ( platform.hasCapability(PlatformManagerCapabilities.RecoverableFileDelete)){

		    	int	queued = recycler.getQueueSize();
		    	
		    	int QUEUE_LIMIT = 1000;
		    	
		    	if ( queued < QUEUE_LIMIT ){
		    		
			    	boolean[]	deleted = { false };
			    	
			    	AESemaphore	sem = new AESemaphore( "Recycler" );
			    	
			    	recycler.dispatch(AERunnable.create(()->{
			    		
			    		try{
				    	
			    			platform.performRecoverableFileDelete( file.getAbsolutePath());
			    			
			    			synchronized( deleted ){
			    			
			    				deleted[0] = true;
			    			}
			    			
			    		}catch( Throwable e ){
			    			
			    		}finally{
			    			
			    			sem.release();
			    		}
			    	}));
			    	
			    	if ( !sem.reserve( 30*1000 )){
			    		
			    		Debug.out( "Recycling of file '" + file + "' took too long, aborted" );
			    	}
			    	
			    	synchronized( deleted ){
			    		
			    		if ( deleted[0] ){
			    			
			    			return( true );
			    		}
			    	}
		    	}else if ( queued == QUEUE_LIMIT ){
		    		
		    		Debug.out( "Recycler queue limit exceeded" );
		    	}
		    }

		    return( file.delete());
			    
		}else{

			return( file.delete());
		}
	}

	public static String
	translateMoveFilePath(
		String old_root,
		String new_root,
		String file_to_move )
	{
			// we're trying to get the bit from the file_to_move beyond the old_root and append it to the new_root

		if ( !file_to_move.startsWith(old_root)){

			return null;
		}

		if ( old_root.equals( new_root )){

				// roots are the same -> nothings gonna change

			return( file_to_move );
		}

		if ( new_root.equals( file_to_move )){

				// new root already the same as the from file, nothing to change

			return( file_to_move );
		}

		String file_suffix = file_to_move.substring(old_root.length());

		if ( file_suffix.startsWith(File.separator )){

			file_suffix = file_suffix.substring(1);

		}else{
				// hack to deal with special known case of this
				// old_root:  c:\fred\jim.dat
				// new_root:  c:\temp\egor\grtaaaa
				// old_file:  c:\fred\jim.dat.az!

			if ( new_root.endsWith( File.separator )){

				Debug.out( "Hmm, this is not going to work out well... " + old_root + ", " + new_root + ", " + file_to_move );

			}else{

					// deal with case where new root already has the right suffix

				if ( new_root.endsWith( file_suffix )){

					return( new_root );
				}

				return( new_root + file_suffix );
			}
		}

		if ( new_root.endsWith(File.separator)){

			new_root = new_root.substring( 0, new_root.length()-1 );
		}

		return new_root + File.separator + file_suffix;
	}

	public static void
	runAsTask(
		CoreOperationTask task )
	{
		runAsTask( CoreOperation.OP_FILE_MOVE, task );
	}

	public static void
	runAsTask(
		int					op_type,
		CoreOperationTask 	task )
	{
		Core core = CoreFactory.getSingleton();

		core.executeOperation( op_type, task );
	}
		
	
	/**
	 * Makes Directories as long as the directory isn't directly in Volumes (OSX)
	 * @param f
	 * @return
	 */
	
	public static boolean 
	mkdirs(
		File f) 
	{
		if (Constants.isOSX) {
			Pattern pat = Pattern.compile("^(/Volumes/[^/]+)");
			Matcher matcher = pat.matcher(f.getParent());
			if (matcher.find()) {
				String sVolume = matcher.group();
				File fVolume = newFile(sVolume);
				if (!fVolume.isDirectory()) {
					Logger.log(new LogEvent(LOGID, LogEvent.LT_WARNING, sVolume
							+ " is not mounted or not available."));
					return false;
				}
			}
		}
		return f.mkdirs();
	}

	/**
	 * Gets the extension of a file name, ensuring we don't go into the path
	 *
	 * @param fName  File name
	 * @return extension, with the '.'
	 */
	public static String getExtension(String fName) {
		final int fileSepIndex = fName.lastIndexOf(File.separator);
		final int fileDotIndex = fName.lastIndexOf('.');
		if (fileSepIndex == fName.length() - 1 || fileDotIndex == -1
				|| fileSepIndex > fileDotIndex) {
			return "";
		}

		return fName.substring(fileDotIndex);
	}

	public static String
	readFileAsString(
		File	file,
		int		size_limit,
		String charset)

		throws IOException
	{
		FileInputStream fis = newFileInputStream(file);
		try {
			return readInputStreamAsString(fis, size_limit, charset);
		} finally {

			fis.close();
		}
	}

	public static String
	readFileAsString(
		File	file,
		int		size_limit )

		throws IOException
	{
		FileInputStream fis = newFileInputStream(file);
		try {
			return readInputStreamAsString(fis, size_limit);
		} finally {

			fis.close();
		}
	}

	public static String
	readGZippedFileAsString(
		File	file,
		int		size_limit )

		throws IOException
	{
		FileInputStream fis = newFileInputStream(file);

		try {
			GZIPInputStream zis = new GZIPInputStream( fis );

			return readInputStreamAsString(zis, size_limit);
		} finally {

			fis.close();
		}
	}
	public static String
	readInputStreamAsString(
		InputStream is,
		int		size_limit )

		throws IOException
	{
		return readInputStreamAsString(is, size_limit, "ISO-8859-1");
	}

	public static String
	readInputStreamAsString(
		InputStream 	is,
		int				size_limit,
		String 			charSet)

		throws IOException
	{
		StringBuilder result = new StringBuilder(1024);

		byte[] buffer = new byte[64*1024];

		while (true) {

			int len = is.read(buffer);

			if (len <= 0) {

				break;
			}

			result.append(new String(buffer, 0, len, charSet));

			if (size_limit >= 0 && result.length() > size_limit) {

				result.setLength(size_limit);

				break;
			}
		}

		return (result.toString());
	}

	public static String readInputStreamAsString(InputStream is, int size_limit,
			int timeoutMillis, String charSet)
			throws IOException {
		StringBuilder result = new StringBuilder(1024);
		long maxTimeMillis = System.currentTimeMillis() + timeoutMillis;
		byte[] buffer = new byte[1024];

		while (System.currentTimeMillis() < maxTimeMillis) {
			int readLength = Math.min(is.available(), buffer.length);

			int len = is.read(buffer, 0, readLength);
			if (len == -1)
				break;

			result.append(new String(buffer, 0, len, charSet));

			if (size_limit >= 0 && result.length() > size_limit) {

				result.setLength(size_limit);

				break;
			}
		}

		return (result.toString());
	}

	public static String
	readInputStreamAsStringWithTruncation(
		InputStream 	is,
		int				size_limit )

		throws IOException
	{
		StringBuilder result = new StringBuilder(1024);

		byte[] buffer = new byte[64*1024];

		try{
			while (true) {

				int len = is.read(buffer);

				if (len <= 0) {

					break;
				}

				result.append(new String(buffer, 0, len, "ISO-8859-1"));

				if (size_limit >= 0 && result.length() > size_limit) {

					result.setLength(size_limit);

					break;
				}
			}
		}catch( SocketTimeoutException e ){
		}

		return (result.toString());
	}

	public static String
	readFileEndAsString(
		File	file,
		int		size_limit,
		String	charset )

		throws IOException
	{
		FileInputStream	fis = newFileInputStream( file );

		try{
			if ( file.length() > size_limit){

					// doesn't really work with multi-byte chars but woreva

				fis.skip(file.length() - size_limit);
			}

			StringBuilder result = new StringBuilder(1024);

			byte[]	buffer = new byte[64*1024];

			while( true ){

				int	len = fis.read( buffer );

				if ( len <= 0 ){

					break;
				}

					// doesn't really work with multi-byte chars but woreva

				result.append( new String( buffer, 0, len, charset ));

				if ( result.length() > size_limit ){

					result.setLength( size_limit );

					break;
				}
			}

			return( result.toString());

		}finally{

			fis.close();
		}
	}

	public static byte[]
	readInputStreamAsByteArray(
		InputStream		is )

	   	throws IOException
	{
		return( readInputStreamAsByteArray( is, Integer.MAX_VALUE ));
	}

	public static byte[]
	readInputStreamAsByteArray(
		InputStream		is,
		int				size_limit )

		throws IOException
	{
		ByteArrayOutputStream	baos = new ByteArrayOutputStream(32*1024);

		byte[]	buffer = new byte[32*1024];

		while( true ){

			int	len = is.read( buffer );

			if ( len <= 0 ){

				break;
			}

			baos.write( buffer, 0, len );

			if ( baos.size() > size_limit ){

				throw( new IOException( "size limit exceeded" ));
			}
		}

		return( baos.toByteArray());
	}

	public static byte[]
   	readFileAsByteArray(
   		File		file )

   		throws IOException
   	{
   		ByteArrayOutputStream	baos = new ByteArrayOutputStream((int)file.length());

   		byte[]	buffer = new byte[32*1024];

   		InputStream is = newFileInputStream( file );

   		try{
	   		while( true ){

	   			int	len = is.read( buffer );

	   			if ( len <= 0 ){

	   				break;
	   			}

	   			baos.write( buffer, 0, len );
	   		}

	   		return( baos.toByteArray());

   		}finally{

   			is.close();
   		}
   	}

	public static boolean getUsableSpaceSupported()
	{
		return reflectOnUsableSpace != null;
	}

	public static long getUsableSpace(File f)
	{
		try{
			return ((Long)reflectOnUsableSpace.invoke(f)).longValue();

		}catch ( Throwable e){

			return -1;
		}
	}

	public static boolean
	canReallyWriteToAppDirectory()
	{
		if ( !FileUtil.getApplicationFile("bogus").getParentFile().canWrite()){

			return( false );
		}

			// handle vista+ madness

		if ( Constants.isWindowsVistaOrHigher ){

			try{
				File write_test = FileUtil.getApplicationFile( "_az_.dll" );

					// should fail if no perms, but sometimes it's created in
					// virtualstore (if ran from java(w).exe for example)

				FileOutputStream fos = newFileOutputStream( write_test );

				try{
					fos.write(32);

				}finally{

					fos.close();
				}

				write_test.delete();

					// look for a file to try and rename. Unfortunately someone renamed License.txt to GPL.txt and screwed this up in 3020...

				File rename_test = FileUtil.getApplicationFile( "License.txt" );

				if ( !rename_test.exists()){

					rename_test = FileUtil.getApplicationFile( "GPL.txt" );
				}

				if ( !rename_test.exists()){

					File[] files = write_test.getParentFile().listFiles();

					if ( files != null ){

						for ( File f: files ){

							String name = f.getName();

							if ( name.endsWith( ".txt" ) || name.endsWith( ".log" )){

								rename_test = f;

								break;
							}
						}
					}
				}

				if ( rename_test.exists()){

					File target = newFile( rename_test.getParentFile(), rename_test.getName() + ".bak" );

					target.delete();

					rename_test.renameTo( target );

					if ( rename_test.exists()){

						return( false );
					}

					target.renameTo( rename_test );

				}else{

					Debug.out( "Failed to find a suitable file for the rename test" );

						// let's assume we can't to be on the safe side

					return( false );
				}
			}catch ( Throwable e ){

				return( false );
			}
		}

		return( true );
	}

	public static boolean
	canWriteToDirectory(
		File		dir )
	{
			// (dir).canWrite() seems to return true for local file systems at least on windows regardless
			// of effective permissions :(

		if ( !dir.isDirectory()){

			return( false );
		}

		try{
			File temp = AETemporaryFileHandler.createTempFileInDir( dir );

			if ( !temp.delete()){

				temp.deleteOnExit();
			}

			return( true );

		}catch( Throwable e ){

			return( false );
		}
	}
	
	public static void
	log(
		String		str )
	{
		log( str, null );
	}
	
	public static void
	log(
		String		str, 
		Throwable	error )
	{
		synchronized( FileUtil.class ){
			
			if ( file_logger == null ){
				
				file_logger = AEDiagnostics.getLogger( "DiskOps" );
				
				file_logger.enableTimeStamp( true );
				
				file_logger.setForced( true );
			}
		}
		
		file_logger.log( str );
		
		if ( error != null ){
			file_logger.log(error );
		}
	}
	
		/**
		 * Gets the encoding that should be used when writing script files (currently only
		 * tested for windows as this is where an issue can arise...)
		 * We also only test based on the user-data directory name to see if an explicit
		 * encoding switch is requried...
		 * @return null - use default
		 */

	private static boolean 	sce_checked;
	private static String	script_encoding;

	public static String
	getScriptCharsetEncoding()
	{
		synchronized( FileUtil.class ){

			if ( sce_checked ){

				return( script_encoding );
			}

			sce_checked = true;

			String	file_encoding 	= System.getProperty( "file.encoding", null );
			String	jvm_encoding	= System.getProperty( "sun.jnu.encoding", null );

			if ( file_encoding == null || jvm_encoding == null || file_encoding.equals( jvm_encoding )){

				return( null );
			}

			try{

				String	test_str = SystemProperties.getUserPath();

				if ( !new String( test_str.getBytes( file_encoding ), file_encoding ).equals( test_str )){

					if ( new String( test_str.getBytes( jvm_encoding ), jvm_encoding ).equals( test_str )){

						Debug.out( "Script encoding determined to be " + jvm_encoding + " instead of " + file_encoding );

						script_encoding = jvm_encoding;
					}
				}
			}catch( Throwable e ){
			}

			return( script_encoding );
		}
	}
	
	public interface
	ProgressListener
	{
		public int ST_NORMAL	= 1;
		public int ST_PAUSED	= 2;
		public int ST_CANCELLED	= 3;
		
		public void
		setTotalSize(
			long	size );
		
		public void
		setCurrentFile(
			File	file );
		
		public void
		bytesDone(
			long	num );
		
		public int
		getState();
		
		public void
		complete();
	}
	
	public static File
	newFile(
		String		parent,
		String...		subDirs )
	{
		return fileHandling.newFile(parent, subDirs);
	}

	public static File
	newFile(
		File		parent_file,
		String...		subDirs )
	{
		return fileHandling.newFile(parent_file, subDirs);
	}
	
	public static File
	newFile(
		URI		uri )
	{
		return fileHandling.newFile(uri);
	}

	public static FileAccessor
	newFileAccessor(
		File			file,
		String			access_mode)
		throws FileNotFoundException
	{
		return fileHandling.newFileAccessor(file, access_mode);
	}

	/**
	 * @return {@link File#getAbsolutePath()}.contains({@link File#separator} + path + {@link File#separator})
	 * @implNote must handle path containing {@link File#separator}
	 */
	public static boolean containsPathSegment(File f, String path, boolean caseSensitive) {
		return fileHandling.containsPathSegment(f, path, caseSensitive);
	}

	/**
	 * @return path string relative to <code>parentDir</code>. 
	 *         <code>null</code> if file is not in parentDir.
	 *         Empty String if file is parentDir.
	 */
	public static String getRelativePath(File parentDir, File file) {
		return fileHandling.getRelativePath(parentDir, file);
	}

	public static class FileHandlerHack
		extends FileHandler
	{
		@Override
		public File newFile(File parent, String... subDirs) {
			if (!(parent instanceof FileHack)) {
				return super.newFile(parent, subDirs);
			}
			if (subDirs == null || subDirs.length == 0) {
				return parent;
			}

			FileHack file = new FileHack((FileHack) parent, subDirs[0]);
			for (int i = 1, subDirsLength = subDirs.length;
				i < subDirsLength; i++) {
				file = new FileHack(file, subDirs[i]);
			}
			return file;
		}

		@Override
		public File newFile(String parent, String... subDirs) {
			if (parent != null && !parent.startsWith(FileHack.hack_prefix)) {
				return super.newFile(parent, subDirs);
			}
			FileHack fileHack = new FileHack(parent);
			if (subDirs == null || subDirs.length == 0) {
				return fileHack;
			}

			FileHack file = new FileHack(fileHack, subDirs[0]);
			for (int i = 1, subDirsLength = subDirs.length; i < subDirsLength; i++) {
				file = new FileHack(file, subDirs[i]);
			}
			return file;
		}

		@Override
		public File getCanonicalFileSafe(File file) {
			if (file instanceof FileHack) {
				return file.getAbsoluteFile();
			}

			return super.getCanonicalFileSafe(file);
		}

		@Override
		public String getCanonicalPathSafe(File file) {
			try{
				if (file instanceof FileHack) {
					return file.getCanonicalPath();
				}
			}catch( Throwable e ){

				return( file.getAbsolutePath());
			}

			return super.getCanonicalPathSafe(file);
		}

		@Override
		public boolean isAncestorOf(File _parent, File _child) {
			if (_parent instanceof FileHack && _child instanceof FileHack) {
				return getRelativePath(_parent, _child) != null;
			}

			return super.isAncestorOf(_parent, _child);
		}

		@Override
		public FileAccessor newFileAccessor(File file, String access_mode)
			throws FileNotFoundException {
			if (file instanceof FileHack) {
				file = ((FileHack) file).getHackTarget();
			}
			return super.newFileAccessor(file, access_mode);
		}
	}
	
	public static class
	FileHack
		extends File
	{
		private static final String hack_target = "C:\\Temp\\ContentStore";
		private static final String hack_prefix = "content://";

		final private String 	path;
		final private File		target;
		
		private
		FileHack(
			String	_path )
		{
			super( Base32.encode( _path.getBytes( Constants.UTF_8 )));
			
			path	= _path;
			
			target = new File( hack_target, path.substring( hack_prefix.length()));
		}

		// Called via Reflection
		private FileHack(
			FileHack path, String subPath) 
		{
			this(new File(path.toString(), subPath.startsWith(DIR_SEP)
					? subPath.substring(1) : subPath).toString());
		}
		
		public File
		getHackTarget()
		{
			return( target );
		}
		
		public File
		getAbsoluteFile()
		{
			return( this );
		}
		
		public String
		getAbsolutePath()
		{
			return( path );
		}
		
		public boolean
		exists()
		{
			return( target.exists());
		}
		
		@Override
		public String getName(){
			String temp = path;
			if ( temp.endsWith( DIR_SEP )){
				temp = temp.substring( 0, temp.length()-1);
			}
			
			int	pos = temp.lastIndexOf( DIR_SEP );
			
			if ( pos == -1 ){
				
				return( "" );
				
			}else{
				
				return( temp.substring( pos+1 ));
			}
		}
		
		@Override
		public String getParent(){
			return( getParentFile().getAbsolutePath());
		}
		
		@Override
		public File getParentFile(){
			String temp = path;
			if ( temp.endsWith( DIR_SEP )){
				temp = temp.substring( 0, temp.length()-1);
			}
			
			int	pos = temp.lastIndexOf( DIR_SEP );
			
			if ( pos == -1 ){
				
				return( null );
				
			}else{
				
				return( new FileHack( temp.substring( 0, pos+1 )));
			}
		}
		
		@Override
		public String getPath(){
			return( path );
		}
		
		
		public boolean
		isFile()
		{
			return( target.isFile());
		}
		
		public boolean
		isDirectory()
		{
			return( target.isDirectory());
		}	
		
		@Override
		public int compareTo(File other){
			if ( other instanceof FileHack ){
				return( path.compareTo((((FileHack)other).path)));
			}
			return( -1 );
		}
		
		@Override
		public int hashCode(){
			return( path.hashCode());
		}
		
		@Override
		public boolean equals(Object other){
			if ( other instanceof FileHack ){
				return( path.equals((((FileHack)other).path)));
			}
			return( false );
		}
			
		@Override
		public boolean createNewFile() throws IOException{
			return( target.createNewFile());
		}
		
		@Override
		public boolean delete(){
			return( target.delete());
		}
				
		@Override
		public File getCanonicalFile() throws IOException{
			return( this );
		}
		
		@Override
		public String getCanonicalPath() throws IOException{
			return( path );
		}
				
		@Override
		public long lastModified(){
			return( target.lastModified());
		}
		
		@Override
		public String[] list(){
			File[] files = listFiles();
			if ( files != null ){
				String[] result = new String[files.length];
				
				for ( int i=0;i<result.length;i++){
					result[i] = files[i].getAbsolutePath();
				};
				
				return( result );
			}else{
				return( null );
			}
		}
		
		@Override
		public long length(){
			return( target.length());
		}
				
		@Override
		public File[] listFiles(){
			File[] files = target.listFiles();
			
			if ( files != null ){
			
				for ( int i=0;i<files.length;i++){
					
					files[i] = new FileHack( hack_prefix + files[i].getAbsolutePath().substring( hack_target.length() + 1 ));
				}	
			}
			
			return( files );
		}
		
		@Override
		public boolean mkdir(){
			return( target.mkdir());
		}
		
		@Override
		public boolean mkdirs(){
			return( target.mkdirs());
		}

		@Override
		public String toString(){
			return( path );
		}
		
		
		
			// NOT NEEDED
		
		@Override
		public boolean canExecute(){
			Debug.out( "!" );
			return super.canExecute();
		}
		
		@Override
		public boolean canRead(){
			Debug.out( "!" );
			return super.canRead();
		}
		
		@Override
		public boolean canWrite(){
			Debug.out( "!" );
			return super.canWrite();
		}

		@Override
		public void deleteOnExit(){
			Debug.out( "!" );
			super.deleteOnExit();
		}

		@Override
		public long getFreeSpace(){
			Debug.out( "!" );
			return super.getFreeSpace();
		}
		
		@Override
		public long getTotalSpace(){
			Debug.out( "!" );
			return super.getTotalSpace();
		}
		
		@Override
		public long getUsableSpace(){
			Debug.out( "!" );
			return super.getUsableSpace();
		}
		
		@Override
		public boolean isAbsolute(){
			Debug.out( "!" );
			return super.isAbsolute();
		}
		
		@Override
		public boolean isHidden(){
			Debug.out( "!" );
			return super.isHidden();
		}

		@Override
		public String[] list(FilenameFilter filter){
			Debug.out( "!" );
			return super.list(filter);
		}

		@Override
		public File[] listFiles(FileFilter filter){
			Debug.out( "!" );
			return super.listFiles(filter);
		}
		
		@Override
		public File[] listFiles(FilenameFilter filter){
			Debug.out( "!" );
			return super.listFiles(filter);
		}
				
		@Override
		public boolean renameTo(File dest){
			Debug.out( "!" );
			return super.renameTo(dest);
		}
		
		@Override
		public boolean setExecutable(boolean executable){
			Debug.out( "!" );
			return super.setExecutable(executable);
		}
		
		@Override
		public boolean setExecutable(boolean executable, boolean ownerOnly){
			Debug.out( "!" );
			return super.setExecutable(executable, ownerOnly);
		}
		
		@Override
		public boolean setLastModified(long time){
			Debug.out( "!" );
			return super.setLastModified(time);
		}
		
		@Override
		public boolean setReadable(boolean readable){
			Debug.out( "!" );
			return super.setReadable(readable);
		}
		@Override
		public boolean setReadable(boolean readable, boolean ownerOnly){
			Debug.out( "!" );
			return super.setReadable(readable, ownerOnly);
		}
		
		@Override
		public boolean setReadOnly(){
			Debug.out( "!" );
			return super.setReadOnly();
		}
		@Override
		public boolean setWritable(boolean writable){
			Debug.out( "!" );
			return super.setWritable(writable);
		}
		@Override
		public boolean setWritable(boolean writable, boolean ownerOnly){
			Debug.out( "!" );
			return super.setWritable(writable, ownerOnly);
		}
		
		@Override
		public Path toPath(){
			Debug.out( "!" );
			return( super.toPath());
		}
		
		@Override
		public URI toURI(){
			Debug.out( "!" );
			return super.toURI();
		}
		@Override
		public URL toURL() throws MalformedURLException{
			Debug.out( "!" );
			return super.toURL();
		}
	}
}
