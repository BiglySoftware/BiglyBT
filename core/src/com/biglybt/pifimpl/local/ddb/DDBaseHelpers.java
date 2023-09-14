/*
 * Created on 21-Feb-2005
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

package com.biglybt.pifimpl.local.ddb;

import java.io.*;
import java.util.*;

import com.biglybt.core.util.Debug;
import com.biglybt.core.util.HashWrapper;
import com.biglybt.core.util.SHA1Simple;
import com.biglybt.pif.ddb.DistributedDatabaseException;
import com.biglybt.pif.ddb.DistributedDatabaseTransferType;

/**
 * @author parg
 *
 */

public class
DDBaseHelpers
{
	protected static byte[]
	encode(
		Object	obj )

		throws DistributedDatabaseException
	{
		byte[]	res;

		if ( obj == null ){

			throw( new DistributedDatabaseException( "null not supported" ));

		}else if ( obj instanceof byte[] ){

			res = (byte[])obj;

		}else if ( obj instanceof String ){

			try{
				res = ((String)obj).getBytes("UTF-8");

			}catch( UnsupportedEncodingException e ){

				throw( new DistributedDatabaseException( "charset error", e ));
			}
		}else if (	obj instanceof Byte ||
					obj instanceof Short ||
					obj instanceof Integer ||
					obj instanceof Long ||
					obj instanceof Float ||
					obj instanceof Double ||
					obj instanceof Boolean ){

			throw( new DistributedDatabaseException( "not supported yet!" ));

		}else{

			try{
				ByteArrayOutputStream	baos = new ByteArrayOutputStream();

				ObjectOutputStream	oos = new ObjectOutputStream( baos );

				oos.writeObject( obj );

				oos.close();

				res = baos.toByteArray();

			}catch( Throwable e ){

				throw( new DistributedDatabaseException( "encoding fails", e ));
			}
		}

		return( res );
	}

	protected static Object
	decode(
		Class	target,
		byte[]	data )

		throws DistributedDatabaseException
	{
		if ( target == byte[].class ){

			return( data );

		}else if ( target == String.class ){

			try{

				return( new String( data, "UTF-8" ));

			}catch( UnsupportedEncodingException e ){

				throw( new DistributedDatabaseException( "charset error", e ));
			}
		}else{

			try{
				ObjectInputStream	iis = new ObjectInputStream( new ByteArrayInputStream( data ));

				Object	res = iis.readObject();

				if ( target.isInstance( res )){

					return( res );

				}else{

					throw( new DistributedDatabaseException( "decoding fails, incompatible type" ));
				}

			}catch( DistributedDatabaseException e ){

				throw( e );

			}catch( Throwable e ){

				throw( new DistributedDatabaseException( "decoding fails", e ));
			}
		}
	}

	private static final Map<String,String>	xfer_migration = new HashMap<>();

	static{
		for ( String[] entry:
			new String[][]{
				{ "com.biglybt.pifimpl.local.ddb.DDBaseTTTorrent", "org.gudy.azureus2.pluginsimpl.local.ddb.DDBaseTTTorrent" },
				{ "com.biglybt.plugin.net.netstatus.NetStatusProtocolTester$testXferType", "com.aelitis.azureus.plugins.net.netstatus.NetStatusProtocolTester$testXferType" },
				{ "com.biglybt.core.content.RelatedContentManager$RCMSearchXFer", "com.aelitis.azureus.core.content.RelatedContentManager$RCMSearchXFer" },
				{ "org.parg.azureus.plugins.networks.i2p.vuzedht.I2PHelperDHTBridge", "org.parg.azureus.plugins.networks.i2p.vuzedht.I2PHelperDHTBridge" },
				
				{ "com.biglybt.core.content.RelatedContentManager$RCMSearchXFerBiglyBT", "com.biglybt.core.content.RelatedContentManager$RCMSearchXFerBiglyBT" },
			}){

			xfer_migration.put( entry[0],  entry[1] );
		}
	}

	protected static HashWrapper
	getKey(
		DistributedDatabaseTransferType		transfer_type )

		throws DistributedDatabaseException
	{
		Class<?> c = transfer_type.getClass();

		String	new_name = c.getName();

		String	old_name = xfer_migration.get( new_name );

		if ( old_name == null ){

			Debug.out( "Missing xfer name map entry for '" + new_name + "'" );

			old_name = new_name;
		}

		if ( old_name == null ){

			throw( new DistributedDatabaseException( "name doesn't exist for '" + c.getName() + "'" ));
		}

		return( new HashWrapper(new SHA1Simple().calculateHash(old_name.getBytes())));
	}
}
