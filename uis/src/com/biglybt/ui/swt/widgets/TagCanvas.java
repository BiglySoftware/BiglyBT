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
import org.eclipse.swt.dnd.*;
import org.eclipse.swt.events.PaintEvent;
import org.eclipse.swt.events.PaintListener;
import org.eclipse.swt.graphics.*;
import org.eclipse.swt.widgets.*;

import com.biglybt.core.category.Category;
import com.biglybt.core.download.DownloadManager;
import com.biglybt.core.tag.Tag;
import com.biglybt.core.tag.TagUtils;
import com.biglybt.core.tag.Taggable;
import com.biglybt.core.util.*;
import com.biglybt.ui.swt.TorrentUtil;
import com.biglybt.ui.swt.Utils;
import com.biglybt.ui.swt.imageloader.ImageLoader;
import com.biglybt.ui.swt.mainwindow.Colors;
import com.biglybt.ui.swt.mainwindow.HSLColor;
import com.biglybt.ui.swt.shells.GCStringPrinter;
import com.biglybt.ui.swt.utils.ColorCache;
import com.biglybt.ui.swt.utils.DragDropUtils;
import com.biglybt.ui.swt.utils.FontUtils;
import com.biglybt.ui.swt.views.utils.TagUIUtils;
import com.biglybt.util.StringCompareUtils;

