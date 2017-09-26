/* *
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

package com.biglybt.ui.swt.views.skin;

import java.util.*;

import org.eclipse.swt.layout.*;
import org.eclipse.swt.widgets.*;

import com.biglybt.ui.common.updater.UIUpdatable;
import com.biglybt.ui.swt.Utils;
import com.biglybt.ui.swt.skin.*;
import com.biglybt.ui.swt.views.skin.sidebar.SideBarEntrySWT;

/**
 * @author TuxPaper
 */
public class SBC_DashboardView
	extends SkinView
	implements UIUpdatable
{

	private static final String UI_NAME = "Dashboard";

	private Composite dashboard_composite;
	
	@Override
	public void updateUI() {
	}

	@Override
	public String getUpdateUIName() {
		return UI_NAME;
	}

	@Override
	public Object skinObjectInitialShow(SWTSkinObject skinObject, Object params) {

		SWTSkinObject so_area = getSkinObject("dashboard-area");

		dashboard_composite = (Composite)so_area.getControl();
		
		dashboard_composite.setLayout( new FormLayout());
		
		return( null );
	}


	@Override
	public Object skinObjectHidden(SWTSkinObject skinObject, Object params) {

		Utils.disposeComposite( dashboard_composite, false );
		
		return super.skinObjectHidden(skinObject, params);
	}

	@Override
	public Object 
	skinObjectShown(
		SWTSkinObject 	skinObject, 
		Object 			params ) 
	{	
		Object result = super.skinObjectShown(skinObject, params);
		
		Utils.disposeComposite( dashboard_composite, false );
		
		Map<String,Object>	map = new HashMap<>();
		
			// main library
		/*
		map.put( "skin_id", "com.biglybt.ui.skin.skin3" );
		map.put( "parent_id", "header.transfers" );
		map.put( "skin_ref", "library" );
		map.put( "id", "Library" );
		map.put( "control_type", 0 );
		*/
			// tag
		/*
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
		
		SkinnedComposite skinned_cimp =	new SkinnedComposite( dashboard_composite );
		
		SWTSkin skin = skinned_cimp.getSkin();
		
		SideBarEntrySWT.importStandAlone((SWTSkinObjectContainer)skin.getSkinObject( "content-area" ), map);
			
		Control c = ((SWTSkinObjectContainer)skin.getSkinObject( "content-area" )).getControl();
		
		c.setLayoutData( Utils.getFilledFormData());
		
		dashboard_composite.getParent().layout( true, true );
		
		return( result );
	}

	@Override
	public Object 
	skinObjectDestroyed(
		SWTSkinObject 	skinObject, 
		Object 			params ) 
	{
		return super.skinObjectDestroyed(skinObject, params);
	}
}
