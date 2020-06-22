/*
 * Created on 29-Oct-2004
 * Created by Paul Gardner
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

import java.io.File;
import java.io.IOException;


/**
 * @author parg
 *
 */

public class
AETemporaryFileHandler
{
    private static final boolean PORTABLE = System.getProperty(SystemProperties.SYSPROP_PORTABLE_ROOT, "" ).length() > 0;

	private static final String	PREFIX = "AZU";
	private static final String	SUFFIX = ".tmp";

	private static boolean	started_up;
	private static File		tmp_dir;

	public static synchronized void
	startup()
	{
		if ( started_up ){

			return;
		}

		started_up	= true;

		try{
			tmp_dir		= FileUtil.getUserFile( "tmp" );

			if ( tmp_dir.exists()){

				File[]	files = tmp_dir.listFiles();

				if ( files != null ){

					for (int i=0;i<files.length;i++){

						File	file = files[i];

						if ( file.getName().startsWith(PREFIX) && file.getName().endsWith(SUFFIX)){

							if ( file.isDirectory()){

								FileUtil.recursiveDelete( file );

							}else{

								file.delete();
							}
						}
					}
				}
			}else{

				tmp_dir.mkdir();
			}

		}catch( Throwable e ){

			try{
				tmp_dir = File.createTempFile(PREFIX,SUFFIX).getParentFile();

			}catch( Throwable f ){

				tmp_dir = new File("");
			}

				// with webui we don't have the file stuff so this fails with class not found

			if ( !(e instanceof NoClassDefFoundError )){

				Debug.printStackTrace( e );
			}
		}
	}

	public static File
	getTempDirectory()
	{
		startup();

		return( tmp_dir );
	}

	public static boolean
	isTempFile(
		File		file )
	{
		if ( !file.exists()){

			return( false );
		}

		try{
			String s1 = file.getParentFile().getCanonicalPath();
			String s2 = tmp_dir.getCanonicalPath();

			if ( !s1.equals(s2)){

				return( false );
			}

			String	name = file.getName();

			if ( !name.startsWith( PREFIX )){

				return( false );
			}

			if ( !name.endsWith( SUFFIX )){

				return( false );
			}

			return( true );

		}catch( Throwable e ){

			Debug.out( e );

			return( false );
		}
	}

	public static File
	createTempFile()

		throws IOException
	{
		startup();

		return( File.createTempFile( PREFIX, SUFFIX, tmp_dir ));
	}

	public static File
	createTempFileInDir(
		File	parent_dir )

		throws IOException
	{
		startup();

		return( File.createTempFile( PREFIX, SUFFIX, parent_dir ));
	}
	
	public static synchronized File
	createSemiTempFile(
		String	preferred_name )

		throws IOException
	{
		preferred_name = FileUtil.convertOSSpecificChars( preferred_name, false );
		
		File result = createSemiTempFile();
		
		if ( preferred_name.length() > 0 ){
			
			File dir = result.getParentFile();
			
			for ( int i=0;i<10;i++){
				
				File file = FileUtil.newFile( dir, preferred_name );
				
				if ( !file.exists()){
					
					try{
						if ( file.createNewFile()){
							
							result.delete();
							
							return( file );
						}
					}catch( Throwable e ){
						
					}
				}
				
				preferred_name = "_" + preferred_name;
			}
		}
		
		return( result );
	}

	public static File
	createSemiTempFile()

		throws IOException
	{
		if ( PORTABLE ){

			try{
				File stmp_dir = FileUtil.getUserFile( "tmp2" );

				if ( !stmp_dir.exists()){

					stmp_dir.mkdirs();
				}

				if ( stmp_dir.canWrite()){

					return( File.createTempFile( PREFIX, null, stmp_dir ));
				}
			}catch( Throwable e ){

				Debug.out( e );
			}
		}

		return( File.createTempFile( PREFIX, null ));
	}

	public static File
	createTempDir()

		throws IOException
	{
		startup();

		for (int i=0;i<16;i++){

			File f = File.createTempFile( PREFIX, SUFFIX, tmp_dir );

			f.delete();

			if ( f.mkdirs()){

				return( f );
			}
		}

		throw( new IOException( "Failed to create temporary directory in " + tmp_dir ));
	}
}
