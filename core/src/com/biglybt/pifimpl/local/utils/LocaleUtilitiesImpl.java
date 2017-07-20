/*
 * File    : LocaleUtilitiesImpl.java
 * Created : 30-Mar-2004
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

package com.biglybt.pifimpl.local.utils;

/**
 * @author parg
 *
 */

import java.io.IOException;
import java.io.PipedInputStream;
import java.io.PipedOutputStream;
import java.util.*;

import com.biglybt.core.config.COConfigurationManager;
import com.biglybt.core.config.ParameterListener;
import com.biglybt.core.internat.LocaleUtil;
import com.biglybt.core.internat.LocaleUtilDecoder;
import com.biglybt.core.internat.MessageText;
import com.biglybt.core.util.Debug;
import com.biglybt.pif.PluginInterface;
import com.biglybt.pif.utils.LocaleDecoder;
import com.biglybt.pif.utils.LocaleListener;
import com.biglybt.pif.utils.LocaleUtilities;


public class
LocaleUtilitiesImpl
	implements LocaleUtilities
{
	private PluginInterface	pi;

	private List			listeners;

	public
	LocaleUtilitiesImpl(
		PluginInterface _pi )
	{
		pi	 = _pi;
	}

	@Override
	public void
	integrateLocalisedMessageBundle(
		String		resource_bundle_prefix )
	{
		MessageText.integratePluginMessages(resource_bundle_prefix,pi.getPluginClassLoader());
	}

	@Override
	public void integrateLocalisedMessageBundle(ResourceBundle rb) {
		MessageText.integratePluginMessages(rb);
	}

	@Override
	public void integrateLocalisedMessageBundle(Properties p) {
		// Surely there's a more convenient way of doing this?
		ResourceBundle rb = null;
		try {
			PipedInputStream in_stream = new PipedInputStream();
			PipedOutputStream out_stream = new PipedOutputStream(in_stream);
			p.store(out_stream, "");
			out_stream.close();
			rb = new PropertyResourceBundle(in_stream);
			in_stream.close();
		}
		catch (IOException ioe) {return;}
		integrateLocalisedMessageBundle(rb);
	}

	@Override
	public String
	getLocalisedMessageText(
		String		key )
	{
		return( MessageText.getString( key ));
	}

	@Override
	public String
	getLocalisedMessageText(
		String		key,
		String[]	params )
	{
		return( MessageText.getString( key, params ));
	}

	@Override
	public boolean hasLocalisedMessageText(String key) {
		return MessageText.keyExists(key);
	}

	@Override
	public String localise(String key) {
		String res = MessageText.getString(key);
		if (res.charAt(0) == '!' && !MessageText.keyExists(key)) {
			return null;
		}
		return res;
	}

	@Override
	public Locale getCurrentLocale() {
		return MessageText.getCurrentLocale();
	}

	@Override
	public LocaleDecoder[]
	getDecoders()
	{
		LocaleUtilDecoder[]	decs = LocaleUtil.getSingleton().getDecoders();

		LocaleDecoder[]	res = new LocaleDecoder[decs.length];

		for (int i=0;i<res.length;i++){

			res[i] = new LocaleDecoderImpl( decs[i] );
		}

		return( res );
	}

	@Override
	public void
	addListener(
		LocaleListener		l )
	{
		if ( listeners == null ){

			listeners	= new ArrayList();

			COConfigurationManager.addParameterListener(
				"locale.set.complete.count",
				new ParameterListener()
				{
					@Override
					public void
					parameterChanged(
						String parameterName )
					{
						for (int i=0;i<listeners.size();i++){

							try{
								((LocaleListener)listeners.get(i)).localeChanged( MessageText.getCurrentLocale());

							}catch( Throwable e ){

								Debug.printStackTrace(e);
							}
						}
					}
				});
		}

		listeners.add( l );
	}

	@Override
	public void
	removeListener(
		LocaleListener		l )
	{
		if ( listeners == null ){

			return;
		}

		listeners.remove(l);
	}
}
