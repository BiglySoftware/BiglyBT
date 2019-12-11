/*
 * Created on 22 juin 2005
 * Created by Olivier Chalouhi
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
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details ( see the LICENSE file ).
 *
 * You should have received a copy of the GNU General Public License
 * along with this program; if not, write to the Free Software
 * Foundation, Inc., 59 Temple Place, Suite 330, Boston, MA  02111-1307  USA
 */
package com.biglybt.ui.swt.views.stats;


import com.biglybt.core.Core;
import org.eclipse.swt.SWT;
import org.eclipse.swt.layout.FillLayout;
import org.eclipse.swt.widgets.Composite;

import com.biglybt.core.internat.MessageText;
import com.biglybt.core.util.Debug;
import com.biglybt.pif.PluginInterface;
import com.biglybt.ui.swt.Messages;
import com.biglybt.ui.swt.pif.UISWTView;
import com.biglybt.ui.swt.pif.UISWTViewEvent;
import com.biglybt.ui.swt.pifimpl.UISWTViewCoreEventListener;
import com.biglybt.core.CoreFactory;
import com.biglybt.core.CoreRunningListener;
import com.biglybt.core.dht.DHT;
import com.biglybt.plugin.dht.DHTPlugin;

public class DHTOpsView
	implements UISWTViewCoreEventListener
{
	public static final int DHT_TYPE_MAIN   = DHT.NW_AZ_MAIN;

	public static final String MSGID_PREFIX = "DHTOpsView";

	DHT dht;
	Composite panel;
	DHTOpsPanel drawPanel;
	private final boolean autoAlpha;
	private final boolean autoDHT;

	private int dht_type;
	private Core core;
	private UISWTView swtView;

	public DHTOpsView() {
		this(false);
	}

	public DHTOpsView(boolean autoAlpha) {
		this( autoAlpha, true );
	}

	public DHTOpsView(boolean autoAlpha, boolean autoDHT ) {
		this.autoAlpha = autoAlpha;
		this.autoDHT	= autoDHT;
	}

	private void init(Core core) {
		try {
			PluginInterface dht_pi = core.getPluginManager().getPluginInterfaceByClass( DHTPlugin.class );

			if ( dht_pi == null ){

				if ( drawPanel != null ){

					drawPanel.setUnavailable();
				}

				return;
			}

			DHTPlugin dht_plugin = (DHTPlugin)dht_pi.getPlugin();

			DHT[] dhts = dht_plugin.getDHTs();

			for (int i=0;i<dhts.length;i++){
				if ( dhts[i].getTransport().getNetwork() == dht_type ){
					dht = dhts[i];
					break;
				}
			}

			if ( drawPanel != null ){

				if ( dht != null ){
					
					drawPanel.setID( String.valueOf( dht.getTransport().getNetwork()));
				}
				
				if ( 	dht == null &&
						!dht_plugin.isInitialising()){

					drawPanel.setUnavailable();
				}
			}

			if ( dht == null ){
				return;
			}

		} catch(Exception e) {
			Debug.printStackTrace( e );
		}
	}

	public void
	setDHT(
		DHT		_dht )
	{
		dht	= _dht;
	}

	public void initialize(Composite composite) {
		if ( autoDHT ){
			CoreFactory.addCoreRunningListener(new CoreRunningListener() {

				@Override
				public void coreRunning(Core core) {
					DHTOpsView.this.core = core;
					init(core);
				}
			});
		}

		panel = new Composite(composite,SWT.NULL);
		panel.setLayout(new FillLayout());
		drawPanel = new DHTOpsPanel(panel);
		drawPanel.setAutoAlpha(autoAlpha);
		
		if ( dht != null ){
			
			drawPanel.setID( String.valueOf( dht.getTransport().getNetwork()));
		}
	}

	private Composite getComposite() {
		return panel;
	}

	private void refresh() {
		if (dht == null) {
			if (core != null) {
				// keep trying until dht is avail
				init(core);
			} else {
				return;
			}
		}

		if (dht != null) {
			drawPanel.refreshView( dht );
		}
	}

	private String
	getTitleID()
	{
		return( MSGID_PREFIX + ".title.full" );
	}

	public
	void delete()
	{
		if (drawPanel != null) {
			drawPanel.delete();
		}
	}

	@Override
	public boolean eventOccurred(UISWTViewEvent event) {
		switch (event.getType()) {
		case UISWTViewEvent.TYPE_CREATE:
			swtView = (UISWTView)event.getData();
			swtView.setTitle(MessageText.getString(getTitleID()));
			break;

		case UISWTViewEvent.TYPE_DESTROY:
			delete();
			break;

		case UISWTViewEvent.TYPE_INITIALIZE:
			initialize((Composite)event.getData());
			break;

		case UISWTViewEvent.TYPE_LANGUAGEUPDATE:
			Messages.updateLanguageForControl(getComposite());
			if (swtView != null) {
				swtView.setTitle(MessageText.getString(getTitleID()));
			}
			break;

		case UISWTViewEvent.TYPE_DATASOURCE_CHANGED:
			if (event.getData() instanceof Number) {
				dht_type = ((Number) event.getData()).intValue();
				if (swtView != null) {
					swtView.setTitle(MessageText.getString(getTitleID()));
				}
			}
			break;

		case UISWTViewEvent.TYPE_FOCUSGAINED:
			break;

		case UISWTViewEvent.TYPE_REFRESH:
			refresh();
			break;
		}

		return true;
	}
}
