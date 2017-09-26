/*
 * Created on Oct 21, 2010
 *
 * Copyright (C) Azureus Software, Inc, All Rights Reserved.
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

package com.biglybt.ui.swt.views.skin;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.biglybt.ui.swt.mdi.BaseMdiEntry;
import com.biglybt.ui.swt.mdi.MultipleDocumentInterfaceSWT;

/**
 * Transfers Sidebar aka "My Torrents" aka "Files"
 * @author TuxPaper
 * @created Oct 21, 2010
 *
 */
public class SB_Dashboard
{
	// main library
/*
map.put( "mdi", "sidebar" );
map.put( "skin_id", "com.biglybt.ui.skin.skin3" );
map.put( "parent_id", "header.transfers" );
map.put( "skin_ref", "library" );
map.put( "id", "Library" );
map.put( "control_type", 0 );
*/
	// tag
/*
map.put( "mdi", "sidebar" );
map.put( "skin_id", "com.biglybt.ui.skin.skin3" );
map.put( "parent_id", "header.transfers" );
map.put( "skin_ref", "library" );
map.put( "id", "Tag.3.2" );


Map ds_map = new HashMap();
ds_map.put( "exporter", "com.biglybt.core.tag.impl.TagManagerImpl" );
Map export_map = new HashMap();
export_map.put( "uid", new Long( 12884901890L ));
ds_map.put("export", export_map );

map.put( "data_source", ds_map );
map.put( "control_type", 0 );
*/



// {event_listener={name=com.biglybt.ui.swt.subscriptions.SubscriptionView}, skin_id=com.biglybt.ui.skin.skin3, parent_id=Subscriptions, skin_ref=null, id=Subscription_04C72453A8202FF2CDCF474BC8DFE49392330BC623362827F87EE20AA9B53ECA42D1512AAB4DF7089A66C488BBB5C3290C, data_source=data_source={exporter=com.biglybt.core.subs.impl.SubscriptionManagerImpl, export={id=BHBFNORGGHOPQS2Y}}, control_type=0}
/*
map.put( "mdi", "sidebar" );
map.put( "skin_id", "com.biglybt.ui.skin.skin3" );
map.put( "parent_id", "Subscriptions" );

map.put( "id", "Subscription_04C72453A8202FF2CDCF474BC8DFE49392330BC623362827F87EE20AA9B53ECA42D1512AAB4DF7089A66C488BBB5C3290C" );
map.put( "control_type", 0 );

Map ds_map = new HashMap();
ds_map.put( "exporter", "com.biglybt.core.subs.impl.SubscriptionManagerImpl" );
Map export_map = new HashMap();
export_map.put( "id", "BHBFNORGGHOPQS2Y");
ds_map.put("export", export_map );

map.put( "data_source", ds_map );

Map el_map = new HashMap();
el_map.put( "name", "com.biglybt.ui.swt.subscriptions.SubscriptionView" );

map.put( "event_listener", el_map );
*/


// {event_listener={name=com.biglybt.ui.swt.views.PeersGraphicView}, mdi=tabbed, skin_id=com.biglybt.ui.skin.skin3, parent_id=null, skin_ref=null, id=PeersGraphicView, data_source={exports=[{exporter=com.biglybt.core.global.GlobalManager, export={id=5OKT3IHDIAZMSRV5RYW2SOMVEYCNWDDZ}}]}, control_type=0}
/*
map.put( "mdi", "tabbed" );
map.put( "skin_id", "com.biglybt.ui.skin.skin3" );
map.put( "id", "PeersGraphicView" );
map.put( "control_type", 0 );

Map dss_map = new HashMap();
List dss_list = new ArrayList();
dss_map.put( "exports", dss_list );
Map ds_map = new HashMap();
dss_list.add( ds_map );

ds_map.put( "exporter", "com.biglybt.core.global.impl.GlobalManagerImpl" );
Map export_map = new HashMap();
export_map.put( "id", "5OKT3IHDIAZMSRV5RYW2SOMVEYCNWDDZ");
ds_map.put("export", export_map );

map.put( "data_source", dss_map );

Map el_map = new HashMap();
el_map.put( "name", "com.biglybt.ui.swt.views.PeersGraphicView" );

map.put( "event_listener", el_map );
*/
	
	private Map<String,Object>		current;

	public 
	SB_Dashboard(
		final MultipleDocumentInterfaceSWT mdi) 
	{
		

		

	}

	public void
	addItem(
		BaseMdiEntry		entry )
	{
		Map<String,Object> map = entry.exportStandAlone();
		
		System.out.println( "dbi: " + map );
		
		current = map;
	}

	public Map<String,Object>
	getCurrent()
	{
		return( current );
	}
	
	public void
	dispose()
	{
		
	}

}
