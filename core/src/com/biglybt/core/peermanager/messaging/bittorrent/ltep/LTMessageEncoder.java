/*
 * Created on 17 Sep 2007
 * Created by Allan Crooks
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
package com.biglybt.core.peermanager.messaging.bittorrent.ltep;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

import com.biglybt.core.logging.LogEvent;
import com.biglybt.core.logging.LogIDs;
import com.biglybt.core.logging.Logger;
import com.biglybt.core.networkmanager.RawMessage;
import com.biglybt.core.peermanager.messaging.Message;
import com.biglybt.core.peermanager.messaging.MessageStreamEncoder;
import com.biglybt.core.peermanager.messaging.bittorrent.BTLTMessage;
import com.biglybt.core.peermanager.messaging.bittorrent.BTMessageFactory;
import com.biglybt.core.util.Constants;
import com.biglybt.core.util.Debug;

/**
 * @author Allan Crooks
 *
 */
public class LTMessageEncoder implements MessageStreamEncoder {

	protected final static LogIDs LOGID = LogIDs.PEER;
	private final Object log_object;
	private HashMap extension_map;

	public LTMessageEncoder(Object log_object) {
		this.log_object = log_object;
		this.extension_map = null; // Only instantiate it when we need to.
	}

	@Override
	public RawMessage[] encodeMessage(Message message) {
		if (!(message instanceof LTMessage)) {
			return new RawMessage[] {BTMessageFactory.createBTRawMessage(message)};
		}

		// What type of message is it? LT_handshake messages are always straight forward.
		if (message instanceof LTHandshake) {
			return new RawMessage[] {BTMessageFactory.createBTRawMessage(new BTLTMessage(message, (byte)0))};
		}

		// Other message types have to be matched up against the appropriate ID.
		if (extension_map != null) {
			Byte ext_id = (Byte)this.extension_map.get(message.getID());
			if (ext_id != null) {
				//Logger.log(new LogEvent(this.log_object, LOGID,	"Converting LT message to BT message, ext id is " + ext_id));
				return new RawMessage[] {BTMessageFactory.createBTRawMessage(new BTLTMessage(message, ext_id.byteValue()))};
			}
		}

		// Anything else means that the client doesn't support that extension.
		// We'll drop the message instead.
		if (Logger.isEnabled())
			Logger.log(new LogEvent(this.log_object, LOGID,	"Unable to send LT message of type " + message.getID() + ", not supported by peer - dropping message."));

		return new RawMessage[0];
	}

	public void updateSupportedExtensions(Map map) {
		try {
			Iterator itr = map.entrySet().iterator();
			while (itr.hasNext()) {
				Map.Entry extension = (Map.Entry)itr.next();
				String ext_name;
				Object ext_key = extension.getKey();
				if (ext_key instanceof byte[]) {
					ext_name = new String((byte[])ext_key, Constants.DEFAULT_ENCODING_CHARSET);
				}
				else if (ext_key instanceof String) {
					ext_name = (String)ext_key;
				}
				else {
					throw new RuntimeException("unexpected type for extension name: " + ext_key.getClass());
				}

				int ext_value;
				Object ext_value_obj = extension.getValue();

				if (ext_value_obj instanceof Long) {
					ext_value = ((Long)extension.getValue()).intValue();
				}
				else if (ext_value_obj instanceof byte[]) {
					byte[] ext_value_bytes = (byte[])ext_value_obj;
					if (ext_value_bytes.length == 1) {
						ext_value = (int)ext_value_bytes[0];
					}
					else {
						//throw new RuntimeException("extension id byte array format length != 1: " + ext_value_bytes.length);
						// seeing some of these, just log and ignore for the moment
						
						Debug.outNoStack( "Invalid LT extension: name=" + ext_name + ", value=" + new String(ext_value_bytes));
						
						continue;
					}
				}
				else {
					throw new RuntimeException("unsupported extension id type: " + ext_value_obj.getClass().getName());
				}
				if (extension_map == null) {
					this.extension_map = new HashMap();
				}

				if (ext_value == 0) {this.extension_map.remove(ext_name);}
				else {this.extension_map.put(ext_name, new Byte((byte)ext_value));}
			}
		}
		catch (Exception e) {
			if (Logger.isEnabled())
				Logger.log(new LogEvent(this.log_object, LOGID, "Unable to update LT extension list for peer", e));
		}
	}

	public boolean supportsUTPEX() {
		return( supportsExtension( LTMessage.ID_UT_PEX ));
	}

	public boolean supportsUTMetaData() {
		return( supportsExtension( LTMessage.ID_UT_METADATA ));
	}
	
	public boolean supportsUTHolePunch() {
		return( supportsExtension( LTMessage.ID_UT_HOLEPUNCH ));
	}

	public boolean
	supportsExtension(
		String		extension_name )
	{
		if (extension_map == null) {return false;}

		Number num = (Number)this.extension_map.get( extension_name );

		return( num != null && num.intValue() != 0 );
	}

	public static final int	CET_PEX	= 1;

	private Map<Integer,CustomExtensionHandler>		custom_handlers;

	public void
	addCustomExtensionHandler(
		int							extension_type,
		CustomExtensionHandler		handler )
	{
		if ( custom_handlers == null ){

			custom_handlers = new HashMap<>();
		}

		custom_handlers.put( extension_type, handler );
	}

	public boolean
	hasCustomExtensionHandler(
		int		extension_type )
	{
		if ( custom_handlers == null ){

			return( false );
		}

		return( custom_handlers.containsKey( extension_type ));
	}

	public Object
	handleCustomExtension(
		int			extension_type,
		Object[]	args )
	{
		if ( custom_handlers == null ){

			return( null );
		}

		CustomExtensionHandler handler = custom_handlers.get( extension_type );

		if ( handler != null ){

			return( handler.handleExtension(args));
		}

		return( null );
	}

	public interface
	CustomExtensionHandler
	{
		public Object
		handleExtension(
			Object[]	args );
	}
}
