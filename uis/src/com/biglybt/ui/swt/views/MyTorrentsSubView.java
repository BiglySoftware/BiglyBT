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

import com.biglybt.core.Core;
import com.biglybt.core.CoreFactory;
import org.eclipse.swt.SWT;
import org.eclipse.swt.events.SelectionEvent;
import org.eclipse.swt.events.SelectionListener;
import org.eclipse.swt.layout.FormData;
import org.eclipse.swt.layout.FormLayout;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.Button;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Control;
import org.eclipse.swt.widgets.Layout;
import com.biglybt.core.config.COConfigurationManager;
import com.biglybt.core.util.AERunnable;
import com.biglybt.pif.download.Download;
import com.biglybt.ui.swt.Messages;
import com.biglybt.ui.swt.Utils;
import com.biglybt.ui.swt.views.table.utils.TableColumnCreator;

import com.biglybt.core.tag.Tag;

/**
 * @author TuxPaper
 * @created Mar 6, 2015
 *
 */
public class MyTorrentsSubView
	extends MyTorrentsView
{

	private Button btnAnyTags;
	private boolean anyTorrentTags;

	public MyTorrentsSubView() {
		super("MyTorrentsSubView", false);
		neverShowCatOrTagButtons = true;
		isEmptyListOnNullDS = true;
		Core _core = CoreFactory.getSingleton();
		init(_core, "MyTorrentsSubView", Download.class,
				TableColumnCreator.createCompleteDM("MyTorrentsSubView"));
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
  protected void setCurrentTags(final Tag[] tags) {
  	super.setCurrentTags(tags);
  	updateButtonVisibility(tags);
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
}
