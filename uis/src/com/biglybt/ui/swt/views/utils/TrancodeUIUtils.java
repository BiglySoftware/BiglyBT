/*
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU General Public License
 * as published by the Free Software Foundation; either version 2
 * of the License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program; if not, write to the Free Software
 * Foundation, Inc., 59 Temple Place - Suite 330, Boston, MA  02111-1307, USA.
 */

package com.biglybt.ui.swt.views.utils;

import com.biglybt.core.config.COConfigurationManager;
import com.biglybt.core.devices.Device;
import com.biglybt.core.devices.DeviceManager;
import com.biglybt.core.devices.DeviceManagerFactory;
import com.biglybt.core.devices.DeviceMediaRenderer;
import com.biglybt.core.internat.MessageText;
import com.biglybt.core.util.Debug;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

public class TrancodeUIUtils {
	public interface
	TranscodeTarget
	{
		public String
		getID();

		public String
		getName();

		public TranscodeProfile[]
		getProfiles();
	}

	public interface
	TranscodeProfile
	{
		public String
		getUID();

		public String
		getName();
	}


	public static TranscodeTarget[]
	getTranscodeTargets()
	{
		List<TranscodeTarget> result = new ArrayList<>();

		if ( !COConfigurationManager.getStringParameter("ui").equals("az2")){

			try{
				DeviceManager dm = DeviceManagerFactory.getSingleton();

				Device[] devices = dm.getDevices();

				for ( final Device d: devices ){

					if ( d instanceof DeviceMediaRenderer){

						final DeviceMediaRenderer dmr = (DeviceMediaRenderer)d;

						boolean	hide_device = d.isHidden();

						if ( COConfigurationManager.getBooleanParameter( "device.sidebar.ui.rend.hidegeneric", true ) ){

							if ( dmr.isNonSimple()){

								hide_device = true;
							}
						}

						if ( hide_device ){

							continue;
						}

						result.add(
								new TranscodeTarget()
								{
									@Override
									public String
									getName()
									{
										return( d.getName());
									}

									@Override
									public String
									getID()
									{
										return( d.getID());
									}

									@Override
									public TranscodeProfile[]
									getProfiles()
									{
										List<TranscodeProfile>	ps = new ArrayList<>();

										com.biglybt.core.devices.TranscodeProfile[] profs = dmr.getTranscodeProfiles();

										if ( profs.length == 0 ){

											if ( dmr.getTranscodeRequirement() == com.biglybt.core.devices.TranscodeTarget.TRANSCODE_NEVER ){

												ps.add(
														new TranscodeProfile()
														{
															@Override
															public String
															getUID()
															{
																return( dmr.getID() + "/" + dmr.getBlankProfile().getName());
															}

															@Override
															public String
															getName()
															{
																return( MessageText.getString( "devices.profile.direct" ));
															}
														});												}
										}else{

											for ( final com.biglybt.core.devices.TranscodeProfile prof: profs ){

												ps.add(
														new TranscodeProfile()
														{
															@Override
															public String
															getUID()
															{
																return( prof.getUID());
															}

															@Override
															public String
															getName()
															{
																return( prof.getName());
															}
														});
											}
										}

										return( ps.toArray( new TranscodeProfile[ ps.size()]));
									}
								});
					}
				}

			}catch( Throwable e ){

				Debug.out( e );
			}
		}

		Collections.sort(
				result,
				new Comparator<TranscodeTarget>()
				{
					@Override
					public int
					compare(
							TranscodeTarget o1,
							TranscodeTarget o2)
					{
						return( o1.getName().compareTo( o2.getName()));
					}
				});

		return( result.toArray( new TranscodeTarget[result.size()]));
	}

}
