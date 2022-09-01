/*
 * Created on 18-Jan-2006
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

package com.biglybt.core.networkmanager.impl;

import java.util.ArrayList;
import java.util.List;

import com.biglybt.core.logging.LogEvent;
import com.biglybt.core.logging.LogIDs;
import com.biglybt.core.logging.Logger;
import com.biglybt.core.util.*;

public abstract class
ProtocolDecoder
{
	static final LogIDs LOGID = LogIDs.NWMAN;

	private static final int	TIMEOUT_CHECK		= 5000;
	private static final int	LOG_TICKS			= 60000 / TIMEOUT_CHECK;

	static final List<ProtocolDecoder>			decoders	= new ArrayList<>();

	static final AEMonitor	class_mon 	= new AEMonitor( "TCPProtocolDecoder:class" );

	static int	loop = 0;


	static{

		SimpleTimer.addPeriodicEvent(
				"ProtocolDecoder:timeouts",
				5000,
				new TimerEventPerformer()
				{
					@Override
					public void perform(TimerEvent ev )
					{

						loop++;

						List<ProtocolDecoder>	copy;

						try{
							class_mon.enter();

							if ( loop % LOG_TICKS == 0 ){

								if (Logger.isEnabled()){

									if ( decoders.size() > 0 ){

										Logger.log(	new LogEvent(LOGID, "Active protocol decoders = " + decoders.size()));
									}
								}
							}

							copy = new ArrayList<>(decoders);

						}finally{

							class_mon.exit();
						}

						if ( copy.size() > 0 ){

							List<ProtocolDecoder> to_remove = new ArrayList<>();

							long	now = SystemTime.getCurrentTime();

							for ( ProtocolDecoder decoder: copy ){

								if ( decoder.isComplete( now )){

									to_remove.add( decoder );
								}
							}

							if ( to_remove.size() > 0 ){

								try{
									class_mon.enter();

									for ( ProtocolDecoder decoder: to_remove ){

										decoders.remove( decoder );
									}
								}finally{

									class_mon.exit();
								}
							}
						}
					}
				}
		);


	}

	protected
	ProtocolDecoder(
		boolean	run_timer )
	{
		if ( run_timer ){

			try{
				class_mon.enter();

				decoders.add( this );

			}finally{

				class_mon.exit();
			}
		}
	}

	public abstract boolean
	isComplete(
		long	now );

	public abstract TransportHelperFilter
	getFilter();

	public static void
	addSecrets(
		String			name,
		byte[][]		secrets )
	{
		ProtocolDecoderPHE.addSecretsSupport( name, secrets );
	}

	public static void
	removeSecrets(
		byte[][]		secrets )
	{
		ProtocolDecoderPHE.removeSecretsSupport( secrets );
	}
}
