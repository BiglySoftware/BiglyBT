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

package com.biglybt.ui.swt.views;

import java.util.Set;

import org.eclipse.swt.SWT;
import org.eclipse.swt.events.SelectionEvent;
import org.eclipse.swt.events.SelectionListener;
import org.eclipse.swt.layout.*;
import org.eclipse.swt.widgets.*;

import com.biglybt.core.Core;
import com.biglybt.core.CoreFactory;
import com.biglybt.core.config.COConfigurationManager;
import com.biglybt.core.download.DownloadManager;
import com.biglybt.core.tag.Tag;
import com.biglybt.core.tag.TagDownload;
import com.biglybt.core.util.AERunnable;
import com.biglybt.ui.common.table.impl.TableColumnManager;
import com.biglybt.ui.common.viewtitleinfo.ViewTitleInfo;
import com.biglybt.ui.common.viewtitleinfo.ViewTitleInfoManager;
import com.biglybt.ui.swt.Messages;
import com.biglybt.ui.swt.Utils;
import com.biglybt.ui.swt.pif.UISWTViewEvent;
import com.biglybt.ui.swt.views.table.TableViewSWT;
import com.biglybt.ui.swt.views.table.utils.TableColumnCreator;
import com.biglybt.util.DataSourceUtils;

import com.biglybt.pif.download.Download;
import com.biglybt.pif.ui.tables.TableManager;

/**
 * @author TuxPaper
 * @created Mar 6, 2015
 *
 */
