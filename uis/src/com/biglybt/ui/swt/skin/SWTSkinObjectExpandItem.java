/*
 * Created on Jun 21, 2006 1:22:57 PM
 * Copyright (C) Azureus Software, Inc, All Rights Reserved.
 *
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU General Public License
 * as published by the Free Software Foundation; either version 2
 * of the License, or (at your option) any later version.
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 * You should have received a copy of the GNU General Public License
 * along with this program; if not, write to the Free Software
 * Foundation, Inc., 59 Temple Place - Suite 330, Boston, MA  02111-1307, USA.
 */
package com.biglybt.ui.swt.skin;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

import org.eclipse.swt.SWT;
import org.eclipse.swt.events.*;
import org.eclipse.swt.graphics.Rectangle;
import org.eclipse.swt.widgets.*;
import com.biglybt.core.config.COConfigurationManager;
import com.biglybt.core.util.AERunnable;
import com.biglybt.core.util.Constants;
import com.biglybt.core.util.SystemTime;
import com.biglybt.ui.swt.Utils;

/**
 * @author TuxPaper
 * @created Jun 21, 2006
 *
 */
public class SWTSkinObjectExpandItem
	extends SWTSkinObjectContainer
	implements ExpandListener
{
	private ExpandItem expandItem;

	private boolean expanded;

	private boolean textOverride;

	private Composite composite;

	private boolean fillHeight;

	public SWTSkinObjectExpandItem(SWTSkin skin, SWTSkinProperties properties,
			String sID, String sConfigID, SWTSkinObject parent) {
		super(skin, properties, null, sID, sConfigID, "expanditem", parent);

		createExpandItem();
	}

	@SuppressWarnings("deprecation")
	private void createExpandItem() {
		if (!(parent instanceof SWTSkinObjectExpandBar)) {
			return;
		}

		final SWTSkinObjectExpandBar soExpandBar = (SWTSkinObjectExpandBar) parent;

		int style = SWT.NONE;
		if (properties.getIntValue(sConfigID + ".border", 0) == 1) {
			style = SWT.BORDER;
		}

		final ExpandBar expandBar = soExpandBar.getExpandbar();
		expandBar.addExpandListener(this);

		expandItem = new ExpandItem(expandBar, style);

		String lastExpandStateID = "ui.skin." + sConfigID + ".expanded";
		if (COConfigurationManager.hasParameter(lastExpandStateID, true)) {
			boolean lastExpandState = COConfigurationManager.getBooleanParameter(
					lastExpandStateID, false);
			setExpanded(lastExpandState);
		} else if (properties.getBooleanValue(sConfigID + ".expanded", false)) {
			setExpanded(true);
		}

		composite = createComposite(soExpandBar.getComposite());
		expandItem.setControl(composite);
		composite.setLayoutData(null);
		composite.setData("skin.layedout", true);

		soExpandBar.addExpandItem(this);

		expandItem.addDisposeListener(new DisposeListener() {
			@Override
			public void widgetDisposed(DisposeEvent e) {
				soExpandBar.removeExpandItem(SWTSkinObjectExpandItem.this);
			}
		});

		composite.addListener(SWT.Resize, new Listener() {
			private Map<Rectangle,Long>	resize_history = new HashMap<>();
			@Override
			public void handleEvent(Event event) {
				Rectangle bounds = composite.getBounds();
				long now = SystemTime.getMonotonousTime();
				Long	prev = resize_history.get( bounds );
				if ( prev != null){
					if ( now - prev < 500 ){
						return;
					}
				}
				Iterator<Long> it = resize_history.values().iterator();
				while( it.hasNext()){
					if ( now - it.next() >= 500 ){
						it.remove();
					}
				}
				resize_history.put( bounds, now );
				Utils.execSWTThreadLater(0, new AERunnable() {
					@Override
					public void runSupport() {
						SWTSkinObjectExpandBar soExpandBar = (SWTSkinObjectExpandBar) parent;
						if ( !expandItem.isDisposed()){
							soExpandBar.handleResize(expandItem);
						}
					}
				});
			}
		});
	}

	@Override
	public void relayout() {
		super.relayout();
		SWTSkinObjectExpandBar soExpandBar = (SWTSkinObjectExpandBar) parent;
		soExpandBar.handleResize(expandItem);
	}

	protected void resizeComposite() {
		//System.out.println(SWTSkinObjectExpandItem.this + "] resize "
		//		+ composite.getSize() + ";" + Debug.getCompressedStackTrace());
		SWTSkinObjectExpandBar soExpandBar = (SWTSkinObjectExpandBar) parent;
		final ExpandBar expandBar = soExpandBar.getExpandbar();
		if (composite.isDisposed()) {
			return;
		}

		if (!composite.isVisible()) {
			return;
		}

		Rectangle clientArea = expandBar.getClientArea();

		int newHeight;
		if (properties.getBooleanValue(sConfigID + ".fillheight", false)) {
			ExpandItem[] items = expandBar.getItems();
			int h = expandBar.getSpacing();
			for (ExpandItem item : items) {
				h += expandBar.getSpacing();
				//System.out.print(expandBar.indexOf(item) + ":hh="
				//		+ item.getHeaderHeight() + "/" + item.getHeight()
				//		+ ", ");
				int hh = item.getHeaderHeight();
				// linux problems.. negative header height and headher height > itemheight
				int ih = item.getHeight();
				if (hh < 0) {
					hh += item.getHeight();
				} else if (hh > ih) {
					hh -= ih;
				}

				h += hh;
				if (expandItem != item) {
					if (item.getExpanded() && item.getControl().isVisible()) {
						h += item.getHeight();
					}
				}
			}
			//System.out.println("tot=" + h + ";" + Debug.getCompressedStackTrace());
			
			newHeight = clientArea.height - h;

			int min = properties.getIntValue(sConfigID + ".fillheightmin", 0 );

			if ( min > 0 ){
				if (  newHeight < min ){
					newHeight = min;
				}
			}
			//System.out.println("fill " + clientArea + ";h=" + h + " to " + newHeight);
		} else {
			newHeight = composite.computeSize(clientArea.width, SWT.DEFAULT, true).y;
			expandBar.computeSize(SWT.DEFAULT, SWT.DEFAULT, true);
		}

		if (expandItem.getHeight() != newHeight) {
			expandItem.setHeight(newHeight);
		}
	}

	public ExpandItem getExpandItem() {
		return expandItem;
	}

	public boolean isExpanded() {
		return expanded;
	}

	private void setExpandedVariable(boolean expand) {
		expanded = expand;
		String lastExpandStateID = "ui.skin." + sConfigID + ".expanded";
		COConfigurationManager.setParameter(lastExpandStateID, expand);
	}

	public void setExpanded(final boolean expand) {
		Utils.execSWTThread(new AERunnable() {
			@Override
			public void runSupport() {
				expandItem.setExpanded(expand);
				setExpandedVariable(expand);
				Utils.execSWTThreadLater(0, new AERunnable() {
					@Override
					public void runSupport() {
						SWTSkinObjectExpandBar soExpandBar = (SWTSkinObjectExpandBar) parent;
						soExpandBar.handleResize(expandItem);
					}
				});
			}
		});
	}

	@Override
	public void itemCollapsed(ExpandEvent e) {
		if (e.item == expandItem) {
			setExpandedVariable(false);

			Utils.execSWTThreadLater(0, new AERunnable() {
				@Override
				public void runSupport() {
					SWTSkinObjectExpandBar soExpandBar = (SWTSkinObjectExpandBar) parent;
					soExpandBar.handleResize(expandItem);
				}
			});
		}
	}

	@Override
	public void itemExpanded(ExpandEvent e) {
		if (e.item == expandItem) {
			setExpandedVariable(true);
			Utils.execSWTThreadLater(0, new AERunnable() {
				@Override
				public void runSupport() {
					SWTSkinObjectExpandBar soExpandBar = (SWTSkinObjectExpandBar) parent;
					soExpandBar.handleResize(expandItem);
				}
			});
		}
	}

	// @see SWTSkinObjectBasic#switchSuffix(java.lang.String, int, boolean)
	@Override
	public String switchSuffix(String suffix, int level, boolean walkUp,
	                           boolean walkDown) {
		suffix = super.switchSuffix(suffix, level, walkUp, walkDown);

		if (suffix == null) {
			return null;
		}

		String sPrefix = sConfigID + ".text";
		String text = properties.getStringValue(sPrefix + suffix);
		if (text != null) {
			setText(text, true);
		}

		fillHeight = properties.getBooleanValue(sConfigID + ".fillheight", false);

		return suffix;
	}

	public void setText(final String text) {
		setText(text, false);
	}

	/**
	 * @param text
	 *
	 * @since 3.1.1.1
	 */
	private void setText(final String text, boolean auto) {
		if (!auto) {
			textOverride = true;
		} else if (textOverride) {
			return;
		}

		Utils.execSWTThread(new AERunnable() {
			@Override
			public void runSupport() {
				if (expandItem != null && !expandItem.isDisposed()) {
					if (Constants.isWindows) {
						expandItem.setText(text.replaceAll("&", "&&"));
					} else {
						expandItem.setText(text);
					}
				}
			}
		});

	}

	public boolean fillsHeight() {
		return fillHeight;
	}

	@Override
	public void dispose() {
		super.dispose();
		if (parent instanceof SWTSkinObjectExpandBar) {
			SWTSkinObjectExpandBar soExpandBar = (SWTSkinObjectExpandBar) parent;
			ExpandBar expandbar = soExpandBar.getExpandbar();
			if (expandbar != null && !expandbar.isDisposed()) {
				expandbar.removeExpandListener(this);
			}
		}
	}
}
