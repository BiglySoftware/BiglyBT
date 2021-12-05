/*
 * Created on 04-Oct-2004
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

package com.biglybt.core.util.jar;

import java.io.*;
import java.net.URL;
import java.net.URLClassLoader;

import com.biglybt.core.config.COConfigurationManager;
import com.biglybt.core.logging.LogAlert;
import com.biglybt.core.logging.Logger;
import com.biglybt.core.util.AETemporaryFileHandler;
import com.biglybt.core.util.Debug;
import com.biglybt.core.util.FileUtil;

/**
 * @author parg
 *
 */
public class
AEJarSigner2
{
	protected static Class	JarSigner_class;

	protected final String		keystore_name;
	protected final String		keystore_password;
	protected final String		alias;

	public
	AEJarSigner2(
		String		_alias,
		String		_keystore_name,
		String		_keystore_password )
	{
		alias				= _alias;
		keystore_name		= _keystore_name;
		keystore_password	= _keystore_password;
	}

	protected void
	loadJarSigner()

		throws IOException
	{
		File	tools_dir;

		String	manual_tools_dir = COConfigurationManager.getStringParameter( "Security.JAR.tools.dir" );

		if ( manual_tools_dir.length() == 0 ){

			String		java_home	= System.getProperty( "java.home" );

				// if it ends in "jre" then go up one dir
				// then look for lib/tools.jar

			File	jh	= new File( java_home );

			if ( jh.getName().equalsIgnoreCase("jre")){

				jh = jh.getParentFile();

			}else{

					// otherwise, for 1.5, see if the JDK is also installed in default position

				String	dir_name = jh.getName();

				if ( dir_name.startsWith( "jre" )){

					dir_name = "jdk" + dir_name.substring(3);

					jh	= new File( jh.getParentFile(), dir_name );
				}
			}

			tools_dir = new File( jh, "lib" );

		}else{

			tools_dir = new File( manual_tools_dir );
		}

		File	tools_jar = new File( tools_dir, "tools.jar" );

		// System.out.println( "tools_jar = " + tools_jar.toString() + ", exists = " + tools_jar.exists());

		if ( tools_jar.exists()){

			try{
				ClassLoader cl = new URLClassLoader(new URL[]{tools_jar.toURI().toURL()},AEJarSigner2.class.getClassLoader());

				JarSigner_class = cl.loadClass( "sun.security.tools.JarSigner" );

			}catch( Throwable e ){
				Logger.logTextResource(new LogAlert(LogAlert.UNREPEATABLE,
						LogAlert.AT_ERROR, "Security.jar.signfail"), new String[] { e
						.getMessage() });

				Debug.printStackTrace(e);

				throw( new IOException( "JAR signing fails: " + e.getMessage()));
			}

		}else{
			Logger.logTextResource(new LogAlert(LogAlert.UNREPEATABLE,
					LogAlert.AT_ERROR, "Security.jar.tools_not_found"),
					new String[] { tools_dir.toString() });


			throw( new IOException( "JAR signing fails: tools.jar not found" ));
		}
	}

	protected void
	signJarFile(
		File		input_file )

		throws IOException
	{
		if ( JarSigner_class == null ){

			loadJarSigner();
		}

		PrintStream	old_err	= null;
		PrintStream	old_out	= null;

		String	failure_msg	= null;

		try{
			Object	jar_signer	= JarSigner_class.newInstance();

			String[]	args =
				{	"-keystore",
					keystore_name,
					"-storepass",
					keystore_password,
					input_file.toString(),
					alias };


			old_err = System.err;
			old_out	= System.out;

			ByteArrayOutputStream	baos = new ByteArrayOutputStream();

			PrintStream	ps = new PrintStream(baos);

			System.setErr( ps );
			System.setOut( ps );

			try{
				JarSigner_class.getMethod(
						"run",
						new Class[]{ String[].class }).invoke( jar_signer, new Object[]{ args });

			}catch( Throwable e ){

				ps.close();

				String	err_msg = baos.toString();

				if ( err_msg.length() > 0 ){

					failure_msg	= err_msg;

				}else{

					Debug.printStackTrace(e);

					failure_msg	= e.getMessage();
				}
			}
		}catch( Throwable e ){

			Debug.printStackTrace(e);

			failure_msg	= e.getMessage();

		}finally{

			if ( old_err != null ){

				System.setErr( old_err );
				System.setOut( old_out );
			}
		}

		if ( failure_msg != null ){

			Debug.out( "JAR signing fails '" + failure_msg + "'" );

			Logger.logTextResource(new LogAlert(LogAlert.UNREPEATABLE,
					LogAlert.AT_ERROR, "Security.jar.signfail"),
					new String[] { failure_msg });

			throw( new IOException( "JAR signing fails: " + failure_msg ));
		}
	}

	public void
	signJarFile(
		File			file,
		OutputStream	os )

		throws 	IOException
	{
		signJarFile( file );

		FileInputStream	fis = null;

		try{
			fis = new FileInputStream( file );

			FileUtil.copyFile( file, os, false );

		}finally{

			try{
				if (fis != null) {fis.close();}

			}catch( Throwable e ){

				Debug.printStackTrace(e);
			}
		}
	}

	public void
	signJarStream(
		InputStream		is,
		OutputStream	os )

		throws 	IOException
	{
		File	temp_file = AETemporaryFileHandler.createTempFile();

		FileOutputStream	fos = null;

		try{

			byte[]	buffer = new byte[8192];

			fos = new FileOutputStream( temp_file );

			while(true){

				int	len = is.read( buffer );

				if ( len <= 0 ){

					break;
				}

				fos.write( buffer, 0, len );
			}

			fos.close();

			fos	= null;

			signJarFile( temp_file, os );

		}finally{

			try{
				is.close();

			}catch( Throwable e ){

				Debug.printStackTrace( e );
			}

			if ( fos != null ){

				try{
					fos.close();

				}catch( Throwable e ){

					Debug.printStackTrace( e );
				}
			}
			temp_file.delete();

		}
	}
}
