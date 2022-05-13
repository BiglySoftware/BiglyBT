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
import java.util.Collections;
import java.util.List;

import org.eclipse.swt.SWT;
import org.eclipse.swt.graphics.*;
import org.eclipse.swt.widgets.Control;

import com.biglybt.core.tag.Tag;
import com.biglybt.core.tag.Taggable;
import com.biglybt.core.util.Debug;
import com.biglybt.ui.swt.Utils;
import com.biglybt.ui.swt.imageloader.ImageLoader;
import com.biglybt.ui.swt.mainwindow.Colors;
import com.biglybt.ui.swt.mainwindow.HSLColor;
import com.biglybt.ui.swt.shells.GCStringPrinter;
import com.biglybt.ui.swt.utils.ColorCache;
import com.biglybt.ui.swt.widgets.TagCanvas.TagButtonTrigger;
import com.biglybt.util.StringCompareUtils;

public class TagPainter
{
	private static final int DEF_MIN_WIDTH = 25;

	private static final Point MAX_IMAGE_SIZE = new Point(40, 28);

	private static final int DEF_CURVE_WIDTH = 25;

	private static final int COMPACT_CURVE_WIDTH = 15;

	private static final int DEF_PADDING_IMAGE_X = 5;

	private static final int COMPACT_PADDING_IMAGE_X = 0;

	private static final int DEF_PADDING_IMAGE_Y = 2;

	private static final int COMPACT_PADDING_IMAGE_Y = 1;

	private static final int DEF_CONTENT_PADDING_Y = 3;

	private static final int COMPACT_CONTENT_PADDING_Y = 2;

	private static final int DEF_CONTENT_PADDING_X0 = 5;

	private static final int COMPACT_CONTENT_PADDING_X0 = 3;

	private static final int DEF_CONTENT_PADDING_X1 = 8;

	private static final int COMPACT_CONTENT_PADDING_X1 = 5;

	protected final Tag tag;

	private final TagCanvas control;

	public int paddingContentY = DEF_CONTENT_PADDING_Y;

	public int paddingContentX0 = DEF_CONTENT_PADDING_X0;

	public int paddingContentX1 = DEF_CONTENT_PADDING_X1;

	public int paddingImageX = DEF_PADDING_IMAGE_X;

	public int paddingImageY = DEF_PADDING_IMAGE_Y;
	public int curveWidth = DEF_CURVE_WIDTH;
	public String imageID;
	public Image image;
	public String lastUsedName;
	public boolean imageOverridesText = false;
	public boolean showImage = true;
	public Font font = null;
	public Color colorTagFaded;
	public Color colorTag;
	public boolean needsBorderOnSelection;
	protected boolean compact;
	protected boolean isEnabled = true;
	protected boolean enableWhenNoTaggables;
	protected TagButtonTrigger trigger;
	protected boolean disableAuto;
	private int minWidth = DEF_MIN_WIDTH;
	private boolean grayed;
	private int alpha = 255;

	private boolean selected;

	private boolean disposed;

	public TagPainter(Tag tag, TagCanvas control) {
		this.tag = tag;
		this.control = control;
	}

	public TagPainter(Tag tag) {
		this.tag = tag;
		control = null;
	}

	public Point computeSize(Device device, Font font) {
		if (lastUsedName == null) {
			lastUsedName = tag.getTagName(true);
		}

		GC gc = new GC(device);
		gc.setFont(font);

		gc.setTextAntialias(SWT.ON);

		boolean canShowImage = showImage && image != null && !image.isDisposed();
		boolean onlyShowImage = imageOverridesText && canShowImage;

		String text = lastUsedName.isEmpty() || onlyShowImage ? "\u200b"
				: lastUsedName;

		GCStringPrinter sp = new GCStringPrinter(gc, text,
				new Rectangle(0, 0, 9999, 9999), false, true, SWT.LEFT);
		sp.calculateMetrics();
		Point size = sp.getCalculatedSize();
		gc.dispose();

		if (size == null) {
			return null;
		}
		size.x += paddingContentX0 + paddingContentX1;
		size.y += paddingContentY + paddingContentY;

		if (canShowImage) {
			if (onlyShowImage) {
				size.x = 0;
			}
			Rectangle bounds = image.getBounds();
			int imageH = size.y - paddingImageY - paddingImageY;
			int imageW = (bounds.width * imageH) / bounds.height;
			size.x += imageW + paddingImageX;
		}


		size.x = Math.max(minWidth, size.x);

		return size;
	}

