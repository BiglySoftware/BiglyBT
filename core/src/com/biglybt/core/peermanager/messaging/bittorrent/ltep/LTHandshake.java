package com.biglybt.core.peermanager.messaging.bittorrent.ltep;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

import com.biglybt.core.peermanager.messaging.Message;
import com.biglybt.core.peermanager.messaging.MessageException;
import com.biglybt.core.peermanager.messaging.MessagingUtil;
import com.biglybt.core.util.*;

/*
 * Copyright (C) Azureus Software, Inc, All Rights Reserved.
 *
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU General Public License
 * as published by the Free Software Foundation; either version 2
 * of the License, or (at your option) any later version.
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 * You should have received a copy of the GNU General Public License
 * along with this program; if not, write to the Free Software
 * Foundation, Inc., 59 Temple Place - Suite 330, Boston, MA 02111-1307, USA.
 *
 */

public class LTHandshake implements LTMessage {

	private Map data_dict;
	private byte[] bencoded_data;
	private String bencoded_string;
	private String description;
    private final byte version;
    private DirectByteBuffer[] buffer_array;

    public LTHandshake(Map data_dict, byte version) {
    	this.data_dict = (data_dict == null) ? Collections.EMPTY_MAP : data_dict;
    	this.version = version;
    }

	@Override
	public Message deserialize(DirectByteBuffer data, byte version) throws MessageException {
		if (data == null) {
			throw new MessageException( "[" +getID() + "] decode error: data == null");
		}
		if (data.remaining(DirectByteBuffer.SS_MSG ) < 1) {
			throw new MessageException( "[" +getID() + "] decode error: less than 1 byte in payload");
		}

		// Try decoding the data now.
		Map res_data_dict = MessagingUtil.convertBencodedByteStreamToPayload(data, 1, getID());

		LTHandshake result = new LTHandshake(res_data_dict, this.version);
		return result;
	}

	@Override
	public DirectByteBuffer[] getData() {
		if (buffer_array == null) {
			buffer_array = new DirectByteBuffer[1];
			DirectByteBuffer buffer = DirectByteBufferPool.getBuffer(DirectByteBuffer.AL_MSG_LT_HANDSHAKE, getBencodedData().length);
			buffer_array[0] = buffer;

			buffer.put(DirectByteBuffer.SS_MSG, getBencodedData());
			buffer.flip(DirectByteBuffer.SS_MSG);
		}
		return buffer_array;
	}

	@Override
	public void destroy() {
		this.data_dict = null;
		this.bencoded_data = null;
		this.description = null;
		if (buffer_array != null) {
			buffer_array[0].returnToPool();
		}
		this.buffer_array = null;
	}

	@Override
	public String getDescription() {
		if (description == null) {
			description = LTMessage.ID_LT_HANDSHAKE.toUpperCase() + ": " + this.getBencodedString();
		}
		return description;
	}

	public String getBencodedString() {
		if (bencoded_string == null) {
			bencoded_string = new String(this.getBencodedData(), Constants.BYTE_ENCODING_CHARSET);
		}
		return bencoded_string;
	}

	public byte[] getBencodedData() {
		if (this.bencoded_data == null) {
			try {this.bencoded_data = BEncoder.encode(this.data_dict);}
			catch (java.io.IOException ioe) {
				this.bencoded_data = new byte[0];
				Debug.printStackTrace(ioe);
			}
		}
		return this.bencoded_data;
	}

	public Map getDataMap() {
		return this.data_dict;
	}

	public String getClientName() {
		byte[] client_name = (byte[])data_dict.get("v");
		if (client_name == null) {return null;}
		return new String(client_name, Constants.DEFAULT_ENCODING_CHARSET);
	}

