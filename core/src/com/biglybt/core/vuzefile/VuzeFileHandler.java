/*
 * Created on May 16, 2008
 * Created by Paul Gardner
 *
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
 */


package com.biglybt.core.vuzefile;

import java.io.*;
import java.net.URI;
import java.net.URL;
import java.util.*;


import com.biglybt.core.util.BDecoder;
import com.biglybt.core.util.Constants;
import com.biglybt.core.util.CopyOnWriteList;
import com.biglybt.core.util.Debug;
import com.biglybt.core.util.FileUtil;
import com.biglybt.pif.utils.StaticUtilities;
import com.biglybt.pif.utils.resourcedownloader.ResourceDownloader;


public class
VuzeFileHandler
{
	private static final VuzeFileHandler singleton = new VuzeFileHandler();

	public static VuzeFileHandler
	getSingleton()
	{
		return( singleton );
	}

	private final CopyOnWriteList<VuzeFileProcessor>	processors = new CopyOnWriteList<>();


	protected
	VuzeFileHandler()
	{
	}

	private static final String[] 		accepted_exts = { "vuze", "vuz", "biglybt", "big" };
	private static final Set<String>	accepted_exts_set = new HashSet<>( Arrays.asList( accepted_exts ));

	private static final String[] 		filter_exts = { "*.biglybt", "*.big", "*.vuze", "*.vuz", Constants.FILE_WILDCARD };

	private static final String 		main_ext = ".biglybt";

	public static boolean
	isAcceptedVuzeFileName(
		String		original_name )
	{
		String name = original_name.toLowerCase( Locale.US );

		int pos = name.lastIndexOf( "." );

		if ( pos >= 0 ){

			name = name.substring(pos+1);
		}

		boolean ok = accepted_exts_set.contains( name );
		
		if ( ok ){
			
				// we do get URLs thrown in here, make sure they're loadable
			
			try{
				File test_file = FileUtil.newFile( original_name );

				test_file = migrateFile( test_file );

				if ( test_file.isFile()){
					
					return( true );
				}
			}catch( Throwable e ){
			}
			
			try{
				URL	url = new URI( original_name ).toURL();
	
				String	protocol = url.getProtocol().toLowerCase();
	
				ok = protocol.equals( "file" ) || protocol.equals( "http" ) || protocol.equals( "https")  || protocol.equals("biglybt");
				
			}catch( Throwable e ){
				
					// not a URL, ok
			}
		}
		
		return( ok );
	}

	public static boolean
	isAcceptedVuzeFileName(
		File		file )
	{
		return( isAcceptedVuzeFileName( file.getName()));
	}

	public static String
	getVuzeFileName(
		String		name )
	{
		if ( isAcceptedVuzeFileName(name)){

				// remove existing acceptable suffix

			int pos = name.lastIndexOf( "." );

			if ( pos >= 0 ){

				name = name.substring(0, pos );
			}
		}

		return( name + getVuzeFileSuffix());
	}

	public static String
	getVuzeFileSuffix()
	{
		return( main_ext );
	}


	public static String[]
	getVuzeFileFilterExtensions()
	{
		return( filter_exts);
	}

	private static File
	migrateFile(
		File	file )
	{
		if ( file.exists()) {

			return( file );
		}

		String name = file.getName();

		int pos = name.lastIndexOf( "." );

		if ( pos >= 0 ){

			String prefix = name.substring(0,pos+1);

			for ( String ext: accepted_exts ){

				String test_name = prefix + ext;

				File test_file = FileUtil.newFile( file.getParentFile(), test_name );

				if ( test_file.exists()) {

					return( test_file );
				}
			}
		}

		return( file );
	}

	public VuzeFile
	loadVuzeFile(
		String	target  )
	{
		try{
			File test_file = FileUtil.newFile( target );

			test_file = migrateFile( test_file );

			if ( test_file.isFile()){

				return( getVuzeFile( FileUtil.newFileInputStream( test_file )));

			}else{

				URL	url = new URI( target ).toURL();

				String	protocol = url.getProtocol().toLowerCase();

				if ( protocol.equals( "http" ) || protocol.equals( "https")  || protocol.equals("biglybt") ){

					ResourceDownloader rd = StaticUtilities.getResourceDownloaderFactory().create( url );

					return( getVuzeFile(rd.download()));
				}
			}
		}catch( Throwable e ){
		}

		return( null );
	}

	public VuzeFile
	loadVuzeFile(
		byte[]		bytes )
	{
		return( loadVuzeFile( new ByteArrayInputStream( bytes )));
	}

	public VuzeFile
	loadVuzeFile(
		InputStream 	is )
	{
		return( getVuzeFile( is ));
	}

	public VuzeFile
	loadVuzeFile(
		File 	file )
	{
		file = migrateFile( file );

		InputStream is = null;

		try{
			is = FileUtil.newFileInputStream( file );

			return( getVuzeFile( is ));

		}catch( Throwable e ){

			return( null );

		}finally{

			if ( is != null ){

				try{
					is.close();

				}catch( Throwable e ){
				}
			}
		}
	}

	protected VuzeFile
	getVuzeFile(
		InputStream		is )
	{
		try{
			BufferedInputStream bis = new BufferedInputStream( is );

			try{
				bis.mark(100);

				boolean is_json = false;

				while( true ){

					int next = bis.read();

					if ( next == -1 ){

						break;
					}

					char c = (char)next;

					if ( !Character.isWhitespace(c)){

						is_json = c == '{';

						break;
					}
				}

				bis.reset();

				Map map;

				if ( is_json ){

					byte[] bytes = FileUtil.readInputStreamAsByteArray( bis, 2*1024*1024 );

					map = BDecoder.decodeFromJSON( new String( bytes, "UTF-8" ));

				}else{

					map = BDecoder.decode(bis);
				}

				return( loadVuzeFile( map ));

			}finally{

				is.close();
			}
		}catch( Throwable e ){
		}

		return( null );
	}

	public VuzeFile
	loadVuzeFile(
		Map	map )
	{
		if ( map.containsKey( "vuze" ) && !map.containsKey( "info" )){

			return( new VuzeFileImpl( this, (Map)map.get( "vuze" )));
		}

		return( null );
	}

	public VuzeFile
	loadAndHandleVuzeFile(
		String		target,
		int			expected_types )
	{
		VuzeFile vf = loadVuzeFile( target );

		if ( vf == null ){

			return( null );
		}

		handleFiles( new VuzeFile[]{ vf }, expected_types );

		return( vf );
	}

	public void
	handleFiles(
		VuzeFile[]		files,
		int				expected_types )
	{
		Iterator<VuzeFileProcessor> it = processors.iterator();

		while( it.hasNext()){

			VuzeFileProcessor	proc = it.next();

			try{
				proc.process( files, expected_types );

			}catch( Throwable e ){

				Debug.printStackTrace(e);
			}
		}

		for (int i=0;i<files.length;i++){

			VuzeFile vf = files[i];

			VuzeFileComponent[] comps = vf.getComponents();

			for (int j=0;j<comps.length;j++){

				VuzeFileComponent comp = comps[j];

				if ( !comp.isProcessed()){

					Debug.out( "Failed to handle Vuze file component " + comp.getContent());
				}
			}
		}
	}

	public VuzeFile
	create()
	{
		return( new VuzeFileImpl( this ));
	}

	public void
	addProcessor(
		VuzeFileProcessor		proc )
	{
		processors.add( proc );
	}
}