	public void dispose() {
		if (imageID != null) {
			ImageLoader.getInstance().releaseImage(imageID);
			imageID = null;
			image = null;
		}
		if (font != null) {
			font.dispose();
			font = null;
		}
		disposed = true;
	}

	public Control getControl() {
		return control;
	}

	public Point getSize(GC gc) {
		updateImage();
		return computeSize(gc.getDevice(), gc.getFont());
	}

	public Tag getTag() {
		return tag;
	}

	public boolean isDisposed() {
		return disposed;
	}

	public boolean isGrayed() {
		return grayed;
	}

	public boolean isSelected() {
		return selected;
	}
	
	public void
	setAlpha(
		int	_alpha )
	{
		if ( alpha != _alpha ){
			alpha = _alpha;
			redrawControl();
		}
	}

	public boolean paint(Taggable taggable, GC gc, int x, int y) {
		Point size = computeSize(gc.getDevice(), gc.getFont());

		if (taggable != null) {
			updateState(Collections.singletonList(taggable), gc.getDevice(),
					gc.getForeground(), gc.getBackground());
		} else {
			updateImage();
			updateColors(gc.getDevice(), gc.getForeground(), gc.getBackground());
		}
		Rectangle r = new Rectangle(0, 0, size.x, size.y);
		paintControl(gc, x, y, r, size, false);
		return true;
	}

	public void paintControl(GC gc, int xOfs, int yOfs, Rectangle clientArea,
			Point size, boolean focused) {
		if (lastUsedName == null) {
			return;
		}
		
		gc.setAlpha( alpha );
		//System.out.println("paint " + lastUsedName + "; " + clientArea + "; " + e);

		Color colorOrigBG = gc.getBackground();

		Color colorText = Colors.getInstance().getReadableColor(
				selected ? grayed ? colorTagFaded : colorTag : colorOrigBG);

		//Point size = getSize();
		gc.setAntialias(SWT.ON);
		gc.setTextAntialias(SWT.ON);

		if (selected) {
			gc.setBackground(grayed ? colorTagFaded : colorTag);
			gc.fillRoundRectangle(xOfs + -curveWidth, yOfs, size.x + curveWidth - 1,
					size.y - 1, curveWidth, curveWidth);
		}

		boolean imageOverride = imageOverridesText && image != null;

		if (!imageOverride) {
			if (!selected || grayed) {
				int lineWidth = focused ? 2 : 2;
				int y1 = lineWidth / 2;
				int x1 = y1;
				int width = size.x - lineWidth;
				int height = size.y - lineWidth;
				gc.setLineWidth(lineWidth);
				gc.setForeground(colorTag);
				gc.setLineStyle(SWT.LINE_SOLID);

				gc.drawRoundRectangle(xOfs + -curveWidth, yOfs + y1, width + curveWidth,
						height - y1 + 1, curveWidth, curveWidth);
				gc.drawLine(xOfs + x1, yOfs + y1, xOfs + x1, yOfs + height - y1 + 1);
				gc.setLineWidth(1);
			}
		}

		if (selected && needsBorderOnSelection) {
			gc.setLineWidth(1);
			gc.setForeground(colorText);
			gc.setAlpha(0x70);
			gc.setLineStyle(SWT.LINE_SOLID);

			gc.drawRoundRectangle(xOfs + -curveWidth, yOfs, size.x + curveWidth - 1,
					size.y - 1, curveWidth, curveWidth);
			gc.drawLine(xOfs, yOfs, xOfs, yOfs + size.y - 1);
			gc.setAlpha(alpha);
		}

		int imageX;

		clientArea.x += xOfs;
		clientArea.y += yOfs;
		if (compact && showImage && imageOverride) {
			Rectangle bounds = image.getBounds();
			int imageH = size.y - paddingImageY - paddingImageY;
			int imageW = (bounds.width * imageH) / bounds.height;

			int leftPad = (clientArea.width - (imageW + paddingImageX * 2)) / 2;

			clientArea.x += leftPad;

			imageX = clientArea.x + paddingImageX;

			gc.drawImage(image, 0, 0, bounds.width, bounds.height, imageX,
					clientArea.y + paddingImageY, imageW, imageH);
			clientArea.x += leftPad + imageW + paddingImageX * 2;
			clientArea.width -= leftPad + imageW + paddingImageX * 2;

		} else {
			clientArea.x += paddingContentX0;
			clientArea.width = clientArea.width - paddingContentX0;
			imageX = clientArea.x;
			if (showImage && image != null) {
				Rectangle bounds = image.getBounds();
				int imageH = size.y - paddingImageY - paddingImageY;
				int imageW = (bounds.width * imageH) / bounds.height;

				gc.drawImage(image, 0, 0, bounds.width, bounds.height, imageX,
						clientArea.y + paddingImageY, imageW, imageH);
				clientArea.x += imageW + paddingImageX;
				clientArea.width -= imageW - paddingImageX;
			}
		}

		gc.setForeground(colorText);
		clientArea.y += paddingContentY;
		clientArea.height -= (paddingContentY + paddingContentY);

		String text = imageOverride ? "\u200b"
				: (lastUsedName.isEmpty() ? "\u200b" : lastUsedName);

		GCStringPrinter sp = new GCStringPrinter(gc, text, clientArea, true, true,
				SWT.LEFT);
		sp.printString();

		if (focused) {

			Rectangle focusRect = sp.getCalculatedDrawRect();
			if (focusRect == null) {
				focusRect = sp.getPrintArea();
			}
			gc.setLineDash(new int[] {
				2,
				1
			});
			int y = focusRect.y + focusRect.height - 1;

			if (imageOverride) {
				Rectangle bounds = image.getBounds();
				int imageH = size.y - paddingImageY - paddingImageY;
				int imageW = (bounds.width * imageH) / bounds.height;

				gc.drawLine(imageX, y, imageX + imageW, y);

			} else {

				if (lastUsedName.isEmpty()) {
					focusRect.width = minWidth - curveWidth;
				}
				gc.drawLine(focusRect.x, y, focusRect.x + focusRect.width - 1, y);
			}

			gc.setLineStyle(SWT.LINE_SOLID);
		}
	}

