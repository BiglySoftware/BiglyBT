/*
 * Copyright (C) Bigly Software.  All Rights Reserved.
 *
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU General Public License
 * as published by the Free Software Foundation; either version 2
 * of the License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program; if not, write to the Free Software
 * Foundation, Inc., 59 Temple Place - Suite 330, Boston, MA  02111-1307, USA.
 */

package com.biglybt.ui.swt.mdi;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import com.biglybt.ui.mdi.MdiCloseListener;
import com.biglybt.ui.mdi.MdiEntry;
import com.biglybt.ui.mdi.MdiEntryVitalityImage;
import com.biglybt.ui.mdi.MdiEntryVitalityImageListener;
import org.eclipse.swt.SWT;
import org.eclipse.swt.graphics.Image;
import org.eclipse.swt.graphics.Rectangle;

import com.biglybt.core.util.*;
import com.biglybt.ui.swt.Utils;

import com.biglybt.ui.swt.imageloader.ImageLoader;
import com.biglybt.ui.swt.widgets.TabFolderRenderer;

/**
 * @author TuxPaper
 * @created Sep 15, 2008
 *
 */
public class MdiEntryVitalityImageSWT
	implements MdiEntryVitalityImage, TabFolderRenderer.TabbedEntryVitalityImage
{
	private String imageID;

	private final MdiEntry mdiEntry;

	private List<MdiEntryVitalityImageListener> listeners = Collections.EMPTY_LIST;

	private String tooltip;

	private Rectangle hitArea;

	private boolean visible = true;

	private int currentAnimationIndex;

	private String suffix = "";

	private TimerEventPerformer performer;

	private TimerEventPeriodic timerEvent;

	private Image[] images;

	private int delayTime = -1;

	private String fullImageID;

	private int alignment = SWT.RIGHT;
	private boolean showOutsideOfEntry;
	private boolean showOnlyOnSelection;
	private boolean alwaysLast;

	public MdiEntryVitalityImageSWT(final MdiEntry mdiEntry, String imageID) {
		this.mdiEntry = mdiEntry;

		mdiEntry.addListener(new MdiCloseListener() {

			@Override
			public void mdiEntryClosed(MdiEntry entry, boolean userClosed) {
				ImageLoader imageLoader = ImageLoader.getInstance();
				if (fullImageID != null && imageLoader != null) {
					imageLoader.releaseImage(fullImageID);
				}
			}
		});

		setImageID(imageID);
	}

	// @see com.biglybt.pif.ui.sidebar.SideBarVitalityImage#getImageID()
	@Override
	public String getImageID() {
		return imageID;
	}

	/**
	 * @return the sideBarEntry
	 */
	@Override
	public MdiEntry getMdiEntry() {
		return mdiEntry;
	}

	// @see com.biglybt.pif.ui.sidebar.SideBarVitalityImage#addListener(com.biglybt.pif.ui.sidebar.SideBarVitalityImageListener)
	@Override
	public void addListener(MdiEntryVitalityImageListener l) {
		if (listeners == Collections.EMPTY_LIST) {
			listeners = new ArrayList<>(1);
		}
		listeners.add(l);
	}

	public boolean
	hasListeners()
	{
		return( !listeners.isEmpty());
	}
	
	@Override
	public void triggerClickedListeners(int x, int y) {
		Object[] list = listeners.toArray();
		for (int i = 0; i < list.length; i++) {
			MdiEntryVitalityImageListener l = (MdiEntryVitalityImageListener) list[i];
			try {
				l.mdiEntryVitalityImage_clicked(x, y);
			} catch (Exception e) {
				Debug.out(e);
			}
		}
	}

	// @see com.biglybt.pif.ui.sidebar.SideBarVitalityImage#setTooltip(java.lang.String)
	@Override
	public void setToolTip(String tooltip) {
		this.tooltip = tooltip;
	}

	public String getToolTip() {
		return tooltip;
	}

	/**
	 * @param bounds relative to entry
	 *
	 * @since 3.1.1.1
	 */
	public void setHitArea(Rectangle hitArea) {
		this.hitArea = hitArea;
	}

	public Rectangle getHitArea() {
		return hitArea;
	}

	// @see com.biglybt.pif.ui.sidebar.SideBarVitalityImage#getVisible()
	@Override
	public boolean isVisible() {
		return visible;
	}

	// @see com.biglybt.pif.ui.sidebar.SideBarVitalityImage#setVisible(boolean)
	@Override
	public void setVisible(boolean visible) {
		if (this.visible == visible) {
			return;
		}
		this.visible = visible;

		if (visible) {
			createTimerEvent();
		} else if (timerEvent != null) {
			timerEvent.cancel();
		}

		//System.out.println("Gonna redraw because of " + mdiEntry.getId() + " set to " + this.visible + " via " + Debug.getCompressedStackTrace() );

		Utils.execSWTThread(new AERunnable() {
			@Override
			public void runSupport() {
				if (mdiEntry != null) {
					mdiEntry.redraw();
				}
			}
		});
	}

	/**
	 *
	 *
	 * @since 3.1.1.1
	 */
	private synchronized void createTimerEvent() {
		if (timerEvent != null) {
			timerEvent.cancel();
		}
		if (images != null && images.length > 1) {
			ImageLoader imageLoader = ImageLoader.getInstance();
			int delay = delayTime == -1 ? imageLoader.getAnimationDelay(imageID)
					: delayTime;

			if (performer == null) {
				performer = new TimerEventPerformer() {
					private boolean exec_pending = false;

					private Object lock = this;

					@Override
					public void perform(TimerEvent event) {
						synchronized (lock) {

							if (exec_pending) {

								return;
							}

							exec_pending = true;
						}

						Utils.execSWTThread(new AERunnable() {
							@Override
							public void runSupport() {
								synchronized (lock) {

									exec_pending = false;
								}

								if (images == null || images.length == 0 || !visible
										|| hitArea == null) {
									return;
								}
								currentAnimationIndex++;
								if (currentAnimationIndex >= images.length) {
									currentAnimationIndex = 0;
								}
								if (mdiEntry instanceof MdiEntrySWT) {
									((MdiEntrySWT) mdiEntry).redraw(hitArea);
								}
							}
						});
					}
				};
			}
			timerEvent = SimpleTimer.addPeriodicEvent("Animate " + mdiEntry.getViewID()
					+ "::" + imageID + suffix, delay, performer);
		}
	}

	/**
	 * @param images
	 * @return the currentAnimationIndex
	 */
	public int getCurrentAnimationIndex(Image[] images) {
		if (currentAnimationIndex >= images.length) {
			currentAnimationIndex = 0;
		} else if (currentAnimationIndex < 0) {
			currentAnimationIndex = 0;
		}
		return currentAnimationIndex;
	}

	public void switchSuffix(String suffix) {
		if (suffix == null) {
			suffix = "";
		}
		if (suffix.equals(this.suffix)) {
			return;
		}
		this.suffix = suffix;
		setImageID(imageID);
	}

	@Override
	public void setImageID(final String id) {
		Utils.execSWTThread(new AERunnable() {
			@Override
			public void runSupport() {
				ImageLoader imageLoader = ImageLoader.getInstance();
				String newFullImageID = id + suffix;
				if (newFullImageID.equals(fullImageID)) {
					return;
				}
				if (fullImageID != null) {
					imageLoader.releaseImage(fullImageID);
				}
				imageID = id;
				images = imageLoader.getImages(newFullImageID);
				if (images == null || images.length == 0) {
					imageLoader.releaseImage(newFullImageID);
					newFullImageID = id;
					images = imageLoader.getImages(id);
				}
				fullImageID = newFullImageID;
				currentAnimationIndex = 0;
				if (isVisible()) {
					createTimerEvent();
				}
				if (mdiEntry != null) {
					mdiEntry.redraw();
				}
			}
		});
	}

	/**
	 * @return
	 *
	 * @since 3.1.1.1
	 */
	public Image getImage() {
		if (images == null || images.length == 0
				|| currentAnimationIndex >= images.length) {
			return null;
		}
		return images[currentAnimationIndex];
	}

	/**
	 * @param delayTime the delayTime to set
	 */
	public void setDelayTime(int delayTime) {
		if (this.delayTime == delayTime) {
			return;
		}
		this.delayTime = delayTime;
		if (isVisible()) {
			createTimerEvent();
		}
	}

	/**
	 * @return the delayTime
	 */
	public int getDelayTime() {
		return delayTime;
	}

	@Override
	public int getAlignment() {
		return alignment;
	}

	@Override
	public void setAlignment(int alignment) {
		this.alignment = alignment;
	}

	@Override
	public boolean getShowOutsideOfEntry() {
		return showOutsideOfEntry;
	}

	@Override
	public void setShowOutsideOfEntry(boolean showOutsideOfEntry) {
		this.showOutsideOfEntry = showOutsideOfEntry;
	}

	public void setShowOnlyOnSelection(boolean showOnlyOnSelection) {
		this.showOnlyOnSelection = showOnlyOnSelection;
	}

	public boolean getShowOnlyOnSelection() {
		return showOnlyOnSelection;
	}

	public void setAlwaysLast(boolean alwaysLast) {
		this.alwaysLast = alwaysLast;
	}

	public boolean getAlwaysLast() {
		return alwaysLast;
	}

	public void dispose() {
		if (listeners.size() > 0) {
			listeners.clear();
		}
	}
}
