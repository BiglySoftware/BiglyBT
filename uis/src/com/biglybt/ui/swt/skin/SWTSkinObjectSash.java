/*
 * Copyright (C) Azureus Software, Inc, All Rights Reserved.
 *
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU General Public License
 * as published by the Free Software Foundation; either version 2
 * of the License, or (at your option) any later version.
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 * You should have received a copy of the GNU General Public License
 * along with this program; if not, write to the Free Software
 * Foundation, Inc., 59 Temple Place - Suite 330, Boston, MA 02111-1307, USA.
 *
 */

package com.biglybt.ui.swt.skin;

import java.text.NumberFormat;

import org.eclipse.swt.SWT;
import org.eclipse.swt.events.PaintEvent;
import org.eclipse.swt.events.PaintListener;
import org.eclipse.swt.graphics.Point;
import org.eclipse.swt.graphics.Rectangle;
import org.eclipse.swt.layout.FormData;
import org.eclipse.swt.widgets.*;

import com.biglybt.core.config.COConfigurationManager;
import com.biglybt.core.util.AERunnable;
import com.biglybt.core.util.AERunnableObject;
import com.biglybt.core.util.Debug;
import com.biglybt.ui.swt.Utils;

/**
 * <p>
 * Parameters:
 * <dl>
 * <dt>.startpos</dt>
 * <dd>Position in pixels of where to start the sash by default</dd>
 * <dt></dt>
 * <dd></dd>
 * </dl>
 *
 * @author TuxPaper
 * @created Oct 18, 2006
 *
 */
