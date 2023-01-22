/*
 * Copyright (C) Azureus Software, Inc, All Rights Reserved.
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

package com.biglybt.ui.swt.subscriptions;

import com.biglybt.core.subs.Subscription;
import com.biglybt.core.subs.SubscriptionListener;
import com.biglybt.core.util.Debug;
import com.biglybt.ui.common.viewtitleinfo.ViewTitleInfo;
import com.biglybt.ui.common.viewtitleinfo.ViewTitleInfoManager;
import com.biglybt.ui.swt.pif.UISWTView;
import com.biglybt.ui.swt.pif.UISWTViewEvent;
import com.biglybt.ui.swt.pifimpl.UISWTViewCore;
import com.biglybt.ui.swt.pifimpl.UISWTViewCoreEventListener;
import com.biglybt.ui.swt.skin.UISWTViewSkinAdapter;


public class
SubscriptionView
	extends UISWTViewSkinAdapter
	implements SubscriptionsViewBase, UISWTViewCoreEventListener
{
	private static final Object	SUBS_KEY = new Object();
	
	public
	SubscriptionView()
	{
		super( 	"com/biglybt/ui/swt/subscriptions",
				"skin3_subs_view.properties",
				"subscriptionresultsviewwrapper",
				"subscriptionresultsview" );
	}

	@Override
	public boolean
	eventOccurred(
		UISWTViewEvent event )
	{
		try{
			UISWTView 	view = event.getView();
	
			if ( view instanceof UISWTViewCore ){
				
				UISWTViewCore viewCore = (UISWTViewCore)view;
				
				switch (event.getType()) {
					case UISWTViewEvent.TYPE_PRE_CREATE:{
		
						Subscription subs = (Subscription)view.getDataSource();
						
						int[] last_unread = { -1 };
						
						ViewTitleInfo titleInfo = 
							new ViewTitleInfo(){
								
								@Override
								public Object 
								getTitleInfoProperty(
									int propertyID )
								{
									if ( propertyID == ViewTitleInfo.TITLE_INDICATOR_TEXT ){
										
										int unread = last_unread[0] = subs.getHistory().getNumUnread();
										
										return( String.valueOf( unread ));
									}
									
									return( null );
								}
							};
						
						SubscriptionListener listener = 
							new SubscriptionListener(){
								
								@Override
								public void 
								subscriptionDownloaded(
									Subscription subs)
								{
								}
								
								@Override
								public void 
								subscriptionChanged(
									Subscription	subs, 
									int				reason)
								{
									if ( subs.getHistory().getNumUnread() != last_unread[0] ){
										
										ViewTitleInfoManager.refreshTitleInfo(titleInfo);
									}
								}
							};
							
						subs.addListener( listener );
						
						viewCore.setViewTitleInfo( titleInfo );

						viewCore.setUserData( SUBS_KEY, new Object[]{ subs, listener });
						
						break;
					}
					case UISWTViewEvent.TYPE_DESTROY:{
						
						Object[] entry = (Object[])viewCore.getUserData( SUBS_KEY );
						
						if ( entry != null ){
							
							((Subscription)entry[0]).removeListener((SubscriptionListener)entry[1] );
						}
						
						break;
					}
				}
			}
		}catch( Throwable e ){
			
			Debug.out( e );
		}
		
		return( super.eventOccurred(event));
	}
	
	@Override
	public void
	refreshView()
	{
	}
}