	public boolean isUploadOnly() {
			// been seeing a bunch of
			// java.lang.ClassCastException: [B cannot be cast to java.lang.Long
		    // at com.biglybt.core.peermanager.messaging.bittorrent.ltep.LTHandshake.isUploadOnly(LTHandshake.java:108)

		Object ulOnly = data_dict.get("upload_only");

		/* NO, this is totally wrong - the entry in the extensions dict indicates the support of an 'upload_only' extension message
		 * http://forum.utorrent.com/viewtopic.php?id=53911
		 *
		if ( ulOnly == null ){

				// apparently it is actually supposed to be in the extensions dict...

			Map ext_dict = (Map)data_dict.get("m");

			if ( ext_dict != null ){

				ulOnly = ext_dict.get("upload_only");
			}
		}
		*/

		if ( ulOnly == null ){
			return( false );
		}else if ( ulOnly instanceof Number ){
			Number n_ulOnly = (Number)ulOnly;
			return n_ulOnly.longValue() > 0L;
		}else{
				// seeing String value '0' here....

			if ( ulOnly instanceof byte[] ){

				String	str_val = new String((byte[])ulOnly );

				try{
					int i = Integer.parseInt( str_val );

					return( i > 0 );

				}catch( Throwable e ){
				}
			}

			String debug;

			if ( ulOnly instanceof byte[] ){

				byte[] bytes = (byte[])ulOnly;

				debug = new String(bytes) + "/";

				for ( int i=0;i<bytes.length;i++){

					debug += (i==0?"":",") + (((int)bytes[i]) & 0x00ff );
				}

			}else{

				debug = String.valueOf( ulOnly );
			}

			Debug.out( "Invalid entry for 'upload_only' - " + debug + ", map=" + data_dict );

			return( false );
		}
	}

	public InetAddress getIPv6() {
		byte[] addr = (byte[])data_dict.get("ipv6");
		if(addr != null && addr.length == 16)
		{
			try
			{
				return InetAddress.getByAddress(addr);
			} catch (UnknownHostException e)
			{
				// should not happen
				e.printStackTrace();
			}
		}

		return null;
	}

	public int getTCPListeningPort()
	{
		Long port = (Long)data_dict.get("p");
		if(port == null)
			return 0;
		int val = port.intValue();
		if(val <= 65535 && val > 0)
			return val;
		return 0;
	}

	public Boolean isCryptoRequested()
	{
		Long crypto = (Long)data_dict.get("e");
		if(crypto == null)
			return null;
		return Boolean.valueOf(crypto.longValue() == 1);
	}

	public Map getExtensionMapping() {
		Map result = (Map)data_dict.get("m");
		return (result == null) ? Collections.EMPTY_MAP : result;
	}

	public int
	getMetadataSize()
	{
		Long l = (Long)data_dict.get( "metadata_size" );

		if ( l != null ){

			return( l.intValue());
		}

		return( 0 );
	}

	public void
	addDefaultExtensionMappings(
		boolean		enable_pex,
		boolean		enable_md,
		boolean		enable_uo,
		boolean		enable_hp )
	{
		if ( enable_pex || enable_md || enable_uo || enable_hp ){
			Map ext = (Map)data_dict.get("m");

			if ( ext == null ){
				ext = new HashMap();
				data_dict.put( "m", ext );
			}

			if ( enable_pex ){

				ext.put( ID_UT_PEX, new Long( SUBID_UT_PEX ));
			}

			if ( enable_md ){

				ext.put( ID_UT_METADATA, new Long( SUBID_UT_METADATA ));
			}

			if ( enable_uo ){

				ext.put( ID_UT_UPLOAD_ONLY, new Long( SUBID_UT_UPLOAD_ONLY ));
			}
			if ( enable_hp ){

				ext.put( ID_UT_HOLEPUNCH, new Long( SUBID_UT_HOLEPUNCH ));
			}
		}
	}

	public void
	addOptionalExtensionMapping(
		String		id,
		long		subid )
	{
		Map ext = (Map)data_dict.get("m");

		if ( ext == null ){
			ext = new HashMap();
			data_dict.put( "m", ext );
		}

		ext.put( id, new Long( subid ));
	}

	@Override
	public String getFeatureID() {return LTMessage.LT_FEATURE_ID;}
	@Override
	public int getFeatureSubID() {return LTMessage.SUBID_LT_HANDSHAKE;}
	@Override
	public String getID() {return LTHandshake.ID_LT_HANDSHAKE;}
	@Override
	public byte[] getIDBytes() {return LTHandshake.ID_LT_HANDSHAKE_BYTES;}
    @Override
    public int getType() {return Message.TYPE_PROTOCOL_PAYLOAD;}
	@Override
	public byte getVersion() {return this.version;}


}
