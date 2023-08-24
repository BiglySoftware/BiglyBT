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

import java.util.Arrays;
import java.util.Comparator;

import org.eclipse.swt.SWT;
import org.eclipse.swt.custom.StackLayout;
import org.eclipse.swt.layout.FillLayout;
import org.eclipse.swt.layout.FormAttachment;
import org.eclipse.swt.layout.FormData;
import org.eclipse.swt.layout.FormLayout;
import org.eclipse.swt.widgets.*;
import com.biglybt.core.config.COConfigurationManager;
import com.biglybt.core.internat.MessageText;
import com.biglybt.core.util.AEThread2;
import com.biglybt.ui.swt.Utils;
import com.biglybt.ui.swt.components.shell.ShellFactory;

import com.biglybt.core.subs.Subscription;
import com.biglybt.core.subs.SubscriptionAssociationLookup;
import com.biglybt.core.subs.SubscriptionException;
import com.biglybt.core.subs.SubscriptionLookupListener;
import com.biglybt.core.subs.SubscriptionManager;
import com.biglybt.core.subs.SubscriptionManagerFactory;
import com.biglybt.core.subs.SubscriptionPopularityListener;
import com.biglybt.ui.swt.widgets.AnimatedImage;

public class SubscriptionListWindow implements SubscriptionLookupListener {

	final private String			display_name;
	final private byte[]			torrent_hash;
	final private String[]			networks;
	final private boolean			useCachedSubs;

	private Display display;
	private Shell shell;

	AnimatedImage animatedImage;

	Button action;
	Label loadingText;
	ProgressBar loadingProgress;
	boolean loadingDone = false;

	SubscriptionAssociationLookup lookup = null;

	Composite mainComposite;
	Composite loadingPanel;
	Composite listPanel;
	Table subscriptionsList;
	StackLayout mainLayout;

	private static class SubscriptionItemModel {
		String name;
		long popularity;
		String popularityDisplay;
		Subscription subscription;
		boolean selected;
	}

	SubscriptionItemModel subscriptionItems[];



