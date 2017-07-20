/*
 * Created on 25-May-2004
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

package com.biglybt.update;

/**
 * @author parg
 *
 */

import java.io.*;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.jar.JarEntry;
import java.util.jar.JarInputStream;
import java.util.jar.JarOutputStream;

import com.biglybt.pif.logging.LoggerChannel;

public class
UpdateJarPatcher
{
	private static String MANIFEST_NAME = "META-INF/MANIFEST.MF";

	protected Map		patch_entries	= new HashMap();

	protected
	UpdateJarPatcher(
		InputStream		input_file,
		InputStream		patch_file,
		OutputStream	output_file,
		LoggerChannel	log )

		throws IOException
	{
		readPatchEntries( patch_file );

		JarInputStream	jis = new JarInputStream(input_file);

		JarOutputStream	jos = new JarOutputStream(output_file);

		boolean 	manifest_found	= false;

		while( true ){

			JarEntry is_entry = jis.getNextJarEntry();

			if ( is_entry == null ){

				break;
			}

			if ( is_entry.isDirectory()){

				continue;
			}

			String	name = is_entry.getName();

			if ( name.equalsIgnoreCase( MANIFEST_NAME )){

				manifest_found	= true;
			}

			InputStream	eis = getPatch( name);

			if ( eis != null ){

				log.log( "patch - replace: " + name );

			}else{

				eis = jis;
			}

			JarEntry os_entry = new JarEntry(name);

			writeEntry( jos, os_entry, eis );
		}

			// write any new entries

		Iterator	it = patch_entries.keySet().iterator();

		while( it.hasNext()){

			String	name = (String)it.next();

			if ( name.equalsIgnoreCase( MANIFEST_NAME )){

				manifest_found = true;
			}

			log.log( "patch - add: " + name);

			InputStream	eis = (InputStream)patch_entries.get(name);

			JarEntry os_entry = new JarEntry(name);

			writeEntry( jos, os_entry, eis );
		}

		if ( !manifest_found ){

			JarEntry entry = new JarEntry( MANIFEST_NAME );

			ByteArrayInputStream bais = new ByteArrayInputStream("Manifest-Version: 1.0\r\n\r\n".getBytes());

			writeEntry( jos, entry, bais );
		}

		jos.finish();	// FLUSH is not sufficient!!!!
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

	protected void
	readPatchEntries(
		InputStream		is )

		throws IOException
	{
		JarInputStream	jis = new JarInputStream(is );

		while( true ){

			JarEntry ent = jis.getNextJarEntry();

			if ( ent == null ){

				break;
			}

			if ( ent.isDirectory()){

				continue;
			}

			ByteArrayOutputStream	baos = new ByteArrayOutputStream();

			byte[]	buffer = new byte[8192];

			while( true ){

				int	l = jis.read( buffer );

				if ( l <= 0 ){

					break;
				}

				baos.write( buffer, 0, l );
			}

			patch_entries.put( ent.getName(), new ByteArrayInputStream( baos.toByteArray()));
		}
	}


	public InputStream
	getPatch(
		String	name )
	{
		return((InputStream)patch_entries.remove(name));
	}
}
