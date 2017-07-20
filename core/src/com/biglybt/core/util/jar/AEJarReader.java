/*
 * File    : WUJarReader.java
 * Created : 31-Mar-2004
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

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.util.HashMap;
import java.util.Map;
import java.util.jar.JarEntry;
import java.util.jar.JarInputStream;

public class
AEJarReader
{
	protected final Map		entries	= new HashMap();

	public
	AEJarReader(
		String		name )
	{
		InputStream 	is 	= null;
		JarInputStream 	jis = null;

		try{
			is = getClass().getClassLoader().getResourceAsStream(name);

			jis = new JarInputStream(is );

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

				entries.put( ent.getName(), new ByteArrayInputStream( baos.toByteArray()));
			}

		}catch( Throwable e ){

			e.printStackTrace();

		}finally{

			try{
				if ( jis != null ){

					jis.close();
				}

				if (is != null){

					is.close();
				}
			}catch( Throwable e ){

			}
		}
	}

	public InputStream
	getResource(
		String	name )
	{
		return((InputStream)entries.get(name));
	}
}