	public
	SubscriptionListWindow(
		Shell		parent,
		String		display_name,
		byte[]		torrent_hash,
		String[]	networks,
		boolean 	useCachedSubs )
	{
		this.display_name		= display_name;
		this.torrent_hash 		= torrent_hash;
		this.networks			= networks;
		this.useCachedSubs		= useCachedSubs;

		shell = ShellFactory.createShell( parent, SWT.DIALOG_TRIM | SWT.RESIZE );
		Utils.setShellIcon(shell);
		shell.setSize(400,300);
		Utils.centerWindowRelativeTo( shell, parent );

		String networks_str = "";

		for ( String net: networks ){

			networks_str +=
					(networks_str.length()==0?"":", ") +
					MessageText.getString( "ConfigView.section.connection.networks." + net );
		}

		if ( networks_str.length() == 0 ){

			networks_str = MessageText.getString("label.none");
		}

		display = shell.getDisplay();
		shell.setText(MessageText.getString("subscriptions.listwindow.title") + " [" + networks_str + "]" );

		shell.setLayout(new FormLayout());

		mainComposite = new Composite(shell,SWT.NONE);
		Control separator = Utils.createSkinnedLabelSeparator(shell,SWT.HORIZONTAL);
		Button cancel = new Button(shell,SWT.PUSH);
		action = new Button(shell,SWT.PUSH);
		cancel.setText(MessageText.getString("Button.cancel"));

		FormData data;

		data = new FormData();
		data.left = new FormAttachment(0,0);
		data.right = new FormAttachment(100,0);
		data.top = new FormAttachment(0,0);
		data.bottom = new FormAttachment(separator,0);
		mainComposite.setLayoutData(data);

		data = new FormData();
		data.left = new FormAttachment(0,0);
		data.right = new FormAttachment(100,0);
		data.bottom = new FormAttachment(cancel,-2);
		separator.setLayoutData(data);

		data = new FormData();
		data.right = new FormAttachment(action);
		data.width = 100;
		data.bottom = new FormAttachment(100,-5);
		cancel.setLayoutData(data);

		data = new FormData();
		data.right = new FormAttachment(100,-5);
		data.width = 100;
		data.bottom = new FormAttachment(100,-5);
		action.setLayoutData(data);

		cancel.addListener(SWT.Selection, new Listener() {
			@Override
			public void handleEvent(Event arg0) {
				if(lookup != null) {
					lookup.cancel();
				}
				if(!shell.isDisposed()) {
					shell.dispose();
				}
			}
		});

		mainLayout = new StackLayout();
		mainComposite.setLayout(mainLayout);

		loadingPanel = new Composite(mainComposite,SWT.NONE);
		loadingPanel.setLayout(new FormLayout());

		listPanel = new Composite(mainComposite,SWT.NONE);
		listPanel.setLayout(new FillLayout());

		subscriptionsList = new Table(listPanel,SWT.V_SCROLL | SWT.SINGLE | SWT.FULL_SELECTION | SWT.VIRTUAL);
		subscriptionsList.setHeaderVisible(true);

		TableColumn name = new TableColumn(subscriptionsList,SWT.NONE);
		name.setText(MessageText.getString("subscriptions.listwindow.name"));
		name.setWidth(310);
		name.setResizable(false);

		TableColumn popularity = new TableColumn(subscriptionsList,SWT.NONE);
		popularity.setText(MessageText.getString("subscriptions.listwindow.popularity"));
		popularity.setWidth(70);
		popularity.setResizable(false);

		subscriptionsList.addListener(SWT.SetData, new Listener() {
			@Override
			public void handleEvent(Event e) {
				TableItem item = (TableItem) e.item;
				int index = subscriptionsList.indexOf(item);
				if(index >= 0 && index < subscriptionItems.length) {
					SubscriptionItemModel subscriptionItem = subscriptionItems[index];
					item.setText(0,subscriptionItem.name);
					item.setText(1,subscriptionItem.popularityDisplay);
				}
			}
		});

		subscriptionsList.setSortColumn(popularity);
		subscriptionsList.setSortDirection(SWT.DOWN);

		subscriptionsList.addListener(SWT.Selection, new Listener() {
			@Override
			public void handleEvent(Event arg0) {
				action.setEnabled(subscriptionsList.getSelectionIndex() != -1);
			}
		});

		Listener sortListener = new Listener() {
			@Override
			public void handleEvent(Event e) {
				// determine new sort column and direction
				TableColumn sortColumn = subscriptionsList.getSortColumn();
				TableColumn currentColumn = (TableColumn) e.widget;
				int dir = subscriptionsList.getSortDirection();
				if (sortColumn == currentColumn) {
					dir = dir == SWT.UP ? SWT.DOWN : SWT.UP;
				} else {
					subscriptionsList.setSortColumn(currentColumn);
					dir = SWT.DOWN;
				}
				subscriptionsList.setSortDirection(dir);
				sortAndRefresh();
			}
		};
		name.addListener(SWT.Selection, sortListener);
		popularity.addListener(SWT.Selection, sortListener);

		animatedImage = new AnimatedImage(loadingPanel);
		loadingText = new Label(loadingPanel,SWT.WRAP | SWT.CENTER);
		loadingProgress = new ProgressBar(loadingPanel,SWT.HORIZONTAL);

		animatedImage.setImageFromName("spinner_big");

		loadingText.setText(MessageText.getString("subscriptions.listwindow.loadingtext", new String[] { display_name }));

		loadingProgress.setMinimum(0);
		loadingProgress.setMaximum(300);
		loadingProgress.setSelection(0);

		data = new FormData();
		data.left = new FormAttachment(1,2,-16);
		data.top = new FormAttachment(1,2,-32);
		data.width = 32;
		data.height = 32;
		animatedImage.setLayoutData(data);

		data = new FormData();
		data.left = new FormAttachment(0,5);
		data.right = new FormAttachment(100,-5);
		data.top = new FormAttachment(animatedImage.getControl(),10);
		data.height = 50;
		loadingText.setLayoutData(data);

		data = new FormData();
		data.left = new FormAttachment(0,5);
		data.right = new FormAttachment(100,-5);
		data.top = new FormAttachment(loadingText,5);
		loadingProgress.setLayoutData(data);

		boolean autoCheck = COConfigurationManager.getBooleanParameter("subscriptions.autocheck");

		if(autoCheck) {
			startChecking();
		} else {
			action.setText(MessageText.getString("Button.yes"));
			Composite acceptPanel = new Composite(mainComposite,SWT.NONE);
			acceptPanel.setLayout(new FormLayout());

			Label acceptLabel = new Label(acceptPanel,SWT.WRAP | SWT.CENTER);

			acceptLabel.setText(MessageText.getString("subscriptions.listwindow.autochecktext"));

			data = new FormData();
			data.left = new FormAttachment(0,5);
			data.right = new FormAttachment(100,-5);
			data.top = new FormAttachment(1,3,0);
			acceptLabel.setLayoutData(data);

			action.addListener(SWT.Selection, new Listener() {
				@Override
				public void handleEvent(Event event) {
					action.removeListener(SWT.Selection,this);
					COConfigurationManager.setParameter("subscriptions.autocheck",true);
					startChecking();
					mainComposite.layout();
				}
			});
			mainLayout.topControl = acceptPanel;
		}


		//shell.setSize(400,300);
		shell.open();

	}