	private void redrawControl() {
		if (control == null || control.isDisposed()) {
			return;
		}
		control.redraw();
	}

	private void relayoutControl(boolean redraw) {
		if (control == null || control.isDisposed()) {
			return;
		}
		try {
			control.requestLayout();
		} catch (Throwable e) {
			// old swt no support
		}
		if (redraw) {
			control.redraw();
		}
	}

	public boolean setCompact(boolean compact, boolean imageOverride) {
		if (this.compact == compact) {
			return false;
		}
		this.compact = compact;
		if (compact) {
			paddingImageX = COMPACT_PADDING_IMAGE_X;
			paddingImageY = COMPACT_PADDING_IMAGE_Y;
			paddingContentY = COMPACT_CONTENT_PADDING_Y;
			paddingContentX0 = COMPACT_CONTENT_PADDING_X0;
			paddingContentX1 = COMPACT_CONTENT_PADDING_X1;
			curveWidth = COMPACT_CURVE_WIDTH;
			showImage = imageOverride;
			imageOverridesText = imageOverride;
			if (imageOverridesText) {
				paddingImageY = 2;
			}
		} else {
			paddingImageX = DEF_PADDING_IMAGE_X;
			paddingImageY = DEF_PADDING_IMAGE_Y;
			paddingContentY = DEF_CONTENT_PADDING_Y;
			paddingContentX0 = DEF_CONTENT_PADDING_X0;
			paddingContentX1 = DEF_CONTENT_PADDING_X1;
			curveWidth = DEF_CURVE_WIDTH;
			showImage = true;
		}
		relayoutControl(false);
		return true;
	}

	public boolean setDisableAuto(boolean disableAuto) {
		if (this.disableAuto == disableAuto) {
			return false;
		}
		this.disableAuto = disableAuto;

		boolean enable;

		if (disableAuto) {

			boolean[] auto = tag.isTagAuto();

			boolean isTagAuto = auto.length >= 2 && auto[0] && auto[1];

			enable = !isTagAuto;

		} else {

			enable = true;
		}

		boolean redraw = setEnabledNoRedraw(enable);
		if (redraw) {
			redrawControl();
		}
		return redraw;
	}

	public boolean setEnableWhenNoTaggables(boolean enableWhenNoTaggables) {
		if (this.enableWhenNoTaggables == enableWhenNoTaggables) {
			return false;
		}
		this.enableWhenNoTaggables = enableWhenNoTaggables;
		if (control != null) {
			control.redraw();
		}
		return true;
	}

	public boolean setEnabled(boolean enabled) {
		if (setEnabledNoRedraw(enabled)) {
			redrawControl();
			return true;
		}
		return false;
	}

	public boolean setEnabledNoRedraw(boolean enabled) {
		boolean wasEnabled = isEnabled;
		isEnabled = enabled;
		return wasEnabled != enabled;
	}

	public boolean setGrayed(boolean b) {
		if (setGrayedNoRedraw(b)) {
			redrawControl();
			return true;
		}
		return false;
	}