public class SWTSkinObjectSash
	extends SWTSkinObjectBasic
{
	/**
	 * Fast Drag disables resizing left and right sides on each mouse move (when
	 * mouse is down)
	 *
	 * Two problems with disabling FASTDRAG:
	 * 1) The places we use the sash currently have very slow re-rendering
	 * 2) when the user drags out of bounds (minsize, etc), and we set doit
	 *    to false.  When the user lifts up the mouse button, we get one
	 *    selection event at the old position (because we cancelled)
	 *
	 * #2 can be fixed... #1 not so much..
	 */
	private static final boolean FASTDRAG = true;

	protected String sControlBefore;

	protected String sControlAfter;

	private Composite createOn;

	private final boolean isVertical;

	private Sash sash;

	private Composite parentComposite;

	private Composite above = null;

	private int aboveMin = 0;

	private Composite below = null;

	private int belowMin = 0;

	private String sBorder;

	private SWTSkinObject soAbove;

	private SWTSkinObject soBelow;

	public SWTSkinObjectSash(final SWTSkin skin,
			final SWTSkinProperties properties, final String sID,
			final String sConfigID, String[] typeParams, SWTSkinObject parent,
			final boolean bVertical) {
		super(skin, properties, sID, sConfigID, "sash", parent);
		isVertical = bVertical;

		int style = bVertical ? SWT.VERTICAL : SWT.HORIZONTAL;

		if (typeParams.length > 2) {
			sControlBefore = typeParams[1];
			sControlAfter = typeParams[2];
		}

		if (parent == null) {
			createOn = skin.getShell();
		} else {
			createOn = (Composite) parent.getControl();
		}

		if (createOn == null || createOn.isDisposed()) {
			Debug.out("Can not create " + sID + " because parent is null or disposed");
			return;
		}

		sash = new Sash(createOn, style);

		int splitAtPX = COConfigurationManager.getIntParameter("v3." + sID
				+ ".splitAtPX", -1);
		if (splitAtPX >= 0) {
			sash.setData("PX", new Long(splitAtPX));
		} else {
			String sPos = properties.getStringValue(sConfigID + ".startpos");
			if (sPos != null) {
				try {
					int l = NumberFormat.getInstance().parse(sPos).intValue();
					sash.setData("PX", new Long(l));
				} catch (Exception e) {
					Debug.out(e);
				}
			}
		}

		parentComposite = createOn;

		SWTSkinObject soInitializeSashAfterCreated = parent == null ? this : parent;
		soInitializeSashAfterCreated.addListener(new SWTSkinObjectListener() {
			@Override
			public Object eventOccured(SWTSkinObject skinObject, int eventType,
			                           Object params) {
				if (eventType == EVENT_CREATED) {
					initialize();
				}
				return null;
			}
		});

		sBorder = properties.getStringValue(sConfigID + ".border", (String) null);
		if (sBorder != null) {
			sash.addPaintListener(new PaintListener() {
				@Override
				public void paintControl(PaintEvent e) {
					e.gc.setForeground(e.gc.getDevice().getSystemColor(
							SWT.COLOR_WIDGET_NORMAL_SHADOW));
					Point size = sash.getSize();
					if (bVertical) {
						e.gc.drawLine(0, 0, 0, size.y);
						if (!sBorder.startsWith("thin-top")) {
							int x = size.x - 1;
							e.gc.drawLine(x, 0, x, 0 + size.y);
						}
					} else {
						e.gc.drawLine(0, 0, 0 + size.x, 0);
						if (!sBorder.startsWith("thin-top")) {
							int y = size.y - 1;
							e.gc.drawLine(0, y, 0 + size.x, y);
						}
					}
				}
			});
		}

		setControl(sash);
	}

	/**
	 *
	 *
	 * @since 3.1.0.1
	 */
	protected void initialize() {
		SWTSkinObject skinObject;

		skinObject = skin.getSkinObjectByID(sControlBefore);

		if (skinObject != null) {
			soAbove = skinObject;
			above = (Composite) skinObject.getControl();
			aboveMin = skinObject.getProperties().getIntValue(
					getConfigID() + ".above" + (isVertical ? ".minwidth" : ".minheight"),
					0);
			boolean aboveVisible = COConfigurationManager.getBooleanParameter("v3."
					+ sID + ".aboveVisible", true);
			soAbove.setVisible(aboveVisible);
		}

		skinObject = skin.getSkinObjectByID(sControlAfter);

		if (skinObject != null) {
			soBelow = skinObject;
			below = (Composite) skinObject.getControl();
		}
		if (below == null) {
			return;
		}

		belowMin = skinObject==null?0:skinObject.getProperties().getIntValue(
				getConfigID() + ".below" + (isVertical ? ".minwidth" : ".minheight"), 0);

		Listener l = new Listener() {
			@Override
			public void handleEvent(Event e) {
				if (e.type == SWT.MouseUp) {
					if (e.button == 3 || (e.button == 1 && (e.stateMask & SWT.MOD1) > 0)) {
						String sPos = properties.getStringValue(sConfigID + ".startpos");
						if (sPos == null) {
							return;
						}
						try {
							int l = NumberFormat.getInstance().parse(sPos).intValue();
							sash.setData("PX", new Long(l));
							// FALL THROUGH
							e.type = SWT.Show;
						} catch (Exception ex) {
							Debug.out(ex);
							return;
						}
					} else {
						return;
					}
				}

				if (e.type == SWT.Show) {
					// delay so soAbove's show gets triggered
					Utils.execSWTThreadLater(0, new AERunnable() {
						@Override
						public void runSupport() {
							handleShow();
						}
					});
				} else if (e.type == SWT.Selection) {
					if (FASTDRAG && e.detail == SWT.DRAG) {
						return;
					}

					Rectangle area = parentComposite.getBounds();
					FormData aboveData = (FormData) above.getLayoutData();
					//FormData belowData = (FormData) below.getLayoutData();
					if (isVertical) {
						// Need to figure out if we have to use border width elsewhere
						// in calculations (probably)
						aboveData.width = e.x - above.getBorderWidth();
						if (aboveData.width < aboveMin) {
							aboveData.width = aboveMin;
							e.x = aboveMin;
						} else {
							int excess = area.width - (above.getBorderWidth() * 2)
									- sash.getSize().x;
							if (excess - aboveData.width < belowMin) {
								aboveData.width = excess - belowMin;
								e.doit = false;
							}
						}
					} else {
						aboveData.height = e.y - above.getBorderWidth();
						if (aboveData.height < aboveMin) {
							aboveData.height = aboveMin;
							e.y = aboveMin;
						} else {
							int excess = area.height - (above.getBorderWidth() * 2)
									- sash.getSize().y;
							if (excess - aboveData.height < belowMin) {
								aboveData.height = excess - belowMin;
								e.doit = false;
							}
						}
					}

					parentComposite.layout(true);

					double aboveNewSize;
					if (isVertical) {
						aboveNewSize = above.getBounds().width + (sash.getSize().x / 2.0);
					} else {
						aboveNewSize = above.getBounds().height + (sash.getSize().y / 2.0);
					}
					sash.setData("PX", new Long((long) aboveNewSize));

				}
			}
		};
		sash.addListener(SWT.Selection, l);
		sash.addListener(SWT.MouseUp, l);
		sash.getShell().addListener(SWT.Show, l);

		handleShow();
	}

	@Override
	public void dispose() {
		Long px = (Long) sash.getData("PX");
		if (px != null && px.longValue() != 0) {
			COConfigurationManager.setParameter("v3." + sID + ".splitAtPX",
					px.longValue());
		}
		super.dispose();
	}

	/**
	 * @param e
	 *
	 * @since 3.1.0.1
	 */
	protected void handleShow() {
		if ( sash.isDisposed()){
			return;
		}

		Long px = (Long) sash.getData("PX");
		if (px == null) {
			return;
		}
		
		if ( px < 0 ){
			
			if ( parentComposite.getSize().y <= 0 ){
				
					// can't handle this yet
				
				return;
			}
			
			px = -px;
			
			sash.setData("PX", px );

			setBelowSize( px.intValue());
			
			return;
		}
		int newAboveSize;
		if (soAbove.isVisible()) {
			newAboveSize = px.intValue();
			if (newAboveSize < aboveMin) {
				newAboveSize = aboveMin;
			}
		} else {
			newAboveSize = 0;
		}

		FormData aboveData = (FormData) above.getLayoutData();
		if (aboveData == null) {
			aboveData = Utils.getFilledFormData();
			above.setLayoutData(aboveData);
		}
		if (isVertical) {
			aboveData.width = newAboveSize;
		} else {
			aboveData.height = newAboveSize;
		}

		parentComposite.layout(true);
	}

	/**
	 * @param below
	 * @param bVertical
	 * @param parentComposite
	 * @param sash
	 * @param above
	 *
	 */
	/*
	protected void setPercent(double pctAbove, Control sash, Composite above,
			Composite below, boolean bVertical, Control parentComposite,
			int minAbove, int belowMin) {
		FormData aboveData = (FormData) above.getLayoutData();
		if (aboveData == null) {
			return;
		}
		boolean layoutNeeded = false;
		if (bVertical) {
			int parentWidth = parentComposite.getBounds().width
					- (parentComposite.getBorderWidth() * 2) - sash.getSize().x;
			int newWidth = (int) (parentWidth * pctAbove);
			if (newWidth != aboveData.width) {
				aboveData.width = newWidth;
				layoutNeeded = true;
			}

			if (pctAbove != 0.0
					&& parentWidth - aboveData.width - sash.getSize().x < minAbove) {
				aboveData.width = parentWidth - minAbove - sash.getSize().x;
				layoutNeeded = true;

				//d = (double) (aboveData.width + sash.getSize().x) / parentWidth;
			} else if (aboveData.width < belowMin) {
				layoutNeeded = true;
				aboveData.width = belowMin;
			}

			sash.setData("PX", new Long(aboveData.width));
		} else {
			int parentHeight = parentComposite.getBounds().height
					- (parentComposite.getBorderWidth() * 2) - sash.getSize().y;
			int newHeight = (int) (parentHeight * pctAbove);
			if (aboveData.height != newHeight) {
				aboveData.height = newHeight;
				layoutNeeded = true;
			}

			if (pctAbove != 0.0 && parentHeight - aboveData.height < minAbove
					&& parentHeight >= minAbove) {
				aboveData.height = parentHeight - minAbove;
				layoutNeeded = true;
			} else if (aboveData.height < belowMin) {
				layoutNeeded = true;
				aboveData.height = belowMin;
			}
			sash.setData("PX", new Long(aboveData.height));
		}
		if (layoutNeeded) {
			above.getParent().layout();
		}
	}
	*/
	
	private void setBelowSize(final int px) {
		Utils.execSWTThread(new AERunnable() {
			@Override
			public void runSupport() {
				int sashHeight = isVertical ? sash.getSize().x : sash.getSize().y;
				int parentHeight = parentComposite.getBounds().height
						- (parentComposite.getBorderWidth() * 2);

				int wantAboveSize = parentHeight - sashHeight - px;
				
				if ( wantAboveSize < 0 ){
					wantAboveSize = 0;
				}
				sash.setData("PX", new Long(wantAboveSize));
				handleShow();
			}
		});
	}

	/*
	private void setAboveSize(final int px) {
		Utils.execSWTThread(new AERunnable() {
			@Override
			public void runSupport() {
				sash.setData("PX", new Long(px));
				handleShow();
			}
		});
	}
	
	public void resetWidth() {
		Utils.execSWTThread(new AERunnable() {
			@Override
			public void runSupport() {
				String sPos = properties.getStringValue(sConfigID + ".startpos");
				COConfigurationManager.removeParameter("v3." + sID + ".splitAt");
				COConfigurationManager.removeParameter("v3." + sID + ".splitAtPX");
				if (sPos != null) {
					sash.setData("PX", null);
					try {
						int l = NumberFormat.getInstance().parse(sPos).intValue();
						setAboveSize(l);
					} catch (Exception e) {
						Debug.out(e);
					}
				}
			}
		});
	}
	*/
	
	public boolean isAboveVisible() {
		if (soAbove == null || soAbove.isDisposed()) {
			return false;
		}
		return soAbove.isVisible();
	}

	public void setAboveVisible(boolean visible) {
		if (soAbove == null) {
			return;
		}
		COConfigurationManager.setParameter("v3." + sID + ".aboveVisible", visible);
		soAbove.setVisible(visible);
		handleShow();
	}
}
