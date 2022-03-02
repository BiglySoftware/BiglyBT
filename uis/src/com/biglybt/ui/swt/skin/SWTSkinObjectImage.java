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

package com.biglybt.ui.swt.skin;

import org.eclipse.swt.SWT;
import org.eclipse.swt.events.*;
import org.eclipse.swt.graphics.*;
import org.eclipse.swt.layout.FormData;
import org.eclipse.swt.widgets.*;

import com.biglybt.core.util.*;
import com.biglybt.ui.swt.Utils;

import com.biglybt.ui.swt.imageloader.ImageLoader;
import com.biglybt.ui.swt.imageloader.ImageLoader.ImageDownloaderListener;

/**
 * @author TuxPaper
 * @created Jun 12, 2006
 *
 */
public class SWTSkinObjectImage
	extends SWTSkinObjectBasic
{
	protected static final Long DRAW_SCALE = new Long(1);

	protected static final Long DRAW_SCALEDOWN_OR_CENTER = new Long(8);

	protected static final Long DRAW_STRETCH = new Long(2);

	protected static final Long DRAW_NORMAL = new Long(0);

	protected static final Long DRAW_LEFT = new Long(7);

	protected static final Long DRAW_TILE = new Long(3);

	protected static final Long DRAW_CENTER = new Long(4);

	protected static final Long DRAW_HCENTER = new Long(5);

	protected static final Long DRAW_ANIMATE = new Long(6);

	private Canvas canvas;

	private boolean customImage;

	private String customImageID;

	private String currentImageID;

	private static PaintListener paintListener;

	private int h_align;

	private int drawAlpha = 255;

	static {
		paintListener = new PaintListener() {
			@Override
			public void paintControl(PaintEvent e) {
				//SWTSkinObject so = (SWTSkinObject) e.widget.getData("SkinObject");
				try {
					e.gc.setAdvanced(true);
					e.gc.setInterpolation(SWT.HIGH);
				} catch (Exception ex) {
				}

				final Canvas control = (Canvas) e.widget;
				
				Image bgImage = (Image)control.getParent().getData( "BGImage" );
				
				if ( bgImage != null && !bgImage.isDisposed()){
										
					e.gc.drawImage( bgImage, 0, 0 );
					
					control.getParent().setData( "BGImageOld", bgImage );
				}
				
				Image imgSrc = (Image) control.getData("image");

				Integer drawAlpha = (Integer) control.getData("drawAlpha");
				if (drawAlpha != null) {
					e.gc.setAlpha(drawAlpha);
				}

				//Long hpadding_obj = (Long) control.getData("hpadding");
				//int hpadding = hpadding_obj == null ? 0 : hpadding_obj.intValue();

				Long drawMode = (Long) control.getData("drawmode");
				if (drawMode == DRAW_ANIMATE) {
					Image[] images = (Image[]) control.getData("images");
					if (images != null) {
  					int idx = ((Number) control.getData("ImageIndex")).intValue();
  					imgSrc = images[idx];
					}
				}

				Image imgRight = null;
				Image imgLeft = null;
				String idToRelease = null;
				ImageLoader imageLoader = null;

				if (imgSrc == null || imgSrc.isDisposed()) {
					SWTSkinObjectImage soImage = (SWTSkinObjectImage) control.getData("SkinObject");
					imageLoader = soImage.getSkin().getImageLoader(
							soImage.getProperties());
					String imageID = (String) control.getData("ImageID");
					if (imageLoader.imageExists(imageID)) {
						idToRelease = imageID;
						Image[] images = imageLoader.getImages(imageID);
						if (images.length == 3) {
							imgLeft = images[0];
							imgSrc = images[1];
							imgRight = images[2];
						} else {
							imgSrc = images[0];
						}
					} else {
						return;
					}
				}
				Rectangle imgSrcBounds = imgSrc.getBounds();
				Point size = control.getSize();

				if (drawMode == DRAW_SCALEDOWN_OR_CENTER) {
					if (size.x < imgSrcBounds.width || size.y < imgSrcBounds.height) {
						drawMode = DRAW_SCALE;
					} else {
						drawMode = DRAW_CENTER;
					}
				}

				if (drawMode == DRAW_STRETCH) {
					e.gc.drawImage(imgSrc, 0, 0, imgSrcBounds.width, imgSrcBounds.height,
							0, 0, size.x, size.y);
				} else if (drawMode == DRAW_LEFT) {
					e.gc.drawImage(imgSrc, 0, 0);
				} else if (drawMode == DRAW_NORMAL || drawMode == DRAW_CENTER
						|| drawMode == DRAW_ANIMATE) {
					if ((control.getStyle() & SWT.RIGHT) != 0) {
  					e.gc.drawImage(imgSrc, size.x - imgSrcBounds.width,
  							(size.y - imgSrcBounds.height) / 2);
					} else {
  					e.gc.drawImage(imgSrc, (size.x - imgSrcBounds.width) / 2,
  							(size.y - imgSrcBounds.height) / 2);
					}
				} else if (drawMode == DRAW_HCENTER) {
					e.gc.drawImage(imgSrc, (size.x - imgSrcBounds.width) / 2, 0);
				} else if (drawMode == DRAW_SCALE) {
					float dx = (float) size.x / imgSrcBounds.width;
					float dy = (float) size.y / imgSrcBounds.height ;
					float d = Math.min(dx, dy);
					int newX = (int) (imgSrcBounds.width * d);
					int newY = (int) (imgSrcBounds.height * d);

					e.gc.drawImage(imgSrc, 0, 0, imgSrcBounds.width, imgSrcBounds.height,
							(size.x - newX) / 2, (size.y - newY) / 2, newX, newY);
				} else {
					int x0 = 0;
					int y0 = 0;
					int x1 = size.x;
					int y1 = size.y;

					if (imgRight == null) {
						imgRight = (Image) control.getData("image-right");
					}
					if (imgRight != null) {
						int width = imgRight.getBounds().width;

						x1 -= width;
					}

					if (imgLeft == null) {
						imgLeft = (Image) control.getData("image-left");
					}
					if (imgLeft != null) {
						// TODO: Tile down
						e.gc.drawImage(imgLeft, 0, 0);

						x0 += imgLeft.getBounds().width;
					}

					for (int y = y0; y < y1; y += imgSrcBounds.height) {
						for (int x = x0; x < x1; x += imgSrcBounds.width) {
							e.gc.drawImage(imgSrc, x, y);
						}
					}

					if (imgRight != null) {
						// TODO: Tile down
						e.gc.drawImage(imgRight, x1, 0);
					}
				}
				if (idToRelease != null && imageLoader != null) {
					imageLoader.releaseImage(idToRelease);
				}
			}
		};
	}

	/**
	 * @param skin
	 *
	 */
	public SWTSkinObjectImage(SWTSkin skin, SWTSkinProperties skinProperties,
			String sID, String sConfigID, SWTSkinObject parent) {
		super(skin, skinProperties, sID, sConfigID, "image", parent);
		customImage = false;
		customImageID = null;
		setControl(createImageWidget(sConfigID));
	}

	private Canvas createImageWidget(String sConfigID) {
		String propImageID = properties.getStringValue(sConfigID + ".imageid");
		if (propImageID != null) {
			currentImageID = customImageID = propImageID;
		}
		int style = SWT.WRAP | SWT.DOUBLE_BUFFERED;

		String sAlign = properties.getStringValue(sConfigID + ".align");
		if (sAlign != null && !Constants.isUnix) {
			h_align = SWTSkinUtils.getAlignment(sAlign, SWT.NONE);
			if (h_align != SWT.NONE) {
				style |= h_align;
			}
		}

		if (properties.getIntValue(sConfigID + ".border", 0) == 1) {
			style |= SWT.BORDER;
		}

		Composite createOn;
		if (parent == null) {
			createOn = skin.getShell();
		} else {
			createOn = (Composite) parent.getControl();
		}

		canvas = new Canvas(createOn, style);
		canvas.setData("SkinObject", this);

//		 {
//				public Point computeSize(int wHint, int hHint) {
//					Object image = canvas.getData("image");
//					Object imageID = canvas.getData("ImageID");
//					if (image == null
//							&& (imageID == null || ((String) imageID).length() == 0)) {
//						return new Point(0, 0);
//					}
//					return super.computeSize(wHint, hHint);
//				};
//
//				public Point computeSize(int wHint, int hHint, boolean changed) {
//					Object image = canvas.getData("image");
//					Object imageID = canvas.getData("ImageID");
//					if (image == null
//							&& (imageID == null || ((String) imageID).length() == 0)) {
//						return new Point(0, 0);
//					}
//					return super.computeSize(wHint, hHint, changed);
//				};
//			};

		Color color = properties.getColor(sConfigID + ".color");
		if (color != null) {
			canvas.setBackground(color);
		}

		final String sURL = properties.getStringValue(sConfigID + ".url");
		if (sURL != null && sURL.length() > 0) {
			Utils.setTT(canvas,sURL);
			canvas.addListener(SWT.MouseUp, new Listener() {
				@Override
				public void handleEvent(Event arg0) {
					Utils.launch(UrlUtils.encode(sURL));
				}
			});
		}

		String sCursor = properties.getStringValue(sConfigID + ".cursor");
		if (sCursor != null && sCursor.length() > 0) {
			if (sCursor.equalsIgnoreCase("hand")) {
				canvas.addListener(SWT.MouseEnter,
						skin.getHandCursorListener(canvas.getDisplay()));
				canvas.addListener(SWT.MouseExit,
						skin.getHandCursorListener(canvas.getDisplay()));
			}
		}

		//		SWTBGImagePainter painter = (SWTBGImagePainter) parent.getData("BGPainter");
		//		if (painter != null) {
		//			canvas.addListener(SWT.Paint, painter);
		//		}
		canvas.addDisposeListener(new DisposeListener() {
			@Override
			public void widgetDisposed(DisposeEvent e) {
				String oldImageID = (String) canvas.getData("ImageID");
				if (oldImageID != null && canvas.getData("image") != null) {
					ImageLoader imageLoader = skin.getImageLoader(properties);
					imageLoader.releaseImage(oldImageID);
				}
			}
		});

		// needed to set paint listener and canvas size
		swt_reallySetImage();

		return canvas;
	}

	@Override
	public void setVisible(boolean visible) {
		super.setVisible(visible);

		if (visible) {
			Utils.execSWTThread(new AERunnable() {
				@Override
				public void runSupport() {
					swt_reallySetImage();
				}
			});
		}
	}

	//protected void setCanvasImage(String sConfigID, AECallback<Image> callback) {
	protected void setCanvasImage(String sImageID, AECallback callback) {
		setCanvasImage(sConfigID, sImageID, callback);
	}

	//private void setCanvasImage(final String sConfigID, final String sImageID, AECallback<Image> callback) {
	private void setCanvasImage(final String sConfigID, final String sImageID,
			AECallback callback) {
		Utils.execSWTThread(new AERunnableWithCallback(callback) {

			@Override
			public Object runSupport() {
				if (canvas == null || canvas.isDisposed()) {
					return null;
				}

				canvas.setData("drawAlpha", drawAlpha);

				String oldImageID = (String) canvas.getData("ImageID");
				if (sImageID != null && sImageID.equals(oldImageID)) {
					
						// background might have changed, check
					Image bgImage 		= (Image)canvas.getParent().getData( "BGImage" );
					Image bgImageOld 	= (Image)canvas.getParent().getData( "BGImageOld" );
									
					if ( bgImage == bgImageOld ){
						return null;
					}
				}

				ImageLoader imageLoader = skin.getImageLoader(properties);

				if (oldImageID != null && canvas.getData("image") != null) {
					imageLoader.releaseImage(oldImageID);
				}

				int hpadding = properties.getIntValue(sConfigID + ".h-padding", 0);
				canvas.setData("hpadding", new Long(hpadding));


				Image[] images = sImageID == null || sImageID.length() == 0 ? null
						: imageLoader.getImages(sImageID);

				String sDrawMode = properties.getStringValue(sConfigID + ".drawmode");
				if (sDrawMode == null) {
					sDrawMode = properties.getStringValue(
							SWTSkinObjectImage.this.sConfigID + ".drawmode", "");
				}

				Long drawMode;
				if (sDrawMode.equals("scale")) {
					drawMode = DRAW_SCALE;
				} else if (sDrawMode.equals("scaledown")) {
						drawMode = DRAW_SCALEDOWN_OR_CENTER;
				} else if (sDrawMode.equals("stretch")) {
					drawMode = DRAW_STRETCH;
				} else if (sDrawMode.equals("center")) {
					drawMode = DRAW_CENTER;
				} else if (sDrawMode.equals("h-center")) {
					drawMode = DRAW_HCENTER;
				} else if (sDrawMode.equalsIgnoreCase("tile")) {
					drawMode = DRAW_TILE;
				} else if (sDrawMode.equalsIgnoreCase("animate")
						|| (sDrawMode.length() == 0 && images != null && images.length > 3)) {
					drawMode = DRAW_ANIMATE;
				} else if (sDrawMode.equalsIgnoreCase("left")) {
					drawMode = DRAW_LEFT;
				} else {
					drawMode = DRAW_NORMAL;
				}
				canvas.setData("drawmode", drawMode);

				Image image = null;

				boolean hasExistingDelay = canvas.getData("delay") != null;
				canvas.setData("delay", null);
				if (images == null) {
					canvas.setData("images", null);
					image = null;
				} else if (drawMode == DRAW_ANIMATE) {
					int animationDelay = ImageLoader.getInstance().getAnimationDelay(sImageID);

					canvas.setData("images", images);
					canvas.setData("ImageIndex", Long.valueOf(0));
					canvas.setData("delay", new Long(animationDelay));
					image = images[0];

					if (!hasExistingDelay) {
						setupAnimationTrigger(animationDelay);
					}
				} else if (images.length == 3) {
					Image imageLeft = images[0];
					if (ImageLoader.isRealImage(imageLeft)) {
						canvas.setData("image-left", imageLeft);
					}

					image = images[1];

					Image imageRight = images[2];
					if (ImageLoader.isRealImage(imageRight)) {
						canvas.setData("image-right", imageRight);
					}
				} else if (images.length > 0) {
					image = images[0];
				}

				if (image == null || image.isDisposed()) {
					image = ImageLoader.noImage;
				}


				//allowImageDimming = sDrawMode.equalsIgnoreCase("dim");

				Rectangle imgBounds = image.getBounds();
				if (drawMode != DRAW_CENTER && drawMode != DRAW_HCENTER
						&& drawMode != DRAW_STRETCH && drawMode != DRAW_SCALEDOWN_OR_CENTER) {
					canvas.setSize(imgBounds.width + hpadding, imgBounds.height);
					canvas.setData("oldSize", canvas.getSize());
				}
				//canvas.setData("image", image);

				if (drawMode == DRAW_TILE || drawMode == DRAW_NORMAL || drawMode == DRAW_LEFT
						|| drawMode == DRAW_ANIMATE) {
					// XXX Huh? A tile of one? :)
					FormData fd = (FormData) canvas.getLayoutData();
					if (fd == null) {
						fd = new FormData(imgBounds.width  + hpadding, imgBounds.height);
					} else {
						fd.width = imgBounds.width + hpadding;
						fd.height = imgBounds.height;
					}
					canvas.setData("oldSize", new Point(fd.width, fd.height));
					canvas.setLayoutData(fd);
					Utils.relayout(canvas);
				}

				// remove in case already added
				canvas.removePaintListener(paintListener);

				canvas.addPaintListener(paintListener);
				canvas.setData("ImageID", sImageID);

				canvas.redraw();

				SWTSkinUtils.addMouseImageChangeListeners(canvas);
				if (drawMode != DRAW_ANIMATE) {
					imageLoader.releaseImage(sImageID);
				}
				return null;
			}
		});
	}

	protected void setupAnimationTrigger(int animationDelay) {
		Utils.execSWTThreadLater(animationDelay, new AERunnable() {
			@Override
			public void runSupport() {
				if (!control.isDisposed()) {
					Object data = control.getData("delay");
					if (data == null) {
						return;
					}

					Image[] images = (Image[]) control.getData("images");
					int idx = ((Number) control.getData("ImageIndex")).intValue();
					idx++;
					if (idx >= images.length) {
						idx = 0;
					}
					control.setData("ImageIndex", new Long(idx));
					control.redraw();

					int delay = ((Number) control.getData("delay")).intValue();
					setupAnimationTrigger(delay);
				}
			}
		});
	}

	// @see SWTSkinObject#setBackground(java.lang.String, java.lang.String)
	@Override
	public void setBackground(String sConfigID, String sSuffix) {
		// No background for images?
	}

	// @see SWTSkinObject#switchSuffix(java.lang.String)
	@Override
	public String switchSuffix(String suffix, int level, boolean walkUp,
	                           boolean walkDown) {
		suffix = super.switchSuffix(suffix, level, walkUp, walkDown);
		if (customImage) {
			return suffix;
		}
		if (suffix == null) {
			return null;
		}

		final String fSuffix = suffix;

		Utils.execSWTThread(new AERunnable() {

			@Override
			public void runSupport() {
				currentImageID = (customImageID == null ? (sConfigID + ".image")
						: customImageID)
						+ fSuffix;
				if (isVisible()) {
					swt_reallySetImage();
				}
			}
		});

		return suffix;
	}

	protected void swt_reallySetImage() {
		if (currentImageID == null || customImage) {
			drawAlpha = 255;
			return;
		}

		boolean removedDisabled = false;
		ImageLoader imageLoader = skin.getImageLoader(properties);
		boolean imageExists = imageLoader.imageExists(currentImageID);
		if (!imageExists && imageLoader.imageExists(currentImageID + ".image")) {
			currentImageID = sConfigID + ".image";
			imageExists = true;
		}
		if (!imageExists && suffixes != null) {
			for (int i = suffixes.length - 1; i >= 0; i--) {
				String suffixToRemove = suffixes[i];
				if (suffixToRemove != null) {
					if (suffixToRemove.equals("-disabled")) {
						removedDisabled = true;
					}
					currentImageID = currentImageID.substring(0, currentImageID.length()
							- suffixToRemove.length());
					if (imageLoader.imageExists(currentImageID)) {
						imageExists = true;
						break;
					}
				}
			}
		}

		if (imageExists) {
			drawAlpha  = removedDisabled ? 64 : 255;
			setCanvasImage(currentImageID, null);
		} else {
			drawAlpha = 255;
			Utils.execSWTThread(new AERunnable() {
				@Override
				public void runSupport() {
					FormData fd = (FormData) canvas.getLayoutData();
					if (fd == null) {
						fd = new FormData(0, 0);
					} else {
						fd.width = 0;
						fd.height = 0;
					}
					canvas.setLayoutData(fd);
					if (initialized) {
						Utils.relayout(canvas);
					}
				}
			});
		}
	}

	public void setImage(final Image image) {
		Utils.execSWTThread(new AERunnable() {
			@Override
			public void runSupport() {
				customImage = true;
				customImageID = null;
				drawAlpha  = 255;
				canvas.setData("image", image);
				canvas.setData("ImageID", null);
				canvas.setData("image-left", null);
				canvas.setData("image-right", null);
				canvas.setData("drawAlpha", null);

				canvas.removePaintListener(paintListener);
				canvas.addPaintListener(paintListener);

				Utils.relayout(canvas);
				canvas.redraw();
			}
		});
	}

	public void setImageByID(final String imageID, final AECallback callback) {
		if (!customImage && customImageID != null
				&& customImageID.equals(imageID)) {
			if (callback != null) {
				callback.callbackFailure(null);
			}
			return;
		}
		customImage = false;
		customImageID = imageID;

		if (imageID == null) {
			setCanvasImage(this.sConfigID, null, null);
			return;
		}

		Utils.execSWTThread(new AERunnable() {
			@Override
			public void runSupport() {
				String fullImageID = imageID + getSuffix();
				ImageLoader imageLoader = skin.getImageLoader(properties);
				Image image = imageLoader.getImage(fullImageID);
				if (ImageLoader.isRealImage(image)) {
					setCanvasImage(sConfigID, fullImageID, callback);
				} else {
					setCanvasImage(sConfigID, imageID, callback);
				}
				imageLoader.releaseImage(fullImageID);
			}
		});
	}

	public void setImageUrl(final String url) {
		if (!customImage && customImageID != null
				&& customImageID.equals(url)) {
			return;
		}
		customImage = false;
		customImageID = url;

		Utils.execSWTThread(new AERunnable() {
			@Override
			public void runSupport() {
				final ImageLoader imageLoader = skin.getImageLoader(properties);
				imageLoader.getUrlImage(url, new ImageDownloaderListener() {
					@Override
					public void imageDownloaded(Image image, String key, boolean returnedImmediately) {
						setCanvasImage(url, null);
						imageLoader.releaseImage(url);
					}
				});
			}
		});
	}
}
