/*
 * Created on Oct 14, 2016
 * Created by Paul Gardner
 *
 * Copyright 2016 Azureus Software, Inc.  All rights reserved.
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
 * Foundation, Inc., 59 Temple Place, Suite 330, Boston, MA 02111-1307, USA.
 */


package com.biglybt.activities;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import com.biglybt.core.util.SystemTime;

public class
LocalActivityManager
{
	static List<ActivitiesEntry>	pending = new ArrayList<>(1);

	static{
		ActivitiesManager.addListener(
			new ActivitiesLoadedListener()
			{
				@Override
				public void
				vuzeActivitiesLoaded()
				{
					synchronized( LocalActivityManager.class ){

						for ( ActivitiesEntry entry: pending ){

							ActivitiesManager.addEntries(new ActivitiesEntry[] {
									entry
								});
						}

						pending = null;
					}
				}
			});

	}

	public static void
	addLocalActivity(
		String															uid,
		String															icon_id,
		String															name,
		String[]														actions,
		Class<? extends LocalActivityCallback>	callback,
		Map<String,String>												callback_data )
	{
		ActivitiesEntry entry =
			new ActivitiesEntry(
				SystemTime.getCurrentTime(),
				name,
				ActivitiesConstants.TYPEID_LOCALNEWS ) ;

		entry.setID( uid );

		entry.setIconIDRaw( icon_id );

		entry.setActions( actions );

		entry.setCallback( callback,  callback_data );

		synchronized( LocalActivityManager.class ){

			if ( pending != null ){

				pending.add( entry );

			}else{

				ActivitiesManager.addEntries(new ActivitiesEntry[] {
						entry
				});
			}
		}
	}

	public interface
	LocalActivityCallback
	{
		public void
		actionSelected(
				String				action,
				Map<String,String>	data );
	}
}
