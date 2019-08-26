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

package com.biglybt.ui.swt.views.utils;

import java.io.File;
import java.util.List;
import java.util.*;

import org.eclipse.swt.SWT;
import org.eclipse.swt.accessibility.ACC;
import org.eclipse.swt.events.*;
import org.eclipse.swt.graphics.*;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.layout.RowLayout;
import org.eclipse.swt.widgets.*;

import com.biglybt.core.tag.Tag;
import com.biglybt.core.tag.TagUtils;
import com.biglybt.core.tag.Taggable;
import com.biglybt.core.util.Constants;
import com.biglybt.core.util.Debug;
import com.biglybt.ui.swt.MenuBuildUtils;
import com.biglybt.ui.swt.Utils;
import com.biglybt.ui.swt.imageloader.ImageLoader;
import com.biglybt.ui.swt.mainwindow.Colors;
import com.biglybt.ui.swt.shells.GCStringPrinter;
import com.biglybt.ui.swt.utils.ColorCache;
import com.biglybt.util.StringCompareUtils;

/**
 * @author TuxPaper
 * @created Nov 17, 2015
 *
 */
public class TagButtonsUI
{

	private static final Point MAX_IMAGE_SIZE = new Point(40, 28);
	
	private ArrayList<TagCanvas> tagWidgets;
	private Composite cMainComposite;
	private TagButtonTrigger trigger;
	private boolean enableWhenNoTaggables;

	private boolean disableAuto = true;
	
	public void buildTagGroup(List<Tag> tags, Composite cMainComposite,
			boolean allowContextMenu, TagButtonTrigger trigger) {

		this.cMainComposite = cMainComposite;
		this.trigger = trigger;

		cMainComposite.setLayout(new GridLayout(1, false));

		tagWidgets = new ArrayList<>();

		Listener menuDetectListener = allowContextMenu ? event -> {
			final TagCanvas tagCanvas = (TagCanvas) event.widget;
			Menu menu = new Menu(tagCanvas);
			tagCanvas.setMenu(menu);

			MenuBuildUtils.addMaintenanceListenerForMenu(menu, (menu1, menuEvent) -> {
				TagUIUtils.createSideBarMenuItems(menu1, tagCanvas.getTag());
			});
		} : null;


		tags = TagUtils.sortTags(tags);
		Composite g = null;
		String group = null;
		for (Tag tag : tags) {
			String newGroup = tag.getGroup();
			if (g == null || (group != null && !group.equals(newGroup))
					|| (group == null && newGroup != null)) {
				group = newGroup;

				g = group == null ? new Composite(cMainComposite, SWT.DOUBLE_BUFFERED)
						: new Group(cMainComposite, SWT.DOUBLE_BUFFERED);
				if (group != null) {
					((Group) g).setText(group);
				}
				g.setLayoutData(new GridData(SWT.FILL, SWT.FILL, true, true));
				RowLayout rowLayout = new RowLayout();
				rowLayout.pack = true;
				rowLayout.spacing = 5;
				g.setLayout(rowLayout);
			}

			TagCanvas p = new TagCanvas(g, tag);
			GridLayout layout = new GridLayout(1, false);
			layout.marginHeight = 3;
			if (Constants.isWindows) {
				layout.marginWidth = 0;
				layout.marginLeft = 4;
				layout.marginRight = 7;
				layout.marginTop = 1;
			} else {
				layout.marginWidth = 0;
				layout.marginLeft = 3;
				layout.marginRight = 11;
			}
			p.setLayout(layout);

			if (allowContextMenu) {
				p.addListener(SWT.MenuDetect, menuDetectListener);
			}

			tagWidgets.add(p);

			Utils.setTT(p, TagUtils.getTagTooltip(tag));
			
		}
	}

	public void setSelectedTags( List<Tag> tags ){
		
		Set<Tag> tag_set = new HashSet<>( tags );
		
		for (TagCanvas widget : tagWidgets) {

			Tag tag = widget.getTag();
			if (tag == null) {
				continue;
			}
			
			boolean select = tag_set.contains( tag );

			widget.setSelected(select);
		}
	}
	
	public List<Tag>
	getSelectedTags()
	{
		List<Tag> result = new ArrayList<>();
		
		if (tagWidgets == null || tagWidgets.isEmpty()) {
			return result;
		}

		for (TagCanvas tagCanvas : tagWidgets) {
			if (tagCanvas.isSelected()){
				result.add(tagCanvas.getTag());
			}
		}

		return( result );
	}
	
	public boolean updateFields(List<Taggable> taggables) {
		if (cMainComposite.isDisposed()){
			return false;
		}
		
		for (TagCanvas tagWidget : tagWidgets) {
			tagWidget.updateState(taggables);
		}

		return false;
	}


	public void
	setDisableAuto(
		boolean	b )
	{
		disableAuto = b;
	}
	
	public void setEnableWhenNoTaggables(boolean enableWhenNoTaggables) {
		this.enableWhenNoTaggables = enableWhenNoTaggables;
	}


	public static interface TagButtonTrigger {
		public void tagButtonTriggered(Tag tag, boolean doTag);
		public Boolean tagSelectedOverride(Tag tag);
	}

