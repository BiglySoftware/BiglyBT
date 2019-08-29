/*
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

import com.biglybt.core.util.Debug;
import com.biglybt.ui.common.viewtitleinfo.ViewTitleInfo;
import com.biglybt.ui.mdi.MdiEntryVitalityImage;
import com.biglybt.ui.swt.mainwindow.Colors;
import com.biglybt.ui.swt.shells.GCStringPrinter;
import com.biglybt.ui.swt.utils.ColorCache;
import org.eclipse.swt.SWT;
import org.eclipse.swt.custom.CTabFolder;
import org.eclipse.swt.custom.CTabFolderRenderer;
import org.eclipse.swt.custom.CTabItem;
import org.eclipse.swt.graphics.*;

import java.lang.reflect.Field;

/**
 * Created by TuxPaper on 7/16/2017.
 */
public class TabbedMDI_Ren {
	static void setupTabFolderRenderer(final TabbedMDI mdi, final CTabFolder tabFolder) {
		CTabFolderRenderer renderer = new CTabFolderRenderer(tabFolder) {
			/* (non-Javadoc)
			 * @see org.eclipse.swt.custom.CTabFolderRenderer#computeSize(int, int, org.eclipse.swt.graphics.GC, int, int)
			 */
			@Override
			protected Point computeSize(int part, int state, GC gc, int wHint,
			                            int hHint) {
				gc.setAntialias(SWT.ON);
				Point pt = super.computeSize(part, state, gc, wHint, hHint);
				if (tabFolder.isDisposed()) {
					return pt;
				}

				if (part >= 0) {
					TabbedEntry entry = mdi.getEntryFromTabItem(tabFolder.getItem(part));
					if (entry != null) {
						ViewTitleInfo viewTitleInfo = entry.getViewTitleInfo();
						if (viewTitleInfo != null) {
							Object titleRight = viewTitleInfo.getTitleInfoProperty(ViewTitleInfo.TITLE_INDICATOR_TEXT);
							if (titleRight != null) {
								Point size = gc.textExtent(titleRight.toString(), 0);
								pt.x += size.x + 10 + 2;
							}
						}


						MdiEntryVitalityImage[] vitalityImages = entry.getVitalityImages();
						com.biglybt.ui.swt.imageloader.ImageLoader imageLoader = com.biglybt.ui.swt.imageloader.ImageLoader.getInstance();
						for (MdiEntryVitalityImage mdiEntryVitalityImage : vitalityImages) {
							if (mdiEntryVitalityImage != null && mdiEntryVitalityImage.isVisible()) {
								String imageID = mdiEntryVitalityImage.getImageID();
								Image image = imageLoader.getImage(imageID);
								if (com.biglybt.ui.swt.imageloader.ImageLoader.isRealImage(image)) {
									pt.x += image.getBounds().x + 1;
								}
							}

						}
					}
				}
				return pt;
			}

			/* (non-Javadoc)
			 * @see org.eclipse.swt.custom.CTabFolderRenderer#draw(int, int, org.eclipse.swt.graphics.Rectangle, org.eclipse.swt.graphics.GC)
			 */
			@Override
			protected void draw(int part, int state, Rectangle bounds, GC parent_gc) {
				try {
					//super.draw(part, state & ~(SWT.FOREGROUND), bounds, gc);
					super.draw(part, state, bounds, parent_gc);
				} catch (Throwable t) {
					Debug.out(t);
				}
				if (part < 0) {
					return;
				}
				try {
					CTabItem item = mdi.getTabFolder().getItem(part);
					TabbedEntry entry = mdi.getEntryFromTabItem(item);
					if (entry == null) {
						return;
					}

					ViewTitleInfo viewTitleInfo = entry.getViewTitleInfo();
					if (viewTitleInfo != null) {
						Object titleRight = viewTitleInfo.getTitleInfoProperty(ViewTitleInfo.TITLE_INDICATOR_TEXT);
						if (titleRight != null) {
							String textIndicator = titleRight.toString();
							int x1IndicatorOfs = 0;
							int SIDEBAR_SPACING = 0;
							int x2 = bounds.x + bounds.width;

							if (item.getShowClose()) {
								try {
									Field fldCloseRect = item.getClass().getDeclaredField("closeRect");
									fldCloseRect.setAccessible(true);
									Rectangle closeBounds = (Rectangle) fldCloseRect.get(item);
									if (closeBounds != null && closeBounds.x > 0) {
										x2 = closeBounds.x;
									}
								} catch (Exception e) {
									x2 -= 20;
								}
							}
							//gc.setAntialias(SWT.ON); FAIL ON WINDOWS 7 AT LEAST

							Point textSize = parent_gc.textExtent(textIndicator);
							//Point minTextSize = gc.textExtent("99");
							//if (textSize.x < minTextSize.x + 2) {
							//	textSize.x = minTextSize.x + 2;
							//}

							int width = textSize.x + 10;
							x1IndicatorOfs += width + SIDEBAR_SPACING;
							int startX = x2 - x1IndicatorOfs;

							int textOffsetY = 0;

							int height = textSize.y + 1;
							int startY = bounds.y + ((bounds.height - height) / 2) + 1;

							Image image = new Image( parent_gc.getDevice(), width+1, height+1 );
							
							
							GC gc = new GC( image );
							
							gc.setAdvanced( true );
							
							gc.setAntialias( SWT.ON );
							
							gc.setBackground( mdi.getTabFolder().getBackground());
							gc.fillRectangle( new Rectangle( 0, 0, width+1, height+1 ));
							//gc.setBackground(((state & SWT.SELECTED) != 0 ) ? item.getParent().getSelectionBackground() : item.getParent().getBackground());
							//gc.fillRectangle(startX - 5, startY, width + 5, height);

							//Pattern pattern;
							//Color color1;
							//Color color2;

							//gc.fillRectangle(startX, startY, width, height);


							Color default_color = ColorCache.getSchemedColor(gc.getDevice(), "#5b6e87");

							Object color =  viewTitleInfo.getTitleInfoProperty(ViewTitleInfo.TITLE_INDICATOR_COLOR);

							if ( color instanceof int[] ){

								gc.setBackground(ColorCache.getColor( gc.getDevice(),(int[])color ));

							}else{

								gc.setBackground( default_color );
							}


							Color text_color = Colors.white;

							gc.fillRoundRectangle(0, 0, width, height, textSize.y * 2 / 3,
									height * 2 / 3);

							if ( color != null ){

								text_color = Colors.getInstance().getReadableColor(gc.getBackground());

								gc.setBackground( default_color );

								gc.drawRoundRectangle(0, 0, width, height, textSize.y * 2 / 3,
										height * 2 / 3);
							}
							gc.setForeground(text_color);
							
							GCStringPrinter.printString(gc, textIndicator, new Rectangle(0,
									0 + textOffsetY, width, height), true, false, SWT.CENTER);

							gc.dispose();
							
							parent_gc.drawImage( image, startX, startY );
							
							image.dispose();
						}
					}

				} catch (Throwable t) {
					Debug.out(t);
				}
			}
		};
		tabFolder.setRenderer(renderer);
	}
}
