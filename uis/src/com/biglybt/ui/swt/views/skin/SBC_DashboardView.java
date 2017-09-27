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

import org.eclipse.swt.SWT;
import org.eclipse.swt.custom.SashForm;
import org.eclipse.swt.events.SelectionAdapter;
import org.eclipse.swt.events.SelectionEvent;
import org.eclipse.swt.events.SelectionListener;
import org.eclipse.swt.layout.*;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Control;
import org.eclipse.swt.widgets.Group;
import org.eclipse.swt.widgets.Menu;
import org.eclipse.swt.widgets.MenuItem;

import com.biglybt.core.download.DownloadManager;
import com.biglybt.pif.download.Download;
import com.biglybt.ui.common.updater.UIUpdatable;
import com.biglybt.ui.swt.Messages;
import com.biglybt.ui.swt.Utils;
import com.biglybt.ui.swt.mdi.BaseMdiEntry;
import com.biglybt.ui.swt.shells.main.MainMDISetup;
import com.biglybt.ui.swt.skin.*;
import com.biglybt.ui.swt.views.skin.SB_Dashboard.DashboardItem;
import com.biglybt.ui.swt.views.skin.SB_Dashboard.DashboardListener;

public class SBC_DashboardView
	extends SkinView
	implements UIUpdatable, DashboardListener
{

	private static final String UI_NAME = "Dashboard";

	private Composite 			dashboard_composite;
	
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
		
		MainMDISetup.getSb_dashboard().addListener( this );
		
		return( null );
	}


	@Override
	public Object skinObjectHidden(SWTSkinObject skinObject, Object params) {

		// if we don't dispose then need to propagate show/hide events to sub-views
		
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
		
		build();
		
		return( result );
	}
	
	private void
	build()
	{
		if ( dashboard_composite == null ) {
			
			return;
		}
		
		Utils.disposeComposite( dashboard_composite, false );
		
		List<DashboardItem>	items = MainMDISetup.getSb_dashboard().getCurrent();
		
		SashForm sf = new SashForm( dashboard_composite, SWT.HORIZONTAL );
		
		sf.setLayoutData( Utils.getFilledFormData());
		
		for ( final DashboardItem item: items ) {
			
			Group g = new Group( sf, SWT.NULL );
			
			g.setLayoutData( Utils.getFilledFormData());
			
			g.setLayout( new GridLayout());
			
			g.setText( item.getTitle());
			
			Menu	menu = new Menu( g );
			
			
			MenuItem itemPop = new MenuItem( menu, SWT.PUSH );
			
			Messages.setLanguageText(itemPop, "menu.pop.out");

			itemPop.addSelectionListener(
				new SelectionAdapter(){
					
					@Override
					public void widgetSelected(SelectionEvent arg0){
						SkinnedDialog skinnedDialog =
								new SkinnedDialog(
										"skin3_dlg_sidebar_popout",
										"shell",
										null,	// standalone
										SWT.RESIZE | SWT.MAX | SWT.DIALOG_TRIM);
	
						SWTSkin skin = skinnedDialog.getSkin();
	
						SWTSkinObjectContainer cont = BaseMdiEntry.importStandAlone((SWTSkinObjectContainer)skin.getSkinObject( "content-area" ), item.getState());
	
						if ( cont != null ){
	
							skinnedDialog.setTitle( item.getTitle());
	
							skinnedDialog.open();
	
						}else{
	
							skinnedDialog.close();
						}
					}
				});
			
			new MenuItem( menu, SWT.SEPARATOR );
			
			MenuItem itemRemove = new MenuItem( menu, SWT.PUSH );
			
			Messages.setLanguageText(itemRemove, "MySharesView.menu.remove");

			Utils.setMenuItemImage(itemRemove, "delete");
			
			itemRemove.addSelectionListener(
				new SelectionAdapter(){
				
					@Override
					public void widgetSelected(SelectionEvent arg0){
						item.remove();
					}
				});
			
			g.setMenu( menu );
			
			SkinnedComposite skinned_comp =	new SkinnedComposite( g );
			
			SWTSkin skin = skinned_comp.getSkin();
			
			BaseMdiEntry.importStandAlone((SWTSkinObjectContainer)skin.getSkinObject( "content-area" ), item.getState());
				
			Control c = ((SWTSkinObjectContainer)skin.getSkinObject( "content-area" )).getControl();
			
			c.setLayoutData( Utils.getFilledFormData());
		}
		
		dashboard_composite.getParent().layout( true, true );
	}

	@Override
	public void itemsChanged(){
		Utils.execSWTThread(
			new Runnable()
			{
				public void
				run()
				{
					build();
				}
			});		
	}
	
	@Override
	public Object 
	skinObjectDestroyed(
		SWTSkinObject 	skinObject, 
		Object 			params ) 
	{
		MainMDISetup.getSb_dashboard().removeListener( this );
		
		return super.skinObjectDestroyed(skinObject, params);
	}
}
