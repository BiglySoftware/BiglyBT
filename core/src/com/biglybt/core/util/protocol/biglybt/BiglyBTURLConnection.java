/* 
 * Copyright (C) Bigly Software, Inc, All Rights Reserved.
 *
 * This program is free software; you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation; either version 2 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program; if not, write to the Free Software
 * Foundation, Inc., 59 Temple Place, Suite 330, Boston, MA 02111-1307  USA
 */

package com.biglybt.core.util.protocol.biglybt;

/**
 * @author parg
 *
 */

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.biglybt.core.util.Constants;
import com.biglybt.core.vuzefile.VuzeFile;
import com.biglybt.core.vuzefile.VuzeFileComponent;
import com.biglybt.core.vuzefile.VuzeFileHandler;
import com.biglybt.pif.utils.StaticUtilities;
import com.biglybt.pif.utils.resourcedownloader.ResourceDownloader;
import com.biglybt.pif.utils.resourcedownloader.ResourceDownloaderException;
import com.biglybt.pifimpl.update.sf.impl2.SFPluginDetailsLoaderImpl;


class
BiglyBTURLConnection
	extends HttpURLConnection
{
	private static final boolean ONLY_VUZE_FILE = true;
	private final URL		url;

	private int		response_code	= HTTP_OK;
	private String	response_msg	= "OK";

	private InputStream					input_stream;
	private final Map<String,List<String>> 	headers = new HashMap<>();

	BiglyBTURLConnection(
		URL 	u )
	{
		super(u);

		url		= u;
	}

	@Override
	public void
	connect()
		throws IOException
	{
		String str = url.toExternalForm();

		int	pos = str.indexOf( "body=" );

		if (pos >= 0) {

			str = str.substring(pos + 5);

			byte[] bytes = str.getBytes(Constants.BYTE_ENCODING_CHARSET);

			VuzeFile vf = VuzeFileHandler.getSingleton().loadVuzeFile(bytes);

			if (vf == null) {

				throw (new IOException("Invalid biglybt url"));
			}

			input_stream = new ByteArrayInputStream(bytes);
		}  else {
			String host = url.getHost();
			String httpsURL;
			if (host.equalsIgnoreCase("install-plugin") && url.getPath().length() > 1) {
				String plugin_id = url.getPath().substring(1);
				httpsURL = Constants.PLUGINS_WEB_SITE + "getplugin.php?plugin="
						+ plugin_id + "&" + SFPluginDetailsLoaderImpl.getBaseUrlParams();
			} else if (host.contains(".")) {
				httpsURL = "https" + str.substring("biglybt".length());
			} else {
				throw (new IOException("Invalid biglybt url"));
			}

			ResourceDownloader rd = StaticUtilities.getResourceDownloaderFactory().create(new URL(httpsURL));
			try {
				if (ONLY_VUZE_FILE) {

					VuzeFile vf = VuzeFileHandler.getSingleton().loadVuzeFile(rd.download());

					if (vf == null) {

						throw (new IOException("Invalid biglybt url"));
					}

					boolean hasPlugin = false;
					VuzeFileComponent[] components = vf.getComponents();
					for (VuzeFileComponent component : components) {
						if (component.getType() == VuzeFileComponent.COMP_TYPE_PLUGIN) {
							hasPlugin = true;
							break;
						}
					}

					if (!hasPlugin) {
						throw (new IOException("Biglybt url does not contain plugin"));
					}

					input_stream = new ByteArrayInputStream(vf.exportToBytes());
				} else {
					input_stream = rd.download();
				}
			} catch (ResourceDownloaderException e) {
				throw new IOException(e);
			}
		}

	}

   @Override
   public Map<String,List<String>>
    getHeaderFields()
    {
        return( headers );
    }

    @Override
    public String
    getHeaderField(
    	String	name )
    {
    	List<String> values = headers.get( name );

    	if ( values == null || values.size() == 0 ){

    		return( null );
    	}

    	return( values.get( values.size()-1 ));
    }

    public void
    setHeaderField(
    	String	name,
    	String	value )
    {
       	List<String> values = headers.get( name );

       	if ( values == null ){

       		values = new ArrayList<>();

       		headers.put( name, values );
       	}

       	values.add( value );
    }

	@Override
	public InputStream
	getInputStream()

		throws IOException
	{
		if ( input_stream == null ){

			connect();
		}

		return( input_stream );
	}

	public void
	setResponse(
		int		_code,
		String	_msg )
	{
		response_code		= _code;
		response_msg		= _msg;
	}

	@Override
	public int
	getResponseCode()
	{
		return( response_code );
	}

	@Override
	public String
	getResponseMessage()
	{
		return( response_msg );
	}

	@Override
	public boolean
	usingProxy()
	{
		return( false );
	}

	@Override
	public void
	disconnect()
	{
	}
}
