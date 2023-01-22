/*
 * Copyright (C) Bigly Software, Inc, All Rights Reserved.
 *
 * This program is free software; you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation; either version 2 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program; if not, write to the Free Software
 * Foundation, Inc., 59 Temple Place, Suite 330, Boston, MA 02111-1307  USA
 */

package com.biglybt.ui.swt.widgets;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Collections;
import java.util.List;

import org.eclipse.swt.SWT;
import org.eclipse.swt.custom.CTabFolder;
import org.eclipse.swt.custom.CTabFolderRenderer;
import org.eclipse.swt.custom.CTabItem;
import org.eclipse.swt.graphics.*;
import org.eclipse.swt.widgets.Display;
import org.eclipse.swt.widgets.Event;

import com.biglybt.core.util.Constants;
import com.biglybt.core.util.Debug;
import com.biglybt.ui.common.viewtitleinfo.ViewTitleInfo;
import com.biglybt.ui.swt.Utils;
import com.biglybt.ui.swt.imageloader.ImageLoader;
import com.biglybt.ui.swt.mainwindow.Colors;
import com.biglybt.ui.swt.shells.GCStringPrinter;
import com.biglybt.ui.swt.utils.ColorCache;


public class TabFolderRenderer
	extends CTabFolderRenderer
{
	private static final int PADDING_BUBBLE_X = 5;

	private static final int PADDING_INDICATOR_X1 = 5;

	private static final int PADDING_INDICATOR_AND_CLOSE = 5;

	private final CTabFolder tabFolder;

	private final Adapter	provider;
	
	public 
	TabFolderRenderer(
		CTabFolder 	_tabFolder, 
		Adapter 	_provider) 
	{
		super(_tabFolder);
		
		tabFolder = _tabFolder;
		
		provider = _provider;
		
		tabFolder.setRenderer( this );
	}
	
	@Override
	protected Point 
	computeSize(
		int part, int state, GC gc, int wHint, int hHint ) 
	{
		gc.setAntialias(SWT.ON);
		Point pt = super.computeSize(part, state, gc, wHint, hHint);
		if (tabFolder.isDisposed()) {
			return pt;
		}

		if (part < 0 || part >= tabFolder.getItemCount()) {
			return pt;
		}

		CTabItem item = tabFolder.getItem(part);
		if (item == null) {
			return pt;
		}

		TabbedEntry entry = provider.getTabbedEntryFromTabItem(item);
		
		if (entry == null) {
			return pt;
		}

		int ourWidth = 0;

		ViewTitleInfo viewTitleInfo = entry.getTabbedEntryViewTitleInfo();
		if (viewTitleInfo != null) {
			Object titleRight = viewTitleInfo.getTitleInfoProperty(
					ViewTitleInfo.TITLE_INDICATOR_TEXT);
			if (titleRight != null) {
				String textIndicator = titleRight.toString();
				item.setData("textIndicator", textIndicator);
				Point size = gc.textExtent(textIndicator, 0);
				ourWidth += size.x + (PADDING_BUBBLE_X * 2) + PADDING_INDICATOR_X1;
			} else {
				item.setData("textIndicator", null);
			}
		} else {
			item.setData("textIndicator", null);
		}

		boolean showUnselectedClose = tabFolder.getUnselectedCloseVisible();
		boolean selected = (state & SWT.SELECTED) != 0;
		boolean parentHasClose = (tabFolder.getStyle() & SWT.CLOSE) != 0;
		boolean showingClose = (parentHasClose || item.getShowClose())
				&& (showUnselectedClose || selected);

		List<TabbedEntryVitalityImage> vitalityImages = entry.getTabbedEntryVitalityImages();
		
		boolean first = true;
		for (TabbedEntryVitalityImage vitalityImage : vitalityImages) {
			if (vitalityImage == null || !vitalityImage.isVisible()
					|| vitalityImage.getAlignment() != SWT.RIGHT
					|| vitalityImage.getShowOutsideOfEntry()) {
				continue;
			}
			
			if (!selected && vitalityImage.getShowOnlyOnSelection()) {
				continue;
			}

			Image image = vitalityImage.getImage();
			if (com.biglybt.ui.swt.imageloader.ImageLoader.isRealImage(image)) {
				ourWidth += image.getBounds().width;
				if (first && !vitalityImage.getAlwaysLast()) {
					first = false;
				}
				ourWidth += PADDING_INDICATOR_X1;
			}
		}
		
		if (!selected) {
			ourWidth += PADDING_INDICATOR_AND_CLOSE;
		}

		pt.x += ourWidth;

		return pt;
	}

	@Override
	protected Rectangle computeTrim(int part, int state, int x, int y, int width,
			int height) {
		Rectangle trim = super.computeTrim(part, state, x, y, width, height);
		if (part < 0 || part >= tabFolder.getItemCount()) {
			return trim;
		}

		CTabItem item = tabFolder.getItem(part);
		if (item != null && item.getImage() == null) {
			trim.x -= PADDING_INDICATOR_AND_CLOSE;
			trim.width += PADDING_INDICATOR_AND_CLOSE;
		}
		return trim;
	}

	@Override
	protected void draw(int part, int state, Rectangle bounds, GC gc) {
		if (part < 0 || part >= tabFolder.getItemCount()) {
			try {
				//super.draw(part, state & ~(SWT.FOREGROUND), bounds, gc);
				super.draw(part, state, bounds, gc);
			} catch (Throwable t) {
				Debug.out(t);
			}
			return;
		}
		try {
			super.draw(part, state & ~(SWT.FOREGROUND), bounds, gc);
			//super.draw(part, state, bounds, gc);
		} catch (Throwable t) {
			Debug.out(t);
		}

		if (bounds.width == 0 || bounds.height == 0) {
			return;
		}

		try {
			CTabItem item = tabFolder.getItem(part);
			if (item == null) {
				return;
			}

			TabbedEntry entry_maybe_null = provider.getTabbedEntryFromTabItem(item);

			int x2 = bounds.x + bounds.width;
			boolean showUnselectedClose = tabFolder.getUnselectedCloseVisible();
			boolean selected = (state & SWT.SELECTED) != 0;
			boolean parentHasClose = (tabFolder.getStyle() & SWT.CLOSE) != 0;
			boolean showingClose = (parentHasClose || item.getShowClose())
					&& (showUnselectedClose || selected);

			Rectangle closeRect = null;
			int closeImageState = 0;
			if (showingClose) {
				try {
					Field fldCloseRect = CTabItem.class.getDeclaredField("closeRect");
					fldCloseRect.setAccessible(true);
					closeRect = (Rectangle) fldCloseRect.get(item);
					if (item.getShowClose() && closeRect != null && closeRect.x > 0) {
						x2 = closeRect.x;
					}

					Field fldCloseImageState = CTabItem.class.getDeclaredField(
							"closeImageState");
					fldCloseImageState.setAccessible(true);
					closeImageState = (int) fldCloseImageState.get(item);

				} catch (Throwable t) {
					x2 -= 20;
				}
			} else {
				x2 -= PADDING_INDICATOR_AND_CLOSE;
			}

			int oldAntiAlias = gc.getAntialias();
			boolean oldAdvanced = gc.getAdvanced();
			try {
				gc.setAdvanced(true);
				gc.setAntialias(SWT.ON);

				if ( entry_maybe_null != null ){
					List<TabbedEntryVitalityImage> vitalityImages = entry_maybe_null.getTabbedEntryVitalityImages();
					Collections.reverse(vitalityImages);
					boolean first = true;
					for (TabbedEntryVitalityImage vitalityImage :  vitalityImages) {
						if (vitalityImage == null || !vitalityImage.isVisible()
								|| vitalityImage.getAlignment() != SWT.RIGHT
								|| vitalityImage.getShowOutsideOfEntry()) {
							continue;
						}
	
	
						if (!selected  && vitalityImage.getShowOnlyOnSelection()) {
							vitalityImage.setHitArea(null);
							continue;
						}
	
						vitalityImage.switchSuffix(entry_maybe_null.isTabbedEntryActive() ? "-selected" : "");
						Image image = vitalityImage.getImage();
						if (image == null || image.isDisposed()) {
							continue;
						}
	
						Rectangle imageBounds = image.getBounds();
						int startX = x2 - imageBounds.width;
						int startY = bounds.y + ((bounds.height - imageBounds.height) / 2)
								+ 1;
						if (first && !vitalityImage.getAlwaysLast()) {
							//startX -= PADDING_INDICATOR_AND_CLOSE;
							first = false;
						}
						gc.drawImage(image, startX, startY);
						vitalityImage.setHitArea(new Rectangle(startX, startY,
								imageBounds.width, imageBounds.height));
	
						x2 = startX;
						x2 -= PADDING_INDICATOR_X1;
					}					
				
					ViewTitleInfo viewTitleInfo = entry_maybe_null.getTabbedEntryViewTitleInfo();
	
					if (viewTitleInfo != null) {
	
						String textIndicator = (String) item.getData("textIndicator");
						if (textIndicator != null) {
	
							Point textSize = gc.textExtent("" + textIndicator, 0);
							//Point minTextSize = gc.textExtent("99");
							//if (textSize.x < minTextSize.x + 2) {
							//	textSize.x = minTextSize.x + 2;
							//}
	
							int width = textSize.x + (PADDING_BUBBLE_X * 2)
									+ PADDING_INDICATOR_X1;
							int startX = x2 - width;
							if (first) {
								startX -= PADDING_INDICATOR_AND_CLOSE;
								first = false;
							}
	
							int textOffsetY = 0;
	
							int height = textSize.y + 1;
							int startY = bounds.y + ((bounds.height - height) / 2) + 1;
	
							Color default_color = ColorCache.getSchemedColor(gc.getDevice(),
									"#5b6e87");
	
							Object color = viewTitleInfo.getTitleInfoProperty(
									ViewTitleInfo.TITLE_INDICATOR_COLOR);
	
							if (color instanceof int[]) {
	
								gc.setBackground(
										ColorCache.getColor(gc.getDevice(), (int[]) color));
	
							} else {
	
								gc.setBackground(default_color);
							}
	
							Color text_color = Colors.white;
	
							int bubbleStartX = startX + PADDING_INDICATOR_X1;
							int bubbleStartY = startY;
							int bubbleWidth = width - PADDING_INDICATOR_X1;
	
							gc.fillRoundRectangle(bubbleStartX, bubbleStartY, bubbleWidth,
									height, textSize.y * 2 / 3, height * 2 / 3);
	
							if (color != null) {
	
								text_color = Colors.getInstance().getReadableColor(
										gc.getBackground());
	
								gc.setBackground(default_color);
	
								gc.drawRoundRectangle(bubbleStartX, bubbleStartY, bubbleWidth,
										height, textSize.y * 2 / 3, height * 2 / 3);
							}
							gc.setForeground(text_color);
	
							GCStringPrinter.printString(
									gc, textIndicator, new Rectangle(bubbleStartX,
											bubbleStartY + textOffsetY, bubbleWidth, height),
									true, false, SWT.CENTER);
	
							x2 = startX;
						}
					}
				}

				/////////////////
				if ((state & SWT.FOREGROUND) != 0) {

					// draw Image
					Rectangle trim = computeTrim(part, SWT.NONE, 0, 0, 0, 0);
					int xDraw = bounds.x - trim.x;
					Image image = item.getImage();
					if (image != null && !image.isDisposed()
							&& tabFolder.getUnselectedImageVisible()) {
						Rectangle imageBounds = image.getBounds();
						// only draw image if it won't overlap with close button
						int maxImageWidth = bounds.x + bounds.width - xDraw
								- (trim.width + trim.x);
						if (showingClose && closeRect != null) {
							maxImageWidth -= closeRect.width + 4; //INTERNAL_SPACING;
						}
						if (imageBounds.width < maxImageWidth) {
							int imageX = xDraw;
							int imageHeight = imageBounds.height;
							int imageY = bounds.y + (bounds.height - imageHeight) / 2;
							boolean onBottom = (tabFolder.getStyle() & SWT.BOTTOM) != 0;
							imageY += onBottom ? -1 : 1;
							int imageWidth = imageBounds.width * imageHeight
									/ imageBounds.height;
							gc.drawImage(image, imageBounds.x, imageBounds.y,
									imageBounds.width, imageBounds.height, imageX, imageY,
									imageWidth, imageHeight);
							xDraw += imageWidth + 4; //INTERNAL_SPACING;
						}
					}
					
						// draw close
					
					if (showingClose) {
						
							// update - removed this hack for Linux after seeing hang :(
						
						if ( closeImageState == 0 && Utils.isDarkAppearanceNative() && Constants.isOSX ){
														
								// OSX + Linux paint an almost black cross on a black background
								// hack to take whatever the OS paints and lighten it
							
							Image img = new Image( gc.getDevice(), closeRect );
							
							GC gcImg = new GC( img );
							
							gcImg.setBackground(Colors.black);
							gcImg.fillRectangle(0,0,closeRect.width,closeRect.height);
							
							try {
								Method methDrawClose = CTabFolderRenderer.class.getDeclaredMethod(
										"drawClose", GC.class, Rectangle.class, int.class);
								methDrawClose.setAccessible(true);
								methDrawClose.invoke(this, gcImg, new Rectangle(0,0,closeRect.width,closeRect.height), closeImageState);
							} catch (Throwable t) {
								t.printStackTrace();
							}
							
							ImageData idata = img.getImageData();	// linux hang here :(
							PaletteData	pdata = idata.palette;
							
							int redMask 	= pdata.redMask;
							int greenMask 	= pdata.greenMask;
							int blueMask 	= pdata.blueMask;
							int redShift 	= pdata.redShift;
							int greenShift 	= pdata.greenShift;
							int blueShift 	= pdata.blueShift;
																				
							for ( int i=0;i<closeRect.width;i++){
								for ( int j=0;j<closeRect.height;j++){
									int pixel = idata.getPixel(i,j);
																	
									int red = pixel & redMask;
									red = (redShift < 0) ? (red >>> -redShift ): ( red << redShift );
									int green = pixel & greenMask;
									green = (greenShift < 0) ? ( green >>> -greenShift ) : ( green << greenShift );
									int blue = pixel & blueMask;
									blue = (blueShift < 0) ? ( blue >>> -blueShift ) : ( blue << blueShift );

									int rgb = (red<<16)|(green<<8)|blue;
									
									if ( rgb != 0x000000 ){
										Color c = new Color(gc.getDevice(),new RGB(red+75,green+75,blue+75));
										gc.setForeground(c);
										gc.drawPoint(closeRect.x+i,closeRect.y+j);
										c.dispose();
									}
								}
							}							
							
							gcImg.dispose();
							img.dispose();

						}else if ( closeImageState == 0 && Utils.isDarkAppearanceNative() && Constants.isLinux ){
							
							ImageLoader imageLoader = ImageLoader.getInstance();
							
							Image img = imageLoader.getImage( "image.tabfolder.close.up._dark" );
							
							Rectangle b = img.getBounds();
							
							gc.drawImage(img, closeRect.x+(closeRect.width-b.width)/2, closeRect.y+1+(closeRect.height-b.height)/2 );
							
							imageLoader.releaseImage( "image.tabfolder.close.up._dark" );
							
						}else{
							try {
								Method methDrawClose = CTabFolderRenderer.class.getDeclaredMethod(
										"drawClose", GC.class, Rectangle.class, int.class);
								methDrawClose.setAccessible(true);
								methDrawClose.invoke(this, gc, closeRect, closeImageState);
							} catch (Throwable t) {
								t.printStackTrace();
							}
						}
					}

					// Draw Text
					Rectangle printArea = new Rectangle(xDraw, bounds.y + 1,
							x2 - xDraw + 1, bounds.height);

					gc.setForeground(selected ? tabFolder.getSelectionForeground()
							: tabFolder.getForeground());

					Font gcFont = gc.getFont();
					gc.setFont(
							item.getFont() == null ? tabFolder.getFont() : item.getFont());
					GCStringPrinter sp = new GCStringPrinter(gc, item.getText(),
							printArea, false, true, 0);
					sp.printString();
					gc.setFont(gcFont);

					// draw a Focus rectangle
					if (tabFolder.isFocusControl() && selected) {
						Display display = tabFolder.getDisplay();
						if (tabFolder.getSimple() || tabFolder.getSingle()) {
							Rectangle drawRect = sp.getCalculatedDrawRect();
							if (drawRect != null) {
								gc.setBackground(Colors.getSystemColor( display, SWT.COLOR_BLACK));
								gc.setForeground(Colors.getSystemColor( display, SWT.COLOR_WHITE));
								gc.drawFocus(drawRect.x - 1, drawRect.y - 1, drawRect.width + 2,
										drawRect.height + 2);
							}
						} else {
							gc.setForeground(
									display.getSystemColor(SWT.COLOR_WIDGET_DARK_SHADOW));
							int lineY = tabFolder.getTabHeight();
							int lineX2 = xDraw + sp.getCalculatedSize().x + 1;
							gc.drawLine(xDraw, lineY, lineX2, lineY);
						}
					}
				}

			} finally {
				gc.setAntialias(oldAntiAlias);
				gc.setAdvanced(oldAdvanced);
			}
		} catch (Throwable t) {
			Debug.out(t);
		}
	}
	
	public void
	redraw(
		TabbedEntry		entry )
	{
		redraw( entry.getTabbedEntryItem());
	}
	
	public void
	redraw(
		int	index )
	{
		CTabItem[] items = tabFolder.getItems();
		
		if ( index > 0 && index < items.length ){
			
			redraw( items[index] );
		}
	}
	
	public void
	redraw(
		CTabItem		item )
	{
		Utils.execSWTThread(() -> {
			
			if (item == null || item.isDisposed()) {
				return;
			}

			Rectangle bounds = item.getBounds();
			GC gc = new GC(parent);
			Point point = computeSize(parent.indexOf(item), parent.getSelection() == item ? SWT.SELECTED : SWT.NONE, gc, SWT.DEFAULT, SWT.DEFAULT);
			gc.dispose();
			if (point.x != bounds.width) {
				// width of the tab changed (text changed or image added/removed),
				// tell folder to recalculate tab positions by faking Resize event
				parent.notifyListeners(SWT.Resize, new Event());
			} else {
				// Same size, but maybe the image got updated or text is different and 
				// just happens to be the same size
				parent.redraw(bounds.x, bounds.y, bounds.width, bounds.height, true);
			}
		});
	}
	public interface
	TabbedEntry
	{
		public CTabItem
		getTabbedEntryItem();
		
		public ViewTitleInfo 
		getTabbedEntryViewTitleInfo();
		
		public List<TabbedEntryVitalityImage> 
		getTabbedEntryVitalityImages();
		
		public boolean
		isTabbedEntryActive();
	}
	
	public interface
	TabbedEntryVitalityImage
	{
		public boolean 
		isVisible();
		
		public Image 
		getImage();
		
		public void 
		setHitArea(
			Rectangle hitArea );
		
		public void
		switchSuffix(
			String	suffix );
		
		public int
		getAlignment();
		
		public boolean 
		getShowOnlyOnSelection();
		
		public boolean 
		getAlwaysLast();
		
		public boolean 
		getShowOutsideOfEntry();
	}
	
	public interface
	Adapter
	{
		public TabbedEntry
		getTabbedEntryFromTabItem(
			CTabItem	item );
	}
}