public class MyTorrentsSubView
	extends MyTorrentsView
	implements ViewTitleInfo
{

	public static final String MSGID_PREFIX = TableManager.TABLE_MYTORRENTS_SUBVIEW;
	
	private Button btnAnyTags;
	private boolean anyTorrentTags;

	private boolean	destroyed;
	private Tag[] tempTags;

	public MyTorrentsSubView() {
		super(MSGID_PREFIX, false);
		neverShowCatButtons = true;
		neverShowTagButtons = true;
		isEmptyListOnNullDS = true;
		
			// If we don't save any existing column sort order data here then it get's lost :(
		
		TableColumnManager tcm = TableColumnManager.getInstance();
		tcm.saveTableColumns( Download.class, MSGID_PREFIX);
		  
		Core _core = CoreFactory.getSingleton();
		init(_core, MSGID_PREFIX, Download.class, TableColumnCreator.createCompleteDM(MSGID_PREFIX));
	}

	/* (non-Javadoc)
	 * @see com.biglybt.ui.swt.views.table.impl.TableViewTab#initComposite(org.eclipse.swt.widgets.Composite)
	 */
	@Override
	public Composite initComposite(Composite composite) {
		Composite parent = new Composite(composite, SWT.NONE);
		GridLayout layout = new GridLayout();
		layout.marginHeight = layout.marginWidth = 0;
		layout.horizontalSpacing = layout.verticalSpacing = 0;
		parent.setLayout(layout);

		Layout compositeLayout = composite.getLayout();
		if (compositeLayout instanceof GridLayout) {
			parent.setLayoutData(new GridData(SWT.FILL, SWT.FILL, true, true));
		} else if (compositeLayout instanceof FormLayout) {
			parent.setLayoutData(Utils.getFilledFormData());
		}

		Composite cTop = new Composite(parent, SWT.NONE);

		GridData gd = new GridData(SWT.FILL, SWT.BEGINNING, true, false);
		cTop.setLayoutData(gd);
		cTop.setLayout(new FormLayout());

		btnAnyTags = new Button(cTop, SWT.CHECK);
		Messages.setLanguageText(btnAnyTags, "TorrentTags.Button.Any");
		btnAnyTags.addSelectionListener(new SelectionListener() {
			@Override
			public void widgetSelected(SelectionEvent e) {
				COConfigurationManager.setParameter("TorrentTags.Any",
						!anyTorrentTags);
			}

			@Override
			public void widgetDefaultSelected(SelectionEvent e) {
			}
		});
		anyTorrentTags = COConfigurationManager.getBooleanParameter(
				"TorrentTags.Any");
		btnAnyTags.setSelection(anyTorrentTags);
		setCurrentTagsAny(anyTorrentTags);
		updateButtonVisibility(getCurrentTags());
		Composite tableParent = new Composite(parent, SWT.NONE);

		tableParent.setLayoutData(new GridData(SWT.FILL, SWT.FILL, true, true));
		GridLayout gridLayout = new GridLayout();
		gridLayout.horizontalSpacing = gridLayout.verticalSpacing = 0;
		gridLayout.marginHeight = gridLayout.marginWidth = 0;
		tableParent.setLayout(gridLayout);

		parent.setTabList(new Control[] {
			tableParent,
			cTop
		});

		return tableParent;
	}


	@Override
	public TableViewSWT<DownloadManager> initYourTableView() {
		if ( destroyed ){
				// unfortunately this view doesn't properly support destruction and re-creation as required	
				// when embedded in a sub-tab so we have to hack around this
			
			destroyed = false;
			Core _core = CoreFactory.getSingleton();
			init(_core, MSGID_PREFIX, Download.class,
					TableColumnCreator.createCompleteDM(MSGID_PREFIX));
		}
		return( super.initYourTableView());
	}
	 
  @Override
  public void tableViewInitialized() {
  	anyTorrentTags = COConfigurationManager.getBooleanParameter("TorrentTags.Any");
    COConfigurationManager.addParameterListener("TorrentTags.Any", this);
    super.tableViewInitialized();
  }

  @Override
  public void tableViewDestroyed() {
  	COConfigurationManager.removeParameterListener("TorrentTags.Any", this);
    super.tableViewDestroyed();
  }

  @Override
  public boolean eventOccurred(UISWTViewEvent event) {
	  if ( event.getType() == UISWTViewEvent.TYPE_DESTROY ){
		  TableColumnManager tcm = TableColumnManager.getInstance();
		  tcm.saveTableColumns( Download.class, MSGID_PREFIX);
		  destroyed = true;
	  }
	  return( super.eventOccurred(event));
  }
  
		
  /* (non-Javadoc)
   * @see com.biglybt.ui.swt.views.MyTorrentsView#parameterChanged(java.lang.String)
   */
  @Override
  public void parameterChanged(String parameterName) {
  	if ("TorrentTags.Any".equals(parameterName)) {
			anyTorrentTags = COConfigurationManager.getBooleanParameter(parameterName);
			if (btnAnyTags != null && !btnAnyTags.isDisposed()) {
				Utils.execSWTThread(new AERunnable() {
					@Override
					public void runSupport() {
						if (btnAnyTags != null && !btnAnyTags.isDisposed()) {
							btnAnyTags.setSelection(anyTorrentTags);
						}
					}
				});
			}
			setCurrentTagsAny(anyTorrentTags);
  	}
  	super.parameterChanged(parameterName);
  }

  /* (non-Javadoc)
   * @see com.biglybt.ui.swt.views.MyTorrentsView#setCurrentTags(com.biglybt.core.tag.Tag[])
   */
  @Override
  protected void setCurrentTags(Tag... tags) {
  	super.setCurrentTags(tags);
	  ViewTitleInfoManager.refreshTitleInfo(this);
  	updateButtonVisibility(tags);
  }

	@Override
	public void parentDataSourceChanged(Object newParentDataSource) {
		super.parentDataSourceChanged(newParentDataSource);
		if (getTableView() == null) {
			tempTags = DataSourceUtils.getTags(newParentDataSource);
			ViewTitleInfoManager.refreshTitleInfo(this);
		} else {
			tempTags = null;
		}
	}

	private void updateButtonVisibility(final Tag[] tags) {
  	Utils.execSWTThread(new AERunnable() {

			@Override
			public void runSupport() {
				if (btnAnyTags == null || btnAnyTags.isDisposed()) {
					return;
				}
				boolean show = tags != null && tags.length > 1;
				btnAnyTags.setVisible(show);
				FormData fd = Utils.getFilledFormData();
				fd.height = show ? SWT.DEFAULT : 0;
				btnAnyTags.setLayoutData(fd);
				Composite cTop = btnAnyTags.getParent();
				cTop.getParent().layout(true, true);
			}
		});
	}

	@Override
	public Object getTitleInfoProperty(int propertyID) {
		if (propertyID == ViewTitleInfo.TITLE_INDICATOR_TEXT) {
			Tag[] tags = tempTags == null ? getCurrentTags() : tempTags;
			if (tags == null || tags.length == 0) {
				return null;
			}
			DownloadManager[] dms = DataSourceUtils.getDMs(tags);
			if (dms.length == 0) {
				return null;
			}

			Set<DownloadManager> minTaggedDownloads = null;
			for (Tag value : tags) {
				if (!(value instanceof TagDownload)) {
					continue;
				}
				TagDownload tag = (TagDownload) value;
				Set<DownloadManager> taggedDownloads = tag.getTaggedDownloads();
				if (minTaggedDownloads == null
						|| minTaggedDownloads.size() > taggedDownloads.size()) {
					minTaggedDownloads = taggedDownloads;
				}
			}
			int count = 0;
			for (DownloadManager dm : minTaggedDownloads) {
				boolean hasAll = true;
				for (Tag tag : tags) {
					if (!tag.hasTaggable(dm)) {
						hasAll = false;
						break;
					}
				}
				if (hasAll) {
					count++;
				}
			}
			return count == dms.length ? "" + count : dms.length + " | " + count;
		}
		return null;
	}
}
