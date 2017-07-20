/*
 * File    : WUJarBuilder.java
 * Created : 10-Feb-2004
 * By      : parg
 *
 * This program is free software; you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation; either version 2 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details ( see the LICENSE file ).
 *
 * You should have received a copy of the GNU General Public License
 * along with this program; if not, write to the Free Software
 * Foundation, Inc., 59 Temple Place, Suite 330, Boston, MA  02111-1307  USA
 */

package com.biglybt.core.util.jar;

/**
 * @author parg
 *
 */

import java.io.*;
import java.net.URI;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.jar.JarEntry;
import java.util.jar.JarOutputStream;

import com.biglybt.core.logging.LogAlert;
import com.biglybt.core.logging.Logger;
import com.biglybt.core.security.SEKeyDetails;
import com.biglybt.core.security.SESecurityManager;
import com.biglybt.core.util.Debug;
import com.biglybt.core.util.FileUtil;
import com.biglybt.core.util.SystemTime;

public class
AEJarBuilder
{
	public static long
	buildFromPackages(
		OutputStream		os,
		ClassLoader			class_loader,
		String[]			package_names,
		Map					package_map,
		String				sign_alias )

			throws IOException
	{
		List	resource_names = new ArrayList();

		for (int i=0;i<package_names.length;i++){

			List	entries = (List)package_map.get(package_names[i]);

			if ( entries == null ){

				Debug.out( "package '" + package_names[i] + "' missing" );

			}else{

				for (int j=0;j<entries.size();j++){

					resource_names.add( package_names[i] + "/" + entries.get(j));
				}
			}
		}

		String[]	res = new String[resource_names.size()];

		resource_names.toArray( res );

		return( buildFromResources2( os, class_loader, null, res, sign_alias ));
	}

	public static void
	buildFromResources(
		OutputStream		os,
		ClassLoader			class_loader,
		String				resource_prefix,
		String[]			resource_names,
		String				sign_alias )

			throws IOException
	{
		buildFromResources2( os, class_loader, resource_prefix, resource_names, sign_alias );
	}

	private static long
	buildFromResources2(
		OutputStream		os,
		ClassLoader			class_loader,
		String				resource_prefix,
		String[]			resource_names,
		String				sign_alias )

		throws IOException
	{
		if ( sign_alias != null ){

			ByteArrayOutputStream	baos = new ByteArrayOutputStream(65536);

			long tim = buildFromResourcesSupport( new JarOutputStream( baos ),class_loader,resource_prefix,resource_names );

			try{
					// leave this check in here as we might as well check for the alias

				SEKeyDetails	kd = SESecurityManager.getKeyDetails( sign_alias );

				if ( kd == null ){
					Logger.log(new LogAlert(LogAlert.UNREPEATABLE, LogAlert.AT_ERROR,
							"Certificate alias '" + sign_alias
									+ "' not found, jar signing fails"));

					throw( new Exception( "Certificate alias '" + sign_alias + "' not found "));
				}

				// WUJarSigner signer = new WUJarSigner(sign_alias, (PrivateKey)kd.getKey(), kd.getCertificateChain());

				AEJarSigner2 signer =
					new AEJarSigner2(
							sign_alias,
							SESecurityManager.getKeystoreName(),
							SESecurityManager.getKeystorePassword());

				signer.signJarStream( new ByteArrayInputStream(baos.toByteArray()), os );

				return( tim );

			}catch( Throwable e ){

				Debug.printStackTrace( e );

				throw( new IOException( e.getMessage()));
			}

		}else{

			JarOutputStream	jos;

			if ( os instanceof JarOutputStream ){

				jos	= (JarOutputStream)os;

			}else{

				jos = new JarOutputStream( os );
			}

			return( buildFromResourcesSupport( jos,class_loader,resource_prefix,resource_names ));
		}
	}

	public static long
	buildFromResourcesSupport(
		JarOutputStream		jos,
		ClassLoader			class_loader,
		String				resource_prefix,
		String[]			resource_names )

		throws IOException
	{
		long	latest_time	= 0;
		long	now			= SystemTime.getCurrentTime();

		for (int i=0;i<resource_names.length;i++){

			String	resource_name = resource_names[i];

			if ( resource_prefix != null ){

				resource_name = resource_prefix + "/" + resource_name;
			}

			InputStream	is = null;

			try{
				is	= class_loader.getResourceAsStream(resource_name);

				if ( is == null ){

					Debug.out( "WUJarBuilder: failed to find resource '" + resource_name + "'");

				}else{

					URL	url = class_loader.getResource( resource_name );

					try{
						File	file = null;

						if ( url != null ){

							String	url_str = url.toString();

							if ( url_str.startsWith("jar:file:" )){

								file	= FileUtil.getJarFileFromURL( url_str );

							}else if ( url_str.startsWith( "file:")){

								file	= new File( URI.create( url_str ));
							}
						}

						if ( file == null ){

							latest_time	= now;

						}else{

							long	time = file.lastModified();

							if ( time > latest_time ){

								latest_time	= time;
							}
						}
					}catch( Throwable e ){

						Debug.printStackTrace( e );
					}

					JarEntry entry = new JarEntry(resource_name);

					writeEntry( jos, entry, is );
				}
			}finally{
				if ( is != null ){

					is.close();
				}
			}
		}

		JarEntry entry = new JarEntry("META-INF/MANIFEST.MF");

		String manifest_lines =
				"Manifest-Version: 1.0\r\n" +
				"Permissions: all-permissions\r\n" +
				"\r\n";

		ByteArrayInputStream bais = new ByteArrayInputStream( manifest_lines.getBytes( "ISO-8859-1" ));

		writeEntry( jos, entry, bais );

		jos.flush();

		jos.finish();

		return( latest_time );
	}



	private static void
	writeEntry(
		JarOutputStream 	jos,
		JarEntry 			entry,
		InputStream 		data )

		throws IOException
	{
		jos.putNextEntry(entry);

		byte[]	newBytes = new byte[4096];

		int size = data.read(newBytes);

		while (size != -1){

			jos.write(newBytes, 0, size);

			size = data.read(newBytes);
		}
	}
}

