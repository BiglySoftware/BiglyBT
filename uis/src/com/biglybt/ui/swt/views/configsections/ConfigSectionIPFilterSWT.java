/*
 * File    : ConfigPanel*.java
 * Created : 11 mar. 2004
 * By      : TuxPaper
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

package com.biglybt.ui.swt.views.configsections;

import java.util.Arrays;
import java.util.Comparator;

import org.eclipse.swt.SWT;
import org.eclipse.swt.events.MouseAdapter;
import org.eclipse.swt.events.MouseEvent;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.*;

import com.biglybt.core.CoreFactory;
import com.biglybt.core.config.ConfigKeys.*;
import com.biglybt.core.ipfilter.*;
import com.biglybt.core.logging.LogAlert;
import com.biglybt.core.logging.Logger;
import com.biglybt.ui.common.updater.UIUpdatable;
import com.biglybt.ui.config.ConfigSectionIPFilter;
import com.biglybt.ui.swt.Messages;
import com.biglybt.ui.swt.Utils;
import com.biglybt.ui.swt.config.IpFilterEditor;
import com.biglybt.ui.swt.pif.UISWTParameterContext;
import com.biglybt.ui.swt.uiupdater.UIUpdaterSWT;
import com.biglybt.pif.ui.config.BooleanParameter;

public class ConfigSectionIPFilterSWT
	extends ConfigSectionIPFilter
{
	private IPEditorParameter paramContextIPEditor;

	static class FilterComparator
		implements Comparator<IpRange>
	{

		boolean ascending = true;

		static final int FIELD_NAME = 0;

		static final int FIELD_START_IP = 1;

		static final int FIELD_END_IP = 2;

		int field = FIELD_START_IP;

		@Override
		public int compare(IpRange range0, IpRange range1) {
			if (field == FIELD_NAME) {
				return (ascending ? 1 : -1) * (range0.compareDescription(range1));
			}
			if (field == FIELD_START_IP) {
				return (ascending ? 1 : -1) * (range0.compareStartIpTo(range1));
			}
			if (field == FIELD_END_IP) {
				return (ascending ? 1 : -1) * (range0.compareEndIpTo(range1));
			}
			return 0;
		}

		public void setField(int newField) {
			if (field == newField)
				ascending = !ascending;
			field = newField;
		}

	}

	public ConfigSectionIPFilterSWT() {
		init(new IPEditorParameter());
	}

	@Override
	public void saveConfigSection() {
		super.saveConfigSection();
		try {
			final IpFilterManager ipFilterManager = CoreFactory.getSingleton().getIpFilterManager();
			IpFilter filter = ipFilterManager.getIPFilter();
			filter.save();
		} catch (Exception e) {
			Logger.log(
					new LogAlert(LogAlert.UNREPEATABLE, "Save of filter file fails", e));
		}
	}

	@Override
	public void deleteConfigSection() {
		super.deleteConfigSection();

		if (paramContextIPEditor != null) {
			paramContextIPEditor.dispose();
			paramContextIPEditor = null;
		}
	}

	public class IPEditorParameter
		implements UISWTParameterContext
	{

		private Table table;

		private FilterComparator comparator;

		private boolean bIsCachingDescriptions = false;

		IpRange[] ipRanges;

		IpFilter filter;

		private IPFilterListener filterListener;

		// volatile boolean noChange = true;

		@Override
		public void create(Composite gFilter) {
			
			paramContextIPEditor = this;
			
			// table
			comparator = new FilterComparator();
			IpFilterManager ipFilterManager = CoreFactory.getSingleton().getIpFilterManager();
			filter = ipFilterManager.getIPFilter();

			GridLayout gridLayout = new GridLayout();
			gridLayout.marginHeight = gridLayout.marginWidth = 0;
			gFilter.setLayout(gridLayout);
			Layout parentLayout = gFilter.getParent().getLayout();
			if (parentLayout instanceof GridLayout) {
				GridLayout gridParentLayout = (GridLayout) parentLayout;
				GridData gridData = new GridData(GridData.FILL_BOTH);
				gridData.horizontalSpan = gridParentLayout.numColumns;
				gFilter.setLayoutData(gridData);
			}

			table = new Table(gFilter,
					SWT.SINGLE | SWT.BORDER | SWT.FULL_SELECTION | SWT.VIRTUAL);
			String[] headers = {
				"label.description",
				"ConfigView.section.ipfilter.start",
				"ConfigView.section.ipfilter.end"
			};
			int[] sizes = {
				110,
				110,
				110
			};
			int[] aligns = {
				SWT.LEFT,
				SWT.CENTER,
				SWT.CENTER
			};
			for (int i = 0; i < headers.length; i++) {
				TableColumn tc = new TableColumn(table, aligns[i]);
				tc.setText(headers[i]);
				tc.setWidth(sizes[i]);
				Messages.setLanguageText(tc, headers[i]); //$NON-NLS-1$
			}

			TableColumn[] columns = table.getColumns();
			columns[0].setData(FilterComparator.FIELD_NAME);
			columns[1].setData(FilterComparator.FIELD_START_IP);
			columns[2].setData(FilterComparator.FIELD_END_IP);

			Listener listener = e -> {
				TableColumn tc = (TableColumn) e.widget;
				if (tc == null || tc.isDisposed()) {
					return;
				}
				int field = ((Number) tc.getData()).intValue();
				comparator.setField(field);

				if (field == FilterComparator.FIELD_NAME && !bIsCachingDescriptions) {
					IpFilterManager ipFilterManager1 = CoreFactory.getSingleton().getIpFilterManager();
					ipFilterManager1.cacheAllDescriptions();
					bIsCachingDescriptions = true;
				}
				resyncTable();
			};

			columns[0].addListener(SWT.Selection, listener);
			columns[1].addListener(SWT.Selection, listener);
			columns[2].addListener(SWT.Selection, listener);

			table.setHeaderVisible(true);

			GridData gridData = new GridData(GridData.FILL_BOTH);
			gridData.heightHint = table.getHeaderHeight() * 3;
			gridData.widthHint = 200;
			table.setLayoutData(gridData);

			Composite cArea = new Composite(gFilter, SWT.NULL);
			GridLayout layout = new GridLayout();
			layout.marginHeight = 0;
			layout.marginWidth = 0;
			layout.numColumns = 4;
			cArea.setLayout(layout);
			gridData = new GridData(GridData.FILL_HORIZONTAL);
			cArea.setLayoutData(gridData);

			Button add = new Button(cArea, SWT.PUSH);
			gridData = new GridData(GridData.CENTER);
			gridData.widthHint = 100;
			add.setLayoutData(gridData);
			Messages.setLanguageText(add, "Button.add");
			add.addListener(SWT.Selection, arg0 -> addRange());

			Button remove = new Button(cArea, SWT.PUSH);
			gridData = new GridData(GridData.CENTER);
			gridData.widthHint = 100;
			remove.setLayoutData(gridData);
			Messages.setLanguageText(remove, "ConfigView.section.ipfilter.remove");
			remove.addListener(SWT.Selection, arg0 -> {
				TableItem[] selection = table.getSelection();
				if (selection.length == 0) {
					return;
				}
				removeRange((IpRange) selection[0].getData());
				resyncTable();
			});

			Button edit = new Button(cArea, SWT.PUSH);
			gridData = new GridData(GridData.CENTER);
			gridData.widthHint = 100;
			edit.setLayoutData(gridData);
			Messages.setLanguageText(edit, "Button.edit");
			edit.addListener(SWT.Selection, arg0 -> {
				TableItem[] selection = table.getSelection();
				if (selection.length == 0)
					return;
				editRange(selection[0]);
			});

			table.addMouseListener(new MouseAdapter() {
				@Override
				public void mouseDoubleClick(MouseEvent arg0) {
					TableItem[] selection = table.getSelection();
					if (selection.length == 0)
						return;
					editRange( selection[0]);
				}
			});

			Control[] controls = new Control[3];
			controls[0] = add;
			controls[1] = remove;
			controls[2] = edit;

			BooleanParameter enabled = (BooleanParameter) getPluginParam(
					IPFilter.BCFG_IP_FILTER_ENABLED);
			if (enabled != null) {
				Runnable code = () -> {
					boolean enable = enabled.getValue();
					for (Control control : controls) {
						control.setEnabled(enable);
					}
				};
				enabled.addListener(param -> Utils.execSWTThread(code));
				code.run();
			}

			ipRanges = new IpRange[0];

			table.addListener(SWT.SetData, event -> {
				TableItem item = (TableItem) event.item;
				int index = table.indexOf(item);

				// seems we can get -1 here (see bug 1219314 )

				if (index < 0 || index >= ipRanges.length) {
					return;
				}
				IpRange range = ipRanges[index];
				item.setText(0, range.getDescription());
				item.setText(1, range.getStartIp());
				item.setText(2, range.getEndIp());
				item.setData(range);
			});

			resyncTable();

			table.addListener(SWT.Resize, e -> resizeTable());

			gFilter.addListener(SWT.Resize, e -> resizeTable());

			filterListener = new IPFilterListener() {
				@Override
				public void IPFilterEnabledChanged(boolean is_enabled) {
				}

				@Override
				public boolean canIPBeBanned(String ip) {
					return true;
				}

				@Override
				public void IPBanned(BannedIp ip) {
				}

				@Override
				public void IPBlockedListChanged(final IpFilter filter) {
					Utils.execSWTThread(() -> {
						if (table.isDisposed()) {
							filter.removeListener(filterListener);
							return;
						}
						resyncTable();
					});

				}

				@Override
				public boolean canIPBeBlocked(String ip, byte[] torrent_hash) {
					return true;
				}
			};
			filter.addListener(filterListener);
			
			UIUpdaterSWT.getInstance().addUpdater(
				new UIUpdatable() {
					@Override
					public void updateUI() {
						if (gFilter.isDisposed()) {
							UIUpdaterSWT.getInstance().removeUpdater(this);
						} else {
							refresh();
						}
					}

					@Override
					public String getUpdateUIName() {
						return ("IPFilter ConfigView" );
					}
				});
		}

		private void resizeTable() {
			int iNewWidth = table.getClientArea().width
					- table.getColumn(1).getWidth() - table.getColumn(2).getWidth() - 20;
			if (iNewWidth > 50)
				table.getColumn(0).setWidth(iNewWidth);
		}

		public void removeRange(IpRange range) {
			filter.removeRange(range);
		}

		public void 
		editRange(
			TableItem item )
		{	
			IpRange range = (IpRange)item.getData();
			
			new IpFilterEditor(
				CoreFactory.getSingleton(), 
				table.getShell(), 
				range,
				()->{
					item.setText(0, range.getDescription());
					item.setText(1, range.getStartIp());
					item.setText(2, range.getEndIp());
				});
		}

		public void addRange() {
			new IpFilterEditor(CoreFactory.getSingleton(), table.getShell(), null,null);
		}

		public void refresh() {
			/* table.getItems() is VERY slow
			if (table == null || table.isDisposed() || noChange)
				return;
			noChange = true;
			TableItem[] items = table.getItems();
			for (TableItem item : items) {
				if (item == null || item.isDisposed()) {
					continue;
				}
				String tmp = item.getText(0);
				IpRange range = (IpRange) item.getData();

				String desc = range.getDescription();

				if (desc != null && !desc.equals(tmp))
					item.setText(0, desc);

				tmp = item.getText(1);
				if (range.getStartIp() != null && !range.getStartIp().equals(tmp))
					item.setText(1, range.getStartIp());

				tmp = item.getText(2);
				if (range.getEndIp() != null && !range.getEndIp().equals(tmp))
					item.setText(2, range.getEndIp());

			}
			*/
		}

		private void
		resyncTable()
		{
			Utils.getOffOfSWTThread(()->{
			
				IpRange[] ranges = getSortedRanges(filter.getRanges());
				
				Utils.execSWTThread(()->{
					ipRanges = ranges;
					table.setItemCount(ipRanges.length);
					table.clearAll();
						// 	bug 69398 on Windows
					table.redraw();
				});
			});
		}
		
		private IpRange[] getSortedRanges(IpRange[] ranges) {
			Arrays.sort(ranges, comparator);

			return (ranges);

		}

		public void dispose() {
			if (bIsCachingDescriptions) {
				IpFilterManager ipFilterManager = CoreFactory.getSingleton().getIpFilterManager();
				ipFilterManager.clearDescriptionCache();
				bIsCachingDescriptions = false;
			}

			filter.removeListener(filterListener);
		}
	}
}