	private void startChecking() {
		action.setText(MessageText.getString("subscriptions.listwindow.subscribe"));
		action.setEnabled(false);
		try {


			SubscriptionManager subs_man = SubscriptionManagerFactory.getSingleton();
			if ( useCachedSubs ){
				Subscription[] subs = subs_man.getKnownSubscriptions( torrent_hash );
				complete(torrent_hash,subs);
			}else{
				lookup = subs_man.lookupAssociations( torrent_hash, display_name, networks, this);

				lookup.setTimeout( 1*60*1000 );
			}


			loadingDone = false;
			AEThread2 progressMover = new AEThread2("progressMover",true) {
				@Override
				public void run() {
					final int[] waitTime = new int[1];
					waitTime[0]= 100;
					while(!loadingDone) {
						if(display != null && ! display.isDisposed()) {
							display.asyncExec(new Runnable() {
								@Override
								public void run() {
									if(loadingProgress != null && !loadingProgress.isDisposed()) {
										int currentSelection = loadingProgress.getSelection() +1;
										loadingProgress.setSelection(currentSelection);
										if(currentSelection > (loadingProgress.getMaximum()) * 80 / 100) {
											waitTime[0] = 300;
										}
										if (currentSelection > (loadingProgress.getMaximum()) * 90 / 100) {
											waitTime[0] = 1000;
										}
									} else {
										loadingDone = true;
									}
								}
							});
						}
						try {
							Thread.sleep(waitTime[0]);
							//Thread.sleep(100);
						} catch (Exception e) {
							loadingDone = true;
						}
					}
				}
			};
			progressMover.start();

		} catch(Exception e) {
			failed(null,null);
		}
		animatedImage.start();
		mainLayout.topControl = loadingPanel;
	}

	/*private void populateSubscription(final Subscription subscription) {
		final TableItem item = new TableItem(subscriptionsList,SWT.NONE);
		item.setData("subscription",subscription);
		item.setText(0,subscription.getName());
		try {
			item.setText(1,MessageText.getString("subscriptions.listwindow.popularity.reading"));





		action.setEnabled(true);
	}*/

	@Override
	public void found(byte[] hash, Subscription subscription) {
		// TODO Auto-generated method stub

	}

