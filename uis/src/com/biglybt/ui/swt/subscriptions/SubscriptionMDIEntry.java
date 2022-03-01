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



import java.util.List;

import com.biglybt.core.internat.MessageText;
import com.biglybt.core.util.*;
import com.biglybt.pif.PluginInterface;
import com.biglybt.pif.ui.UIInstance;
import com.biglybt.pif.ui.UIManager;
import com.biglybt.pif.ui.menus.*;
import com.biglybt.pifimpl.local.PluginInitializer;
import com.biglybt.ui.mdi.MdiAcceleratorListener;
import com.biglybt.ui.mdi.MdiCloseListener;
import com.biglybt.ui.swt.pif.UISWTViewEventListener;


import com.biglybt.core.subs.*;
import com.biglybt.ui.common.viewtitleinfo.ViewTitleInfo;
import com.biglybt.ui.common.viewtitleinfo.ViewTitleInfoManager;
import com.biglybt.ui.mdi.MdiEntry;
import com.biglybt.ui.mdi.MdiEntryVitalityImage;
import com.biglybt.ui.swt.mdi.BaseMdiEntry;

public class SubscriptionMDIEntry implements SubscriptionListener, ViewTitleInfo
{
	private static final String ALERT_IMAGE_ID	= "image.sidebar.vitality.alert";
	private static final String AUTH_IMAGE_ID	= "image.sidebar.vitality.auth";

	private final MdiEntry mdiEntry;

	MdiEntryVitalityImage spinnerImage;

	private MdiEntryVitalityImage warningImage;
	private final Subscription subs;
	private String current_parent;

	public SubscriptionMDIEntry(Subscription subs, MdiEntry entry) {
		this.subs = subs;
		this.mdiEntry = entry;
		current_parent = subs.getParent();
		if ( current_parent != null && current_parent.length() == 0 ){
			current_parent = null;
		}
		setupMdiEntry();
	}

	private void setupMdiEntry() {
		if (mdiEntry == null) {
			return;
		}

		mdiEntry.setViewTitleInfo(this);

		mdiEntry.setImageLeftID("image.sidebar.subscriptions");

		warningImage = mdiEntry.addVitalityImage( ALERT_IMAGE_ID );

		spinnerImage = mdiEntry.addVitalityImage("image.sidebar.vitality.dots");

		if (spinnerImage != null) {
			spinnerImage.setVisible(false);
		}

		setWarning();

		setupMenus(
			subs,
			new Runnable(){
				@Override
				public void run() {
					SubscriptionMDIEntry.this.refreshView();
				}
			});

		subs.addListener(this);

		mdiEntry.addAcceleratorListener((character,mask)->{
			if (( character == 'c' || character == 'C' ) && mask== MdiAcceleratorListener.SHIFT ){
				subs.getHistory().markAllResultsRead();
			}
		});
		
		mdiEntry.addListener(new MdiCloseListener() {
			@Override
			public void mdiEntryClosed(MdiEntry entry, boolean userClosed) {
				subs.removeListener(SubscriptionMDIEntry.this);
			}
		});
	}

	protected static String
	setupMenus(
		Subscription		subs,
		final Runnable		refresher )
	{
		PluginInterface pi = PluginInitializer.getDefaultInterface();
		UIManager uim = pi.getUIManager();

		final MenuManager menu_manager = uim.getMenuManager();

		final String key = "sidebar.Subscription_" + ByteFormatter.encodeString(subs.getPublicKey());

		SubscriptionManagerUI.MenuCreator menu_creator =
			new SubscriptionManagerUI.MenuCreator()
			{
				@Override
				public MenuItem
				createMenu(
					String 	resource_id )
				{
					List<MenuItem> mis = menu_manager.getMenuItems(key, resource_id);
					
					for ( MenuItem old: mis ){
						
						old.remove();
					}
						
					MenuItem menu_item = menu_manager.addMenuItem(key, resource_id);
						
					menu_item.setDisposeWithUIDetach(UIInstance.UIT_SWT);
						
					return( menu_item );
				}

				@Override
				public void refreshView() {
					if ( refresher != null ){
						refresher.run();
					}
				}
			};

		SubscriptionManagerUI.createMenus( menu_manager, menu_creator, new Subscription[]{ subs });

		return( key );
	}
	protected String
	getCurrentParent()
	{
		return( current_parent );
	}

	protected boolean
	isDisposed()
	{
		return( mdiEntry.isEntryDisposed());
	}

	@Override
	public void subscriptionDownloaded(Subscription subs) {
	}

	@Override
	public void subscriptionChanged(Subscription subs, int reason ) {
		mdiEntry.redraw();
		ViewTitleInfoManager.refreshTitleInfo(mdiEntry.getViewTitleInfo());
	}

	protected void refreshView() {
		if (!(mdiEntry instanceof BaseMdiEntry)) {
			return;
		}
		UISWTViewEventListener eventListener = ((BaseMdiEntry)mdiEntry).getEventListener();
		if (eventListener instanceof SubscriptionView) {
			SubscriptionView subsView = (SubscriptionView) eventListener;
			subsView.refreshView();
		}
	}

	protected void
	setWarning()
	{
			// possible during initialisation, status will be shown again on complete

		if ( warningImage == null ){

			return;
		}

		SubscriptionHistory history = subs.getHistory();

		String	last_error = history.getLastError();

		boolean	auth_fail = history.isAuthFail();

			// don't report problem until its happened a few times, but not for auth fails as this is a perm error

		if ( history.getConsecFails() < 3 && !auth_fail ){

			last_error = null;
		}

		boolean	trouble = last_error != null;

		if ( trouble ){

			warningImage.setToolTip( last_error );

			warningImage.setImageID( auth_fail?AUTH_IMAGE_ID:ALERT_IMAGE_ID );

			warningImage.setVisible( true );

		}else{

			warningImage.setVisible( false );

			warningImage.setToolTip( "" );
		}
	}

	@Override
	public Object
	getTitleInfoProperty(
		int propertyID )
	{
		// This should work, but since we have subs already in class, use that
		//if (mdiEntry == null) {
		//	return null;
		//}
		//Object ds = mdiEntry.getDatasource();
		//if (!(ds instanceof Subscription)) {
		//	return null;
		//}
		//Subscription subs = (Subscription) ds;

		switch( propertyID ){

			case TITLE_TEXT:{

				return( subs.getName());
			}
			case TITLE_INDICATOR_TEXT_TOOLTIP:{

				long	pop = subs.getCachedPopularity();

				String res = subs.getName();

				if ( pop > 1 ){

					res += " (" + MessageText.getString("subscriptions.listwindow.popularity").toLowerCase() + "=" + pop + ")";
				}

				return( res );
			}
			case TITLE_INDICATOR_TEXT:{

				SubscriptionMDIEntry mdi = (SubscriptionMDIEntry) subs.getUserData(SubscriptionManagerUI.SUB_ENTRYINFO_KEY);

				if ( mdi != null ){

					mdi.setWarning();
				}

				if( subs.getHistory().getNumUnread() > 0 ){

					return ( "" + subs.getHistory().getNumUnread());
				}

				return null;
			}
		}

		return( null );
	}

	protected void
	removeWithConfirm()
	{
		SubscriptionManagerUI.removeWithConfirm( subs );
	}
}