public class TagCanvas
	extends Canvas
	implements PaintListener, Listener, DropTargetListener, DragSourceListener
{
	private static final Point MAX_IMAGE_SIZE = new Point(40, 28);

	public interface TagButtonTrigger
	{
		void tagButtonTriggered(TagCanvas tagCanvas, Tag tag, int stateMask,
				boolean longPress);

		Boolean tagSelectedOverride(Tag tag);
	}

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

	private static final int MIN_WIDTH = 25;

	private int paddingContentY = DEF_CONTENT_PADDING_Y;

	private int paddingContentX0 = DEF_CONTENT_PADDING_X0;

	private int paddingContentX1 = DEF_CONTENT_PADDING_X1;

	private int paddingImageX = DEF_PADDING_IMAGE_X;

	private int paddingImageY = DEF_PADDING_IMAGE_Y;

	private int curveWidth = DEF_CURVE_WIDTH;

	private final DropTarget dropTarget;

	private Image image;

	private String imageID;

	private boolean selected;

	private final Tag tag;

	private String lastUsedName;

	private boolean grayed;

	private boolean disableAuto;

	private boolean enableWhenNoTaggables;

	private TagButtonTrigger trigger;

	private boolean compact;

	private boolean showImage = true;

	private boolean imageOverridesText = false;
	
	private Font font = null;

	private Color colorTagFaded;

	private Color colorTag;

	private boolean mouseDown = false;

	private TimerEvent timerEvent;

	private boolean needsBorderOnSelection;

	private boolean isEnabled = true;

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

		boolean[] auto = tag.isTagAuto();
		
		if (auto.length >= 2 && auto[0] && auto[1]) {
			font = FontUtils.getFontWithStyle(getFont(), SWT.ITALIC, 1.0f);
			setFont(font);
		}

		updateColors();

		setDisableAuto(disableAuto);

		addListener(SWT.MouseDown, this);
		addListener(SWT.MouseUp, this);
		addListener(SWT.KeyDown, this);
		addListener(SWT.FocusOut, this);
		addListener(SWT.FocusIn, this);
		addListener(SWT.Traverse, this);
		addListener(SWT.MouseHover, this);
		addListener(SWT.MouseExit, this);

		dropTarget = new DropTarget(this,
				DND.DROP_DEFAULT | DND.DROP_MOVE | DND.DROP_COPY | DND.DROP_LINK);
		Transfer[] types = new Transfer[] {
			TextTransfer.getInstance()
		};
		dropTarget.setTransfer(types);
		dropTarget.addDropListener(this);

		DragSource dragSource = DragDropUtils.createDragSource(this,
				DND.DROP_COPY | DND.DROP_MOVE);
		dragSource.setTransfer(TextTransfer.getInstance());
		dragSource.addDragListener(this);

		updateImage();
		addPaintListener(this);
	}

	@Override
	public Point computeSize(int wHint, int hHint, boolean changed) {
		if (lastUsedName == null) {
			lastUsedName = tag.getTagName(true);
		}

		GC gc = new GC(getDisplay());
		gc.setFont(getFont());

		gc.setTextAntialias(SWT.ON);
		
		String text = imageOverridesText && image != null?"\u200b":(lastUsedName.isEmpty()?"\u200b":lastUsedName);
		
		GCStringPrinter sp = new GCStringPrinter(gc, text,	new Rectangle(0, 0, 9999, 9999), false, true, SWT.LEFT);
		sp.calculateMetrics();
		Point size = sp.getCalculatedSize();
		gc.dispose();

		if (size == null) {
			return super.computeSize(wHint, hHint, changed);
		}
		size.x += paddingContentX0 + paddingContentX1;
		size.y += paddingContentY + paddingContentY;
		
		if (showImage && image != null && !image.isDisposed()) {
			Rectangle bounds = image.getBounds();
			int imageH = size.y - paddingImageY - paddingImageY;
			int imageW = (bounds.width * imageH) / bounds.height;
			size.x += imageW + paddingImageX;
		}

		size.x = Math.max(MIN_WIDTH, size.x);

		return size;
	}

	@Override
	public void handleEvent(Event e) {
		switch (e.type) {
			case SWT.MouseDown: {
				if (!isEnabled() || e.button != 1) {
					return;
				}
				if (timerEvent == null) {
					timerEvent = SimpleTimer.addEvent("MouseHold",
							SystemTime.getOffsetTime(1000), te -> {
								timerEvent = null;
								if (!mouseDown) {
									return;
								}

								// held
								if (trigger == null) {
									mouseDown = false;
									return;
								}

								Utils.execSWTThread(() -> {
									if (!mouseDown) {
										return;
									}

									if (e.display.getCursorControl() != TagCanvas.this) {
										return;
									}

									mouseDown = false;

									trigger.tagButtonTriggered(this, tag, e.stateMask, true);
								});
							});
				}

				mouseDown = true;
				break;
			}
			case SWT.MouseUp: {
				if (!isEnabled() || e.button != 1 || !mouseDown) {
					return;
				}
				mouseDown = false;

				if (timerEvent != null) {
					timerEvent.cancel();
					timerEvent = null;
				}

				if (!getClientArea().contains(e.x, e.y)) {
					return;
				}

				if (trigger != null) {
					trigger.tagButtonTriggered(this, tag, e.stateMask, false);
				}

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
				if (!isEnabled()) {
					return;
				}
				switch (e.detail) {
					case SWT.TRAVERSE_PAGE_NEXT:
					case SWT.TRAVERSE_PAGE_PREVIOUS:
						e.doit = false;
						return;
					case SWT.TRAVERSE_RETURN:
						if (trigger != null) {
							trigger.tagButtonTriggered(this, tag, e.stateMask, false);
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
				} else if (e.keyCode == SWT.SPACE) {
					if (trigger != null) {
						trigger.tagButtonTriggered(this, tag, e.stateMask, false);
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

		boolean enable;
		
		if ( disableAuto ){
			
			boolean[] auto = tag.isTagAuto();
		
			boolean isTagAuto= auto.length >= 2 && auto[0] && auto[1];
			
			enable = !isTagAuto;
			
		}else{
			
			enable = true;
		}
		
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
		try {
			requestLayout();
		}catch( Throwable e ) {
			// old swt no support
		}
		redraw();
	}

	public Tag getTag() {
		return tag;
	}

	public boolean isSelected() {
		return selected;
	}

	public void setSelected(boolean select) {
		if (setSelected(select, true)) {
			redraw();
		}
	}

	private boolean setSelected(boolean select, boolean unGray) {
		if (select != selected) {
			selected = select;
			if (grayed && unGray) {
				grayed = false;
			}
			return true;
		}
		return false;
	}

	@Override
	public void paintControl(PaintEvent e) {
		if (lastUsedName == null) {
			return;
		}

		Rectangle clientArea = getClientArea();
		//System.out.println("paint " + lastUsedName + "; " + clientArea + "; " + e);

		boolean selected = isSelected();
		boolean focused = isFocusControl();

		GC gc = e.gc;
		
		Color colorOrigBG = gc.getBackground();

		Color colorText = Colors.getInstance().getReadableColor(
				selected ? grayed ? colorTagFaded : colorTag : colorOrigBG);

		Point size = getSize();
		gc.setAntialias(SWT.ON);
		gc.setTextAntialias(SWT.ON);

		if (selected) {
			gc.setBackground(grayed ? colorTagFaded : colorTag);
			gc.fillRoundRectangle(-curveWidth, 0, size.x + curveWidth - 1,
					size.y - 1, curveWidth, curveWidth);
		}
		
		boolean imageOverride = imageOverridesText && image != null;

		if ( !imageOverride ){
			if (!selected || grayed) {
				int lineWidth = focused ? 2 : 2;
				int y1 = lineWidth / 2;
				int x1 = y1;
				int width = size.x - lineWidth;
				int height = size.y - lineWidth;
				gc.setLineWidth(lineWidth);
				gc.setForeground(colorTag);
				gc.setLineStyle(SWT.LINE_SOLID);
	
				gc.drawRoundRectangle(-curveWidth, y1, width + curveWidth,
						height - y1 + 1, curveWidth, curveWidth);
				gc.drawLine(x1, y1, x1, height - y1 + 1);
				gc.setLineWidth(1);
			}
		}
		
		if (selected && needsBorderOnSelection) {
			gc.setLineWidth(1);
			gc.setForeground(colorText);
			gc.setAlpha(0x70);
			gc.setLineStyle(SWT.LINE_SOLID);

			gc.drawRoundRectangle(-curveWidth, 0, size.x + curveWidth - 1,
					size.y - 1, curveWidth, curveWidth);
			gc.drawLine(0, 0, 0, size.y - 1);
			gc.setAlpha(0xFF);
		}

		int imageX;
		
		if ( compact && showImage && imageOverride ){
			Rectangle bounds = image.getBounds();
			int imageH = size.y - paddingImageY - paddingImageY;
			int imageW = (bounds.width * imageH) / bounds.height;

			int leftPad = ( clientArea.width - ( imageW + paddingImageX*2 ))/2;
			
			clientArea.x += leftPad;
			
			imageX = clientArea.x + paddingImageX;
			
			gc.drawImage(image, 0, 0, bounds.width, bounds.height, imageX,
					clientArea.y + paddingImageY, imageW, imageH);
			clientArea.x += leftPad + imageW + paddingImageX*2;
			clientArea.width -= leftPad + imageW + paddingImageX*2;

		}else{
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
		
		String text = imageOverride?"\u200b":(lastUsedName.isEmpty()?"\u200b":lastUsedName);

		GCStringPrinter sp = new GCStringPrinter(gc, text, clientArea,
				true, true, SWT.LEFT);
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

			if ( imageOverride ){
				Rectangle bounds = image.getBounds();
				int imageH = size.y - paddingImageY - paddingImageY;
				int imageW = (bounds.width * imageH) / bounds.height;

				gc.drawLine(imageX, y, imageX+imageW, y);
				
			}else{
			
				if ( lastUsedName.isEmpty()){
					focusRect.width = MIN_WIDTH - curveWidth;
				}
				gc.drawLine(focusRect.x, y, focusRect.x + focusRect.width - 1, y);
			}
			
			gc.setLineStyle(SWT.LINE_SOLID);
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
		if (font != null) {
			font.dispose();
			font = null;
		}
		dropTarget.dispose();
	}

	public boolean updateName() {
		String tagName = tag.getTagName(true);
		if (!tagName.equals(lastUsedName)) {
			lastUsedName = null;
			try {
				requestLayout();
			}catch( Throwable e ) {
				// old swt no support
			}
			return true;
		}
		return false;
	}

	public void setGrayed(boolean b) {
		if (setGrayedNoRedraw(b)) {
			redraw();
		}
	}

	private boolean setGrayedNoRedraw(boolean b) {
		if (b == grayed) {
			return false;
		}
		grayed = b;
		return true;
	}

	private boolean updateColors() {
		Display display = getDisplay();
		Color newColorTag = ColorCache.getColor(display, tag.getColor());
		if (newColorTag == null) {
			newColorTag = getForeground();
		}
		if (newColorTag.equals(colorTag)) {
			return false;
		}
		colorTag = newColorTag;

		HSLColor hslColor = new HSLColor();
		hslColor.initHSLbyRGB(colorTag.getRed(), colorTag.getGreen(),
				colorTag.getBlue());
		Color colorWidgetBG = getBackground();
		hslColor.blend(colorWidgetBG.getRed(), colorWidgetBG.getGreen(),
				colorWidgetBG.getBlue(), 0.75f);
		Color newColorTagFaded = ColorCache.getColor(display, hslColor.getRed(),
				hslColor.getGreen(), hslColor.getBlue());
		colorTagFaded = newColorTagFaded == null ? getForeground()
				: newColorTagFaded;

		needsBorderOnSelection = !Colors.isColorContrastOk(colorWidgetBG, colorTag);
		return true;
	}

	public void updateState(List<Taggable> taggables) {
		boolean needRedraw = false;
		updateImage();
		needRedraw |= updateName();
		needRedraw |= updateColors();

		if (taggables == null) {
			needRedraw |= setEnabledNoRedraw(enableWhenNoTaggables);
			if (!enableWhenNoTaggables) {
				needRedraw |= setSelected(false, false);
				if (needRedraw) {
					redraw();
				}
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

		boolean	newEnableState;
		
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

		if ( disableAuto ){
			needRedraw |= setEnabledNoRedraw(newEnableState);
		}
		
		if (needRedraw) {
			redraw();
		}
	}

	@SuppressWarnings("MethodDoesntCallSuperMethod")
	@Override
	public void setEnabled(boolean enabled) {
		if (setEnabledNoRedraw(enabled)) {
			redraw();
		}
	}

	private boolean setEnabledNoRedraw(boolean enabled) {
		checkWidget();
		boolean wasEnabled = isEnabled;
		isEnabled = enabled;
		return wasEnabled != enabled;
	}

	@Override
	public boolean getEnabled() {
		return isEnabled;
	}

	@Override
	public boolean isEnabled() {
		return isEnabled && super.isEnabled();
	}

	private void updateImage() {
		String iconFile = tag.getImageFile();
		if (iconFile != null) {
			try {
				File file = new File( iconFile );
				
				ImageLoader.getInstance().getFileImage( file, MAX_IMAGE_SIZE,
						(image, key, returnedImmediately) -> {
							if (image == null) {
								return;
							}

							if ( isDisposed()){
								
								com.biglybt.ui.swt.imageloader.ImageLoader.getInstance().releaseImage( key );
								
							}else{
								
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

	public void setCompact(boolean compact, boolean imageOverride ) {
		if (this.compact == compact) {
			return;
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
			if ( imageOverridesText ){
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
		try {	
			requestLayout();
		}catch( Throwable e ) {
			// old swt no support
		}
	}

	public boolean isCompact() {
		return compact;
	}

	private static List<DownloadManager> handleDropTargetEvent(
			DropTargetEvent e) {
		Object data = e.data == null ? DragDropUtils.getLastDraggedObject()
				: e.data;
		List<DownloadManager> dms = DragDropUtils.getDownloadsFromDropData(data,
				false);

		if (dms.isEmpty()) {
			e.detail = DND.DROP_NONE;
			return dms;
		}
		boolean doAdd = false;

		Control dropControl = ((DropTarget) e.widget).getControl();
		if (!(dropControl instanceof TagCanvas)) {
			e.detail = DND.DROP_NONE;
			return dms;
		}

		Tag tag = ((TagCanvas) dropControl).getTag();
		for (DownloadManager dm : dms) {
			if (!tag.hasTaggable(dm)) {
				doAdd = true;
				break;
			}
		}

		boolean[] auto = tag.isTagAuto();
		if (auto.length < 2 || (doAdd && auto[0])
				|| (!doAdd && auto[0] && auto[1])) {
			e.detail = DND.DROP_NONE;
			return dms;
		}

		e.detail = doAdd ? DND.DROP_COPY : DND.DROP_MOVE;
		return dms;
	}

	@Override
	public void dragEnter(DropTargetEvent event) {
		handleDropTargetEvent(event);
	}

	@Override
	public void dragLeave(DropTargetEvent event) {

	}

	@Override
	public void dragOperationChanged(DropTargetEvent event) {

	}

	@Override
	public void dropAccept(DropTargetEvent event) {
		handleDropTargetEvent(event);
	}

	@Override
	public void dragOver(DropTargetEvent e) {
		handleDropTargetEvent(e);
	}

	@Override
	public void drop(DropTargetEvent e) {
		List<DownloadManager> dms = handleDropTargetEvent(e);

		if (dms.isEmpty()) {
			return;
		}

		Control dropControl = ((DropTarget) e.widget).getControl();
		if (!(dropControl instanceof TagCanvas)) {
			return;
		}

		Tag tag = ((TagCanvas) dropControl).getTag();

		if (tag instanceof Category) {
			TorrentUtil.assignToCategory(dms.toArray(), (Category) tag);
			return;
		}

		boolean doAdd = e.detail == DND.DROP_COPY;

		Utils.getOffOfSWTThread(() -> {
			// handleDropTargetEvent set e.detail based on doAdd and checked if tag
			// can be assigned/unassigned
			for (DownloadManager dm : dms) {
				if (doAdd) {
					tag.addTaggable(dm);
				} else {
					tag.removeTaggable(dm);
				}
			}
		});
	}

	@Override
	public void dragStart(DragSourceEvent event) {
		if (isDisposed()) {
			return;
		}
	}

	@Override
	public void dragSetData(DragSourceEvent event) {
		event.data = DragDropUtils.DROPDATA_PREFIX_TAG_UID + "\n" + tag.getTagUID();
	}

	@Override
	public void dragFinished(DragSourceEvent event) {

	}

	public boolean isGrayed() {
		return grayed;
	}
}