	private class TagCanvas
		extends Canvas
		implements PaintListener, Listener
	{
		private static final int CURVE_WIDTH = 20;

		private static final int CONTENT_PADDING_Y0 = 4;
		
		private static final int PADDING_IMAGE_X = 5;

		private static final int CONTENT_PADDING_Y1 = 4;

		public static final int CONTENT_PADDING_X0 = 5;

		private Image image;

		private String imageID;

		private boolean selected;

		private Tag tag;

		private String lastUsedName;

		private boolean grayed;

		public TagCanvas(Composite parent, Tag tag) {
			super(parent, SWT.DOUBLE_BUFFERED);
			this.tag = tag;

			boolean[] auto = tag.isTagAuto();

			if (auto[0] && auto[1] && disableAuto) {
				setEnabled(false);
			} else {
				addListener(SWT.MouseDown, this);
				addListener(SWT.MouseUp, this);
				addListener(SWT.KeyDown, this);
				addListener(SWT.FocusOut, this);
				addListener(SWT.FocusIn, this);
				addListener(SWT.Traverse, this);
			}

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

			GCStringPrinter sp = new GCStringPrinter(gc, lastUsedName,
					new Rectangle(0, 0, 9999, 9999), false, true, SWT.LEFT);
			sp.calculateMetrics();
			Point size = sp.getCalculatedSize();
			size.x += CONTENT_PADDING_X0 + (CURVE_WIDTH / 2);
			size.y += CONTENT_PADDING_Y0 + CONTENT_PADDING_Y1;
			gc.dispose();

			if (image != null && !image.isDisposed()) {
				Rectangle bounds = image.getBounds();
				int imageW = (bounds.width * (size.y - CONTENT_PADDING_Y0 - CONTENT_PADDING_Y1)) / bounds.height;
				size.x += imageW + PADDING_IMAGE_X;
			}

			return size;
		}
		

		@Override
		public void handleEvent(Event e) {
			switch (e.type) {
				case SWT.MouseDown: {
					if (e.button != 1) {
						return;
					}
					trigger.tagButtonTriggered(tag, !isSelected());
					redraw();
					break;
				}
				case SWT.MouseUp: {
					if (e.button != 1) {
						return;
					}
					getAccessible().setFocus(ACC.CHILDID_SELF);
					break;
				}
				case SWT.FocusOut:
				case SWT.FocusIn: {
					redraw();
					break;
				}
				case SWT.Traverse: {
					switch (e.detail) {
						case SWT.TRAVERSE_PAGE_NEXT:
						case SWT.TRAVERSE_PAGE_PREVIOUS:
							e.doit = false;
							return;
						case SWT.TRAVERSE_RETURN:
							trigger.tagButtonTriggered(tag, !isSelected());
							redraw();
					}
					e.doit = true;
				}
			}
		}

		public void setImage(Image newImage, String key) {
			if (!ImageLoader.isRealImage(newImage)) {
				return;
			}
			if (newImage == image && StringCompareUtils.equals(key, imageID)) {
				return;
			}
			if (imageID != null) {
				ImageLoader.getInstance().releaseImage(imageID);
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
				e.gc.fillRoundRectangle(-CURVE_WIDTH, lineWidth - 1,
						width + CURVE_WIDTH, size.y - lineWidth, CURVE_WIDTH, CURVE_WIDTH);
				e.gc.setAlpha(0xff);
			}
			if (!selected) {
				e.gc.setAlpha(0x80);
			}
			e.gc.setLineStyle(enabled ? SWT.LINE_SOLID : SWT.LINE_DOT);
			e.gc.drawRoundRectangle(-CURVE_WIDTH, lineWidth - 1, width + CURVE_WIDTH,
					size.y - lineWidth, CURVE_WIDTH, CURVE_WIDTH);
			e.gc.drawLine(lineWidth - 1, lineWidth, lineWidth - 1,
					size.y - lineWidth);

			clientArea.x += CONTENT_PADDING_X0;
			clientArea.width = clientArea.width - CONTENT_PADDING_X0;
			if (image != null) {
				Rectangle bounds = image.getBounds();
				int imageH = size.y - CONTENT_PADDING_Y0 - CONTENT_PADDING_Y1;
				int imageW = (bounds.width * imageH) / bounds.height;

				e.gc.drawImage(image, 0, 0, bounds.width, bounds.height, clientArea.x,
						clientArea.y + CONTENT_PADDING_Y0, imageW, imageH);
				clientArea.x += imageW + PADDING_IMAGE_X;
				clientArea.width -= imageW - PADDING_IMAGE_X;
			}
			e.gc.setAlpha(grayed ? 0xA0 : 0xFF);
			e.gc.setForeground(getForeground());
			clientArea.y += CONTENT_PADDING_Y0;
			clientArea.height -= (CONTENT_PADDING_Y0 + CONTENT_PADDING_Y1);
			GCStringPrinter sp = new GCStringPrinter(e.gc, lastUsedName, clientArea, true, true,
				SWT.LEFT);
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
						e.gc.drawRectangle(printArea.x - 2, printArea.y, printArea.width + 4,
							printArea.height);
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
				ImageLoader.getInstance().releaseImage(imageID);
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

			Boolean override = trigger.tagSelectedOverride(tag);

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
	}
}