	@Override
	public void complete(final byte[] hash, final Subscription[] subscriptions) {
		if( ! (subscriptions.length > 0) ) {
			failed(hash, null);
		} else {

			if(display != null && !display.isDisposed()) {
				display.asyncExec(new Runnable() {
					@Override
					public void run() {
						if ( mainComposite.isDisposed()) {
							return;
						}
						
						subscriptionItems = new SubscriptionItemModel[subscriptions.length];
						for(int i = 0 ; i < subscriptions.length ; i++) {
							final SubscriptionItemModel subscriptionItem = new SubscriptionItemModel();
							subscriptionItems[i] = subscriptionItem;
							subscriptionItem.name = subscriptions[i].getName();
							subscriptionItem.popularity = -1;
							subscriptionItem.popularityDisplay = MessageText.getString("subscriptions.listwindow.popularity.reading");
							subscriptionItem.subscription = subscriptions[i];

							try {
							subscriptions[i].getPopularity(
									new SubscriptionPopularityListener()
									{
										@Override
										public void
										gotPopularity(
											long		popularity )
										{
											update(subscriptionItem,popularity, popularity + "" );
										}

										@Override
										public void
										failed(
											SubscriptionException		error )
										{
											update(subscriptionItem,-2,MessageText.getString("subscriptions.listwindow.popularity.unknown"));
										}


									});
							} catch(SubscriptionException e) {

								update(subscriptionItem,-2,MessageText.getString("subscriptions.listwindow.popularity.unknown"));

							}

						}

						animatedImage.stop();

						mainLayout.topControl = listPanel;
						mainComposite.layout();

						sortAndRefresh();
						subscriptionsList.setSelection(0);

						action.addListener(SWT.Selection, new Listener() {
							@Override
							public void handleEvent(Event arg0) {
								if(subscriptionsList != null && !subscriptionsList.isDisposed()) {
									int selectedIndex = subscriptionsList.getSelectionIndex();
									if(selectedIndex >= 0 && selectedIndex < subscriptionItems.length) {
										Subscription subscription = (Subscription) subscriptionItems[selectedIndex].subscription;
										if(subscription != null) {
											subscription.setSubscribed(true);
											subscription.requestAttention();
										}
									}
								}
							}
						});
					}

				});
			}
		}
	}

	protected void
	update(
		final SubscriptionItemModel subscriptionItem,
		final long		popularity,
		final String	text )
	{
		display.asyncExec(
			new Runnable()
			{
				@Override
				public void
				run()
				{
					subscriptionItem.popularity = popularity;
					subscriptionItem.popularityDisplay = text;

					sortAndRefresh();
				}
			});
	}

	private void sortAndRefresh() {

		if ( subscriptionsList.isDisposed()){

			return;
		}

		for(int i = 0 ; i < subscriptionItems.length ; i++) {
			subscriptionItems[i].selected = false;
		}

		int currentSelection = subscriptionsList.getSelectionIndex();
		if(currentSelection >= 0 && currentSelection < subscriptionItems.length) {
			subscriptionItems[currentSelection].selected = true;
		}

		final int dir = subscriptionsList.getSortDirection() == SWT.DOWN ? 1 : -1;
		final boolean nameSort = subscriptionsList.getColumn(0) == subscriptionsList.getSortColumn();
		Arrays.sort(subscriptionItems,new Comparator() {
			@Override
			public int compare(Object arg0, Object arg1) {
				SubscriptionItemModel item0 = (SubscriptionItemModel) arg0;
				SubscriptionItemModel item1 = (SubscriptionItemModel) arg1;
				if(nameSort) {
					return dir * item0.name.compareTo(item1.name);
				} else {
					return dir * (int) (item1.popularity - item0.popularity);
				}
			}
		});
		subscriptionsList.setItemCount(subscriptionItems.length);
		subscriptionsList.clearAll();
		if(currentSelection >= 0 && currentSelection < subscriptionItems.length) {
			for(int i = 0 ; i < subscriptionItems.length ; i++) {
				if(subscriptionItems[i].selected) {
					subscriptionsList.setSelection(i);
				}
			}
		}
	}

	@Override
	public void failed(byte[] hash, SubscriptionException error) {
		if(display != null && !display.isDisposed()) {
			display.asyncExec(new Runnable() {
				@Override
				public void run() {
					animatedImage.stop();
					animatedImage.dispose();
					loadingProgress.dispose();
					if ( !loadingText.isDisposed()){
						loadingText.setText(MessageText.getString("subscriptions.listwindow.failed"));
					}
				}
			});
		}
	}
}
