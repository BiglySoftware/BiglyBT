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

package com.biglybt.ui.swt.views.table.impl;

import java.util.Map;

import org.eclipse.swt.graphics.Image;
import org.eclipse.swt.widgets.Composite;

import com.biglybt.core.internat.MessageText;
import com.biglybt.core.util.AEDiagnosticsEvidenceGenerator;
import com.biglybt.core.util.IndentWriter;
import com.biglybt.ui.common.table.TableView;
import com.biglybt.ui.common.table.TableViewFilterCheck.TableViewFilterCheckEx;
import com.biglybt.ui.mdi.MdiEntry;
import com.biglybt.ui.swt.Messages;
import com.biglybt.ui.swt.components.BubbleTextBox;
import com.biglybt.ui.swt.debug.ObfuscateImage;
import com.biglybt.ui.swt.pif.UISWTView;
import com.biglybt.ui.swt.pif.UISWTViewEvent;
import com.biglybt.ui.swt.pifimpl.UISWTViewCoreEventListener;
import com.biglybt.ui.swt.views.table.TableViewSWT;
import com.biglybt.util.MapUtils;

/**
 * An {@link UISWTView} that contains a {@link TableView}.  Usually is
 * an view in a  {@link MdiEntry}, or a TableView's subview.
 */
public abstract class TableViewTab<DATASOURCETYPE>
	implements UISWTViewCoreEventListener, AEDiagnosticsEvidenceGenerator,
		ObfuscateImage
{
	private TableViewSWT<DATASOURCETYPE> tv;
	private final String textPrefixID;
	private Composite composite;
	private UISWTView swtView;
	private BubbleTextBox filterTextControl;
	private TableViewFilterCheckEx<DATASOURCETYPE> filterCheck;
	private boolean enableTabs = true;

	public TableViewTab(String textPrefixID) {
		this.textPrefixID = textPrefixID;
	}

	public TableViewSWT<DATASOURCETYPE> getTableView() {
		return tv;
	}

	public final void initialize(Composite composite) {
			// this view is instantiated manually from open-torrent-options->availability (TrackerAvailView) so
			// needs to work without an swtView - there's probably a better way of embedding the view in a
			// composite though....
		
		tv = initYourTableView();
		
		if ( !enableTabs ){
			tv.setEnableTabViews( false, false );
		}
		
		Composite parent = initComposite(composite);
		
		if ( swtView == null ){
			tv.initialize(parent);
		}else{
			tv.initialize(swtView, parent);
		}
		
		if (parent != composite) {
			this.composite = composite;
		} else {
			this.composite = tv.getComposite();
		}

		if (filterCheck != null) {
			tv.enableFilterCheck(filterTextControl, filterCheck);
		}

		if ( swtView != null ){
			Object dataSource = swtView.getDataSource();
			if (dataSource != null) {
				tv.setParentDataSource(dataSource);
			}
		}
		
		tableViewTabInitComplete();
	}

	public void
	setEnableTabViews(
		boolean	b )
	{
		enableTabs = b;
	}
	
	public void tableViewTabInitComplete() {
	}

	public Composite initComposite(Composite composite) {
		return composite;
	}

	public abstract TableViewSWT<DATASOURCETYPE> initYourTableView();

	// marked final and redirected to parentDataSourceChanged, so this method 
	// doesn't get confused with this TableView's datasource change
	public final void dataSourceChanged(Object newDataSource) {
		// No need to store datasource localy, super class handles it all
		parentDataSourceChanged(newDataSource);
		if (tv != null) {
			tv.setParentDataSource(newDataSource);
		}
	}
	
	public void parentDataSourceChanged(Object newParentDataSource) {
	}

	public final void refresh() {
		if (tv != null) {
			tv.refreshTable(false);
		}
	}

	public final void delete() {
		if (tv != null) {
			tv.delete();
		}
		tv = null;
	}

	public String getFullTitle() {
		return MessageText.getString(getTextPrefixID() + ".title.full");
	}

	/* (non-Javadoc)
	 * @see com.biglybt.core.util.AEDiagnosticsEvidenceGenerator#generate(com.biglybt.core.util.IndentWriter)
	 */
	@Override
	public void generate(IndentWriter writer) {
		if (tv != null) {
			tv.generate(writer);
		}
	}

	public Composite getComposite() {
		return composite;
	}

	public String getTextPrefixID() {
		return textPrefixID;
	}

	public void viewActivated() {
		// cheap hack.. calling isVisible freshens table's visible status (and
		// updates subviews)
		if (tv != null) {
			tv.isVisible();
		}
	}

	private void viewDeactivated() {
		if (tv != null) {
			tv.isVisible();
		}
	}

	@Override
	public boolean eventOccurred(UISWTViewEvent event) {
		switch (event.getType()) {
			case UISWTViewEvent.TYPE_CREATE:
				swtView = (UISWTView) event.getData();
				if (swtView == null || !allowCreate(swtView)) {
					return false;
				}
				swtView.setTitle(getFullTitle());
				break;

			case UISWTViewEvent.TYPE_DESTROY:
				delete();
				swtView = null;
				break;

			case UISWTViewEvent.TYPE_INITIALIZE:
				initialize((Composite) event.getData());
				break;

			case UISWTViewEvent.TYPE_LANGUAGEUPDATE:
				updateLanguage();
				break;

			case UISWTViewEvent.TYPE_DATASOURCE_CHANGED:
				dataSourceChanged(event.getData());
				break;

			case UISWTViewEvent.TYPE_SHOWN:
				viewActivated();
				break;

			case UISWTViewEvent.TYPE_HIDDEN:
				viewDeactivated();
				break;

			case UISWTViewEvent.TYPE_REFRESH:
				refresh();
				break;

			case UISWTViewEvent.TYPE_OBFUSCATE:
				Object data = event.getData();
				if (data instanceof Map) {
					obfuscatedImage((Image) MapUtils.getMapObject((Map) data, "image",
							null, Image.class));
				}
				break;
		}

		return true;
	}

	public boolean allowCreate(UISWTView swtView) {
		return true;
	}

	public void updateLanguage() {
		if (swtView != null) {
			swtView.setTitle(getFullTitle());
		}
		Messages.updateLanguageForControl(composite);
	}

	public UISWTView getSWTView() {
		return swtView;
	}

	public void enableFilterCheck(BubbleTextBox textControl,
			TableViewFilterCheckEx<DATASOURCETYPE> filter_check_handler) {
		if (tv != null) {
			tv.enableFilterCheck(textControl, filter_check_handler);
		} else {
			filterTextControl = textControl;
			filterCheck = filter_check_handler;
		}
	}

	// @see com.biglybt.ui.swt.debug.ObfuscateImage#obfuscatedImage(org.eclipse.swt.graphics.Image)
	@Override
	public Image obfuscatedImage(Image image) {
		if (tv != null) {
			return tv.obfuscatedImage(image);
		}
		return null;
	}
}