	protected boolean setGrayedNoRedraw(boolean b) {
		if (b == grayed) {
			return false;
		}
		grayed = b;
		return true;
	}

	public boolean setImage(Image newImage, String key) {
		if (!ImageLoader.isRealImage(newImage)) {
			return false;
		}
		if (newImage == image && StringCompareUtils.equals(key, imageID)) {
			return false;
		}
		if (imageID != null) {
			ImageLoader.getInstance().releaseImage(imageID);
		}
		image = newImage;
		this.imageID = key;
		if ( control==null||control.isInitialised()){
			relayoutControl(true);
		}
		return true;
	}

	public boolean setMinWidth(int minWidth) {
		if (this.minWidth == minWidth) {
			return false;
		}
		this.minWidth = minWidth;
		relayoutControl(false);
		return true;
	}

	public boolean setSelected(boolean select) {
		if (setSelected(select, true)) {
			redrawControl();
			return true;
		}
		return false;
	}

	protected boolean setSelected(boolean select, boolean unGray) {
		if (select != selected) {
			selected = select;
			if (grayed && unGray) {
				grayed = false;
			}
			return true;
		}
		return false;
	}

	public boolean updateColors(Device device, Color defaultFG, Color defaultBG) {
		Color newColorTag = ColorCache.getColor(device, tag.getColor());
		if (newColorTag == null) {
			newColorTag = defaultFG;
		}
		if (newColorTag.equals(colorTag)) {
			return false;
		}
		colorTag = newColorTag;

		HSLColor hslColor = new HSLColor();
		hslColor.initHSLbyRGB(colorTag.getRed(), colorTag.getGreen(),
				colorTag.getBlue());
		hslColor.blend(defaultBG.getRed(), defaultBG.getGreen(),
				defaultBG.getBlue(), 0.75f);
		Color newColorTagFaded = ColorCache.getColor(device, hslColor.getRed(),
				hslColor.getGreen(), hslColor.getBlue());
		colorTagFaded = newColorTagFaded == null ? defaultFG : newColorTagFaded;

		needsBorderOnSelection = !Colors.isColorContrastOk(defaultBG, colorTag);
		return true;
	}

	public void updateImage() {
		String iconFile = tag.getImageFile();
		if (iconFile != null) {
			try {
				File file = new File(iconFile);

				ImageLoader.getInstance().getFileImage(file, MAX_IMAGE_SIZE,
						(image, key, returnedImmediately) -> {
							if (image == null) {
								return;
							}

							if (isDisposed()) {

								ImageLoader.getInstance().releaseImage(key);

							} else {

								Utils.execSWTThread(() -> setImage(image, key));
							}
						});
			} catch (Throwable e) {
				Debug.out(e);
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

	public boolean updateName() {
		String tagName = tag.getTagName(true);
		if (!tagName.equals(lastUsedName)) {
			lastUsedName = null;
			relayoutControl(false);
			return true;
		}
		return false;
	}

	public boolean updateState(List<Taggable> taggables) {
		if (control == null || control.isDisposed()) {
			return false;
		}
		return updateState(taggables, control.getDisplay(), control.getForeground(),
				control.getBackground());
	}

	/**
	 * @return true if something updated (and needs a redraw)
	 */
	public boolean updateState(List<Taggable> taggables, Device device,
			Color defaultFG, Color defaultBG) {
		updateImage();
		boolean needRedraw = updateName();
		needRedraw |= updateColors(device, defaultFG, defaultBG);

		if (taggables == null) {
			needRedraw |= setEnabledNoRedraw(enableWhenNoTaggables);
			if (!enableWhenNoTaggables) {
				needRedraw |= setSelected(false, false);
				if (needRedraw) {
					redrawControl();
				}
				return needRedraw;
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

		boolean newEnableState;

		if (hasTag && hasNoTag) {
			newEnableState = !auto_add;

			needRedraw |= setGrayedNoRedraw(true);
			needRedraw |= setSelected(true, false);
		} else {

			if (auto_add && auto_rem) {
				needRedraw |= setGrayedNoRedraw(!hasTag);
				newEnableState = false;
			} else {
				newEnableState = (hasTag) || (!hasTag && !auto_add);
				needRedraw |= setGrayedNoRedraw(false);
			}

			needRedraw |= setSelected(hasTag, false);
		}

		if (disableAuto) {
			needRedraw |= setEnabledNoRedraw(newEnableState);
		}

		if (needRedraw) {
			redrawControl();
		}
		return needRedraw;
	}
}
