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

package com.biglybt.ui.swt.widgets;

import java.io.File;
import java.util.List;

import org.eclipse.swt.SWT;
import org.eclipse.swt.accessibility.ACC;
import org.eclipse.swt.events.PaintEvent;
import org.eclipse.swt.events.PaintListener;
import org.eclipse.swt.graphics.*;
import org.eclipse.swt.widgets.*;

import com.biglybt.core.tag.Tag;
import com.biglybt.core.tag.TagUtils;
import com.biglybt.core.tag.Taggable;
import com.biglybt.core.util.Constants;
import com.biglybt.core.util.Debug;
import com.biglybt.ui.swt.Utils;
import com.biglybt.ui.swt.imageloader.ImageLoader;
import com.biglybt.ui.swt.mainwindow.Colors;
import com.biglybt.ui.swt.shells.GCStringPrinter;
import com.biglybt.ui.swt.utils.ColorCache;
import com.biglybt.ui.swt.views.utils.TagUIUtils;
import com.biglybt.util.StringCompareUtils;

public class TagCanvas
	extends Canvas
	implements PaintListener, Listener
{
	private static final Point MAX_IMAGE_SIZE = new Point(40, 28);

	public interface TagButtonTrigger
	{
		void tagButtonTriggered(Tag tag, boolean doTag);

		Boolean tagSelectedOverride(Tag tag);
	}

	private static final int DEF_CURVE_WIDTH = 25;

	private static final int COMPACT_CURVE_WIDTH = 15;

	private static final int DEF_PADDING_IMAGE_X = 5;

	private static final int COMPACT_PADDING_IMAGE_X = 0;

	private static final int DEF_CONTENT_PADDING_Y = 4;

	private static final int COMPACT_CONTENT_PADDING_Y = 2;

	private static final int DEF_CONTENT_PADDING_X0 = 5;

	private static final int COMPACT_CONTENT_PADDING_X0 = 3;

	private static final int DEF_CONTENT_PADDING_X1 = 8;

	private static final int COMPACT_CONTENT_PADDING_X1 = 5;

	private int paddingContentY = DEF_CONTENT_PADDING_Y;

	private int paddingContentX0 = DEF_CONTENT_PADDING_X0;

	private int paddingContentX1 = DEF_CONTENT_PADDING_X1;

	private int paddingImageX = DEF_PADDING_IMAGE_X;

	private int curveWidth = DEF_CURVE_WIDTH;

	private Image image;

	private String imageID;

	private boolean selected;

	private Tag tag;

	private String lastUsedName;

	private boolean grayed;

	private boolean disableAuto;

	private boolean enableWhenNoTaggables;

	private TagButtonTrigger trigger;

	private boolean compact;

	private boolean showImage = true;

	/** 
	 * Creates a Tag Canvas.<br/>  
	 * Auto Tags will be disabled.<br/>
	 * When Tag has no taggables, it will be disabled.
	 */
	public TagCanvas(Composite parent, Tag tag) {
		this(parent, tag, true, false);
	}

	public TagCanvas(Composite parent, Tag tag, boolean disableAuto,
			boolean enableWhenNoTaggables) {
		super(parent, SWT.DOUBLE_BUFFERED);
		this.tag = tag;
		this.enableWhenNoTaggables = enableWhenNoTaggables;

		setDisableAuto(disableAuto);
		
		addListener(SWT.MouseDown, this);
		addListener(SWT.MouseUp, this);
		addListener(SWT.KeyDown, this);
		addListener(SWT.FocusOut, this);
		addListener(SWT.FocusIn, this);
		addListener(SWT.Traverse, this);
		addListener(SWT.MouseHover, this);
		addListener(SWT.MouseExit, this);

		updateImage();
		addPaintListener(this);
	}

	@Override
	public Point computeSize(int wHint, int hHint, boolean changed) {
		if (tag == null) {
			return super.computeSize(wHint, hHint, changed);
		}

		if (lastUsedName == null) {
			lastUsedName = tag.getTagName(true);
		}

		GC gc = new GC(getDisplay());
		gc.setFont(getFont());

		gc.setTextAntialias(SWT.ON);
		GCStringPrinter sp = new GCStringPrinter(gc, lastUsedName,
				new Rectangle(0, 0, 9999, 9999), false, true, SWT.LEFT);
		sp.calculateMetrics();
		Point size = sp.getCalculatedSize();
		size.x += paddingContentX0 + paddingContentX1;
		size.y += paddingContentY + paddingContentY;
		gc.dispose();

		if (showImage && image != null && !image.isDisposed()) {
			Rectangle bounds = image.getBounds();
			int imageW = (bounds.width * (size.y - paddingContentY - paddingContentY))
					/ bounds.height;
			size.x += imageW + paddingImageX;
		}

		return size;
	}

	@Override
	public void handleEvent(Event e) {
		switch (e.type) {
			case SWT.MouseDown: {
				if (!getEnabled() || e.button != 1) {
					return;
				}
				if (trigger != null) {
					trigger.tagButtonTriggered(tag, !isSelected());
				}
				redraw();
				break;
			}
			case SWT.MouseUp: {
				if (!getEnabled() || e.button != 1) {
					return;
				}
				getAccessible().setFocus(ACC.CHILDID_SELF);
				break;
			}
			case SWT.MouseHover: {
				Utils.setTT(this, TagUtils.getTagTooltip(tag));
				break;
			}
			case SWT.MouseExit: {
				setToolTipText(null);
			}
			case SWT.FocusOut:
			case SWT.FocusIn: {
				redraw();
				break;
			}
			case SWT.Traverse: {
				if (!getEnabled()) {
					return;
				}
				switch (e.detail) {
					case SWT.TRAVERSE_PAGE_NEXT:
					case SWT.TRAVERSE_PAGE_PREVIOUS:
						e.doit = false;
						return;
					case SWT.TRAVERSE_RETURN:
						if (trigger != null) {
							trigger.tagButtonTriggered(tag, !isSelected());
							redraw();
						}
				}
				e.doit = true;
			}
			case SWT.KeyDown: {
				if (e.keyCode == SWT.F2 && (e.stateMask & SWT.MODIFIER_MASK) == 0) {
					if (!tag.getTagType().isTagTypeAuto()) {
						TagUIUtils.openRenameTagDialog(tag);
						e.doit = false;
					}
				}
			}
		}
	}

	public void setDisableAuto(boolean disableAuto) {
		if (this.disableAuto == disableAuto) {
			return;
		}
		this.disableAuto = disableAuto;
		
		boolean[] auto = tag.isTagAuto();
		boolean isAuto = auto.length >= 2 && auto[0] && auto[1];
		boolean enable = !isAuto || !disableAuto;
		setEnabled(enable);
	}

	public boolean isDisableAuto() {
		return disableAuto;
	}

	public void setImage(Image newImage, String key) {
		if (!com.biglybt.ui.swt.imageloader.ImageLoader.isRealImage(newImage)) {
			return;
		}
		if (newImage == image && StringCompareUtils.equals(key, imageID)) {
			return;
		}
		if (imageID != null) {
			com.biglybt.ui.swt.imageloader.ImageLoader.getInstance().releaseImage(
					imageID);
		}
		this.image = newImage;
		this.imageID = key;
		requestLayout();
		redraw();
	}

	public Tag getTag() {
		return tag;
	}

	public boolean isSelected() {
		return selected;
	}

	public void setSelected(boolean select) {
		if (select != selected) {
			selected = select;
			if (grayed) {
				grayed = false;
			}
			redraw();
		}
	}

	@Override
	public void paintControl(PaintEvent e) {
		if (tag == null || lastUsedName == null) {
			return;
		}

		Rectangle clientArea = getClientArea();
		//System.out.println("paint " + lastUsedName + "; " + clientArea + "; " + e);

		boolean selected = isSelected();
		boolean enabled = isEnabled();

		Color color = ColorCache.getColor(e.display, tag.getColor());
		Point size = getSize();
		e.gc.setAntialias(SWT.ON);
		if (color != null) {
			e.gc.setForeground(color);
		}
		int lineWidth = selected || !enabled ? 2 : 1;
		e.gc.setLineWidth(lineWidth);

		int width = size.x - lineWidth;
		if (selected) {
			e.gc.setAlpha(0x40);
			if (color != null) {
				e.gc.setBackground(color);
			}
			e.gc.fillRoundRectangle(-curveWidth, lineWidth - 1, width + curveWidth,
					size.y - lineWidth, curveWidth, curveWidth);
			e.gc.setAlpha(0xff);
		}
		if (!selected) {
			e.gc.setAlpha(0x80);
		}
		e.gc.setLineStyle(enabled ? SWT.LINE_SOLID : SWT.LINE_DOT);
		e.gc.drawRoundRectangle(-curveWidth, lineWidth - 1, width + curveWidth,
				size.y - lineWidth, curveWidth, curveWidth);
		e.gc.drawLine(lineWidth - 1, lineWidth, lineWidth - 1, size.y - lineWidth);

		clientArea.x += paddingContentX0;
		clientArea.width = clientArea.width - paddingContentX0;
		if (showImage && image != null) {
			Rectangle bounds = image.getBounds();
			int imageH = size.y - paddingContentY - paddingContentY;
			int imageW = (bounds.width * imageH) / bounds.height;

			e.gc.drawImage(image, 0, 0, bounds.width, bounds.height, clientArea.x,
					clientArea.y + paddingContentY, imageW, imageH);
			clientArea.x += imageW + paddingImageX;
			clientArea.width -= imageW - paddingImageX;
		}
		e.gc.setAlpha(grayed ? 0xA0 : 0xFF);
		e.gc.setForeground(getForeground());
		clientArea.y += paddingContentY;
		clientArea.height -= (paddingContentY + paddingContentY);
		GCStringPrinter sp = new GCStringPrinter(e.gc, lastUsedName, clientArea,
				true, true, SWT.LEFT);
		sp.printString();
		if (isFocusControl()) {
			Rectangle printArea = sp.getCalculatedDrawRect();
			if (printArea == null) {
				printArea = clientArea;
			}
			e.gc.setAlpha(0xFF);

			try {
				if (Constants.isWindows) {
					// drawFocus doesn't always draw when it should on Windows :(
					e.gc.setBackground(Colors.white);
					e.gc.setForeground(Colors.black);
					e.gc.setLineStyle(SWT.LINE_DOT);
					e.gc.setLineWidth(1);
					e.gc.drawRoundRectangle(printArea.x - 2, printArea.y, printArea.width + 4,
							printArea.height, 4, 4);
				} else {
					e.gc.drawFocus(printArea.x - 2, printArea.y - 1, printArea.width + 4,
							printArea.height + 2);
				}
			} catch (Throwable t) {
				Debug.out(t);
			}
		}
	}

	@Override
	public void dispose() {
		super.dispose();
		if (imageID != null) {
			com.biglybt.ui.swt.imageloader.ImageLoader.getInstance().releaseImage(
					imageID);
			imageID = null;
			image = null;
		}
	}

	public void updateName() {
		String tagName = tag.getTagName(true);
		if (!tagName.equals(lastUsedName)) {
			lastUsedName = null;
			requestLayout();
			redraw();
		}
	}

	public void setGrayed(boolean b) {
		if (b == grayed) {
			return;
		}
		grayed = b;
		redraw();
	}

	public void updateState(List<Taggable> taggables) {
		updateImage();
		updateName();

		if (taggables == null) {
			setEnabled(enableWhenNoTaggables);
			if (!enableWhenNoTaggables) {
				setSelected(false);
				return;
			}
		}

		boolean hasTag = false;
		boolean hasNoTag = false;

		Boolean override = trigger == null ? null
				: trigger.tagSelectedOverride(tag);

		if (taggables != null && override == null) {
			for (Taggable taggable : taggables) {
				boolean curHasTag = tag.hasTaggable(taggable);
				if (!hasTag && curHasTag) {
					hasTag = true;
					if (hasNoTag) {
						break;
					}
				} else if (!hasNoTag && !curHasTag) {
					hasNoTag = true;
					if (hasTag) {
						break;
					}
				}
			}
		} else if (override != null) {
			hasNoTag = !override;
			hasTag = override;
		}

		boolean[] auto = tag.isTagAuto();

		boolean auto_add = auto[0];
		boolean auto_rem = auto[1];

		if (hasTag && hasNoTag) {
			setEnabled(!auto_add);

			setGrayed(true);
			setSelected(true);
		} else {

			if (auto_add && auto_rem) {
				setGrayed(!hasTag);
				setEnabled(false);
			} else {
				setEnabled((hasTag) || (!hasTag && !auto_add));
				setGrayed(false);
			}

			setSelected(hasTag);
		}
	}

	@Override
	public void setEnabled(boolean enabled) {
		boolean wasEnabled = isEnabled();
		super.setEnabled(enabled);
		if (wasEnabled != enabled) {
			redraw();
		}
	}

	private void updateImage() {
		String iconFile = tag.getImageFile();
		if (iconFile != null) {
			try {
				String resource = new File(iconFile).toURI().toURL().toExternalForm();

				ImageLoader.getInstance().getUrlImage(resource, MAX_IMAGE_SIZE,
						(image, key, returnedImmediately) -> {
							if (image == null) {
								return;
							}

							Utils.execSWTThread(() -> setImage(image, key));
						});
			} catch (Throwable e) {
			}
		} else {
			String id = tag.getImageID();
			if (id != null) {
				Image image = ImageLoader.getInstance().getImage(id);
				setImage(image, id);
			} else {
				setImage(null, null);
			}
		}
	}

	public void setEnableWhenNoTaggables(boolean enableWhenNoTaggables) {
		if (this.enableWhenNoTaggables == enableWhenNoTaggables) {
			return;
		}
		this.enableWhenNoTaggables = enableWhenNoTaggables;
		redraw();
	}

	public boolean getEnableWhenNoTaggables() {
		return enableWhenNoTaggables;
	}

	public void setTrigger(TagButtonTrigger trigger) {
		this.trigger = trigger;
	}

	public void setCompact(boolean compact) {
		if (this.compact == compact) {
			return;
		}
		this.compact = compact;
		if (compact) {
			paddingImageX = COMPACT_PADDING_IMAGE_X;
			paddingContentY = COMPACT_CONTENT_PADDING_Y;
			paddingContentX0 = COMPACT_CONTENT_PADDING_X0;
			paddingContentX1 = COMPACT_CONTENT_PADDING_X1;
			curveWidth = COMPACT_CURVE_WIDTH;
			showImage = false;
		} else {
			paddingImageX = DEF_PADDING_IMAGE_X;
			paddingContentY = DEF_CONTENT_PADDING_Y;
			paddingContentX0 = DEF_CONTENT_PADDING_X0;
			paddingContentX1 = DEF_CONTENT_PADDING_X1;
			curveWidth = DEF_CURVE_WIDTH;
			showImage = true;
		}
		requestLayout();
	}

	public boolean isCompact() {
		return compact;
	}
}
